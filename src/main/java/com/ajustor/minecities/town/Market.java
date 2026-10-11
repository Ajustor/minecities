package com.ajustor.minecities.town;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Le marché (brief 07) : l'émeraude est la monnaie, et le trésor de la ville est ce que le grenier en
 * contient. Une économie fermée, sans émeraudes tombées du ciel : le marchand vend le surplus de la
 * production au cours des villageois (tant de pièces pour une émeraude), et achète ce qui manque
 * (les outils des métiers, des flèches, du pain) au prix des villageois.
 */
public final class Market {
	/** Le grenier garde toujours tant de chaque marchandise : seul le reste se vend. */
	public static final int RESERVE = 32;
	/** Un marchand ne gagne pas plus de tant d'émeraudes par jour. */
	public static final int DAILY_EARNINGS = 32;

	/** Ce qui se vend, et combien il en faut pour une émeraude (les cours des villageois). */
	private static final Map<Item, Integer> SELL = new LinkedHashMap<>();
	/** Ce qui s'achète : combien d'émeraudes, pour combien de pièces. */
	private static final Map<Item, Offer> BUY = new LinkedHashMap<>();

	/** Une offre d'achat : {@code count} pièces pour {@code price} émeraudes. */
	public record Offer(Item item, int count, int price) {
		public ItemStack stack() {
			return new ItemStack(item, count);
		}
	}

	static {
		SELL.put(Items.WHEAT, 20);
		SELL.put(Items.POTATO, 26);
		SELL.put(Items.CARROT, 22);
		SELL.put(Items.BEETROOT, 15);
		SELL.put(Items.PUMPKIN, 6);
		SELL.put(Items.MELON, 4);
		SELL.put(Items.APPLE, 16);
		SELL.put(Items.COD, 15);
		SELL.put(Items.SALMON, 13);
		SELL.put(Items.BEEF, 14);
		SELL.put(Items.PORKCHOP, 14);
		SELL.put(Items.CHICKEN, 14);
		SELL.put(Items.MUTTON, 14);
		SELL.put(Items.RABBIT, 10);
		SELL.put(Items.LEATHER, 6);
		SELL.put(Items.FEATHER, 24);
		SELL.put(Items.EGG, 16);
		SELL.put(Items.WOOL.white(), 18);
		SELL.put(Items.STRING, 14);
		SELL.put(Items.ROTTEN_FLESH, 32);
		SELL.put(Items.STICK, 32);
		SELL.put(Items.OAK_LOG, 16);
		SELL.put(Items.SPRUCE_LOG, 16);
		SELL.put(Items.BIRCH_LOG, 16);
		SELL.put(Items.JUNGLE_LOG, 16);
		SELL.put(Items.ACACIA_LOG, 16);
		SELL.put(Items.DARK_OAK_LOG, 16);
		SELL.put(Items.CHERRY_LOG, 16);
		SELL.put(Items.MANGROVE_LOG, 16);
		SELL.put(Items.COAL, 15);
		SELL.put(Items.IRON_INGOT, 4);

		for (Offer offer : new Offer[]{
				new Offer(Items.STONE_HOE, 1, 1),
				new Offer(Items.STONE_AXE, 1, 1),
				new Offer(Items.STONE_PICKAXE, 1, 1),
				new Offer(Items.STONE_SHOVEL, 1, 1),
				new Offer(Items.STONE_SWORD, 1, 1),
				new Offer(Items.FISHING_ROD, 1, 2),
				new Offer(Items.BOW, 1, 2),
				new Offer(Items.ARROW, 16, 1),
				new Offer(Items.BREAD, 6, 1)}) {
			BUY.put(offer.item(), offer);
		}
	}

	private Market() {
	}

	/** Combien il faut de cette marchandise pour une émeraude ; vide si elle ne se vend pas. */
	public static Optional<Integer> rate(Item item) {
		return Optional.ofNullable(SELL.get(item));
	}

	public static boolean sells(ItemStack stack) {
		return SELL.containsKey(stack.getItem());
	}

	/** Ce qu'on peut vendre d'un stock, sans entamer la réserve : un multiple du cours. */
	public static int saleable(int stock, int rate) {
		int surplus = Math.max(0, stock - RESERVE);
		return surplus / rate * rate;
	}

	/** Les émeraudes que rapportent tant de pièces (au cours, arrondi à l'émeraude inférieure). */
	public static int emeralds(int count, int rate) {
		return count / rate;
	}

	/** L'offre pour acheter cet objet ; vide s'il ne s'achète pas. */
	public static Optional<Offer> offer(Item item) {
		return Optional.ofNullable(BUY.get(item));
	}

	/** Ce qu'on achète pour l'outil d'un métier, s'il en faut un. */
	public static Optional<Offer> toolFor(Job job) {
		return switch (job) {
			case FARMER -> offer(Items.STONE_HOE);
			case LUMBERJACK -> offer(Items.STONE_AXE);
			case FISHERMAN -> offer(Items.FISHING_ROD);
			case GUARD -> offer(Items.STONE_SWORD);
			case ARCHER -> offer(Items.BOW);
			default -> Optional.empty();
		};
	}

	/** Ce qu'on achète pour l'outil que réclame un bâtisseur ({@code pickaxe}, {@code shovel}, {@code axe}). */
	public static Optional<Offer> builderTool(String name) {
		return switch (name) {
			case "pickaxe" -> offer(Items.STONE_PICKAXE);
			case "shovel" -> offer(Items.STONE_SHOVEL);
			case "axe" -> offer(Items.STONE_AXE);
			default -> Optional.empty();
		};
	}
}
