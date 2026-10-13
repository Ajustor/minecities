package com.ajustor.minecities.block;

import com.ajustor.minecities.town.TownService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * La mairie annexe (brief 03) : une grande ville en pose une ou plusieurs dans ses quartiers
 * éloignés. Chacune étend le territoire autour d'elle et garde un grenier où les citoyens du
 * quartier mangent. Le maire la pose sur le territoire, assez loin de l'hôtel de ville.
 */
public class TownAnnexBlock extends BaseEntityBlock {
	public static final MapCodec<TownAnnexBlock> CODEC = simpleCodec(TownAnnexBlock::new);

	public TownAnnexBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HorizontalDirectionalBlock.FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
				context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(HorizontalDirectionalBlock.FACING,
				rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TownAnnexBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player) {
			TownService.placeAnnex(serverLevel, pos, player, stack);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
				&& player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof TownAnnexBlockEntity annex) {
			// L'annexe est un grenier : le rang du joueur doit le lui ouvrir.
			boolean allowed = com.ajustor.minecities.town.Towns.get(serverLevel.getServer()).all().stream()
					.filter(t -> t.dimension().equals(level.dimension()) && t.annexes().contains(pos)).findFirst()
					.map(t -> com.ajustor.minecities.town.TownService.mayOpenGranary(serverPlayer, t)).orElse(true);
			if (allowed) {
				player.openMenu(annex);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
