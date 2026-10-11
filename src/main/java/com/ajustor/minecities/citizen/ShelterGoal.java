package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Raids;
import com.ajustor.minecities.town.Resident;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Pendant un raid, les citoyens qui ne se battent pas se mettent à l'abri : chez eux, près de leur
 * lit ; sans logis, à l'hôtel de ville. Ils y restent jusqu'à la fin du raid.
 */
class ShelterGoal extends Goal {
	private final CitizenEntity citizen;
	private int repath;

	ShelterGoal(CitizenEntity citizen) {
		this.citizen = citizen;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	private boolean raid() {
		return !citizen.isDefender() && citizen.townId().filter(Raids::underAttack).isPresent();
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
		if (citizen.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) < 2.0 * 2.0) {
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
