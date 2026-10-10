package com.ajustor.minecities.town;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseScannerTest {
	/**
	 * Un monde de blocs posés à la main : tout ce qui n'est pas posé est de l'air, et le sol (y < 0)
	 * est plein.
	 */
	private static final class Blocks implements HouseScanner.World {
		final Map<HouseScanner.Pos, HouseScanner.Cell> cells = new HashMap<>();

		@Override
		public HouseScanner.Cell classify(int x, int y, int z) {
			if (y < 0) {
				return HouseScanner.Cell.SOLID;
			}
			return cells.getOrDefault(new HouseScanner.Pos(x, y, z), HouseScanner.Cell.OPEN);
		}

		void set(int x, int y, int z, HouseScanner.Cell cell) {
			cells.put(new HouseScanner.Pos(x, y, z), cell);
		}

		/** Une boîte creuse : murs, sol et toit pleins autour de l'intérieur [x0, x1] x [0, h) x [z0, z1]. */
		Blocks room(int x0, int z0, int x1, int z1, int height) {
			for (int x = x0 - 1; x <= x1 + 1; x++) {
				for (int z = z0 - 1; z <= z1 + 1; z++) {
					for (int y = 0; y <= height; y++) {
						boolean inside = x >= x0 && x <= x1 && z >= z0 && z <= z1 && y < height;
						if (!inside) {
							set(x, y, z, HouseScanner.Cell.SOLID);
						}
					}
				}
			}
			return this;
		}
	}

	private static Blocks furnishedRoom() {
		Blocks world = new Blocks().room(0, 0, 4, 4, 3);
		// Une porte dans le mur nord, un lit et un coffre contre le mur est.
		world.set(2, 0, -1, HouseScanner.Cell.DOOR);
		world.set(2, 1, -1, HouseScanner.Cell.DOOR);
		world.set(4, 0, 2, HouseScanner.Cell.BED);
		world.set(4, 0, 3, HouseScanner.Cell.SOLID);
		world.set(0, 0, 4, HouseScanner.Cell.CONTAINER);
		return world;
	}

	private static final HouseScanner.Pos PLAQUE = new HouseScanner.Pos(1, 1, 1);

	@Test
	void aClosedRoomWithADoorAndABedIsAHouse() {
		HouseScanner.Result result = HouseScanner.scan(furnishedRoom(), PLAQUE);
		assertEquals(HouseScanner.Status.VALID, result.status());
		assertEquals(1, result.beds().size());
		assertEquals(new HouseScanner.Pos(4, 0, 2), result.beds().getFirst());
		assertEquals(1, result.containers().size());
		// 5x5x3 moins le lit (deux blocs) et le coffre.
		assertEquals(5 * 5 * 3 - 3, result.volume());
	}

	@Test
	void aHoleInTheWallLeaksOutside() {
		Blocks world = furnishedRoom();
		world.set(0, 1, -1, HouseScanner.Cell.OPEN);
		assertEquals(HouseScanner.Status.NOT_ENCLOSED, HouseScanner.scan(world, PLAQUE).status());
	}

	@Test
	void aMissingRoofLeaksOutside() {
		Blocks world = furnishedRoom();
		world.set(2, 3, 2, HouseScanner.Cell.OPEN);
		assertEquals(HouseScanner.Status.NOT_ENCLOSED, HouseScanner.scan(world, PLAQUE).status());
	}

	@Test
	void aHouseNeedsADoor() {
		Blocks world = furnishedRoom();
		world.set(2, 0, -1, HouseScanner.Cell.SOLID);
		world.set(2, 1, -1, HouseScanner.Cell.SOLID);
		assertEquals(HouseScanner.Status.NO_DOOR, HouseScanner.scan(world, PLAQUE).status());
	}

	@Test
	void aHouseNeedsABed() {
		Blocks world = furnishedRoom();
		world.set(4, 0, 2, HouseScanner.Cell.SOLID);
		assertEquals(HouseScanner.Status.NO_BED, HouseScanner.scan(world, PLAQUE).status());
	}

	@Test
	void aCupboardIsNotAHouse() {
		Blocks world = new Blocks().room(0, 0, 0, 0, 2);
		world.set(0, 0, -1, HouseScanner.Cell.DOOR);
		world.set(1, 0, 0, HouseScanner.Cell.BED);
		assertEquals(HouseScanner.Status.TOO_SMALL, HouseScanner.scan(world, new HouseScanner.Pos(0, 1, 0)).status());
	}

	@Test
	void aHallTooBigIsNotAHouse() {
		Blocks world = new Blocks().room(0, 0, 40, 3, 3);
		world.set(2, 0, -1, HouseScanner.Cell.DOOR);
		world.set(1, 0, 4, HouseScanner.Cell.BED);
		assertEquals(HouseScanner.Status.NOT_ENCLOSED, HouseScanner.scan(world, PLAQUE).status());
	}

	@Test
	void twoRoomsJoinedByAnOpeningAreOneHouse() {
		Blocks world = new Blocks().room(0, 0, 9, 4, 3);
		// Une cloison percée d'un passage, et un lit de chaque côté.
		for (int z = 0; z <= 4; z++) {
			for (int y = 0; y < 3; y++) {
				if (!(z == 2 && y < 2)) {
					world.set(5, y, z, HouseScanner.Cell.SOLID);
				}
			}
		}
		world.set(2, 0, -1, HouseScanner.Cell.DOOR);
		world.set(0, 0, 2, HouseScanner.Cell.BED);
		world.set(9, 0, 2, HouseScanner.Cell.BED);
		HouseScanner.Result result = HouseScanner.scan(world, PLAQUE);
		assertTrue(result.valid());
		assertEquals(2, result.beds().size());
	}
}
