package com.ajustor.minecities.citizen;

import com.ajustor.minecities.registry.McItems;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.ajustor.minecities.citizen.CraftGoal.Craft;
import static com.ajustor.minecities.citizen.CraftGoal.Input;
import static com.ajustor.minecities.citizen.CraftGoal.of;

/**
 * Les ouvrages des artisans (brief 11), qui ferment les filières : la mine nourrit le joaillier et le
 * maçon, la rivière et le désert le verrier, l'élevage le tisserand, et tous remplissent les besoins
 * des bâtisseurs (briques, pierre taillée, verre, lits) ou le trésor (les bijoux se vendent cher).
 */
final class Crafts {
	private Crafts() {
	}

	/** Le joaillier : des bijoux, du plus précieux au plus simple ; le marchand les vend cher. */
	static final List<Craft> JEWELER = List.of(
			new Craft(List.of(of(Items.GOLD_INGOT, 3), of(Items.DIAMOND, 1)), new ItemStack(McItems.DIADEM), 0),
			new Craft(List.of(of(Items.GOLD_INGOT, 2), of(Items.LAPIS_LAZULI, 2)), new ItemStack(McItems.GOLD_RING), 0),
			new Craft(List.of(of(Items.COPPER_INGOT, 2), of(Items.AMETHYST_SHARD, 1)),
					new ItemStack(McItems.AMETHYST_BROOCH), 0));

	/** Le maçon : pierre taillée, briques et pierre polie pour les bâtisses et les demeures. */
	static final List<Craft> MASON = List.of(
			new Craft(List.of(of(Items.CLAY_BALL, 4)), new ItemStack(Items.BRICKS), 64),
			new Craft(List.of(of(Items.ANDESITE, 1)), new ItemStack(Items.POLISHED_ANDESITE), 64),
			new Craft(List.of(of(Items.COBBLESTONE, 1)), new ItemStack(Items.STONE_BRICKS), 64));

	/** Le verrier : le verre, les vitres, et les vitraux blancs des demeures. */
	static final List<Craft> GLASSBLOWER = List.of(
			new Craft(List.of(of(Items.GLASS_PANE, 8), of(Items.DYE.white(), 1)),
					new ItemStack(Items.STAINED_GLASS_PANE.white(), 8), 32),
			new Craft(List.of(of(Items.GLASS, 3)), new ItemStack(Items.GLASS_BOTTLE, 3), 16),
			new Craft(List.of(of(Items.GLASS, 6)), new ItemStack(Items.GLASS_PANE, 16), 64),
			new Craft(List.of(new Input(s -> s.is(ItemTags.SMELTS_TO_GLASS), 1)), new ItemStack(Items.GLASS), 64));

	/** Le tisserand : la laine (de la ficelle), des lits pour les maisons, des manteaux contre la pluie. */
	static final List<Craft> WEAVER = List.of(
			new Craft(List.of(new Input(s -> s.is(ItemTags.WOOL), 3), new Input(s -> s.is(ItemTags.PLANKS), 3)),
					new ItemStack(Items.BED.white()), 8),
			new Craft(List.of(new Input(s -> s.is(ItemTags.WOOL), 6), of(Items.LEATHER, 2)),
					new ItemStack(McItems.COAT), 8),
			new Craft(List.of(of(Items.STRING, 4)), new ItemStack(Items.WOOL.white()), 32),
			new Craft(List.of(of(Items.STRING, 1), of(Items.HONEYCOMB, 1)), new ItemStack(Items.CANDLE), 8));

	/** Les essences de bois que le charpentier sait travailler. */
	static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
			"cherry", "pale_oak");

	private static net.minecraft.world.item.Item item(String name) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
				net.minecraft.resources.Identifier.withDefaultNamespace(name));
	}

	/** La bûche dont on tire ces planches (quatre par bûche) ; vide si ce ne sont pas des planches. */
	static java.util.Optional<net.minecraft.world.item.Item> logOf(net.minecraft.world.item.Item planks) {
		for (String wood : WOODS) {
			if (planks == item(wood + "_planks") && item(wood + "_log") != Items.AIR) {
				return java.util.Optional.of(item(wood + "_log"));
			}
		}
		return java.util.Optional.empty();
	}

	/** Le charpentier : des planches de chaque essence, des bâtons, des bols (le reste sur commande). */
	static final List<Craft> CARPENTER = carpenter();

	private static List<Craft> carpenter() {
		java.util.List<Craft> crafts = new java.util.ArrayList<>();
		for (String wood : WOODS) {
			net.minecraft.world.item.Item log = item(wood + "_log");
			net.minecraft.world.item.Item planks = item(wood + "_planks");
			if (log != Items.AIR && planks != Items.AIR) {
				crafts.add(new Craft(List.of(of(log, 1)), new ItemStack(planks, 4), 64));
			}
		}
		crafts.add(new Craft(List.of(new Input(s -> s.is(net.minecraft.tags.ItemTags.PLANKS), 2)),
				new ItemStack(Items.STICK, 4), 32));
		crafts.add(new Craft(List.of(new Input(s -> s.is(net.minecraft.tags.ItemTags.PLANKS), 3)),
				new ItemStack(Items.BOWL, 4), 8));
		return List.copyOf(crafts);
	}

	/**
	 * Un ouvrage de bois que réclame un bâtisseur (une porte, des escaliers de chêne…), d'après la
	 * recette vanilla : vide si ce n'en est pas un.
	 */
	static java.util.Optional<Craft> woodwork(net.minecraft.world.item.Item wanted) {
		String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(wanted).getPath();
		for (String wood : WOODS) {
			if (!name.startsWith(wood + "_")) {
				continue;
			}
			net.minecraft.world.item.Item planks = item(wood + "_planks");
			String form = name.substring(wood.length() + 1);
			Craft craft = switch (form) {
				case "stairs" -> new Craft(List.of(of(planks, 6)), new ItemStack(wanted, 4), 0);
				case "slab" -> new Craft(List.of(of(planks, 3)), new ItemStack(wanted, 6), 0);
				case "door" -> new Craft(List.of(of(planks, 6)), new ItemStack(wanted, 3), 0);
				case "trapdoor" -> new Craft(List.of(of(planks, 6)), new ItemStack(wanted, 2), 0);
				case "fence" -> new Craft(List.of(of(planks, 4), of(Items.STICK, 2)), new ItemStack(wanted, 3), 0);
				case "fence_gate" -> new Craft(List.of(of(planks, 2), of(Items.STICK, 4)), new ItemStack(wanted, 1), 0);
				case "pressure_plate" -> new Craft(List.of(of(planks, 2)), new ItemStack(wanted, 1), 0);
				case "button" -> new Craft(List.of(of(planks, 1)), new ItemStack(wanted, 1), 0);
				default -> null;
			};
			return java.util.Optional.ofNullable(craft);
		}
		return java.util.Optional.empty();
	}

	/** Ce qu'on fait de ce qui manque à un chantier : jusqu'à tant d'avance, pas davantage. */
	private static final int ON_ORDER = 4;

	private static Input planks(int count) {
		return new Input(s -> s.is(ItemTags.PLANKS), count);
	}

	/**
	 * Un meuble que réclame un bâtisseur (brief 11 : les niveaux 3 et 4), d'après la recette vanilla ; le
	 * charpentier le fait sur commande. Vide si ce n'en est pas un.
	 */
	static java.util.Optional<Craft> furniture(net.minecraft.world.item.Item wanted) {
		Craft craft = null;
		if (wanted == Items.CHEST) {
			craft = new Craft(List.of(planks(8)), new ItemStack(Items.CHEST), ON_ORDER);
		} else if (wanted == Items.BARREL) {
			craft = new Craft(List.of(planks(7)), new ItemStack(Items.BARREL), ON_ORDER);
		} else if (wanted == Items.CRAFTING_TABLE) {
			craft = new Craft(List.of(planks(4)), new ItemStack(Items.CRAFTING_TABLE), ON_ORDER);
		} else if (wanted == Items.BOOKSHELF) {
			craft = new Craft(List.of(planks(6), of(Items.BOOK, 3)), new ItemStack(Items.BOOKSHELF), ON_ORDER);
		} else if (wanted == Items.COMPOSTER) {
			craft = new Craft(List.of(planks(4)), new ItemStack(Items.COMPOSTER), ON_ORDER);
		} else if (wanted == Items.CAMPFIRE) {
			craft = new Craft(List.of(of(Items.STICK, 3), new Input(s -> s.is(ItemTags.LOGS), 3), of(Items.COAL, 1)),
					new ItemStack(Items.CAMPFIRE), ON_ORDER);
		}
		return java.util.Optional.ofNullable(craft);
	}

	/** La pierre ouvragée que réclame un bâtisseur ; le maçon la fait sur commande. Vide si ce n'en est pas. */
	static java.util.Optional<Craft> stonework(net.minecraft.world.item.Item wanted) {
		Craft craft = null;
		if (wanted == Items.SMOOTH_STONE_SLAB) {
			craft = new Craft(List.of(of(Items.COBBLESTONE, 2)), new ItemStack(Items.SMOOTH_STONE_SLAB, 2), ON_ORDER * 2);
		} else if (wanted == Items.DECORATED_POT) {
			craft = new Craft(List.of(of(Items.BRICK, 4)), new ItemStack(Items.DECORATED_POT), ON_ORDER);
		} else if (wanted == Items.FLOWER_POT) {
			craft = new Craft(List.of(of(Items.BRICK, 3)), new ItemStack(Items.FLOWER_POT), ON_ORDER);
		} else if (wanted == Items.QUARTZ_PILLAR) {
			craft = new Craft(List.of(of(Items.QUARTZ_BLOCK, 2)), new ItemStack(Items.QUARTZ_PILLAR, 2), ON_ORDER);
		} else if (wanted == Items.BRICKS) {
			craft = new Craft(List.of(of(Items.CLAY_BALL, 4)), new ItemStack(Items.BRICKS), ON_ORDER * 4);
		}
		return java.util.Optional.ofNullable(craft);
	}

	/** Le tanneur : du cuir (des peaux de lapin), et des tuniques de cuir pour les gardes qui n'ont rien. */
	static final List<Craft> TANNER = List.of(
			new Craft(List.of(of(Items.RABBIT_HIDE, 4)), new ItemStack(Items.LEATHER), 32),
			new Craft(List.of(of(Items.LEATHER, 8)), new ItemStack(Items.LEATHER_CHESTPLATE), 2));

	/** Le bibliothécaire : des livres, pour la bibliothèque et l'école. */
	static final List<Craft> LIBRARIAN = List.of(
			new Craft(List.of(of(Items.PAPER, 3), of(Items.LEATHER, 1)), new ItemStack(Items.BOOK), 16),
			new Craft(List.of(of(Items.SUGAR_CANE, 3)), new ItemStack(Items.PAPER, 3), 24));
}
