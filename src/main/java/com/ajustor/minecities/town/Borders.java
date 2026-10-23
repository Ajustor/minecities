package com.ajustor.minecities.town;

import com.ajustor.minecities.registry.McItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
 * plaque de maison ou un plan en main, le contour des territoires voisins se dessine en particules :
 * on voit si le bâtiment tombe dedans.
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
			if (player.isHolding(McItems.TOWN_HALL) || player.isHolding(McItems.HOUSE_PLAQUE)
					|| player.isHolding(McItems.PLAN)) {
				for (Town town : towns.all()) {
					if (town.dimension().equals(level.dimension())) {
						outline(level, player, town, town.hall(), town.radius());
						for (BlockPos annex : town.activeAnnexes()) {
							outline(level, player, town, annex, Town.ANNEX_RADIUS);
						}
					}
				}
			}
		}
		WHERE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	private static void welcome(ServerPlayer player, Town town) {
		title(player, Component.translatable("hud.minecities.welcome", town.name()).withStyle(ChatFormatting.GOLD),
				Component.translatable("hud.minecities.welcome.sub", Component.translatable(town.tier().translationKey()),
						town.mayorName()).withStyle(ChatFormatting.GRAY));
	}

	/** Un titre et un sous-titre au milieu de l'écran du joueur. */
	public static void title(ServerPlayer player, Component title, Component subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	/** Le joueur est-il sur ce territoire (pour ne pas lui souhaiter la bienvenue juste après) ? */
	public static void arrived(ServerPlayer player, Town town) {
		WHERE.put(player.getUUID(), town.id());
	}

	/**
	 * Le bout du contour de ce cercle (l'hôtel de ville ou une annexe) qui passe près du joueur, sauf ce
	 * qui tombe à l'intérieur du territoire (là où les cercles se recouvrent).
	 */
	private static void outline(ServerLevel level, ServerPlayer player, Town town, BlockPos center, int r) {
		double dx = player.getX() - (center.getX() + 0.5);
		double dz = player.getZ() - (center.getZ() + 0.5);
		double radius = r;
		if (Math.abs(Math.sqrt(dx * dx + dz * dz) - radius) > SPAN * 1.5) {
			return;
		}
		double facing = Math.atan2(dz, dx);
		double step = STEP / radius;
		int points = (int) (SPAN / STEP);
		for (int k = -points; k <= points; k++) {
			double angle = facing + k * step;
			double x = center.getX() + 0.5 + Math.cos(angle) * radius;
			double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
			int bx = (int) Math.floor(x);
			int bz = (int) Math.floor(z);
			BlockPos outside = new BlockPos((int) Math.floor(center.getX() + 0.5 + Math.cos(angle) * (radius + 2)), 0,
					(int) Math.floor(center.getZ() + 0.5 + Math.sin(angle) * (radius + 2)));
			if (!level.isLoaded(new BlockPos(bx, 0, bz)) || town.contains(level.dimension(), outside)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
			level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, x, y + 0.3, z, 1, 0, 0.2, 0, 0);
		}
	}
}
