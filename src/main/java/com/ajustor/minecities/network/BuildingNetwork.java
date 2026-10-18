package com.ajustor.minecities.network;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.Builds;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.registry.McBlocks;
import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.HouseScanner;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * L'interface d'un bâtiment : elle s'ouvre d'un clic sur sa plaque (après le relevé) ou du bouton
 * « Détails » de l'onglet Bâtiments ; le maire et ses adjoints y commandent son amélioration.
 */
public final class BuildingNetwork {
	/** On agit sur le bâtiment de près : sa plaque, ou l'hôtel de ville. */
	private static final double REACH = 8.0;
	/** Une demande au plus par joueur tous les tant de ticks : chacune coûte un relevé complet. */
	private static final int COOLDOWN = 10;
	private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();
	/** Les matériaux montrés, les plus nombreux d'abord. */
	private static final int MATERIALS = 8;

	/** Le maire commande l'amélioration du bâtiment de cette plaque. */
	public record ActionPayload(BlockPos plaque, String action) implements CustomPacketPayload {
		public static final Type<ActionPayload> TYPE = new Type<>(MineCities.id("building_action"));
		public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, ActionPayload::plaque,
				ByteBufCodecs.STRING_UTF8, ActionPayload::action,
				ActionPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private BuildingNetwork() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(BuildingSnapshot.TYPE, BuildingSnapshot.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE,
				(payload, context) -> act(context.player(), payload));
	}

	/** Ouvre l'interface du bâtiment de cette plaque ; faux s'il n'est dans aucune ville. */
	public static boolean open(ServerLevel level, BlockPos plaque, ServerPlayer player) {
		Optional<BuildingSnapshot> snapshot = snapshot(level, plaque, player);
		snapshot.ifPresent(s -> ServerPlayNetworking.send(player, s));
		return snapshot.isPresent();
	}

	public static Optional<BuildingSnapshot> snapshot(ServerLevel level, BlockPos plaque, ServerPlayer player) {
		Optional<Town> found = Towns.get(level.getServer()).at(level.dimension(), plaque);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		Town town = found.get();
		// Seulement une plaque, ou une maison de la ville : pas n'importe quelle pièce close envoyée par
		// le client, qui deviendrait une maison sans que personne l'ait déclarée.
		if (town.house(plaque).isEmpty() && !level.getBlockState(plaque).is(McBlocks.HOUSE_PLAQUE)) {
			return Optional.empty();
		}
		// Un relevé frais : l'état du bâtiment tel qu'il est maintenant (et la ville à jour).
		HouseScanner.Result result = TownService.resurvey(level, town, plaque);
		Optional<House> house = town.house(plaque);
		String kind = house.map(h -> h.warehouse() ? "warehouse" : h.village() ? "village"
				: !h.beds().isEmpty() && !h.workstations().isEmpty() ? "both"
				: !h.beds().isEmpty() ? "home" : "workshop").orElse("home");
		int beds = house.map(h -> h.beds().size()).orElse(0);
		int sleepers = house.map(h -> (int) town.residents().values().stream()
				.filter(r -> r.bed().filter(h.beds()::contains).isPresent()).count()).orElse(0);
		List<String> jobs = house.map(h -> h.workstations().stream().map(w -> w.job().translationKey()).toList())
				.orElse(List.of());
		Component status = house.isPresent() ? Component.translatable("screen.minecities.building.registered")
				: Component.translatable(result.status().translationKey());
		// Un chantier en cours à sa place (une amélioration commandée).
		Optional<Construction> site = town.constructions().stream().filter(c -> !c.done() && c.contains(plaque))
				.findFirst();
		int placed = -1;
		int total = 0;
		if (site.isPresent()) {
			total = Builds.plan(level.getServer(), site.get()).size();
			placed = Math.min(site.get().step(), total);
		}
		// L'amélioration : le niveau suivant du schéma d'après lequel la ville l'a bâti.
		Optional<Component> next = Optional.empty();
		Optional<Component> refusal;
		List<BuildingSnapshot.Material> materials = new ArrayList<>();
		Optional<Construction> built = town.builtAt(plaque);
		boolean rebuild = built.isEmpty();
		if (built.isEmpty()) {
			// Fait à la main : il se rebâtit au niveau 2 de sa famille, à sa place.
			Optional<Blueprint> replacement = house.flatMap(h -> Builds.replacement(level.getServer(), h));
			if (house.isEmpty() || replacement.isEmpty()) {
				refusal = Optional.of(Component.translatable("screen.minecities.building.no_family"));
			} else {
				next = Optional.of(replacement.get().displayName());
				BlockPos origin = Builds.replacementOrigin(replacement.get(), house.get());
				refusal = site.isPresent() ? Optional.of(Component.translatable("screen.minecities.building.in_progress"))
						: Builds.check(level, town, replacement.get(), origin, net.minecraft.world.level.block.Rotation.NONE,
								house.get().min().flatMap(min -> house.get().max().map(max -> new BlockPos[]{min, max})));
				materials(level, town, BuildPlan.of(replacement.get(), com.ajustor.minecities.build.BuildStyle.PLAINS,
						origin, net.minecraft.world.level.block.Rotation.NONE), materials);
			}
		} else {
			Optional<Blueprint> upgrade = Blueprints.get(level.getServer(), built.get().blueprint())
					.flatMap(Blueprint::upgrade).flatMap(id -> Blueprints.get(level.getServer(), id));
			if (upgrade.isEmpty()) {
				refusal = Optional.of(Component.translatable("screen.minecities.building.top"));
			} else {
				next = Optional.of(upgrade.get().displayName());
				refusal = site.isPresent() ? Optional.of(Component.translatable("screen.minecities.building.in_progress"))
						: Builds.check(level, town, upgrade.get(), built.get().origin(), built.get().rotation(),
								Optional.of(new BlockPos[]{built.get().min(), built.get().max()}));
				materials(level, town, BuildPlan.of(upgrade.get(), built.get().style(), built.get().origin(),
						built.get().rotation()), materials);
			}
		}
		return Optional.of(new BuildingSnapshot(plaque, town.name(), kind, house.isPresent(), status,
				town.buildingLevel(plaque), beds, sleepers, jobs, placed, total, next, refusal, materials,
				town.canManage(player.getUUID()), rebuild));
	}

	/** Les matériaux d'un plan, les plus nombreux d'abord, face à ce que le grenier en a. */
	private static void materials(ServerLevel level, Town town, List<BuildPlan.Placement> plan,
			List<BuildingSnapshot.Material> out) {
		Map<Item, Integer> needs = BuildPlan.materials(plan);
		Optional<com.ajustor.minecities.block.TownHallBlockEntity> granary = TownService.granary(level, town);
		needs.entrySet().stream().sorted(Map.Entry.<Item, Integer>comparingByValue(Comparator.reverseOrder()))
				.limit(MATERIALS).forEach(e -> out.add(new BuildingSnapshot.Material(new ItemStack(e.getKey()),
						e.getValue(), granary.map(g -> Stock.count(g, s -> s.is(e.getKey()))).orElse(0))));
	}

	private static void act(ServerPlayer player, ActionPayload payload) {
		ServerLevel level = player.level();
		Optional<Town> town = Towns.get(level.getServer()).at(level.dimension(), payload.plaque());
		boolean near = player.distanceToSqr(Vec3.atCenterOf(payload.plaque())) <= REACH * REACH
				|| town.filter(t -> player.distanceToSqr(Vec3.atCenterOf(t.hall())) <= REACH * REACH).isPresent();
		if (town.isEmpty() || !near) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST_ACTION.put(player.getUUID(), now);
		if (last != null && now - last >= 0 && now - last < COOLDOWN) {
			return;
		}
		if (payload.action().equals("upgrade") && town.get().canManage(player.getUUID())) {
			Optional<Component> refusal = town.get().builtAt(payload.plaque()).isPresent()
					? Builds.upgrade(level, town.get(), payload.plaque()) : Builds.rebuild(level, town.get(), payload.plaque());
			player.sendSystemMessage(refusal.map(r -> r.copy().withStyle(ChatFormatting.RED))
					.orElse(Component.translatable("message.minecities.building.upgrading").withStyle(ChatFormatting.GREEN)));
		}
		open(level, payload.plaque(), player);
	}
}
