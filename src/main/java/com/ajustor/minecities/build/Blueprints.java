package com.ajustor.minecities.build;

import com.ajustor.minecities.MineCities;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Les schémas connus : ceux du mod et ceux des datapacks, lus dans {@code data/<espace>/blueprint/}
 * (relus après un {@code /reload}), et les bâtiments que les joueurs enregistrent avec un bloc de
 * structure : les fichiers {@code generated/<espace>/structure/*.nbt} du monde, relus quand ils
 * changent.
 */
public final class Blueprints {
	/** On ne relit les structures du monde que toutes les 5 secondes : les bâtisseurs demandent souvent. */
	private static final int SCAN_TICKS = 100;

	private static Map<Identifier, Blueprint> cache;
	/** Les structures enregistrées, avec la date de leur fichier. */
	private static final Map<Identifier, Saved> saved = new HashMap<>();
	private static int scanned = Integer.MIN_VALUE;

	private record Saved(long modified, Blueprint blueprint) {
	}

	private Blueprints() {
	}

	public static void register() {
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> cache = null);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			cache = null;
			saved.clear();
			scanned = Integer.MIN_VALUE;
		});
	}

	/** Tous les schémas : ceux du mod et des datapacks, puis ceux des joueurs (sans les écraser). */
	public static Map<Identifier, Blueprint> all(MinecraftServer server) {
		Map<Identifier, Blueprint> all = new TreeMap<>();
		saved(server).forEach(all::put);
		all.putAll(resources(server));
		return all;
	}

	private static Map<Identifier, Blueprint> resources(MinecraftServer server) {
		if (cache == null) {
			Map<Identifier, Blueprint> loaded = new TreeMap<>();
			Map<Identifier, Resource> found = server.getResourceManager().listResources("blueprint",
					id -> id.getPath().endsWith(".json"));
			for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
				String path = e.getKey().getPath();
				Identifier id = Identifier.fromNamespaceAndPath(e.getKey().getNamespace(),
						path.substring("blueprint/".length(), path.length() - ".json".length()));
				try (Reader reader = e.getValue().openAsReader()) {
					loaded.put(id, Blueprint.parse(id, JsonParser.parseReader(reader).getAsJsonObject()));
				} catch (Exception ex) {
					MineCities.LOGGER.error("Schéma illisible : {}", id, ex);
				}
			}
			cache = loaded;
		}
		return cache;
	}

	public static Optional<Blueprint> get(MinecraftServer server, Identifier id) {
		Blueprint resource = resources(server).get(id);
		return resource != null ? Optional.of(resource) : Optional.ofNullable(saved(server).get(id));
	}

	/** Les bâtiments enregistrés dans ce monde avec un bloc de structure. */
	private static synchronized Map<Identifier, Blueprint> saved(MinecraftServer server) {
		int now = server.getTickCount();
		if (scanned == Integer.MIN_VALUE || now - scanned >= SCAN_TICKS || now < scanned) {
			scanned = now;
			scan(server);
		}
		Map<Identifier, Blueprint> out = new HashMap<>();
		saved.forEach((id, s) -> out.put(id, s.blueprint()));
		return out;
	}

	/** Relit le dossier des structures : les nouvelles, celles qui ont changé ; oublie les disparues. */
	private static void scan(MinecraftServer server) {
		Path root = server.getWorldPath(LevelResource.GENERATED_DIR);
		Map<Identifier, Long> files = new HashMap<>();
		if (Files.isDirectory(root)) {
			try (Stream<Path> spaces = Files.list(root)) {
				for (Path space : spaces.toList()) {
					Path dir = space.resolve("structure");
					if (!Files.isDirectory(dir)) {
						continue;
					}
					try (Stream<Path> nbt = Files.walk(dir)) {
						for (Path file : nbt.filter(f -> f.toString().endsWith(".nbt")).toList()) {
							String path = dir.relativize(file).toString().replace('\\', '/');
							Identifier id = Identifier.tryBuild(space.getFileName().toString(),
									path.substring(0, path.length() - ".nbt".length()));
							if (id != null) {
								files.put(id, Files.getLastModifiedTime(file).toMillis());
							}
						}
					}
				}
			} catch (IOException e) {
				MineCities.LOGGER.error("Structures du monde illisibles : {}", root, e);
			}
		}
		saved.keySet().retainAll(files.keySet());
		files.forEach((id, modified) -> {
			Saved known = saved.get(id);
			if (known != null && known.modified() == modified) {
				return;
			}
			try {
				server.getStructureManager().get(id).ifPresent(template ->
						saved.put(id, new Saved(modified, Blueprint.fromStructure(id, template))));
			} catch (RuntimeException e) {
				MineCities.LOGGER.error("Structure illisible : {}", id, e);
			}
		});
	}
}
