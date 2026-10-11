package com.ajustor.minecities.gametest;

import com.ajustor.minecities.build.BuildStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.look;

/**
 * La vitrine des bâtiments (v1.2) : les quatre niveaux de la maison dans chacun des quatre styles,
 * puis quelques ateliers, et l'intérieur d'une maison. Rien à vérifier : des images à regarder.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=styles}
 */
public class StyleScenes implements FabricClientGameTest {
	private static final int Y = 100;
	/** L'écart entre deux bâtiments d'une rangée, et entre deux rangées. */
	private static final int STEP_X = 16;
	private static final int STEP_Z = 40;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!CityScenes.selected("styles")) {
			return;
		}
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			command(sp, "time set 1000");
			command(sp, "gamerule doDaylightCycle false");
			command(sp, "gamerule doMobSpawning false");
			command(sp, "weather clear");
			context.waitTicks(20);
			sp.getServer().runOnServer(StyleScenes::build);
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				hideGui(mc, true);
			});
			BuildStyle[] styles = BuildStyle.values();
			for (int row = 0; row < styles.length; row++) {
				// Vue de trois quarts, de face (les portes regardent le nord).
				int z = row * STEP_Z;
				look(sp, 30, Y + 10, z - 18, 30, Y + 3, z + 4);
				context.waitTicks(30);
				context.takeScreenshot("styles_" + row + "_" + styles[row].serializedName());
			}
			// Des ateliers du quatrième niveau, côte à côte.
			int z = styles.length * STEP_Z;
			look(sp, 32, Y + 11, z - 20, 32, Y + 3, z + 4);
			context.waitTicks(30);
			context.takeScreenshot("styles_4_workshops");
			// L'intérieur d'une maison de niveau 3.
			look(sp, 2 * STEP_X + 6.5, Y + 2.6, 2.5, 2 * STEP_X + 6.5, Y + 1, 7);
			context.waitTicks(20);
			context.takeScreenshot("styles_5_inside");
		}
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BuildStyle[] styles = BuildStyle.values();
		for (int x = -10; x <= 80; x++) {
			for (int z = -30; z <= styles.length * STEP_Z + 20; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 16; y++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		for (int row = 0; row < styles.length; row++) {
			for (int n = 1; n <= 4; n++) {
				RaidScenes.paste(level, "house_" + n, new BlockPos((n - 1) * STEP_X, Y, row * STEP_Z), styles[row]);
			}
		}
		String[] workshops = {"kitchen_4", "smithy_4", "library_4", "market_4"};
		for (int i = 0; i < workshops.length; i++) {
			RaidScenes.paste(level, workshops[i], new BlockPos(i * 18, Y, styles.length * STEP_Z), BuildStyle.PLAINS);
		}
		command(server, "gamemode spectator @p");
	}
}
