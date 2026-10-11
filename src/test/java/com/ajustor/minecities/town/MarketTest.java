package com.ajustor.minecities.town;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le marché, les paliers qui ouvrent des métiers, les mairies annexes (v0.7). */
class MarketTest {
	private static final BlockPos HALL = new BlockPos(0, 64, 0);

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void onlyTheSurplusIsSoldAtVillagerRates() {
		int rate = Market.rate(Items.WHEAT).orElseThrow();
		assertEquals(20, rate);
		// La réserve reste au grenier, et l'on ne vend que par émeraude entière.
		assertEquals(0, Market.saleable(Market.RESERVE, rate));
		assertEquals(0, Market.saleable(Market.RESERVE + 19, rate));
		assertEquals(40, Market.saleable(Market.RESERVE + 45, rate));
		assertEquals(2, Market.emeralds(40, rate));
		assertTrue(Market.rate(Items.DIAMOND).isEmpty());
	}

	@Test
	void theMerchantBuysEachWorkersTool() {
		assertEquals(Items.STONE_HOE, Market.toolFor(Job.FARMER).orElseThrow().item());
		assertEquals(Items.FISHING_ROD, Market.toolFor(Job.FISHERMAN).orElseThrow().item());
		assertEquals(2, Market.toolFor(Job.ARCHER).orElseThrow().price());
		assertTrue(Market.toolFor(Job.COOK).isEmpty());
		assertEquals(Items.STONE_PICKAXE, Market.builderTool("pickaxe").orElseThrow().item());
		assertEquals(16, Market.offer(Items.ARROW).orElseThrow().count());
	}

	@Test
	void theMarketOpensWithTheTown() {
		Town town = Town.found("Valombre", Level.OVERWORLD, HALL, UUID.randomUUID(), "Maire", 0);
		BlockPos plaque = new BlockPos(10, 64, 10);
		town.putHouse(new House(plaque, List.of(), List.of(), List.of(new House.Workstation(plaque.east(), Job.MERCHANT),
				new House.Workstation(plaque.west(), Job.FARMER)), 30));
		town.welcome(UUID.randomUUID(), "Alice", 0);
		town.welcome(UUID.randomUUID(), "Bob", 0);
		town.assignJobs();
		assertEquals(1, town.workers(Job.FARMER));
		assertEquals(0, town.workers(Job.MERCHANT));
		assertFalse(Job.MERCHANT.unlockedAt(TownTier.VILLAGE));
		// Un palier imposé (par un opérateur) ouvre le marché.
		town.forceTier(Optional.of(TownTier.TOWN));
		town.updateTier();
		town.assignJobs();
		assertEquals(1, town.workers(Job.MERCHANT));
	}

	@Test
	void annexesExtendTheTerritoryOfBigTowns() {
		Town town = Town.found("Valombre", Level.OVERWORLD, HALL, UUID.randomUUID(), "Maire", 0);
		BlockPos far = new BlockPos(160, 64, 0);
		BlockPos annex = new BlockPos(120, 64, 0);
		assertEquals(Optional.of("tier"), town.annexRefusal(Level.OVERWORLD, annex));
		town.forceTier(Optional.of(TownTier.CITY));
		town.updateTier();
		assertFalse(town.contains(Level.OVERWORLD, far));
		assertEquals(Optional.of("too_close"), town.annexRefusal(Level.OVERWORLD, new BlockPos(20, 64, 0)));
		assertEquals(Optional.of("outside"), town.annexRefusal(Level.OVERWORLD, new BlockPos(300, 64, 0)));
		assertEquals(Optional.empty(), town.annexRefusal(Level.OVERWORLD, annex));
		town.addAnnex(annex);
		assertTrue(town.contains(Level.OVERWORLD, far));
		// Le palier redescend : l'annexe n'est plus en service, le territoire se resserre.
		town.forceTier(Optional.of(TownTier.TOWN));
		town.updateTier();
		assertTrue(town.activeAnnexes().isEmpty());
		assertFalse(town.contains(Level.OVERWORLD, far));
	}

	@Test
	void theMarketKeepsItsBooks() {
		Chronicle chronicle = Chronicle.empty();
		chronicle.earn(1000, 3);
		chronicle.earn(2000, 2);
		assertEquals(5, chronicle.earnedToday(5000));
		// Un nouveau jour : on recommence à compter.
		assertEquals(0, chronicle.earnedToday(25000));
		chronicle.earn(25000, 1);
		assertEquals(1, chronicle.earnedToday(25000));
		assertEquals(6, chronicle.earnedTotal());
	}
}
