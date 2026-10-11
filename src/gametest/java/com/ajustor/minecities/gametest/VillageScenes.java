package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.House;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.look;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Un village vanilla repris par une ville : une maison de deux lits sans plaque, et deux villageois
 * sans métier. La ville les recense, reprend leur maison (sans y loger de citoyen tant qu'ils y
 * dorment), puis l'un d'eux devient citoyen.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=village}
 */
public class VillageScenes implements FabricClientGameTest {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!CityScenes.selected("village")) {
			return;
		}
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			// Cette scène compte ses citoyens : pas de nouveaux venus pour atteindre le minimum.
			com.ajustor.minecities.town.Families.setMinimum(0);
			command(sp, "time set 1000");
			command(sp, "gamerule doDaylightCycle false");
			command(sp, "gamerule doMobSpawning false");
			command(sp, "weather clear");
			context.waitTicks(20);
			sp.getServer().runOnServer(VillageScenes::build);
			context.runOnClient(mc -> {
				hideGui(mc, true);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			// Les villageois prennent leurs lits (mémoire HOME) avant que la ville ne soit fondée.
			context.waitTicks(400);
			sp.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				TownService.found(server.overworld(), HALL, player, new ItemStack(McItems.TOWN_HALL));
			});
			waitOrExplain(sp, server -> town(server).houses().stream().anyMatch(House::village), 1200,
					"la maison du village n'a pas été reprise", server -> "bâtiments " + town(server).houses()
							+ ", villageois " + town(server).villagers() + ".");
			look(sp, -2, Y + 7, -6, 6, Y, 6);
			context.waitTicks(20);
			context.takeScreenshot("village_0_adopted");
			waitOrExplain(sp, server -> town(server).citizens() >= 1, 3600, "aucun villageois n'est devenu citoyen",
					server -> "villageois " + town(server).villagers() + ", citoyens " + town(server).citizens() + ".");
			context.waitTicks(40);
			context.takeScreenshot("village_1_naturalized");
		}
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -16; x <= 16; x++) {
			for (int z = -16; z <= 16; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 8; y++) {
					level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		// Une maison de village : deux lits, une porte, pas de plaque.
		WorkScenes.room(level, 4, 4, 8, 8, 6);
		for (int x : new int[]{4, 8}) {
			command(server, "setblock %d %d 7 minecraft:white_bed[facing=south,part=foot]".formatted(x, Y));
			command(server, "setblock %d %d 8 minecraft:white_bed[facing=south,part=head]".formatted(x, Y));
		}
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			hall.setItem(0, new ItemStack(Items.BREAD, 16));
		}
		command(server, "summon minecraft:villager 6 %d 6".formatted(Y));
		command(server, "summon minecraft:villager 5 %d 5".formatted(Y));
		command(server, "time set 13000");
		command(server, "gamemode spectator @p");
	}
}
