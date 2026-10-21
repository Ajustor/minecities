package com.ajustor.minecities.client.config;

import com.ajustor.minecities.MineCities;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Les réglages du joueur, dans {@code config/minecities-client.json}, modifiables depuis le menu des
 * mods (Mod Menu). Le fichier est créé au premier lancement ; une clé absente reprend sa valeur par
 * défaut.
 *
 * @param language la langue des textes du mod seuls (« fr_fr », « en_us »…), indépendante de celle du
 *                 jeu ; vide : celle du jeu
 */
public record ClientConfig(String language) {
	public static final ClientConfig DEFAULT = new ClientConfig("");
	/** La langue de référence du mod : sous toutes les autres, pour les clés qui leur manqueraient. */
	public static final String BASE_LANGUAGE = "en_us";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ClientConfig current = DEFAULT;

	public ClientConfig {
		language = language == null ? "" : language;
	}

	public static ClientConfig get() {
		return current;
	}

	public ClientConfig withLanguage(String value) {
		return new ClientConfig(value);
	}

	/**
	 * Les fichiers de langue du mod à poser par-dessus ceux du jeu, dans l'ordre : rien si le mod suit
	 * la langue du jeu ; sinon la langue de référence, puis celle choisie.
	 */
	public List<String> overlay() {
		if (language.isEmpty()) {
			return List.of();
		}
		return language.equals(BASE_LANGUAGE) ? List.of(BASE_LANGUAGE) : List.of(BASE_LANGUAGE, language);
	}

	public static ClientConfig fromJson(JsonObject json) {
		return new ClientConfig(json.has("language") ? json.get("language").getAsString() : DEFAULT.language());
	}

	public JsonObject toJson() {
		JsonObject out = new JsonObject();
		out.addProperty("language", language);
		return out;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("minecities-client.json");
	}

	public static void load() {
		Path file = file();
		try {
			if (Files.exists(file)) {
				current = fromJson(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
			}
		} catch (IOException | RuntimeException e) {
			MineCities.LOGGER.error("Configuration {} illisible : valeurs par défaut", file, e);
			current = DEFAULT;
		}
		save(current);
	}

	/** Retient ces réglages et les écrit dans le fichier. */
	public static void save(ClientConfig config) {
		current = config;
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(config.toJson()));
		} catch (IOException e) {
			MineCities.LOGGER.error("Impossible d'écrire {}", file, e);
		}
	}
}
