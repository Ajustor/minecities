package com.ajustor.minecities.town;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Une ville sauvegardée se relit telle quelle, y compris ce que les dernières versions ont ajouté. */
class TownSaveTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void aTownSurvivesSavingAndLoading() {
		Town town = Town.found("Valombre", Level.OVERWORLD, new BlockPos(0, 64, 0), UUID.randomUUID(), "Maire", 0);
		BlockPos plaque = new BlockPos(10, 64, 10);
		town.putHouse(new House(plaque, List.of(plaque.east()), List.of(), 30)
				.within(new BlockPos(8, 63, 8), new BlockPos(14, 68, 14)));
		UUID alice = UUID.randomUUID();
		town.welcome(alice, "Alice Martin", 100);
		town.forceTier(Optional.of(TownTier.CITY));
		town.updateTier();
		town.addAnnex(new BlockPos(100, 64, 0));
		town.orphan(Optional.of(5000L));
		town.chronicle().celebrate(4000);
		town.chronicle().earn(4000, 7);
		town.chronicle().death(3000);

		JsonElement json = Town.CODEC.encodeStart(JsonOps.INSTANCE, town).getOrThrow();
		Town read = Town.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

		assertEquals("Valombre", read.name());
		assertEquals(TownTier.CITY, read.tier());
		assertEquals(Optional.of(TownTier.CITY), read.forcedTier());
		assertEquals(List.of(new BlockPos(100, 64, 0)), read.annexes());
		assertEquals(Optional.of(5000L), read.orphanedAt());
		assertEquals("Alice Martin", read.resident(alice).orElseThrow().name());
		assertTrue(read.house(plaque).orElseThrow().encloses(new BlockPos(14, 68, 14)));
		assertEquals(7, read.chronicle().earnedToday(4000));
		assertEquals(4000, read.chronicle().festivalAt());
		assertEquals(1, read.chronicle().recentDeaths(4000));
	}

	@Test
	void anOldSaveWithoutTheNewFieldsStillLoads() {
		Town town = Town.found("Valombre", Level.OVERWORLD, new BlockPos(0, 64, 0), UUID.randomUUID(), "Maire", 0);
		JsonElement json = Town.CODEC.encodeStart(JsonOps.INSTANCE, town).getOrThrow();
		for (String added : List.of("annexes", "forced_tier", "orphaned_at", "deaths", "festival_at", "earned_total")) {
			json.getAsJsonObject().remove(added);
		}
		Town read = Town.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(TownTier.VILLAGE, read.tier());
		assertTrue(read.annexes().isEmpty());
		assertTrue(read.orphanedAt().isEmpty());
	}
}
