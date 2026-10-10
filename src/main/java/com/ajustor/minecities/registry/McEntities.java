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

	private McEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(CITIZEN, CitizenEntity.createAttributes());
	}
}
