package com.ajustor.minecities.registry;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.block.HousePlaqueBlock;
import com.ajustor.minecities.block.MineWinchBlock;
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
