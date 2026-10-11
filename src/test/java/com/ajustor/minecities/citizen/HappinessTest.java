package com.ajustor.minecities.citizen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HappinessTest {
	/** Un citoyen ordinaire : repu, logé, employé, sans repas en mémoire. */
	private static Happiness.Factors ordinary(int meals, int variety, boolean dish) {
		return new Happiness.Factors(18, meals, variety, dish, Happiness.expectedVariety(0), true, false, true, false,
				false, false, 0, false, false, false);
	}

	@Test
	void dietForgetsOldMealsAndTiresOfRepeats() {
		Diet diet = new Diet();
		for (int i = 0; i < 4; i++) {
			diet.eaten("minecraft:bread");
		}
		diet.eaten("minecraft:cooked_beef");
		diet.eaten("minecraft:baked_potato");
		assertEquals(List.of("minecraft:bread", "minecraft:bread", "minecraft:bread", "minecraft:cooked_beef",
				"minecraft:baked_potato"), diet.meals());
		assertEquals(3, diet.times("minecraft:bread"));
		assertEquals(3, diet.variety());
		// Le pain, encore : la moitié seulement ; un aliment nouveau nourrit tout à fait.
		assertEquals(2, diet.nutrition("minecraft:bread", 5));
		assertEquals(8, diet.nutrition("minecraft:cooked_porkchop", 8));
		assertEquals(6, Diet.nutrition(8, 2));
		assertEquals(1, Diet.nutrition(1, 3));
		assertFalse(diet.hadDish());
		diet.eaten("minecraft:mushroom_stew");
		assertTrue(diet.hadDish());
	}

	@Test
	void anOrdinaryCitizenIsFairlyHappy() {
		// 5 + repu + logé + employé.
		assertEquals(8, Happiness.of(ordinary(0, 0, false)).score());
	}

	@Test
	void varietyMakesHappyAndMonotonySad() {
		int monotonous = Happiness.of(ordinary(5, 1, false)).score();
		int varied = Happiness.of(ordinary(5, 4, true)).score();
		assertEquals(7, monotonous);
		assertEquals(10, varied);
		// En ville, deux aliments ne suffisent plus.
		Happiness.Factors town = new Happiness.Factors(18, 5, 2, false, Happiness.expectedVariety(1), true, false,
				true, false, false, false, 0, false, false, false);
		assertTrue(Happiness.of(town).factors().contains(new Happiness.Factor("monotonous", -1)));
	}

	@Test
	void misfortunesAddUp() {
		Happiness.Factors misery = new Happiness.Factors(0, 5, 1, false, 2, false, false, false, true, false, true, 3,
				true, false, false);
		Happiness.Mood mood = Happiness.of(misery);
		// 5 - 3 (affamé) - 1 (monotone) - 2 (sans logis) - 1 (outil) - 1 (blessé) - 2 (deuil) - 1 (danger).
		assertEquals(0, mood.score());
		assertEquals(7, mood.factors().size());
	}

	@Test
	void aFestivalCheersEveryoneUp() {
		Happiness.Factors sad = new Happiness.Factors(10, 0, 0, false, 2, false, false, false, false, false, false, 0,
				false, true, false);
		// 5 - 1 (faim) - 2 (sans logis) + 2 (fête).
		assertEquals(4, Happiness.of(sad).score());
		// Avec une bibliothèque, un point de plus.
		Happiness.Factors read = new Happiness.Factors(10, 0, 0, false, 2, false, false, false, false, false, false, 0,
				false, true, true);
		assertEquals(5, Happiness.of(read).score());
	}

	@Test
	void happyCitizensWorkFaster() {
		assertTrue(Happiness.workPace(10) < Happiness.workPace(7));
		assertTrue(Happiness.workPace(7) < Happiness.workPace(5));
		assertEquals(1.0f, Happiness.workPace(5));
		assertTrue(Happiness.workPace(2) > 1.0f);
		assertTrue(Happiness.workPace(0) > Happiness.workPace(3));
	}
}
