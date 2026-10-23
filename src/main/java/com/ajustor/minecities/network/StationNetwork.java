package com.ajustor.minecities.network;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Hunger;
import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.Towns;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * L'interface des postes de travail du mod : un clic droit sur le poste l'ouvre (le serveur envoie ce
 * qu'il y a à montrer), et le bouton « Où ? » fait luire son travailleur.
 */
public final class StationNetwork {
	/** On agit sur le poste à portée de main. */
	private static final double REACH = 8.0;

	/** Une demande au plus par joueur tous les tant de ticks : chacune coûte un relevé (celui d'un puits de mine lit des milliers de blocs). */
	private static final int COOLDOWN = 10;
	private static final Map<UUID, Long> LAST_ACTION = new java.util.HashMap<>();

	/** Trop tôt après la précédente demande de ce joueur ? (MC-49) */
	private static boolean throttled(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST_ACTION.put(player.getUUID(), now);
		return last != null && now - last >= 0 && now - last < COOLDOWN;
	}

	/** Le joueur fait luire le travailleur de ce poste. */
	public record ActionPayload(BlockPos pos, String action, UUID target) implements CustomPacketPayload {
		public static final Type<ActionPayload> TYPE = new Type<>(MineCities.id("station_action"));
		public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, ActionPayload::pos,
				ByteBufCodecs.STRING_UTF8, ActionPayload::action,
				UUIDUtil.STREAM_CODEC, ActionPayload::target,
				ActionPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private StationNetwork() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(StationSnapshot.TYPE, StationSnapshot.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, context) -> {
			if (!throttled(context.player())) {
				act(context.player(), payload);
			}
		});
	}

	/** Le métier de ce poste du mod : celui qu'il sert ici, sinon celui de son nom (un poste loin de l'eau…). */
	public static Optional<Job> job(ServerLevel level, BlockPos pos, BlockState state) {
		Optional<Job> job = Job.ofWorkstation(level, pos, state);
		if (job.isPresent()) {
			return job;
		}
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		for (Job j : Job.values()) {
			if (path.equals(j.stationId())) {
				return Optional.of(j);
			}
		}
		return Optional.empty();
	}

	/** Ouvre l'interface de ce poste ; faux si le bloc n'est le poste d'aucun métier. */
	public static boolean open(ServerLevel level, BlockPos pos, ServerPlayer player) {
		Optional<StationSnapshot> snapshot = snapshot(level, pos, player);
		snapshot.ifPresent(s -> ServerPlayNetworking.send(player, s));
		return snapshot.isPresent();
	}

	public static Optional<StationSnapshot> snapshot(ServerLevel level, BlockPos pos) {
		return snapshot(level, pos, null);
	}

	/** Un poste libre : un citoyen sans emploi viendra le prendre, ou il faut d'abord un habitant de plus. */
	public static String vacancy(Town town) {
		return town.residents().values().stream().anyMatch(r -> !r.isChild() && r.job().isEmpty()) ? "vacant"
				: "vacant_none";
	}

	/** Ce que l'interface montre de ce poste, à ce joueur (s'il gère la ville, il voit les commandes). */
	public static Optional<StationSnapshot> snapshot(ServerLevel level, BlockPos pos, ServerPlayer viewer) {
		BlockState state = level.getBlockState(pos);
		Optional<Job> found = job(level, pos, state);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		Job job = found.get();
		Optional<Town> town = Towns.get(level.getServer()).at(level.dimension(), pos);
		List<StationSnapshot.Worker> workers = new ArrayList<>();
		List<StationSnapshot.Site> sites = new ArrayList<>();
		List<StationSnapshot.Shortage> missing = new ArrayList<>();
		String status;
		String detail = "";
		if (town.isEmpty()) {
			status = "no_town";
		} else {
			Town t = town.get();
			boolean declared = t.houses().stream().anyMatch(h -> h.workstations().stream()
					.anyMatch(w -> w.pos().equals(pos) && w.job() == job));
			for (Map.Entry<UUID, Resident> e : t.residents().entrySet()) {
				Optional<Resident.Employment> employment = e.getValue().job();
				if (employment.isPresent() && employment.get().job() == job && employment.get().station().equals(pos)) {
					workers.add(worker(level, e.getKey(), e.getValue()));
				}
			}
			if (Job.ofWorkstation(level, pos, state).isEmpty()) {
				status = "no_water";
			} else if (!job.unlockedAt(t.tier())) {
				status = "locked";
				detail = job.minTier().translationKey();
			} else if (!declared) {
				status = "undeclared";
			} else {
				status = workers.isEmpty() ? vacancy(t) : "ok";
			}
			if (job == Job.BUILDER) {
				for (Construction c : t.constructions()) {
					if (!c.done()) {
						sites.add(site(level, c));
					}
				}
				com.ajustor.minecities.build.Builds.shortages(level, t)
						.forEach((item, count) -> missing.add(new StationSnapshot.Shortage(item, count)));
			}
		}
		// Le puits du mineur : son état, ce qu'il peut encore donner et ce qu'il a donné.
		String mineState = "";
		List<StationSnapshot.Ore> ores = new ArrayList<>();
		if (job == Job.MINER && town.isPresent()) {
			Town t = town.get();
			Optional<com.ajustor.minecities.town.Mine> existing = t.mine(pos);
			BlockPos workshop = t.houses().stream().filter(h -> h.workstations().stream().anyMatch(w -> w.pos().equals(pos)))
					.map(House::plaque).findFirst().orElse(pos);
			com.ajustor.minecities.town.Mine mine = existing.orElseGet(() -> com.ajustor.minecities.town.Mines.plan(level, t,
					pos, workshop));
			mineState = existing.isEmpty() ? "new" : mine.closed() ? "closed" : mine.exhausted() ? "exhausted" : "digging";
			Map<String, Integer> remaining = mine.closed() ? Map.of() : com.ajustor.minecities.town.Mines.prospect(level, t, mine);
			java.util.Set<String> keys = new java.util.TreeSet<>(remaining.keySet());
			keys.addAll(mine.mined().keySet());
			for (String key : keys) {
				ores.add(new StationSnapshot.Ore(new net.minecraft.world.item.ItemStack(
						com.ajustor.minecities.town.Mines.oreItem(key)), remaining.getOrDefault(key, 0),
						mine.mined().getOrDefault(key, 0)));
			}
		}
		boolean manager = viewer != null && town.filter(t -> t.canManage(viewer.getUUID())).isPresent();
		// La maîtrise de l'atelier où sert ce poste (MC-63) ; rien hors d'un atelier déclaré.
		String mastery = town.flatMap(t -> t.houses().stream().filter(h -> h.workstations().stream()
						.anyMatch(w -> w.pos().equals(pos))).findFirst()
				.map(h -> com.ajustor.minecities.town.Mastery.state(level, t, h.plaque()))).orElse("");
		return Optional.of(new StationSnapshot(pos, state.getBlock().getDescriptionId(), job.serializedName(),
				town.map(Town::name).orElse(""), status, detail, workers, sites, manager, mineState, ores, mastery,
				missing));
	}

	/** Un travailleur : ce qu'il fait, d'après ce qu'on voit de lui. */
	private static StationSnapshot.Worker worker(ServerLevel level, UUID id, Resident resident) {
		String rank = resident.job().map(j -> com.ajustor.minecities.town.Experience.of(j, level.getGameTime())
				.serializedName()).orElse("");
		if (!(level.getEntity(id) instanceof CitizenEntity citizen)) {
			return new StationSnapshot.Worker(id, resident.name(), "away", Optional.empty(), rank);
		}
		String state = citizen.isSleeping() ? "sleeping" : citizen.illness().isPresent() ? "sick"
				: citizen.hunger() < Hunger.HUNGRY ? "hungry" : "working";
		return new StationSnapshot.Worker(id, resident.name(), state, citizen.lastNeed(), rank);
	}

	/** Un chantier : son nom, et combien de blocs de son plan sont posés. */
	private static StationSnapshot.Site site(ServerLevel level, Construction c) {
		Optional<com.ajustor.minecities.build.Blueprint> blueprint = Blueprints.get(level.getServer(), c.blueprint());
		int total = com.ajustor.minecities.build.Builds.plan(level.getServer(), c).size();
		Component name = blueprint.<Component>map(com.ajustor.minecities.build.Blueprint::displayName)
				.orElse(Component.literal(c.blueprint().getPath()));
		// Les blocs posés, où qu'ils soient dans le plan : le bâtisseur en passe quand un matériau manque.
		int placed = (int) java.util.stream.IntStream.range(0, total).filter(c::isPlaced).count();
		return new StationSnapshot.Site(name, c.origin(), placed, total, c.id(), c.paused());
	}

	/** Le compte rendu d'un dépôt pour les chantiers : ce qui a été déposé, ou qu'il n'avait rien d'utile. */
	private static Component supplied(Map<net.minecraft.world.item.Item, Integer> given) {
		if (given.isEmpty()) {
			return Component.translatable("message.minecities.build.supplied_none")
					.withStyle(net.minecraft.ChatFormatting.YELLOW);
		}
		net.minecraft.network.chat.MutableComponent list = Component.empty();
		given.forEach((item, count) -> {
			if (!list.getSiblings().isEmpty()) {
				list.append(", ");
			}
			list.append(Component.translatable("message.minecities.build.supplied_item", count,
					item.getName(new net.minecraft.world.item.ItemStack(item))));
		});
		return Component.translatable("message.minecities.build.supplied", list)
				.withStyle(net.minecraft.ChatFormatting.GREEN);
	}

	private static void act(ServerPlayer player, ActionPayload payload) {
		ServerLevel level = player.level();
		if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > REACH * REACH) {
			return;
		}
		if (payload.action().equals("refresh")) {
			// L'écran ouvert redemande l'état du poste (MC-72).
			open(level, payload.pos(), player);
			return;
		}
		if (payload.action().equals("pause")) {
			// Le maire (ou un adjoint) suspend ce chantier, ou le reprend (MC-84).
			Optional<Town> town = Towns.get(level.getServer()).at(level.dimension(), payload.pos());
			town.filter(t -> t.canManage(player.getUUID())).flatMap(t -> t.constructions().stream()
					.filter(c -> c.id().equals(payload.target()) && !c.done()).findFirst()).ifPresent(c -> {
				boolean paused = c.togglePause();
				Towns.get(level.getServer()).changed();
				player.sendSystemMessage(Component.translatable(paused ? "message.minecities.build.paused"
						: "message.minecities.build.resumed", site(level, c).name()).withStyle(net.minecraft.ChatFormatting.GOLD));
			});
			open(level, payload.pos(), player);
			return;
		}
		if (payload.action().equals("supply")) {
			// Le joueur dépose, de son inventaire, ce qui manque aux chantiers de la ville.
			Optional<Town> town = Towns.get(level.getServer()).at(level.dimension(), payload.pos());
			if (town.isPresent() && job(level, payload.pos(), level.getBlockState(payload.pos()))
					.filter(j -> j == Job.BUILDER).isPresent()) {
				player.sendSystemMessage(supplied(com.ajustor.minecities.build.Builds.supply(level, town.get(), player)));
			}
			open(level, payload.pos(), player);
			return;
		}
		if (payload.action().equals("close_mine")) {
			// Le maire (ou un adjoint) rebouche le puits épuisé de ce treuil.
			Optional<Town> town = Towns.get(level.getServer()).at(level.dimension(), payload.pos());
			Optional<com.ajustor.minecities.town.Mine> mine = town.flatMap(t -> t.mine(payload.pos()));
			if (town.isPresent() && mine.isPresent() && town.get().canManage(player.getUUID())) {
				Optional<String> refusal = com.ajustor.minecities.town.Mines.close(level, town.get(), mine.get());
				player.sendSystemMessage(Component.translatable(refusal.map(r -> "message.minecities.mine.close." + r)
						.orElse("message.minecities.mine.close.done")).withStyle(refusal.isPresent()
						? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GREEN));
			}
			open(level, payload.pos(), player);
			return;
		}
		if (payload.action().equals("posting")) {
			// Le maire (ou un adjoint) fait revenir le travailleur de ce poste.
			Optional<Town> town = Towns.get(level.getServer()).at(level.dimension(), payload.pos());
			if (town.isPresent() && town.get().canManage(player.getUUID()) && snapshot(level, payload.pos())
					.filter(s -> s.workers().stream().anyMatch(w -> w.id().equals(payload.target()))).isPresent()) {
				com.ajustor.minecities.town.Postings.Outcome outcome = com.ajustor.minecities.town.Postings.send(level,
						town.get(), payload.target());
				player.sendSystemMessage(outcome.message().copy().withStyle(outcome.sent()
						? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
			}
			return;
		}
		if (payload.action().equals("locate") && level.getEntity(payload.target()) instanceof CitizenEntity citizen
				&& snapshot(level, payload.pos()).filter(s -> s.workers().stream()
						.anyMatch(w -> w.id().equals(payload.target()))).isPresent()) {
			citizen.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
			player.sendSystemMessage(Component.translatable("message.minecities.locate", citizen.getName(),
					citizen.blockPosition().toShortString()));
		}
	}
}
