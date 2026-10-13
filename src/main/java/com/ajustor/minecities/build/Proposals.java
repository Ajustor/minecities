package com.ajustor.minecities.build;

import com.ajustor.minecities.network.GhostPayload;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.Towns;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Les chantiers proposés, pas encore commandés (brief 09) : le maire (ou un adjoint) choisit un
 * schéma, par {@code /ville build} ou en posant un plan, et voit son fantôme à l'endroit choisi ;
 * il le tourne, puis le valide (le chantier rejoint la file de la ville) ou l'abandonne (rien n'est
 * posé, rien n'est dépensé). Les joueurs proches voient aussi le fantôme. Une proposition par joueur ;
 * elle s'oublie au bout de dix minutes, ou quand il se déconnecte.
 */
public final class Proposals {
	/** Les joueurs à cette distance du chantier voient son fantôme. */
	private static final double VIEW = 96;
	private static final int LIFETIME = 20 * 60 * 10;

	/** Un chantier proposé, et les joueurs à qui son fantôme a été envoyé. */
	public static final class Proposal {
		private final UUID owner;
		private final ResourceKey<Level> dimension;
		private final UUID town;
		private final Blueprint blueprint;
		private final BuildStyle style;
		private BlockPos origin;
		private Rotation rotation;
		private long expires;
		private final Set<UUID> viewers = new HashSet<>();

		private Proposal(UUID owner, ResourceKey<Level> dimension, UUID town, Blueprint blueprint, BuildStyle style,
				BlockPos origin, Rotation rotation, long expires) {
			this.owner = owner;
			this.dimension = dimension;
			this.town = town;
			this.blueprint = blueprint;
			this.style = style;
			this.origin = origin;
			this.rotation = rotation;
			this.expires = expires;
		}

		public Blueprint blueprint() {
			return blueprint;
		}

		public BuildStyle style() {
			return style;
		}

		public BlockPos origin() {
			return origin;
		}

		public Rotation rotation() {
			return rotation;
		}

		/** Le milieu de l'emprise, au sol. */
		private BlockPos center() {
			BlockPos[] bounds = BuildPlan.bounds(blueprint, origin, rotation);
			return new BlockPos((bounds[0].getX() + bounds[1].getX()) / 2, bounds[0].getY(),
					(bounds[0].getZ() + bounds[1].getZ()) / 2);
		}
	}

	private static final Map<UUID, Proposal> PROPOSALS = new HashMap<>();

	private Proposals() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Proposals::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(server, handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PROPOSALS.clear());
	}

	/** La proposition en cours de ce joueur. */
	public static Optional<Proposal> of(UUID player) {
		return Optional.ofNullable(PROPOSALS.get(player));
	}

	/**
	 * Propose ce schéma à cet endroit, à la place de la proposition d'avant. Renvoie le refus, s'il y
	 * en a un : pas le maire ni un adjoint, schéma pas encore ouvert à ce palier, emprise hors du
	 * territoire ou sur un chantier en cours.
	 */
	public static Optional<Component> propose(ServerLevel level, ServerPlayer player, Town town, Blueprint blueprint,
			BuildStyle style, BlockPos origin, Rotation rotation) {
		if (!town.canManage(player.getUUID())) {
			return Optional.of(Component.translatable("command.minecities.not_manager"));
		}
		Optional<Component> refusal = Builds.check(level, town, blueprint, origin, rotation);
		if (refusal.isPresent()) {
			return refusal;
		}
		cancel(level.getServer(), player.getUUID());
		Proposal proposal = new Proposal(player.getUUID(), level.dimension(), town.id(), blueprint, style, origin,
				rotation, level.getGameTime() + LIFETIME);
		PROPOSALS.put(player.getUUID(), proposal);
		show(level, proposal);
		player.sendSystemMessage(Component.translatable("message.minecities.proposal", blueprint.displayName(),
				Component.translatable(style.translationKey())).withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(buttons());
		return Optional.empty();
	}

	/** Tourne la proposition d'un quart de tour, autour du milieu de son emprise. */
	public static Optional<Component> rotate(ServerLevel level, ServerPlayer player) {
		Proposal proposal = PROPOSALS.get(player.getUUID());
		if (proposal == null) {
			return Optional.of(Component.translatable("message.minecities.proposal.none"));
		}
		BlockPos center = proposal.center();
		Rotation next = proposal.rotation.getRotated(Rotation.CLOCKWISE_90);
		Proposal turned = new Proposal(proposal.owner, proposal.dimension, proposal.town, proposal.blueprint,
				proposal.style, proposal.origin, next, proposal.expires);
		BlockPos origin = proposal.origin.offset(center.subtract(turned.center()));
		Optional<Town> town = Towns.get(level.getServer()).byId(proposal.town);
		if (town.isEmpty()) {
			cancel(level.getServer(), player.getUUID());
			return Optional.of(Component.translatable("message.minecities.proposal.none"));
		}
		Optional<Component> refusal = Builds.check(level, town.get(), proposal.blueprint, origin, next);
		if (refusal.isPresent()) {
			return refusal;
		}
		proposal.origin = origin;
		proposal.rotation = next;
		proposal.expires = level.getGameTime() + LIFETIME;
		proposal.viewers.clear();
		show(level, proposal);
		return Optional.empty();
	}

	/** Commande le chantier proposé : il rejoint la file de la ville. */
	public static Optional<Component> confirm(ServerLevel level, ServerPlayer player) {
		Proposal proposal = PROPOSALS.get(player.getUUID());
		if (proposal == null) {
			return Optional.of(Component.translatable("message.minecities.proposal.none"));
		}
		Optional<Town> town = Towns.get(level.getServer()).byId(proposal.town);
		if (town.isEmpty() || !town.get().canManage(player.getUUID())) {
			cancel(level.getServer(), player.getUUID());
			return Optional.of(Component.translatable("command.minecities.not_manager"));
		}
		ServerLevel site = level.getServer().getLevel(proposal.dimension);
		if (site == null) {
			return Optional.of(Component.translatable("message.minecities.proposal.none"));
		}
		Optional<Component> refusal = Builds.order(site, town.get(), proposal.blueprint, proposal.style,
				proposal.origin, proposal.rotation);
		if (refusal.isPresent()) {
			return refusal;
		}
		cancel(level.getServer(), player.getUUID());
		BlockPos[] bounds = BuildPlan.bounds(proposal.blueprint, proposal.origin, proposal.rotation);
		Builds.outline(site, player, bounds[0], bounds[1]);
		player.sendSystemMessage(Component.translatable("command.minecities.build.ordered",
				proposal.blueprint.displayName(), Component.translatable(proposal.style.translationKey()),
				town.get().name()).withStyle(ChatFormatting.GOLD));
		Builds.describeMaterials(site, town.get(), BuildPlan.of(proposal.blueprint, proposal.style, proposal.origin,
				proposal.rotation), player);
		if (town.get().workers(Job.BUILDER) == 0) {
			player.sendSystemMessage(Component.translatable("command.minecities.build.no_builder")
					.withStyle(ChatFormatting.YELLOW));
		}
		return Optional.empty();
	}

	/** Abandonne la proposition de ce joueur ; vrai s'il en avait une. */
	public static boolean cancel(MinecraftServer server, UUID player) {
		Proposal proposal = PROPOSALS.remove(player);
		if (proposal == null) {
			return false;
		}
		GhostPayload clear = GhostPayload.clear(player);
		for (UUID viewer : proposal.viewers) {
			ServerPlayer online = server.getPlayerList().getPlayer(viewer);
			if (online != null) {
				ServerPlayNetworking.send(online, clear);
			}
		}
		return true;
	}

	/** Les boutons du chat : tourner, valider, abandonner. */
	private static Component buttons() {
		MutableComponent line = Component.empty();
		for (String action : new String[]{"rotate", "confirm", "cancel"}) {
			if (!line.getSiblings().isEmpty()) {
				line.append(" ");
			}
			ChatFormatting color = action.equals("confirm") ? ChatFormatting.GREEN
					: action.equals("cancel") ? ChatFormatting.RED : ChatFormatting.AQUA;
			line.append(Component.translatable("message.minecities.proposal." + action).withStyle(s -> s.withColor(color)
					.withClickEvent(new ClickEvent.RunCommand("/ville build " + action))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("/ville build " + action)))));
		}
		return line;
	}

	/** Envoie le fantôme aux joueurs proches qui ne l'ont pas encore. */
	private static void show(ServerLevel level, Proposal proposal) {
		GhostPayload ghost = null;
		BlockPos center = proposal.center();
		for (ServerPlayer player : level.players()) {
			if (proposal.viewers.contains(player.getUUID())
					|| player.distanceToSqr(center.getX(), center.getY(), center.getZ()) > VIEW * VIEW) {
				continue;
			}
			if (ghost == null) {
				ghost = new GhostPayload(proposal.owner, false, ghost(proposal));
			}
			if (ServerPlayNetworking.canSend(player, GhostPayload.TYPE)) {
				ServerPlayNetworking.send(player, ghost);
			}
			proposal.viewers.add(player.getUUID());
		}
	}

	/** Les blocs du fantôme : tout le plan sauf l'air, portes et lits entiers. */
	public static List<GhostPayload.Block> ghost(Proposal proposal) {
		return ghost(BuildPlan.of(proposal.blueprint, proposal.style, proposal.origin, proposal.rotation), i -> true);
	}

	/** Les blocs du fantôme de ce plan, pour ceux que {@code wanted} retient (par leur rang dans le plan). */
	public static List<GhostPayload.Block> ghost(List<BuildPlan.Placement> plan, java.util.function.IntPredicate wanted) {
		List<GhostPayload.Block> blocks = new ArrayList<>();
		for (int i = 0; i < plan.size(); i++) {
			if (!wanted.test(i)) {
				continue;
			}
			BuildPlan.Placement p = plan.get(i);
			BlockState state = p.state();
			if (state.isAir()) {
				continue;
			}
			blocks.add(new GhostPayload.Block(p.pos(), state));
			// La moitié haute d'une porte, la tête d'un lit : le plan les pose avec l'autre moitié.
			if (state.getBlock() instanceof DoorBlock) {
				blocks.add(new GhostPayload.Block(p.pos().above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
			} else if (state.getBlock() instanceof BedBlock) {
				Direction facing = state.getValue(BedBlock.FACING);
				blocks.add(new GhostPayload.Block(p.pos().relative(facing), state.setValue(BedBlock.PART, BedPart.HEAD)));
			}
		}
		return blocks;
	}

	private static void tick(MinecraftServer server) {
		if (PROPOSALS.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		for (UUID owner : List.copyOf(PROPOSALS.keySet())) {
			Proposal proposal = PROPOSALS.get(owner);
			ServerLevel level = server.getLevel(proposal.dimension);
			if (level == null || level.getGameTime() > proposal.expires) {
				ServerPlayer player = server.getPlayerList().getPlayer(owner);
				cancel(server, owner);
				if (player != null) {
					player.sendSystemMessage(Component.translatable("message.minecities.proposal.expired")
							.withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			show(level, proposal);
		}
	}

	/** Pour les scènes : l'identifiant du schéma proposé par ce joueur. */
	public static Optional<Identifier> blueprintOf(UUID player) {
		return of(player).map(p -> p.blueprint.id());
	}
}
