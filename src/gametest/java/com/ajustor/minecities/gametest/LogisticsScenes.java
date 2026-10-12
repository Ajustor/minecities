package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.look;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * L'entrepôt et la logistique (v1.4), dans le vrai jeu. Sans livreur, le charpentier va lui-même
 * chercher ses bûches à l'entrepôt et y rapporte ses planches. Avec un livreur, le maçon laisse ses
 * briques dans le coffre de son atelier, et le livreur les porte à l'entrepôt.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=logistics}
 */
public class LogisticsScenes implements FabricClientGameTest {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos WAREHOUSE = new BlockPos(-22, Y, -18);
	private static final BlockPos CARPENTRY = new BlockPos(-8, Y, -18);
	private static final BlockPos MASONRY = new BlockPos(4, Y, -18);
	private static final BlockPos COURIER_HUT = new BlockPos(16, Y, -18);
	private static final BlockPos HOUSE = new BlockPos(-6, Y, 10);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!CityScenes.selected("logistics")) {
			return;
		}
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			Families.setMinimum(0);
			command(sp, "time set 1000");
			command(sp, "gamerule doDaylightCycle false");
			command(sp, "gamerule doMobSpawning false");
			command(sp, "weather clear");
			context.waitTicks(20);
			sp.getServer().runOnServer(LogisticsScenes::build);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// Sans livreur : un entrepôt (avec des bûches), un charpentier.
			check(sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
				Town town = town(server);
				for (int i = 0; i < 2; i++) {
					TownService.immigrate(level, town);
				}
				for (BlockPos origin : new BlockPos[]{HOUSE, WAREHOUSE, CARPENTRY}) {
					RaidScenes.plaque(level, origin).ifPresent(p -> TownService.declareHouse(level, p, player));
				}
				if (town.warehouseContainers().isEmpty()) {
					return "pas d'entrepôt : " + town.houses();
				}
				if (level.getBlockEntity(town.warehouseContainers().getFirst()) instanceof Container chest) {
					chest.setItem(0, new ItemStack(Items.OAK_LOG, 16));
				}
				return town.workers(Job.CARPENTER) == 1 ? null : "pas de charpentier : " + town.residents();
			}));
			waitOrExplain(sp, server -> warehouse(server, Items.OAK_PLANKS) >= 32, 3600,
					"le charpentier n'a pas rapporté de planches à l'entrepôt",
					server -> "planches à l'entrepôt " + warehouse(server, Items.OAK_PLANKS) + ", au grenier "
							+ granary(server, Items.OAK_PLANKS) + ".");
			check(sp.getServer().computeOnServer(server -> granary(server, Items.OAK_PLANKS) == 0 ? null
					: "les planches sont allées au grenier, pas à l'entrepôt"));
			look(sp, WAREHOUSE.getX() + 5.5, Y + 2.6, WAREHOUSE.getZ() + 2.5, WAREHOUSE.getX() + 5, Y + 1, WAREHOUSE.getZ() + 7);
			context.runOnClient(mc -> hideGui(mc, true));
			context.waitTicks(20);
			context.takeScreenshot("logistics_0_warehouse");

			// Avec un livreur : le maçon laisse ses briques à l'atelier, le livreur les porte à l'entrepôt.
			check(sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				Town town = town(server);
				for (int i = 0; i < 2; i++) {
					TownService.immigrate(level, town);
				}
				for (BlockPos origin : new BlockPos[]{MASONRY, COURIER_HUT}) {
					RaidScenes.plaque(level, origin).ifPresent(p -> TownService.declareHouse(level, p, player));
				}
				if (level.getBlockEntity(town.warehouseContainers().getFirst()) instanceof Container chest) {
					chest.setItem(1, new ItemStack(Items.CLAY_BALL, 16));
				}
				return town.workers(Job.MASON) == 1 && town.workers(Job.COURIER) == 1 ? null
						: "maçon " + town.workers(Job.MASON) + ", livreur " + town.workers(Job.COURIER) + " ; "
								+ town.houses();
			}));
			look(sp, 4, Y + 14, -2, -6, Y, -14);
			context.waitTicks(10);
			CityScenes.film(context, sp, "logistics", 40, 20, server -> warehouse(server, Items.BRICKS) >= 2);
			waitOrExplain(sp, server -> warehouse(server, Items.BRICKS) >= 2, 4800,
					"le livreur n'a pas porté les briques à l'entrepôt",
					server -> "briques à l'entrepôt " + warehouse(server, Items.BRICKS) + ", à la taillerie "
							+ masonry(server, Items.BRICKS) + ", argile à la taillerie " + masonry(server, Items.CLAY_BALL)
							+ ".");
		}
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static int count(MinecraftServer server, java.util.List<BlockPos> containers, Item item) {
		int total = 0;
		for (BlockPos pos : containers) {
			if (server.overworld().getBlockEntity(pos) instanceof Container c) {
				total += Stock.count(c, s -> s.is(item));
			}
		}
		return total;
	}

	private static int warehouse(MinecraftServer server, Item item) {
		return count(server, town(server).warehouseContainers(), item);
	}

	private static int granary(MinecraftServer server, Item item) {
		return count(server, java.util.List.of(HALL), item);
	}

	private static int masonry(MinecraftServer server, Item item) {
		return RaidScenes.plaque(server.overworld(), MASONRY).flatMap(p -> town(server).house(p))
				.map(h -> count(server, h.containers(), item)).orElse(-1);
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -30; x <= 30; x++) {
			for (int z = -24; z <= 24; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 14; y++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		RaidScenes.paste(level, "warehouse_1", WAREHOUSE);
		RaidScenes.paste(level, "carpentry_1", CARPENTRY);
		RaidScenes.paste(level, "masonry_1", MASONRY);
		RaidScenes.paste(level, "builder_hut_1", COURIER_HUT);
		RaidScenes.paste(level, "house_2", HOUSE);
		// La hutte du livreur : une table de cartographie à la place de la table d'artisan.
		for (BlockPos pos : BlockPos.betweenClosed(COURIER_HUT, COURIER_HUT.offset(10, 3, 10))) {
			if (level.getBlockState(pos).is(Blocks.CRAFTER)) {
				level.setBlockAndUpdate(pos, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
			}
		}
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			hall.setItem(0, new ItemStack(Items.COOKED_BEEF, 32));
			hall.setItem(1, new ItemStack(Items.BREAD, 32));
		}
		command(server, "gamemode spectator @p");
	}
}
