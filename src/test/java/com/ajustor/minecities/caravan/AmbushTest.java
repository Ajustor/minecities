package com.ajustor.minecities.caravan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbushTest {
	@Test
	void twoGuardsHoldOffTwoBandits() {
		assertTrue(Caravans.repels(2, 2, 3));
	}

	@Test
	void aLoneMerchantFallsToTwoBandits() {
		assertFalse(Caravans.repels(0, 2, 0));
	}

	@Test
	void fourBanditsNeedLuckAgainstTwoGuards() {
		assertFalse(Caravans.repels(2, 4, 0));
		assertTrue(Caravans.repels(2, 3, 1));
	}
}
