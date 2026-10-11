package com.ajustor.minecities.client.screen;

import com.ajustor.minecities.network.TownActionPayload;
import com.ajustor.minecities.network.TownSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * L'écran de l'hôtel de ville, l'interface d'administration de la cité : le résumé de la ville, ses
 * citoyens (métier, faim, famille, ce qui leur manque), et le registre où le maire accepte ou refuse
 * les demandes de mariage. Un bouton ouvre le grenier.
 */
public class TownHallScreen extends Screen {
	private static final int WIDTH = 340;
	private static final int HEIGHT = 230;
	private static final int ROWS = 7;
	private static final int ROW = 22;
	private static final int PAPER = 0xFFEDE3C8;
	private static final int EDGE = 0xFF5A4A33;
	private static final int INK = 0xFF2B2B40;
	private static final int FADED = 0xFF7A7060;
	private static final int GOOD = 0xFF2E7D32;
	private static final int BAD = 0xFFB0201A;
	private static final int ACCENT = 0xFF8A5A1E;

	public enum Tab { SUMMARY, CITIZENS, REGISTRY }

	private TownSnapshot town;
	private Tab tab = Tab.SUMMARY;
	private int page;
	private int left;
	private int top;

	public TownHallScreen(TownSnapshot town) {
		super(Component.literal(town.name()));
		this.town = town;
	}

	/** Le serveur a envoyé un nouvel état (après une décision du maire). */
	public void refresh(TownSnapshot snapshot) {
		town = snapshot;
		rebuildWidgets();
	}

	/** Passe à cet onglet. */
	public void select(Tab tab) {
		this.tab = tab;
		page = 0;
		rebuildWidgets();
	}

	public boolean shows(TownSnapshot snapshot) {
		return town.hall().equals(snapshot.hall());
	}

	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		int x = left + 10;
		for (Tab t : Tab.values()) {
			Component label = Component.translatable("screen.minecities.tab." + t.name().toLowerCase());
			if (t == Tab.REGISTRY && !town.requests().isEmpty()) {
				label = Component.translatable("screen.minecities.tab.registry_count", town.requests().size());
			}
			Button button = addRenderableWidget(Button.builder(label, b -> {
				tab = t;
				page = 0;
				rebuildWidgets();
			}).bounds(x, top + 26, 80, 18).build());
			button.active = tab != t;
			x += 84;
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.minecities.granary"),
				b -> ClientPlayNetworking.send(new TownActionPayload(town.hall(), "granary", TownActionPayload.NONE)))
				.bounds(left + WIDTH - 72, top + 26, 62, 18).build());
		if (tab == Tab.CITIZENS) {
			int pages = Math.max(1, (town.rows().size() + ROWS - 1) / ROWS);
			if (pages > 1) {
				Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
					page--;
					rebuildWidgets();
				}).bounds(left + WIDTH - 60, top + HEIGHT - 24, 20, 18).build());
				previous.active = page > 0;
				Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
					page++;
					rebuildWidgets();
				}).bounds(left + WIDTH - 32, top + HEIGHT - 24, 20, 18).build());
				next.active = page < pages - 1;
			}
		}
		if (tab == Tab.REGISTRY && town.manager()) {
			int y = top + 56;
			for (TownSnapshot.RequestRow request : town.requests().stream().limit(ROWS).toList()) {
				addRenderableWidget(Button.builder(Component.translatable("screen.minecities.accept"),
						b -> ClientPlayNetworking.send(new TownActionPayload(town.hall(), "accept", request.id())))
						.bounds(left + WIDTH - 150, y, 66, 18).build());
				addRenderableWidget(Button.builder(Component.translatable("screen.minecities.refuse"),
						b -> ClientPlayNetworking.send(new TownActionPayload(town.hall(), "refuse", request.id())))
						.bounds(left + WIDTH - 80, y, 66, 18).build());
				y += ROW + 4;
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(left - 3, top - 3, left + WIDTH + 3, top + HEIGHT + 3, EDGE);
		graphics.fill(left, top, left + WIDTH, top + HEIGHT, PAPER);
		graphics.text(font, Component.translatable("screen.minecities.title", town.name(),
				Component.translatable(town.tier())), left + 10, top + 9, INK, false);
		graphics.fill(left + 8, top + 48, left + WIDTH - 8, top + 49, EDGE);
		switch (tab) {
			case SUMMARY -> summary(graphics);
			case CITIZENS -> citizens(graphics);
			case REGISTRY -> registry(graphics);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void line(GuiGraphicsExtractor graphics, Component text, int y, int color) {
		graphics.text(font, text, left + 14, y, color, false);
	}

	private void summary(GuiGraphicsExtractor graphics) {
		int y = top + 58;
		line(graphics, Component.translatable("screen.minecities.population", town.population(), town.citizens(),
				town.villagers()), y, INK);
		y += 14;
		line(graphics, Component.translatable("screen.minecities.homes", town.homes(), town.capacity()), y, INK);
		y += 14;
		line(graphics, Component.translatable("screen.minecities.jobs", town.employed(), town.workstations()), y, INK);
		y += 14;
		line(graphics, Component.translatable("screen.minecities.meals", town.meals()), y,
				town.meals() > 0 ? INK : BAD);
		y += 20;
		if (!town.nextTier().isEmpty()) {
			line(graphics, Component.translatable("screen.minecities.next", Component.translatable(town.nextTier())),
					y, ACCENT);
			y += 14;
			line(graphics, Component.translatable("screen.minecities.next.needs", town.population(),
					town.nextPopulation(), town.homes(), town.nextHouses()), y, FADED);
			y += 14;
		}
		if (!town.requests().isEmpty()) {
			y += 6;
			line(graphics, Component.translatable("screen.minecities.pending", town.requests().size()), y, ACCENT);
		}
	}

	private void citizens(GuiGraphicsExtractor graphics) {
		List<TownSnapshot.CitizenRow> rows = town.rows();
		if (rows.isEmpty()) {
			line(graphics, Component.translatable("screen.minecities.no_citizens"), top + 58, FADED);
			return;
		}
		int y = top + 56;
		for (TownSnapshot.CitizenRow row : rows.subList(page * ROWS, Math.min(rows.size(), (page + 1) * ROWS))) {
			Component name = row.child() ? Component.translatable("screen.minecities.child", row.name())
					: Component.literal(row.name());
			graphics.text(font, name, left + 14, y, INK, false);
			Component job = row.job().isEmpty() ? Component.translatable(row.child() ? "screen.minecities.too_young"
					: "citizen.minecities.jobless") : Component.translatable(row.job());
			graphics.text(font, job, left + 130, y, FADED, false);
			graphics.text(font, Component.translatable(row.hunger()), left + 220, y,
					row.hunger().endsWith("starving") || row.hunger().endsWith("hungry") ? BAD : GOOD, false);
			Component second = row.need().<Component>map(n -> Component.literal("« ").append(n).append(" »"))
					.orElse(row.spouse().isEmpty() ? (row.housed() ? Component.empty()
							: Component.translatable("citizen.minecities.homeless"))
							: Component.translatable("screen.minecities.spouse", row.spouse()));
			graphics.text(font, second, left + 22, y + 10, row.need().isPresent() || !row.housed() ? BAD : ACCENT,
					false);
			y += ROW;
		}
	}

	private void registry(GuiGraphicsExtractor graphics) {
		if (town.requests().isEmpty()) {
			line(graphics, Component.translatable("screen.minecities.no_requests"), top + 58, FADED);
			return;
		}
		int y = top + 61;
		for (TownSnapshot.RequestRow request : town.requests().stream().limit(ROWS).toList()) {
			line(graphics, Component.translatable("screen.minecities.request", request.first(), request.second()), y,
					INK);
			y += ROW + 4;
		}
		if (!town.manager()) {
			line(graphics, Component.translatable("screen.minecities.mayor_decides"), top + HEIGHT - 18, FADED);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
