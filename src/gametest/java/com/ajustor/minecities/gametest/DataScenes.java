package com.ajustor.minecities.gametest;

import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildStyle;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ajustor.minecities.gametest.CityScenes.check;

/**
 * Les données du mod, toutes chargées par le jeu : chaque recette, table de butin et schéma écrit dans
 * {@code data/minecities/} doit exister une fois le monde ouvert, et chaque bloc que nomme un schéma, dans
 * chaque style, doit exister dans cette version de Minecraft. Le jeu ne fait qu'écrire dans son journal un
 * fichier qu'il ne sait pas lire, et un bloc inconnu devient de l'air ; ici, la scène échoue. C'est le
 * garde-fou des formats de données et des noms qui changent d'une version de Minecraft à l'autre (voir
 * tools/data/migrate.py).
 *
 * <p>{@code ./gradlew :26.3:runGameTest -PminecitiesGametest=data}
 */
public class DataScenes implements Scene {
	@Override
	public String name() {
		return "data";
	}

	@Override
	public void play(Stage stage) {
		expect(stage, "recipe", server -> server.getRecipeManager().getRecipes().stream()
				.map(holder -> holder.id().identifier()).collect(Collectors.toSet()));
		expect(stage, "loot_table", server -> server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE)
				.listElementIds().map(key -> key.identifier()).collect(Collectors.toSet()));
		expect(stage, "blueprint", server -> Blueprints.all(server).keySet());

		check(stage.compute(server -> {
			Set<String> unknown = new TreeSet<>();
			for (Blueprint blueprint : Blueprints.all(server).values()) {
				for (Blueprint.Cell cell : blueprint.cells()) {
					for (BuildStyle style : BuildStyle.values()) {
						String text = style.resolve(cell.template());
						int bracket = text.indexOf('[');
						Identifier block = Identifier.parse(bracket < 0 ? text : text.substring(0, bracket));
						if (!BuiltInRegistries.BLOCK.containsKey(block)) {
							unknown.add(block + " (" + blueprint.id() + ", " + style.serializedName() + ")");
						}
					}
				}
			}
			return unknown.isEmpty() ? null : "blocs inconnus de cette version : " + unknown;
		}));
	}

	/** Tous les fichiers de {@code data/minecities/<dossier>} sont chargés. */
	private static void expect(Stage stage, String folder, Function<MinecraftServer, Set<Identifier>> loaded) {
		Set<Identifier> written = written(folder);
		check(written.isEmpty() ? "le mod devrait avoir des " + folder : null);
		Set<Identifier> missing = new TreeSet<>(written);
		missing.removeAll(stage.compute(loaded));
		check(missing.isEmpty() ? null : folder + " écrits mais pas chargés par le jeu : " + missing);
	}

	/** Les identifiants des fichiers JSON de {@code data/minecities/<dossier>}, dans les ressources du mod. */
	private static Set<Identifier> written(String folder) {
		Set<Identifier> ids = new TreeSet<>();
		for (Path root : FabricLoader.getInstance().getModContainer("minecities").orElseThrow().getRootPaths()) {
			Path dir = root.resolve("data").resolve("minecities").resolve(folder);
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(dir)) {
				files.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
					String rel = dir.relativize(p).toString().replace('\\', '/');
					ids.add(Identifier.fromNamespaceAndPath("minecities", rel.substring(0, rel.length() - ".json".length())));
				});
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return ids;
	}
}
