package com.ajustor.minecities.network;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.build.PlanItem;
import com.ajustor.minecities.build.Proposals;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.Towns;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le plan unique (MC-23) : un clic droit dans le vide ouvre l'interface du plan (choisir le schéma et
 * le style, tourner, valider ou abandonner le chantier proposé) ; le serveur revérifie tout, droits
 * compris. Tant qu'un joueur tient le plan dans la ville, il voit le fantôme des chantiers en cours,
 * ce qu'il reste à poser.
 */
public final class PlanNetwork {
	/** Au-delà, un chantier ne se montre pas. */
	private static final double VIEW = 96;

	/** Ce que montre l'interface du plan. */
	public record PlanSnapshot(String town, boolean manager, String blueprint, String style, boolean proposal,
			List<TownSnapshot.PlanRow> plans) implements CustomPacketPayload {
		public static final Type<PlanSnapshot> TYPE = new Type<>(MineCities.id("plan_snapshot"));
		public static final StreamCodec<FriendlyByteBuf, PlanSnapshot> CODEC = StreamCodec.of(PlanSnapshot::write,
				PlanSnapshot::read);

		private static void write(FriendlyByteBuf buf, PlanSnapshot s) {
			buf.writeUtf(s.town);
			buf.writeBoolean(s.manager);
			buf.writeUtf(s.blueprint);
			buf.writeUtf(s.style);
			buf.writeBoolean(s.proposal);
			buf.writeVarInt(s.plans.size());
			for (TownSnapshot.PlanRow plan : s.plans) {
				buf.writeUtf(plan.id());
				buf.writeUtf(plan.key());
				buf.writeUtf(plan.fallback());
				buf.writeVarInt(plan.width());
				buf.writeVarInt(plan.depth());
				buf.writeVarInt(plan.height());
				buf.writeUtf(plan.kind());
				buf.writeUtf(plan.locked());
			}
		}

		private static PlanSnapshot read(FriendlyByteBuf buf) {
			String town = buf.readUtf();
			boolean manager = buf.readBoolean();
			String blueprint = buf.readUtf();
			String style = buf.readUtf();
			boolean proposal = buf.readBoolean();
			int count = buf.readVarInt();
			List<TownSnapshot.PlanRow> plans = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				plans.add(new TownSnapshot.PlanRow(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(),
						buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf()));
			}
			return new PlanSnapshot(town, manager, blueprint, style, proposal, plans);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Ce que le joueur demande depuis l'interface : {@code select:<style>:<schéma>}, {@code rotate},
	 * {@code confirm} ou {@code cancel}.
	 */
	public record ActionPayload(String action) implements CustomPacketPayload {
		public static final Type<ActionPayload> TYPE = new Type<>(MineCities.id("plan_action"));
		public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ActionPayload::action, ActionPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Ce que coûterait ce schéma dans ce style : l'interface le demande au survol d'un plan (MC-76). */
	public record CostRequest(String blueprint, String style) implements CustomPacketPayload {
		public static final Type<CostRequest> TYPE = new Type<>(MineCities.id("plan_cost_request"));
		public static final StreamCodec<FriendlyByteBuf, CostRequest> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, CostRequest::blueprint, ByteBufCodecs.STRING_UTF8, CostRequest::style,
				CostRequest::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Un matériau du coût : l'objet (son identifiant), combien il en faut, combien les réserves en ont. */
	public record Material(String item, int needed, int stocked) {
	}

	/** Le coût d'un schéma : les émeraudes payées à la commande, et les matériaux les plus nombreux. */
	public record CostPayload(String blueprint, String style, int emeralds, List<Material> materials)
			implements CustomPacketPayload {
		public static final Type<CostPayload> TYPE = new Type<>(MineCities.id("plan_cost"));
		public static final StreamCodec<FriendlyByteBuf, CostPayload> CODEC = StreamCodec.of(CostPayload::write,
				CostPayload::read);

		private static void write(FriendlyByteBuf buf, CostPayload c) {
			buf.writeUtf(c.blueprint);
			buf.writeUtf(c.style);
			buf.writeVarInt(c.emeralds);
			buf.writeVarInt(c.materials.size());
			for (Material m : c.materials) {
				buf.writeUtf(m.item());
				buf.writeVarInt(m.needed());
				buf.writeVarInt(m.stocked());
			}
		}

		private static CostPayload read(FriendlyByteBuf buf) {
			String blueprint = buf.readUtf();
			String style = buf.readUtf();
			int emeralds = buf.readVarInt();
			int count = buf.readVarInt();
			List<Material> materials = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				materials.add(new Material(buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
			}
			return new CostPayload(blueprint, style, emeralds, materials);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Combien de matériaux le coût montre, les plus nombreux d'abord. */
	private static final int COST_LINES = 10;

	/** Les chantiers dont chaque joueur voit le fantôme, et combien de blocs il en restait à l'envoi. */
	private static final Map<UUID, Map<UUID, Integer>> SHOWN = new HashMap<>();

	private PlanNetwork() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(PlanSnapshot.TYPE, PlanSnapshot.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE,
				(payload, context) -> act(context.player(), payload.action()));
		PayloadTypeRegistry.serverboundPlay().register(CostRequest.TYPE, CostRequest.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CostPayload.TYPE, CostPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CostRequest.TYPE, (payload, context) -> cost(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(PlanNetwork::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SHOWN.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHOWN.clear());
	}

	/** Le coût de ce schéma, pour ce joueur : ce qu'en ont les réserves de sa ville (MC-76). */
	private static void cost(ServerPlayer player, CostRequest request) {
		if (ServerPlayNetworking.canSend(player, CostPayload.TYPE)) {
			costOf(player, request).ifPresent(cost -> ServerPlayNetworking.send(player, cost));
		}
	}

	/** Le coût de ce schéma pour ce joueur ; vide si le schéma n'existe pas. */
	public static Optional<CostPayload> costOf(ServerPlayer player, CostRequest request) {
		ServerLevel level = player.level();
		Optional<Blueprint> blueprint = Optional.ofNullable(net.minecraft.resources.Identifier.tryParse(request.blueprint()))
				.flatMap(id -> Blueprints.get(level.getServer(), id));
		if (blueprint.isEmpty()) {
			return Optional.empty();
		}
		com.ajustor.minecities.build.BuildStyle style = com.ajustor.minecities.build.BuildStyle
				.fromSerializedName(request.style()).orElse(com.ajustor.minecities.build.BuildStyle.PLAINS);
		Map<net.minecraft.world.item.Item, Integer> needs = BuildPlan.materials(BuildPlan.of(blueprint.get(), style,
				net.minecraft.core.BlockPos.ZERO, net.minecraft.world.level.block.Rotation.NONE));
		Towns towns = Towns.get(level.getServer());
		Optional<com.ajustor.minecities.town.Town> town = towns.at(level.dimension(), player.blockPosition())
				.or(() -> towns.memberOf(player.getUUID()).stream().findFirst());
		List<net.minecraft.world.Container> stores = new ArrayList<>();
		town.ifPresent(t -> com.ajustor.minecities.town.Storage.stores(t).forEach(pos -> {
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof net.minecraft.world.Container c) {
				stores.add(c);
			}
		}));
		List<Material> materials = needs.entrySet().stream()
				.sorted(Map.Entry.<net.minecraft.world.item.Item, Integer>comparingByValue().reversed())
				.limit(COST_LINES)
				.map(e -> new Material(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey()).toString(),
						e.getValue(), stores.stream().mapToInt(c -> com.ajustor.minecities.town.Stock.count(c,
								s -> s.is(e.getKey()))).sum()))
				.toList();
		return Optional.of(new CostPayload(request.blueprint(), request.style(),
				com.ajustor.minecities.town.Budget.cost(blueprint.get().level()), materials));
	}

	/** Le plan que tient ce joueur, en main ou dans l'autre main. */
	public static Optional<ItemStack> held(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(McItems.PLAN)) {
				return Optional.of(stack);
			}
		}
		return Optional.empty();
	}

	/** Ce que l'interface montre à ce joueur, dans la ville où il est ; vide hors des villes. */
	public static Optional<PlanSnapshot> snapshot(ServerLevel level, ServerPlayer player) {
		Optional<Town> town = Towns.get(level.getServer()).at(level.dimension(), player.blockPosition());
		if (town.isEmpty()) {
			return Optional.empty();
		}
		ItemStack plan = held(player).orElse(ItemStack.EMPTY);
		String blueprint = plan.isEmpty() ? "" : PlanItem.blueprint(plan).map(Identifier::toString).orElse("");
		String style = (plan.isEmpty() ? BuildStyle.PLAINS : PlanItem.style(plan)).serializedName();
		return Optional.of(new PlanSnapshot(town.get().name(), town.get().canManage(player.getUUID()), blueprint, style,
				Proposals.of(player.getUUID()).isPresent(), TownNetwork.plans(level, town.get())));
	}

	/** Ouvre l'interface du plan ; hors des villes, dit pourquoi. */
	public static void open(ServerLevel level, ServerPlayer player) {
		Optional<PlanSnapshot> snapshot = snapshot(level, player);
		if (snapshot.isEmpty()) {
			player.sendSystemMessage(Component.translatable("command.minecities.no_town").withStyle(ChatFormatting.RED));
			return;
		}
		if (ServerPlayNetworking.canSend(player, PlanSnapshot.TYPE)) {
			ServerPlayNetworking.send(player, snapshot.get());
		}
	}

	/** Une demande de l'interface, revérifiée ici : le plan en main, la ville, les droits, le palier. */
	public static void act(ServerPlayer player, String action) {
		ServerLevel level = player.level();
		Optional<Component> refusal = switch (action) {
			case "rotate" -> Proposals.rotate(level, player);
			case "confirm" -> Proposals.confirm(level, player);
			case "cancel" -> Proposals.cancel(level.getServer(), player.getUUID()) ? Optional.empty()
					: Optional.of(Component.translatable("message.minecities.proposal.none"));
			default -> action.startsWith("select:") ? select(level, player, action) : Optional.empty();
		};
		refusal.ifPresent(r -> player.sendSystemMessage(r.copy().withStyle(ChatFormatting.RED)));
		if (action.equals("cancel") && refusal.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.minecities.proposal.cancelled")
					.withStyle(ChatFormatting.GRAY));
		}
	}

	/** « select:<style>:<schéma> » : le plan en main prend ce schéma et ce style. */
	private static Optional<Component> select(ServerLevel level, ServerPlayer player, String action) {
		Optional<ItemStack> plan = held(player);
		if (plan.isEmpty()) {
			return Optional.of(Component.translatable("message.minecities.plan.not_held"));
		}
		Optional<Town> town = Towns.get(level.getServer()).at(level.dimension(), player.blockPosition());
		if (town.isEmpty()) {
			return Optional.of(Component.translatable("command.minecities.no_town"));
		}
		if (!town.get().canManage(player.getUUID())) {
			return Optional.of(Component.translatable("command.minecities.not_manager"));
		}
		String[] parts = action.split(":", 3);
		Optional<Blueprint> blueprint = parts.length < 3 ? Optional.empty()
				: Optional.ofNullable(Identifier.tryParse(parts[2])).flatMap(id -> Blueprints.get(level.getServer(), id))
				.filter(b -> !b.legacy());
		if (blueprint.isEmpty()) {
			return Optional.of(Component.translatable("command.minecities.no_blueprint"));
		}
		if (town.get().tier().ordinal() < blueprint.get().tier().ordinal()) {
			return Optional.of(Component.translatable("screen.minecities.plans.locked",
					Component.translatable(blueprint.get().tier().translationKey())));
		}
		BuildStyle style = BuildStyle.fromSerializedName(parts[1]).orElse(BuildStyle.PLAINS);
		PlanItem.choose(plan.get(), blueprint.get(), style);
		player.sendSystemMessage(Component.translatable("message.minecities.plan.chosen", blueprint.get().displayName(),
				Component.translatable(style.translationKey())).withStyle(ChatFormatting.GOLD));
		return Optional.empty();
	}

	/** Le plan de chaque chantier montré, par identifiant de fantôme : il ne change pas (MC-49). */
	private static final Map<UUID, List<BuildPlan.Placement>> PLANS = new HashMap<>();

	/** Le fantôme d'un chantier se montre sous cet identifiant, distinct de celui d'un joueur. */
	public static UUID ghostId(Construction construction) {
		return UUID.nameUUIDFromBytes(("site:" + construction.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	/** Chaque seconde : le fantôme des chantiers en cours, à qui tient le plan dans la ville ; effacé sinon. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			show(player);
		}
		// Les plans des chantiers que plus personne ne regarde s'oublient.
		java.util.Set<UUID> seen = new java.util.HashSet<>();
		SHOWN.values().forEach(m -> seen.addAll(m.keySet()));
		PLANS.keySet().retainAll(seen);
	}

	/** Met à jour les chantiers que voit ce joueur. */
	public static void show(ServerPlayer player) {
		ServerLevel level = player.level();
		Map<UUID, Construction> wanted = new HashMap<>();
		if (held(player).isPresent()) {
			Towns.get(level.getServer()).at(level.dimension(), player.blockPosition()).ifPresent(town -> {
				for (Construction c : town.constructions()) {
					double dx = (c.min().getX() + c.max().getX()) / 2.0 - player.getX();
					double dz = (c.min().getZ() + c.max().getZ()) / 2.0 - player.getZ();
					if (!c.done() && dx * dx + dz * dz <= VIEW * VIEW) {
						wanted.put(ghostId(c), c);
					}
				}
			});
		}
		Map<UUID, Integer> shown = SHOWN.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
		if (!ServerPlayNetworking.canSend(player, GhostPayload.TYPE)) {
			return;
		}
		for (UUID gone : List.copyOf(shown.keySet())) {
			if (!wanted.containsKey(gone)) {
				ServerPlayNetworking.send(player, GhostPayload.clear(gone));
				shown.remove(gone);
			}
		}
		for (Map.Entry<UUID, Construction> entry : wanted.entrySet()) {
			// Renvoyé quand ce qui reste à poser a changé : les bâtisseurs avancent.
			Construction c = entry.getValue();
			Optional<Blueprint> blueprint = Blueprints.get(level.getServer(), c.blueprint());
			if (blueprint.isEmpty()) {
				continue;
			}
			List<BuildPlan.Placement> plan = PLANS.computeIfAbsent(entry.getKey(),
					k -> BuildPlan.of(blueprint.get(), c.style(), c.origin(), c.rotation()));
			List<GhostPayload.Block> blocks = Proposals.ghost(plan, i -> !c.isPlaced(i));
			if (blocks.isEmpty()) {
				if (shown.remove(entry.getKey()) != null) {
					ServerPlayNetworking.send(player, GhostPayload.clear(entry.getKey()));
				}
				continue;
			}
			if (!Integer.valueOf(blocks.size()).equals(shown.get(entry.getKey()))) {
				ServerPlayNetworking.send(player, new GhostPayload(entry.getKey(), true, blocks));
				shown.put(entry.getKey(), blocks.size());
			}
		}
	}
}
