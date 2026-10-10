package com.ajustor.minecities.town;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reconnaît une maison construite par le joueur, sans plan imposé : depuis la plaque posée à
 * l'intérieur, on remplit l'espace libre de proche en proche. Si ce remplissage reste enfermé
 * (murs, toit, sol, portes fermant les ouvertures), c'est une maison ; les lits et les coffres qui
 * bordent cet espace sont les siens.
 *
 * <p>Indépendant de Minecraft : le monde est vu à travers {@link World}, ce qui permet de tester
 * l'algorithme sur des maisons dessinées à la main.
 */
public final class HouseScanner {
	/** Au-delà, l'espace n'est pas fermé (ou c'est un palais, pas une maison). */
	public static final int MAX_VOLUME = 2048;
	/** Une maison tient dans un cube de ce côté. */
	public static final int MAX_EXTENT = 32;
	/** Il faut au moins de quoi se tenir debout. */
	public static final int MIN_VOLUME = 4;

	private static final int[][] DIRECTIONS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	/** Ce qu'est un bloc pour la maison. */
	public enum Cell {
		/** Libre : de l'air, une torche, un tapis... on y passe. */
		OPEN,
		/** Un mur, un toit, un sol, un meuble plein. */
		SOLID,
		/** Une porte (ou une trappe, un portillon) : ferme une ouverture. */
		DOOR,
		/** La tête d'un lit : une place pour dormir. */
		BED,
		/** Un coffre, un tonneau : des réserves. */
		CONTAINER,
		/** Une plaque de maison : libre, mais on la note (une pièce n'a qu'une plaque). */
		PLAQUE
	}

	/** Le monde, vu par le scanner. */
	@FunctionalInterface
	public interface World {
		Cell classify(int x, int y, int z);
	}

	public record Pos(int x, int y, int z) {
		Pos offset(int[] d) {
			return new Pos(x + d[0], y + d[1], z + d[2]);
		}
	}

	public enum Status {
		VALID("valid"),
		/** L'espace fuit : une ouverture sans porte, un toit manquant, ou une maison trop grande. */
		NOT_ENCLOSED("not_enclosed"),
		TOO_SMALL("too_small"),
		NO_DOOR("no_door"),
		NO_BED("no_bed"),
		/** Une autre plaque déclare déjà cette pièce. */
		ALREADY_DECLARED("already_declared");

		private final String key;

		Status(String key) {
			this.key = key;
		}

		public String translationKey() {
			return "house.minecities.status." + key;
		}
	}

	/**
	 * Le résultat d'un relevé : son verdict, le volume intérieur, ce qui borde l'intérieur, et les
	 * autres plaques trouvées dedans. Les listes sont dans l'ordre de découverte.
	 */
	public record Result(Status status, int volume, List<Pos> beds, List<Pos> containers, int doors,
			List<Pos> plaques) {
		public boolean valid() {
			return status == Status.VALID;
		}
	}

	private HouseScanner() {
	}

	/** Relève la maison dont l'intérieur contient {@code start} (la plaque, toujours tenue pour libre). */
	public static Result scan(World world, Pos start) {
		Set<Pos> interior = new HashSet<>();
		Set<Pos> border = new HashSet<>();
		List<Pos> beds = new ArrayList<>();
		List<Pos> containers = new ArrayList<>();
		List<Pos> plaques = new ArrayList<>();
		int doors = 0;
		ArrayDeque<Pos> queue = new ArrayDeque<>();
		queue.add(start);
		interior.add(start);
		int minX = start.x, maxX = start.x, minY = start.y, maxY = start.y, minZ = start.z, maxZ = start.z;
		while (!queue.isEmpty()) {
			Pos p = queue.poll();
			for (int[] d : DIRECTIONS) {
				Pos n = p.offset(d);
				if (interior.contains(n) || border.contains(n)) {
					continue;
				}
				Cell cell = world.classify(n.x, n.y, n.z);
				if (cell == Cell.OPEN || cell == Cell.PLAQUE) {
					interior.add(n);
					if (cell == Cell.PLAQUE) {
						plaques.add(n);
					}
					if (interior.size() > MAX_VOLUME) {
						return leak(interior.size());
					}
					minX = Math.min(minX, n.x);
					maxX = Math.max(maxX, n.x);
					minY = Math.min(minY, n.y);
					maxY = Math.max(maxY, n.y);
					minZ = Math.min(minZ, n.z);
					maxZ = Math.max(maxZ, n.z);
					if (maxX - minX >= MAX_EXTENT || maxY - minY >= MAX_EXTENT || maxZ - minZ >= MAX_EXTENT) {
						return leak(interior.size());
					}
					queue.add(n);
				} else {
					border.add(n);
					switch (cell) {
						case BED -> beds.add(n);
						case CONTAINER -> containers.add(n);
						case DOOR -> doors++;
						default -> {
						}
					}
				}
			}
		}
		Status status;
		if (interior.size() < MIN_VOLUME) {
			status = Status.TOO_SMALL;
		} else if (doors == 0) {
			status = Status.NO_DOOR;
		} else if (beds.isEmpty()) {
			status = Status.NO_BED;
		} else {
			status = Status.VALID;
		}
		return new Result(status, interior.size(), List.copyOf(beds), List.copyOf(containers), doors,
				List.copyOf(plaques));
	}

	private static Result leak(int volume) {
		return new Result(Status.NOT_ENCLOSED, volume, List.of(), List.of(), 0, List.of());
	}
}
