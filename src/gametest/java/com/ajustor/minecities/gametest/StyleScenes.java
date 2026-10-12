package com.ajustor.minecities.gametest;

import com.ajustor.minecities.build.BuildStyle;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;

/**
 * La vitrine des bâtiments (v1.2) : les quatre niveaux de la maison dans chacun des quatre styles,
 * puis quelques ateliers, et l'intérieur d'une maison. Rien à vérifier : des images à regarder.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=styles}
 */
public class StyleScenes implements Scene {
	private static final int Y = 100;
	/** L'écart entre deux bâtiments d'une rangée, et entre deux rangées. */
	private static final int STEP_X = 16;
	private static final int STEP_Z = 40;

	@Override
	public String name() {
		return "styles";
	}

	@Override
	public boolean visual() {
		return true;
	}

	@Override
	public void play(Stage stage) {
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.waitTicks(20);
		stage.run(StyleScenes::build);
		stage.client(mc -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			hideGui(mc, true);
		});
		BuildStyle[] styles = BuildStyle.values();
		for (int row = 0; row < styles.length; row++) {
			// Vue de trois quarts, de face (les portes regardent le nord).
			int z = row * STEP_Z;
			stage.look(30, Y + 10, z - 18, 30, Y + 3, z + 4);
			stage.waitTicks(30);
			stage.shot("styles_" + row + "_" + styles[row].serializedName());
		}
		// Des ateliers du quatrième niveau, côte à côte.
		int z = styles.length * STEP_Z;
		stage.look(32, Y + 11, z - 20, 32, Y + 3, z + 4);
		stage.waitTicks(30);
		stage.shot("styles_4_workshops");
		// Les intérieurs : une maison de niveau 3, une demeure, une cuisine et une bibliothèque.
		stage.look(2 * STEP_X + 6.5, Y + 2.6, 2.5, 2 * STEP_X + 6.5, Y + 1, 7);
		stage.waitTicks(20);
		stage.shot("styles_5_inside");
		stage.look(3 * STEP_X + 7.5, Y + 2.8, 2.2, 3 * STEP_X + 5.5, Y + 0.8, 8);
		stage.waitTicks(20);
		stage.shot("styles_6_mansion");
		int row = styles.length * STEP_Z;
		stage.look(7.5, Y + 2.8, row + 2.2, 4.5, Y + 0.8, row + 6.5);
		stage.waitTicks(20);
		stage.shot("styles_7_kitchen");
		stage.look(2 * 18 + 7.5, Y + 2.8, row + 2.2, 2 * 18 + 5, Y + 0.8, row + 6.5);
		stage.waitTicks(20);
		stage.shot("styles_8_library");
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
