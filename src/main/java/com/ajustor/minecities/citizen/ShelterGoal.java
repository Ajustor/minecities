package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Resident;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Pendant un raid ou quand la cloche sonne l'alerte, les citoyens qui ne se battent pas se mettent à
 * l'abri : chez eux, près de leur lit ; sans logis, à l'hôtel de ville. Ils y restent jusqu'à la fin
 * du raid ou de l'alerte.
 */
class ShelterGoal extends Goal {
	/** Sans s'approcher de son refuge pendant 15 secondes (une porte bouchée), il s'y glisse. */
	private static final int STUCK = 300;

	private final CitizenEntity citizen;
	private int repath;
	private int stuck;
	private double closest;

	ShelterGoal(CitizenEntity citizen) {
		this.citizen = citizen;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	private boolean raid() {
		return !citizen.isDefender() && citizen.alarmed();
	}

	@Override
	public boolean canUse() {
		return raid();
	}

	@Override
	public boolean canContinueToUse() {
		return raid();
	}

	@Override
	public void start() {
		repath = 0;
		stuck = 0;
		closest = Double.MAX_VALUE;
		citizen.say("raid", Component.translatable("speech.minecities.raid"));
	}

	@Override
	public void tick() {
		Optional<BlockPos> refuge = citizen.resident().flatMap(Resident::bed)
				.or(() -> citizen.town().map(t -> t.hall().above()));
		if (refuge.isEmpty()) {
			return;
		}
		BlockPos target = refuge.get();
		double distance = citizen.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
		if (distance < 2.0 * 2.0) {
			citizen.getNavigation().stop();
			return;
		}
		// Coincé (à deux dans l'embrasure d'une porte, derrière un voisin) : il finit par y arriver.
		if (distance < closest - 0.5) {
			closest = distance;
			stuck = 0;
		} else if (++stuck > STUCK) {
			stuck = 0;
			citizen.snapTo(target.getX() + 0.5, target.getY() + 0.6, target.getZ() + 0.5, citizen.getYRot(), 0);
			citizen.getNavigation().stop();
			return;
		}
		if (--repath <= 0 || citizen.getNavigation().isDone()) {
			repath = 20;
			citizen.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1, 0.8);
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}
}
