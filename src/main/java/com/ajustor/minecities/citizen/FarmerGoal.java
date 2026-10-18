package com.ajustor.minecities.citizen;

import com.ajustor.minecities.compat.EntityCompat;
import com.ajustor.minecities.town.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Le fermier, au composteur. Il travaille les champs autour de son atelier : récolte ce qui est
 * mûr (blé, carottes, pommes de terre, betteraves), replante aussitôt, sème la terre labourée
 * restée nue, et porte la récolte au grenier quand ses poches sont pleines ou que le soir vient.
 * Il coupe aussi la canne à sucre (en laissant le pied, qui repousse) et cueille les citrouilles et
 * les pastèques au bout de leurs tiges ; il ne sème rien à côté d'une tige, où pousse le fruit.
 * Il lui faut une houe, qui s'use à chaque récolte.
 */
class FarmerGoal extends WorkGoal {
	/** Les champs d'un fermier : jusqu'à 16 blocs autour de son composteur, sur 3 de dénivelé. */
	static final int FIELD_RADIUS = 16;
	private static final int FIELD_HEIGHT = 3;
	/** Il garde de quoi replanter : 16 semences de chaque sorte. */
	private static final int SEEDS_KEPT = 16;
	/** Au-delà de tant d'objets récoltés, il va les déposer. */
	private static final int LOAD = 48;

	private BlockPos target;
	private boolean delivering;
	private int scan;

	FarmerGoal(CitizenEntity citizen) {
		super(citizen, Job.FARMER);
	}

	@Override
	protected void onStop() {
		target = null;
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		if (!ensureTool()) {
			return;
		}
		if (delivering || shouldDeliver()) {
			deliver();
			return;
		}
		if (target == null || !worthIt(target)) {
			target = --scan <= 0 ? nextPlot() : null;
			if (target == null) {
				if (scan <= 0) {
					scan = 40;
					if (produce() > 0) {
						delivering = true;
					} else {
						rest(IDLE_SHORT);
					}
				}
				return;
			}
		}
		if (goTo(target)) {
			work(target);
			target = null;
		}
	}

	@Override
	protected void gaveUp(BlockPos pos) {
		target = null;
	}

	/** Un plant mûr à récolter, ou une terre labourée nue à semer (s'il a de quoi). */
	private boolean worthIt(BlockPos pos) {
		if (ripe(pos)) {
			return true;
		}
		return level().getBlockState(pos).isAir() && level().getBlockState(pos.below()).is(Blocks.FARMLAND)
				&& !nextToStem(pos) && seed(null) >= 0;
	}

	/**
	 * Ce qui se récolte en {@code pos} : une culture mûre, une canne à sucre au-dessus de son pied, ou
	 * une citrouille (une pastèque) qu'une tige a fait pousser.
	 */
	private boolean ripe(BlockPos pos) {
		BlockState state = level().getBlockState(pos);
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (state.is(Blocks.SUGAR_CANE)) {
			return level().getBlockState(pos.below()).is(Blocks.SUGAR_CANE);
		}
		if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockState stem = level().getBlockState(pos.relative(side));
				if (stem.getBlock() instanceof AttachedStemBlock
						&& stem.getValue(AttachedStemBlock.FACING) == side.getOpposite()) {
					return true;
				}
			}
		}
		return false;
	}

	/** Une case voisine d'une tige de citrouille ou de pastèque : c'est là que pousse le fruit. */
	private boolean nextToStem(BlockPos pos) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			Block block = level().getBlockState(pos.relative(side)).getBlock();
			if (block instanceof StemBlock || block instanceof AttachedStemBlock) {
				return true;
			}
		}
		return false;
	}

	/** La parcelle la plus proche qui demande du travail : les récoltes d'abord, puis les semis. */
	private BlockPos nextPlot() {
		BlockPos station = employment().station();
		BlockPos best = null;
		boolean bestRipe = false;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(station.offset(-FIELD_RADIUS, -FIELD_HEIGHT, -FIELD_RADIUS),
				station.offset(FIELD_RADIUS, FIELD_HEIGHT, FIELD_RADIUS))) {
			if (!level().isLoaded(pos) || !reachable(pos) || !worthIt(pos) || !town().contains(level().dimension(), pos)) {
				continue;
			}
			boolean ripe = ripe(pos);
			double distance = citizen.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (ripe && !bestRipe || ripe == bestRipe && distance < bestDistance) {
				best = pos.immutable();
				bestRipe = ripe;
				bestDistance = distance;
			}
		}
		return best;
	}

	private void work(BlockPos pos) {
		ServerLevel level = level();
		BlockState state = level.getBlockState(pos);
		EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
		citizen.act(Action.HOE, 15);
		if (state.is(Blocks.SUGAR_CANE) && ripe(pos)) {
			// Toute la tige au-dessus du pied, du haut vers le bas : rien ne tombe par terre.
			BlockPos top = pos;
			while (level.getBlockState(top.above()).is(Blocks.SUGAR_CANE)) {
				top = top.above();
			}
			for (BlockPos cut = top; cut.getY() >= pos.getY(); cut = cut.below()) {
				harvest(cut, level.getBlockState(cut));
			}
		} else if (ripe(pos)) {
			harvest(pos, state);
			if (state.getBlock() instanceof CropBlock) {
				plant(pos, state.getBlock());
			}
		} else if (state.isAir()) {
			plant(pos, null);
		}
	}

	/** Récolte ce bloc : il en emporte ce qu'il donne, et sa houe s'use. */
	private void harvest(BlockPos pos, BlockState state) {
		ServerLevel level = level();
		ItemStack hoe = citizen.getMainHandItem();
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, citizen,
				masterTool(hoe, net.minecraft.world.item.enchantment.Enchantments.FORTUNE, 2));
		if (com.ajustor.minecities.town.Disasters.blighted(town(), level.getGameTime())) {
			// La disette : la récolte est perdue, il ne garde que de quoi ressemer.
			drops = drops.stream().filter(d -> d.getItem() instanceof net.minecraft.world.item.BlockItem b
					&& b.getBlock() == state.getBlock()).limit(1).map(d -> d.copyWithCount(1)).toList();
			citizen.say("blight", net.minecraft.network.chat.Component.translatable("speech.minecities.blight"));
		}
		level.levelEvent(2001, pos, Block.getId(state));
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		drops.forEach(citizen::carry);
		hoe.hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
	}

	/** Sème en {@code pos} : la même culture si {@code crop} est donnée, sinon ce qu'il a. */
	private void plant(BlockPos pos, Block crop) {
		int slot = seed(crop);
		if (slot < 0) {
			return;
		}
		ItemStack seeds = citizen.inventory().get(slot);
		Block block = ((BlockItem) seeds.getItem()).getBlock();
		seeds.shrink(1);
		level().setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
		level().playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
	}

	/** La case de l'inventaire où sont des semences (de cette culture, ou de n'importe laquelle). */
	private int seed(Block crop) {
		List<ItemStack> inventory = citizen.inventory();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.get(i);
			if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock
					&& (crop == null || item.getBlock() == crop)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isSeed(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock;
	}

	private int produce() {
		return produce(FarmerGoal::isSeed, SEEDS_KEPT);
	}

	private boolean shouldDeliver() {
		int produce = produce();
		return produce >= LOAD || citizen.inventoryFull() && produce > 0 || evening() && produce > 0;
	}

	/** Porte la récolte au grenier, en gardant ses semences de réserve. */
	private void deliver() {
		if (deliver(FarmerGoal::isSeed, SEEDS_KEPT)) {
			delivering = false;
		}
	}
}
