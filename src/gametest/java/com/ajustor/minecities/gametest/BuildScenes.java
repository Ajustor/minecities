package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Builds;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.Map;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Le bâtisseur, dans le vrai jeu : un atelier à quatre tables d'artisan et deux tables de cartographie
 * (quatre bâtisseurs et deux livreurs), une petite maison commandée
 * (style plaines), adossée à une butte, avec ses matériaux au grenier ; le bâtisseur la bâtit, elle se
 * déclare toute seule ; puis on l'agrandit en grande maison, au même endroit (son haut, du haut de ses
 * échafaudages).
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=build}
 */
public class BuildScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos HUT_PLAQUE = new BlockPos(5, Y + 1, 4);
	/** Le coin nord-ouest de la maison commandée. */
	private static final BlockPos ORIGIN = new BlockPos(-14, Y, 6);
	/** La maison, de niveau en niveau (brief 11) : cabane, maison, bâtisse. */
	private static final Identifier SMALL = Identifier.fromNamespaceAndPath("minecities", "house_1");
	private static final Identifier LARGE = Identifier.fromNamespaceAndPath("minecities", "house_2");
	private static final Identifier MANOR = Identifier.fromNamespaceAndPath("minecities", "house_3");

	@Override
	public String name() {
		return "build";
	}

	@Override
	public void play(Stage stage) {
		// Cette scène compte ses citoyens : pas de nouveaux venus pour atteindre le minimum.
		com.ajustor.minecities.town.Families.setMinimum(0);
		walled.clear();
		scaffolded = false;
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.waitTicks(20);
		stage.run(BuildScenes::build);
		stage.client(mc -> {
			hideGui(mc, true);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});

		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			for (int i = 0; i < 6; i++) {
				TownService.immigrate(level, town);
			}
			TownService.declareHouse(level, HUT_PLAQUE, player);
			if (town.workers(Job.BUILDER) != 4 || town.workers(Job.COURIER) != 2) {
				return "il faut quatre bâtisseurs et deux livreurs : " + town.residents() + " ; " + town.houses();
			}
			stock(server, SMALL);
			return Builds.order(level, town, blueprint(server, SMALL), BuildStyle.PLAINS, ORIGIN, Rotation.NONE)
					.map(c -> "commande refusée : " + c.getString()).orElse(null);
		}));
		stage.look(-16, Y + 9, -2, -11, Y + 1, 9);
		stage.waitTicks(40);
		// Le chantier filmé : une image par seconde de jeu.
		stage.film("petite_maison", 240, 20, BuildScenes::built);
		waitOrExplain(stage, BuildScenes::built, 9000, "la petite maison n'a pas été bâtie", BuildScenes::progress);
		check(walled.isEmpty() ? null : "des citoyens se sont retrouvés dans un mur pendant le chantier : " + walled);
		check(stage.compute(server -> homeBeds(server) == 2 ? null
				: "la petite maison n'est pas déclarée avec deux lits : " + town(server).houses()));
		stage.waitTicks(20);
		stage.shot("build_1_small_house");

		// L'agrandir : la grande maison, au même endroit.
		check(stage.compute(server -> {
			stock(server, LARGE);
			return Builds.upgrade(server.overworld(), town(server), ORIGIN.offset(3, 1, 3))
					.map(c -> "agrandissement refusé : " + c.getString()).orElse(null);
		}));
		waitOrExplain(stage, BuildScenes::built, 15000, "la grande maison n'a pas été bâtie", BuildScenes::progress);
		check(walled.isEmpty() ? null : "des citoyens se sont retrouvés dans un mur pendant le chantier : " + walled);
		// Son toit monte bien au-delà de ce qu'on atteint depuis le sol.
		check(scaffolded ? null : "les bâtisseurs n'ont dressé aucun échafaudage pour le haut de la grande maison");
		check(stage.compute(server -> homeBeds(server) == 4 ? null
				: "la grande maison n'est pas déclarée avec quatre lits : " + town(server).houses()));
		stage.look(-16, Y + 9, -2, -10, Y + 1, 9);
		stage.waitTicks(20);
		stage.shot("build_2_large_house");

		// Puis la bâtisse (niveau 3, dès la ville) : pierre taillée, briques, fer, six lits.
		check(stage.compute(server -> {
			town(server).forceTier(java.util.Optional.of(com.ajustor.minecities.town.TownTier.TOWN));
			town(server).updateTier();
			stock(server, MANOR);
			return Builds.upgrade(server.overworld(), town(server), ORIGIN.offset(3, 1, 3))
					.map(c -> "bâtisse refusée : " + c.getString()).orElse(null);
		}));
		stage.look(-19, Y + 8, -3, -10, Y + 3, 9);
		stage.film("manoir", 300, 20, BuildScenes::built);
		waitOrExplain(stage, BuildScenes::built, 15000, "la bâtisse n'a pas été bâtie", BuildScenes::progress);
		check(walled.isEmpty() ? null : "des citoyens se sont retrouvés dans un mur pendant le chantier : " + walled);
		check(stage.compute(server -> homeBeds(server) == 6
				&& server.overworld().getBlockState(ORIGIN.offset(1, 0, 1)).is(Blocks.STONE_BRICKS)
				&& server.overworld().getBlockState(ORIGIN.offset(2, 1, 1)).is(Blocks.BRICKS)
				&& server.overworld().getBlockState(ORIGIN.offset(4, 0, 0)).is(Blocks.COBBLESTONE_STAIRS)
				&& server.overworld().getBlockState(ORIGIN.offset(0, 3, 6)).is(Blocks.BRICKS) ? null
				: "la bâtisse n'est pas conforme : " + town(server).houses()));
		stage.look(-18, Y + 7, -3, -10, Y + 3, 9);
		stage.waitTicks(20);
		stage.shot("build_3_manor");
	}

	/** Les citoyens surpris dans un mur pendant le chantier ; et si un échafaudage a été dressé. */
	private static final java.util.Set<String> walled = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static volatile boolean scaffolded;

	/** Le chantier est-il fini ? En passant, on note qui est dans un mur et si les échafaudages sont dressés. */
	private static boolean built(MinecraftServer server) {
		for (CitizenEntity c : CityScenes.citizens(server)) {
			if (c.isInWall()) {
				walled.add(c.getName().getString() + " en " + c.blockPosition().toShortString());
			}
		}
		if (town(server).constructions().stream().anyMatch(c -> !c.scaffolding().isEmpty())) {
			scaffolded = true;
		}
		return town(server).constructions().stream().allMatch(Construction::done);
	}

	private static String progress(MinecraftServer server) {
		return town(server).activeConstruction().map(c -> {
			var plan = BuildPlan.of(blueprint(server, c.blueprint()), c.style(), c.origin(), c.rotation());
			if (c.step() >= plan.size()) {
				return "chantier " + c.blueprint() + " fini mais pas clos.";
			}
			var p = plan.get(c.step());
			return "chantier " + c.blueprint() + " à l'étape " + c.step() + "/" + plan.size() + " : " + p.state()
					+ " en " + p.pos().toShortString() + ", où se trouve " + server.overworld().getBlockState(p.pos()) + ". "
					+ BuildPlan.material(p.state()).map(m -> ledger(server, plan, m)).orElse("");
		}).orElse("aucun chantier.");
	}

	/** Où en est ce matériau : au plan, posé, au grenier, dans les poches, par terre. */
	private static String ledger(MinecraftServer server, java.util.List<BuildPlan.Placement> plan, Item item) {
		ServerLevel level = server.overworld();
		long needed = plan.stream().filter(p -> BuildPlan.material(p.state()).filter(item::equals).isPresent()).count();
		long placed = plan.stream().filter(p -> BuildPlan.material(p.state()).filter(item::equals).isPresent()
				&& level.getBlockState(p.pos()).is(p.state().getBlock())).count();
		long elsewhere = 0;
		for (BlockPos pos : BlockPos.betweenClosed(ORIGIN.offset(-6, 0, -6), ORIGIN.offset(16, 12, 16))) {
			if (level.getBlockState(pos).getBlock().asItem() == item) {
				elsewhere++;
			}
		}
		int granary = level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall
				? com.ajustor.minecities.town.Stock.count(hall, s -> s.is(item)) : -1;
		int pockets = CityScenes.citizens(server).stream().mapToInt(c -> c.inventory().stream().filter(s -> s.is(item))
				.mapToInt(ItemStack::getCount).sum() + (c.getMainHandItem().is(item) ? c.getMainHandItem().getCount() : 0)).sum();
		int ground = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(-64, 0, -64, 64, 200, 64), e -> e.getItem().is(item)).stream()
				.mapToInt(e -> e.getItem().getCount()).sum();
		String content = level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall
				? java.util.stream.IntStream.range(0, hall.getContainerSize()).mapToObj(hall::getItem)
						.filter(s -> !s.isEmpty()).map(ItemStack::toString).toList().toString() : "?";
		return "%s : %d au plan, %d posés à leur place, %d de ce bloc alentour, %d au grenier, %d en poche, %d par terre. Grenier : %s."
				.formatted(item, needed, placed, elsewhere, granary, pockets, ground, content);
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static Blueprint blueprint(MinecraftServer server, Identifier id) {
		return Blueprints.get(server, id).orElseThrow(() -> new AssertionError("schéma introuvable : " + id));
	}

	/** Les lits de la maison bâtie (sa plaque est à l'intérieur du mur nord, derrière le perron). */
	private static int homeBeds(MinecraftServer server) {
		return town(server).houses().stream().filter(h -> h.plaque().equals(ORIGIN.offset(3, 2, 2)))
				.mapToInt(House::capacity).sum();
	}

	/** Remplit le grenier des matériaux du schéma (et de pain pour le bâtisseur). */
	private static void stock(MinecraftServer server, Identifier id) {
		if (!(server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall)) {
			throw new AssertionError("pas de grenier");
		}
		hall.clearContent();
		Map<Item, Integer> needs = BuildPlan.materials(BuildPlan.of(blueprint(server, id), BuildStyle.PLAINS, ORIGIN,
				Rotation.NONE));
		int slot = 0;
		hall.setItem(slot++, new ItemStack(Items.BREAD, 32));
		// Les outils pour dégager : la pioche pour la pierre, la pelle pour la terre, la hache pour le bois.
		// Un jeu d'outils par bâtisseur.
		for (int i = 0; i < 4; i++) {
			hall.setItem(slot++, new ItemStack(Items.IRON_PICKAXE));
			hall.setItem(slot++, new ItemStack(Items.IRON_SHOVEL));
			hall.setItem(slot++, new ItemStack(Items.IRON_AXE));
		}
		hall.setItem(slot++, new ItemStack(Items.SCAFFOLDING, 32));
		// De quoi payer le chantier (MC-57 : à partir du niveau 2).
		hall.setItem(slot++, new ItemStack(Items.EMERALD, 32));
		for (Map.Entry<Item, Integer> e : needs.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int count = Math.min(left, new ItemStack(e.getKey()).getMaxStackSize());
				if (slot >= hall.getContainerSize()) {
					throw new AssertionError("le grenier est trop petit pour les matériaux de " + id);
				}
				hall.setItem(slot++, new ItemStack(e.getKey(), count));
				left -= count;
			}
		}
		hall.setChanged();
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -24; x <= 16; x++) {
			for (int z = -12; z <= 24; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 10; y++) {
					level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		// Un peu d'herbe et un rocher sur le terrain : le bâtisseur doit dégager.
		command(server, "fill -12 %d 8 -10 %d 9 minecraft:short_grass".formatted(Y, Y));
		command(server, "setblock -9 %d 10 minecraft:mossy_cobblestone".formatted(Y));
		command(server, "setblock -11 %d 11 minecraft:dirt".formatted(Y));
		// Une butte contre le côté ouest du chantier, en pente douce : le bâtisseur s'y tient pour bâtir
		// ce côté-là, sans y être remis « dehors » dans la terre, à y étouffer.
		for (int x = -19; x <= -15; x++) {
			for (int z = -2; z <= 18; z++) {
				int height = Math.min(4, Math.min(-x - 13, z + 2));
				for (int y = Y; y < Y + height; y++) {
					level.setBlockAndUpdate(new BlockPos(x, y, z), (y == Y + height - 1 ? Blocks.GRASS_BLOCK
							: Blocks.DIRT).defaultBlockState());
				}
			}
		}
		// Une vache au milieu du terrain : le bâtisseur doit la faire sortir pour bâtir.
		command(server, "summon minecraft:cow -11 %d 9".formatted(Y));
		// L'atelier du bâtisseur, bâti à la main : une table d'artisan.
		WorkScenes.room(level, 4, 4, 8, 8, 6);
		// Quatre tables d'artisan : quatre bâtisseurs, qui partagent le chantier.
		for (int z = 5; z <= 8; z++) {
			command(server, "setblock 8 %d %d minecraft:crafter".formatted(Y, z));
		}
		// Deux tables de cartographie : deux livreurs, qui leur portent les matériaux.
		command(server, "setblock 4 %d 6 minecraft:cartography_table".formatted(Y));
		command(server, "setblock 4 %d 7 minecraft:cartography_table".formatted(Y));
		command(server, "setblock 5 %d 4 minecities:house_plaque[facing=south]".formatted(Y + 1));
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		command(server, "gamemode spectator @p");
	}
}
