package com.ajustor.minecities.caravan;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * L'échoppe d'une caravane arrivée (MC-80) : comptoir, auvent rayé, et sur le comptoir un exemplaire de
 * chaque marchandise. Comme le chariot, elle n'existe que près d'un joueur ; un clic ouvre le troc avec
 * le marchand ({@link StallMerchant}).
 */
public class CaravanStall extends Entity {
	/** Ce qu'on voit sur le comptoir : trois marchandises au plus. */
	public static final int SHOWN = 3;
	@SuppressWarnings("unchecked")
	private static final EntityDataAccessor<ItemStack>[] WARES = new EntityDataAccessor[SHOWN];

	static {
		for (int i = 0; i < SHOWN; i++) {
			WARES[i] = SynchedEntityData.defineId(CaravanStall.class, EntityDataSerializers.ITEM_STACK);
		}
	}

	/** L'échoppe qu'elle montre (côté serveur). */
	private UUID stall;

	public CaravanStall(EntityType<?> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		for (EntityDataAccessor<ItemStack> ware : WARES) {
			builder.define(ware, ItemStack.EMPTY);
		}
	}

	void show(UUID stall, List<ItemStack> wares) {
		this.stall = stall;
		for (int i = 0; i < SHOWN; i++) {
			ItemStack shown = i < wares.size() ? wares.get(i).copyWithCount(1) : ItemStack.EMPTY;
			if (!ItemStack.matches(shown, entityData.get(WARES[i]))) {
				entityData.set(WARES[i], shown);
			}
		}
	}

	/** La marchandise posée à cette place du comptoir (vide s'il n'y en a pas). */
	public ItemStack ware(int index) {
		return entityData.get(WARES[index]);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player instanceof ServerPlayer serverPlayer && stall != null) {
			Caravans.trade(serverPlayer, stall);
		}
		return InteractionResult.SUCCESS;
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
