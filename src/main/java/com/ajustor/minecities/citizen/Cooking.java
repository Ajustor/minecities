package com.ajustor.minecities.citizen;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les recettes du cuisinier : ce que le fumoir cuit, et les plats qu'il prépare avec ce que le
 * grenier lui offre (pain, soupes, ragoûts, tarte, biscuits). Les plats passent avant la simple
 * cuisson : ils rendent les citoyens plus heureux (brief 04).
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

	/** Un plat : ses ingrédients, et ce qu'il donne. */
	record Recipe(Map<Item, Integer> ingredients, Item dish, int count) {
	}

	/** Les plats, du plus recherché au plus simple. */
	static final List<Recipe> RECIPES = List.of(
			new Recipe(ordered(Items.RABBIT, 1, Items.CARROT, 1, Items.POTATO, 1, Items.BROWN_MUSHROOM, 1, Items.BOWL, 1),
					Items.RABBIT_STEW, 1),
			new Recipe(ordered(Items.PUMPKIN, 1, Items.SUGAR, 1, Items.EGG, 1), Items.PUMPKIN_PIE, 1),
			new Recipe(ordered(Items.BROWN_MUSHROOM, 1, Items.RED_MUSHROOM, 1, Items.BOWL, 1), Items.MUSHROOM_STEW, 1),
			new Recipe(ordered(Items.BEETROOT, 6, Items.BOWL, 1), Items.BEETROOT_SOUP, 1),
			new Recipe(ordered(Items.WHEAT, 2, Items.COCOA_BEANS, 1), Items.COOKIE, 8),
			new Recipe(ordered(Items.WHEAT, WHEAT_PER_BREAD), Items.BREAD, 1));

	private Cooking() {
	}

	private static Map<Item, Integer> ordered(Object... pairs) {
		Map<Item, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((Item) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	/** Ce que le cuisinier emporte du grenier : ce qui se cuit, et les ingrédients de ses plats. */
	static boolean isIngredient(ItemStack stack) {
		return COOKED.containsKey(stack.getItem())
				|| RECIPES.stream().anyMatch(r -> r.ingredients().containsKey(stack.getItem()));
	}

	private static int count(List<ItemStack> inventory, Item item) {
		return inventory.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	private static boolean has(List<ItemStack> inventory, Recipe recipe) {
		return recipe.ingredients().entrySet().stream().allMatch(e -> count(inventory, e.getKey()) >= e.getValue());
	}

	static boolean canCook(List<ItemStack> inventory) {
		return inventory.stream().anyMatch(s -> COOKED.containsKey(s.getItem()) && !s.isEmpty())
				|| RECIPES.stream().anyMatch(r -> has(inventory, r));
	}

	/**
	 * Prépare un plat, ou à défaut cuit une portion, avec ce qu'il a dans les poches ; vide s'il n'y
	 * a plus rien à faire.
	 */
	static ItemStack cookOne(List<ItemStack> inventory) {
		for (Recipe recipe : RECIPES) {
			if (has(inventory, recipe)) {
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
		for (ItemStack stack : inventory) {
			Item cooked = COOKED.get(stack.getItem());
			if (cooked != null && !stack.isEmpty()) {
				stack.shrink(1);
				return new ItemStack(cooked);
			}
		}
		return ItemStack.EMPTY;
	}
}
