package com.ajustor.minecities.citizen;

import com.ajustor.minecities.compat.EntityCompat;
import com.ajustor.minecities.town.Field;
import com.ajustor.minecities.town.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Le fermier, au composteur. Il travaille les champs autour de son atelier : récolte ce qui est
 * mûr (blé, carottes, pommes de terre, betteraves), replante aussitôt, sème la terre labourée
 * restée nue, et porte la récolte au grenier quand ses poches sont pleines ou que le soir vient.
 * Il coupe aussi la canne à sucre (en laissant le pied, qui repousse) et cueille les citrouilles et
 * les pastèques au bout de leurs tiges ; il ne sème rien à côté d'une tige, où pousse le fruit.
 * Il lui faut une houe, qui s'use à chaque récolte.
 *
 * <p>Il travaille aussi les champs que le maire a tracés au piquet de champ ({@link Field}), où qu'ils
 * soient dans la ville : il y arrache l'herbe, laboure la terre nue et y sème la culture choisie (une
 * autre culture déjà en place est récoltée à maturité, puis remplacée), en allant chercher les semences
 * au grenier s'il n'en a pas.
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
	/** Les semences qu'il va chercher pour un champ (null : rien à chercher). */
	private Item fetching;
	/** Les semences qu'un champ demande et qu'il n'a pas (recompté à chaque recherche). */
	private final Set<Item> missing = new LinkedHashSet<>();

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
		if (fetching != null) {
			fetchSeeds();
			return;
		}
		if (target == null || !worthIt(target)) {
			target = --scan <= 0 ? nextPlot() : null;
			if (target == null) {
				if (scan <= 0) {
					scan = 40;
					if (produce() > 0) {
						delivering = true;
					} else if (!missing.isEmpty()) {
						fetching = missing.iterator().next();
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

	/**
	 * Un plant mûr à récolter, une terre labourée nue à semer (s'il a de quoi), ou, dans un champ tracé,
	 * une terre à labourer (et l'herbe qui la couvre).
	 */
	private boolean worthIt(BlockPos pos) {
		if (ripe(pos)) {
			return true;
		}
		BlockState here = level().getBlockState(pos);
		Optional<Field> field = town().fieldAt(pos);
		if (!here.isAir() && !(field.isPresent() && clearable(here))) {
			return false;
		}
		if (nextToStem(pos)) {
			return false;
		}
		BlockState below = level().getBlockState(pos.below());
		boolean soil = below.is(Blocks.FARMLAND) || field.isPresent() && tillable(below);
		return soil && seed(wanted(field)) >= 0;
	}

	/** Le plant que veut ce champ ; null : ce qu'il a (hors champ, ou un champ « au choix »). */
	private static Block wanted(Optional<Field> field) {
		return field.flatMap(f -> f.crop().plant()).orElse(null);
	}

	/** Une terre que la houe laboure. */
	private static boolean tillable(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.DIRT_PATH);
	}

	/** Ce qui pousse sur une terre à labourer et s'arrache à la main : herbes, fleurs, neige. */
	private static boolean clearable(BlockState state) {
		return state.canBeReplaced() && state.getFluidState().isEmpty();
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

	/**
	 * La parcelle la plus proche qui demande du travail, autour de sa ferme ou dans un champ tracé : les
	 * récoltes d'abord, puis les champs tracés, puis le reste. Note au passage les semences qui manquent.
	 */
	private BlockPos nextPlot() {
		BlockPos station = employment().station();
		missing.clear();
		BlockPos[] best = {null};
		int[] bestRank = {-1};
		double[] bestDistance = {Double.MAX_VALUE};
		java.util.function.Consumer<BlockPos> consider = pos -> {
			if (!level().isLoaded(pos) || !worthIt(pos) || !reachable(pos)
					|| !town().contains(level().dimension(), pos)) {
				return;
			}
			// Les récoltes d'abord ; puis les champs tracés (les semences qu'il est allé chercher pour eux
			// n'iront pas ailleurs) ; puis le reste.
			int rank = ripe(pos) ? 2 : town().fieldAt(pos).isPresent() ? 1 : 0;
			double distance = citizen.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (rank > bestRank[0] || rank == bestRank[0] && distance < bestDistance[0]) {
				best[0] = pos.immutable();
				bestRank[0] = rank;
				bestDistance[0] = distance;
			}
		};
		for (BlockPos pos : BlockPos.betweenClosed(station.offset(-FIELD_RADIUS, -FIELD_HEIGHT, -FIELD_RADIUS),
				station.offset(FIELD_RADIUS, FIELD_HEIGHT, FIELD_RADIUS))) {
			consider.accept(pos);
		}
		for (Field field : town().fields()) {
			Optional<Item> seed = field.crop().seed();
			boolean lacking = seed.isPresent() && seed(field.crop().plant().orElseThrow()) < 0;
			for (BlockPos pos : BlockPos.betweenClosed(field.min().offset(0, -Field.SLOPE, 0),
					field.max().offset(0, Field.SLOPE + 1, 0))) {
				consider.accept(pos);
				// Une terre à semer, mais pas la semence : il ira la chercher.
				if (lacking && !missing.contains(seed.get()) && level().isLoaded(pos) && sowable(pos)) {
					missing.add(seed.get());
				}
			}
		}
		return best[0];
	}

	/** Une case de champ prête pour un semis (labourée ou à labourer), semence mise à part. */
	private boolean sowable(BlockPos pos) {
		BlockState here = level().getBlockState(pos);
		if (!here.isAir() && !clearable(here) || nextToStem(pos)) {
			return false;
		}
		BlockState below = level().getBlockState(pos.below());
		return below.is(Blocks.FARMLAND) || tillable(below);
	}

	/** Va chercher au grenier les semences d'un champ ; s'il n'y en a nulle part, le maire est prévenu. */
	private void fetchSeeds() {
		Item seed = fetching;
		Optional<BlockPos> source = find(supplies(), s -> s.is(seed));
		if (source.isEmpty()) {
			citizen.complain("seeds", Component.translatable("message.minecities.citizen.needs_seeds",
					citizen.getName(), Component.translatable(seed.getDescriptionId())),
					Component.translatable("speech.minecities.needs_tool",
							Component.translatable(seed.getDescriptionId())));
			fetching = null;
			rest(IDLE_TOOL);
			return;
		}
		if (goTo(source.get())) {
			container(source.get()).ifPresent(c -> {
				ItemStack taken = take(List.of(c), s -> s.is(seed), SEEDS_KEPT);
				if (!taken.isEmpty()) {
					citizen.carry(taken);
					citizen.satisfied("seeds");
				}
			});
			fetching = null;
		}
	}

	private void work(BlockPos pos) {
		ServerLevel level = level();
		BlockState state = level.getBlockState(pos);
		Optional<Field> field = town().fieldAt(pos);
		Block wanted = wanted(field);
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
			if (state.getBlock() instanceof CropBlock && !nextToStem(pos)) {
				// La même culture ; dans un champ tracé, celle qu'il veut (sans la semence, la terre attend
				// qu'il aille la chercher).
				plant(pos, wanted != null ? wanted : state.getBlock());
			}
		} else if (state.isAir() || field.isPresent() && clearable(state)) {
			if (!state.isAir()) {
				level.destroyBlock(pos, false, citizen);
			}
			BlockState below = level.getBlockState(pos.below());
			if (field.isPresent() && tillable(below)) {
				// Il laboure, puis sème aussitôt : une terre labourée nue redevient terre.
				level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
				level.playSound(null, pos.below(), com.ajustor.minecities.compat.SoundCompat.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
				citizen.getMainHandItem().hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
			}
			if (level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
				plant(pos, wanted);
			}
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

	/**
	 * La case de l'inventaire où sont des semences : de ce plant (culture ou tige de citrouille, de
	 * pastèque), ou de n'importe quelle culture.
	 */
	private int seed(Block crop) {
		List<ItemStack> inventory = citizen.inventory();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.get(i);
			if (stack.getItem() instanceof BlockItem item
					&& (crop == null ? item.getBlock() instanceof CropBlock : item.getBlock() == crop)) {
				return i;
			}
		}
		return -1;
	}

	/** Des semences, qu'il garde pour ressemer : celles des cultures et des tiges. */
	private static boolean isSeed(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item
				&& (item.getBlock() instanceof CropBlock || item.getBlock() instanceof StemBlock);
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
