package com.ajustor.minecities.client.screen;

import com.ajustor.minecities.network.TownActionPayload;
import com.ajustor.minecities.network.TownSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * L'écran de l'hôtel de ville, l'interface d'administration de la cité : le résumé de la ville (et
 * son moral, ses fêtes, l'alerte), ses citoyens (métier, faim, humeur, famille, ce qui leur manque ;
 * un bouton les fait luire pour les retrouver), ses bâtiments, le registre où le maire accepte ou
 * refuse les demandes de mariage, la mairie, les joueurs de la ville (habitants, demandes pour s'y
 * installer, droits de chaque rang), l'élection du maire (pétition, candidatures, vote) et les plans des bâtiments qu'il peut commander (dans le style
 * choisi). Un bouton ouvre le grenier.
 */
public class TownHallScreen extends Screen {
	private static final int WIDTH = 420;
	private static final int HEIGHT = 230;
	private static final int ROWS = 7;
	/** Les plans : deux lignes de moins, sous le choix du style et au-dessus du grenier. */
	private static final int PLAN_ROWS = 5;
	/** Les demandes de mariage : espacées, avec leurs deux boutons ; cinq tiennent au-dessus des pages. */
	private static final int REQUEST_ROWS = 5;
	private static final int ROW = 22;
	/** Les joueurs de la ville : quatre lignes, au-dessus des droits de chaque rang. */
	private static final int MEMBER_ROWS = 4;
	/** Un décret : son nom, puis son effet dessous. */
	private static final int DECREE_ROW = 20;
	/** L'interligne du Résumé : serré, pour que tout tienne au-dessus des boutons du bas. */
	private static final int SUMMARY_LINE = 12;
	private static final int MEMBER_ROW = 16;
	/** Les droits, dans l'ordre de {@code Membership.Permission}. */
	private static final String[] PERMISSIONS = {"declare", "granary", "break"};
	private static final int PAPER = 0xFFEDE3C8;
	private static final int EDGE = 0xFF5A4A33;
	private static final int INK = 0xFF2B2B40;
	private static final int FADED = 0xFF7A7060;
	private static final int GOOD = 0xFF2E7D32;
	private static final int BAD = 0xFFB0201A;
	private static final int ACCENT = 0xFF8A5A1E;
	private static final int FESTIVE = 0xFF9C27B0;

	public enum Tab { SUMMARY, CITIZENS, BUILDINGS, REGISTRY, COUNCIL, RESIDENTS, ELECTION, PLANS }

	private static final String[] STYLES = {"plains", "taiga", "desert", "savanna"};

	private TownSnapshot town;
	private Tab tab = Tab.SUMMARY;
	private int page;
	/** Le style des plans qu'on prend (dans {@link #STYLES}). */
	private int style;
	private int left;
	private int top;

	public TownHallScreen(TownSnapshot town) {
		super(Component.literal(town.name()));
		this.town = town;
	}


	/** Tous les combien de ticks l'écran ouvert redemande l'état au serveur (MC-72). */
	private static final int REFRESH = 40;
	private int age;

	@Override
	public void tick() {
		super.tick();
		if (++age % REFRESH == 0) {
			send("refresh", TownActionPayload.NONE);
		}
	}

	/** Le serveur a envoyé un nouvel état (après une décision du maire, ou toutes les deux secondes). */
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
			Button button = addRenderableWidget(Button.builder(label, b -> select(t))
					.bounds(x, top + 26, 49, 18).build());
			button.active = tab != t;
			x += 50;
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.minecities.granary"),
				b -> send("granary", TownActionPayload.NONE)).bounds(left + 10, top + HEIGHT - 26, 62, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.minecities.map"),
				b -> minecraft.gui.setScreen(new TownMapScreen(town, this))).bounds(left + 76, top + HEIGHT - 26, 50, 18)
				.build());
		switch (tab) {
			case SUMMARY -> {
				if (town.manager()) {
					Component label = Component.translatable("screen.minecities.festival", town.life().festivalCost());
					int w = Math.max(160, font.width(label) + 16);
					Button festival = addRenderableWidget(Button.builder(label,
							b -> send("festival", TownActionPayload.NONE))
							.bounds(left + WIDTH - 10 - w, top + HEIGHT - 26, w, 18).build());
					festival.active = town.life().canCelebrate();
				}
			}
			case CITIZENS -> {
				List<TownSnapshot.CitizenRow> rows = visible(town.rows());
				int y = top + 54;
				for (TownSnapshot.CitizenRow row : rows) {
					addRenderableWidget(Button.builder(Component.translatable("screen.minecities.locate"),
							b -> send("locate", row.id())).bounds(left + WIDTH - 44, y, 34, 16).build());
					if (town.manager() && !row.job().isEmpty()) {
						// Le renvoyer à son poste.
						addRenderableWidget(Button.builder(Component.translatable("screen.minecities.posting"),
								b -> send("posting", row.id())).bounds(left + WIDTH - 86, y, 40, 16).build());
					}
					y += ROW;
				}
				pager(town.rows().size());
			}
			case BUILDINGS -> {
				// Chaque bâtiment déclaré a son interface : son niveau, son amélioration.
				int y = top + 54;
				for (TownSnapshot.BuildingRow row : visible(town.buildings())) {
					if (!row.monument()) {
						BlockPos plaque = row.plaque();
						addRenderableWidget(Button.builder(Component.translatable("screen.minecities.building.details"),
								b -> send("building:" + plaque.getX() + "," + plaque.getY() + "," + plaque.getZ(),
										TownActionPayload.NONE)).bounds(left + WIDTH - 66, y, 56, 16).build());
					}
					y += ROW;
				}
				pager(town.buildings().size());
			}
			case COUNCIL -> {
				if (town.manager()) {
					TownSnapshot.Council council = town.council();
					int y = top + 82;
					for (int i = 0; i < council.decrees().size(); i++) {
						String decree = council.decrees().get(i);
						boolean on = council.decreed().get(i);
						addRenderableWidget(Button.builder(Component.translatable(on ? "screen.minecities.decree.lift"
										: "screen.minecities.decree.take"),
								b -> send("decree." + decree, TownActionPayload.NONE))
								.bounds(left + WIDTH - 90, y, 80, 16).build());
						y += DECREE_ROW;
					}
					// Le taux de l'impôt, sous les décrets (MC-57).
					if (council.budget()) {
						addRenderableWidget(Button.builder(Component.translatable("screen.minecities.council.tax.change"),
								b -> send("tax.next", TownActionPayload.NONE)).bounds(left + WIDTH - 90, y, 80, 16).build());
					}
				}
			}
			case PLANS -> {
				addRenderableWidget(Button.builder(Component.translatable("screen.minecities.plans.style",
						Component.translatable("style.minecities." + STYLES[style])), b -> {
					style = (style + 1) % STYLES.length;
					rebuildWidgets();
				}).bounds(left + 14, top + 54, 130, 16).build());
				if (town.manager()) {
					int y = top + 76;
					for (TownSnapshot.PlanRow plan : visible(town.plans(), PLAN_ROWS)) {
						Button take = addRenderableWidget(Button.builder(Component.translatable("screen.minecities.plans.choose"),
								b -> send("plan:" + STYLES[style] + ":" + plan.id(), TownActionPayload.NONE))
								.bounds(left + WIDTH - 80, y, 70, 16).build());
						take.active = plan.locked().isEmpty();
						y += ROW;
					}
				}
				pager(town.plans().size(), PLAN_ROWS);
			}
			case RESIDENTS -> residentWidgets();
			case ELECTION -> electionWidgets();
			case REGISTRY -> {
				if (town.manager()) {
					int y = top + 56;
					for (TownSnapshot.RequestRow request : visible(town.requests(), REQUEST_ROWS)) {
						addRenderableWidget(Button.builder(Component.translatable("screen.minecities.accept"),
								b -> send("accept", request.id())).bounds(left + WIDTH - 150, y, 66, 18).build());
						addRenderableWidget(Button.builder(Component.translatable("screen.minecities.refuse"),
								b -> send("refuse", request.id())).bounds(left + WIDTH - 80, y, 66, 18).build());
						y += ROW + 4;
					}
				}
				pager(town.requests().size(), REQUEST_ROWS);
			}
		}
	}

	/** Les joueurs de la ville et les demandes, dans une seule liste. */
	private List<TownSnapshot.PlayerRow> members() {
		List<TownSnapshot.PlayerRow> all = new java.util.ArrayList<>(town.members().players());
		all.addAll(town.members().requests().stream()
				.map(r -> new TownSnapshot.PlayerRow(r.id(), r.name(), "request")).toList());
		return all;
	}

	/**
	 * L'onglet Habitants : s'installer ou partir ; pour le maire et ses adjoints, accepter, refuser,
	 * renvoyer ; pour le maire, les droits de l'habitant et du visiteur.
	 */
	private void residentWidgets() {
		TownSnapshot.Members members = town.members();
		if (members.rank().equals("visitor") && !members.requested()) {
			addRenderableWidget(Button.builder(Component.translatable("screen.minecities.residents.join"),
					b -> send("join", TownActionPayload.NONE)).bounds(left + WIDTH - 150, top + 54, 140, 16).build());
		} else if (members.rank().equals("resident") || members.rank().equals("deputy")) {
			addRenderableWidget(Button.builder(Component.translatable("screen.minecities.residents.leave"),
					b -> send("leave", TownActionPayload.NONE)).bounds(left + WIDTH - 150, top + 54, 140, 16).build());
		}
		if (town.manager()) {
			int y = top + 76;
			for (TownSnapshot.PlayerRow row : visible(members(), MEMBER_ROWS)) {
				if (row.rank().equals("request")) {
					addRenderableWidget(Button.builder(Component.translatable("screen.minecities.residents.accept"),
							b -> send("resident.accept", row.id())).bounds(left + WIDTH - 150, y - 3, 66, 14).build());
					addRenderableWidget(Button.builder(Component.translatable("screen.minecities.refuse"),
							b -> send("resident.refuse", row.id())).bounds(left + WIDTH - 80, y - 3, 66, 14).build());
				} else if (row.rank().equals("resident")) {
					addRenderableWidget(Button.builder(Component.translatable("screen.minecities.residents.expel"),
							b -> send("resident.remove", row.id())).bounds(left + WIDTH - 80, y - 3, 66, 14).build());
				}
				y += MEMBER_ROW;
			}
		}
		if (members.rank().equals("mayor")) {
			String[] ranks = {"resident", "visitor"};
			for (int r = 0; r < ranks.length; r++) {
				List<Boolean> allowed = r == 0 ? members.resident() : members.visitor();
				for (int p = 0; p < PERMISSIONS.length; p++) {
					String action = "permission:" + ranks[r] + ":" + PERMISSIONS[p];
					addRenderableWidget(Button.builder(Component.translatable(allowed.get(p)
									? "screen.minecities.residents.allowed" : "screen.minecities.residents.denied"),
							b -> send(action, TownActionPayload.NONE))
							.bounds(left + 130 + p * 90, top + HEIGHT - 66 + r * 18, 84, 16).build());
				}
			}
		}
		pager(members().size(), MEMBER_ROWS);
	}

	/** L'onglet Élection : ouvrir l'élection, signer la pétition, se porter candidat, voter. */
	private void electionWidgets() {
		TownSnapshot.Ballot ballot = town.ballot();
		String rank = town.members().rank();
		boolean member = !rank.equals("visitor");
		switch (ballot.phase()) {
			case "none" -> {
				if (rank.equals("mayor")) {
					Button call = addRenderableWidget(Button.builder(Component.translatable("screen.minecities.election.call"),
							b -> send("election.call", TownActionPayload.NONE))
							.bounds(left + WIDTH - 170, top + HEIGHT - 26, 160, 18).build());
					call.active = ballot.allowedIn() == 0;
				} else if (member) {
					Button sign = addRenderableWidget(Button.builder(Component.translatable("screen.minecities.election.sign",
									ballot.signatures(), ballot.needed()),
							b -> send("election.sign", TownActionPayload.NONE))
							.bounds(left + WIDTH - 170, top + HEIGHT - 26, 160, 18).build());
					sign.active = ballot.allowedIn() == 0 && !ballot.signed();
				}
			}
			case "candidacy" -> {
				if (member && ballot.candidates().stream().noneMatch(c -> c.id().equals(self()))) {
					addRenderableWidget(Button.builder(Component.translatable("screen.minecities.election.run"),
							b -> send("election.run", TownActionPayload.NONE))
							.bounds(left + WIDTH - 170, top + HEIGHT - 26, 160, 18).build());
				}
			}
			case "voting" -> {
				if (member && !ballot.voted()) {
					int y = top + 104;
					for (TownSnapshot.PlayerRow candidate : ballot.candidates().stream().limit(MEMBER_ROWS + 1).toList()) {
						addRenderableWidget(Button.builder(Component.translatable("screen.minecities.election.vote"),
								b -> send("vote", candidate.id())).bounds(left + WIDTH - 90, y - 3, 80, 14).build());
						y += MEMBER_ROW;
					}
				}
			}
			default -> {
			}
		}
	}

	/** Le joueur qui regarde l'écran. */
	private java.util.UUID self() {
		return minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : TownActionPayload.NONE;
	}

	private <T> List<T> visible(List<T> all) {
		return visible(all, ROWS);
	}

	private <T> List<T> visible(List<T> all, int rows) {
		return all.subList(Math.min(all.size(), page * rows), Math.min(all.size(), (page + 1) * rows));
	}

	/** Les boutons pour tourner les pages d'une liste. */
	private void pager(int count) {
		pager(count, ROWS);
	}

	private void pager(int count, int rows) {
		int pages = Math.max(1, (count + rows - 1) / rows);
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

	/** Où est la souris, pour les infobulles dessinées avec le contenu des onglets. */
	private int mouseX;
	private int mouseY;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
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
			case COUNCIL -> council(graphics);
			case RESIDENTS -> residents(graphics);
			case ELECTION -> election(graphics);
			case PLANS -> plans(graphics);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		// Les demandes en attente : une pastille sur l'onglet du registre.
		if (!town.requests().isEmpty()) {
			String count = String.valueOf(town.requests().size());
			int bx = left + 10 + Tab.REGISTRY.ordinal() * 50 + 49 - font.width(count) - 3;
			int by = top + 23;
			graphics.fill(bx - 2, by - 1, bx + font.width(count) + 2, by + 9, BAD);
			graphics.text(font, count, bx, by, 0xFFFFFFFF, false);
		}
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
		y += SUMMARY_LINE;
		line(graphics, Component.translatable("screen.minecities.homes", town.homes(), town.capacity()), y, INK);
		y += SUMMARY_LINE;
		// La croissance : d'où viendra le prochain habitant, ou ce qui l'empêche (MC-58).
		String growth = town.life().growth();
		line(graphics, Component.translatable("screen.minecities.growth." + growth, town.life().growthDays(),
				town.life().tavernLevel()), y, growth.equals("tavern") || growth.equals("newcomers") ? INK : BAD);
		y += SUMMARY_LINE;
		line(graphics, Component.translatable("screen.minecities.jobs", town.employed(), town.workstations()), y, INK);
		y += SUMMARY_LINE;
		line(graphics, Component.translatable("screen.minecities.meals", town.meals()), y,
				town.meals() > 0 ? INK : BAD);
		y += SUMMARY_LINE;
		TownSnapshot.Life life = town.life();
		line(graphics, Component.translatable("screen.minecities.treasury", life.treasury(), life.earnedToday()), y,
				ACCENT);
		y += SUMMARY_LINE;
		if (town.council().budget()) {
			int box = town.council().taxBox();
			line(graphics, box >= 0 ? Component.translatable("screen.minecities.tax_box", box)
					: Component.translatable("screen.minecities.tax_box.none"), y, box >= 0 ? INK : FADED);
			y += SUMMARY_LINE;
		}
		if (life.maxAnnexes() > 0) {
			line(graphics, Component.translatable("screen.minecities.annexes", life.annexes(), life.maxAnnexes()), y,
					INK);
			y += SUMMARY_LINE;
		}
		if (life.appeal() > 0) {
			line(graphics, Component.translatable("screen.minecities.appeal", life.appeal()), y, ACCENT);
			y += SUMMARY_LINE;
		}
		if (life.mood() >= 0) {
			double mood = life.mood() / 10.0;
			// Ce qui pèse le plus, à la suite (MC-71) : autant de raisons que la ligne en tient.
			MutableComponent text = Component.translatable("screen.minecities.mood", String.format("%.1f", mood));
			List<Component> worries = new java.util.ArrayList<>();
			for (String worry : life.worries()) {
				String[] kv = worry.split(":");
				worries.add(Component.translatable("mood.minecities." + kv[0]).append(" (" + kv[1] + ")"));
				if (font.width(text.copy().append(Component.translatable("screen.minecities.mood.worries",
						net.minecraft.network.chat.ComponentUtils.formatList(worries, Component.literal(", ")))))
						> WIDTH - 28) {
					worries.removeLast();
					break;
				}
			}
			if (!worries.isEmpty()) {
				text.append(Component.translatable("screen.minecities.mood.worries",
						net.minecraft.network.chat.ComponentUtils.formatList(worries, Component.literal(", "))));
			}
			line(graphics, text, y, moodColor(mood));
			y += SUMMARY_LINE;
		}
		if (life.alarmed()) {
			line(graphics, Component.translatable("screen.minecities.alarmed"), y, BAD);
			y += SUMMARY_LINE;
		}
		if (!life.disaster().isEmpty()) {
			line(graphics, Component.translatable("screen.minecities.disaster." + life.disaster()), y, BAD);
			y += SUMMARY_LINE;
		}
		if (life.partying()) {
			line(graphics, Component.translatable("screen.minecities.partying"), y, FESTIVE);
			y += SUMMARY_LINE;
		} else if (!life.canCelebrate()) {
			long minutes = Math.max(1, life.untilFestival() / 1200);
			line(graphics, Component.translatable("screen.minecities.next_festival", minutes), y, FADED);
			y += SUMMARY_LINE;
		}
		y += 6;
		if (!town.nextTier().isEmpty()) {
			line(graphics, Component.translatable("screen.minecities.next", Component.translatable(town.nextTier())),
					y, ACCENT);
			y += SUMMARY_LINE;
			line(graphics, Component.translatable("screen.minecities.next.needs", town.population(),
					town.nextPopulation(), town.homes(), town.nextHouses()), y, FADED);
			y += SUMMARY_LINE;
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
				Component mood = Component.translatable("screen.minecities.mood.short", row.mood());
				graphics.text(font, mood, left + 300, y, moodColor(row.mood()), false);
				// Au survol : ce qui fait son humeur (MC-71).
				if (!row.factors().isEmpty() && mouseX >= left + 300 && mouseX < left + 300 + font.width(mood)
						&& mouseY >= y && mouseY < y + 9) {
					List<Component> lines = new java.util.ArrayList<>();
					for (String factor : row.factors()) {
						String[] kv = factor.split(":");
						int delta = Integer.parseInt(kv[1]);
						lines.add(Component.literal((delta > 0 ? "+" : "") + delta + " ")
								.append(Component.translatable("mood.minecities." + kv[0]))
								.withColor(delta > 0 ? GOOD : delta < 0 ? BAD : FADED));
					}
					graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
				}
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
			String kind = building.monument() ? "screen.minecities.building.monument"
					: building.village() ? "screen.minecities.building.village"
					: building.beds() > 0 && !building.jobs().isEmpty() ? "screen.minecities.building.both"
					: building.beds() > 0 ? "screen.minecities.building.home" : "screen.minecities.building.workshop";
			graphics.text(font, Component.translatable(kind), left + 14, y, INK, false);
			graphics.text(font, Component.literal(building.plaque().toShortString()), left + 140, y, FADED, false);
			int x = left + 22;
			if (building.beds() > 0) {
				Component beds = Component.translatable("screen.minecities.building.beds", building.sleepers(),
						building.beds());
				graphics.text(font, beds, x, y + 10, building.sleepers() < building.beds() ? GOOD : FADED, false);
				x += font.width(beds) + 10;
			}
			if (!building.jobs().isEmpty()) {
				MutableComponent jobs = Component.empty();
				for (String job : building.jobs()) {
					if (!jobs.getSiblings().isEmpty()) {
						jobs.append(", ");
					}
					jobs.append(job.startsWith("!") ? Component.translatable("screen.minecities.job_locked",
							Component.translatable(job.substring(1))) : Component.translatable(job));
				}
				graphics.text(font, jobs, x, y + 10, ACCENT, false);
			}
			y += ROW;
		}
	}

	private void council(GuiGraphicsExtractor graphics) {
		TownSnapshot.Council council = town.council();
		int y = top + 58;
		line(graphics, Component.translatable("screen.minecities.council.mayor", council.mayor()), y, INK);
		y += 12;
		line(graphics, council.deputies().isEmpty() ? Component.translatable("screen.minecities.council.no_deputy")
				: Component.translatable("screen.minecities.council.deputies", String.join(", ", council.deputies())),
				y, FADED);
		// Les décrets (le ● dit ceux en vigueur), puis l'impôt : tout tient au-dessus des boutons du bas.
		y += 14;
		for (int i = 0; i < council.decrees().size(); i++) {
			boolean on = council.decreed().get(i);
			MutableComponent decree = Component.translatable("decree.minecities." + council.decrees().get(i));
			graphics.text(font, Component.literal(on ? "● " : "○ ").append(decree), left + 22, y, on ? GOOD : FADED,
					false);
			graphics.text(font, Component.translatable("decree.minecities." + council.decrees().get(i) + ".effect"),
					left + 34, y + 10, FADED, false);
			y += DECREE_ROW;
		}
		// Le budget : le taux de l'impôt, et ce que la journée a rapporté et coûté.
		if (council.budget()) {
			graphics.text(font, Component.literal("● ").append(Component.translatable("screen.minecities.council.tax",
					Component.translatable("tax.minecities." + council.tax()), council.perAdult())), left + 22, y, ACCENT,
					false);
			graphics.text(font, Component.translatable("screen.minecities.council.budget", council.taxes(),
					council.wages(), council.works()), left + 34, y + 10, FADED, false);
		}
		// Le commerce, en haut à droite (à côté du maire et de ses adjoints).
		int x = left + WIDTH / 2 + 10;
		int ty = top + 58;
		graphics.text(font, Component.translatable("screen.minecities.council.trade"), x, ty, ACCENT, false);
		ty += 14;
		if (council.partners().isEmpty()) {
			graphics.text(font, Component.translatable("screen.minecities.council.no_trade"), x + 8, ty, FADED, false);
		}
		for (int i = 0; i < council.partners().size() && ty < top + 80; i++) {
			graphics.text(font, Component.translatable(council.open().get(i) ? "screen.minecities.council.partner"
					: "screen.minecities.council.pending", council.partners().get(i)), x + 8, ty,
					council.open().get(i) ? GOOD : FADED, false);
			ty += 12;
		}
	}

	/** Les joueurs de la ville, leur rang, les demandes ; puis ce que chaque rang a le droit de faire. */
	private void residents(GuiGraphicsExtractor graphics) {
		TownSnapshot.Members members = town.members();
		Component rank = Component.translatable("rank.minecities." + members.rank());
		line(graphics, members.requested() ? Component.translatable("screen.minecities.residents.requested", rank)
				: Component.translatable("screen.minecities.residents.rank", rank), top + 58, ACCENT);
		int y = top + 76;
		for (TownSnapshot.PlayerRow row : visible(members(), MEMBER_ROWS)) {
			boolean request = row.rank().equals("request");
			graphics.text(font, Component.literal(row.name()), left + 14, y, request ? FADED : INK, false);
			graphics.text(font, request ? Component.translatable("screen.minecities.residents.request")
					: Component.translatable("rank.minecities." + row.rank()), left + 140, y, request ? ACCENT : FADED,
					false);
			y += MEMBER_ROW;
		}
		graphics.fill(left + 8, top + HEIGHT - 86, left + WIDTH - 8, top + HEIGHT - 85, EDGE);
		for (int p = 0; p < PERMISSIONS.length; p++) {
			Component column = Component.translatable("screen.minecities.residents.column." + PERMISSIONS[p]);
			graphics.text(font, column, left + 172 + p * 90 - font.width(column) / 2, top + HEIGHT - 79, ACCENT, false);
		}
		String[] ranks = {"resident", "visitor"};
		for (int r = 0; r < ranks.length; r++) {
			int rowY = top + HEIGHT - 62 + r * 18;
			graphics.text(font, Component.translatable("rank.minecities." + ranks[r]), left + 14, rowY, INK, false);
			if (!members.rank().equals("mayor")) {
				List<Boolean> allowed = r == 0 ? members.resident() : members.visitor();
				for (int p = 0; p < PERMISSIONS.length; p++) {
					Component state = Component.translatable(allowed.get(p) ? "screen.minecities.residents.allowed"
							: "screen.minecities.residents.denied");
					graphics.text(font, state, left + 172 + p * 90 - font.width(state) / 2, rowY,
							allowed.get(p) ? GOOD : BAD, false);
				}
			}
		}
	}

	/** Le mandat, le dernier élu, puis l'élection en cours : la pétition, les candidats, le vote. */
	private void election(GuiGraphicsExtractor graphics) {
		TownSnapshot.Ballot ballot = town.ballot();
		int y = top + 58;
		line(graphics, Component.translatable("screen.minecities.council.mayor", town.council().mayor()), y, INK);
		y += 14;
		if (ballot.termIn() >= 0) {
			line(graphics, Component.translatable("screen.minecities.election.term", minutes(ballot.termIn())), y, FADED);
			y += 14;
		}
		if (!ballot.lastWinner().isEmpty()) {
			line(graphics, Component.translatable("screen.minecities.election.last", ballot.lastWinner(),
					ballot.lastVotes()), y, FADED);
		}
		y = top + 90;
		switch (ballot.phase()) {
			case "none" -> {
				line(graphics, Component.translatable("screen.minecities.election.none"), y, ACCENT);
				y += 14;
				line(graphics, Component.translatable("screen.minecities.election.petition", ballot.signatures(),
						ballot.needed()), y, FADED);
				y += 14;
				if (ballot.allowedIn() > 0) {
					line(graphics, Component.translatable("screen.minecities.election.too_soon",
							minutes(ballot.allowedIn())), y, FADED);
				}
			}
			case "candidacy" -> {
				line(graphics, Component.translatable("screen.minecities.election.candidacy", minutes(ballot.endsIn())),
						y, ACCENT);
				candidates(graphics, ballot, y + 14);
			}
			case "voting" -> {
				line(graphics, Component.translatable("screen.minecities.election.voting", ballot.round(),
						minutes(ballot.endsIn())), y, ACCENT);
				candidates(graphics, ballot, y + 14);
				if (ballot.voted()) {
					line(graphics, Component.translatable("screen.minecities.election.voted"), top + HEIGHT - 50, GOOD);
				}
			}
			default -> {
			}
		}
	}

	private void candidates(GuiGraphicsExtractor graphics, TownSnapshot.Ballot ballot, int y) {
		for (TownSnapshot.PlayerRow candidate : ballot.candidates().stream().limit(MEMBER_ROWS + 1).toList()) {
			graphics.text(font, Component.literal(candidate.name()), left + 22, y, INK, false);
			graphics.text(font, Component.translatable("rank.minecities." + candidate.rank()), left + 160, y, FADED, false);
			y += MEMBER_ROW;
		}
	}

	/** Des ticks de jeu en minutes, au moins une. */
	private static long minutes(long ticks) {
		return Math.max(1, ticks / 1200);
	}

	/** Les plans : nom, dimensions, maison ou atelier, et le palier qu'attend un plan fermé. */
	private void plans(GuiGraphicsExtractor graphics) {
		List<TownSnapshot.PlanRow> plans = town.plans();
		if (plans.isEmpty()) {
			line(graphics, Component.translatable("screen.minecities.plans.none"), top + 80, FADED);
			return;
		}
		int y = top + 78;
		for (TownSnapshot.PlanRow plan : visible(plans, PLAN_ROWS)) {
			graphics.text(font, Component.translatableWithFallback(plan.key(), plan.fallback()), left + 14, y,
					plan.locked().isEmpty() ? INK : FADED, false);
			// Sous le nom : maison ou atelier (ou le palier attendu), puis les dimensions.
			Component detail = plan.locked().isEmpty()
					? Component.translatable("blueprint.minecities.kind." + plan.kind())
					: Component.translatable("screen.minecities.plans.locked", Component.translatable(plan.locked()));
			graphics.text(font, detail, left + 22, y + 9, plan.locked().isEmpty() ? ACCENT : BAD, false);
			graphics.text(font, Component.translatable("screen.minecities.plans.size", plan.width(), plan.depth(),
					plan.height()), left + 22 + Math.max(110, font.width(detail) + 10), y + 9, FADED, false);
			y += ROW;
		}
		if (!town.manager()) {
			graphics.text(font, Component.translatable("screen.minecities.managers_only"), left + 80, top + HEIGHT - 20,
					FADED, false);
		}
	}

	private void registry(GuiGraphicsExtractor graphics) {
		if (town.requests().isEmpty()) {
			line(graphics, Component.translatable("screen.minecities.no_requests"), top + 58, FADED);
			return;
		}
		int y = top + 61;
		for (TownSnapshot.RequestRow request : visible(town.requests(), REQUEST_ROWS)) {
			line(graphics, Component.translatable("screen.minecities.request", request.first(), request.second()), y,
					INK);
			y += ROW + 4;
		}
		if (!town.manager()) {
			graphics.text(font, Component.translatable("screen.minecities.mayor_decides"), left + 80, top + HEIGHT - 20,
					FADED, false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
