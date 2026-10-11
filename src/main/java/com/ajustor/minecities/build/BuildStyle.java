package com.ajustor.minecities.build;

import java.util.Map;
import java.util.Optional;

/**
 * Les styles de construction, à la manière des villages vanilla : un même schéma se décline en
 * plaines, taïga, désert ou savane, en remplaçant ses jetons ({wall}, {roof}, {door}…) par les
 * blocs du style. Les bâtiments améliorés y prennent des matériaux plus nobles : fondation de pierre
 * taillée ({fine_foundation}) et toit d'escaliers ({roof_stairs}).
 */
public enum BuildStyle {
	PLAINS("plains", Map.ofEntries(
			Map.entry("foundation", "minecraft:cobblestone"),
			Map.entry("floor", "minecraft:oak_planks"),
			Map.entry("wall", "minecraft:oak_planks"),
			Map.entry("frame", "minecraft:oak_log"),
			Map.entry("roof", "minecraft:spruce_planks"),
			Map.entry("roof_slab", "minecraft:spruce_slab"),
			Map.entry("window", "minecraft:glass_pane"),
			Map.entry("door", "minecraft:oak_door"),
			Map.entry("bed", "minecraft:red_bed"),
			Map.entry("fence", "minecraft:oak_fence"),
			Map.entry("fence_gate", "minecraft:oak_fence_gate"),
			Map.entry("stripped_log", "minecraft:stripped_oak_log"),
			Map.entry("roof_stairs", "minecraft:spruce_stairs"),
			Map.entry("steps", "minecraft:cobblestone_stairs"),
			Map.entry("fine_foundation", "minecraft:stone_bricks"))),
	TAIGA("taiga", Map.ofEntries(
			Map.entry("foundation", "minecraft:cobblestone"),
			Map.entry("floor", "minecraft:spruce_planks"),
			Map.entry("wall", "minecraft:spruce_planks"),
			Map.entry("frame", "minecraft:spruce_log"),
			Map.entry("roof", "minecraft:dark_oak_planks"),
			Map.entry("roof_slab", "minecraft:dark_oak_slab"),
			Map.entry("window", "minecraft:glass_pane"),
			Map.entry("door", "minecraft:spruce_door"),
			Map.entry("bed", "minecraft:brown_bed"),
			Map.entry("fence", "minecraft:spruce_fence"),
			Map.entry("fence_gate", "minecraft:spruce_fence_gate"),
			Map.entry("stripped_log", "minecraft:stripped_spruce_log"),
			Map.entry("roof_stairs", "minecraft:dark_oak_stairs"),
			Map.entry("steps", "minecraft:cobblestone_stairs"),
			Map.entry("fine_foundation", "minecraft:mossy_stone_bricks"))),
	DESERT("desert", Map.ofEntries(
			Map.entry("foundation", "minecraft:sandstone"),
			Map.entry("floor", "minecraft:smooth_sandstone"),
			Map.entry("wall", "minecraft:smooth_sandstone"),
			Map.entry("frame", "minecraft:cut_sandstone"),
			Map.entry("roof", "minecraft:cut_sandstone"),
			Map.entry("roof_slab", "minecraft:smooth_sandstone_slab"),
			Map.entry("window", "minecraft:glass_pane"),
			Map.entry("door", "minecraft:jungle_door"),
			Map.entry("bed", "minecraft:yellow_bed"),
			Map.entry("fence", "minecraft:jungle_fence"),
			Map.entry("fence_gate", "minecraft:jungle_fence_gate"),
			Map.entry("stripped_log", "minecraft:stripped_jungle_log"),
			Map.entry("roof_stairs", "minecraft:smooth_sandstone_stairs"),
			Map.entry("steps", "minecraft:sandstone_stairs"),
			Map.entry("fine_foundation", "minecraft:chiseled_sandstone"))),
	SAVANNA("savanna", Map.ofEntries(
			Map.entry("foundation", "minecraft:cobblestone"),
			Map.entry("floor", "minecraft:acacia_planks"),
			Map.entry("wall", "minecraft:acacia_planks"),
			Map.entry("frame", "minecraft:acacia_log"),
			Map.entry("roof", "minecraft:orange_terracotta"),
			Map.entry("roof_slab", "minecraft:acacia_slab"),
			Map.entry("window", "minecraft:glass_pane"),
			Map.entry("door", "minecraft:acacia_door"),
			Map.entry("bed", "minecraft:orange_bed"),
			Map.entry("fence", "minecraft:acacia_fence"),
			Map.entry("fence_gate", "minecraft:acacia_fence_gate"),
			Map.entry("stripped_log", "minecraft:stripped_acacia_log"),
			Map.entry("roof_stairs", "minecraft:acacia_stairs"),
			Map.entry("steps", "minecraft:cobblestone_stairs"),
			Map.entry("fine_foundation", "minecraft:stone_bricks")));

	private final String serializedName;
	private final Map<String, String> tokens;

	BuildStyle(String serializedName, Map<String, String> tokens) {
		this.serializedName = serializedName;
		this.tokens = tokens;
	}

	public String serializedName() {
		return serializedName;
	}

	public String translationKey() {
		return "style.minecities." + serializedName;
	}

	/** Remplace les jetons {nom} d'un bloc écrit dans un schéma par ceux du style. */
	public String resolve(String template) {
		String out = template;
		for (Map.Entry<String, String> e : tokens.entrySet()) {
			out = out.replace("{" + e.getKey() + "}", e.getValue());
		}
		return out;
	}

	public static Optional<BuildStyle> fromSerializedName(String name) {
		for (BuildStyle style : values()) {
			if (style.serializedName.equals(name)) {
				return Optional.of(style);
			}
		}
		return Optional.empty();
	}
}
