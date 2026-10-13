package com.ajustor.minecities.citizen;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthTest {
	/** Un hasard qui renvoie toujours la même chose. */
	private static RandomGenerator always(double value) {
		return new RandomGenerator() {
			@Override
			public long nextLong() {
				return 0;
			}

			@Override
			public double nextDouble() {
				return value;
			}
		};
	}

	@Test
	void aColdIncubatesThenShowsThenPasses() {
		Health health = new Health();
		assertTrue(health.contract(Disease.COLD, 1000));
		assertEquals(Optional.empty(), health.symptoms(1000), "il couve d'abord");
		assertFalse(health.needsCare(1000));
		long sick = 1000 + Disease.COLD.incubation();
		assertEquals(Optional.of(Disease.COLD), health.symptoms(sick));
		assertTrue(health.needsCare(sick));
		assertEquals(Optional.empty(), health.tick(sick + Disease.COLD.duration() - 1));
		assertEquals(Optional.of(Disease.COLD), health.tick(sick + Disease.COLD.duration()));
		assertEquals(Optional.empty(), health.disease());
	}

	@Test
	void theDoctorShortensIt() {
		Health health = new Health();
		health.contract(Disease.FEVER, 0);
		long sick = Disease.FEVER.incubation() + 100;
		assertTrue(health.treat(sick));
		assertFalse(health.needsCare(sick), "soigné, il n'attend plus le médecin");
		assertFalse(health.treat(sick), "on ne le soigne pas deux fois");
		assertEquals(sick + Disease.FEVER.convalescence(), health.until());
		assertEquals(Optional.of(Disease.FEVER), health.tick(sick + Disease.FEVER.convalescence()));
	}

	@Test
	void noTreatmentBeforeTheSymptoms() {
		Health health = new Health();
		health.contract(Disease.DYSENTERY, 0);
		assertFalse(health.treat(10));
	}

	@Test
	void oneDiseaseAtATimeAndImmunityAfterwards() {
		Health health = new Health();
		health.contract(Disease.COLD, 0);
		assertFalse(health.contract(Disease.FEVER, 1), "une maladie à la fois");
		long cured = Disease.COLD.incubation() + Disease.COLD.duration();
		health.tick(cured);
		assertFalse(health.contract(Disease.COLD, cured + 1), "protégé du rhume");
		assertTrue(health.contract(Disease.FEVER, cured + 1), "pas de la fièvre");
		Health later = new Health();
		later.contract(Disease.COLD, 0);
		later.tick(cured);
		assertTrue(later.contract(Disease.COLD, cured + Health.IMMUNITY), "plus protégé après deux jours");
	}

	@Test
	void notImmuneToWhatHeNeverHad() {
		// Au tout début d'un monde, une date peut être négative : jamais malade, jamais protégé.
		assertFalse(new Health().immune(Disease.FEVER, -5000));
		assertTrue(new Health().contract(Disease.FEVER, -5000));
	}

	@Test
	void theRainWithoutACoat() {
		assertEquals(Optional.of(Disease.COLD), new Health().exposed(true, false, 0, always(0.0)));
		assertEquals(Optional.of(Disease.FEVER), new Health().exposed(true, false, 0, always(Health.RAIN_COLD)));
		assertEquals(Optional.empty(), new Health().exposed(true, false, 0, always(0.5)));
		assertEquals(Optional.empty(), new Health().exposed(true, true, 0, always(0.0)), "le manteau protège");
		assertEquals(Optional.empty(), new Health().exposed(false, false, 0, always(0.0)), "pas de pluie");
	}

	@Test
	void taintedFood() {
		assertEquals(Optional.of(Disease.DYSENTERY), new Health().ate(true, 0, always(0.1)));
		assertEquals(Optional.empty(), new Health().ate(true, 0, always(0.9)));
		assertEquals(Optional.empty(), new Health().ate(false, 0, always(0.0)), "un bon repas ne rend pas malade");
	}

	@Test
	void contagion() {
		assertEquals(Optional.of(Disease.FEVER), new Health().caught(Disease.FEVER, 0, always(0.0)));
		assertEquals(Optional.empty(), new Health().caught(Disease.FEVER, 0, always(0.5)));
	}

	@Test
	void aWellFedDryTownStaysHealthy() {
		// Au sec et sans repas gâté, personne ne tombe malade, quel que soit le hasard.
		Random random = new Random(3);
		Health health = new Health();
		for (int i = 0; i < 10_000; i++) {
			health.exposed(false, false, i, random);
			health.ate(false, i, random);
		}
		assertEquals(Optional.empty(), health.disease());
	}

	@Test
	void savedAndRead() {
		Health health = new Health();
		health.contract(Disease.FEVER, 100);
		health.treat(100 + Disease.FEVER.incubation());
		Health cold = new Health();
		cold.contract(Disease.COLD, 0);
		cold.tick(Disease.COLD.incubation() + Disease.COLD.duration());
		for (Health h : new Health[]{health, cold}) {
			Health read = Health.CODEC.parse(JsonOps.INSTANCE, Health.CODEC.encodeStart(JsonOps.INSTANCE, h)
					.getOrThrow()).getOrThrow();
			assertEquals(h.disease(), read.disease());
			assertEquals(h.until(), read.until());
			assertEquals(h.treated(), read.treated());
			assertEquals(h.immune(Disease.COLD, 30000), read.immune(Disease.COLD, 30000));
		}
	}
}
