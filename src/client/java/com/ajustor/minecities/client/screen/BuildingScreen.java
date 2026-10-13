package com.ajustor.minecities.client.screen;

import com.ajustor.minecities.network.BuildingNetwork;
import com.ajustor.minecities.network.BuildingSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * L'interface d'un bâtiment : ce qu'il est, son niveau, ses lits et ses postes, son dernier relevé ;
 * le chantier en cours à sa place ; et son amélioration, avec le niveau suivant, ce qu'il coûte et
 * ce que le grenier en a déjà, ou ce qui l'empêche. Le maire (ou un adjoint) la commande d'un bouton.
 * Un bâtiment fait à la main se rebâtit au niveau 2 de sa famille, à sa place.
 */
public class BuildingScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int HEIGHT = 222;
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
			Button upgrade = addRenderableWidget(Button.builder(Component.translatable(building.rebuild()
									? "screen.minecities.building.rebuild" : "screen.minecities.building.upgrade",
							building.next().get()),
					b -> ClientPlayNetworking.send(new BuildingNetwork.ActionPayload(building.plaque(), "upgrade")))
					.bounds(left + 10, top + HEIGHT - 26, 190, 18).build());
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
		graphics.text(font, building.status(), left + 12, y, building.valid() ? GOOD : BAD, false);
		y += 13;
		if (building.beds() > 0) {
			graphics.text(font, Component.translatable("screen.minecities.building.beds", building.sleepers(),
					building.beds()), left + 12, y, INK, false);
			y += 12;
		}
		if (!building.jobs().isEmpty()) {
			MutableComponent jobs = Component.empty();
			for (String job : building.jobs()) {
				if (!jobs.getSiblings().isEmpty()) {
					jobs.append(", ");
				}
				jobs.append(Component.translatable(job));
			}
			graphics.text(font, Component.translatable("screen.minecities.building.jobs", jobs), left + 12, y, INK, false);
			y += 12;
		}
		if (building.placed() >= 0) {
			graphics.text(font, Component.translatable("screen.minecities.building.site", building.placed(),
					building.total()), left + 12, y, ACCENT, false);
			y += 12;
		}
		y += 6;
		graphics.fill(left + 8, y, left + WIDTH - 8, y + 1, EDGE);
		y += 6;
		// L'amélioration.
		if (building.next().isPresent()) {
			graphics.text(font, Component.translatable("screen.minecities.building.next", building.next().get()),
					left + 12, y, ACCENT, false);
			y += 11;
			if (building.rebuild()) {
				// Fait à la main : le bâtisseur le démolit pour bâtir le nouveau à sa place.
				graphics.text(font, Component.translatable("screen.minecities.building.replaces"), left + 12, y, BAD, false);
				y += 11;
			}
			y += 2;
			for (BuildingSnapshot.Material m : building.materials()) {
				graphics.item(m.item(), left + 14, y - 4);
				graphics.text(font, m.item().getHoverName(), left + 34, y, INK, false);
				graphics.text(font, Component.translatable("screen.minecities.building.material", m.stocked(), m.needed()),
						left + WIDTH - 70, y, m.stocked() >= m.needed() ? GOOD : BAD, false);
				y += 13;
			}
		}
		building.refusal().ifPresent(r -> graphics.text(font, r, left + 12, top + HEIGHT - 40, BAD, false));
		if (!building.manager() && building.next().isPresent()) {
			graphics.text(font, Component.translatable("screen.minecities.mayor_decides"), left + 12, top + HEIGHT - 20,
					FADED, false);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
