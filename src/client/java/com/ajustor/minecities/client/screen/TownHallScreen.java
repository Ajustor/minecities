package com.ajustor.minecities.client.screen;

import com.ajustor.minecities.network.TownActionPayload;
import com.ajustor.minecities.network.TownSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * L'écran de l'hôtel de ville, l'interface d'administration de la cité : le résumé de la ville (et
 * son moral, ses fêtes, l'alerte), ses citoyens (métier, faim, humeur, famille, ce qui leur manque ;
 * un bouton les fait luire pour les retrouver), ses bâtiments, et le registre où le maire accepte ou
 * refuse les demandes de mariage. Un bouton ouvre le grenier.
 */
public class TownHallScreen extends Screen {
	private static final int WIDTH = 380;
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
	private static final int FESTIVE = 0xFF9C27B0;

	public enum Tab { SUMMARY, CITIZENS, BUILDINGS, REGISTRY }

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

	private void send(String action, java.util.UUID target) {
		ClientPlayNetworking.send(new TownActionPayload(town.hall(), action, target));
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
			Button button = addRenderableWidget(Button.builder(label, b -> select(t))
					.bounds(x, top + 26, 70, 18).build());
			button.active = tab != t;
			x += 74;
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.minecities.granary"),
				b -> send("granary", TownActionPayload.NONE)).bounds(left + WIDTH - 72, top + 26, 62, 18).build());
		switch (tab) {
			case SUMMARY -> {
				if (town.manager()) {
					Button festival = addRenderableWidget(Button.builder(Component.translatable(
									"screen.minecities.festival", town.life().festivalCost()),
							b -> send("festival", TownActionPayload.NONE))
							.bounds(left + WIDTH - 170, top + HEIGHT - 26, 160, 18).build());
					festival.active = town.life().canCelebrate();
				}
			}
			case CITIZENS -> {
				List<TownSnapshot.CitizenRow> rows = visible(town.rows());
				int y = top + 54;
				for (TownSnapshot.CitizenRow row : rows) {
					addRenderableWidget(Button.builder(Component.translatable("screen.minecities.locate"),
							b -> send("locate", row.id())).bounds(left + WIDTH - 44, y, 34, 16).build());
					y += ROW;
				}
				pager(town.rows().size());
			}
			case BUILDINGS -> pager(town.buildings().size());
			case REGISTRY -> {
				if (town.manager()) {
					int y = top + 56;
					for (TownSnapshot.RequestRow request : town.requests().stream().limit(ROWS).toList()) {
						addRenderableWidget(Button.builder(Component.translatable("screen.minecities.accept"),
								b -> send("accept", request.id())).bounds(left + WIDTH - 150, y, 66, 18).build());
						addRenderableWidget(Button.builder(Component.translatable("screen.minecities.refuse"),
								b -> send("refuse", request.id())).bounds(left + WIDTH - 80, y, 66, 18).build());
						y += ROW + 4;
					}
				}
			}
		}
	}

	private <T> List<T> visible(List<T> all) {
		return all.subList(Math.min(all.size(), page * ROWS), Math.min(all.size(), (page + 1) * ROWS));
	}

	/** Les boutons pour tourner les pages d'une liste. */
	private void pager(int count) {
		int pages = Math.max(1, (count + ROWS - 1) / ROWS);
		if (pages <= 1) {
			return;
		}
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
			case BUILDINGS -> buildings(graphics);
			case REGISTRY -> registry(graphics);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void line(GuiGraphicsExtractor graphics, Component text, int y, int color) {
		graphics.text(font, text, left + 14, y, color, false);
	}

	/** La couleur d'une humeur : vert content, ocre mitigé, rouge malheureux. */
	private static int moodColor(double mood) {
		return mood >= 7 ? GOOD : mood > 3 ? ACCENT : BAD;
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
		y += 14;
		TownSnapshot.Life life = town.life();
		if (life.mood() >= 0) {
			double mood = life.mood() / 10.0;
			line(graphics, Component.translatable("screen.minecities.mood", String.format("%.1f", mood)), y,
					moodColor(mood));
			y += 14;
		}
		if (life.alarmed()) {
			line(graphics, Component.translatable("screen.minecities.alarmed"), y, BAD);
			y += 14;
		}
		if (life.partying()) {
			line(graphics, Component.translatable("screen.minecities.partying"), y, FESTIVE);
			y += 14;
		} else if (!life.canCelebrate()) {
			long minutes = Math.max(1, life.untilFestival() / 1200);
			line(graphics, Component.translatable("screen.minecities.next_festival", minutes), y, FADED);
			y += 14;
		}
		y += 6;
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
		for (TownSnapshot.CitizenRow row : visible(rows)) {
			Component name = row.child() ? Component.translatable("screen.minecities.child", row.name())
					: Component.literal(row.name());
			graphics.text(font, name, left + 14, y, INK, false);
			Component job = row.job().isEmpty() ? Component.translatable(row.child() ? "screen.minecities.too_young"
					: "citizen.minecities.jobless") : Component.translatable(row.job());
			graphics.text(font, job, left + 140, y, FADED, false);
			graphics.text(font, Component.translatable(row.hunger()), left + 230, y,
					row.hunger().endsWith("starving") || row.hunger().endsWith("hungry") ? BAD : GOOD, false);
			if (row.mood() >= 0) {
				graphics.text(font, Component.translatable("screen.minecities.mood.short", row.mood()), left + 300, y,
						moodColor(row.mood()), false);
			}
			Component second = row.need().<Component>map(n -> Component.literal("« ").append(n).append(" »"))
					.orElse(row.spouse().isEmpty() ? (row.housed() ? Component.empty()
							: Component.translatable("citizen.minecities.homeless"))
							: Component.translatable("screen.minecities.spouse", row.spouse()));
			graphics.text(font, second, left + 22, y + 10, row.need().isPresent() || !row.housed() ? BAD : ACCENT,
					false);
			y += ROW;
		}
	}

	private void buildings(GuiGraphicsExtractor graphics) {
		List<TownSnapshot.BuildingRow> buildings = town.buildings();
		if (buildings.isEmpty()) {
			line(graphics, Component.translatable("screen.minecities.no_buildings"), top + 58, FADED);
			return;
		}
		int y = top + 56;
		for (TownSnapshot.BuildingRow building : visible(buildings)) {
			String kind = building.village() ? "screen.minecities.building.village"
					: building.beds() > 0 && !building.jobs().isEmpty() ? "screen.minecities.building.both"
					: building.beds() > 0 ? "screen.minecities.building.home" : "screen.minecities.building.workshop";
			graphics.text(font, Component.translatable(kind), left + 14, y, INK, false);
			graphics.text(font, Component.literal(building.plaque().toShortString()), left + 140, y, FADED, false);
			if (building.beds() > 0) {
				graphics.text(font, Component.translatable("screen.minecities.building.beds", building.sleepers(),
						building.beds()), left + 250, y, building.sleepers() < building.beds() ? GOOD : FADED, false);
			}
			if (!building.jobs().isEmpty()) {
				MutableComponent jobs = Component.empty();
				for (String job : building.jobs()) {
					if (!jobs.getSiblings().isEmpty()) {
						jobs.append(", ");
					}
					jobs.append(Component.translatable(job));
				}
				graphics.text(font, jobs, left + 22, y + 10, ACCENT, false);
			}
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
