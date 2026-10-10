package com.ajustor.minecities.registry;

import com.ajustor.minecities.MineCities;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.function.Function;

public final class McItems {
	public static final Item TOWN_HALL = register("town_hall", p -> new BlockItem(McBlocks.TOWN_HALL, p),
			new Item.Properties().stacksTo(1).useBlockDescriptionPrefix());
	public static final Item HOUSE_PLAQUE = register("house_plaque", p -> new BlockItem(McBlocks.HOUSE_PLAQUE, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item CITIZEN_SPAWN_EGG = register("citizen_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(McEntities.CITIZEN));

	public static final ResourceKey<CreativeModeTab> TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, MineCities.id("minecities"));

	private McItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MineCities.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void register() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.minecities.minecities"))
				.icon(() -> new ItemStack(TOWN_HALL))
				.displayItems((parameters, output) -> {
					output.accept(TOWN_HALL);
					output.accept(HOUSE_PLAQUE);
					output.accept(CITIZEN_SPAWN_EGG);
				})
				.build());
	}
}
