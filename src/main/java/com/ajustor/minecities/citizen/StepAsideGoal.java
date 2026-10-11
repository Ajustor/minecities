package com.ajustor.minecities.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Quand un bâtisseur lui demande de laisser la place, le citoyen s'écarte : il va au point qu'on
 * lui indique (hors du chantier), avant toute autre tâche, puis reprend ce qu'il faisait.
 */
class StepAsideGoal extends Goal {
	private final CitizenEntity citizen;
	private int repath;

	StepAsideGoal(CitizenEntity citizen) {
		this.citizen = citizen;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		return citizen.stepAsideTarget().isPresent();
	}

	@Override
	public void start() {
		repath = 0;
		if (citizen.isSleeping()) {
			citizen.stopSleeping();
		}
	}

	@Override
	public void tick() {
		BlockPos target = citizen.stepAsideTarget().orElse(null);
		if (target == null) {
			return;
		}
		if (citizen.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) < 1.0) {
			citizen.clearStepAside();
			return;
		}
		if (--repath <= 0 || citizen.getNavigation().isDone()) {
			repath = 20;
			citizen.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0, 0.75);
		}
	}

	@Override
	public void stop() {
		citizen.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}
}
