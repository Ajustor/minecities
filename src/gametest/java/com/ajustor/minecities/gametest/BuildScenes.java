package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Builds;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
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
import static com.ajustor.minecities.gametest.CityScenes.look;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Le bâtisseur, dans le vrai jeu : un atelier à la table d'artisan, une petite maison commandée
 * (style plaines) avec ses matériaux au grenier ; le bâtisseur la bâtit, elle se déclare toute
 * seule ; puis on l'agrandit en grande maison, au même endroit.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=build}
 */
public class BuildScenes implements FabricClientGameTest {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos HUT_PLAQUE = new BlockPos(5, Y + 1, 4);
	/** Le coin nord-ouest de la maison commandée. */
	private static final BlockPos ORIGIN = new BlockPos(-14, Y, 6);
	private static final Identifier SMALL = Identifier.fromNamespaceAndPath("minecities", "house_small");
	private static final Identifier LARGE = Identifier.fromNamespaceAndPath("minecities", "house_large");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!CityScenes.selected("build")) {
			return;
		}
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			command(sp, "time set 1000");
			command(sp, "gamerule doDaylightCycle false");
			command(sp, "gamerule doMobSpawning false");
			command(sp, "weather clear");
			context.waitTicks(20);
			sp.getServer().runOnServer(BuildScenes::build);
			context.runOnClient(mc -> {
				hideGui(mc, true);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});

			check(sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
				Town town = town(server);
				TownService.immigrate(level, town);
				TownService.declareHouse(level, HUT_PLAQUE, player);
				if (town.workers(Job.BUILDER) != 1) {
					return "pas de bâtisseur : " + town.residents() + " ; " + town.houses();
				}
				stock(server, SMALL);
				return Builds.order(level, town, blueprint(server, SMALL), BuildStyle.PLAINS, ORIGIN, Rotation.NONE)
						.map(c -> "commande refusée : " + c.getString()).orElse(null);
			}));
			look(sp, -16, Y + 9, -2, -11, Y + 1, 9);
			context.waitTicks(600);
			context.takeScreenshot("build_0_in_progress");
			waitOrExplain(sp, server -> town(server).constructions().stream().allMatch(Construction::done), 9000,
					"la petite maison n'a pas été bâtie", BuildScenes::progress);
			check(sp.getServer().computeOnServer(server -> homeBeds(server) == 2 ? null
					: "la petite maison n'est pas déclarée avec deux lits : " + town(server).houses()));
			context.waitTicks(20);
			context.takeScreenshot("build_1_small_house");

			// L'agrandir : la grande maison, au même endroit.
			check(sp.getServer().computeOnServer(server -> {
				stock(server, LARGE);
				return Builds.upgrade(server.overworld(), town(server), ORIGIN.offset(3, 1, 3))
						.map(c -> "agrandissement refusé : " + c.getString()).orElse(null);
			}));
			waitOrExplain(sp, server -> town(server).constructions().stream().allMatch(Construction::done), 9000,
					"la grande maison n'a pas été bâtie", BuildScenes::progress);
			check(sp.getServer().computeOnServer(server -> homeBeds(server) == 4 ? null
					: "la grande maison n'est pas déclarée avec quatre lits : " + town(server).houses()));
			look(sp, -16, Y + 9, -2, -10, Y + 1, 9);
			context.waitTicks(20);
			context.takeScreenshot("build_2_large_house");
		}
	}

	private static String progress(MinecraftServer server) {
		return town(server).activeConstruction().map(c -> "chantier " + c.blueprint() + " à l'étape " + c.step()
				+ ".").orElse("aucun chantier.");
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static Blueprint blueprint(MinecraftServer server, Identifier id) {
		return Blueprints.get(server, id).orElseThrow(() -> new AssertionError("schéma introuvable : " + id));
	}

	/** Les lits de la maison bâtie (sa plaque est à l'intérieur du mur nord). */
	private static int homeBeds(MinecraftServer server) {
		return town(server).houses().stream().filter(h -> h.plaque().equals(ORIGIN.offset(2, 2, 1)))
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
		for (Map.Entry<Item, Integer> e : needs.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int count = Math.min(left, new ItemStack(e.getKey()).getMaxStackSize());
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
		// L'atelier du bâtisseur, bâti à la main : une table d'artisan.
		WorkScenes.room(level, 4, 4, 8, 8, 6);
		command(server, "setblock 8 %d 6 minecraft:crafter".formatted(Y));
		command(server, "setblock 5 %d 4 minecities:house_plaque[facing=south]".formatted(Y + 1));
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		command(server, "gamemode spectator @p");
	}
}
