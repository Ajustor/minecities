package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * L'apiculteur, à sa ruche (brief 11). Il fait le tour des ruches et des nids du territoire proche :
 * ceux qui sont pleins de miel, il les récolte, en bouteilles s'il a des fioles (du miel, un délice
 * pour le moral), sinon aux cisailles (des rayons). Il rapporte la récolte au grenier.
 */
class BeekeeperGoal extends WorkGoal {
	/** Il visite les ruches à tant de blocs de la sienne. */
	static final int RANGE = 24;
	private static final int LOAD = 12;

	private BlockPos hive;
	private int scan;
	/** Quand il ira voir au grenier s'il y a des fioles (pas plus d'une fois par minute). */
	private long nextBottles;
	/** Plus rien à récolter : il rapporte ce qu'il a. */
	private boolean homeward;

	BeekeeperGoal(CitizenEntity citizen) {
		super(citizen, Job.BEEKEEPER);
	}

	@Override
	protected void onStop() {
		hive = null;
	}

	@Override
	public void tick() {
		if (!stationStands() || !ensureTool()) {
			rest(IDLE_SHORT);
			return;
		}
		int harvest = produce(s -> s.is(Items.GLASS_BOTTLE), 64);
		if (harvest >= LOAD || harvest > 0 && (homeward || evening())) {
			if (deliver(s -> s.is(Items.GLASS_BOTTLE), 16)) {
				homeward = false;
				// Au grenier, il reprend des fioles vides.
				if (citizen.inventory().stream().noneMatch(s -> s.is(Items.GLASS_BOTTLE))) {
					container(town().hall()).ifPresent(c -> citizen.carry(Stock.take(c, s -> s.is(Items.GLASS_BOTTLE), 16)));
				}
				rest(40);
			}
			return;
		}
		// Sans fiole, il passe au grenier en prendre : le miel en bouteille vaut mieux que les rayons.
		if (citizen.inventory().stream().noneMatch(s -> s.is(Items.GLASS_BOTTLE))
				&& level().getGameTime() >= nextBottles) {
			BlockPos granary = town().hall();
			if (!goTo(granary)) {
				return;
			}
			container(granary).ifPresent(c -> citizen.carry(Stock.take(c, s -> s.is(Items.GLASS_BOTTLE), 16)));
			nextBottles = level().getGameTime() + 1200;
			return;
		}
		if (hive == null || !full(level().getBlockState(hive))) {
			if (--scan > 0) {
				return;
			}
			scan = 40;
			hive = findHive().orElse(null);
			if (hive == null && harvest > 0) {
				homeward = true;
				return;
			}
			if (hive == null) {
				// Rien à récolter : il soigne sa ruche.
				if (goTo(employment().station())) {
					citizen.act(Action.FEED, 30);
				}
				rest(IDLE_LONG / 2);
				return;
			}
		}
		if (!goTo(hive)) {
			return;
		}
		harvest(hive);
		hive = null;
		rest(60);
	}

	private static boolean full(BlockState state) {
		return state.is(BlockTags.BEEHIVES) && state.hasProperty(BeehiveBlock.HONEY_LEVEL)
				&& state.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS;
	}

	private Optional<BlockPos> findHive() {
		BlockPos center = employment().station();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RANGE, -6, -RANGE), center.offset(RANGE, 12, RANGE))) {
			if (full(level().getBlockState(pos)) && town().contains(level().dimension(), pos)) {
				double distance = pos.distSqr(citizen.blockPosition());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/** Comme un joueur : une fiole remplie de miel, ou trois rayons aux cisailles ; la ruche se vide. */
	private void harvest(BlockPos pos) {
		BlockState state = level().getBlockState(pos);
		if (!full(state)) {
			return;
		}
		Optional<ItemStack> bottle = citizen.inventory().stream().filter(s -> s.is(Items.GLASS_BOTTLE)).findFirst();
		if (bottle.isPresent()) {
			bottle.get().shrink(1);
			citizen.carry(new ItemStack(Items.HONEY_BOTTLE));
			level().playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
		} else {
			citizen.carry(new ItemStack(Items.HONEYCOMB, 3));
			citizen.getMainHandItem().hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
			level().playSound(null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0f, 1.0f);
		}
		level().setBlockAndUpdate(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, 0));
		citizen.swing(InteractionHand.MAIN_HAND);
		citizen.act(Action.FEED, 20);
		level().sendParticles(ParticleTypes.FALLING_HONEY, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6,
				0.3, 0.3, 0.3, 0);
	}
}
