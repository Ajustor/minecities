package com.ajustor.minecities.citizen;

import com.ajustor.minecities.compat.EntityCompat;
import com.ajustor.minecities.compat.SoundCompat;
import com.ajustor.minecities.town.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Le bûcheron, au billot (une bûche écorcée posée seule). Il abat les arbres à 16 blocs de son
 * billot : tout le tronc d'un coup, à la hache, qui s'use à chaque bûche. Il replante une pousse
 * au pied, ramasse ce que les feuilles laissent tomber (pousses, pommes, bâtons), et porte le bois
 * au grenier.
 */
class LumberjackGoal extends WorkGoal {
	static final int FOREST_RADIUS = 16;
	/** Un arbre, pas une maison de rondins : au plus tant de bûches, et des feuilles naturelles. */
	private static final int MAX_LOGS = 128;
	private static final int SAPLINGS_KEPT = 8;
	private static final int LOAD = 64;
	/** Ce qui vaut la peine d'être ramassé sous les arbres. */
	private static final Predicate<ItemStack> FOREST_LITTER = s -> s.is(ItemTags.SAPLINGS) || s.is(ItemTags.LOGS)
			|| s.is(Items.APPLE) || s.is(Items.STICK);

	private BlockPos tree;
	private boolean delivering;
	private int scan;

	LumberjackGoal(CitizenEntity citizen) {
		super(citizen, Job.LUMBERJACK);
	}

	@Override
	protected void onStop() {
		tree = null;
	}

	private static boolean isSapling(ItemStack stack) {
		return stack.is(ItemTags.SAPLINGS);
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
		int produce = produce(LumberjackGoal::isSapling, SAPLINGS_KEPT);
		if (delivering || produce >= LOAD || citizen.inventoryFull() && produce > 0 || evening() && produce > 0) {
			delivering = !deliver(LumberjackGoal::isSapling, SAPLINGS_KEPT);
			return;
		}
		BlockPos station = employment().station();
		// Ce qui traîne sous les arbres passe avant le prochain arbre.
		if (collectLitter(station, FOREST_RADIUS, FOREST_LITTER)) {
			return;
		}
		if (tree == null || !isTrunkBase(tree)) {
			tree = --scan <= 0 ? nextTree(station) : null;
			if (tree == null) {
				if (scan <= 0) {
					scan = 60;
					if (produce > 0) {
						delivering = true;
					} else {
						rest(IDLE_SHORT);
					}
				}
				return;
			}
		}
		// Il abat à portée de hache, comme un joueur : un tronc sur une butte reste atteignable.
		if (citizen.distanceToSqr(tree.getX() + 0.5, tree.getY() + 0.5, tree.getZ() + 0.5) <= 4.5 * 4.5 || goTo(tree)) {
			citizen.getNavigation().stop();
			fell(tree);
			tree = null;
		}
	}

	/** Le pied d'un tronc : une bûche (non écorcée) posée sur de la terre. */
	private boolean isTrunkBase(BlockPos pos) {
		BlockState state = level().getBlockState(pos);
		return state.is(BlockTags.LOGS) && !isStripped(state) && level().getBlockState(pos.below()).is(BlockTags.DIRT);
	}

	private static boolean isStripped(BlockState state) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()
				.startsWith("stripped_");
	}

	private BlockPos nextTree(BlockPos station) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(station.offset(-FOREST_RADIUS, -4, -FOREST_RADIUS),
				station.offset(FOREST_RADIUS, 6, FOREST_RADIUS))) {
			if (!level().isLoaded(pos) || !isTrunkBase(pos) || !town().contains(level().dimension(), pos)) {
				continue;
			}
			double distance = citizen.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (distance < bestDistance && !trunk(pos).isEmpty()) {
				best = pos.immutable();
				bestDistance = distance;
			}
		}
		return best;
	}

	/**
	 * Les bûches de l'arbre dont {@code base} est le pied, si c'est bien un arbre : des bûches
	 * reliées, pas trop nombreuses, et des feuilles naturelles autour. Vide sinon.
	 */
	private List<BlockPos> trunk(BlockPos base) {
		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(base);
		seen.add(base);
		boolean leaves = false;
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			logs.add(pos);
			if (logs.size() > MAX_LOGS) {
				return List.of();
			}
			for (BlockPos next : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))) {
				if (seen.contains(next)) {
					continue;
				}
				BlockState state = level().getBlockState(next);
				if (state.is(BlockTags.LOGS) && !isStripped(state)) {
					BlockPos immutable = next.immutable();
					seen.add(immutable);
					queue.add(immutable);
				} else if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
					leaves = true;
				}
			}
		}
		return leaves ? logs : List.of();
	}

	private void fell(BlockPos base) {
		ServerLevel level = level();
		List<BlockPos> logs = trunk(base);
		if (logs.isEmpty()) {
			return;
		}
		Block species = level.getBlockState(base).getBlock();
		EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
		citizen.act(Action.CHOP, 30);
		level.playSound(null, base, SoundCompat.AXE_STRIP, SoundSource.BLOCKS, 1.0f, 0.8f);
		for (BlockPos pos : logs) {
			ItemStack axe = citizen.getMainHandItem();
			if (axe.isEmpty()) {
				break;
			}
			BlockState state = level.getBlockState(pos);
			Block.getDrops(state, level, pos, null, citizen, axe).forEach(citizen::carry);
			level.levelEvent(2001, pos, Block.getId(state));
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			axe.hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
		}
		replant(base, species);
	}

	/** Une pousse au pied de l'arbre abattu : de la même essence si possible. */
	private void replant(BlockPos base, Block species) {
		List<ItemStack> inventory = citizen.inventory();
		int slot = -1;
		String wood = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(species).getPath()
				.replace("_log", "").replace("_stem", "");
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.get(i);
			if (isSapling(stack) && stack.getItem() instanceof BlockItem) {
				if (slot < 0 || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
						.startsWith(wood)) {
					slot = i;
				}
			}
		}
		if (slot < 0) {
			return;
		}
		ItemStack sapling = inventory.get(slot);
		BlockState state = ((BlockItem) sapling.getItem()).getBlock().defaultBlockState();
		if (level().getBlockState(base).isAir() && state.canSurvive(level(), base)) {
			sapling.shrink(1);
			level().setBlock(base, state, Block.UPDATE_ALL);
			level().playSound(null, base, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
		}
	}
}
