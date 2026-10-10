package com.ajustor.minecities.build;

import com.ajustor.minecities.registry.McBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Le plan d'un chantier : les blocs d'un schéma, dans un style, posés à une origine et tournés,
 * dans l'ordre où le bâtisseur les pose. D'abord il dégage (du haut vers le bas), puis il monte le
 * gros œuvre couche par couche, puis il pose ce qui s'accroche (portes, lits, torches, plaque), et
 * l'eau en dernier.
 */
public final class BuildPlan {
	/** Un bloc à poser (ou de l'air à faire). La moitié haute d'une porte, la tête d'un lit suivent seules. */
	public record Placement(BlockPos pos, BlockState state) {
	}

	private BuildPlan() {
	}

	/** Tourne un décalage local (x, z) : la porte, au nord dans le schéma, regarde où dit la rotation. */
	public static BlockPos offset(Rotation rotation, int x, int y, int z) {
		return switch (rotation) {
			case NONE -> new BlockPos(x, y, z);
			case CLOCKWISE_90 -> new BlockPos(-z, y, x);
			case CLOCKWISE_180 -> new BlockPos(-x, y, -z);
			case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, -x);
		};
	}

	/** La rotation qui tourne la porte (nord) vers {@code front}. */
	public static Rotation facing(Direction front) {
		for (Rotation rotation : Rotation.values()) {
			if (rotation.rotate(Direction.NORTH) == front) {
				return rotation;
			}
		}
		return Rotation.NONE;
	}

	/**
	 * L'origine (le coin nord-ouest du schéma, une fois tourné) pour que l'entrée soit devant
	 * {@code standing}, à deux pas, la porte vers lui.
	 */
	public static BlockPos originFacing(Blueprint blueprint, BlockPos standing, Rotation rotation) {
		BlockPos anchor = offset(rotation, blueprint.width() / 2, 0, -2);
		return standing.subtract(anchor);
	}

	/** Les coins de l'emprise du chantier. */
	public static BlockPos[] bounds(Blueprint blueprint, BlockPos origin, Rotation rotation) {
		BlockPos a = origin.offset(offset(rotation, 0, 0, 0));
		BlockPos b = origin.offset(offset(rotation, blueprint.width() - 1, blueprint.height() - 1, blueprint.depth() - 1));
		return new BlockPos[]{BlockPos.min(a, b), BlockPos.max(a, b)};
	}

	public static List<Placement> of(Blueprint blueprint, BuildStyle style, BlockPos origin, Rotation rotation) {
		List<Placement> plan = new ArrayList<>();
		for (Blueprint.Cell cell : blueprint.cells()) {
			BlockState state = parse(style.resolve(cell.template())).rotate(rotation);
			if (isFollower(state)) {
				continue;
			}
			plan.add(new Placement(origin.offset(offset(rotation, cell.x(), cell.y(), cell.z())), state));
		}
		plan.sort(Comparator.comparingInt((Placement p) -> stage(p.state()))
				.thenComparingInt(p -> p.state().isAir() ? -p.pos().getY() : p.pos().getY())
				.thenComparingInt(p -> p.pos().getZ())
				.thenComparingInt(p -> p.pos().getX()));
		return plan;
	}

	/** La moitié haute d'une porte, la tête d'un lit : posées avec l'autre moitié. */
	private static boolean isFollower(BlockState state) {
		return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
				|| state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
	}

	private static int stage(BlockState state) {
		if (state.isAir()) {
			return 0;
		}
		if (state.is(Blocks.WATER)) {
			return 3;
		}
		Block block = state.getBlock();
		if (block instanceof DoorBlock || block instanceof BedBlock || block instanceof BaseTorchBlock
				|| state.is(McBlocks.HOUSE_PLAQUE)) {
			return 2;
		}
		return 1;
	}

	/** Ce qu'il faut au bâtisseur pour poser ce bloc ; vide s'il ne coûte rien (de l'air). */
	public static Optional<Item> material(BlockState state) {
		if (state.isAir()) {
			return Optional.empty();
		}
		if (state.is(Blocks.WATER)) {
			return Optional.of(Items.WATER_BUCKET);
		}
		if (state.is(Blocks.FARMLAND)) {
			return Optional.of(Items.DIRT);
		}
		Item item = state.getBlock().asItem();
		return item == Items.AIR ? Optional.empty() : Optional.of(item);
	}

	/** Les matériaux de tout le plan, par objet, dans l'ordre où ils servent. */
	public static Map<Item, Integer> materials(List<Placement> plan) {
		Map<Item, Integer> needs = new LinkedHashMap<>();
		for (Placement p : plan) {
			material(p.state()).ifPresent(item -> needs.merge(item, 1, Integer::sum));
		}
		return needs;
	}

	/** Lit « minecraft:oak_door[facing=north,half=lower] » ; les propriétés inconnues du bloc sont ignorées. */
	public static BlockState parse(String text) {
		int bracket = text.indexOf('[');
		String id = bracket < 0 ? text : text.substring(0, bracket);
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
		BlockState state = block.defaultBlockState();
		if (bracket >= 0) {
			for (String pair : text.substring(bracket + 1, text.length() - 1).split(",")) {
				String[] kv = pair.split("=", 2);
				Property<?> property = block.getStateDefinition().getProperty(kv[0].trim());
				if (property != null) {
					state = with(state, property, kv[1].trim());
				}
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}
}
