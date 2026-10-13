package com.ajustor.minecities.block;

import com.ajustor.minecities.town.TownService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * La plaque de maison, accrochée à un mur, côté intérieur. Elle déclare la maison à la ville dont
 * elle est sur le territoire : la pièce doit être close (portes comprises) et avoir au moins un lit,
 * chaque lit accueillant un citoyen. Un clic dessus relève la maison à nouveau et en dit l'état.
 */
public class HousePlaqueBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<HousePlaqueBlock> CODEC = simpleCodec(HousePlaqueBlock::new);

	/** L'état du dernier relevé, qui se voit sur la plaque : porte verte, ou rouge. */
	public enum Status implements StringRepresentable {
		NONE("none"),
		VALID("valid"),
		INVALID("invalid");

		private final String name;

		Status(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);

	/** Une plaque d'un pixel d'épaisseur, contre le mur derrière elle (FACING regarde vers la pièce). */
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
			Direction.NORTH, Block.box(3, 4, 15, 13, 12, 16),
			Direction.SOUTH, Block.box(3, 4, 0, 13, 12, 1),
			Direction.WEST, Block.box(15, 4, 3, 16, 12, 13),
			Direction.EAST, Block.box(0, 4, 3, 1, 12, 13));

	public HousePlaqueBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, Status.NONE));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, STATUS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		if (face.getAxis().isHorizontal()) {
			BlockState state = defaultBlockState().setValue(FACING, face);
			if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
				return state;
			}
		}
		for (Direction direction : context.getNearestLookingDirections()) {
			if (direction.getAxis().isHorizontal()) {
				BlockState state = defaultBlockState().setValue(FACING, direction.getOpposite());
				if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
					return state;
				}
			}
		}
		return null;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos wall = pos.relative(facing.getOpposite());
		return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
				? Blocks.AIR.defaultBlockState() : state;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player
				&& !TownService.declareHouse(serverLevel, pos, player)) {
			// Son rang ne lui permet pas de déclarer de maison ici : la plaque lui revient.
			serverLevel.destroyBlock(pos, !player.isCreative());
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
				&& TownService.declareHouse(serverLevel, pos, serverPlayer)) {
			// Après le relevé, l'interface du bâtiment : son niveau, ses lits, son amélioration.
			com.ajustor.minecities.network.BuildingNetwork.open(serverLevel, pos, serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		if (level.getBlockState(pos).is(this)) {
			// La plaque a seulement changé de couleur.
			return;
		}
		TownService.plaqueRemoved(level, pos);
	}
}
