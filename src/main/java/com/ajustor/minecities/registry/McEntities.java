package com.ajustor.minecities.registry;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.citizen.CitizenEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class McEntities {
	private static final ResourceKey<EntityType<?>> CITIZEN_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, MineCities.id("citizen"));

	/** Un citoyen : il habite une maison de la ville, y dort, et mange au grenier ou dans ses coffres. */
	public static final EntityType<CitizenEntity> CITIZEN = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			CITIZEN_KEY,
			EntityType.Builder.<CitizenEntity>of(CitizenEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.clientTrackingRange(10)
					.build(CITIZEN_KEY));

	private static final ResourceKey<EntityType<?>> CARAVAN_CART_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, MineCities.id("caravan_cart"));

	/** Le chariot d'une caravane (MC-80) : placé par la caravane, jamais sauvegardé (elle le recrée). */
	public static final EntityType<com.ajustor.minecities.caravan.CaravanCart> CARAVAN_CART = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, CARAVAN_CART_KEY,
			EntityType.Builder.<com.ajustor.minecities.caravan.CaravanCart>of(com.ajustor.minecities.caravan.CaravanCart::new,
							MobCategory.MISC)
					.sized(1.6F, 1.8F)
					.noSave()
					.clientTrackingRange(10)
					.updateInterval(2)
					.build(CARAVAN_CART_KEY));

	private static final ResourceKey<EntityType<?>> CARAVAN_STALL_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, MineCities.id("caravan_stall"));

	/** L'échoppe d'une caravane arrivée (MC-80) : comme le chariot, recréée près des joueurs. */
	public static final EntityType<com.ajustor.minecities.caravan.CaravanStall> CARAVAN_STALL = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, CARAVAN_STALL_KEY,
			EntityType.Builder.<com.ajustor.minecities.caravan.CaravanStall>of(com.ajustor.minecities.caravan.CaravanStall::new,
							MobCategory.MISC)
					.sized(2.6F, 2.4F)
					.noSave()
					.clientTrackingRange(10)
					.updateInterval(20)
					.build(CARAVAN_STALL_KEY));

	private static final ResourceKey<EntityType<?>> BANDIT_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, MineCities.id("bandit"));

	/** Un bandit de grand chemin (MC-80), qui tend des embuscades aux caravanes. */
	public static final EntityType<com.ajustor.minecities.caravan.Bandit> BANDIT = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, BANDIT_KEY,
			EntityType.Builder.<com.ajustor.minecities.caravan.Bandit>of(com.ajustor.minecities.caravan.Bandit::new,
							MobCategory.MONSTER)
					.sized(0.6F, 1.95F)
					.eyeHeight(1.62F)
					.clientTrackingRange(8)
					.build(BANDIT_KEY));

	private McEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(CITIZEN, CitizenEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(BANDIT, com.ajustor.minecities.caravan.Bandit.createAttributes());
	}
}
