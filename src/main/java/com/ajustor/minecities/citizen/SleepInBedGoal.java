package com.ajustor.minecities.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Optional;

/**
 * La nuit, le citoyen rentre chez lui et se couche dans son lit ; il se lève au matin. Dans les
 * dimensions où les lits explosent, il ne dort pas.
 */
public class SleepInBedGoal extends Goal {
	private final CitizenEntity citizen;
	private BlockPos bed;
	private int recalc;

	public SleepInBedGoal(CitizenEntity citizen) {
		this.citizen = citizen;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	/**
	 * Là où les lits explosent (le Nether, l'End), les citoyens ne dorment pas, et ne s'en portent
	 * pas plus mal.
	 */
	public static boolean bedsWork(Level level, BlockPos pos) {
		return !level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos).explodes();
	}

	/** Entre le coucher du soleil (12 500) et l'aube (23 500). */
	public static boolean isNight(Level level) {
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return time >= 12500 && time < 23500;
	}

	/** L'heure de rentrer pour ce citoyen : la nuit, ou dès la fin d'après-midi sous couvre-feu (MC-66). */
	public static boolean bedtime(CitizenEntity citizen) {
		if (isNight(citizen.level())) {
			return true;
		}
		long time = Math.floorMod(citizen.level().getOverworldClockTime(), 24000L);
		return time >= com.ajustor.minecities.town.Decree.CURFEW_TIME && time < 23500 && citizen.town()
				.filter(t -> t.decreed(com.ajustor.minecities.town.Decree.CURFEW)).isPresent();
	}

	private boolean bedAvailable(BlockPos pos) {
		if (!bedsWork(citizen.level(), pos)) {
			return false;
		}
		BlockState state = citizen.level().getBlockState(pos);
		return state.getBlock() instanceof BedBlock
				&& (!state.getValue(BedBlock.OCCUPIED) || citizen.getSleepingPos().filter(pos::equals).isPresent());
	}

	@Override
	public boolean canUse() {
		if (!bedtime(citizen) && !citizen.bedridden() || citizen.isDefender()) {
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
		return (bedtime(citizen) || citizen.bedridden()) && bed != null && citizen.bed().filter(bed::equals).isPresent()
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
