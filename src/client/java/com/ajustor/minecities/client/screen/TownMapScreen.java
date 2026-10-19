package com.ajustor.minecities.client.screen;

import com.ajustor.minecities.network.TownActionPayload;
import com.ajustor.minecities.network.TownSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La carte du territoire (MC-13), ouverte depuis l'hôtel de ville : un plan à plat, comme une carte
 * Minecraft, du territoire (le disque de l'hôtel de ville et ceux des mairies annexes) et des
 * bâtiments de la ville, chacun de la couleur de son genre. La molette zoome (autour du curseur), on
 * fait glisser pour se déplacer, et un clic sur un bâtiment le fait luire en jeu.
 */
public class TownMapScreen extends Screen {
	private static final int PAPER = 0xFFE3D3A8;
	private static final int EDGE = 0xFF5A4A33;
	private static final int INK = 0xFF2B2B40;
	private static final int FADED = 0xFF7A7060;
	private static final int TERRITORY = 0xFFB9CF8E;
	private static final int ANNEX = 0xFFA9C98A;
	private static final int BORDER = 0xFF6E8B3D;
	private static final int HALL = 0xFFB0201A;
	/** La couleur de chaque genre de bâtiment, dans l'ordre de la légende. */
	private static final Map<String, Integer> KINDS = new java.util.LinkedHashMap<>();

	static {
		KINDS.put("home", 0xFF8A5A32);
		KINDS.put("workshop", 0xFF4F6D8F);
		KINDS.put("both", 0xFF7A5C8E);
		KINDS.put("village", 0xFFB58B4C);
		KINDS.put("warehouse", 0xFF5E5E5E);
		KINDS.put("monument", 0xFFD4AF37);
		KINDS.put("site", 0xFFE08A3C);
	}

	private static final double MIN_SCALE = 0.25;
	private static final double MAX_SCALE = 8;

	private final Screen parent;
	private TownSnapshot town;
	/** Les pixels par bloc. */
	private double scale;
	/** Le point du monde au centre de la carte. */
	private double centerX;
	private double centerZ;
	private int left;
	private int top;
	private int right;
	private int bottom;

	public TownMapScreen(TownSnapshot town, Screen parent) {
		super(Component.translatable("screen.minecities.map.title", town.name()));
		this.town = town;
		this.parent = parent;
		this.centerX = town.hall().getX() + 0.5;
		this.centerZ = town.hall().getZ() + 0.5;
	}

	public boolean shows(TownSnapshot snapshot) {
		return town.hall().equals(snapshot.hall());
	}

	/** Le serveur a envoyé un nouvel état : la carte suit le palier, les annexes, les bâtiments. */
	/** Tous les combien de ticks l'écran ouvert redemande l'état au serveur (MC-72). */
	private static final int REFRESH = 40;
	private int age;

	@Override
	public void tick() {
		super.tick();
		if (++age % REFRESH == 0) {
			ClientPlayNetworking.send(new com.ajustor.minecities.network.TownActionPayload(town.hall(), "refresh",
					com.ajustor.minecities.network.TownActionPayload.NONE));
		}
	}

	public void refresh(TownSnapshot snapshot) {
		town = snapshot;
	}

	public TownSnapshot town() {
		return town;
	}

	@Override
	protected void init() {
		left = 12;
		top = 28;
		right = width - 12;
		bottom = height - 34;
		if (scale == 0) {
			// Tout le territoire tient dans la carte.
			scale = clampScale(Math.min(right - left, bottom - top) / (2.0 * extent() + 16));
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.minecities.map.back"),
				b -> onClose()).bounds(left, height - 26, 80, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.minecities.map.center"), b -> {
			centerX = town.hall().getX() + 0.5;
			centerZ = town.hall().getZ() + 0.5;
		}).bounds(left + 86, height - 26, 80, 18).build());
	}

	/** Jusqu'où s'étend le territoire, depuis l'hôtel de ville. */
	private int extent() {
		int extent = town.map().radius();
		for (BlockPos annex : town.map().annexes()) {
			extent = (int) Math.max(extent, Math.hypot(annex.getX() - town.hall().getX(), annex.getZ()
					- town.hall().getZ()) + town.map().annexRadius());
		}
		return extent;
	}

	private static double clampScale(double scale) {
		return Math.clamp(scale, MIN_SCALE, MAX_SCALE);
	}

	private int screenX(double worldX) {
		return (int) Math.floor((left + right) / 2.0 + (worldX - centerX) * scale);
	}

	private int screenY(double worldZ) {
		return (int) Math.floor((top + bottom) / 2.0 + (worldZ - centerZ) * scale);
	}

	/** Où ce point du monde tombe à l'écran. */
	public int[] toScreen(double worldX, double worldZ) {
		return new int[]{screenX(worldX), screenY(worldZ)};
	}

	private double worldX(double x) {
		return centerX + (x - (left + right) / 2.0) / scale;
	}

	private double worldZ(double y) {
		return centerZ + (y - (top + bottom) / 2.0) / scale;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(0, 0, width, height, 0xC0101010);
		graphics.fill(left - 3, top - 3, right + 3, bottom + 3, EDGE);
		graphics.fill(left, top, right, bottom, PAPER);
		graphics.enableScissor(left, top, right, bottom);
		TownSnapshot.TownMap map = town.map();
		for (BlockPos annex : map.annexes()) {
			disc(graphics, annex.getX() + 0.5, annex.getZ() + 0.5, map.annexRadius(), ANNEX);
		}
		disc(graphics, town.hall().getX() + 0.5, town.hall().getZ() + 0.5, map.radius(), TERRITORY);
		ring(graphics, town.hall().getX() + 0.5, town.hall().getZ() + 0.5, map.radius());
		for (BlockPos annex : map.annexes()) {
			ring(graphics, annex.getX() + 0.5, annex.getZ() + 0.5, map.annexRadius());
			marker(graphics, annex, ANNEX, BORDER);
		}
		Optional<TownSnapshot.Plot> hovered = plotAt(mouseX, mouseY);
		for (TownSnapshot.Plot plot : map.plots()) {
			int x0 = screenX(plot.minX());
			int y0 = screenY(plot.minZ());
			int x1 = Math.max(x0 + 2, screenX(plot.maxX() + 1));
			int y1 = Math.max(y0 + 2, screenY(plot.maxZ() + 1));
			graphics.fill(x0, y0, x1, y1, KINDS.getOrDefault(plot.kind(), FADED));
			graphics.outline(x0, y0, x1 - x0, y1 - y0, hovered.filter(plot::equals).isPresent() ? 0xFFFFFFFF : EDGE);
		}
		marker(graphics, town.hall(), HALL, 0xFF000000);
		graphics.disableScissor();
		// Le titre, la boussole et l'échelle.
		graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
		graphics.text(font, Component.translatable("screen.minecities.map.north"), right - 14, top + 4, INK, false);
		int bar = scaleBar();
		graphics.fill(right - 10 - (int) (bar * scale), bottom - 8, right - 10, bottom - 6, INK);
		graphics.text(font, Component.translatable("screen.minecities.map.scale", bar), right - 10 - (int) (bar * scale),
				bottom - 18, INK, false);
		legend(graphics);
		// L'infobulle du jeu : elle se place d'elle-même dans l'écran.
		hovered.ifPresent(plot -> graphics.setTooltipForNextFrame(font, Component.translatable("screen.minecities.map.hover",
				Component.translatable("screen.minecities.map.kind." + plot.kind()), plot.at().toShortString()),
				mouseX, mouseY));
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** Une longueur ronde (en blocs) qui fait entre 40 et 100 pixels à cette échelle. */
	private int scaleBar() {
		for (int blocks : new int[]{1, 2, 5, 10, 20, 50, 100, 200, 500}) {
			if (blocks * scale >= 40) {
				return blocks;
			}
		}
		return 500;
	}

	/** La légende, en bas : chaque genre de bâtiment présent sur la carte, et sa couleur. */
	private void legend(GuiGraphicsExtractor graphics) {
		int x = left + 176;
		int y = height - 21;
		java.util.Set<String> shown = new java.util.HashSet<>();
		town.map().plots().forEach(p -> shown.add(p.kind()));
		for (Map.Entry<String, Integer> kind : KINDS.entrySet()) {
			if (!shown.contains(kind.getKey())) {
				continue;
			}
			Component label = Component.translatable("screen.minecities.map.kind." + kind.getKey());
			if (x + 10 + font.width(label) > right) {
				break;
			}
			graphics.fill(x, y, x + 8, y + 8, kind.getValue());
			graphics.outline(x, y, 8, 8, EDGE);
			graphics.text(font, label, x + 10, y, 0xFFFFFFFF, false);
			x += 18 + font.width(label);
		}
	}

	/** Un disque plein, ligne par ligne. */
	private void disc(GuiGraphicsExtractor graphics, double cx, double cz, int radius, int color) {
		int y0 = Math.max(top, screenY(cz - radius));
		int y1 = Math.min(bottom, screenY(cz + radius) + 1);
		for (int y = y0; y < y1; y++) {
			double dz = worldZ(y + 0.5) - cz;
			double half = Math.sqrt(Math.max(0, (double) radius * radius - dz * dz));
			int x0 = Math.max(left, screenX(cx - half));
			int x1 = Math.min(right, screenX(cx + half) + 1);
			if (x1 > x0) {
				graphics.fill(x0, y, x1, y + 1, color);
			}
		}
	}

	/** Le bord d'un disque, en pointillés serrés. */
	private void ring(GuiGraphicsExtractor graphics, double cx, double cz, int radius) {
		int steps = Math.max(48, (int) (radius * scale * 2));
		for (int i = 0; i < steps; i++) {
			double angle = 2 * Math.PI * i / steps;
			int x = screenX(cx + Math.cos(angle) * radius);
			int y = screenY(cz + Math.sin(angle) * radius);
			graphics.fill(x, y, x + 1, y + 1, BORDER);
		}
	}

	/** Un repère carré, de quatre pixels au moins, centré sur ce bloc. */
	private void marker(GuiGraphicsExtractor graphics, BlockPos pos, int color, int edge) {
		int size = Math.max(5, (int) Math.ceil(3 * scale));
		int x = screenX(pos.getX() + 0.5) - size / 2;
		int y = screenY(pos.getZ() + 0.5) - size / 2;
		graphics.fill(x, y, x + size, y + size, color);
		graphics.outline(x, y, size, size, edge);
	}

	/** Le bâtiment sous le curseur, s'il y en a un (au moins trois pixels autour, pour les petits). */
	public Optional<TownSnapshot.Plot> plotAt(double mouseX, double mouseY) {
		if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= bottom) {
			return Optional.empty();
		}
		double slack = 3 / scale;
		double x = worldX(mouseX);
		double z = worldZ(mouseY);
		List<TownSnapshot.Plot> plots = town.map().plots();
		for (int i = plots.size() - 1; i >= 0; i--) {
			TownSnapshot.Plot p = plots.get(i);
			if (x >= p.minX() - slack && x <= p.maxX() + 1 + slack && z >= p.minZ() - slack && z <= p.maxZ() + 1 + slack) {
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}

	/** Clic sur un bâtiment : il luit en jeu. */
	public void select(TownSnapshot.Plot plot) {
		BlockPos at = plot.at();
		ClientPlayNetworking.send(new TownActionPayload(town.hall(), "glow:" + at.getX() + "," + at.getY() + ","
				+ at.getZ(), TownActionPayload.NONE));
	}

	/** Zoome d'un cran autour de ce point de l'écran (vers l'avant : plus près). */
	public void zoom(double mouseX, double mouseY, double steps) {
		double x = worldX(mouseX);
		double z = worldZ(mouseY);
		scale = clampScale(scale * Math.pow(1.25, steps));
		// Le point sous le curseur y reste.
		centerX = x - (mouseX - (left + right) / 2.0) / scale;
		centerZ = z - (mouseY - (top + bottom) / 2.0) / scale;
	}

	public double scale() {
		return scale;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		Optional<TownSnapshot.Plot> plot = plotAt(event.x(), event.y());
		plot.ifPresent(this::select);
		return plot.isPresent();
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.x() >= left && event.x() < right && event.y() >= top && event.y() < bottom) {
			centerX -= dx / scale;
			centerZ -= dy / scale;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			zoom(mouseX, mouseY, scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Retour à l'hôtel de ville, à jour de ce que la carte a reçu. */
	@Override
	public void onClose() {
		if (parent instanceof TownHallScreen hall) {
			hall.refresh(town);
		}
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
