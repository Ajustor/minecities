package com.ajustor.minecities.town;

import com.ajustor.minecities.block.TownHallBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Le commerce entre villes (brief 07). Deux maires qui signent un accord (chacun de son côté) ouvrent
 * une route : une fois par jour au plus dans chaque sens, une caravane part du marché de la ville
 * qui a trop de marchandises vers celle qui en manque ; la seconde paie la première en émeraudes, au
 * cours des villageois, dès le départ. La caravane fait ensuite le trajet ({@link
 * com.ajustor.minecities.caravan.Caravans}). Il faut un marchand au départ, et les deux greniers chargés.
 */
public final class Trade {
	/** On regarde chaque minute si une caravane peut partir (une par jour et par sens, voir Caravans). */
	private static final int INTERVAL = 1200;
	/** Au-delà de tant, une ville a de quoi vendre ; en dessous de tant, elle manque. */
	static final int PLENTY = 64;
	static final int SHORT = 16;
	/** Une caravane porte tant au plus. */
	static final int LOAD = 32;
	/** La nourriture se vend tant de portions par émeraude. */
	static final int FOOD_RATE = 8;

	private static int ticks;

	private Trade() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Trade::tick);
	}

	/** Deux villes commercent quand chacune a signé avec l'autre. */
	public static boolean trading(Town a, Town b) {
		return a.partners().contains(b.id()) && b.partners().contains(a.id());
	}

	/** Le prix, en émeraudes, de tant de pièces de cette marchandise (au moins une émeraude). */
	public static int price(Item item, int count) {
		int rate = Market.rate(item).orElse(FOOD_RATE);
		return Math.max(1, count / rate);
	}

	/** Ce qu'une caravane emporte : de quoi combler le manque, sans entamer la réserve du vendeur. */
	public static int load(int sellerStock, int buyerStock) {
		if (sellerStock <= PLENTY || buyerStock >= SHORT) {
			return 0;
		}
		return Math.min(LOAD, sellerStock - PLENTY);
	}

	private static void tick(MinecraftServer server) {
		if (++ticks % INTERVAL != 0) {
			return;
		}
		Towns towns = Towns.get(server);
		Set<String> done = new LinkedHashSet<>();
		for (Town a : towns.all()) {
			for (java.util.UUID id : Set.copyOf(a.partners())) {
				Optional<Town> b = towns.byId(id);
				if (b.isEmpty()) {
					a.removePartner(id);
					continue;
				}
				String key = a.id().compareTo(id) < 0 ? a.id() + "|" + id : id + "|" + a.id();
				if (!trading(a, b.get()) || !done.add(key)) {
					continue;
				}
				ServerLevel la = server.getLevel(a.dimension());
				ServerLevel lb = server.getLevel(b.get().dimension());
				if (la == null || lb == null) {
					continue;
				}
				Optional<TownHallBlockEntity> ga = TownService.granary(la, a);
				Optional<TownHallBlockEntity> gb = TownService.granary(lb, b.get());
				if (ga.isEmpty() || gb.isEmpty()) {
					continue;
				}
				caravan(server, a, ga.get(), b.get(), gb.get());
				caravan(server, b.get(), gb.get(), a, ga.get());
			}
		}
	}

	/**
	 * Une caravane du vendeur vers l'acheteur (MC-80), une par jour et par sens : elle charge ce dont
	 * l'acheteur manque et que le vendeur a en trop, l'acheteur la paie au départ, et elle prend la
	 * route ; sa cargaison entre au grenier de l'acheteur à l'arrivée. D'une dimension à l'autre, pas de
	 * route : la livraison est immédiate, comme avant.
	 */
	private static void caravan(MinecraftServer server, Town seller, TownHallBlockEntity from, Town buyer,
			TownHallBlockEntity to) {
		if (seller.workers(Job.MERCHANT) == 0) {
			return;
		}
		ServerLevel level = server.getLevel(seller.dimension());
		com.ajustor.minecities.caravan.Caravans caravans = com.ajustor.minecities.caravan.Caravans.get(server);
		boolean road = level != null && seller.dimension().equals(buyer.dimension());
		if (road && !caravans.mayDepart(seller, buyer, level.getGameTime())) {
			return;
		}
		java.util.List<ItemStack> cargo = new java.util.ArrayList<>();
		int paid = 0;
		for (int i = 0; i < from.getContainerSize() && cargo.size() < CARGO_KINDS; i++) {
			ItemStack stack = from.getItem(i);
			if (stack.isEmpty() || !(TownService.isFood(stack) || Market.rate(stack.getItem()).isPresent())
					|| cargo.stream().anyMatch(c -> c.is(stack.getItem()))) {
				continue;
			}
			Item item = stack.getItem();
			int count = load(Stock.count(from, s -> s.is(item)), Stock.count(to, s -> s.is(item)));
			if (count <= 0) {
				continue;
			}
			int price = price(item, count);
			if (Stock.count(to, s -> s.is(Items.EMERALD)) < price) {
				continue;
			}
			ItemStack goods = Stock.take(from, s -> s.is(item), count);
			if (goods.isEmpty()) {
				continue;
			}
			// L'acheteur paie au départ.
			Stock.take(to, s -> s.is(Items.EMERALD), price(item, goods.getCount()));
			paid += price(item, goods.getCount());
			cargo.add(goods);
		}
		java.util.List<ItemStack> wares = road ? wares(from, cargo) : java.util.List.of();
		if (cargo.isEmpty() && wares.isEmpty()) {
			return;
		}
		// Le grenier du vendeur plein : ce qui n'y entre pas retourne à l'acheteur, au lieu de disparaître.
		ItemStack unpaid = Stock.insert(from, new ItemStack(Items.EMERALD, paid));
		if (!unpaid.isEmpty()) {
			Stock.insert(to, unpaid);
			paid -= unpaid.getCount();
		}
		seller.chronicle().earn(server.overworld().getGameTime(), paid);
		if (road) {
			String merchant = seller.residents().values().stream().filter(r -> r.job()
					.filter(j -> j.job() == Job.MERCHANT).isPresent()).map(Resident::name).findFirst().orElse("");
			caravans.depart(level, seller, buyer, cargo, wares, merchant);
		} else {
			for (ItemStack goods : cargo) {
				ItemStack rest = Stock.insert(to, goods);
				if (!rest.isEmpty()) {
					Stock.insert(from, rest);
				}
			}
			Component message = Component.translatable("message.minecities.caravan.arrived", seller.name(), buyer.name(),
					com.ajustor.minecities.caravan.Caravans.describe(cargo));
			TownService.notify(server, seller, message);
			TownService.notify(server, buyer, message);
		}
		Towns.get(server).changed();
	}

	/** Combien de marchandises différentes une caravane emporte au plus. */
	private static final int CARGO_KINDS = 4;
	/** Ce que le marchand emporte pour son échoppe : tant de marchandises, tant de pièces de chacune. */
	private static final int WARE_KINDS = 3;
	private static final int WARE_LOAD = 16;
	/** Un bijou part à l'échoppe tant qu'il en reste ce nombre au grenier. */
	private static final int JEWELS_KEPT = 2;

	/**
	 * Ce que le marchand prend pour son échoppe (MC-80) : de ce que sa ville a en trop (nourriture,
	 * marchandises au cours des villageois, bijoux), autre chose que la cargaison, par lots entiers. Ce
	 * n'est pas vendu à l'avance : la recette et les invendus reviendront au grenier.
	 */
	static java.util.List<ItemStack> wares(TownHallBlockEntity from, java.util.List<ItemStack> cargo) {
		java.util.List<ItemStack> wares = new java.util.ArrayList<>();
		for (int i = 0; i < from.getContainerSize() && wares.size() < WARE_KINDS; i++) {
			ItemStack stack = from.getItem(i);
			if (stack.isEmpty() || cargo.stream().anyMatch(c -> c.is(stack.getItem()))
					|| wares.stream().anyMatch(w -> w.is(stack.getItem()))) {
				continue;
			}
			Item item = stack.getItem();
			boolean jewel = Market.value(item).isPresent();
			if (!jewel && !TownService.isFood(stack) && Market.rate(item).isEmpty()) {
				continue;
			}
			int kept = jewel ? JEWELS_KEPT : PLENTY;
			int lot = com.ajustor.minecities.caravan.Stall.lot(item).count();
			int count = Math.min(WARE_LOAD, Stock.count(from, s -> s.is(item)) - kept) / lot * lot;
			if (count <= 0) {
				continue;
			}
			ItemStack goods = Stock.take(from, s -> s.is(item), count);
			if (!goods.isEmpty()) {
				wares.add(goods);
			}
		}
		return wares;
	}
}
