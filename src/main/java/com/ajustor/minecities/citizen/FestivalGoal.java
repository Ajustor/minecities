package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Town;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Optional;

/**
 * La fête : les citoyens laissent leur ouvrage et viennent danser devant l'hôtel de ville, chacun à
 * sa place autour, en sautillant, tant que dure la fête.
 */
class FestivalGoal extends Goal {
	private final CitizenEntity citizen;
	private BlockPos spot;
	private int repath;

	FestivalGoal(CitizenEntity citizen) {
		this.citizen = citizen;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	private boolean party() {
		Optional<Town> town = citizen.town();
		return town.isPresent() && !citizen.isSleeping() && citizen.hunger() > 0
				&& town.get().chronicle().partying(citizen.level().getGameTime()) && !citizen.alarmed()
				&& citizen.getTarget() == null;
	}

	@Override
	public boolean canUse() {
		return party();
	}

	@Override
	public boolean canContinueToUse() {
		return party();
	}

	@Override
	public void start() {
		BlockPos hall = citizen.town().orElseThrow().hall();
		// Une place en cercle autour de l'hôtel de ville, à 3 à 6 blocs.
		double angle = citizen.getRandom().nextDouble() * Math.PI * 2;
		double distance = 3 + citizen.getRandom().nextDouble() * 3;
		spot = hall.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
		repath = 0;
		citizen.say("festival", Component.translatable("speech.minecities.festival"));
	}

	@Override
	public void tick() {
		BlockPos hall = citizen.town().map(Town::hall).orElse(spot);
		if (citizen.distanceToSqr(spot.getX() + 0.5, citizen.getY(), spot.getZ() + 0.5) > 2.0 * 2.0) {
			if (--repath <= 0 || citizen.getNavigation().isDone()) {
				repath = 30;
				citizen.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1, 0.7);
			}
			return;
		}
		citizen.getNavigation().stop();
		citizen.getLookControl().setLookAt(hall.getX() + 0.5, hall.getY() + 1.5, hall.getZ() + 0.5);
		citizen.act(Action.DANCE, 20);
		if (citizen.onGround() && citizen.getRandom().nextInt(25) == 0) {
			citizen.getJumpControl().jump();
		}
	}

	@Override
	public void stop() {
		spot = null;
		citizen.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}
}
