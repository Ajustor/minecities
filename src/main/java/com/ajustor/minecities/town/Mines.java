package com.ajustor.minecities.town;

import com.ajustor.minecities.citizen.CitizenEntity;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ce qu'on sait d'une galerie de mineur (MC-20) : ce qu'elle peut encore donner (les minerais le long
 * de l'escalier et des couloirs à venir), le chemin de remontée (les marches et le couloir en cours,
 * à vérifier et réparer avant de descendre), qui est encore dessous, et comment reboucher le puits.
 */
public final class Mines {
	private Mines() {
	}

	// Où et jusqu'où

	/** L'entrée d'une galerie neuve : dehors, à deux blocs à l'est du bâtiment, au niveau du sol. */
	public static Mine plan(ServerLevel level, Town town, BlockPos station, BlockPos workshop) {
		Optional<House> house = town.house(workshop);
		int x = house.flatMap(House::max).map(BlockPos::getX).orElse(station.getX() + 4) + 2;
		int z = station.getZ();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		return Mine.open(station, new BlockPos(x, y, z));
	}

	/** La profondeur où l'escalier s'arrête : 40 au plus haut, et seize blocs sous l'entrée au moins. */
	public static int depth(ServerLevel level, Mine mine) {
		return Math.max(level.getMinY() + 8, Math.min(mine.entrance().getY() - 16, 40));
	}

	/** Le bas de l'escalier : atteint, ou prévu (l'escalier descend vers l'est d'un pas par marche). */
	public static BlockPos bottom(ServerLevel level, Mine mine) {
		return mine.bottom().orElseGet(() -> {
			int steps = Math.max(0, mine.entrance().getY() - depth(level, mine));
			return mine.entrance().relative(Direction.EAST, steps).below(steps);
		});
	}

	// Ce qu'elle peut donner

	/** Le nom commun d'un minerai : le fer de surface et celui des profondeurs se comptent ensemble. */
	public static String oreKey(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().replace("deepslate_", "");
	}

	/** L'objet qui montre ce minerai dans l'interface. */
	public static Item oreItem(String key) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(key));
		return item == Items.AIR ? Items.STONE : item;
	}

	/**
	 * Les minerais encore en place le long de ce que le mineur creusera : l'escalier qui reste, puis les
	 * quatre couloirs depuis le bas (deux de haut, et leurs parois), dans le territoire. Une estimation :
	 * un filon peut mener un peu plus loin.
	 */
	public static Map<String, Integer> prospect(ServerLevel level, Town town, Mine mine) {
		Map<String, Integer> ores = new LinkedHashMap<>();
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		BlockPos bottom = bottom(level, mine);
		// L'escalier, de l'entrée au bas : trois de haut à chaque marche.
		int steps = mine.entrance().getY() - bottom.getY();
		for (int k = 1; k <= steps; k++) {
			BlockPos step = mine.entrance().relative(Direction.EAST, k).below(k);
			for (int h = 0; h < 3; h++) {
				count(level, town, step.above(h), ores, seen);
			}
		}
		// Les quatre couloirs.
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int j = 1; j <= Mine.CORRIDOR; j++) {
				BlockPos cell = bottom.relative(d, j);
				for (int h = 0; h < 2; h++) {
					count(level, town, cell.above(h), ores, seen);
				}
			}
		}
		return ores;
	}

	/** Ce bloc et ses voisins : les minerais qu'on y trouve. */
	private static void count(ServerLevel level, Town town, BlockPos pos, Map<String, Integer> ores,
			java.util.Set<BlockPos> seen) {
		for (BlockPos p : new BlockPos[]{pos, pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below()}) {
			if (!seen.add(p) || !level.isLoaded(p) || !town.contains(level.dimension(), p)) {
				continue;
			}
			BlockState state = level.getBlockState(p);
			if (state.is(ConventionalBlockTags.ORES)) {
				ores.merge(oreKey(state), 1, Integer::sum);
			}
		}
	}

	// Le chemin de remontée

	/** Les cases où l'on pose les pieds pour remonter : les marches, puis le couloir en cours, jusqu'à la face. */
	public static List<BlockPos> route(Mine mine) {
		List<BlockPos> route = new ArrayList<>();
		BlockPos stairsEnd = mine.bottom().orElse(mine.face());
		int steps = mine.entrance().getY() - stairsEnd.getY();
		for (int k = 1; k <= steps; k++) {
			route.add(mine.entrance().relative(Direction.EAST, k).below(k));
		}
		if (mine.bottom().isPresent()) {
			for (int j = 1; j <= mine.length(); j++) {
				route.add(mine.bottom().get().relative(mine.heading(), j));
			}
		}
		return route;
	}

	/** Une case où l'on passe : un sol solide, et deux cases sans obstacle ni liquide. */
	public static boolean passable(ServerLevel level, BlockPos feet) {
		if (!level.isLoaded(feet)) {
			return true;
		}
		BlockPos floor = feet.below();
		if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
			return false;
		}
		for (BlockPos p : new BlockPos[]{feet, feet.above()}) {
			BlockState state = level.getBlockState(p);
			if (!state.getCollisionShape(level, p).isEmpty() && !state.is(Blocks.TORCH)
					|| !level.getFluidState(p).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** La première case du chemin qui ne passe plus (un éboulement, du gravier tombé, un trou), s'il y en a une. */
	public static Optional<BlockPos> broken(ServerLevel level, Mine mine) {
		return route(mine).stream().filter(p -> !passable(level, p)).findFirst();
	}

	/**
	 * Répare une case du chemin : il dégage ce qui l'encombre et comble le sol. Faux si elle ne se répare
	 * pas (de l'eau ou de la lave, ou de la roche-mère).
	 */
	public static boolean repair(ServerLevel level, BlockPos feet) {
		for (BlockPos p : new BlockPos[]{feet, feet.above()}) {
			if (!level.getFluidState(p).isEmpty() || level.getBlockState(p).getDestroySpeed(level, p) < 0) {
				return false;
			}
		}
		for (BlockPos p : new BlockPos[]{feet, feet.above()}) {
			BlockState state = level.getBlockState(p);
			if (!state.getCollisionShape(level, p).isEmpty() && !state.is(Blocks.TORCH)) {
				level.destroyBlock(p, false);
			}
		}
		BlockPos floor = feet.below();
		if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
			if (!level.getFluidState(floor).isEmpty() || level.getBlockState(floor).getDestroySpeed(level, floor) < 0) {
				return false;
			}
			level.setBlockAndUpdate(floor, Blocks.COBBLESTONE.defaultBlockState());
		}
		return passable(level, feet);
	}

	// Sous terre, et fermer

	/** L'emprise de la galerie : de l'entrée jusqu'au bout des couloirs, sous la surface. */
	private static AABB underground(ServerLevel level, Mine mine) {
		BlockPos bottom = bottom(level, mine);
		int minX = Math.min(mine.entrance().getX(), bottom.getX() - Mine.CORRIDOR) - 1;
		int maxX = Math.max(mine.entrance().getX(), bottom.getX() + Mine.CORRIDOR) + 2;
		return new AABB(minX, bottom.getY() - 1, bottom.getZ() - Mine.CORRIDOR - 1, maxX, mine.entrance().getY() - 2,
				bottom.getZ() + Mine.CORRIDOR + 2);
	}

	/** Quelqu'un (un citoyen, un joueur) est-il encore au fond ? */
	public static boolean occupied(ServerLevel level, Mine mine) {
		AABB box = underground(level, mine);
		return !level.getEntitiesOfClass(CitizenEntity.class, box, CitizenEntity::isAlive).isEmpty()
				|| !level.getEntitiesOfClass(Player.class, box, p -> p.isAlive() && !p.isSpectator()).isEmpty();
	}

	/** Pourquoi on ne peut pas reboucher ce puits ; vide si on peut. */
	public static Optional<String> closeRefusal(ServerLevel level, Town town, Mine mine) {
		if (mine.closed()) {
			return Optional.of("closed");
		}
		if (!mine.exhausted() && !prospect(level, town, mine).isEmpty()) {
			return Optional.of("not_exhausted");
		}
		if (occupied(level, mine)) {
			return Optional.of("occupied");
		}
		return Optional.empty();
	}

	/** Rebouche le puits : les marches de l'escalier se comblent de pierre, de l'entrée au fond. */
	public static Optional<String> close(ServerLevel level, Town town, Mine mine) {
		Optional<String> refusal = closeRefusal(level, town, mine);
		if (refusal.isPresent()) {
			return refusal;
		}
		BlockPos stairsEnd = mine.bottom().orElse(mine.face());
		int steps = mine.entrance().getY() - stairsEnd.getY();
		for (int k = 0; k <= steps; k++) {
			BlockPos step = mine.entrance().relative(Direction.EAST, k).below(k);
			for (int h = 0; h < 3; h++) {
				BlockPos p = step.above(h);
				// L'entrée se rebouche au ras du sol, pas au-dessus.
				if (k == 0 && h > 0 || !level.getBlockState(p).canBeReplaced()) {
					continue;
				}
				level.setBlockAndUpdate(p, Blocks.COBBLESTONE.defaultBlockState());
			}
		}
		town.putMine(mine.sealed());
		Towns.get(level.getServer()).changed();
		return Optional.empty();
	}
}
