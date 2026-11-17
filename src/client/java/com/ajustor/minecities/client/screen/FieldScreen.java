package com.ajustor.minecities.client.screen;

import com.ajustor.minecities.field.FieldNetwork;
import com.ajustor.minecities.town.Field;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Le choix de la culture d'un champ (piquet de champ) : une icône par culture, la choisie entourée ; un
 * clic choisit et referme. En bas, supprimer le champ. Le serveur revérifie chaque demande.
 */
public class FieldScreen extends Screen {
	private static final int WIDTH = 410;
	private static final int HEIGHT = 175;
	private static final int CELL = 56;
	private static final int PAPER = 0xFFEDE3C8;
	private static final int EDGE = 0xFF5A4A33;
	private static final int INK = 0xFF2B2B40;
	private static final int FADED = 0xFF7A7060;
	private static final int CHOSEN = 0x602E7D32;

	private final FieldNetwork.FieldSnapshot field;
	private int left;
	private int top;

	public FieldScreen(FieldNetwork.FieldSnapshot field) {
		super(Component.translatable("item.minecities.field_marker"));
		this.field = field;
	}

	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		Field.Crop[] crops = Field.Crop.values();
		int x0 = left + (WIDTH - crops.length * CELL) / 2;
		for (int i = 0; i < crops.length; i++) {
			Field.Crop crop = crops[i];
			addRenderableWidget(Button.builder(Component.empty(), b -> send("crop:" + crop.serializedName()))
					.bounds(x0 + i * CELL + 14, top + 50, 28, 28)
					.tooltip(net.minecraft.client.gui.components.Tooltip.create(
							Component.translatable("crop.minecities." + crop.serializedName() + ".desc")))
					.build());
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.minecities.field.delete"),
				b -> send("delete")).bounds(left + 10, top + HEIGHT - 26, 120, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(left + WIDTH - 90, top + HEIGHT - 26, 80, 18).build());
	}

	private void send(String action) {
		ClientPlayNetworking.send(new FieldNetwork.FieldAction(field.id(), action));
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(left - 3, top - 3, left + WIDTH + 3, top + HEIGHT + 3, EDGE);
		graphics.fill(left, top, left + WIDTH, top + HEIGHT, PAPER);
		graphics.text(font, Component.translatable("screen.minecities.field.title", field.width(), field.depth(),
				field.town()), left + 10, top + 9, INK, false);
		graphics.text(font, Component.translatable("screen.minecities.field.question"), left + 10, top + 26, FADED,
				false);
		Field.Crop[] crops = Field.Crop.values();
		int x0 = left + (WIDTH - crops.length * CELL) / 2;
		for (int i = 0; i < crops.length; i++) {
			if (crops[i].serializedName().equals(field.crop())) {
				graphics.fill(x0 + i * CELL + 2, top + 46, x0 + i * CELL + CELL - 2, top + 96, CHOSEN);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		for (int i = 0; i < crops.length; i++) {
			int x = x0 + i * CELL;
			graphics.item(new ItemStack(crops[i].icon()), x + 20, top + 56);
			Component name = Component.translatable("crop.minecities." + crops[i].serializedName());
			int w = font.width(name);
			graphics.text(font, name, x + (CELL - w) / 2, top + 84, INK, false);
		}
		int y = top + 104;
		for (net.minecraft.util.FormattedCharSequence line : font.split(
				Component.translatable("screen.minecities.field.hint"), WIDTH - 20)) {
			graphics.text(font, line, left + 10, y, FADED, false);
			y += 10;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
