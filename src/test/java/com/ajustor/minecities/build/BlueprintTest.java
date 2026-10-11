package com.ajustor.minecities.build;

import com.ajustor.minecities.town.TownTier;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintTest {
	private static Path blueprints() {
		return Path.of(System.getProperty("minecities.resources"), "data/minecities/blueprint");
	}

	@Test
	void everyShippedBlueprintParsesAndHasAPlaque() throws IOException {
		try (Stream<Path> files = Files.list(blueprints())) {
			for (Path file : files.toList()) {
				String name = file.getFileName().toString().replace(".json", "");
				Blueprint blueprint = Blueprint.parse(Identifier.fromNamespaceAndPath("minecities", name),
						JsonParser.parseString(Files.readString(file)).getAsJsonObject());
				assertTrue(blueprint.cells().stream().anyMatch(c -> c.template().startsWith("minecities:house_plaque")),
						name + " : pas de plaque");
				assertTrue(blueprint.cells().stream().anyMatch(c -> c.template().contains("{door}")), name + " : pas de porte");
				assertFalse(blueprint.cells().isEmpty());
			}
		}
	}

	private static Blueprint read(String name) throws IOException {
		return Blueprint.parse(Identifier.fromNamespaceAndPath("minecities", name),
				JsonParser.parseString(Files.readString(blueprints().resolve(name + ".json"))).getAsJsonObject());
	}

	@Test
	void everyBuildingGrowsThroughFourLevels() throws IOException {
		for (String family : List.of("house", "kitchen", "farm", "barracks", "market", "mine", "bakery", "smithy")) {
			for (int level = 1; level <= Blueprint.LEVELS; level++) {
				Blueprint blueprint = read(family + "_" + level);
				assertEquals(level, blueprint.level());
				assertEquals(family, blueprint.family());
				assertEquals(level < Blueprint.LEVELS
						? Optional.of(Identifier.fromNamespaceAndPath("minecities", family + "_" + (level + 1)))
						: Optional.empty(), blueprint.upgrade());
			}
		}
		// Les niveaux 3 et 4 attendent la ville, puis la grande ville ; le marché, la ville dès le début.
		assertEquals(TownTier.VILLAGE, read("house_2").tier());
		assertEquals(TownTier.TOWN, read("house_3").tier());
		assertEquals(TownTier.CITY, read("house_4").tier());
		assertEquals(TownTier.TOWN, read("market_1").tier());
	}

	@Test
	void eachLevelHousesMoreAndCostsMore() throws IOException {
		int beds = 0;
		for (int level = 1; level <= Blueprint.LEVELS; level++) {
			Blueprint house = read("house_" + level);
			long here = house.cells().stream().filter(c -> c.template().contains("part=head")).count();
			assertEquals(2 * level, here);
			assertTrue(here > beds);
			beds = (int) here;
		}
		assertTrue(read("house_1").cells().stream().noneMatch(c -> c.template().contains("bricks")));
		assertTrue(read("house_3").cells().stream().anyMatch(c -> c.template().equals("minecraft:bricks")));
		assertTrue(read("house_4").cells().stream().anyMatch(c -> c.template().equals("minecraft:gold_block")));
	}

	@Test
	void oldBlueprintsLeadToTheNewLevels() throws IOException {
		Blueprint small = read("house_small");
		assertTrue(small.legacy());
		assertEquals(Identifier.fromNamespaceAndPath("minecities", "house_2"), small.upgrade().orElseThrow());
		assertEquals(3, read("manor").level());
		assertEquals(2, read("kitchen_large").level());
		assertFalse(read("house_1").legacy());
	}

	@Test
	void stylesReplaceTheirTokens() {
		assertEquals("minecraft:spruce_door[facing=north]", BuildStyle.TAIGA.resolve("{door}[facing=north]"));
		assertEquals("minecraft:sandstone", BuildStyle.DESERT.resolve("{foundation}"));
		for (BuildStyle style : BuildStyle.values()) {
			for (String token : new String[]{"foundation", "floor", "wall", "frame", "roof", "roof_slab", "window",
					"door", "bed", "fence", "fence_gate", "stripped_log", "roof_stairs", "fine_foundation", "steps"}) {
				assertFalse(style.resolve("{" + token + "}").contains("{"), style + " : " + token);
			}
		}
	}

	@Test
	void theDoorFacesTheOneWhoOrders() {
		// La porte est au nord du schéma : elle doit regarder vers le joueur.
		assertEquals(Rotation.NONE, BuildPlan.facing(Direction.NORTH));
		assertEquals(Rotation.CLOCKWISE_90, BuildPlan.facing(Direction.EAST));
		assertEquals(Rotation.CLOCKWISE_180, BuildPlan.facing(Direction.SOUTH));
		assertEquals(Rotation.COUNTERCLOCKWISE_90, BuildPlan.facing(Direction.WEST));
		// Tourner un décalage suit la rotation des directions : l'est devient le sud.
		assertEquals(new BlockPos(0, 0, 1), BuildPlan.offset(Rotation.CLOCKWISE_90, 1, 0, 0));
		assertEquals(new BlockPos(-1, 2, 0), BuildPlan.offset(Rotation.CLOCKWISE_180, 1, 2, 0));
	}
}
