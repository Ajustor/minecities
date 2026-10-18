package com.ajustor.minecities.client.screen;

import com.ajustor.minecities.network.PlanNetwork;
import com.ajustor.minecities.network.TownSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * L'interface du plan (MC-23) : le bâtiment choisi, le choix du style, la liste des schémas de la
 * ville (les fermés grisés, avec le palier qu'ils attendent) ; puis, une fois le plan posé au sol,
 * tourner, valider ou abandonner le chantier proposé. Le serveur revérifie chaque demande.
 */
public class PlanScreen extends Screen {
	private static final int WIDTH = 340;
	private static final int HEIGHT = 230;
	private static final int ROWS = 5;
	private static final int ROW = 22;
	private static final int PAPER = 0xFFEDE3C8;
	private static final int EDGE = 0xFF5A4A33;
	private static final int INK = 0xFF2B2B40;
	private static final int FADED = 0xFF7A7060;
	private static final int BAD = 0xFFB0201A;
	private static final int ACCENT = 0xFF8A5A1E;
	private static final int CHOSEN = 0x402E7D32;
	private static final String[] STYLES = {"plains", "taiga", "desert", "savanna"};

	private final PlanNetwork.PlanSnapshot plan;
	private int style;
	private int page;
	private int left;
	private int top;

	public PlanScreen(PlanNetwork.PlanSnapshot plan) {
		super(Component.translatable("item.minecities.plan"));
		this.plan = plan;
		this.style = Math.max(0, List.of(STYLES).indexOf(plan.style()));
		// La page du bâtiment choisi.
		for (int i = 0; i < plan.plans().size(); i++) {
			if (plan.plans().get(i).id().equals(plan.blueprint())) {
				page = i / ROWS;
			}
		}
	}

	public PlanNetwork.PlanSnapshot plan() {
		return plan;
	}

	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		addRenderableWidget(Button.builder(Component.translatable("screen.minecities.plans.style",
				Component.translatable("style.minecities." + STYLES[style])), b -> {
			style = (style + 1) % STYLES.length;
			rebuildWidgets();
		}).bounds(left + 10, top + 44, 130, 16).build());
		int y = top + 66;
		for (TownSnapshot.PlanRow row : visible()) {
			Button choose = addRenderableWidget(Button.builder(Component.translatable("screen.minecities.plan.choose"),
					b -> send("select:" + STYLES[style] + ":" + row.id(), true))
					.bounds(left + WIDTH - 74, y, 64, 16).build());
			choose.active = plan.manager() && row.locked().isEmpty();
			y += ROW;
		}
		int bottom = top + HEIGHT - 24;
		String[] actions = {"rotate", "confirm", "cancel"};
		for (int i = 0; i < actions.length; i++) {
			String action = actions[i];
			Button button = addRenderableWidget(Button.builder(Component.translatable("screen.minecities.plan." + action),
					b -> send(action, !action.equals("rotate"))).bounds(left + 10 + i * 76, bottom, 72, 18).build());
			button.active = plan.proposal() && plan.manager();
		}
		int pages = Math.max(1, (plan.plans().size() + ROWS - 1) / ROWS);
		if (pages > 1) {
			Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page--;
				rebuildWidgets();
			}).bounds(left + WIDTH - 54, bottom, 20, 18).build());
			previous.active = page > 0;
			Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page++;
				rebuildWidgets();
			}).bounds(left + WIDTH - 30, bottom, 20, 18).build());
			next.active = page < pages - 1;
		}
	}

	private List<TownSnapshot.PlanRow> visible() {
		List<TownSnapshot.PlanRow> all = plan.plans();
		return all.subList(Math.min(all.size(), page * ROWS), Math.min(all.size(), (page + 1) * ROWS));
	}

	/** Envoie la demande ; on referme pour poser le plan, sauf pour tourner (on regarde le fantôme). */
	private void send(String action, boolean close) {
		ClientPlayNetworking.send(new PlanNetwork.ActionPayload(action));
		if (close) {
			onClose();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(left - 3, top - 3, left + WIDTH + 3, top + HEIGHT + 3, EDGE);
		graphics.fill(left, top, left + WIDTH, top + HEIGHT, PAPER);
		graphics.text(font, Component.translatable("screen.minecities.plan.title", plan.town()), left + 10, top + 9,
				INK, false);
		TownSnapshot.PlanRow chosen = plan.plans().stream().filter(p -> p.id().equals(plan.blueprint())).findFirst()
				.orElse(null);
		Component current = chosen == null ? Component.translatable("screen.minecities.plan.blank")
				: Component.translatable("screen.minecities.plan.current",
						Component.translatableWithFallback(chosen.key(), chosen.fallback()),
						Component.translatable("style.minecities." + plan.style()));
		graphics.text(font, current, left + 10, top + 24, chosen == null ? FADED : ACCENT, false);
		graphics.fill(left + 8, top + 36, left + WIDTH - 8, top + 37, EDGE);
		int y = top + 68;
		for (TownSnapshot.PlanRow row : visible()) {
			if (row.id().equals(plan.blueprint())) {
				graphics.fill(left + 8, y - 4, left + WIDTH - 8, y + ROW - 4, CHOSEN);
			}
			graphics.text(font, Component.translatableWithFallback(row.key(), row.fallback()), left + 14, y,
					row.locked().isEmpty() ? INK : FADED, false);
			// Sous le nom : maison ou atelier, puis les dimensions.
			Component size = Component.translatable("screen.minecities.plans.size", row.width(), row.depth(), row.height());
			graphics.text(font, size, left + 140, y + 9, FADED, false);
			Component detail = row.locked().isEmpty()
					? Component.translatable("blueprint.minecities.kind." + row.kind())
					: Component.translatable("screen.minecities.plans.locked", Component.translatable(row.locked()));
			graphics.text(font, detail, left + 22, y + 9, row.locked().isEmpty() ? ACCENT : BAD, false);
			y += ROW;
		}
		Component hint = !plan.manager() ? Component.translatable("screen.minecities.managers_only")
				: plan.proposal() ? Component.translatable("screen.minecities.plan.hint.proposal")
				: Component.translatable("screen.minecities.plan.hint.place");
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(hint, WIDTH - 20);
		int hintY = top + HEIGHT - 30 - 10 * lines.size();
		for (net.minecraft.util.FormattedCharSequence line : lines) {
			graphics.text(font, line, left + 10, hintY, FADED, false);
			hintY += 10;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
