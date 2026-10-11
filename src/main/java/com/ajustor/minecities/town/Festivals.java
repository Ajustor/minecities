package com.ajustor.minecities.town;

import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

import java.util.List;

/**
 * Les fêtes (brief 06) : le maire en offre une contre quelques émeraudes du grenier, ou la ville fête
 * d'elle-même une victoire sur les pillards. Pendant deux minutes, les citoyens dansent devant
 * l'hôtel de ville sous les feux d'artifice ; la fête réjouit la ville pendant un jour.
 */
public final class Festivals {
	/** Ce que coûte une fête, en émeraudes prises au grenier. */
	public static final int COST = 4;
	/** Un feu d'artifice toutes les secondes et demie pendant la fête. */
	private static final int FIREWORK_INTERVAL = 30;
	private static final int[] COLORS = {0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF,
			0xFFFFFF};

	private static int ticks;

	private Festivals() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Festivals::tick);
	}

	/** Pourquoi on ne peut pas donner de fête maintenant. */
	public enum Refusal { TOO_SOON, NO_EMERALDS, NO_GRANARY }

	/**
	 * Le maire offre une fête : elle coûte {@link #COST} émeraudes au grenier, et pas plus d'une tous
	 * les trois jours. Renvoie ce qui l'empêche, ou rien si la fête commence.
	 */
	public static java.util.Optional<Refusal> offer(ServerLevel level, Town town) {
		long now = level.getGameTime();
		if (!town.chronicle().canCelebrate(now)) {
			return java.util.Optional.of(Refusal.TOO_SOON);
		}
		var granary = TownService.granary(level, town);
		if (granary.isEmpty()) {
			return java.util.Optional.of(Refusal.NO_GRANARY);
		}
		if (Stock.count(granary.get(), s -> s.is(Items.EMERALD)) < COST) {
			return java.util.Optional.of(Refusal.NO_EMERALDS);
		}
		Stock.take(granary.get(), s -> s.is(Items.EMERALD), COST);
		celebrate(level, town, "message.minecities.festival.offered");
		return java.util.Optional.empty();
	}

	/** La fête commence (offerte par le maire, ou pour une victoire). */
	public static void celebrate(ServerLevel level, Town town, String announcement) {
		town.chronicle().celebrate(level.getGameTime());
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(announcement, town.name())
				.withStyle(ChatFormatting.LIGHT_PURPLE), false);
		level.playSound(null, town.hall(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 1.2f);
		Towns.get(level.getServer()).changed();
	}

	private static void tick(MinecraftServer server) {
		if (++ticks % FIREWORK_INTERVAL != 0) {
			return;
		}
		for (Town town : Towns.get(server).all()) {
			ServerLevel level = server.getLevel(town.dimension());
			if (level == null || !level.isPositionEntityTicking(town.hall())
					|| !town.chronicle().partying(level.getGameTime())) {
				continue;
			}
			launch(level, town.hall());
		}
	}

	/** Un feu d'artifice de couleurs au hasard, tiré près de l'hôtel de ville. */
	private static void launch(ServerLevel level, BlockPos hall) {
		RandomSource random = level.getRandom();
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		FireworkExplosion.Shape[] shapes = {FireworkExplosion.Shape.SMALL_BALL, FireworkExplosion.Shape.LARGE_BALL,
				FireworkExplosion.Shape.STAR, FireworkExplosion.Shape.BURST};
		FireworkExplosion explosion = new FireworkExplosion(shapes[random.nextInt(shapes.length)],
				IntList.of(COLORS[random.nextInt(COLORS.length)], COLORS[random.nextInt(COLORS.length)]),
				IntList.of(COLORS[random.nextInt(COLORS.length)]), random.nextBoolean(), random.nextBoolean());
		rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + random.nextInt(2), List.of(explosion)));
		double x = hall.getX() + 0.5 + random.nextInt(13) - 6;
		double z = hall.getZ() + 0.5 + random.nextInt(13) - 6;
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x),
				(int) Math.floor(z));
		level.addFreshEntity(new FireworkRocketEntity(level, x, y + 0.5, z, rocket));
	}
}
