package com.ajustor.minecities.caravan;

import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.Towns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le bâton d'itinéraire (MC-80) : il trace la route des caravanes entre deux villes qui commercent.
 * Accroupi, un clic choisit la route (parmi les accords de la ville du joueur) ; un clic sur un bloc y
 * pose un point de passage, ou retire celui qui s'y trouve. Tenu en main, il montre la route : ses
 * points en colonnes, et le trajet qu'elle suit.
 */
public class RouteStaffItem extends Item {
	/** Un clic à moins de tant d'un point le retire. */
	private static final double REMOVE = 2.5;
	/** Le trajet montré au joueur, par route (recalculé quand elle change). */
	private static final Map<String, List<BlockPos>> SHOWN = new HashMap<>();

	public RouteStaffItem(Properties properties) {
		super(properties);
	}

	/** La route choisie : la ville d'où l'on trace, puis celle où l'on va. */
	static Optional<UUID[]> route(ItemStack staff) {
		CustomData data = staff.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return Optional.empty();
		}
		CompoundTag tag = data.copyTag();
		Optional<String> from = tag.getString("from");
		Optional<String> to = tag.getString("to");
		if (from.isEmpty() || to.isEmpty()) {
			return Optional.empty();
		}
		try {
			return Optional.of(new UUID[]{UUID.fromString(from.get()), UUID.fromString(to.get())});
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack staff = context.getItemInHand();
		Towns towns = Towns.get(level.getServer());
		if (player.isSecondaryUseActive()) {
			choose(player, staff, towns);
			return InteractionResult.SUCCESS;
		}
		Optional<UUID[]> route = route(staff);
		Optional<Town> from = route.flatMap(r -> towns.byId(r[0]));
		Optional<Town> to = route.flatMap(r -> towns.byId(r[1]));
		if (from.isEmpty() || to.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.minecities.route.none").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		if (!from.get().canManage(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("message.minecities.route.forbidden", from.get().name())
					.withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		Caravans caravans = Caravans.get(level.getServer());
		List<BlockPos> points = new ArrayList<>(caravans.waypoints(from.get().id(), to.get().id()));
		BlockPos clicked = context.getClickedPos().above();
		Optional<BlockPos> near = points.stream()
				.filter(p -> p.distSqr(clicked) <= REMOVE * REMOVE).findFirst();
		if (near.isPresent()) {
			points.remove(near.get());
			player.sendSystemMessage(Component.translatable("message.minecities.route.removed", points.size())
					.withStyle(ChatFormatting.GOLD));
		} else {
			points.add(clicked.immutable());
			player.sendSystemMessage(Component.translatable("message.minecities.route.added", points.size(),
					from.get().name(), to.get().name()).withStyle(ChatFormatting.GREEN));
		}
		caravans.setWaypoints(from.get().id(), to.get().id(), points);
		SHOWN.remove(key(from.get().id(), to.get().id()));
		return InteractionResult.SUCCESS;
	}

	/** Passe à la route suivante : d'une ville que gère le joueur vers une ville avec qui elle commerce. */
	private static void choose(ServerPlayer player, ItemStack staff, Towns towns) {
		List<UUID[]> routes = new ArrayList<>();
		for (Town town : towns.all()) {
			if (!town.canManage(player.getUUID())) {
				continue;
			}
			for (UUID partner : town.partners()) {
				towns.byId(partner).ifPresent(other -> routes.add(new UUID[]{town.id(), other.id()}));
			}
		}
		if (routes.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.minecities.route.no_agreement")
					.withStyle(ChatFormatting.RED));
			return;
		}
		Optional<UUID[]> current = route(staff);
		int index = 0;
		for (int i = 0; i < routes.size(); i++) {
			if (current.isPresent() && routes.get(i)[0].equals(current.get()[0]) && routes.get(i)[1].equals(current.get()[1])) {
				index = (i + 1) % routes.size();
			}
		}
		UUID[] chosen = routes.get(index);
		CompoundTag tag = new CompoundTag();
		tag.putString("from", chosen[0].toString());
		tag.putString("to", chosen[1].toString());
		staff.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		Town from = towns.byId(chosen[0]).orElseThrow();
		Town to = towns.byId(chosen[1]).orElseThrow();
		staff.set(DataComponents.ITEM_NAME, Component.translatable("item.minecities.route_staff.named", from.name(),
				to.name()));
		player.sendSystemMessage(Component.translatable("message.minecities.route.chosen", from.name(), to.name(),
				Caravans.get(player.level().getServer()).waypoints(from.id(), to.id()).size())
				.withStyle(ChatFormatting.GOLD));
	}

	private static String key(UUID a, UUID b) {
		return a + ">" + b;
	}

	/** Chaque seconde : à qui tient le bâton, la route choisie (ses points et son trajet, près de lui). */
	public static void show(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		Towns towns = Towns.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack staff = held(player);
			Optional<UUID[]> route = staff.isEmpty() ? Optional.empty() : route(staff);
			Optional<Town> from = route.flatMap(r -> towns.byId(r[0]));
			Optional<Town> to = route.flatMap(r -> towns.byId(r[1]));
			if (from.isEmpty() || to.isEmpty() || !(player.level() instanceof ServerLevel level)
					|| !level.dimension().equals(from.get().dimension())) {
				continue;
			}
			Caravans caravans = Caravans.get(server);
			for (BlockPos point : caravans.waypoints(from.get().id(), to.get().id())) {
				if (point.distSqr(player.blockPosition()) < 64 * 64) {
					for (int y = 0; y < 4; y++) {
						level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, point.getX() + 0.5,
								point.getY() + y * 0.6, point.getZ() + 0.5, 1, 0.05, 0.05, 0.05, 0);
					}
				}
			}
			List<BlockPos> path = SHOWN.computeIfAbsent(key(from.get().id(), to.get().id()),
					k -> caravans.path(level, from.get(), to.get()));
			double length = Itinerary.length(path);
			for (double s = 0; s <= length; s += 2) {
				Vec3 at = Itinerary.at(path, s);
				if (at.distanceToSqr(player.position()) < 48 * 48) {
					level.sendParticles(player, ParticleTypes.END_ROD, true, false, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
				}
			}
		}
	}

	private static ItemStack held(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof RouteStaffItem) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}
}
