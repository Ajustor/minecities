package com.ajustor.minecities.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Les réglages du mod, ouverts depuis le menu des mods (Mod Menu) : la langue des textes du mod.
 * Rien n'est écrit avant « Terminé » ; la langue ne se recharge que si elle a changé.
 */
public class ConfigScreen extends Screen {
	private static final int BUTTON_WIDTH = 240;

	private final Screen parent;
	private ClientConfig client = ClientConfig.get();

	public ConfigScreen(Screen parent) {
		super(Component.translatable("config.minecities.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = (width - BUTTON_WIDTH) / 2;
		List<String> languages = new ArrayList<>();
		languages.add("");
		languages.addAll(ModLanguage.available(minecraft.getResourceManager()));
		if (!languages.contains(client.language())) {
			languages.add(client.language());
		}
		addRenderableWidget(CycleButton.<String>builder(
						code -> code.isEmpty() ? Component.translatable("config.minecities.language.game") : ModLanguage.name(code),
						client.language())
				.withValues(languages)
				.withTooltip(code -> Tooltip.create(Component.translatable("config.minecities.language.tooltip")))
				.create(x, 50, BUTTON_WIDTH, 20, Component.translatable("config.minecities.language"),
						(button, code) -> client = client.withLanguage(code)));
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds((width - 200) / 2, height - 28, 200, 20).build());
	}

	/** Écrit les réglages et revient au menu des mods. */
	@Override
	public void onClose() {
		boolean languageChanged = !client.language().equals(ClientConfig.get().language());
		ClientConfig.save(client);
		if (languageChanged) {
			ModLanguage.apply(client.language());
		}
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 20, 0xFFFFFFFF);
	}
}
