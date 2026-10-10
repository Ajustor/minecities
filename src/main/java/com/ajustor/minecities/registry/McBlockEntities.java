package com.ajustor.minecities.registry;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.block.TownHallBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class McBlockEntities {
	public static final BlockEntityType<TownHallBlockEntity> TOWN_HALL = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MineCities.id("town_hall"),
			FabricBlockEntityTypeBuilder.create(TownHallBlockEntity::new, McBlocks.TOWN_HALL).build());

	private McBlockEntities() {
	}

	public static void register() {
	}
}
