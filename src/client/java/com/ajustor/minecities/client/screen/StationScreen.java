package com.ajustor.minecities.client.screen;

import com.ajustor.minecities.network.StationNetwork;
import com.ajustor.minecities.network.StationSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * L'interface d'un poste de travail : le métier et ce qu'il fait, l'état du poste (s'il sert, et
 * sinon pourquoi), ses travailleurs (ce qu'ils font, ce qui leur manque, « Où ? » pour les voir
 * luire) ; pour le bâtisseur, les chantiers de la ville et leur avancement, ce qui leur manque, et un
 * bouton pour le déposer de son inventaire.
 */
public class StationScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int HEIGHT = 200;
	private static final int PAPER = 0xFFEDE3C8;
	private static final int EDGE = 0xFF5A4A33;
	private static final int INK = 0xFF2B2B40;
	private static final int FADED = 0xFF7A7060;
	private static final int GOOD = 0xFF2E7D32;
	private static final int BAD = 0xFFB0201A;
	private static final int ACCENT = 0xFF8A5A1E;
	private static final int ROW = 22;
	private static final int MAX_WORKERS = 3;
	private static final int MAX_SITES = 3;
	/** Ce qui manque aux chantiers : une case par matériau (l'icône, le nombre), sur deux rangées au plus. */
	private static final int CELL = 38;
	private static final int CELL_ROW = 20;
	private static final int MAX_CELL_ROWS = 2;

	private StationSnapshot station;
	private int left;
	private int top;
	/** La hauteur du cadre : plus haut que d'habitude quand le bâtisseur a beaucoup à montrer. */
	private int boxHeight = HEIGHT;

	public StationScreen(StationSnapshot station) {
		super(Component.translatable(station.block()));
		this.station = station;
	}

	public StationSnapshot station() {
		return station;
	}

	public boolean shows(StationSnapshot snapshot) {
		return snapshot.pos().equals(station.pos());
	}

	/** Le serveur a renvoyé l'état du poste. */
	public void refresh(StationSnapshot snapshot) {
		station = snapshot;
		rebuildWidgets();
	}

	/** Tous les combien de ticks l'écran ouvert redemande l'état au serveur (MC-72). */
	private static final int REFRESH = 40;
	private int age;

	@Override
	public void tick() {
		super.tick();
		if (++age % REFRESH == 0) {
			ClientPlayNetworking.send(new StationNetwork.ActionPayload(station.pos(), "refresh", new java.util.UUID(0, 0)));
		}
	}


	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		boxHeight = Math.max(HEIGHT, contentHeight() + 30);
		top = (height - boxHeight) / 2;
		int y = workersTop();
		for (StationSnapshot.Worker worker : station.workers().stream().limit(MAX_WORKERS).toList()) {
			addRenderableWidget(Button.builder(Component.translatable("screen.minecities.locate"),
					b -> ClientPlayNetworking.send(new StationNetwork.ActionPayload(station.pos(), "locate", worker.id())))
					.tooltip(Tooltip.create(Component.translatable("screen.minecities.locate.tooltip"))).bounds(left + WIDTH - 44, y - 2, 34, 16).build());
			if (station.manager()) {
				addRenderableWidget(Button.builder(Component.translatable("screen.minecities.posting"),
						b -> ClientPlayNetworking.send(new StationNetwork.ActionPayload(station.pos(), "posting", worker.id())))
						.tooltip(Tooltip.create(Component.translatable("screen.minecities.posting.tooltip"))).bounds(left + WIDTH - 86, y - 2, 40, 16).build());
			}
			y += ROW;
		}
		// Suspendre ou reprendre un chantier (MC-84), au bout de sa ligne.
		if (station.manager() && station.mine().isEmpty()) {
			int sy = y + 12;
			for (StationSnapshot.Site site : station.sites().stream().limit(MAX_SITES).toList()) {
				addRenderableWidget(Button.builder(Component.literal(site.paused() ? "▶" : "Ⅱ"),
						b -> ClientPlayNetworking.send(new StationNetwork.ActionPayload(station.pos(), "pause", site.id())))
						.tooltip(Tooltip.create(Component.translatable(site.paused() ? "screen.minecities.station.resume"
								: "screen.minecities.station.pause"))).bounds(left + WIDTH - 22, sy - 2, 12, 12).build());
				sy += 12;
			}
		}
		if (station.manager() && station.mine().equals("exhausted")) {
			addRenderableWidget(Button.builder(Component.translatable("screen.minecities.mine.close"),
					b -> ClientPlayNetworking.send(new StationNetwork.ActionPayload(station.pos(), "close_mine",
							new java.util.UUID(0, 0)))).bounds(left + 10, top + boxHeight - 24, 130, 18).build());
			addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
					.bounds(left + WIDTH - 90, top + boxHeight - 24, 80, 18).build());
			return;
		}
		if (!station.missing().isEmpty()) {
			// Déposer d'un clic, de son inventaire, ce qui manque aux chantiers.
			addRenderableWidget(Button.builder(Component.translatable("screen.minecities.station.supply"),
					b -> ClientPlayNetworking.send(new StationNetwork.ActionPayload(station.pos(), "supply",
							new java.util.UUID(0, 0))))
					.tooltip(Tooltip.create(Component.translatable("screen.minecities.station.supply.tooltip")))
					.bounds(left + 10, top + boxHeight - 24, 170, 18).build());
			addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
					.bounds(left + WIDTH - 90, top + boxHeight - 24, 80, 18).build());
			return;
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(left + WIDTH / 2 - 40, top + boxHeight - 24, 80, 18).build());
	}

	/** Où commence la liste des travailleurs, sous la description. */
	private int workersTop() {
		return top + workersOffset();
	}

	private int workersOffset() {
		return 34 + 10 * description().size() + 30;
	}

	/** La hauteur de ce qu'il y a à montrer, du haut du cadre jusqu'aux boutons (sans eux). */
	private int contentHeight() {
		int y = workersOffset() + ROW * (int) Math.min(MAX_WORKERS, station.workers().size());
		if (!station.sites().isEmpty()) {
			y += 12 + 12 * (int) Math.min(MAX_SITES, station.sites().size());
			y += 16 + CELL_ROW * missingRows();
		}
		return y;
	}

	/** Combien de cases tiennent sur une rangée. */
	private static int cellsPerRow() {
		return (WIDTH - 24) / CELL;
	}

	/** Les rangées de cases de ce qui manque (une pour dire que rien ne manque). */
	private int missingRows() {
		return Math.max(1, Math.min(MAX_CELL_ROWS, (station.missing().size() + cellsPerRow() - 1) / cellsPerRow()));
	}

	private List<FormattedCharSequence> description() {
		return font.split(Component.translatable("station.minecities." + station.job()), WIDTH - 24);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(left - 3, top - 3, left + WIDTH + 3, top + boxHeight + 3, EDGE);
		graphics.fill(left, top, left + WIDTH, top + boxHeight, PAPER);
		Component title = station.town().isEmpty() ? getTitle()
				: Component.translatable("screen.minecities.station.title", getTitle(), station.town());
		graphics.text(font, title, left + 10, top + 9, INK, false);
		graphics.fill(left + 8, top + 22, left + WIDTH - 8, top + 23, EDGE);
		int y = top + 30;
		for (FormattedCharSequence line : description()) {
			graphics.text(font, line, left + 12, y, FADED, false);
			y += 10;
		}
		y += 6;
		boolean fine = station.status().equals("ok");
		Component status = station.detail().isEmpty()
				? Component.translatable("screen.minecities.station.status." + station.status())
				: Component.translatable("screen.minecities.station.status." + station.status(),
						Component.translatable(station.detail()));
		graphics.text(font, status, left + 12, y, fine ? GOOD : BAD, false);
		if (!station.mastery().isEmpty()) {
			graphics.text(font, Component.translatable("screen.minecities.station.mastery." + station.mastery()),
					left + 12, y + 12, station.mastery().equals("active") ? GOOD : FADED, false);
		}
		y = workersTop();
		for (StationSnapshot.Worker worker : station.workers().stream().limit(MAX_WORKERS).toList()) {
			graphics.text(font, Component.literal(worker.name()), left + 12, y, INK, false);
			// Son rang (MC-83), après ce qu'il fait.
			if (!worker.rank().isEmpty()) {
				graphics.text(font, Component.translatable("experience.minecities." + worker.rank()), left + 196, y,
						FADED, false);
			}
			graphics.text(font, Component.translatable("screen.minecities.station.worker." + worker.state()),
					left + 120, y, worker.state().equals("working") ? GOOD : ACCENT, false);
			int needY = y + 10;
			worker.need().ifPresent(need -> graphics.text(font, Component.literal("« ").append(need).append(" »"),
					left + 20, needY, BAD, false));
			y += ROW;
		}
		if (!station.mine().isEmpty()) {
			graphics.text(font, Component.translatable("screen.minecities.mine." + station.mine()), left + 12, y,
					station.mine().equals("digging") || station.mine().equals("new") ? ACCENT : BAD, false);
			y += 12;
			if (station.ores().isEmpty()) {
				graphics.text(font, Component.translatable("screen.minecities.mine.none"), left + 20, y, FADED, false);
			}
			int x = left + 14;
			for (StationSnapshot.Ore ore : station.ores()) {
				// Deux colonnes : l'icône, le nom, ce qui reste (et ce qui est extrait).
				graphics.item(ore.item(), x, y - 4);
				graphics.text(font, ore.item().getHoverName(), x + 18, y, ore.remaining() > 0 ? INK : FADED, false);
				Component count = ore.remaining() > 0
						? Component.translatable("screen.minecities.mine.ore", ore.remaining(), ore.mined())
						: Component.translatable("screen.minecities.mine.ore_gone", ore.mined());
				graphics.text(font, count, x + 18, y + 9, ore.remaining() > 0 ? GOOD : BAD, false);
				if (x == left + 14) {
					x = left + WIDTH / 2 + 4;
				} else {
					x = left + 14;
					y += 22;
				}
			}
			y += x == left + 14 ? 0 : 22;
		}
		if (!station.sites().isEmpty()) {
			graphics.text(font, Component.translatable("screen.minecities.station.sites"), left + 12, y, ACCENT, false);
			y += 12;
			for (StationSnapshot.Site site : station.sites().stream().limit(MAX_SITES).toList()) {
				int bar = 80;
				int x = left + WIDTH - 28 - bar;
				Component name = site.paused() ? Component.translatable("screen.minecities.station.paused",
						clip(site.name().getString(), x - 64 - 6 - (left + 20) - 40))
						: Component.literal(clip(site.name().getString(), x - 64 - 6 - (left + 20)));
				graphics.text(font, name, left + 20, y, site.paused() ? FADED : INK, false);
				int done = site.total() == 0 ? 0 : bar * site.placed() / site.total();
				graphics.fill(x, y + 1, x + bar, y + 7, 0xFFB8AA88);
				graphics.fill(x, y + 1, x + done, y + 7, GOOD);
				graphics.text(font, Component.translatable("screen.minecities.station.progress", site.placed(), site.total()),
						x - 64, y, FADED, false);
				y += 12;
			}
			missing(graphics, y + 4, mouseX, mouseY);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** Ce qui manque aux chantiers : l'icône et le nombre de chaque matériau ; ce qui ne tient pas se compte à la fin. */
	private void missing(GuiGraphicsExtractor graphics, int y, int mouseX, int mouseY) {
		List<StationSnapshot.Shortage> all = station.missing();
		if (all.isEmpty()) {
			graphics.text(font, Component.translatable("screen.minecities.station.missing_none"), left + 12, y, GOOD,
					false);
			return;
		}
		graphics.text(font, Component.translatable("screen.minecities.station.missing"), left + 12, y, BAD, false);
		y += 14;
		int room = cellsPerRow() * MAX_CELL_ROWS;
		int shown = all.size() <= room ? all.size() : room - 1;
		for (int i = 0; i < shown; i++) {
			StationSnapshot.Shortage shortage = all.get(i);
			int x = left + 12 + (i % cellsPerRow()) * CELL;
			int row = y + (i / cellsPerRow()) * CELL_ROW;
			net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(shortage.item());
			graphics.item(stack, x, row - 4);
			graphics.text(font, Component.literal("×" + shortage.count()), x + 17, row, BAD, false);
			if (mouseX >= x && mouseX < x + CELL && mouseY >= row - 4 && mouseY < row + 12) {
				graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
			}
		}
		if (shown < all.size()) {
			int x = left + 12 + (shown % cellsPerRow()) * CELL;
			graphics.text(font, Component.translatable("screen.minecities.station.missing_more", all.size() - shown), x,
					y + (shown / cellsPerRow()) * CELL_ROW, FADED, false);
		}
	}

	/** Ce texte, coupé (avec « … ») pour tenir dans cette largeur. */
	private String clip(String text, int width) {
		return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
