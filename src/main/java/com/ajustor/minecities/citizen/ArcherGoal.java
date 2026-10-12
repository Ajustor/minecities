package com.ajustor.minecities.citizen;

import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * L'archer, à la table d'archerie. Il prend un arc et des flèches au grenier ou à la tour, monte à son
 * poste (le point le plus haut de sa tour) et tire, jour et nuit, sur ce qui est hostile sur le
 * territoire, jusqu'à 24 blocs. Une flèche par tir, l'arc s'use.
 */
class ArcherGoal extends WorkGoal {
	private static final double RANGE = 24;
	/** Un tir toutes les secondes et demie. */
	private static final int RELOAD = 30;
	private static final int ARROWS_KEPT = 64;

	private BlockPos post;
	private int reload;

	ArcherGoal(CitizenEntity citizen) {
		super(citizen, Job.ARCHER);
	}

	@Override
	protected void onStop() {
		// Il ne reste pas perché sans rien faire : il redescend au pied de sa tour.
		if (post != null && citizen.blockPosition().getY() > employment().station().getY() + 2
				&& citizen.job().isPresent()) {
			BlockPos station = employment().station();
			citizen.snapTo(station.getX() + 0.5, station.getY(), station.getZ() + 1.5, citizen.getYRot(), 0);
			citizen.fallDistance = 0;
		}
		post = null;
	}

	/**
	 * Sans flèches en réserve, il en taille : un silex, un bâton et une plume font quatre flèches. Il va
	 * chercher le tout là où il est. Renvoie vrai tant qu'il s'en occupe.
	 */
	private boolean fletch() {
		java.util.function.Predicate<ItemStack> feathers = s -> s.is(net.minecraft.world.item.Items.FEATHER);
		java.util.function.Predicate<ItemStack> flints = s -> s.is(net.minecraft.world.item.Items.FLINT);
		java.util.function.Predicate<ItemStack> sticks = s -> s.is(net.minecraft.world.item.Items.STICK);
		java.util.List<net.minecraft.world.Container> pool = pool(supplies());
		int sets = Math.min(16, Math.min(count(pool, feathers), Math.min(count(pool, flints), count(pool, sticks))));
		if (sets <= 0) {
			return false;
		}
		Optional<BlockPos> source = find(supplies(), feathers);
		if (source.isEmpty() || !goTo(source.get())) {
			return true;
		}
		take(pool, feathers, sets);
		take(pool, flints, sets);
		take(pool, sticks, sets);
		citizen.carry(new ItemStack(net.minecraft.world.item.Items.ARROW, 4 * sets));
		citizen.act(Action.BUILD, 40);
		citizen.say("fletched", net.minecraft.network.chat.Component.translatable("speech.minecities.fletched", 4 * sets));
		citizen.satisfied("arrows");
		return true;
	}

	private int arrows() {
		return citizen.inventory().stream().filter(s -> s.is(ItemTags.ARROWS)).mapToInt(ItemStack::getCount).sum();
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		if (!ensureTool()) {
			return;
		}
		if (arrows() == 0) {
			Optional<BlockPos> source = find(supplies(), s -> s.is(ItemTags.ARROWS));
			if (source.isEmpty() && fletch()) {
				return;
			}
			if (source.isEmpty()) {
				citizen.complain("arrows", Component.translatable("message.minecities.archer.no_arrows", citizen.getName()),
						Component.translatable("speech.minecities.no_arrows"));
				rest(400);
				return;
			}
			if (goTo(source.get())) {
				container(source.get()).ifPresent(c -> citizen.carry(Stock.take(c, s -> s.is(ItemTags.ARROWS), ARROWS_KEPT)));
				citizen.satisfied("arrows");
			}
			return;
		}
		if (post == null) {
			post = lookout(employment().station());
		}
		if (citizen.distanceToSqr(post.getX() + 0.5, post.getY(), post.getZ() + 0.5) > 1.5 * 1.5) {
			BlockPos station = employment().station();
			if (citizen.distanceToSqr(station.getX() + 0.5, station.getY(), station.getZ() + 0.5) <= 4 * 4) {
				// Il monte à son poste par l'escalier de la tour.
				citizen.getNavigation().stop();
				citizen.snapTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, citizen.getYRot(), 0);
				citizen.setDeltaMovement(Vec3.ZERO);
				level().playSound(null, post, SoundEvents.LADDER_STEP, SoundSource.NEUTRAL, 1.0f, 1.0f);
			} else {
				goTo(station);
			}
			return;
		}
		citizen.getNavigation().stop();
		LivingEntity target = citizen.getTarget();
		if (target == null || !target.isAlive() || citizen.distanceToSqr(target) > RANGE * RANGE
				|| !citizen.hasLineOfSight(target)) {
			return;
		}
		citizen.getLookControl().setLookAt(target);
		citizen.act(Action.AIM, 20);
		if (--reload > 0) {
			return;
		}
		reload = RELOAD;
		shoot(target);
	}

	private void shoot(LivingEntity target) {
		ItemStack bow = citizen.getMainHandItem();
		ItemStack arrows = citizen.inventory().stream().filter(s -> s.is(ItemTags.ARROWS)).findFirst().orElse(ItemStack.EMPTY);
		if (arrows.isEmpty()) {
			return;
		}
		AbstractArrow arrow = ProjectileUtil.getMobArrow(citizen, arrows.copyWithCount(1), 1.0f, bow);
		double dx = target.getX() - citizen.getX();
		double dy = target.getY(0.3333) - arrow.getY();
		double dz = target.getZ() - citizen.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		arrow.shoot(dx, dy + horizontal * 0.2, dz, 1.6f, 3.0f);
		level().addFreshEntity(arrow);
		level().playSound(null, citizen.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0f, 1.0f);
		citizen.swing(InteractionHand.MAIN_HAND);
		arrows.shrink(1);
		bow.hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
	}

	/**
	 * Le poste de l'archer : la case libre la plus haute, au-dessus d'un bloc plein, à 3 blocs au plus
	 * de sa table d'archerie (le haut de sa tour) ; à défaut, à côté de la table.
	 */
	private BlockPos lookout(BlockPos station) {
		BlockPos best = null;
		for (BlockPos pos : BlockPos.betweenClosed(station.offset(-3, 0, -3), station.offset(3, 16, 3))) {
			if (level().getBlockState(pos).isAir() && level().getBlockState(pos.above()).isAir()
					&& level().getBlockState(pos.below()).blocksMotion()
					&& (best == null || pos.getY() > best.getY())) {
				best = pos.immutable();
			}
		}
		return best != null ? best : station.above();
	}
}
