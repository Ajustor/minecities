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
import java.util.Optional;

/**
 * Le cuisinier, au fumoir, et le boulanger, au four. Le cuisinier prend au grenier ce qui est cru
 * (viandes, poissons, pommes de terre) et les ingrédients de ses plats (soupes, ragoûts, et pain,
 * biscuits, tartes tant qu'il n'y a pas de boulanger) ; le boulanger, le blé, le cacao, les citrouilles,
 * le sucre et les œufs. Ils cuisinent à leur poste, puis rapportent le tout au grenier : la même
 * récolte nourrit mieux, et plus varié.
 */
class CookGoal extends WorkGoal {
	/** Il emporte jusqu'à tant d'ingrédients par fournée. */
	private static final int BATCH = 16;
	/** Une cuisson toutes les deux secondes. */
	private static final int COOK_TIME = 40;

	private enum Phase { FETCH, COOK, DELIVER }

	private Phase phase = Phase.FETCH;
	private int timer;

	private final Job job;

	CookGoal(CitizenEntity citizen, Job job) {
		super(citizen, job);
		this.job = job;
	}

	/** Ses recettes : le boulanger les siennes ; le cuisinier lui laisse les siennes s'il y en a un. */
	private Cooking kitchen() {
		if (job == Job.BAKER) {
			return Cooking.BAKER;
		}
		return citizen.town().filter(t -> t.workers(Job.BAKER) > 0).isPresent() ? Cooking.COOK_WITH_BAKER
				: Cooking.COOK;
	}

	@Override
	public void start() {
		super.start();
		phase = kitchen().canCook(citizen.inventory()) ? Phase.COOK
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
		// Les ingrédients : dans l'atelier, l'entrepôt (le blé) ou le grenier (la viande crue).
		Optional<BlockPos> source = find(supplies(), kitchen()::isIngredient);
		if (source.isEmpty()) {
			rest(IDLE_LONG);
			return;
		}
		BlockPos granary = source.get();
		if (!goTo(granary)) {
			return;
		}
		container(granary).ifPresent(c -> {
			for (int i = 0; i < 6 && !citizen.inventoryFull(); i++) {
				ItemStack taken = Stock.take(c, kitchen()::isIngredient, BATCH);
				if (taken.isEmpty()) {
					break;
				}
				citizen.carry(taken);
			}
			if (!kitchen().canCook(citizen.inventory())) {
				// De quoi rien faire (un bol sans soupe, deux blés) : il repose tout, et attend.
				List<ItemStack> inventory = citizen.inventory();
				for (int i = 0; i < inventory.size(); i++) {
					inventory.set(i, Stock.insert(c, inventory.get(i)));
				}
			}
		});
		if (kitchen().canCook(citizen.inventory())) {
			phase = Phase.COOK;
		} else {
			phase = citizen.inventory().stream().allMatch(ItemStack::isEmpty) ? Phase.FETCH : Phase.DELIVER;
			rest(IDLE_LONG);
		}
	}

	private void cook() {
		BlockPos station = employment().station();
		if (!goTo(station)) {
			return;
		}
		light(true);
		citizen.act(Action.COOK, 10);
		if (++timer < COOK_TIME) {
			if (timer % 10 == 0) {
				level().sendParticles(ParticleTypes.SMOKE, station.getX() + 0.5, station.getY() + 1.1,
						station.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.01);
			}
			return;
		}
		timer = 0;
		ItemStack dish = kitchen().cookOne(citizen.inventory());
		if (dish.isEmpty()) {
			light(false);
			phase = Phase.DELIVER;
			return;
		}
		citizen.swing(InteractionHand.MAIN_HAND);
		level().playSound(null, station, job == Job.BAKER ? SoundEvents.FURNACE_FIRE_CRACKLE : SoundEvents.SMOKER_SMOKE,
				SoundSource.BLOCKS, 1.0f, 1.0f);
		citizen.carry(dish);
	}

	private void deliver() {
		if (deliver(s -> false, 0)) {
			phase = Phase.FETCH;
		}
	}

	/** Allume ou éteint le fumoir (ou le four) pendant qu'il cuisine. */
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
