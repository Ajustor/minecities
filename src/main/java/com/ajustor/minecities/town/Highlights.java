package com.ajustor.minecities.town;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Un bâtiment qui luit (MC-13) : cliqué sur la carte du territoire, il s'entoure quelques secondes d'un
 * contour d'étincelles, qu'on voit de loin, comme un citoyen qu'on cherche luit.
 */
public final class Highlights {
	/** Il luit dix secondes. */
	public static final int TICKS = 200;

	private record Highlight(ResourceKey<Level> dimension, BlockPos min, BlockPos max, long until) {
	}

	private static final List<Highlight> ACTIVE = new ArrayList<>();
	private static int ticks;

	private Highlights() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Highlights::tick);
	}

	/** Ce bâtiment luit, de {@code min} à {@code max}. */
	public static void add(ServerLevel level, BlockPos min, BlockPos max) {
		ACTIVE.removeIf(h -> h.min().equals(min) && h.max().equals(max));
		ACTIVE.add(new Highlight(level.dimension(), min.immutable(), max.immutable(), level.getGameTime() + TICKS));
	}

	/** Les bâtiments qui luisent encore (pour les essais). */
	public static int active() {
		return ACTIVE.size();
	}

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty() || ++ticks % 10 != 0) {
			return;
		}
		ACTIVE.removeIf(h -> {
			ServerLevel level = server.getLevel(h.dimension());
			if (level == null || level.getGameTime() > h.until()) {
				return true;
			}
			outline(level, h.min(), h.max());
			return false;
		});
	}

	/** Les arêtes du bâtiment, en étincelles : le tour du toit, et les quatre coins. */
	private static void outline(ServerLevel level, BlockPos min, BlockPos max) {
		double top = max.getY() + 1.2;
		for (int x = min.getX(); x <= max.getX() + 1; x++) {
			spark(level, x, top, min.getZ());
			spark(level, x, top, max.getZ() + 1);
		}
		for (int z = min.getZ(); z <= max.getZ() + 1; z++) {
			spark(level, min.getX(), top, z);
			spark(level, max.getX() + 1, top, z);
		}
		for (int y = min.getY(); y <= max.getY() + 1; y++) {
			spark(level, min.getX(), y, min.getZ());
			spark(level, max.getX() + 1, y, min.getZ());
			spark(level, min.getX(), y, max.getZ() + 1);
			spark(level, max.getX() + 1, y, max.getZ() + 1);
		}
	}

	private static void spark(ServerLevel level, double x, double y, double z) {
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
	}
}
