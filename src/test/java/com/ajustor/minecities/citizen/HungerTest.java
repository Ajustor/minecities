package com.ajustor.minecities.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HungerTest {
	@Test
	void aFullBarLastsADay() {
		assertEquals(Hunger.MAX / 2, Hunger.decay(Hunger.MAX, 12000), 1e-3);
		assertEquals(0, Hunger.decay(Hunger.MAX, 24000), 1e-3);
		assertEquals(0, Hunger.decay(1, 24000));
	}

	@Test
	void eatingFillsUpToTheMax() {
		assertEquals(14, Hunger.eat(8, 6));
		assertEquals(Hunger.MAX, Hunger.eat(18, 8));
	}

	@Test
	void statesFollowTheBar() {
		assertEquals(Hunger.State.STARVING, Hunger.state(0));
		assertEquals(Hunger.State.HUNGRY, Hunger.state(5));
		assertEquals(Hunger.State.FED, Hunger.state(Hunger.HUNGRY));
		assertEquals(Hunger.State.WELL_FED, Hunger.state(Hunger.MAX));
	}
}
