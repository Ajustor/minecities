package com.ajustor.minecities.citizen;

import java.util.ArrayList;
import java.util.List;

/**
 * Le bonheur d'un citoyen (brief 04) : une note de 0 à 10, à partir de 5, que chaque facteur monte
 * ou descend. Heureux, il travaille plus vite et peut fonder une famille ; malheureux, il traîne,
 * et au bout d'un jour à 1 ou moins il quitte la ville.
 */
public final class Happiness {
	public static final int MIN = 0;
	public static final int MAX = 10;
	/** La note de départ, avant les facteurs. */
	public static final int BASE = 5;
	/** À partir de là, il est heureux : il travaille plus vite. */
	public static final int HAPPY = 7;
	/** Au moins ça pour avoir un enfant. */
	public static final int CONTENT = 5;
	/** Jusque-là, il est malheureux : il traîne au travail. */
	public static final int UNHAPPY = 3;
	/** À ce point-là pendant un jour, il s'en va. */
	public static final int MISERABLE = 1;

	private Happiness() {
	}

	/**
	 * Ce qui fait son bonheur.
	 *
	 * @param hunger           sa faim (0 à 20)
	 * @param meals            le nombre de repas dont il se souvient
	 * @param variety          le nombre d'aliments différents parmi eux
	 * @param dish             un plat élaboré parmi eux
	 * @param expectedVariety  la variété qu'attendent les citoyens au palier de la ville
	 * @param housed           il a un lit
	 * @param child            un enfant (il ne travaille pas)
	 * @param employed         il a un métier
	 * @param missingTool      il lui manque l'outil de son métier
	 * @param married          il est marié
	 * @param hurt             il est blessé (moins de la moitié de sa vie)
	 * @param recentDeaths     les morts de la ville depuis un jour
	 * @param danger           un raid, l'alerte, ou un monstre tout près
	 * @param festival         une fête dans la ville depuis moins d'un jour
	 * @param culture          la ville a une bibliothèque, avec son bibliothécaire
	 * @param decrees          ce que les décrets du maire font au moral
	 * @param monuments        les monuments bâtis dans la ville
	 */
	public record Factors(float hunger, int meals, int variety, boolean dish, int expectedVariety, boolean housed,
			boolean child, boolean employed, boolean missingTool, boolean married, boolean hurt, int recentDeaths,
			boolean danger, boolean festival, boolean culture, int decrees, int monuments) {
		/** Sans monument. */
		public Factors(float hunger, int meals, int variety, boolean dish, int expectedVariety, boolean housed,
				boolean child, boolean employed, boolean missingTool, boolean married, boolean hurt, int recentDeaths,
				boolean danger, boolean festival, boolean culture, int decrees) {
			this(hunger, meals, variety, dish, expectedVariety, housed, child, employed, missingTool, married, hurt,
					recentDeaths, danger, festival, culture, decrees, 0);
		}
	}

	/** Un facteur et ce qu'il pèse (la clé se traduit en « mood.minecities.<clé> »). */
	public record Factor(String key, int delta) {
		public String translationKey() {
			return "mood.minecities." + key;
		}
	}

	/** La note, et ce qui l'explique. */
	public record Mood(int score, List<Factor> factors) {
	}

	public static Mood of(Factors f) {
		List<Factor> factors = new ArrayList<>();
		// Le ventre.
		if (f.hunger() <= 0) {
			factors.add(new Factor("starving", -3));
		} else if (f.hunger() < Hunger.HUNGRY) {
			factors.add(new Factor("hungry", -1));
		} else if (f.hunger() >= Hunger.WELL_FED) {
			factors.add(new Factor("well_fed", 1));
		}
		// La variété : on ne juge qu'après quelques repas.
		if (f.meals() >= 3) {
			if (f.variety() > f.expectedVariety()) {
				factors.add(new Factor("varied", 2));
			} else if (f.variety() == f.expectedVariety()) {
				factors.add(new Factor("enough_variety", 1));
			} else {
				factors.add(new Factor("monotonous", -1));
			}
		}
		if (f.dish()) {
			factors.add(new Factor("dish", 1));
		}
		// Le toit.
		factors.add(f.housed() ? new Factor("housed", 1) : new Factor("homeless", -2));
		// Le travail.
		if (!f.child()) {
			if (f.missingTool()) {
				factors.add(new Factor("missing_tool", -1));
			} else if (f.employed()) {
				factors.add(new Factor("employed", 1));
			}
		}
		if (f.married()) {
			factors.add(new Factor("married", 1));
		}
		// La santé et la sécurité.
		if (f.hurt()) {
			factors.add(new Factor("hurt", -1));
		}
		if (f.recentDeaths() > 0) {
			factors.add(new Factor("mourning", -Math.min(2, f.recentDeaths())));
		}
		if (f.danger()) {
			factors.add(new Factor("danger", -1));
		}
		if (f.festival()) {
			factors.add(new Factor("festival", 2));
		}
		if (f.culture()) {
			factors.add(new Factor("culture", 1));
		}
		if (f.decrees() != 0) {
			factors.add(new Factor(f.decrees() > 0 ? "decrees_good" : "decrees_bad", f.decrees()));
		}
		if (f.monuments() > 0) {
			factors.add(new Factor("monuments", Math.min(com.ajustor.minecities.town.Monument.MOOD_CAP, f.monuments())));
		}
		int score = BASE + factors.stream().mapToInt(Factor::delta).sum();
		return new Mood(Math.clamp(score, MIN, MAX), List.copyOf(factors));
	}

	/**
	 * Ce que son humeur fait à son rythme de travail : les pauses durent d'autant (moins quand il est
	 * heureux, plus quand il ne l'est pas).
	 */
	public static float workPace(int score) {
		if (score >= 9) {
			return 0.65f;
		}
		if (score >= HAPPY) {
			return 0.8f;
		}
		if (score <= MISERABLE) {
			return 1.75f;
		}
		if (score <= UNHAPPY) {
			return 1.35f;
		}
		return 1.0f;
	}

	/** La variété qu'on attend au palier de rang {@code tier} (0 : village) : 2 au village, puis plus. */
	public static int expectedVariety(int tier) {
		return 2 + tier;
	}
}
