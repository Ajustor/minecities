package com.ajustor.minecities;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Le français et l'anglais ont les mêmes clés, et les mêmes paramètres pour chacune. */
class LangTest {
	private static JsonObject lang(String code) throws IOException {
		Path path = Path.of(System.getProperty("minecities.resources"), "assets/minecities/lang", code + ".json");
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	@Test
	void frenchAndEnglishMatch() throws IOException {
		JsonObject fr = lang("fr_fr");
		JsonObject en = lang("en_us");
		Set<String> frKeys = new TreeSet<>(fr.keySet());
		Set<String> enKeys = new TreeSet<>(en.keySet());
		assertEquals(frKeys, enKeys);
		for (String key : frKeys) {
			assertEquals(count(fr.get(key).getAsString()), count(en.get(key).getAsString()), key);
		}
	}

	private static long count(String text) {
		return text.split("%s", -1).length - 1;
	}
}
