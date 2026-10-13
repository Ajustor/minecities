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
 * Un poste de travail du mod (brief 11) : l'établi du joaillier, le four du verrier, et depuis la
 * v1.8 un poste pour chaque métier, à côté du bloc vanilla. Un bloc tourné vers celui qui le pose,
 * avec son modèle ; c'est le relevé du bâtiment qui en fait un emploi. Un clic droit ouvre son
 * interface.
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

	/** Un clic droit ouvre l'interface du poste : le métier, son travailleur, ce qui manque. */
	@Override
	protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
			BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
		if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
				&& player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			return com.ajustor.minecities.network.StationNetwork.open(serverLevel, pos, serverPlayer)
					? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS;
		}
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
