package com.ajustor.minecities.citizen;

import com.ajustor.minecities.compat.EntityCompat;
import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Mine;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.Towns;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Le mineur, au treuil de mine (brief 11). Il creuse une vraie galerie, qui se voit et se visite : un
 * escalier qui part à l'est du bâtiment et descend jusqu'à la profondeur des minerais, puis des
 * couloirs droits depuis le bas de l'escalier, l'un après l'autre. Il suit les filons qu'il croise,
 * pose une torche de temps en temps (il les fait avec le charbon qu'il trouve et des bâtons du
 * grenier), garde un peu de pierre pour les bâtisseurs et rapporte les minerais au grenier.
 *
 * <p>Prudent : il ne perce pas dans l'eau ni la lave, ne creuse ni sous les maisons ni hors du
 * territoire, et laisse la roche-mère.
 */
class MinerGoal extends WorkGoal {
	/** Il ne garde que tant de pierre : le reste reste au fond comme déblais. */
	private static final int STONE_KEPT = 64;
	/** Un filon est suivi sur tant de blocs au plus. */
	private static final int VEIN = 16;
	/** Bloqué sur le chemin de la face si longtemps, il y va par l'échelle (il s'y téléporte). */
	private static final int STUCK = 300;

	private int walking;
	/** Dans combien de pas il refait le compte des minerais à portée. */
	private int prospectIn;
	/** Les minerais extraits depuis le dernier pas enregistré. */
	private final java.util.Map<String, Integer> extracted = new java.util.HashMap<>();

	MinerGoal(CitizenEntity citizen) {
		super(citizen, Job.MINER);
	}

	@Override
	public void start() {
		super.start();
		walking = 0;
	}

	@Override
	public void tick() {
		if (!stationStands()) {
			rest(IDLE_SHORT);
			return;
		}
		// Sans outil, il va le chercher (ou attend qu'on en mette un : ensureTool s'en charge).
		if (!ensureTool()) {
			return;
		}
		// Les poches pleines, ou le soir venu : il remonte livrer.
		if (citizen.inventoryFull() || evening() && produce(this::kept, 16) > 0) {
			if (deliver(this::kept, 16)) {
				// Au grenier, il reprend des bâtons pour ses torches.
				if (citizen.inventory().stream().noneMatch(s -> s.is(Items.STICK))) {
					find(supplies(), s -> s.is(Items.STICK)).flatMap(this::container)
							.ifPresent(c -> citizen.carry(Stock.take(c, s -> s.is(Items.STICK), 8)));
				}
				rest(40);
			}
			return;
		}
		Town town = town();
		Mine mine = town.mine(employment().station()).orElseGet(() -> open(town));
		if (mine == null) {
			rest(IDLE_LONG);
			return;
		}
		if (mine.closed()) {
			citizen.complain("mine", Component.translatable("message.minecities.mine.closed", citizen.getName()),
					Component.translatable("speech.minecities.mine_exhausted"));
			rest(IDLE_LONG);
			return;
		}
		// De temps en temps, il refait le compte : plus un minerai à portée, la galerie est épuisée.
		if (!mine.exhausted() && --prospectIn <= 0) {
			prospectIn = 20;
			if (com.ajustor.minecities.town.Mines.prospect(level(), town, mine).isEmpty()) {
				mine = mine.drained();
				town.putMine(mine);
				Towns.get(level().getServer()).changed();
			}
		}
		if (mine.exhausted()) {
			citizen.complain("mine", Component.translatable("message.minecities.mine.exhausted", citizen.getName()),
					Component.translatable("speech.minecities.mine_exhausted"));
			rest(IDLE_LONG);
			return;
		}
		// Avant de descendre, le chemin de remontée : il répare la première marche qui ne passe plus, et ne
		// s'engage pas s'il ne peut pas.
		Optional<BlockPos> broken = com.ajustor.minecities.town.Mines.broken(level(), mine);
		if (broken.isPresent()) {
			if (!goTo(broken.get())) {
				return;
			}
			if (!com.ajustor.minecities.town.Mines.repair(level(), broken.get())) {
				citizen.complain("mine_blocked", Component.translatable("message.minecities.mine.blocked",
						citizen.getName(), broken.get().toShortString()),
						Component.translatable("speech.minecities.mine_blocked"));
				rest(IDLE_LONG);
			}
			return;
		}
		// Il descend jusqu'à la face.
		if (!goTo(mine.face())) {
			if (++walking > STUCK) {
				walking = 0;
				citizen.snapTo(mine.face().getX() + 0.5, mine.face().getY(), mine.face().getZ() + 0.5,
						citizen.getYRot(), citizen.getXRot());
				citizen.getNavigation().stop();
			}
			return;
		}
		walking = 0;
		town.putMine(dig(town, mine).extracted(extracted));
		extracted.clear();
		Towns.get(level().getServer()).changed();
		rest(12);
	}

	/** Ce qu'il garde en remontant : de quoi faire et poser des torches. */
	private boolean kept(ItemStack stack) {
		return stack.is(Items.TORCH) || stack.is(Items.COAL) || stack.is(Items.STICK);
	}

	/** Ouvre la galerie : l'entrée est dehors, à deux blocs à l'est du bâtiment, au niveau du sol. */
	private Mine open(Town town) {
		Mine mine = com.ajustor.minecities.town.Mines.plan(level(), town, employment().station(), employment().workshop());
		town.putMine(mine);
		return mine;
	}

	/** La profondeur où l'escalier s'arrête. */
	private int depth(Mine mine) {
		return com.ajustor.minecities.town.Mines.depth(level(), mine);
	}

	/** Creuse le pas suivant (ou change de couloir s'il ne faut pas) ; renvoie la galerie à jour. */
	private Mine dig(Town town, Mine mine) {
		if (mine.descending() && mine.face().getY() <= depth(mine)) {
			return mine.reachedBottom();
		}
		if (!mine.descending() && mine.length() >= Mine.CORRIDOR) {
			return mine.nextCorridor();
		}
		BlockPos target = mine.next();
		List<BlockPos> column = new ArrayList<>(List.of(target, target.above()));
		if (mine.descending()) {
			column.add(target.above(2));
		}
		if (!safe(town, column)) {
			return mine.descending() ? mine.reachedBottom() : mine.nextCorridor();
		}
		citizen.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5);
		citizen.act(Action.CHOP, 15);
		EntityCompat.swing(citizen, InteractionHand.MAIN_HAND);
		for (BlockPos pos : column) {
			mineBlock(pos);
			for (Direction d : Direction.values()) {
				BlockPos side = pos.relative(d);
				if (level().getBlockState(side).is(ConventionalBlockTags.ORES)) {
					followVein(town, side);
				}
			}
		}
		// Le sol de la galerie : il comble un trou sous ses pieds, pour qu'on puisse y marcher.
		BlockPos floor = target.below();
		if (!level().getBlockState(floor).isFaceSturdy(level(), floor, Direction.UP)
				&& level().getBlockState(floor).canBeReplaced()) {
			level().setBlockAndUpdate(floor, Blocks.COBBLESTONE.defaultBlockState());
		}
		Mine next = mine.advanced(target);
		if (next.length() % Mine.TORCH_SPACING == 0) {
			torch(target);
		}
		return next;
	}

	/** On peut creuser là : dans le territoire, pas sous une maison, sans liquide autour, sans roche-mère. */
	private boolean safe(Town town, List<BlockPos> column) {
		for (BlockPos pos : column) {
			if (!town.contains(level().dimension(), pos)) {
				return false;
			}
			for (House house : town.houses()) {
				if (house.min().isPresent() && house.max().isPresent()
						&& pos.getX() >= house.min().get().getX() - 1 && pos.getX() <= house.max().get().getX() + 1
						&& pos.getZ() >= house.min().get().getZ() - 1 && pos.getZ() <= house.max().get().getZ() + 1) {
					return false;
				}
			}
			BlockState state = level().getBlockState(pos);
			if (state.getDestroySpeed(level(), pos) < 0) {
				return false;
			}
			for (Direction d : Direction.values()) {
				if (!level().getFluidState(pos.relative(d)).isEmpty() || !level().getFluidState(pos).isEmpty()) {
					return false;
				}
			}
		}
		return true;
	}

	/** Suit le filon dont ce bloc fait partie, s'il est sûr de le creuser. */
	private void followVein(Town town, BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
		int mined = 0;
		while (!queue.isEmpty() && mined < VEIN) {
			BlockPos pos = queue.poll();
			if (!seen.add(pos) || !level().getBlockState(pos).is(ConventionalBlockTags.ORES)
					|| !safe(town, List.of(pos))) {
				continue;
			}
			mineBlock(pos);
			mined++;
			for (Direction d : Direction.values()) {
				queue.add(pos.relative(d));
			}
		}
	}

	/** Abat un bloc avec sa pioche et en garde ce qu'il donne (la pierre, jusqu'à un certain point). */
	private void mineBlock(BlockPos pos) {
		// Sa pioche vient de casser : il ne retire plus rien (le bloc disparaîtrait sans rien laisser).
		if (citizen.getMainHandItem().isEmpty()) {
			return;
		}
		BlockState state = level().getBlockState(pos);
		if (state.isAir() || state.canBeReplaced() && level().getFluidState(pos).isEmpty()) {
			if (!state.isAir()) {
				level().removeBlock(pos, false);
			}
			return;
		}
		if (state.is(ConventionalBlockTags.ORES)) {
			extracted.merge(com.ajustor.minecities.town.Mines.oreKey(state), 1, Integer::sum);
		}
		ItemStack tool = citizen.getMainHandItem();
		List<ItemStack> drops = Block.getDrops(state, level(), pos, level().getBlockEntity(pos), citizen,
				masterTool(tool, net.minecraft.world.item.enchantment.Enchantments.FORTUNE, 2));
		level().levelEvent(2001, pos, Block.getId(state));
		level().removeBlock(pos, false);
		tool.hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
		int stone = citizen.inventory().stream().filter(s -> s.is(Items.COBBLESTONE)
				|| s.is(Items.COBBLED_DEEPSLATE)).mapToInt(ItemStack::getCount).sum();
		for (ItemStack drop : drops) {
			boolean rubble = drop.is(Items.COBBLESTONE) || drop.is(Items.COBBLED_DEEPSLATE) || drop.is(Items.DIRT)
					|| drop.is(Items.GRAVEL) || drop.is(Items.TUFF) || drop.is(Items.DIORITE) || drop.is(Items.ANDESITE)
					|| drop.is(Items.GRANITE);
			if (rubble && (stone >= STONE_KEPT || !drop.is(Items.COBBLESTONE))) {
				// Des déblais : ils restent au fond.
				continue;
			}
			if (drop.is(Items.COBBLESTONE)) {
				stone += drop.getCount();
			}
			// Ce qui ne tient pas dans ses poches tombe à ses pieds, et il le ramasse en passant.
			citizen.carry(drop);
		}
	}

	/** Une torche au sol tous les quelques pas, faite au besoin d'un charbon et d'un bâton. */
	private void torch(BlockPos at) {
		List<ItemStack> inventory = citizen.inventory();
		Optional<ItemStack> torches = inventory.stream().filter(s -> s.is(Items.TORCH)).findFirst();
		if (torches.isEmpty()) {
			Optional<ItemStack> coal = inventory.stream().filter(s -> s.is(Items.COAL)).findFirst();
			Optional<ItemStack> stick = inventory.stream().filter(s -> s.is(Items.STICK)).findFirst();
			if (coal.isEmpty() || stick.isEmpty()) {
				return;
			}
			coal.get().shrink(1);
			stick.get().shrink(1);
			citizen.carry(new ItemStack(Items.TORCH, 4));
			torches = inventory.stream().filter(s -> s.is(Items.TORCH)).findFirst();
		}
		BlockState torch = Blocks.TORCH.defaultBlockState();
		if (torches.isPresent() && level().getBlockState(at).isAir() && torch.canSurvive(level(), at)) {
			level().setBlockAndUpdate(at, torch);
			torches.get().shrink(1);
		}
	}
}
