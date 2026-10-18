package com.ajustor.minecities.block;

import com.ajustor.minecities.town.Budget;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La boîte à impôts (MC-57) : chaque matin, les adultes de la ville y versent l'impôt. Elle ne s'ouvre
 * qu'au maire et à ses adjoints, qui portent ensuite l'argent au grenier (de quoi payer les gardes).
 * Une seule par ville, posée sur son territoire.
 */
public class TaxBoxBlock extends BaseEntityBlock {
	//? if <26.3 {
	public static final MapCodec<TaxBoxBlock> CODEC = simpleCodec(TaxBoxBlock::new);
	//?}
	private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 10, 13);

	public TaxBoxBlock(Properties properties) {
		super(properties);
	}

	//? if <26.3 {
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	//?}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TaxBoxBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player) {
			Budget.placeTaxBox(serverLevel, pos, player, stack);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof TaxBoxBlockEntity box) {
			boolean allowed = Budget.owner(serverLevel, pos).map(t -> t.canManage(player.getUUID())).orElse(true);
			if (allowed) {
				player.openMenu(box);
			} else {
				player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
						"message.minecities.tax_box.locked"));
			}
		}
		return InteractionResult.SUCCESS;
	}
}
