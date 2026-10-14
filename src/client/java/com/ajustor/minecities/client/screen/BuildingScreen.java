package com.ajustor.minecities.client.screen;

import com.ajustor.minecities.network.BuildingNetwork;
import com.ajustor.minecities.network.BuildingSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * L'interface d'un bâtiment : ce qu'il est, son niveau, ses lits et ses postes, son dernier relevé ;
 * le chantier en cours à sa place ; et son amélioration, avec le niveau suivant, ce qu'il coûte et
 * ce que le grenier en a déjà, ou ce qui l'empêche. Le maire (ou un adjoint) la commande d'un bouton.
 * Un bâtiment fait à la main se rebâtit au niveau 2 de sa famille, à sa place.
 */
public class BuildingScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int HEIGHT = 230;
	/** Une colonne de matériaux : l'icône, le nom (coupé s'il le faut), puis « posé / voulu ». */
	private static final int COLUMN = (WIDTH - 24) / 2;
	private static final int PAPER = 0xFFEDE3C8;
	private static final int EDGE = 0xFF5A4A33;
	private static final int INK = 0xFF2B2B40;
	private static final int FADED = 0xFF7A7060;
	private static final int GOOD = 0xFF2E7D32;
	private static final int BAD = 0xFFB0201A;
	private static final int ACCENT = 0xFF8A5A1E;

	private BuildingSnapshot building;
	private int left;
	private int top;

	public BuildingScreen(BuildingSnapshot building) {
		super(Component.translatable("screen.minecities.map.kind." + building.kind()));
		this.building = building;
	}

	public BuildingSnapshot building() {
		return building;
	}

	public boolean shows(BuildingSnapshot snapshot) {
		return building.plaque().equals(snapshot.plaque());
	}

	/** Le serveur a renvoyé l'état du bâtiment (après une commande). */
	public void refresh(BuildingSnapshot snapshot) {
		building = snapshot;
		rebuildWidgets();
	}

	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		if (building.manager() && building.next().isPresent()) {
			// Le bouton dit seulement quoi faire : le niveau suivant est écrit plus haut.
			Button upgrade = addRenderableWidget(Button.builder(Component.translatable(building.rebuild()
									? "screen.minecities.building.rebuild.button" : "screen.minecities.building.upgrade.button"),
					b -> ClientPlayNetworking.send(new BuildingNetwork.ActionPayload(building.plaque(), "upgrade")))
					.bounds(left + 10, top + HEIGHT - 26, 120, 18).build());
			upgrade.active = building.refusal().isEmpty();
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(left + WIDTH - 90, top + HEIGHT - 26, 80, 18).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(left - 3, top - 3, left + WIDTH + 3, top + HEIGHT + 3, EDGE);
		graphics.fill(left, top, left + WIDTH, top + HEIGHT, PAPER);
		graphics.text(font, Component.translatable("screen.minecities.building.title", getTitle(), building.level(),
				building.town()), left + 10, top + 9, INK, false);
		graphics.fill(left + 8, top + 22, left + WIDTH - 8, top + 23, EDGE);
		int y = top + 30;
		y = wrap(graphics, building.status(), y, building.valid() ? GOOD : BAD) + 2;
		if (building.beds() > 0) {
			y = wrap(graphics, Component.translatable("screen.minecities.building.beds", building.sleepers(),
					building.beds()), y, INK);
		}
		if (!building.jobs().isEmpty()) {
			MutableComponent jobs = Component.empty();
			for (String job : building.jobs()) {
				if (!jobs.getSiblings().isEmpty()) {
					jobs.append(", ");
				}
				jobs.append(Component.translatable(job));
			}
			y = wrap(graphics, Component.translatable("screen.minecities.building.jobs", jobs), y, INK);
		}
		if (building.placed() >= 0) {
			y = wrap(graphics, Component.translatable("screen.minecities.building.site", building.placed(),
					building.total()), y, ACCENT);
		}
		y += 5;
		graphics.fill(left + 8, y, left + WIDTH - 8, y + 1, EDGE);
		y += 6;
		// Le bas : le refus (replié), puis les boutons ; les matériaux s'arrêtent au-dessus.
		List<FormattedCharSequence> refusal = building.refusal().map(r -> font.split(r, WIDTH - 24)).orElse(List.of());
		int bottom = top + HEIGHT - 30 - 10 * refusal.size();
		int ry = bottom + 2;
		for (FormattedCharSequence line : refusal) {
			graphics.text(font, line, left + 12, ry, BAD, false);
			ry += 10;
		}
		// L'amélioration.
		if (building.next().isPresent()) {
			y = wrap(graphics, Component.translatable("screen.minecities.building.next", building.next().get()), y, ACCENT);
			if (building.rebuild()) {
				// Fait à la main : le bâtisseur le démolit pour bâtir le nouveau à sa place.
				y = wrap(graphics, Component.translatable("screen.minecities.building.replaces"), y, BAD);
			}
			// Un chantier déjà là : sa liste ne servirait à rien, le refus le dit.
			if (building.placed() < 0) {
				materials(graphics, y + 6, bottom, mouseX, mouseY);
			}
		}
		if (!building.manager() && building.next().isPresent()) {
			graphics.text(font, Component.translatable("screen.minecities.mayor_decides"), left + 12, top + HEIGHT - 20,
					FADED, false);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** Écrit ce texte, replié à la largeur de l'écran ; renvoie la ligne d'après. */
	private int wrap(GuiGraphicsExtractor graphics, Component text, int y, int color) {
		for (FormattedCharSequence line : font.split(text, WIDTH - 24)) {
			graphics.text(font, line, left + 12, y, color, false);
			y += 10;
		}
		return y + 1;
	}

	/** Les matériaux, sur deux colonnes, jusqu'à {@code bottom} ; ce qui ne tient pas se compte à la fin. */
	private void materials(GuiGraphicsExtractor graphics, int y, int bottom, int mouseX, int mouseY) {
		List<BuildingSnapshot.Material> all = building.materials();
		int rows = Math.max(0, (bottom - y) / 13);
		int shown = Math.min(all.size(), rows * 2);
		if (shown < all.size()) {
			// La dernière case annonce le reste.
			shown = Math.max(0, shown - 1);
		}
		for (int i = 0; i < shown; i++) {
			BuildingSnapshot.Material m = all.get(i);
			int x = left + 12 + (i % 2) * (COLUMN + 2);
			int row = y + (i / 2) * 13;
			Component count = Component.translatable("screen.minecities.building.material", m.stocked(), m.needed());
			int countWidth = font.width(count);
			graphics.item(m.item(), x, row - 4);
			String full = m.item().getHoverName().getString();
			int room = COLUMN - 22 - countWidth - 4;
			String name = font.width(full) <= room ? full : font.plainSubstrByWidth(full, room - font.width("…")) + "…";
			graphics.text(font, Component.literal(name), x + 18, row, INK, false);
			if (mouseX >= x && mouseX < x + COLUMN && mouseY >= row - 4 && mouseY < row + 9) {
				graphics.setTooltipForNextFrame(font, m.item(), mouseX, mouseY);
			}
			graphics.text(font, count, x + COLUMN - countWidth, row, m.stocked() >= m.needed() ? GOOD : BAD, false);
		}
		if (shown < all.size()) {
			int x = left + 12 + (shown % 2) * (COLUMN + 2);
			graphics.text(font, Component.translatable("screen.minecities.building.more", all.size() - shown), x + 18,
					y + (shown / 2) * 13, FADED, false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
