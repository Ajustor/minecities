package com.ajustor.minecities.build;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

	@Test
	void theSmallHouseGrowsIntoTheLargeOne() throws IOException {
		Blueprint small = Blueprint.parse(Identifier.fromNamespaceAndPath("minecities", "house_small"),
				JsonParser.parseString(Files.readString(blueprints().resolve("house_small.json"))).getAsJsonObject());
		assertEquals(Identifier.fromNamespaceAndPath("minecities", "house_large"), small.upgrade().orElseThrow());
		assertEquals(7, small.width());
		assertEquals(7, small.depth());
		assertEquals(6, small.height());
	}

	@Test
	void stylesReplaceTheirTokens() {
		assertEquals("minecraft:spruce_door[facing=north]", BuildStyle.TAIGA.resolve("{door}[facing=north]"));
		assertEquals("minecraft:sandstone", BuildStyle.DESERT.resolve("{foundation}"));
		for (BuildStyle style : BuildStyle.values()) {
			for (String token : new String[]{"foundation", "floor", "wall", "frame", "roof", "roof_slab", "window",
					"door", "bed", "fence", "fence_gate", "stripped_log"}) {
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
