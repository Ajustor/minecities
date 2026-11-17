package com.ajustor.minecities.gametest;

import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildStyle;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;

/**
 * La galerie du site (page Bâtiments) : la maison à ses quatre niveaux dans chaque style, puis chaque
 * bâtiment à ses quatre niveaux (en plaines), et les monuments. Une capture par rangée, de face et
 * un peu de biais ; site/gallery.py les recadre pour le site. Rien à vérifier.
 *
 * <p>{@code ./gradlew :26.2:runClientGameTest -PminecitiesGametest=gallery}
 */
public class GalleryScenes implements Scene {
	private static final int Y = 100;
	/** L'écart entre deux bâtiments d'une rangée. */
	private static final int GAP = 4;
	/** Les bâtiments à niveaux, dans l'ordre de la page (la maison à part). */
	static final List<String> FAMILIES = List.of("kitchen", "bakery", "farm", "barn", "fishery", "sawmill",
			"carpentry", "builder_hut", "smithy", "mine", "masonry", "glassworks", "weavery", "tannery", "apiary",
			"jeweler_workshop", "inventor_workshop", "infirmary", "market", "tavern", "warehouse", "courier_post",
			"library", "barracks", "archery");

	@Override
	public String name() {
		return "gallery";
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
		stage.resize(1600, 900);
		stage.run(server -> {
			ground(server.overworld());
			command(server, "gamemode spectator @p");
		});
		stage.client(mc -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			hideGui(mc, true);
		});
		stage.waitTicks(100);
		for (BuildStyle style : BuildStyle.values()) {
			row(stage, "gallery_style_" + style.serializedName(), List.of("house_1", "house_2", "house_3", "house_4"),
					style);
		}
		for (String family : FAMILIES) {
			row(stage, "gallery_building_" + family,
					List.of(family + "_1", family + "_2", family + "_3", family + "_4"), BuildStyle.PLAINS);
		}
		row(stage, "gallery_monuments", List.of("statue", "fountain", "belfry"), BuildStyle.PLAINS);
	}

	/** Pose la rangée, s'y poste et la prend en photo. */
	private static void row(Stage stage, String shot, List<String> names, BuildStyle style) {
		int[] size = stage.compute(server -> {
			ServerLevel level = server.overworld();
			clear(level);
			// Face aux portes (au nord), l'est est à gauche : on pose de droite à gauche, pour lire le
			// niveau 1 à gauche de l'image.
			int x = 0;
			int depth = 0;
			int height = 0;
			for (String name : names.reversed()) {
				Blueprint blueprint = blueprint(server, name);
				RaidScenes.paste(level, name, new BlockPos(x, Y, 0), style);
				x += blueprint.width() + GAP;
				depth = Math.max(depth, blueprint.depth());
				height = Math.max(height, blueprint.height());
			}
			return new int[]{x - GAP, depth, height};
		});
		double width = size[0];
		double cx = width / 2;
		double distance = Math.max(13, 0.44 * width);
		// De face (les portes regardent le nord), un peu de biais et d'en haut.
		stage.look(cx + 0.12 * width, Y + 3 + 0.3 * distance, -distance, cx, Y + size[2] / 3.0, size[1] / 2.0);
		stage.waitTicks(60);
		stage.shot(shot);
	}

	private static Blueprint blueprint(MinecraftServer server, String name) {
		return Blueprints.get(server, Identifier.fromNamespaceAndPath("minecities", name))
				.orElseThrow(() -> new AssertionError("schéma introuvable : " + name));
	}

	/** Une grande pelouse plate, pour que les bâtiments ne flottent pas sur le vide. */
	private static void ground(ServerLevel level) {
		for (int x = -70; x <= 170; x++) {
			for (int z = -110; z <= 70; z++) {
				level.setBlock(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			}
		}
	}

	/** Vide la place entre deux rangées (la pelouse reste). */
	private static void clear(ServerLevel level) {
		for (int x = -4; x <= 100; x++) {
			for (int z = -4; z <= 30; z++) {
				for (int y = Y; y < Y + 22; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.getBlockState(pos).isAir()) {
						// Sans prévenir les voisins : un lit ou une porte ne tombe pas en objets.
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
					}
				}
				BlockPos below = new BlockPos(x, Y - 1, z);
				if (!level.getBlockState(below).is(Blocks.GRASS_BLOCK)) {
					level.setBlock(below, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				}
			}
		}
	}
}
