package com.ajustor.minecities.build;

import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Commander un chantier, l'agrandir, et le montrer au joueur. */
public final class Builds {
	private Builds() {
	}

	/**
	 * Commande un chantier. Renvoie le refus, s'il y en a un : emprise hors du territoire, ou sur
	 * un chantier pas encore fini.
	 */
	public static Optional<Component> order(ServerLevel level, Town town, Blueprint blueprint, BuildStyle style,
			BlockPos origin, Rotation rotation) {
		BlockPos[] bounds = BuildPlan.bounds(blueprint, origin, rotation);
		for (BlockPos corner : new BlockPos[]{bounds[0], bounds[1], new BlockPos(bounds[0].getX(), 0, bounds[1].getZ()),
				new BlockPos(bounds[1].getX(), 0, bounds[0].getZ())}) {
			if (!town.contains(level.dimension(), corner)) {
				return Optional.of(Component.translatable("message.minecities.build.outside", town.name()));
			}
		}
		if (town.constructions().stream().anyMatch(c -> !c.done() && c.overlaps(bounds[0], bounds[1]))) {
			return Optional.of(Component.translatable("message.minecities.build.overlap"));
		}
		town.order(Construction.order(blueprint, style, origin, rotation));
		Towns.get(level.getServer()).changed();
		return Optional.empty();
	}

	/** Agrandit le bâtiment bâti par la ville à cet endroit, d'après le schéma qui lui succède. */
	public static Optional<Component> upgrade(ServerLevel level, Town town, BlockPos where) {
		Optional<Construction> built = town.builtAt(where);
		if (built.isEmpty()) {
			return Optional.of(Component.translatable("message.minecities.build.not_built"));
		}
		Optional<Blueprint> current = Blueprints.get(level.getServer(), built.get().blueprint());
		Optional<Blueprint> next = current.flatMap(Blueprint::upgrade).flatMap(id -> Blueprints.get(level.getServer(), id));
		if (next.isEmpty()) {
			return Optional.of(Component.translatable("message.minecities.build.no_upgrade"));
		}
		return order(level, town, next.get(), built.get().style(), built.get().origin(), built.get().rotation());
	}

	/** Dit au joueur ce que coûte le plan, et ce que le grenier en a déjà. */
	public static void describeMaterials(ServerLevel level, Town town, List<BuildPlan.Placement> plan,
			ServerPlayer player) {
		Map<Item, Integer> needs = BuildPlan.materials(plan);
		player.sendSystemMessage(Component.translatable("message.minecities.build.materials")
				.withStyle(ChatFormatting.GRAY));
		TownService.granary(level, town).ifPresent(granary -> needs.forEach((item, count) -> {
			int stocked = Stock.count(granary, s -> s.is(item));
			player.sendSystemMessage(Component.translatable("message.minecities.build.material", count,
					item.getName(new ItemStack(item)), Math.min(stocked, count))
					.withStyle(stocked >= count ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		}));
	}

	/** Trace l'emprise au sol en particules, pour le joueur seul. */
	public static void outline(ServerLevel level, ServerPlayer player, BlockPos min, BlockPos max) {
		double y = min.getY() + 0.2;
		for (int x = min.getX(); x <= max.getX() + 1; x++) {
			for (double z : new double[]{min.getZ(), max.getZ() + 1}) {
				level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, x, y, z, 1, 0, 0, 0, 0);
			}
		}
		for (int z = min.getZ(); z <= max.getZ() + 1; z++) {
			for (double x : new double[]{min.getX(), max.getX() + 1}) {
				level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, x, y, z, 1, 0, 0, 0, 0);
			}
		}
	}
}
