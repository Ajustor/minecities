package com.ajustor.minecities.registry;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.block.HousePlaqueBlock;
import com.ajustor.minecities.block.MineWinchBlock;
import com.ajustor.minecities.block.WorkstationBlock;
import com.ajustor.minecities.block.TownAnnexBlock;
import com.ajustor.minecities.block.TownHallBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Function;

public final class McBlocks {
	/** L'hôtel de ville : le poser fonde une ville ; il abrite le grenier commun. */
	public static final Block TOWN_HALL = register("town_hall", TownHallBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(3.5f, 1200.0f)
					.noOcclusion()
					.sound(SoundType.WOOD)
					.pushReaction(PushReaction.BLOCK));

	/** La plaque de maison : accrochée à un mur intérieur, elle déclare la maison à la ville. */
	public static final Block HOUSE_PLAQUE = register("house_plaque", HousePlaqueBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(1.0f)
					.noCollision()
					.sound(SoundType.WOOD)
					.pushReaction(PushReaction.DESTROY));

	/** La mairie annexe : elle étend le territoire d'une grande ville, avec un grenier de quartier. */
	public static final Block TOWN_ANNEX = register("town_annex", TownAnnexBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(3.0f, 1200.0f)
					.noOcclusion()
					.sound(SoundType.WOOD)
					.pushReaction(PushReaction.BLOCK));

	/** Le treuil de mine : le poste du mineur. */
	public static final Block MINE_WINCH = register("mine_winch", MineWinchBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(2.0f)
					.noOcclusion()
					.sound(SoundType.WOOD));

	/** L'établi du joaillier : le poste du joaillier. */
	public static final Block JEWELER_BENCH = register("jeweler_bench", WorkstationBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(2.5f)
					.noOcclusion()
					.sound(SoundType.WOOD));

	/** Le registre d'entrepôt : il fait du bâtiment où il est l'entrepôt de la ville. */
	public static final Block WAREHOUSE_LEDGER = register("warehouse_ledger", WorkstationBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(2.0f)
					.noOcclusion()
					.sound(SoundType.WOOD));

	/** L'établi du charpentier : le poste du charpentier. */
	public static final Block CARPENTER_BENCH = register("carpenter_bench", WorkstationBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(2.5f)
					.noOcclusion()
					.sound(SoundType.WOOD));

	/** Le séchoir à peaux : le poste du tanneur. */
	public static final Block TANNING_RACK = register("tanning_rack", WorkstationBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(2.0f)
					.noOcclusion()
					.sound(SoundType.WOOD));

	/** Le four du verrier : le poste du verrier. */
	public static final Block GLASS_KILN = register("glass_kiln", WorkstationBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_RED)
					.strength(3.5f)
					.requiresCorrectToolForDrops()
					.noOcclusion()
					.lightLevel(state -> 7)
					.sound(SoundType.STONE));

	private McBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties) {
		return Blocks.register(ResourceKey.create(Registries.BLOCK, MineCities.id(name)), factory, properties);
	}

	public static void register() {
		// Le chargement de la classe suffit.
	}
}
