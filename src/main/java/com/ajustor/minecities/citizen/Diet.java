package com.ajustor.minecities.citizen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Le régime d'un citoyen (brief 04, comme MineColonies) : il retient ses {@link #MEMORY} derniers
 * repas. Manger toujours la même chose nourrit moins et lasse ; un régime varié rend heureux. Les
 * aliments sont tenus par leur identifiant (« minecraft:bread »).
 */
public final class Diet {
	/** Le nombre de repas dont il se souvient. */
	public static final int MEMORY = 5;
	/** Les plats élaborés : ils valent un repas de fête. */
	public static final Set<String> DISHES = Set.of("minecraft:mushroom_stew", "minecraft:rabbit_stew",
			"minecraft:beetroot_soup", "minecraft:pumpkin_pie", "minecraft:cookie", "minecraft:golden_carrot",
			"minecraft:cake", "minecraft:honey_bottle");

	private final Deque<String> meals = new ArrayDeque<>();

	public Diet() {
	}

	public Diet(List<String> meals) {
		meals.forEach(this::eaten);
	}

	/** Il vient de manger ça. */
	public void eaten(String food) {
		meals.addLast(food);
		while (meals.size() > MEMORY) {
			meals.removeFirst();
		}
	}

	/** Ses derniers repas, du plus ancien au plus récent. */
	public List<String> meals() {
		return List.copyOf(meals);
	}

	/** Combien de fois il a mangé ça parmi ses derniers repas. */
	public int times(String food) {
		return (int) meals.stream().filter(food::equals).count();
	}

	/** Le nombre d'aliments différents parmi ses derniers repas. */
	public int variety() {
		return new HashSet<>(meals).size();
	}

	/** A-t-il mangé un plat élaboré dernièrement ? */
	public boolean hadDish() {
		return meals.stream().anyMatch(DISHES::contains);
	}

	/**
	 * Ce que rapporte un aliment qui nourrit de {@code base} quand il en a déjà mangé {@code times}
	 * fois dernièrement : tout la première fois, les trois quarts la troisième, la moitié au-delà.
	 */
	public static int nutrition(int base, int times) {
		if (times >= 3) {
			return Math.max(1, base / 2);
		}
		if (times == 2) {
			return Math.max(1, base * 3 / 4);
		}
		return base;
	}

	/** Ce que rapporterait cet aliment, maintenant. */
	public int nutrition(String food, int base) {
		return nutrition(base, times(food));
	}
}
