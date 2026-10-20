package com.ajustor.minecities.caravan;

import com.ajustor.minecities.citizen.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Un bandit de grand chemin (MC-80) : capuche, foulard sur le nez, une épée de pierre ou de bois. Il tend
 * des embuscades aux caravanes ({@link Caravans}) : il s'en prend à l'équipage, puis, une fois les
 * défenseurs à terre, prend sa part du chargement et s'enfuit. Tué, il rend ce qu'il emportait.
 */
public class Bandit extends Monster {
	/** Le temps de fuir avant de disparaître, chargé ou non. */
	private static final int FLIGHT = 200;

	/** Ce qu'il a pris au chariot. */
	private final List<ItemStack> loot = new ArrayList<>();
	/** Sa fuite, une fois le pillage fait (en ticks restants) ; 0 : il se bat encore. */
	private int fleeing;

	public Bandit(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 22)
				.add(Attributes.MOVEMENT_SPEED, 0.33)
				.add(Attributes.FOLLOW_RANGE, 32)
				.add(Attributes.ATTACK_DAMAGE, 2.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) {
			@Override
			public boolean canUse() {
				return fleeing == 0 && super.canUse();
			}
		});
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		// D'abord l'équipage d'une caravane, debout ; puis les joueurs.
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, CitizenEntity.class, 10, true, false,
				(target, level) -> fleeing == 0 && target instanceof CitizenEntity citizen && citizen.traveler()
						&& !citizen.downed()));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
			EntitySpawnReason reason, SpawnGroupData data) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(getRandom().nextBoolean() ? Items.STONE_SWORD : Items.WOODEN_SWORD));
		setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		setDropChance(EquipmentSlot.MAINHAND, 0.05f);
		return result;
	}

	/** Il prend cette part du chargement et s'enfuit, loin de ce point. */
	public void flee(List<ItemStack> share, Vec3 from) {
		share.forEach(s -> loot.add(s.copy()));
		fleeing = FLIGHT;
		setTarget(null);
		Vec3 away = position().subtract(from).normalize().scale(24).add(position());
		getNavigation().moveTo(away.x, away.y, away.z, 1.2);
	}

	public boolean fleeing() {
		return fleeing > 0;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (fleeing > 0 && !level().isClientSide()) {
			setTarget(null);
			if (--fleeing == 0) {
				// Hors de vue, avec son butin : il ne reviendra pas.
				discard();
			}
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		// Tué, il rend ce qu'il avait pris.
		loot.forEach(stack -> spawnAtLocation(level, stack));
		loot.clear();
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}
}
