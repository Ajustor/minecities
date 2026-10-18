package com.ajustor.minecities.town;

import com.ajustor.minecities.block.TownHallBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Le commerce entre villes (brief 07). Deux maires qui signent un accord (chacun de son côté) ouvrent
 * une route : une fois par minute, une caravane part du marché de la ville qui a trop d'une
 * marchandise vers celle qui en manque, et la seconde paie la première en émeraudes, au cours des
 * villageois. Il faut un marchand au départ, et les deux greniers chargés.
 */
public final class Trade {
	/** Une caravane par minute au plus et par route. */
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

	/** Une caravane du vendeur vers l'acheteur, s'il y a quelque chose à porter et de quoi payer. */
	private static void caravan(MinecraftServer server, Town seller, TownHallBlockEntity from, Town buyer,
			TownHallBlockEntity to) {
		if (seller.workers(Job.MERCHANT) == 0) {
			return;
		}
		for (int i = 0; i < from.getContainerSize(); i++) {
			ItemStack stack = from.getItem(i);
			if (stack.isEmpty() || !(TownService.isFood(stack) || Market.rate(stack.getItem()).isPresent())) {
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
			ItemStack rest = Stock.insert(to, goods);
			if (!rest.isEmpty()) {
				Stock.insert(from, rest);
			}
			int delivered = goods.getCount() - rest.getCount();
			if (delivered <= 0) {
				continue;
			}
			int paid = price(item, delivered);
			Stock.take(to, s -> s.is(Items.EMERALD), paid);
			// Le grenier du vendeur plein : ce qui n'y entre pas retourne à l'acheteur, au lieu de disparaître.
			ItemStack unpaid = Stock.insert(from, new ItemStack(Items.EMERALD, paid));
			if (!unpaid.isEmpty()) {
				Stock.insert(to, unpaid);
				paid -= unpaid.getCount();
			}
			seller.chronicle().earn(server.overworld().getGameTime(), paid);
			Component message = Component.translatable("message.minecities.trade.caravan", seller.name(), delivered,
					Component.translatable(item.getDescriptionId()), buyer.name(), paid);
			TownService.notify(server, seller, message);
			TownService.notify(server, buyer, message);
			ServerLevel level = server.getLevel(buyer.dimension());
			if (level != null) {
				level.playSound(null, buyer.hall(), SoundEvents.WANDERING_TRADER_YES, SoundSource.NEUTRAL, 1.0f, 1.0f);
			}
			Towns.get(server).changed();
			return;
		}
	}
}
