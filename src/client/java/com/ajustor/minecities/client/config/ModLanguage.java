package com.ajustor.minecities.client.config;

import com.ajustor.minecities.MineCities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Les textes du mod dans une autre langue que le jeu : un joueur peut garder son jeu en anglais et le
 * mod en français, ou l'inverse. Seuls les fichiers {@code assets/minecities/lang} sont concernés,
 * ceux d'un pack de ressources compris.
 */
public final class ModLanguage {
	private ModLanguage() {
	}

	/** Pose les traductions du mod de la langue choisie sur celles du jeu (rien si le mod suit le jeu). */
	public static void overlay(ResourceManager resources, Map<String, String> storage) {
		for (String code : ClientConfig.get().overlay()) {
			for (Resource resource : resources.getResourceStack(file(code))) {
				try (InputStream in = resource.open()) {
					Language.loadFromJson(in, storage::put);
				} catch (IOException | RuntimeException e) {
					MineCities.LOGGER.warn("Langue du mod illisible : {} ({})", code, resource.sourcePackId(), e);
				}
			}
		}
	}

	/** Les langues dans lesquelles le mod est traduit, triées par code. */
	public static List<String> available(ResourceManager resources) {
		return resources.listResources("lang", id -> id.getNamespace().equals(MineCities.MOD_ID)
						&& id.getPath().endsWith(".json"))
				.keySet().stream()
				.map(id -> id.getPath().substring("lang/".length(), id.getPath().length() - ".json".length()))
				.sorted().toList();
	}

	/** Le nom d'une langue, tel que le menu des langues du jeu l'écrit. */
	public static Component name(String code) {
		LanguageInfo info = Minecraft.getInstance().getLanguageManager().getLanguage(code);
		return info != null ? info.toComponent() : Component.literal(code);
	}

	/** Change la langue du mod et recharge les traductions, sans recharger tout le reste. */
	public static void apply(String code) {
		ClientConfig.save(ClientConfig.get().withLanguage(code));
		Minecraft mc = Minecraft.getInstance();
		mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
	}

	private static Identifier file(String code) {
		return MineCities.id("lang/" + code + ".json");
	}
}
