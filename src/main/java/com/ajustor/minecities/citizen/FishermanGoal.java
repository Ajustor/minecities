package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Le pêcheur, au baril, dans un atelier au bord de l'eau. Il gagne la rive la plus proche de son
 * baril, pêche à la canne (la table de pêche vanilla : poissons, et parfois un trésor ou un
 * déchet), une prise toutes les 20 à 40 secondes, et porte sa pêche au grenier.
 */
class FishermanGoal extends WorkGoal {
	static final int SHORE_RADIUS = 12;
	private static final int LOAD = 16;

	/** Là où il se tient, et l'eau où il lance sa ligne. */
	private BlockPos spot;
	private BlockPos water;
	private int bite;
	private boolean delivering;
	private int scan;

	FishermanGoal(CitizenEntity citizen) {
		super(citizen, Job.FISHERMAN);
	}

	@Override
	protected void onStop() {
		spot = null;
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		if (!ensureTool()) {
			return;
		}
		int produce = produce(s -> false, 0);
		if (delivering || produce >= LOAD || citizen.inventoryFull() || evening() && produce > 0) {
			delivering = !deliver(s -> false, 0);
			return;
		}
		if (spot == null) {
			if (--scan > 0) {
				return;
			}
			scan = 60;
			findShore();
			if (spot == null) {
				rest(IDLE_LONG);
				return;
			}
			bite = 400 + citizen.getRandom().nextInt(400);
		}
		if (!goTo(spot)) {
			return;
		}
		citizen.getLookControl().setLookAt(water.getX() + 0.5, water.getY() + 0.5, water.getZ() + 0.5);
		if (citizen.tickCount % 60 == 0) {
			level().sendParticles(ParticleTypes.FISHING, water.getX() + 0.5, water.getY() + 1, water.getZ() + 0.5, 2,
					0.1, 0, 0.1, 0);
		}
		if (--bite > 0) {
			return;
		}
		bite = 400 + citizen.getRandom().nextInt(400);
		cast();
	}

	/** La rive la plus proche du baril : une case où se tenir, au bord d'une eau à ciel ouvert. */
	private void findShore() {
		BlockPos station = employment().station();
		double best = Double.MAX_VALUE;
		spot = null;
		for (BlockPos pos : BlockPos.betweenClosed(station.offset(-SHORE_RADIUS, -3, -SHORE_RADIUS),
				station.offset(SHORE_RADIUS, 2, SHORE_RADIUS))) {
			if (!level().isLoaded(pos) || !level().getBlockState(pos).isAir()
					|| !level().getBlockState(pos.below()).blocksMotion()
					|| !town().contains(level().dimension(), pos)) {
				continue;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos w = pos.relative(d).below();
				if (level().getFluidState(w).is(FluidTags.WATER) && level().getFluidState(w).isSource()
						&& level().getBlockState(w.above()).isAir()) {
					double distance = pos.distSqr(station);
					if (distance < best) {
						best = distance;
						spot = pos.immutable();
						water = w.immutable();
					}
				}
			}
		}
	}

	/** Une prise, tirée de la table de pêche vanilla. */
	private void cast() {
		ItemStack rod = citizen.getMainHandItem();
		citizen.swing(InteractionHand.MAIN_HAND);
		LootParams params = new LootParams.Builder(level())
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(water))
				.withParameter(LootContextParams.TOOL, rod)
				.withOptionalParameter(LootContextParams.THIS_ENTITY, citizen)
				.create(LootContextParamSets.FISHING);
		LootTable table = level().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
		table.getRandomItems(params).forEach(citizen::carry);
		level().sendParticles(ParticleTypes.SPLASH, water.getX() + 0.5, water.getY() + 1, water.getZ() + 0.5, 12,
				0.3, 0.1, 0.3, 0.1);
		level().playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5f, 1.0f);
		rod.hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
	}
}
