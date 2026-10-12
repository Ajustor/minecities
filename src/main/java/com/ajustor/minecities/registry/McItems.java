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
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.function.Function;

public final class McItems {
	public static final Item TOWN_HALL = register("town_hall", p -> new BlockItem(McBlocks.TOWN_HALL, p),
			new Item.Properties().stacksTo(1).useBlockDescriptionPrefix());
	public static final Item HOUSE_PLAQUE = register("house_plaque", p -> new BlockItem(McBlocks.HOUSE_PLAQUE, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item TOWN_ANNEX = register("town_annex", p -> new BlockItem(McBlocks.TOWN_ANNEX, p),
			new Item.Properties().stacksTo(16).useBlockDescriptionPrefix());
	public static final Item MINE_WINCH = register("mine_winch", p -> new BlockItem(McBlocks.MINE_WINCH, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item JEWELER_BENCH = register("jeweler_bench", p -> new BlockItem(McBlocks.JEWELER_BENCH, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item GLASS_KILN = register("glass_kiln", p -> new BlockItem(McBlocks.GLASS_KILN, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item TANNING_RACK = register("tanning_rack", p -> new BlockItem(McBlocks.TANNING_RACK, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item WAREHOUSE_LEDGER = register("warehouse_ledger", p -> new BlockItem(McBlocks.WAREHOUSE_LEDGER, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item CARPENTER_BENCH = register("carpenter_bench", p -> new BlockItem(McBlocks.CARPENTER_BENCH, p),
			new Item.Properties().useBlockDescriptionPrefix());
	/** Les bijoux du joaillier : ils se vendent cher au marché. */
	public static final Item AMETHYST_BROOCH = register("amethyst_brooch", Item::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item GOLD_RING = register("gold_ring", Item::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item DIADEM = register("diadem", Item::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
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
					output.accept(TOWN_ANNEX);
					output.accept(MINE_WINCH);
					output.accept(JEWELER_BENCH);
					output.accept(GLASS_KILN);
					output.accept(TANNING_RACK);
					output.accept(WAREHOUSE_LEDGER);
					output.accept(CARPENTER_BENCH);
					output.accept(AMETHYST_BROOCH);
					output.accept(GOLD_RING);
					output.accept(DIADEM);
					output.accept(CITIZEN_SPAWN_EGG);
				})
				.build());
	}
}
