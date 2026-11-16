package com.ajustor.minecities.citizen;

import com.ajustor.minecities.mixin.MobAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

/**
 * Les citoyens ne sont pas des villageois vanilla : les monstres qui chassent les villageois (zombies,
 * illageois, ravageurs) ne les voyaient pas. Chacun reçoit, à son entrée dans le monde, de quoi les
 * prendre pour cible, à la même priorité que les villageois.
 */
public final class Prey {
	private Prey() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && hunts(mob)) {
				((MobAccessor) mob).minecities$targetSelector().addGoal(3,
						new NearestAttackableTargetGoal<>(mob, CitizenEntity.class, false));
			}
		});
	}

	/** Ce monstre chasse-t-il les villageois ? (Le piglin zombifié, un zombie, les laisse en paix.) */
	static boolean hunts(Mob mob) {
		return mob instanceof Zombie && !(mob instanceof ZombifiedPiglin) || mob instanceof AbstractIllager
				|| mob instanceof Ravager;
	}
}
