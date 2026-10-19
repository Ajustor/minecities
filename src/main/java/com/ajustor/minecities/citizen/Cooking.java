package com.ajustor.minecities.citizen;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les recettes : ce que le fumoir cuit, et les plats qu'on prépare avec ce que le grenier offre. Le
 * cuisinier fait tout tant qu'il n'y a pas de boulanger ; avec un boulanger, il lui laisse le pain,
 * les biscuits et les tartes (et garde viandes, poissons, soupes et ragoûts). Les plats passent avant
 * la simple cuisson : ils rendent les citoyens plus heureux (brief 04).
 */
final class Cooking {
	static final int WHEAT_PER_BREAD = 3;

	private static final Map<Item, Item> COOKED = Map.of(
			Items.BEEF, Items.COOKED_BEEF,
			Items.PORKCHOP, Items.COOKED_PORKCHOP,
			Items.CHICKEN, Items.COOKED_CHICKEN,
			Items.MUTTON, Items.COOKED_MUTTON,
			Items.RABBIT, Items.COOKED_RABBIT,
			Items.COD, Items.COOKED_COD,
			Items.SALMON, Items.COOKED_SALMON,
			Items.POTATO, Items.BAKED_POTATO,
			Items.KELP, Items.DRIED_KELP);

	/** Un plat : ses ingrédients, ce qu'il donne, et s'il sort du four du boulanger. */
	record Recipe(Map<Item, Integer> ingredients, Item dish, int count, boolean bakery, boolean master) {
		Recipe(Map<Item, Integer> ingredients, Item dish, int count, boolean bakery) {
			this(ingredients, dish, count, bakery, false);
		}
	}

	/** Les plats, du plus recherché au plus simple. */
	static final List<Recipe> RECIPES = List.of(
			// Le gâteau, du seul maître cuisinier (MC-63).
			new Recipe(ordered(Items.MILK_BUCKET, 3, Items.SUGAR, 2, Items.EGG, 1, Items.WHEAT, 3), Items.CAKE, 1,
					false, true),
			new Recipe(ordered(Items.RABBIT, 1, Items.CARROT, 1, Items.POTATO, 1, Items.BROWN_MUSHROOM, 1, Items.BOWL, 1),
					Items.RABBIT_STEW, 1, false),
			new Recipe(ordered(Items.PUMPKIN, 1, Items.SUGAR, 1, Items.EGG, 1), Items.PUMPKIN_PIE, 1, true),
			// Sans sucre, il le fait de la canne que rapporte le fermier.
			new Recipe(ordered(Items.PUMPKIN, 1, Items.SUGAR_CANE, 1, Items.EGG, 1), Items.PUMPKIN_PIE, 1, true),
			new Recipe(ordered(Items.BROWN_MUSHROOM, 1, Items.RED_MUSHROOM, 1, Items.BOWL, 1), Items.MUSHROOM_STEW, 1,
					false),
			new Recipe(ordered(Items.BEETROOT, 6, Items.BOWL, 1), Items.BEETROOT_SOUP, 1, false),
			new Recipe(ordered(Items.WHEAT, 2, Items.COCOA_BEANS, 1), Items.COOKIE, 8, true),
			new Recipe(ordered(Items.WHEAT, WHEAT_PER_BREAD), Items.BREAD, 1, true));

	/** Le cuisinier seul : il fait tout. */
	static final Cooking COOK = new Cooking(true, true, true);
	/** Le maître cuisinier : de même, et le gâteau. */
	static final Cooking MASTER_COOK = new Cooking(true, true, true, true);
	static final Cooking MASTER_COOK_WITH_BAKER = new Cooking(true, true, false, true);
	/** Le cuisinier quand la ville a un boulanger : viandes, poissons, soupes et ragoûts. */
	static final Cooking COOK_WITH_BAKER = new Cooking(true, true, false);
	/** Le boulanger : pain, biscuits, tartes. */
	static final Cooking BAKER = new Cooking(false, false, true);

	private final boolean smoking;
	private final boolean kitchen;
	private final boolean bakery;
	private final boolean master;

	private Cooking(boolean smoking, boolean kitchen, boolean bakery) {
		this(smoking, kitchen, bakery, false);
	}

	private Cooking(boolean smoking, boolean kitchen, boolean bakery, boolean master) {
		this.smoking = smoking;
		this.kitchen = kitchen;
		this.bakery = bakery;
		this.master = master;
	}

	private static Map<Item, Integer> ordered(Object... pairs) {
		Map<Item, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((Item) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	private boolean makes(Recipe recipe) {
		return (!recipe.master() || master) && (recipe.bakery() ? bakery : kitchen);
	}

	/** Ce qu'on emporte du grenier : ce qui se cuit, et les ingrédients de ses plats. */
	boolean isIngredient(ItemStack stack) {
		return smoking && COOKED.containsKey(stack.getItem())
				|| RECIPES.stream().anyMatch(r -> makes(r) && r.ingredients().containsKey(stack.getItem()));
	}

	private static int count(List<ItemStack> inventory, Item item) {
		return inventory.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	private static boolean has(List<ItemStack> inventory, Recipe recipe) {
		return recipe.ingredients().entrySet().stream().allMatch(e -> count(inventory, e.getKey()) >= e.getValue());
	}

	boolean canCook(List<ItemStack> inventory) {
		return smoking && inventory.stream().anyMatch(s -> COOKED.containsKey(s.getItem()) && !s.isEmpty())
				|| RECIPES.stream().anyMatch(r -> makes(r) && has(inventory, r));
	}

	/**
	 * Prépare un plat, ou à défaut cuit une portion, avec ce qu'on a dans les poches ; vide s'il n'y
	 * a plus rien à faire.
	 */
	ItemStack cookOne(List<ItemStack> inventory) {
		for (Recipe recipe : RECIPES) {
			if (makes(recipe) && has(inventory, recipe)) {
				recipe.ingredients().forEach((item, needed) -> {
					int left = needed;
					for (ItemStack stack : inventory) {
						if (left > 0 && stack.is(item)) {
							int used = Math.min(left, stack.getCount());
							stack.shrink(used);
							left -= used;
						}
					}
				});
				return new ItemStack(recipe.dish(), recipe.count());
			}
		}
		if (smoking) {
			for (ItemStack stack : inventory) {
				Item cooked = COOKED.get(stack.getItem());
				if (cooked != null && !stack.isEmpty()) {
					stack.shrink(1);
					return new ItemStack(cooked);
				}
			}
		}
		return ItemStack.EMPTY;
	}
}
