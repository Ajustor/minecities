package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Le cuisinier, au fumoir. Il prend au grenier ce qui est cru (viandes, poissons, pommes de terre)
 * et le blé, le cuit au fumoir (trois blés font un pain), puis rapporte le tout au grenier : la
 * même récolte nourrit mieux.
 */
class CookGoal extends WorkGoal {
	/** Il emporte jusqu'à tant d'ingrédients par fournée. */
	private static final int BATCH = 16;
	/** Une cuisson toutes les deux secondes. */
	private static final int COOK_TIME = 40;

	private enum Phase { FETCH, COOK, DELIVER }

	private Phase phase = Phase.FETCH;
	private int timer;

	CookGoal(CitizenEntity citizen) {
		super(citizen, Job.COOK);
	}

	@Override
	public void start() {
		super.start();
		phase = Cooking.canCook(citizen.inventory()) ? Phase.COOK
				: citizen.inventory().stream().anyMatch(s -> !s.isEmpty()) ? Phase.DELIVER : Phase.FETCH;
	}

	@Override
	protected void onStop() {
		light(false);
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		switch (phase) {
			case FETCH -> fetch();
			case COOK -> cook();
			case DELIVER -> deliver();
		}
	}

	private void fetch() {
		BlockPos granary = town().hall();
		if (!goTo(granary)) {
			return;
		}
		container(granary).ifPresent(c -> {
			for (int i = 0; i < 4 && !citizen.inventoryFull(); i++) {
				ItemStack taken = Stock.take(c, Cooking::isIngredient, BATCH);
				if (taken.isEmpty()) {
					break;
				}
				citizen.carry(taken);
			}
		});
		if (Cooking.canCook(citizen.inventory())) {
			phase = Phase.COOK;
		} else {
			// Rien à cuire (ou du blé en trop petite quantité) : on rapporte ce qu'on a, et on attend.
			phase = Phase.DELIVER;
			if (citizen.inventory().stream().allMatch(ItemStack::isEmpty)) {
				phase = Phase.FETCH;
				rest(IDLE_LONG);
			}
		}
	}

	private void cook() {
		BlockPos station = employment().station();
		if (!goTo(station)) {
			return;
		}
		light(true);
		if (++timer < COOK_TIME) {
			if (timer % 10 == 0) {
				level().sendParticles(ParticleTypes.SMOKE, station.getX() + 0.5, station.getY() + 1.1,
						station.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.01);
			}
			return;
		}
		timer = 0;
		ItemStack dish = Cooking.cookOne(citizen.inventory());
		if (dish.isEmpty()) {
			light(false);
			phase = Phase.DELIVER;
			return;
		}
		citizen.swing(InteractionHand.MAIN_HAND);
		level().playSound(null, station, SoundEvents.SMOKER_SMOKE, SoundSource.BLOCKS, 1.0f, 1.0f);
		citizen.carry(dish);
	}

	private void deliver() {
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
		phase = Phase.FETCH;
	}

	/** Allume ou éteint le fumoir pendant qu'il cuisine. */
	private void light(boolean lit) {
		if (citizen.job().isEmpty() || !(citizen.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}
		BlockPos station = employment().station();
		BlockState state = level.getBlockState(station);
		if (state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT) != lit) {
			level.setBlock(station, state.setValue(AbstractFurnaceBlock.LIT, lit), Block.UPDATE_ALL);
		}
	}
}
