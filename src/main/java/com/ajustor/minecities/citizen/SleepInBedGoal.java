package com.ajustor.minecities.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Optional;

/** La nuit, le citoyen rentre chez lui et se couche dans son lit ; il se lève au matin. */
public class SleepInBedGoal extends Goal {
	private final CitizenEntity citizen;
	private BlockPos bed;
	private int recalc;

	public SleepInBedGoal(CitizenEntity citizen) {
		this.citizen = citizen;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	/** Entre le coucher du soleil (12 500) et l'aube (23 500). */
	public static boolean isNight(Level level) {
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return time >= 12500 && time < 23500;
	}

	private boolean bedAvailable(BlockPos pos) {
		BlockState state = citizen.level().getBlockState(pos);
		return state.getBlock() instanceof BedBlock
				&& (!state.getValue(BedBlock.OCCUPIED) || citizen.getSleepingPos().filter(pos::equals).isPresent());
	}

	@Override
	public boolean canUse() {
		if (!isNight(citizen.level())) {
			return false;
		}
		Optional<BlockPos> target = citizen.bed();
		if (target.isEmpty() || !bedAvailable(target.get())) {
			return false;
		}
		bed = target.get();
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return isNight(citizen.level()) && bed != null && citizen.bed().filter(bed::equals).isPresent()
				&& bedAvailable(bed);
	}

	@Override
	public void start() {
		recalc = 0;
	}

	@Override
	public void tick() {
		if (citizen.isSleeping()) {
			return;
		}
		if (citizen.distanceToSqr(bed.getX() + 0.5, bed.getY() + 0.5, bed.getZ() + 0.5) < 2.5 * 2.5) {
			citizen.getNavigation().stop();
			citizen.startSleeping(bed);
			return;
		}
		if (--recalc <= 0) {
			recalc = 40;
			citizen.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 0.6);
		}
	}

	@Override
	public void stop() {
		if (citizen.isSleeping()) {
			citizen.stopSleeping();
		}
		bed = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}
}
