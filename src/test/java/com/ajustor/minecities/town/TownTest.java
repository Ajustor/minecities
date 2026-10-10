package com.ajustor.minecities.town;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownTest {
	private static final BlockPos HALL = new BlockPos(0, 64, 0);
	private static final BlockPos PLAQUE = new BlockPos(10, 64, 10);
	private static final BlockPos BED_A = new BlockPos(11, 64, 11);
	private static final BlockPos BED_B = new BlockPos(12, 64, 11);

	@BeforeAll
	static void bootstrap() {
		// Level et ses codecs veulent les registres de Minecraft.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static Town town() {
		return Town.found("Valombre", Level.OVERWORLD, HALL, UUID.randomUUID(), "Maire", 0);
	}

	@Test
	void tiersFollowPopulationAndHouses() {
		assertEquals(TownTier.VILLAGE, TownTier.of(0, 0));
		assertEquals(TownTier.VILLAGE, TownTier.of(50, 3));
		assertEquals(TownTier.TOWN, TownTier.of(10, 4));
		assertEquals(TownTier.CITY, TownTier.of(30, 12));
		assertEquals(TownTier.MEGALOPOLIS, TownTier.of(200, 200));
		assertEquals(TownTier.TOWN, TownTier.VILLAGE.next());
		assertEquals(null, TownTier.MEGALOPOLIS.next());
	}

	@Test
	void territoryGrowsWithTheTier() {
		Town town = town();
		assertTrue(town.contains(Level.OVERWORLD, new BlockPos(40, 0, 0)));
		assertFalse(town.contains(Level.OVERWORLD, new BlockPos(60, 0, 0)));
		assertFalse(town.contains(Level.NETHER, new BlockPos(1, 64, 1)));
	}

	@Test
	void newcomersGetAFreeBed() {
		Town town = town();
		town.putHouse(new House(PLAQUE, List.of(BED_A, BED_B), List.of(), 30));
		assertEquals(2, town.freeBeds());
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		UUID carol = UUID.randomUUID();
		assertEquals(BED_A, town.welcome(alice, "Alice", 10).bed().orElseThrow());
		assertEquals(BED_B, town.welcome(bob, "Bob", 20).bed().orElseThrow());
		assertFalse(town.welcome(carol, "Carol", 30).hasHome());
		assertEquals(0, town.freeBeds());
		assertEquals(30, town.lastArrival());
		assertEquals(3, town.population());
	}

	@Test
	void losingABedEvictsItsSleeper() {
		Town town = town();
		town.putHouse(new House(PLAQUE, List.of(BED_A, BED_B), List.of(), 30));
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		town.welcome(alice, "Alice", 0);
		town.welcome(bob, "Bob", 0);
		town.putHouse(new House(PLAQUE, List.of(BED_A), List.of(), 30));
		assertTrue(town.resident(alice).orElseThrow().hasHome());
		assertFalse(town.resident(bob).orElseThrow().hasHome());
	}

	@Test
	void removingAHouseEvictsEveryone() {
		Town town = town();
		town.putHouse(new House(PLAQUE, List.of(BED_A, BED_B), List.of(), 30));
		UUID alice = UUID.randomUUID();
		town.welcome(alice, "Alice", 0);
		assertEquals(List.of(alice), town.removeHouse(PLAQUE));
		assertFalse(town.resident(alice).orElseThrow().hasHome());
		assertEquals(0, town.capacity());
	}

	@Test
	void homelessMoveInWhenABedFreesUp() {
		Town town = town();
		UUID alice = UUID.randomUUID();
		town.welcome(alice, "Alice", 0);
		assertFalse(town.resident(alice).orElseThrow().hasHome());
		town.putHouse(new House(PLAQUE, List.of(BED_A), List.of(), 30));
		assertTrue(town.house(alice).orElseThrow().hasHome());
	}

	@Test
	void aBedBelongsToOneHouse() {
		Town town = town();
		town.putHouse(new House(PLAQUE, List.of(BED_A), List.of(), 30));
		assertTrue(town.bedClaimedElsewhere(BED_A, new BlockPos(0, 0, 0)));
		assertFalse(town.bedClaimedElsewhere(BED_A, PLAQUE));
	}

	@Test
	void theTierRisesWithThePopulation() {
		Town town = town();
		for (int h = 0; h < 4; h++) {
			BlockPos plaque = new BlockPos(h * 5, 64, 20);
			town.putHouse(new House(plaque, List.of(plaque.east(), plaque.east(2), plaque.south()), List.of(), 30));
		}
		for (int i = 0; i < 9; i++) {
			town.welcome(UUID.randomUUID(), "C" + i, 0);
		}
		assertTrue(town.updateTier().isEmpty());
		town.welcome(UUID.randomUUID(), "Dixième", 0);
		assertEquals(TownTier.VILLAGE, town.updateTier().orElseThrow());
		assertEquals(TownTier.TOWN, town.tier());
		assertEquals(TownTier.TOWN.radius(), town.radius());
	}

	@Test
	void workstationsHireTheJobless() {
		Town town = town();
		BlockPos workshop = new BlockPos(30, 64, 30);
		BlockPos composter = workshop.east();
		BlockPos smoker = workshop.west();
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		UUID carol = UUID.randomUUID();
		town.welcome(alice, "Alice", 0);
		town.welcome(bob, "Bob", 0);
		town.welcome(carol, "Carol", 0);
		town.putHouse(new House(workshop, List.of(), List.of(), List.of(new House.Workstation(composter, Job.FARMER),
				new House.Workstation(smoker, Job.COOK)), 20));
		var hired = town.assignJobs();
		assertEquals(2, hired.size());
		assertEquals(Job.FARMER, town.resident(alice).orElseThrow().job().orElseThrow().job());
		assertEquals(Job.COOK, town.resident(bob).orElseThrow().job().orElseThrow().job());
		assertTrue(town.resident(carol).orElseThrow().job().isEmpty());
		assertTrue(town.assignJobs().isEmpty());
		// Un atelier sans lit ne compte pas comme maison pour le palier.
		assertEquals(0, town.homes().size());

		// Le fumoir disparaît : le cuisinier perd son emploi, et le poste ne revient à personne.
		town.putHouse(new House(workshop, List.of(), List.of(), List.of(new House.Workstation(composter, Job.FARMER)), 20));
		assertTrue(town.resident(bob).orElseThrow().job().isEmpty());
		assertTrue(town.resident(alice).orElseThrow().job().isPresent());
		town.removeHouse(workshop);
		assertTrue(town.resident(alice).orElseThrow().job().isEmpty());
	}

	@Test
	void theMayorManagesWithDeputies() {
		UUID mayor = UUID.randomUUID();
		Town town = Town.found("Valombre", Level.OVERWORLD, HALL, mayor, "Maire", 0);
		UUID deputy = UUID.randomUUID();
		assertTrue(town.canManage(mayor));
		assertFalse(town.canManage(deputy));
		assertTrue(town.addDeputy(deputy));
		assertTrue(town.canManage(deputy));
		assertFalse(town.addDeputy(mayor));
		town.setMayor(deputy, "Adjoint");
		assertEquals(deputy, town.mayor());
		assertFalse(town.deputies().contains(deputy));
	}

	@Test
	void namesAreDrawnFromTheLists() {
		java.util.Random random = new java.util.Random(42);
		String town = Names.town(random::nextInt);
		String citizen = Names.citizen(random::nextInt);
		assertFalse(town.isBlank());
		assertEquals(2, citizen.split(" ").length);
	}
}
