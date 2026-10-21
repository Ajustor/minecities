package com.ajustor.minecities.gametest;

import com.ajustor.minecities.client.config.ConfigScreen;
import com.ajustor.minecities.client.config.ModLanguage;
import net.minecraft.client.resources.language.I18n;

import java.util.concurrent.atomic.AtomicReference;

import static com.ajustor.minecities.gametest.CityScenes.check;

/**
 * La langue du mod, indépendante de celle du jeu (comme pour FMAB) : le mod passe en anglais, les
 * textes du jeu restent dans leur langue ; puis il revient à celle du jeu. L'écran de réglages (Mod
 * Menu) en capture. Sans fenêtre, il n'y a pas de traductions : la scène ne vérifie rien.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=language -PminecitiesLang=fr_fr}
 */
public class LanguageScenes implements Scene {
	@Override
	public String name() {
		return "language";
	}

	@Override
	public boolean visual() {
		return true;
	}

	@Override
	public void play(Stage stage) {
		AtomicReference<String> game = new AtomicReference<>();
		AtomicReference<String> guide = new AtomicReference<>();
		AtomicReference<String> done = new AtomicReference<>();
		stage.client(mc -> {
			game.set(mc.options.languageCode);
			done.set(I18n.get("gui.done"));
			ModLanguage.apply("en_us");
		});
		stage.waitTicks(2);
		stage.client(mc -> guide.set(I18n.get("item.minecities.guide")));
		check(guide.get() == null || guide.get().equals("Town Guide") ? null
				: "le mod n'est pas passé en anglais : « " + guide.get() + " »");
		stage.client(mc -> guide.set(I18n.get("gui.done")));
		check(done.get() == null || done.get().equals(guide.get()) ? null
				: "les textes du jeu ont changé de langue avec le mod : « " + done.get() + " » → « " + guide.get() + " »");

		// L'écran de réglages, ouvert comme depuis Mod Menu.
		stage.client(mc -> mc.gui.setScreen(new ConfigScreen(null)));
		stage.waitTicks(5);
		stage.shot("language_0_settings");

		// Retour à la langue du jeu.
		stage.client(mc -> {
			mc.gui.setScreen(null);
			ModLanguage.apply("");
		});
		stage.waitTicks(2);
		stage.client(mc -> guide.set(I18n.get("item.minecities.guide")));
		check(game.get() == null || !game.get().equals("fr_fr") || guide.get().equals("Guide de la ville") ? null
				: "le mod n'est pas revenu à la langue du jeu : « " + guide.get() + " »");
	}
}
