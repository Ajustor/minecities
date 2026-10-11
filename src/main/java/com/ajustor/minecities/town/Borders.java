package com.ajustor.minecities.town;

import com.ajustor.minecities.registry.McItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les frontières des villes, pour les joueurs (brief 05) : en entrant sur un territoire, un titre
 * souhaite la bienvenue ; en sortant, un mot au-dessus de la barre d'action. Un hôtel de ville ou une
 * plaque de maison en main, le contour des territoires voisins se dessine en particules.
 */
public final class Borders {
	/** Toutes les demi-secondes. */
	private static final int INTERVAL = 10;
	/** Le contour se dessine sur tant de blocs de part et d'autre du joueur… */
	private static final double SPAN = 24;
	/** …un point tous les tant de blocs. */
	private static final double STEP = 1.5;

	private static final Map<UUID, UUID> WHERE = new HashMap<>();
	private static int ticks;

	private Borders() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Borders::tick);
	}

	private static void tick(MinecraftServer server) {
		if (++ticks % INTERVAL != 0) {
			return;
		}
		Towns towns = Towns.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			Optional<Town> here = towns.at(level.dimension(), player.blockPosition());
			UUID before = WHERE.get(player.getUUID());
			UUID now = here.map(Town::id).orElse(null);
			if (!java.util.Objects.equals(before, now)) {
				if (now != null) {
					welcome(player, here.get());
				} else if (WHERE.containsKey(player.getUUID())) {
					towns.byId(before).ifPresent(t -> player.sendOverlayMessage(Component.translatable(
							"hud.minecities.leaving", t.name()).withStyle(ChatFormatting.GRAY)));
				}
			}
			WHERE.put(player.getUUID(), now);
			if (player.isHolding(McItems.TOWN_HALL) || player.isHolding(McItems.HOUSE_PLAQUE)) {
				for (Town town : towns.all()) {
					if (town.dimension().equals(level.dimension())) {
						outline(level, player, town);
					}
				}
			}
		}
		WHERE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	private static void welcome(ServerPlayer player, Town town) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("hud.minecities.welcome.sub",
				Component.translatable(town.tier().translationKey()), town.mayorName()).withStyle(ChatFormatting.GRAY)));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("hud.minecities.welcome",
				town.name()).withStyle(ChatFormatting.GOLD)));
	}

	/** Le bout du contour de ce territoire qui passe près du joueur. */
	private static void outline(ServerLevel level, ServerPlayer player, Town town) {
		double dx = player.getX() - (town.hall().getX() + 0.5);
		double dz = player.getZ() - (town.hall().getZ() + 0.5);
		double radius = town.radius();
		if (Math.abs(Math.sqrt(dx * dx + dz * dz) - radius) > SPAN * 1.5) {
			return;
		}
		double facing = Math.atan2(dz, dx);
		double step = STEP / radius;
		int points = (int) (SPAN / STEP);
		for (int k = -points; k <= points; k++) {
			double angle = facing + k * step;
			double x = town.hall().getX() + 0.5 + Math.cos(angle) * radius;
			double z = town.hall().getZ() + 0.5 + Math.sin(angle) * radius;
			int bx = (int) Math.floor(x);
			int bz = (int) Math.floor(z);
			if (!level.isLoaded(new net.minecraft.core.BlockPos(bx, 0, bz))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
			level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, x, y + 0.3, z, 1, 0, 0.2, 0, 0);
		}
	}
}
