package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.TownService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Le médecin, à l'alambic (brief 04). Il fait le tour de la ville : il soigne les blessés (deux
 * points de vie toutes les secondes, et les maux comme le poison), et porte à manger à ceux qui
 * meurent de faim, pris au grenier. Sans patient, il prépare ses remèdes à l'alambic.
 */
class DoctorGoal extends WorkGoal {
	/** Il soigne deux points de vie par seconde. */
	private static final int CARE = 20;
	private static final float CARE_AMOUNT = 2.0f;

	private CitizenEntity patient;
	private int care;
	private int search;

	DoctorGoal(CitizenEntity citizen) {
		super(citizen, Job.DOCTOR);
	}

	@Override
	protected double speed() {
		return 0.75;
	}

	@Override
	protected void onStop() {
		patient = null;
	}

	/** A-t-il besoin du médecin ? */
	static boolean ailing(CitizenEntity c) {
		return c.isAlive() && (c.getHealth() < c.getMaxHealth() - 1 || c.hunger() <= 0
				|| c.getActiveEffects().stream().anyMatch(e -> e.getEffect().value().getCategory()
				== MobEffectCategory.HARMFUL));
	}

	private Optional<CitizenEntity> findPatient() {
		BlockPos hall = town().hall();
		int r = town().radius();
		List<CitizenEntity> sick = level().getEntitiesOfClass(CitizenEntity.class,
				new AABB(hall).inflate(r, 32, r), c -> c != citizen && ailing(c)
						&& c.townId().filter(town().id()::equals).isPresent());
		// Les plus mal en point d'abord.
		return sick.stream().min(Comparator.comparingDouble(c -> c.getHealth() / c.getMaxHealth()));
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		if (patient == null || !ailing(patient)) {
			if (patient != null) {
				patient.say("cured", Component.translatable("speech.minecities.cured"));
				patient = null;
			}
			if (--search > 0) {
				return;
			}
			search = 40;
			patient = findPatient().orElse(null);
			if (patient == null) {
				// Personne à soigner : il prépare ses remèdes.
				if (goTo(employment().station())) {
					citizen.act(Action.COOK, 30);
				}
				rest(100);
				return;
			}
			citizen.say("on_my_way", Component.translatable("speech.minecities.doctor_coming", patient.getName()));
		}
		// Un affamé : il lui apporte d'abord à manger.
		if (patient.hunger() <= 0 && carriedFood().isEmpty()) {
			BlockPos granary = town().hall();
			if (goTo(granary)) {
				container(granary).ifPresent(c -> citizen.carry(TownService.takeFood(c)));
				if (carriedFood().isEmpty()) {
					citizen.complain("no_food", Component.translatable("message.minecities.doctor.no_food",
							citizen.getName()), Component.translatable("speech.minecities.hungry"));
					patient = null;
					rest(IDLE_SHORT);
				}
			}
			return;
		}
		if (!goTo(patient.blockPosition())) {
			return;
		}
		patient.getNavigation().stop();
		patient.getLookControl().setLookAt(citizen);
		citizen.act(Action.HEAL, 20);
		if (++care < CARE) {
			return;
		}
		care = 0;
		citizen.swing(InteractionHand.MAIN_HAND);
		Optional<ItemStack> food = carriedFood();
		if (patient.hunger() <= 0 && food.isPresent()) {
			patient.eat(food.get().split(1));
		}
		patient.heal(CARE_AMOUNT);
		for (MobEffectInstance effect : List.copyOf(patient.getActiveEffects())) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				patient.removeEffect(effect.getEffect());
			}
		}
		level().sendParticles(ParticleTypes.HAPPY_VILLAGER, patient.getX(), patient.getY() + 1.2, patient.getZ(), 5,
				0.3, 0.4, 0.3, 0);
		level().playSound(null, patient.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.4f,
				1.6f);
	}

	private Optional<ItemStack> carriedFood() {
		return citizen.inventory().stream().filter(TownService::isFood).findFirst();
	}
}
