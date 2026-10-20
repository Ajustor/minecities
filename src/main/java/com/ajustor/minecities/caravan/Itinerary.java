package com.ajustor.minecities.caravan;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * L'itinéraire d'une caravane (MC-80) : une ligne brisée de points au sol, d'un hôtel de ville à
 * l'autre, par les points de passage posés au bâton d'itinéraire. Entre deux points, quand le terrain
 * est chargé, on cherche un trajet praticable qui préfère les chemins et les routes pavées.
 */
public final class Itinerary {
	/** Au-delà de cette distance entre deux points, on ne cherche pas de trajet : on va tout droit. */
	private static final int MAX_SEGMENT = 160;
	/** La recherche ne s'écarte pas plus que ça de la ligne droite. */
	private static final int MARGIN = 24;
	/** Combien de cases la recherche examine au plus. */
	private static final int MAX_NODES = 40000;

	private Itinerary() {
	}

	/** La longueur, en blocs, de cette ligne brisée. */
	public static double length(List<BlockPos> path) {
		double length = 0;
		for (int i = 1; i < path.size(); i++) {
			length += Math.sqrt(path.get(i - 1).distSqr(path.get(i)));
		}
		return length;
	}

	/** Le point à cette distance du départ, le long de la ligne brisée. */
	public static Vec3 at(List<BlockPos> path, double distance) {
		if (path.isEmpty()) {
			return Vec3.ZERO;
		}
		double left = Math.max(0, distance);
		for (int i = 1; i < path.size(); i++) {
			BlockPos a = path.get(i - 1);
			BlockPos b = path.get(i);
			double segment = Math.sqrt(a.distSqr(b));
			if (left <= segment && segment > 0) {
				double t = left / segment;
				return new Vec3(a.getX() + 0.5 + (b.getX() - a.getX()) * t, a.getY() + (b.getY() - a.getY()) * t,
						a.getZ() + 0.5 + (b.getZ() - a.getZ()) * t);
			}
			left -= segment;
		}
		BlockPos last = path.getLast();
		return new Vec3(last.getX() + 0.5, last.getY(), last.getZ() + 0.5);
	}

	/** La direction de la marche à cette distance (vers l'avant), en degrés comme le lacet d'une entité. */
	public static float yaw(List<BlockPos> path, double distance) {
		Vec3 here = at(path, distance);
		Vec3 ahead = at(path, distance + 1.5);
		if (ahead.distanceToSqr(here) < 1.0E-4) {
			ahead = here;
			here = at(path, distance - 1.5);
		}
		return (float) (Math.toDegrees(Math.atan2(ahead.z - here.z, ahead.x - here.x)) - 90);
	}

	/** Le sol d'une colonne (le bloc où l'on pose le pied), ou la hauteur donnée si la colonne n'est pas chargée. */
	public static BlockPos ground(ServerLevel level, int x, int z, int fallback) {
		if (!level.isLoaded(new BlockPos(x, fallback, z))) {
			return new BlockPos(x, fallback, z);
		}
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/**
	 * Le trajet complet par ces points : entre deux points voisins, un trajet praticable si le terrain
	 * est chargé, sinon une ligne droite.
	 */
	public static List<BlockPos> route(ServerLevel level, List<BlockPos> points) {
		List<BlockPos> path = new ArrayList<>();
		for (int i = 0; i < points.size(); i++) {
			BlockPos point = points.get(i);
			if (i == 0) {
				path.add(point);
				continue;
			}
			List<BlockPos> segment = segment(level, points.get(i - 1), point);
			path.addAll(segment.subList(1, segment.size()));
		}
		return simplify(path);
	}

	/** Un trajet de a à b (a et b compris). */
	static List<BlockPos> segment(ServerLevel level, BlockPos a, BlockPos b) {
		if (a.distManhattan(b) > MAX_SEGMENT * 2 || !level.isLoaded(a) || !level.isLoaded(b)) {
			return List.of(a, b);
		}
		int minX = Math.min(a.getX(), b.getX()) - MARGIN;
		int maxX = Math.max(a.getX(), b.getX()) + MARGIN;
		int minZ = Math.min(a.getZ(), b.getZ()) - MARGIN;
		int maxZ = Math.max(a.getZ(), b.getZ()) + MARGIN;
		record Node(int x, int z, double cost, double estimate) {
		}
		Map<Long, Double> best = new HashMap<>();
		Map<Long, Long> came = new HashMap<>();
		Map<Long, Integer> heights = new HashMap<>();
		PriorityQueue<Node> open = new PriorityQueue<>((p, q) -> Double.compare(p.cost + p.estimate, q.cost + q.estimate));
		long start = key(a.getX(), a.getZ());
		long goal = key(b.getX(), b.getZ());
		best.put(start, 0.0);
		heights.put(start, a.getY());
		open.add(new Node(a.getX(), a.getZ(), 0, Math.sqrt(a.distSqr(b))));
		int seen = 0;
		while (!open.isEmpty() && seen++ < MAX_NODES) {
			Node node = open.poll();
			long here = key(node.x, node.z);
			if (here == goal) {
				List<BlockPos> path = new ArrayList<>();
				for (long k = goal; ; k = came.get(k)) {
					path.add(new BlockPos(x(k), heights.get(k), z(k)));
					if (k == start) {
						break;
					}
				}
				Collections.reverse(path);
				return path;
			}
			if (node.cost > best.getOrDefault(here, Double.MAX_VALUE)) {
				continue;
			}
			int y = heights.get(here);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					int nx = node.x + dx;
					int nz = node.z + dz;
					if (nx < minX || nx > maxX || nz < minZ || nz > maxZ || !level.isLoaded(new BlockPos(nx, y, nz))) {
						continue;
					}
					int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz);
					if (Math.abs(ny - y) > 1) {
						continue;
					}
					double step = (dx != 0 && dz != 0 ? 1.414 : 1.0) * terrain(level.getBlockState(new BlockPos(nx, ny - 1, nz)))
							+ (ny != y ? 0.5 : 0);
					long next = key(nx, nz);
					double cost = node.cost + step;
					if (cost < best.getOrDefault(next, Double.MAX_VALUE)) {
						best.put(next, cost);
						came.put(next, here);
						heights.put(next, ny);
						double estimate = Math.sqrt((double) (b.getX() - nx) * (b.getX() - nx) + (double) (b.getZ() - nz) * (b.getZ() - nz)) * 0.4;
						open.add(new Node(nx, nz, cost, estimate));
					}
				}
			}
		}
		return List.of(a, b);
	}

	/**
	 * Ce que coûte un pas sur ce bloc : un chemin, des pavés, des pierres taillées ou des briques sont
	 * des routes, bien moins chères que l'herbe ; l'eau et la lave, presque infranchissables.
	 */
	static double terrain(BlockState ground) {
		if (ground.is(Blocks.DIRT_PATH) || ground.is(Blocks.COBBLESTONE) || ground.is(Blocks.STONE_BRICKS)
				|| ground.is(Blocks.BRICKS) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.SMOOTH_STONE)
				|| ground.is(Blocks.POLISHED_ANDESITE) || ground.is(Blocks.MOSSY_COBBLESTONE)
				|| ground.is(BlockTags.SLABS) || ground.is(BlockTags.PLANKS)) {
			return 0.4;
		}
		if (!ground.getFluidState().isEmpty()) {
			return 25;
		}
		if (ground.is(BlockTags.LEAVES) || ground.is(BlockTags.LOGS)) {
			return 6;
		}
		return 1;
	}

	/** Retire les points intermédiaires d'une même ligne droite (même direction, même hauteur). */
	static List<BlockPos> simplify(List<BlockPos> path) {
		if (path.size() < 3) {
			return path;
		}
		List<BlockPos> out = new ArrayList<>();
		out.add(path.getFirst());
		for (int i = 1; i < path.size() - 1; i++) {
			BlockPos p = path.get(i - 1);
			BlockPos q = path.get(i);
			BlockPos r = path.get(i + 1);
			boolean straight = q.getX() - p.getX() == r.getX() - q.getX() && q.getZ() - p.getZ() == r.getZ() - q.getZ()
					&& p.getY() == q.getY() && q.getY() == r.getY();
			if (!straight) {
				out.add(q);
			}
		}
		out.add(path.getLast());
		return out;
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static int x(long key) {
		return (int) (key >> 32);
	}

	private static int z(long key) {
		return (int) key;
	}
}
