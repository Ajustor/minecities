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
		Optional<Market.Offer> wanted = wanted(c);
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
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack stack = c.getItem(i);
				Optional<Integer> value = Market.value(stack.getItem());
				if (!stack.isEmpty() && value.isPresent()) {
					Item item = stack.getItem();
					int pieces = Math.max(1, Math.min(stack.getCount(),
							(cap - town().chronicle().earnedToday(now)) / value.get()));
					ItemStack taken = Stock.take(c, s -> s.is(item), pieces);
					buying = null;
					selling = item;
					sellingCount = taken.getCount();
					citizen.carry(taken);
					phase = Phase.CARRY_TO_STALL;
					return;
				}
			}
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack stack = c.getItem(i);
				Optional<Integer> rate = Market.rate(stack.getItem());
				if (stack.isEmpty() || rate.isEmpty()) {
					continue;
				}
				Item item = stack.getItem();
				int saleable = Math.min(Market.saleable(Stock.count(c, s -> s.is(item)), rate.get()),
						rate.get() * Math.max(1, Math.min(4, (cap - town().chronicle().earnedToday(now)))));
				if (saleable <= 0) {
					continue;
				}
				ItemStack taken = Stock.take(c, s -> s.is(item), saleable);
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
	private Optional<Market.Offer> wanted(Container granary) {
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
				if (Stock.count(granary, s -> s.is(item)) == 0) {
					return offer;
				}
			}
		}
		if (!TownService.hasFood(granary)) {
			return Market.offer(Items.BREAD);
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
		// L'affaire est faite : ce qu'il portait contre ce qu'il rapporte.
		citizen.inventory().clear();
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
		phase = Phase.DECIDE;
		rest(200);
	}
}
