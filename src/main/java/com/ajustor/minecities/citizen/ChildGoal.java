package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Town;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les enfants (brief 10, MC-81) : le matin, s'il y a une bibliothèque, ils vont jouer devant (l'école) ;
 * le reste du jour, ils suivent un de leurs parents (un adulte de leur maison), en sautillant.
 */
class ChildGoal extends Goal {
	/** Les heures d'école : du matin au début d'après-midi. */
	private static final long SCHOOL_START = 1000;
	private static final long SCHOOL_END = 7000;
	/** Il suit son parent sans le coller. */
	private static final double FOLLOW = 3;

	private final CitizenEntity child;
	private CitizenEntity parent;
	private BlockPos school;
	private int repath;

	ChildGoal(CitizenEntity child) {
		this.child = child;
		setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (!child.isChild() || child.isSleeping() || child.alarmed() || child.getRandom().nextInt(10) != 0) {
			return false;
		}
		Optional<Town> town = child.town();
		if (town.isEmpty()) {
			return false;
		}
		long time = Math.floorMod(child.level().getOverworldClockTime(), 24000L);
		school = time >= SCHOOL_START && time < SCHOOL_END ? school(town.get()) : null;
		parent = school == null ? parent(town.get()) : null;
		return school != null || parent != null;
	}

	@Override
	public boolean canContinueToUse() {
		return child.isChild() && !child.isSleeping() && !child.alarmed()
				&& (school != null || parent != null && parent.isAlive() && child.distanceToSqr(parent) < 32 * 32);
	}

	/** La bibliothèque de la ville : le poste du bibliothécaire, s'il y en a un. */
	private static BlockPos school(Town town) {
		for (House house : town.houses()) {
			for (House.Workstation station : house.workstations()) {
				if (station.job() == Job.LIBRARIAN) {
					return house.plaque();
				}
			}
		}
		return null;
	}

	/** Un adulte de sa maison, à portée. */
	private CitizenEntity parent(Town town) {
		Optional<BlockPos> home = town.resident(child.getUUID()).flatMap(Resident::home);
		if (home.isEmpty()) {
			return null;
		}
		for (Map.Entry<UUID, Resident> e : town.residents().entrySet()) {
			if (!e.getValue().isChild() && e.getValue().home().equals(home)
					&& child.level().getEntity(e.getKey()) instanceof CitizenEntity adult && adult.isAlive()
					&& child.distanceToSqr(adult) < 32 * 32) {
				return adult;
			}
		}
		return null;
	}

	@Override
	public void start() {
		repath = 0;
	}

	@Override
	public void tick() {
		if (school != null) {
			// À l'école : il court et joue devant la bibliothèque.
			if (child.getNavigation().isDone() && --repath <= 0) {
				repath = 40;
				child.getNavigation().moveTo(school.getX() + child.getRandom().nextInt(7) - 3, school.getY(),
						school.getZ() + child.getRandom().nextInt(7) - 3, 0.9);
			}
		} else if (parent != null && child.distanceToSqr(parent) > FOLLOW * FOLLOW) {
			if (--repath <= 0 || child.getNavigation().isDone()) {
				repath = 20;
				child.getNavigation().moveTo(parent, 0.9);
			}
		} else {
			child.getNavigation().stop();
		}
		// Une démarche bondissante.
		if (child.onGround() && child.getRandom().nextInt(30) == 0) {
			child.getJumpControl().jump();
		}
	}

	@Override
	public void stop() {
		parent = null;
		school = null;
		child.getNavigation().stop();
	}
}
