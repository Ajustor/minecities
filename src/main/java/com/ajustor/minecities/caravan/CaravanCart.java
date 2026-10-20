package com.ajustor.minecities.caravan;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Le chariot d'une caravane (MC-80) : la part visible de sa cargaison, tirée par un cheval. Il n'existe
 * que près d'un joueur ; c'est la caravane ({@link Caravans}) qui le place, pas lui qui se déplace. La
 * nuit, il devient un camp : tente et feu de bois, à côté du chariot.
 */
public class CaravanCart extends Entity {
	/** Le feu du camp, dans le repère du chariot (en blocs : x vers sa gauche, z vers l'avant). */
	public static final double FIRE_X = 0;
	public static final double FIRE_Z = -2.4;
	/** Où s'assoit le marchand, près du feu. */
	public static final double SEAT_X = -1.3;
	public static final double SEAT_Z = -3.2;
	private static final EntityDataAccessor<Boolean> CAMPING = SynchedEntityData.defineId(CaravanCart.class,
			EntityDataSerializers.BOOLEAN);

	public CaravanCart(EntityType<?> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(CAMPING, false);
	}

	public boolean camping() {
		return entityData.get(CAMPING);
	}

	public void setCamping(boolean camping) {
		entityData.set(CAMPING, camping);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
