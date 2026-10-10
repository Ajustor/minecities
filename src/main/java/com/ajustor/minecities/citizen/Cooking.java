package com.ajustor.minecities.citizen;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

/** Les recettes du cuisinier : ce que le fumoir cuit, et le pain (trois blés). */
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

	private Cooking() {
	}

	static boolean isIngredient(ItemStack stack) {
		return COOKED.containsKey(stack.getItem()) || stack.is(Items.WHEAT);
	}

	static boolean canCook(List<ItemStack> inventory) {
		int wheat = 0;
		for (ItemStack stack : inventory) {
			if (COOKED.containsKey(stack.getItem())) {
				return true;
			}
			if (stack.is(Items.WHEAT)) {
				wheat += stack.getCount();
			}
		}
		return wheat >= WHEAT_PER_BREAD;
	}

	/** Cuit une portion prise dans l'inventaire ; vide s'il n'y a plus rien à cuire. */
	static ItemStack cookOne(List<ItemStack> inventory) {
		for (ItemStack stack : inventory) {
			Item cooked = COOKED.get(stack.getItem());
			if (cooked != null && !stack.isEmpty()) {
				stack.shrink(1);
				return new ItemStack(cooked);
			}
		}
		int wheat = 0;
		for (ItemStack stack : inventory) {
			if (stack.is(Items.WHEAT)) {
				wheat += stack.getCount();
			}
		}
		if (wheat < WHEAT_PER_BREAD) {
			return ItemStack.EMPTY;
		}
		int left = WHEAT_PER_BREAD;
		for (ItemStack stack : inventory) {
			if (stack.is(Items.WHEAT) && left > 0) {
				int used = Math.min(left, stack.getCount());
				stack.shrink(used);
				left -= used;
			}
		}
		return new ItemStack(Items.BREAD);
	}
}
