package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Job;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Les fabrications de la v1.4 (brief 12). Ce qui demande de vrais objets (le charpentier, ce que garde
 * chaque atelier) se vérifie en jeu, dans la scène « logistics ».
 */
class LogisticsTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void smithsForgeShearsAndIronBlocks() {
		assertEquals(Items.SHEARS, SmithGoal.forgings("shears").getFirst().result());
		assertEquals(9, SmithGoal.forgings("iron_block").getFirst().materials().get(Items.IRON_INGOT));
		assertEquals("shears", SmithGoal.toolOf(Job.BEEKEEPER).orElseThrow());
	}
}
