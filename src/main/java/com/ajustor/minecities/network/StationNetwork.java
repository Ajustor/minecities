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
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE,
				(payload, context) -> act(context.player(), payload));
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
		Optional<StationSnapshot> snapshot = snapshot(level, pos);
		snapshot.ifPresent(s -> ServerPlayNetworking.send(player, s));
		return snapshot.isPresent();
	}

	public static Optional<StationSnapshot> snapshot(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Optional<Job> found = job(level, pos, state);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		Job job = found.get();
		Optional<Town> town = Towns.get(level.getServer()).at(level.dimension(), pos);
		List<StationSnapshot.Worker> workers = new ArrayList<>();
		List<StationSnapshot.Site> sites = new ArrayList<>();
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
				status = workers.isEmpty() ? "vacant" : "ok";
			}
			if (job == Job.BUILDER) {
				for (Construction c : t.constructions()) {
					if (!c.done()) {
						sites.add(site(level, c));
					}
				}
			}
		}
		return Optional.of(new StationSnapshot(pos, state.getBlock().getDescriptionId(), job.serializedName(), town.map(Town::name).orElse(""), status,
				detail, workers, sites));
	}

	/** Un travailleur : ce qu'il fait, d'après ce qu'on voit de lui. */
	private static StationSnapshot.Worker worker(ServerLevel level, UUID id, Resident resident) {
		if (!(level.getEntity(id) instanceof CitizenEntity citizen)) {
			return new StationSnapshot.Worker(id, resident.name(), "away", Optional.empty());
		}
		String state = citizen.isSleeping() ? "sleeping" : citizen.illness().isPresent() ? "sick"
				: citizen.hunger() < Hunger.HUNGRY ? "hungry" : "working";
		return new StationSnapshot.Worker(id, resident.name(), state, citizen.lastNeed());
	}

	/** Un chantier : son nom, et combien de blocs de son plan sont posés. */
	private static StationSnapshot.Site site(ServerLevel level, Construction c) {
		Optional<com.ajustor.minecities.build.Blueprint> blueprint = Blueprints.get(level.getServer(), c.blueprint());
		int total = com.ajustor.minecities.build.Builds.plan(level.getServer(), c).size();
		Component name = blueprint.<Component>map(com.ajustor.minecities.build.Blueprint::displayName)
				.orElse(Component.literal(c.blueprint().getPath()));
		return new StationSnapshot.Site(name, c.origin(), Math.min(c.step(), total), total);
	}

	private static void act(ServerPlayer player, ActionPayload payload) {
		ServerLevel level = player.level();
		if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > REACH * REACH) {
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
