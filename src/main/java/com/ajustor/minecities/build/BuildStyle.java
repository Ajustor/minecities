package com.ajustor.minecities.build;

import java.util.Map;
import java.util.Optional;

/**
 * Les styles de construction, à la manière des villages vanilla : un même schéma se décline en
 * plaines, taïga, désert ou savane (les villages du jeu), et depuis la v1.15 en neige, cerisier,
 * marais, badlands ou colombage, en remplaçant ses jetons ({wall}, {roof}, {door}…) par les
 * blocs du style. Les bâtiments améliorés y prennent des matériaux plus nobles : fondation de pierre
 * taillée ({fine_foundation}) et toit d'escaliers ({roof_stairs}) ; au niveau 3, la maçonnerie
 * du style ({masonry} aux angles, {brick} au soubassement et à la cheminée), au niveau 4 son
 * toit noble ({fine_roof}, {fine_roof_stairs} : cuivre, ardoise ou cuivre patiné).
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
			Map.entry("trapdoor", "minecraft:oak_trapdoor"),
			Map.entry("carpet", "minecraft:red_carpet"),
			Map.entry("masonry", "minecraft:stone_bricks"),
			Map.entry("brick", "minecraft:bricks"),
			Map.entry("fine_roof", "minecraft:waxed_cut_copper"),
			Map.entry("fine_roof_stairs", "minecraft:waxed_cut_copper_stairs"),
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
			Map.entry("trapdoor", "minecraft:spruce_trapdoor"),
			Map.entry("carpet", "minecraft:brown_carpet"),
			Map.entry("masonry", "minecraft:mossy_stone_bricks"),
			Map.entry("brick", "minecraft:stone_bricks"),
			Map.entry("fine_roof", "minecraft:deepslate_tiles"),
			Map.entry("fine_roof_stairs", "minecraft:deepslate_tile_stairs"),
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
			Map.entry("trapdoor", "minecraft:birch_trapdoor"),
			Map.entry("carpet", "minecraft:yellow_carpet"),
			Map.entry("masonry", "minecraft:chiseled_sandstone"),
			Map.entry("brick", "minecraft:cut_sandstone"),
			Map.entry("fine_roof", "minecraft:waxed_cut_copper"),
			Map.entry("fine_roof_stairs", "minecraft:waxed_cut_copper_stairs"),
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
			Map.entry("trapdoor", "minecraft:acacia_trapdoor"),
			Map.entry("carpet", "minecraft:orange_carpet"),
			Map.entry("masonry", "minecraft:polished_granite"),
			Map.entry("brick", "minecraft:terracotta"),
			Map.entry("fine_roof", "minecraft:waxed_cut_copper"),
			Map.entry("fine_roof_stairs", "minecraft:waxed_cut_copper_stairs"),
			Map.entry("fine_foundation", "minecraft:stone_bricks"))),
	/** Le village enneigé : murs de neige tassée, charpente d'épicéa écorcé, touches de bleu. */
	SNOWY("snowy", Map.ofEntries(
			Map.entry("foundation", "minecraft:cobblestone"),
			Map.entry("floor", "minecraft:spruce_planks"),
			Map.entry("wall", "minecraft:snow_block"),
			Map.entry("frame", "minecraft:stripped_spruce_log"),
			Map.entry("roof", "minecraft:spruce_planks"),
			Map.entry("roof_slab", "minecraft:spruce_slab"),
			Map.entry("window", "minecraft:glass_pane"),
			Map.entry("door", "minecraft:spruce_door"),
			Map.entry("bed", "minecraft:light_blue_bed"),
			Map.entry("fence", "minecraft:spruce_fence"),
			Map.entry("fence_gate", "minecraft:spruce_fence_gate"),
			Map.entry("stripped_log", "minecraft:stripped_spruce_log"),
			Map.entry("roof_stairs", "minecraft:spruce_stairs"),
			Map.entry("steps", "minecraft:stone_brick_stairs"),
			Map.entry("trapdoor", "minecraft:spruce_trapdoor"),
			Map.entry("carpet", "minecraft:light_blue_carpet"),
			Map.entry("masonry", "minecraft:polished_diorite"),
			Map.entry("brick", "minecraft:stone_bricks"),
			Map.entry("fine_roof", "minecraft:deepslate_tiles"),
			Map.entry("fine_roof_stairs", "minecraft:deepslate_tile_stairs"),
			Map.entry("fine_foundation", "minecraft:stone_bricks"))),
	/** À la japonaise : cloisons claires, poteaux de cerisier, toit de tuiles d'ardoise. */
	CHERRY("cherry", Map.ofEntries(
			Map.entry("foundation", "minecraft:polished_andesite"),
			Map.entry("floor", "minecraft:cherry_planks"),
			Map.entry("wall", "minecraft:birch_planks"),
			Map.entry("frame", "minecraft:cherry_log"),
			Map.entry("roof", "minecraft:deepslate_tiles"),
			Map.entry("roof_slab", "minecraft:deepslate_tile_slab"),
			Map.entry("window", "minecraft:white_stained_glass_pane"),
			Map.entry("door", "minecraft:cherry_door"),
			Map.entry("bed", "minecraft:pink_bed"),
			Map.entry("fence", "minecraft:cherry_fence"),
			Map.entry("fence_gate", "minecraft:cherry_fence_gate"),
			Map.entry("stripped_log", "minecraft:stripped_cherry_log"),
			Map.entry("roof_stairs", "minecraft:deepslate_tile_stairs"),
			Map.entry("steps", "minecraft:polished_andesite_stairs"),
			Map.entry("trapdoor", "minecraft:cherry_trapdoor"),
			Map.entry("carpet", "minecraft:pink_carpet"),
			Map.entry("masonry", "minecraft:polished_deepslate"),
			Map.entry("brick", "minecraft:deepslate_bricks"),
			Map.entry("fine_roof", "minecraft:waxed_oxidized_cut_copper"),
			Map.entry("fine_roof_stairs", "minecraft:waxed_oxidized_cut_copper_stairs"),
			Map.entry("fine_foundation", "minecraft:polished_deepslate"))),
	/** Le marais : torchis de boue tassée sur pilotis de palétuvier. */
	SWAMP("swamp", Map.ofEntries(
			Map.entry("foundation", "minecraft:mud_bricks"),
			Map.entry("floor", "minecraft:mangrove_planks"),
			Map.entry("wall", "minecraft:packed_mud"),
			Map.entry("frame", "minecraft:mangrove_log"),
			Map.entry("roof", "minecraft:mangrove_planks"),
			Map.entry("roof_slab", "minecraft:mangrove_slab"),
			Map.entry("window", "minecraft:glass_pane"),
			Map.entry("door", "minecraft:mangrove_door"),
			Map.entry("bed", "minecraft:green_bed"),
			Map.entry("fence", "minecraft:mangrove_fence"),
			Map.entry("fence_gate", "minecraft:mangrove_fence_gate"),
			Map.entry("stripped_log", "minecraft:stripped_mangrove_log"),
			Map.entry("roof_stairs", "minecraft:mangrove_stairs"),
			Map.entry("steps", "minecraft:mud_brick_stairs"),
			Map.entry("trapdoor", "minecraft:mangrove_trapdoor"),
			Map.entry("carpet", "minecraft:green_carpet"),
			Map.entry("masonry", "minecraft:mud_bricks"),
			Map.entry("brick", "minecraft:bricks"),
			Map.entry("fine_roof", "minecraft:waxed_weathered_cut_copper"),
			Map.entry("fine_roof_stairs", "minecraft:waxed_weathered_cut_copper_stairs"),
			Map.entry("fine_foundation", "minecraft:mossy_stone_bricks"))),
	/** Les badlands : murs de terre cuite, chêne noir, toit de grès rouge. */
	BADLANDS("badlands", Map.ofEntries(
			Map.entry("foundation", "minecraft:red_sandstone"),
			Map.entry("floor", "minecraft:dark_oak_planks"),
			Map.entry("wall", "minecraft:terracotta"),
			Map.entry("frame", "minecraft:dark_oak_log"),
			Map.entry("roof", "minecraft:cut_red_sandstone"),
			Map.entry("roof_slab", "minecraft:red_sandstone_slab"),
			Map.entry("window", "minecraft:glass_pane"),
			Map.entry("door", "minecraft:dark_oak_door"),
			Map.entry("bed", "minecraft:white_bed"),
			Map.entry("fence", "minecraft:dark_oak_fence"),
			Map.entry("fence_gate", "minecraft:dark_oak_fence_gate"),
			Map.entry("stripped_log", "minecraft:stripped_dark_oak_log"),
			Map.entry("roof_stairs", "minecraft:red_sandstone_stairs"),
			Map.entry("steps", "minecraft:red_sandstone_stairs"),
			Map.entry("trapdoor", "minecraft:dark_oak_trapdoor"),
			Map.entry("carpet", "minecraft:white_carpet"),
			Map.entry("masonry", "minecraft:chiseled_red_sandstone"),
			Map.entry("brick", "minecraft:red_terracotta"),
			Map.entry("fine_roof", "minecraft:waxed_cut_copper"),
			Map.entry("fine_roof_stairs", "minecraft:waxed_cut_copper_stairs"),
			Map.entry("fine_foundation", "minecraft:cut_red_sandstone"))),
	/** Le colombage : un enduit blanc entre des pans de bois de chêne noir. */
	TIMBER("timber", Map.ofEntries(
			Map.entry("foundation", "minecraft:cobblestone"),
			Map.entry("floor", "minecraft:oak_planks"),
			Map.entry("wall", "minecraft:white_concrete"),
			Map.entry("frame", "minecraft:dark_oak_log"),
			Map.entry("roof", "minecraft:dark_oak_planks"),
			Map.entry("roof_slab", "minecraft:dark_oak_slab"),
			Map.entry("window", "minecraft:glass_pane"),
			Map.entry("door", "minecraft:dark_oak_door"),
			Map.entry("bed", "minecraft:blue_bed"),
			Map.entry("fence", "minecraft:dark_oak_fence"),
			Map.entry("fence_gate", "minecraft:dark_oak_fence_gate"),
			Map.entry("stripped_log", "minecraft:stripped_dark_oak_log"),
			Map.entry("roof_stairs", "minecraft:dark_oak_stairs"),
			Map.entry("steps", "minecraft:stone_brick_stairs"),
			Map.entry("trapdoor", "minecraft:dark_oak_trapdoor"),
			Map.entry("carpet", "minecraft:blue_carpet"),
			Map.entry("masonry", "minecraft:stone_bricks"),
			Map.entry("brick", "minecraft:bricks"),
			Map.entry("fine_roof", "minecraft:deepslate_tiles"),
			Map.entry("fine_roof_stairs", "minecraft:deepslate_tile_stairs"),
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

	/** Les noms de tous les styles, dans l'ordre (ce que les écrans font défiler). */
	public static String[] names() {
		BuildStyle[] styles = values();
		String[] names = new String[styles.length];
		for (int i = 0; i < styles.length; i++) {
			names[i] = styles[i].serializedName;
		}
		return names;
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
