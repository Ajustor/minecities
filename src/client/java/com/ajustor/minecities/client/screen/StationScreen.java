package com.ajustor.minecities.client.screen;

import com.ajustor.minecities.network.StationNetwork;
import com.ajustor.minecities.network.StationSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * L'interface d'un poste de travail : le métier et ce qu'il fait, l'état du poste (s'il sert, et
 * sinon pourquoi), ses travailleurs (ce qu'ils font, ce qui leur manque, « Où ? » pour les voir
 * luire) ; pour le bâtisseur, les chantiers de la ville et leur avancement.
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

	private final StationSnapshot station;
	private int left;
	private int top;

	public StationScreen(StationSnapshot station) {
		super(Component.translatable(station.block()));
		this.station = station;
	}

	public StationSnapshot station() {
		return station;
	}

	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		int y = workersTop();
		for (StationSnapshot.Worker worker : station.workers().stream().limit(MAX_WORKERS).toList()) {
			addRenderableWidget(Button.builder(Component.translatable("screen.minecities.locate"),
					b -> ClientPlayNetworking.send(new StationNetwork.ActionPayload(station.pos(), "locate", worker.id())))
					.bounds(left + WIDTH - 44, y - 2, 34, 16).build());
			if (station.manager()) {
				addRenderableWidget(Button.builder(Component.translatable("screen.minecities.posting"),
						b -> ClientPlayNetworking.send(new StationNetwork.ActionPayload(station.pos(), "posting", worker.id())))
						.bounds(left + WIDTH - 86, y - 2, 40, 16).build());
			}
			y += ROW;
		}
		if (station.manager() && station.mine().equals("exhausted")) {
			addRenderableWidget(Button.builder(Component.translatable("screen.minecities.mine.close"),
					b -> ClientPlayNetworking.send(new StationNetwork.ActionPayload(station.pos(), "close_mine",
							new java.util.UUID(0, 0)))).bounds(left + 10, top + HEIGHT - 24, 130, 18).build());
			addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
					.bounds(left + WIDTH - 90, top + HEIGHT - 24, 80, 18).build());
			return;
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(left + WIDTH / 2 - 40, top + HEIGHT - 24, 80, 18).build());
	}

	/** Où commence la liste des travailleurs, sous la description. */
	private int workersTop() {
		return top + 34 + 10 * description().size() + 30;
	}

	private List<FormattedCharSequence> description() {
		return font.split(Component.translatable("station.minecities." + station.job()), WIDTH - 24);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(left - 3, top - 3, left + WIDTH + 3, top + HEIGHT + 3, EDGE);
		graphics.fill(left, top, left + WIDTH, top + HEIGHT, PAPER);
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
		y = workersTop();
		for (StationSnapshot.Worker worker : station.workers().stream().limit(MAX_WORKERS).toList()) {
			graphics.text(font, Component.literal(worker.name()), left + 12, y, INK, false);
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
				graphics.text(font, site.name(), left + 20, y, INK, false);
				int bar = 80;
				int x = left + WIDTH - 20 - bar;
				int done = site.total() == 0 ? 0 : bar * site.placed() / site.total();
				graphics.fill(x, y + 1, x + bar, y + 7, 0xFFB8AA88);
				graphics.fill(x, y + 1, x + done, y + 7, GOOD);
				graphics.text(font, Component.translatable("screen.minecities.station.progress", site.placed(), site.total()),
						x - 64, y, FADED, false);
				y += 12;
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
