package com.ajustor.minecities.town;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Les réserves de la ville (brief 12) : le grenier de l'hôtel de ville pour la nourriture, l'entrepôt
 * (les coffres du bâtiment qui a un registre d'entrepôt) pour tout le reste. Sans entrepôt, tout va au
 * grenier.
 */
public final class Storage {
	private Storage() {
	}

	/** Toutes les réserves communes : l'entrepôt d'abord, puis le grenier. */
	public static List<BlockPos> stores(Town town) {
		List<BlockPos> stores = new ArrayList<>(town.warehouseContainers());
		stores.add(town.hall());
		return stores;
	}

	/** Cette marchandise va-t-elle au grenier (la nourriture, et les émeraudes du trésor) plutôt qu'à l'entrepôt ? */
	public static boolean granaryGoods(ItemStack stack) {
		return TownService.isFood(stack) || stack.is(net.minecraft.world.item.Items.EMERALD);
	}

	/**
	 * Où déposer cette marchandise : la nourriture au grenier ; le reste dans un coffre de l'entrepôt
	 * qui a de la place (ou en a déjà), sinon au grenier.
	 */
	public static BlockPos depot(ServerLevel level, Town town, ItemStack stack) {
		if (!granaryGoods(stack)) {
			BlockPos fallback = null;
			for (BlockPos pos : town.warehouseContainers()) {
				if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container c && room(c, stack)) {
					if (Stock.count(c, s -> ItemStack.isSameItemSameComponents(s, stack)) > 0) {
						return pos;
					}
					if (fallback == null) {
						fallback = pos;
					}
				}
			}
			if (fallback != null) {
				return fallback;
			}
		}
		return town.hall();
	}

	/** Ce contenant peut-il recevoir au moins une pièce de cette marchandise ? */
	public static boolean room(Container c, ItemStack stack) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack slot = c.getItem(i);
			if (slot.isEmpty() || ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
				return true;
			}
		}
		return false;
	}

	/** Combien la ville en a, dans toutes ses réserves chargées. */
	public static int count(ServerLevel level, Town town, Predicate<ItemStack> filter) {
		int total = 0;
		for (BlockPos pos : stores(town)) {
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container c) {
				total += Stock.count(c, filter);
			}
		}
		return total;
	}

	/** La première réserve qui en a. */
	public static Optional<BlockPos> find(ServerLevel level, Town town, Predicate<ItemStack> filter) {
		return stores(town).stream().filter(pos -> level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container c
				&& Stock.count(c, filter) > 0).findFirst();
	}
}
