package com.ajustor.minecities;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jade est facultatif : dans le code du mod (main et client), seul le paquet compat/jade le nomme, et seul le point d'entrée {@code jade} de
 * fabric.mod.json mène à ce paquet. Sans Jade, aucune classe chargée par le mod ne le demande.
 */
class CompatTest {
	private static final Path SOURCES = Path.of(System.getProperty("minecities.resources")).getParent();

	@Test
	void onlyTheJadePackageNamesJade() throws IOException {
		List<String> offenders = new java.util.ArrayList<>();
		for (Path root : List.of(SOURCES.resolve("java"), SOURCES.resolveSibling("client").resolve("java"))) {
			try (Stream<Path> files = Files.walk(root)) {
				files.filter(p -> p.toString().endsWith(".java"))
						.filter(p -> !p.toString().replace('\\', '/').contains("/com/ajustor/minecities/compat/jade/"))
						.filter(CompatTest::namesJade)
						.forEach(p -> offenders.add(p.toString()));
			}
		}
		assertEquals(List.of(), offenders);
	}

	@Test
	void theJadePluginIsOnlyAJadeEntrypoint() throws IOException {
		String mod = Files.readString(SOURCES.resolve("resources/fabric.mod.json"));
		assertTrue(mod.contains("\"jade\": [\n\t\t\t\"com.ajustor.minecities.compat.jade.MineCitiesJadePlugin\""), mod);
		assertTrue(!mod.matches("(?s).*\"depends\"\\s*:\\s*\\{[^}]*\"jade\".*"), "Jade ne doit pas être requis");
	}

	private static boolean namesJade(Path file) {
		try {
			return Files.readString(file).contains("snownee.jade");
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}
}
