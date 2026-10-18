package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Market;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.TownService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * Le marchand, au pupitre du marché (brief 07). D'abord, il achète ce qui manque aux travailleurs :
 * l'outil qu'un citoyen réclame, des flèches pour l'archer, du pain si le grenier est vide ; il prend
 * les émeraudes au grenier, marchande au pupitre et rapporte la marchandise. Sinon, il vend le
 * surplus du grenier (ce qui dépasse la réserve) au cours des villageois, et rapporte les émeraudes.
 */
class MerchantGoal extends WorkGoal {
	/** On marchande trois secondes au pupitre. */
	private static final int HAGGLE = 60;

	private enum Phase { DECIDE, CARRY_TO_STALL, HAGGLE, CARRY_HOME }

	private Phase phase = Phase.DECIDE;
	/** Ce qu'il achète (vide s'il vend). */
	private Market.Offer buying;
	/** Ce qu'il vend, et combien. */
	private Item selling;
	private int sellingCount;
	private int timer;

	MerchantGoal(CitizenEntity citizen) {
		super(citizen, Job.MERCHANT);
	}

	@Override
	public void start() {
		super.start();
		// Ce qu'il porte encore (une affaire interrompue) retourne au grenier.
		phase = citizen.inventory().stream().allMatch(ItemStack::isEmpty) ? Phase.DECIDE : Phase.CARRY_HOME;
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		switch (phase) {
			case DECIDE -> decide();
			case CARRY_TO_STALL -> {
				if (goTo(employment().station())) {
					phase = Phase.HAGGLE;
					timer = 0;
				}
			}
			case HAGGLE -> haggle();
			case CARRY_HOME -> carryHome();
		}
	}

	/** Que faire : un achat pour un travailleur qui attend, sinon une vente. */
	private void decide() {
		BlockPos granary = town().hall();
		if (!goTo(granary)) {
			return;
		}
		Optional<Container> stock = container(granary);
		if (stock.isEmpty()) {
			rest(IDLE_SHORT);
			return;
		}
		Container c = stock.get();
		int treasury = Stock.count(c, s -> s.is(Items.EMERALD));
		// Les marchandises sont au grenier, à l'entrepôt ou au marché : il en tient les comptes.
		List<Container> goods = pool(supplies());
		Optional<Market.Offer> wanted = wanted(goods);
		if (wanted.isPresent() && treasury >= wanted.get().price()) {
			buying = wanted.get();
			selling = null;
			citizen.carry(Stock.take(c, s -> s.is(Items.EMERALD), buying.price()));
			phase = Phase.CARRY_TO_STALL;
			return;
		}
		long now = level().getGameTime();
		int cap = Market.dailyEarnings() * Math.max(1, town().workers(Job.MERCHANT));
		if (town().chronicle().earnedToday(now) < cap) {
			// Les bijoux d'abord, à la pièce : ils rapportent le plus.
			for (ItemStack stack : slots(goods)) {
				Optional<Integer> value = Market.value(stack.getItem());
				if (!stack.isEmpty() && value.isPresent()) {
					Item item = stack.getItem();
					int pieces = Math.max(1, Math.min(stack.getCount(),
							(cap - town().chronicle().earnedToday(now)) / value.get()));
					ItemStack taken = take(goods, s -> s.is(item), pieces);
					buying = null;
					selling = item;
					sellingCount = taken.getCount();
					citizen.carry(taken);
					phase = Phase.CARRY_TO_STALL;
					return;
				}
			}
			for (ItemStack stack : slots(goods)) {
				Optional<Integer> rate = Market.rate(stack.getItem());
				if (stack.isEmpty() || rate.isEmpty()) {
					continue;
				}
				Item item = stack.getItem();
				int saleable = Math.min(Market.saleable(count(goods, s -> s.is(item)), rate.get()),
						rate.get() * Math.max(1, Math.min(4, (cap - town().chronicle().earnedToday(now)))));
				if (saleable <= 0) {
					continue;
				}
				ItemStack taken = take(goods, s -> s.is(item), saleable);
				buying = null;
				selling = item;
				sellingCount = taken.getCount();
				citizen.carry(taken);
				phase = Phase.CARRY_TO_STALL;
				return;
			}
		}
		// Rien à acheter qu'on puisse payer, rien à vendre : il attend au pupitre.
		if (wanted.isPresent()) {
			citizen.complain("treasury", Component.translatable("message.minecities.market.poor", citizen.getName(),
					Component.translatable(wanted.get().item().getDescriptionId()), wanted.get().price()));
		}
		rest(IDLE_LONG / 2);
	}

	/**
	 * Ce dont on a besoin, s'il ne se trouve pas déjà au grenier : l'outil d'un travailleur qui le
	 * réclame, des flèches, du pain quand il n'y a plus rien à manger.
	 */
	/** Toutes les cases de ces contenants (des copies : on ne fait que regarder). */
	private static List<ItemStack> slots(List<Container> pool) {
		List<ItemStack> all = new java.util.ArrayList<>();
		for (Container c : pool) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				all.add(c.getItem(i).copy());
			}
		}
		return all;
	}

	private Optional<Market.Offer> wanted(List<Container> granary) {
		BlockPos hall = town().hall();
		int r = town().radius();
		List<CitizenEntity> workers = level().getEntitiesOfClass(CitizenEntity.class, new AABB(hall).inflate(r, 48, r),
				w -> w.townId().filter(town().id()::equals).isPresent() && w.job().isPresent());
		for (CitizenEntity worker : workers) {
			Job job = worker.job().get().job();
			Optional<Market.Offer> offer = Optional.empty();
			if (worker.hasNeed("tool:")) {
				for (String name : new String[]{"pickaxe", "shovel", "axe"}) {
					if (worker.hasNeed("tool:" + name)) {
						offer = Market.builderTool(name);
						break;
					}
				}
			} else if (worker.hasNeed("tool")) {
				offer = Market.toolFor(job);
			} else if (worker.hasNeed("arrows")) {
				offer = Market.offer(Items.ARROW);
			}
			if (offer.isPresent()) {
				Item item = offer.get().item();
				if (count(granary, s -> s.is(item)) == 0) {
					return offer;
				}
			}
		}
		if (granary.stream().noneMatch(TownService::hasFood)) {
			return Market.offer(Items.BREAD);
		}
		// Ce qui manque aux chantiers, et qui s'achète.
		for (Item material : CitizenEntity.missingMaterials(level(), town())) {
			Optional<Market.Offer> offer = Market.offer(material);
			if (offer.isPresent() && count(granary, s -> s.is(material)) == 0) {
				return offer;
			}
		}
		return Optional.empty();
	}

	private void haggle() {
		BlockPos stall = employment().station();
		citizen.getLookControl().setLookAt(stall.getX() + 0.5, stall.getY() + 1, stall.getZ() + 0.5);
		citizen.act(Action.TALK, 10);
		if (++timer < HAGGLE) {
			return;
		}
		// L'affaire est faite : ce qu'il a apporté pour elle contre ce qu'il rapporte. Le reste de ses poches
		// (un achat pas encore rangé, le grenier plein) ne part pas avec.
		if (buying != null) {
			spend(s -> s.is(Items.EMERALD), buying.price());
		} else if (selling != null) {
			Item sold = selling;
			spend(s -> s.is(sold), sellingCount);
		}
		if (buying != null) {
			citizen.carry(buying.stack());
			citizen.say("bought", Component.translatable("speech.minecities.bought",
					Component.translatable(buying.item().getDescriptionId())));
		} else if (selling != null) {
			int emeralds = Market.value(selling).map(v -> v * sellingCount)
					.orElseGet(() -> Market.emeralds(sellingCount, Market.rate(selling).orElse(sellingCount + 1)));
			citizen.carry(new ItemStack(Items.EMERALD, emeralds));
			town().chronicle().earn(level().getGameTime(), emeralds);
			citizen.say("sold", Component.translatable("speech.minecities.sold", sellingCount,
					Component.translatable(selling.getDescriptionId()), emeralds));
		}
		level().playSound(null, stall, SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.8f, 1.0f);
		level().sendParticles(ParticleTypes.HAPPY_VILLAGER, stall.getX() + 0.5, stall.getY() + 1.3, stall.getZ() + 0.5,
				6, 0.3, 0.2, 0.3, 0);
		phase = Phase.CARRY_HOME;
	}

	private void carryHome() {
		BlockPos granary = town().hall();
		if (!goTo(granary)) {
			return;
		}
		container(granary).ifPresent(c -> {
			List<ItemStack> inventory = citizen.inventory();
			for (int i = 0; i < inventory.size(); i++) {
				inventory.set(i, Stock.insert(c, inventory.get(i)));
			}
		});
		if (buying != null) {
			TownService.notify(level().getServer(), town(), Component.translatable("message.minecities.market.bought",
					citizen.getName(), buying.count(), Component.translatable(buying.item().getDescriptionId()),
					buying.price()));
		}
		buying = null;
		selling = null;
		// Le grenier plein : il garde le reste et y retournera, plutôt que de l'effacer à l'affaire suivante.
		phase = citizen.inventory().stream().allMatch(ItemStack::isEmpty) ? Phase.DECIDE : Phase.CARRY_HOME;
		rest(200);
	}

	/** Retire de ses poches tant de pièces de ce qui convient. */
	private void spend(java.util.function.Predicate<ItemStack> filter, int count) {
		int left = count;
		for (ItemStack stack : citizen.inventory()) {
			if (left > 0 && filter.test(stack)) {
				int used = Math.min(left, stack.getCount());
				stack.shrink(used);
				left -= used;
			}
		}
	}
}
