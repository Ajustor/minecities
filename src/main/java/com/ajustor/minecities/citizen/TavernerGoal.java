package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Le tavernier, au comptoir de taverne (MC-58). Il va chercher à boire aux réserves (du miel, des
 * baies, des pommes ; le lait reste au médecin), puis sert au comptoir : chaque service ouvre la
 * taverne pour la journée, et les citoyens s'en réjouissent. La taverne attire aussi de nouveaux
 * habitants (voir {@link com.ajustor.minecities.town.Families}).
 */
class TavernerGoal extends WorkGoal {
	/** Ce qui se sert à la taverne. */
	static final Predicate<ItemStack> DRINK = s -> s.is(Items.HONEY_BOTTLE) || s.is(Items.SWEET_BERRIES)
			|| s.is(Items.GLOW_BERRIES) || s.is(Items.APPLE);
	/** Un service dure dix secondes ; il en emporte de quoi en faire quelques-uns. */
	private static final int SERVE_TIME = 200;
	private static final int CARRY = 4;

	private int timer;

	TavernerGoal(CitizenEntity citizen) {
		super(citizen, Job.TAVERNER);
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		if (citizen.inventory().stream().noneMatch(DRINK)) {
			fetch();
		} else {
			serve();
		}
	}

	/** Aux réserves : de quoi servir. */
	private void fetch() {
		Optional<BlockPos> source = find(supplies(), DRINK);
		if (source.isEmpty()) {
			citizen.need("drinks", net.minecraft.network.chat.Component.translatable("speech.minecities.drinks"));
			rest(IDLE_LONG / 2);
			return;
		}
		if (!goTo(source.get())) {
			return;
		}
		citizen.satisfied("drinks");
		List<Container> pool = pool(supplies());
		ItemStack taken = take(pool, DRINK, CARRY);
		if (!taken.isEmpty()) {
			citizen.carry(taken);
		}
		timer = 0;
	}

	/** Au comptoir : il sert, et la taverne est ouverte pour la journée. */
	private void serve() {
		BlockPos station = employment().station();
		if (!goTo(station)) {
			return;
		}
		citizen.act(Action.TALK, 10);
		if (++timer < SERVE_TIME) {
			if (timer % 40 == 0) {
				level().sendParticles(ParticleTypes.HAPPY_VILLAGER, station.getX() + 0.5, station.getY() + 1.2,
						station.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.02);
			}
			return;
		}
		timer = 0;
		for (ItemStack stack : citizen.inventory()) {
			if (DRINK.test(stack)) {
				stack.shrink(1);
				break;
			}
		}
		level().playSound(null, station, SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.6f, 1.0f);
		town().chronicle().served(level().getGameTime());
		rest(600);
	}
}
