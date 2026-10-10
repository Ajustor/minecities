package com.ajustor.minecities.town;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/** Prendre et ranger dans les coffres, le grenier et l'inventaire des citoyens. */
public final class Stock {
	private Stock() {
	}

	/** Compte les objets qui conviennent. */
	public static int count(Container container, Predicate<ItemStack> filter) {
		int count = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && filter.test(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Prend jusqu'à {@code max} objets qui conviennent, d'une seule sorte (celle trouvée en premier). */
	public static ItemStack take(Container container, Predicate<ItemStack> filter, int max) {
		ItemStack taken = ItemStack.EMPTY;
		for (int i = 0; i < container.getContainerSize() && taken.getCount() < max; i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty() || !filter.test(stack)
					|| !taken.isEmpty() && !ItemStack.isSameItemSameComponents(taken, stack)) {
				continue;
			}
			ItemStack part = container.removeItem(i, max - taken.getCount());
			if (taken.isEmpty()) {
				taken = part;
			} else {
				taken.grow(part.getCount());
			}
		}
		if (!taken.isEmpty()) {
			container.setChanged();
		}
		return taken;
	}

	/** Range un objet ; renvoie ce qui n'a pas trouvé de place. */
	public static ItemStack insert(Container container, ItemStack stack) {
		ItemStack rest = stack.copy();
		for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
			for (int i = 0; i < container.getContainerSize() && !rest.isEmpty(); i++) {
				ItemStack slot = container.getItem(i);
				if (pass == 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, rest)) {
					int moved = Math.min(rest.getCount(), Math.min(slot.getMaxStackSize(), container.getMaxStackSize())
							- slot.getCount());
					if (moved > 0) {
						slot.grow(moved);
						rest.shrink(moved);
					}
				} else if (pass == 1 && slot.isEmpty() && container.canPlaceItem(i, rest)) {
					container.setItem(i, rest.split(Math.min(rest.getCount(), container.getMaxStackSize())));
				}
			}
		}
		if (rest.getCount() != stack.getCount()) {
			container.setChanged();
		}
		return rest;
	}

	/** Range un objet dans une liste de cases (l'inventaire d'un citoyen) ; renvoie le reste. */
	public static ItemStack insert(List<ItemStack> slots, ItemStack stack) {
		ItemStack rest = stack.copy();
		for (int i = 0; i < slots.size() && !rest.isEmpty(); i++) {
			ItemStack slot = slots.get(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, rest)) {
				int moved = Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount());
				slot.grow(moved);
				rest.shrink(moved);
			}
		}
		for (int i = 0; i < slots.size() && !rest.isEmpty(); i++) {
			if (slots.get(i).isEmpty()) {
				slots.set(i, rest.copy());
				rest = ItemStack.EMPTY;
			}
		}
		return rest;
	}
}
