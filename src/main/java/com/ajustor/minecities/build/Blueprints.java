package com.ajustor.minecities.build;

import com.ajustor.minecities.MineCities;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Les schémas connus : ceux du mod et ceux des datapacks, lus dans {@code data/<espace>/blueprint/}.
 * Relus après un {@code /reload}.
 */
public final class Blueprints {
	private static Map<Identifier, Blueprint> cache;

	private Blueprints() {
	}

	public static void register() {
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> cache = null);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> cache = null);
	}

	public static Map<Identifier, Blueprint> all(MinecraftServer server) {
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
		return Optional.ofNullable(all(server).get(id));
	}
}
