package com.ajustor.minecities.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * La cérémonie (brief 10, MC-81) : les deux mariés laissent leur ouvrage, viennent se placer face à face
 * devant l'hôtel de ville, et y échangent leurs vœux sous les cœurs.
 */
class WeddingGoal extends Goal {
	private final CitizenEntity citizen;
	private int repath;
	private int vows;

	WeddingGoal(CitizenEntity citizen) {
		this.citizen = citizen;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return citizen.wedding().isPresent() && !citizen.alarmed();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		repath = 0;
		vows = 0;
	}

	@Override
	public void tick() {
		BlockPos spot = citizen.wedding().orElseThrow();
		if (citizen.distanceToSqr(spot.getX() + 0.5, citizen.getY(), spot.getZ() + 0.5) > 1.5 * 1.5) {
			if (--repath <= 0 || citizen.getNavigation().isDone()) {
				repath = 20;
				citizen.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.8);
			}
			return;
		}
		citizen.getNavigation().stop();
		CitizenEntity partner = citizen.spouseEntity().orElse(null);
		if (partner != null) {
			citizen.getLookControl().setLookAt(partner);
		}
		// Les vœux, une fois l'autre arrivé (ou après l'avoir attendu un moment).
		boolean together = partner != null && partner.distanceToSqr(citizen) < 4 * 4;
		if (!together && ++vows < 400) {
			return;
		}
		if (++vows % 20 == 0 && citizen.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HEART, citizen.getX(), citizen.getY() + 2, citizen.getZ(), 3, 0.3, 0.3,
					0.3, 0);
		}
		citizen.act(Action.CHEER, 20);
		if (vows >= 460 || together && vows >= 100) {
			citizen.say("married", Component.translatable("speech.minecities.married"));
			citizen.endWedding();
		}
	}

	@Override
	public void stop() {
		citizen.getNavigation().stop();
	}
}
