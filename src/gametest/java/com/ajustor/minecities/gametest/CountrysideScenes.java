package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.registry.McItems;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.look;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Les métiers de la campagne, dans le vrai jeu : un bûcheron au billot (une bûche écorcée posée au
 * sol) abat les chênes voisins, un éleveur à la botte de foin fait naître des veaux, un pêcheur au
 * baril, au bord d'un étang, pêche. Tout finit au grenier.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=countryside}
 */
public class CountrysideScenes implements FabricClientGameTest {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos HOUSE_PLAQUE = new BlockPos(-11, Y + 1, 4);
	private static final BlockPos LUMBER_PLAQUE = new BlockPos(5, Y + 1, 4);
	private static final BlockPos BARN_PLAQUE = new BlockPos(-11, Y + 1, -12);
	private static final BlockPos FISHERY_PLAQUE = new BlockPos(5, Y + 1, -12);
	private static final BlockPos[] TREES = {new BlockPos(14, Y, 10), new BlockPos(19, Y, 4), new BlockPos(14, Y, 16)};
	private static final AABB PASTURE = new AABB(-26, Y - 2, -30, 6, Y + 4, 2);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!CityScenes.selected("countryside")) {
			return;
		}
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			command(sp, "time set 1000");
			command(sp, "gamerule doDaylightCycle false");
			command(sp, "gamerule doMobSpawning false");
			command(sp, "weather clear");
			context.waitTicks(20);
			sp.getServer().runOnServer(CountrysideScenes::build);
			context.runOnClient(mc -> {
				hideGui(mc, true);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});

			check(sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
				TownService.declareHouse(level, HOUSE_PLAQUE, player);
				Town town = town(server);
				for (int i = 0; i < 3; i++) {
					TownService.immigrate(level, town);
				}
				TownService.declareHouse(level, LUMBER_PLAQUE, player);
				TownService.declareHouse(level, BARN_PLAQUE, player);
				TownService.declareHouse(level, FISHERY_PLAQUE, player);
				for (Job job : new Job[]{Job.LUMBERJACK, Job.BREEDER, Job.FISHERMAN}) {
					if (town.residents().values().stream().noneMatch(r -> r.job().filter(j -> j.job() == job).isPresent())) {
						return "pas de " + job.serializedName() + " : " + town.residents() + " ; bâtiments : " + town.houses();
					}
				}
				return null;
			}));
			int cows = sp.getServer().computeOnServer(CountrysideScenes::cows);
			look(sp, 0, Y + 22, -2, 0, Y, -1);
			context.waitTicks(60);
			context.takeScreenshot("countryside_0_before");

			waitOrExplain(sp, server -> granary(server, s -> s.is(ItemTags.FISHES)) > 0
					|| carried(server, Job.FISHERMAN, s -> s.is(ItemTags.FISHES)), 2400, "le pêcheur n'a rien pêché");
			waitOrExplain(sp, server -> cows(server) > cows, 2400, "l'éleveur n'a pas fait naître de veau");
			waitOrExplain(sp, server -> logs(server) == 0, 2400, "le bûcheron n'a pas abattu les arbres",
					server -> logs(server) + " bûches restantes, à " + remainingLogs(server) + ".");
			waitOrExplain(sp, server -> granary(server, s -> s.is(ItemTags.LOGS)) > 0, 2400,
					"le bois n'est pas arrivé au grenier");
			look(sp, 0, Y + 22, -2, 0, Y, -1);
			context.waitTicks(20);
			context.takeScreenshot("countryside_1_after");
		}
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static int granary(MinecraftServer server, java.util.function.Predicate<ItemStack> filter) {
		return server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall ? Stock.count(hall, filter) : 0;
	}

	/** Le travailleur de ce métier a-t-il dans les poches ce qu'on cherche ? */
	private static boolean carried(MinecraftServer server, Job job, java.util.function.Predicate<ItemStack> filter) {
		return CityScenes.citizens(server).stream().filter(c -> c.job().filter(j -> j.job() == job).isPresent())
				.anyMatch(c -> c.inventory().stream().anyMatch(filter));
	}

	private static int cows(MinecraftServer server) {
		return server.overworld().getEntitiesOfClass(Cow.class, PASTURE).size();
	}

	/** Les bûches restantes des chênes plantés pour la scène. */
	private static int logs(MinecraftServer server) {
		return Arrays.stream(TREES).mapToInt(base -> {
			int count = 0;
			for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 8, 3))) {
				if (server.overworld().getBlockState(pos).is(BlockTags.LOGS)) {
					count++;
				}
			}
			return count;
		}).sum();
	}

	private static String remainingLogs(MinecraftServer server) {
		java.util.List<String> found = new java.util.ArrayList<>();
		for (BlockPos base : TREES) {
			for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 8, 3))) {
				if (server.overworld().getBlockState(pos).is(BlockTags.LOGS)) {
					found.add(pos.toShortString() + " " + server.overworld().getBlockState(pos.below()).getBlock());
				}
			}
		}
		return String.join(" | ", found);
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -28; x <= 28; x++) {
			for (int z = -32; z <= 24; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 10; y++) {
					level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		// La maison : trois lits.
		WorkScenes.room(level, -13, 4, -8, 8, -11);
		for (int x : new int[]{-13, -11, -8}) {
			command(server, "setblock %d %d 7 minecraft:white_bed[facing=south,part=foot]".formatted(x, Y));
			command(server, "setblock %d %d 8 minecraft:white_bed[facing=south,part=head]".formatted(x, Y));
		}
		command(server, "setblock -11 %d 4 minecities:house_plaque[facing=south]".formatted(Y + 1));
		// La scierie : un billot (bûche écorcée) et un coffre avec une hache ; les chênes à l'est.
		WorkScenes.room(level, 4, 4, 8, 8, 6);
		command(server, "setblock 8 %d 5 minecraft:stripped_oak_log".formatted(Y));
		command(server, "setblock 8 %d 8 minecraft:chest[facing=west]".formatted(Y));
		command(server, "setblock 5 %d 4 minecities:house_plaque[facing=south]".formatted(Y + 1));
		if (level.getBlockEntity(new BlockPos(8, Y, 8)) instanceof Container chest) {
			chest.setItem(0, new ItemStack(Items.IRON_AXE));
		}
		for (BlockPos tree : TREES) {
			command(server, "place feature minecraft:oak %d %d %d".formatted(tree.getX(), tree.getY(), tree.getZ()));
		}
		// L'étable : une botte de foin ; des vaches au nord, du blé au grenier.
		WorkScenes.room(level, -13, -12, -8, -8, -11);
		command(server, "setblock -8 %d -11 minecraft:hay_block".formatted(Y));
		command(server, "setblock -11 %d -12 minecities:house_plaque[facing=south]".formatted(Y + 1));
		for (int i = 0; i < 3; i++) {
			command(server, "summon minecraft:cow %d %d -18".formatted(-14 + i * 3, Y));
		}
		// La pêcherie : un baril, au bord d'un étang.
		WorkScenes.room(level, 4, -12, 8, -8, 6);
		command(server, "setblock 8 %d -8 minecraft:barrel".formatted(Y));
		command(server, "setblock 5 %d -12 minecities:house_plaque[facing=south]".formatted(Y + 1));
		for (int x = 11; x <= 15; x++) {
			for (int z = -13; z <= -9; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.WATER.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(x, Y - 2, z), Blocks.WATER.defaultBlockState());
			}
		}
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			hall.setItem(0, new ItemStack(Items.BREAD, 32));
			hall.setItem(1, new ItemStack(Items.WHEAT, 16));
			hall.setItem(2, new ItemStack(Items.FISHING_ROD));
		}
		command(server, "gamemode spectator @p");
	}
}
