package com.ajustor.minecities.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Un poste de travail du mod (brief 11), pour les métiers que le vanilla n'a pas prévus : l'établi
 * du joaillier, le four du verrier. Un bloc tourné vers celui qui le pose, avec son modèle ; c'est
 * le relevé du bâtiment qui en fait un emploi.
 */
public class WorkstationBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<WorkstationBlock> CODEC = simpleCodec(WorkstationBlock::new);
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);

	public WorkstationBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
