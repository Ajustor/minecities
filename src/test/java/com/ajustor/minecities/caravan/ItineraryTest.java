package com.ajustor.minecities.caravan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItineraryTest {
	private static final List<BlockPos> PATH = List.of(new BlockPos(0, 64, 0), new BlockPos(10, 64, 0),
			new BlockPos(10, 64, 20));

	@Test
	void theLengthFollowsEverySegment() {
		assertEquals(30, Itinerary.length(PATH), 1.0E-9);
	}

	@Test
	void aDistanceFallsOnTheRightSegment() {
		assertEquals(new Vec3(5.5, 64, 0.5), Itinerary.at(PATH, 5));
		assertEquals(new Vec3(10.5, 64, 5.5), Itinerary.at(PATH, 15));
		// Au-delà du bout, on reste au bout.
		assertEquals(new Vec3(10.5, 64, 20.5), Itinerary.at(PATH, 99));
	}

	@Test
	void theYawFacesTheWayAhead() {
		// Vers +x : lacet -90 (comme une entité qui regarde vers l'est) ; vers +z : 0.
		assertEquals(-90, Itinerary.yaw(PATH, 3), 1.0E-3);
		assertEquals(0, Itinerary.yaw(PATH, 20), 1.0E-3);
		// Au bout, il garde la direction du dernier segment.
		assertEquals(0, Itinerary.yaw(PATH, 30), 1.0E-3);
	}

	@Test
	void straightStepsAreMerged() {
		List<BlockPos> steps = List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(2, 64, 0),
				new BlockPos(3, 64, 1), new BlockPos(4, 64, 2), new BlockPos(4, 65, 3));
		assertEquals(List.of(new BlockPos(0, 64, 0), new BlockPos(2, 64, 0), new BlockPos(4, 64, 2),
				new BlockPos(4, 65, 3)), Itinerary.simplify(steps));
	}
}
