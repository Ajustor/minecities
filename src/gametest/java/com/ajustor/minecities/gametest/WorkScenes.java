package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Resident;
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
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.look;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Les métiers, dans le vrai jeu : une maison de deux lits, un atelier avec un composteur, un fumoir
 * et un coffre, et un champ mûr à côté. Les deux citoyens sont embauchés ; le fermier attend sa
 * houe, puis récolte et replante le champ et porte la récolte au grenier ; le cuisinier cuit la
 * viande crue du grenier et fait du pain avec le blé.
 *
 * <p>{@code ./gradlew runClientGameTest -Dminecities.gametest=work}
 */
public class WorkScenes implements FabricClientGameTest {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos HOUSE_PLAQUE = new BlockPos(-8, Y + 1, 4);
	private static final BlockPos WORKSHOP_PLAQUE = new BlockPos(5, Y + 1, 4);
	private static final BlockPos WORKSHOP_CHEST = new BlockPos(8, Y, 8);
	private static final BlockPos SMOKER = new BlockPos(4, Y, 8);
	/** Le champ : [10, 14] x [-6, -2], l'eau au centre. */
	private static final int FIELD_X0 = 10, FIELD_X1 = 14, FIELD_Z0 = -6, FIELD_Z1 = -2;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!CityScenes.selected("work")) {
			return;
		}
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			command(sp, "time set 1000");
			command(sp, "gamerule doDaylightCycle false");
			command(sp, "gamerule doMobSpawning false");
			command(sp, "weather clear");
			context.waitTicks(20);
			sp.getServer().runOnServer(WorkScenes::build);
			context.runOnClient(mc -> {
				hideGui(mc, true);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});

			// Fondation, maison, deux citoyens, puis l'atelier : chacun y trouve un emploi.
			check(sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
				TownService.declareHouse(level, HOUSE_PLAQUE, player);
				Town town = town(server);
				TownService.immigrate(level, town);
				TownService.immigrate(level, town);
				TownService.declareHouse(level, WORKSHOP_PLAQUE, player);
				long farmers = town.residents().values().stream().filter(r -> hasJob(r, Job.FARMER)).count();
				long cooks = town.residents().values().stream().filter(r -> hasJob(r, Job.COOK)).count();
				return farmers == 1 && cooks == 1 ? null
						: "emplois mal attribués : " + town.residents() + " ; bâtiments : " + town.houses();
			}));
			look(sp, 6, Y + 9, -14, 9, Y, -1);
			context.waitTicks(40);
			context.takeScreenshot("work_0_field_ripe");

			// Sans houe, le fermier ne travaille pas.
			context.waitTicks(200);
			check(sp.getServer().computeOnServer(server -> {
				Optional<CitizenEntity> farmer = farmer(server);
				if (farmer.isEmpty()) {
					return "pas de fermier : " + CityScenes.census(server);
				}
				return farmer.get().getMainHandItem().isEmpty() ? null : "le fermier a trouvé une houe qui n'existe pas";
			}));

			// Une houe dans le coffre de l'atelier : il la prend, récolte, replante et livre.
			sp.getServer().runOnServer(server -> {
				if (server.overworld().getBlockEntity(WORKSHOP_CHEST) instanceof Container chest) {
					chest.setItem(0, new ItemStack(Items.IRON_HOE));
				}
			});
			waitOrExplain(sp, server -> farmer(server).map(f -> f.getMainHandItem().is(Items.IRON_HOE)).orElse(false),
					1200, "le fermier n'a pas pris la houe");
			context.waitTicks(200);
			context.takeScreenshot("work_1_farmer_at_work");
			waitOrExplain(sp, server -> ripeCrops(server) == 0, 2400, "le champ n'a pas été récolté");
			check(sp.getServer().computeOnServer(server -> youngCrops(server) >= 20 ? null
					: "le champ n'a pas été replanté : " + youngCrops(server) + " jeunes pousses"));
			waitOrExplain(sp, server -> granary(server, Items.CARROT) > 0 || granary(server, Items.WHEAT) > 0
					|| granary(server, Items.BREAD) > 16, 2400, "la récolte n'est pas arrivée au grenier");
			look(sp, 6, Y + 9, -14, 9, Y, -1);
			context.waitTicks(20);
			context.takeScreenshot("work_2_field_replanted");

			// Le cuisinier : la viande crue du grenier revient cuite, le blé en pain.
			waitOrExplain(sp, server -> granary(server, Items.COOKED_BEEF) > 0, 2400,
					"le cuisinier n'a pas cuit la viande");
			waitOrExplain(sp, server -> granary(server, Items.BREAD) > 16, 3600, "le cuisinier n'a pas fait de pain");
			look(sp, 6, Y + 2, 5.2, 4.5, Y + 0.5, 8);
			context.waitTicks(10);
			context.takeScreenshot("work_3_kitchen");
		}
	}

	private static boolean hasJob(Resident resident, Job job) {
		return resident.job().filter(j -> j.job() == job).isPresent();
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static Optional<CitizenEntity> farmer(MinecraftServer server) {
		return citizens(server).stream().filter(c -> c.job().filter(j -> j.job() == Job.FARMER).isPresent()).findFirst();
	}

	private static int granary(MinecraftServer server, Item item) {
		return server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall
				? Stock.count(hall, s -> s.is(item)) : 0;
	}

	private static int ripeCrops(MinecraftServer server) {
		return crops(server, true);
	}

	private static int youngCrops(MinecraftServer server) {
		return crops(server, false);
	}

	private static int crops(MinecraftServer server, boolean ripe) {
		int count = 0;
		for (int x = FIELD_X0; x <= FIELD_X1; x++) {
			for (int z = FIELD_Z0; z <= FIELD_Z1; z++) {
				BlockState state = server.overworld().getBlockState(new BlockPos(x, Y, z));
				if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) == ripe) {
					count++;
				}
			}
		}
		return count;
	}

	/** Une pièce close de planches, porte au nord, intérieur [x0, x1] x [Y, Y + 2] x [z0, z1]. */
	private static void room(ServerLevel level, int x0, int z0, int x1, int z1, int doorX) {
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				for (int y = Y - 1; y <= Y + 3; y++) {
					boolean inside = x >= x0 && x <= x1 && z >= z0 && z <= z1 && y >= Y && y <= Y + 2;
					if (!inside) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), y == Y + 3 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
								: Blocks.OAK_PLANKS.defaultBlockState());
					}
				}
			}
		}
		command(level.getServer(), "setblock %d %d %d minecraft:oak_door[facing=south,half=lower]".formatted(doorX, Y, z0 - 1));
		command(level.getServer(), "setblock %d %d %d minecraft:oak_door[facing=south,half=upper]".formatted(doorX, Y + 1, z0 - 1));
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -20; x <= 20; x++) {
			for (int z = -20; z <= 20; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 8; y++) {
					level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		// La maison : deux lits.
		room(level, -9, 4, -6, 8, -7);
		for (int x : new int[]{-9, -6}) {
			command(server, "setblock %d %d 7 minecraft:white_bed[facing=south,part=foot]".formatted(x, Y));
			command(server, "setblock %d %d 8 minecraft:white_bed[facing=south,part=head]".formatted(x, Y));
		}
		command(server, "setblock -8 %d 4 minecities:house_plaque[facing=south]".formatted(Y + 1));
		// L'atelier : un composteur, un fumoir, un coffre.
		room(level, 4, 4, 8, 8, 6);
		command(server, "setblock 8 %d 4 minecraft:composter".formatted(Y));
		command(server, "setblock 4 %d 8 minecraft:smoker[facing=north]".formatted(Y));
		command(server, "setblock 8 %d 8 minecraft:chest[facing=west]".formatted(Y));
		command(server, "setblock 5 %d 4 minecities:house_plaque[facing=south]".formatted(Y + 1));
		// Le champ : blé et carottes mûrs autour d'un point d'eau.
		for (int x = FIELD_X0; x <= FIELD_X1; x++) {
			for (int z = FIELD_Z0; z <= FIELD_Z1; z++) {
				BlockPos ground = new BlockPos(x, Y - 1, z);
				if (x == 12 && z == -4) {
					level.setBlockAndUpdate(ground, Blocks.WATER.defaultBlockState());
					continue;
				}
				level.setBlockAndUpdate(ground, Blocks.FARMLAND.defaultBlockState());
				CropBlock crop = (CropBlock) (x < 12 ? Blocks.WHEAT : Blocks.CARROTS);
				level.setBlockAndUpdate(ground.above(), crop.getStateForAge(crop.getMaxAge()));
			}
		}
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			hall.setItem(0, new ItemStack(Items.BREAD, 16));
			hall.setItem(1, new ItemStack(Items.BEEF, 6));
		}
		command(server, "gamemode spectator @p");
	}
}
