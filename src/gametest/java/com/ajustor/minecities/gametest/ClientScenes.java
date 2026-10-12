package com.ajustor.minecities.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import java.util.ArrayList;
import java.util.List;

/**
 * Les scènes dans le vrai jeu, chacune dans un monde neuf, pour les captures
 * ({@code build/run/clientGameTest/screenshots}). Une scène qui échoue n'empêche pas les suivantes ;
 * les échecs sont tous rapportés à la fin.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=city,work} ; {@code -PminecitiesFilm} pour
 * les films image par image.
 */
public class ClientScenes implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = new ArrayList<>();
		for (Scene scene : Scene.selected()) {
			long start = System.nanoTime();
			try (TestSingleplayerContext sp = context.worldBuilder()
					.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
				scene.play(new ClientStage(context, sp));
			} catch (AssertionError | RuntimeException e) {
				failures.add(scene.name() + " : " + e.getMessage());
				org.slf4j.LoggerFactory.getLogger("minecities").error("Scène {} en échec", scene.name(), e);
			}
			org.slf4j.LoggerFactory.getLogger("minecities").info("Scène {} : {} s", scene.name(),
					(System.nanoTime() - start) / 1_000_000_000L);
		}
		if (!failures.isEmpty()) {
			throw new AssertionError(failures.size() + " scène(s) en échec :\n" + String.join("\n", failures));
		}
	}
}
