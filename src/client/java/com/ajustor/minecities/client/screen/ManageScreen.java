package com.ajustor.minecities.client.screen;

import com.ajustor.minecities.network.TownActionPayload;
import com.ajustor.minecities.network.TownSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Gérer la mairie sans commande (MC-75) : renommer la ville, nommer ou retirer des adjoints, transmettre
 * la mairie (au maire seul), proposer ou rompre des accords de commerce (au maire et aux adjoints).
 * Le serveur revérifie tout ; il renvoie l'état de la ville après chaque décision.
 */
public class ManageScreen extends Screen {
	private static final int WIDTH = 320;
	private static final int HEIGHT = 220;
	private static final int ROW = 18;
	private static final int PLAYER_ROWS = 3;
	private static final int TOWN_ROWS = 2;
	private static final int PAPER = 0xFFEDE3C8;
	private static final int EDGE = 0xFF5A4A33;
	private static final int INK = 0xFF2B2B40;
	private static final int FADED = 0xFF7A7060;
	private static final int GOOD = 0xFF2E7D32;
	private static final int ACCENT = 0xFF8A5A1E;

	private TownSnapshot town;
	private final Screen parent;
	private int left;
	private int top;
	private int playerPage;
	private int townPage;
	/** Le nom tapé (gardé quand l'écran se rafraîchit) ; null tant qu'on n'y a pas touché. */
	private String typed;
	/** Le joueur à qui l'on s'apprête à transmettre la mairie : le second clic confirme. */
	private UUID transferring;

	public ManageScreen(TownSnapshot town, Screen parent) {
		super(Component.translatable("screen.minecities.manage.title"));
		this.town = town;
		this.parent = parent;
	}

	public boolean shows(TownSnapshot snapshot) {
		return town.hall().equals(snapshot.hall());
	}

	/** Le serveur a renvoyé l'état de la ville (après une décision). */
	public void refresh(TownSnapshot snapshot) {
		town = snapshot;
		if (parent instanceof TownHallScreen hall) {
			hall.refresh(snapshot);
		}
		rebuildWidgets();
	}

	private void send(String action, UUID target) {
		ClientPlayNetworking.send(new TownActionPayload(town.hall(), action, target));
	}

	/** Les autres joueurs de la ville (pas le maire). */
	private List<TownSnapshot.PlayerRow> players() {
		return town.members().players().stream().filter(p -> !p.rank().equals("mayor")).toList();
	}

	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		boolean mayor = town.council().mayorView();
		EditBox name = addRenderableWidget(new EditBox(font, left + 10, top + 26, 200, 16,
				Component.translatable("screen.minecities.manage.name")));
		name.setMaxLength(32);
		name.setValue(typed != null ? typed : town.name());
		name.setResponder(value -> typed = value);
		name.active = mayor;
		Button rename = addRenderableWidget(Button.builder(Component.translatable("screen.minecities.manage.rename"),
				b -> send("rename:" + name.getValue().trim(), TownActionPayload.NONE)).bounds(left + 216, top + 26, 94, 16)
				.build());
		rename.active = mayor;
		// Les élections de la ville, au maire (MC-91).
		Button elections = addRenderableWidget(Button.builder(Component.translatable(town.council().elections()
						? "screen.minecities.manage.elections.on" : "screen.minecities.manage.elections.off"),
				b -> send("elections.toggle", TownActionPayload.NONE))
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("screen.minecities.manage.elections.tooltip")))
				.bounds(left + 10, top + HEIGHT - 24, 120, 18).build());
		elections.active = mayor;

		List<TownSnapshot.PlayerRow> players = players();
		int y = top + 62;
		for (TownSnapshot.PlayerRow row : page(players, playerPage, PLAYER_ROWS)) {
			boolean deputy = row.rank().equals("deputy");
			Button toggle = addRenderableWidget(Button.builder(Component.translatable(deputy
							? "screen.minecities.manage.deputy.remove" : "screen.minecities.manage.deputy.add"),
					b -> send(deputy ? "deputy.remove" : "deputy.add", row.id())).bounds(left + 150, y - 3, 74, 16).build());
			toggle.active = mayor;
			boolean confirming = row.id().equals(transferring);
			Button transfer = addRenderableWidget(Button.builder(Component.translatable(confirming
							? "screen.minecities.manage.transfer.confirm" : "screen.minecities.manage.transfer"), b -> {
				if (row.id().equals(transferring)) {
					send("transfer", row.id());
					transferring = null;
				} else {
					transferring = row.id();
					rebuildWidgets();
				}
			}).bounds(left + 228, y - 3, 82, 16).build());
			transfer.active = mayor;
			y += ROW;
		}
		pager(players.size(), PLAYER_ROWS, top + 46, playerPage, p -> playerPage = p);

		List<String> towns = town.council().towns();
		y = top + 136;
		for (int i = townPage * TOWN_ROWS; i < Math.min(towns.size(), (townPage + 1) * TOWN_ROWS); i++) {
			String other = towns.get(i);
			boolean offered = town.council().offered().get(i);
			addRenderableWidget(Button.builder(Component.translatable(offered ? "screen.minecities.manage.trade.end"
							: "screen.minecities.manage.trade.offer"), b -> send("trade:" + other, TownActionPayload.NONE))
					.bounds(left + 228, y - 3, 82, 16).build()).active = town.manager();
			y += ROW;
		}
		pager(towns.size(), TOWN_ROWS, top + 120, townPage, p -> townPage = p);

		addRenderableWidget(Button.builder(Component.translatable("screen.minecities.manage.back"),
				b -> minecraft.gui.setScreen(parent)).bounds(left + WIDTH - 90, top + HEIGHT - 24, 80, 18).build());
	}

	private static <T> List<T> page(List<T> all, int page, int rows) {
		return all.subList(Math.min(all.size(), page * rows), Math.min(all.size(), (page + 1) * rows));
	}

	/** Les flèches d'une liste, à droite de son titre. */
	private void pager(int count, int rows, int y, int page, java.util.function.IntConsumer set) {
		int pages = Math.max(1, (count + rows - 1) / rows);
		if (pages <= 1) {
			return;
		}
		Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			set.accept(page - 1);
			rebuildWidgets();
		}).bounds(left + WIDTH - 50, y - 4, 18, 14).build());
		previous.active = page > 0;
		Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			set.accept(page + 1);
			rebuildWidgets();
		}).bounds(left + WIDTH - 28, y - 4, 18, 14).build());
		next.active = page < pages - 1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(left - 3, top - 3, left + WIDTH + 3, top + HEIGHT + 3, EDGE);
		graphics.fill(left, top, left + WIDTH, top + HEIGHT, PAPER);
		graphics.text(font, Component.translatable("screen.minecities.manage.title.town", town.name()), left + 10,
				top + 9, INK, false);
		graphics.text(font, Component.translatable("screen.minecities.manage.players"), left + 10, top + 46, ACCENT,
				false);
		List<TownSnapshot.PlayerRow> players = players();
		if (players.isEmpty()) {
			graphics.text(font, Component.translatable("screen.minecities.manage.no_players"), left + 14, top + 62, FADED,
					false);
		}
		int y = top + 62;
		for (TownSnapshot.PlayerRow row : page(players, playerPage, PLAYER_ROWS)) {
			graphics.text(font, Component.literal(row.name()), left + 14, y, INK, false);
			graphics.text(font, Component.translatable("rank.minecities." + row.rank()), left + 90, y, FADED, false);
			y += ROW;
		}
		graphics.text(font, Component.translatable("screen.minecities.manage.trade"), left + 10, top + 120, ACCENT,
				false);
		List<String> towns = town.council().towns();
		if (towns.isEmpty()) {
			graphics.text(font, Component.translatable("screen.minecities.manage.no_towns"), left + 14, top + 136, FADED,
					false);
		}
		y = top + 136;
		for (int i = townPage * TOWN_ROWS; i < Math.min(towns.size(), (townPage + 1) * TOWN_ROWS); i++) {
			String other = towns.get(i);
			int partner = town.council().partners().indexOf(other);
			boolean open = partner >= 0 && town.council().open().get(partner);
			String state = open ? "open" : town.council().offered().get(i) ? "offered" : partner >= 0 ? "theirs" : "none";
			graphics.text(font, Component.literal(other), left + 14, y, INK, false);
			if (!state.equals("none")) {
				graphics.text(font, Component.translatable("screen.minecities.manage.trade." + state), left + 110, y,
						open ? GOOD : FADED, false);
			}
			y += ROW;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
