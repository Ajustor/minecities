package com.ajustor.minecities.field;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.town.Field;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.Towns;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Le piquet de champ côté réseau : l'interface du champ (choisir la culture, supprimer le champ) et,
 * pour qui tient le piquet, le contour des champs de la ville et du champ qu'il est en train de tracer.
 */
public final class FieldNetwork {
	/** Au-delà, un champ ne se montre pas. */
	private static final double VIEW = 64;

	/** Ce que montre l'interface d'un champ. */
	public record FieldSnapshot(String id, String town, int width, int depth, String crop)
			implements CustomPacketPayload {
		public static final Type<FieldSnapshot> TYPE = new Type<>(MineCities.id("field_snapshot"));
		public static final StreamCodec<FriendlyByteBuf, FieldSnapshot> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, FieldSnapshot::id, ByteBufCodecs.STRING_UTF8, FieldSnapshot::town,
				ByteBufCodecs.VAR_INT, FieldSnapshot::width, ByteBufCodecs.VAR_INT, FieldSnapshot::depth,
				ByteBufCodecs.STRING_UTF8, FieldSnapshot::crop, FieldSnapshot::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Ce que le joueur demande depuis l'interface : {@code crop:<culture>} ou {@code delete}. */
	public record FieldAction(String id, String action) implements CustomPacketPayload {
		public static final Type<FieldAction> TYPE = new Type<>(MineCities.id("field_action"));
		public static final StreamCodec<FriendlyByteBuf, FieldAction> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, FieldAction::id, ByteBufCodecs.STRING_UTF8, FieldAction::action,
				FieldAction::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private FieldNetwork() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(FieldSnapshot.TYPE, FieldSnapshot.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(FieldAction.TYPE, FieldAction.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FieldAction.TYPE,
				(payload, context) -> act(context.player(), payload.id(), payload.action()));
		ServerTickEvents.END_SERVER_TICK.register(FieldNetwork::show);
	}

	static void open(ServerPlayer player, Town town, Field field) {
		ServerPlayNetworking.send(player, new FieldSnapshot(field.id().toString(), town.name(), field.width(),
				field.depth(), field.crop().serializedName()));
	}

	/** Change la culture d'un champ, ou le supprime ; le serveur revérifie les droits du joueur. */
	public static boolean act(ServerPlayer player, String fieldId, String action) {
		UUID id;
		try {
			id = UUID.fromString(fieldId);
		} catch (IllegalArgumentException e) {
			return false;
		}
		Towns towns = Towns.get(player.level().getServer());
		Optional<Town> owner = towns.all().stream().filter(t -> t.field(id).isPresent()).findFirst();
		if (owner.isEmpty() || !owner.get().canManage(player.getUUID())) {
			return false;
		}
		Town town = owner.get();
		Field field = town.field(id).orElseThrow();
		if (action.equals("delete")) {
			town.removeField(id);
			player.sendSystemMessage(Component.translatable("message.minecities.field.deleted")
					.withStyle(ChatFormatting.GOLD));
		} else if (action.startsWith("crop:")) {
			Optional<Field.Crop> crop = Field.Crop.fromSerializedName(action.substring("crop:".length()));
			if (crop.isEmpty()) {
				return false;
			}
			town.updateField(field.withCrop(crop.get()));
			player.sendSystemMessage(Component.translatable("message.minecities.field.crop",
					Component.translatable("crop.minecities." + crop.get().serializedName()))
					.withStyle(ChatFormatting.GREEN));
		} else {
			return false;
		}
		towns.changed();
		return true;
	}

	/** Deux fois par seconde : à qui tient le piquet, les champs proches et celui qu'il trace. */
	private static void show(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		Towns towns = Towns.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack marker = held(player);
			if (marker.isEmpty() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			for (Town town : towns.all()) {
				if (!town.dimension().equals(level.dimension())) {
					continue;
				}
				for (Field field : town.fields()) {
					outline(level, player, field.min(), field.max(), color(field.crop()));
				}
			}
			// Le champ en cours : du premier coin au bloc visé.
			Optional<BlockPos> corner = FieldMarkerItem.corner(marker);
			if (corner.isPresent()) {
				HitResult hit = player.pick(24, 0, false);
				BlockPos target = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
						? block.getBlockPos() : corner.get();
				outline(level, player, corner.get(), target, 0xFFFFFF);
				level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, corner.get().getX() + 0.5,
						corner.get().getY() + 1.5, corner.get().getZ() + 0.5, 3, 0.1, 0.3, 0.1, 0);
			}
		}
	}

	/** Le contour d'un rectangle, posé sur le sol de chaque colonne. */
	private static void outline(ServerLevel level, ServerPlayer player, BlockPos a, BlockPos b, int color) {
		int minX = Math.min(a.getX(), b.getX());
		int maxX = Math.max(a.getX(), b.getX());
		int minZ = Math.min(a.getZ(), b.getZ());
		int maxZ = Math.max(a.getZ(), b.getZ());
		if (player.distanceToSqr((minX + maxX) / 2.0, player.getY(), (minZ + maxZ) / 2.0) > VIEW * VIEW) {
			return;
		}
		DustParticleOptions dust = new DustParticleOptions(color, 1.2f);
		for (int x = minX; x <= maxX + 1; x++) {
			mark(level, player, dust, x, minZ);
			mark(level, player, dust, x, maxZ + 1);
		}
		for (int z = minZ + 1; z <= maxZ; z++) {
			mark(level, player, dust, minX, z);
			mark(level, player, dust, maxX + 1, z);
		}
	}

	/** Un point du contour, au coin nord-ouest du bloc (x, z), juste au-dessus du sol. */
	private static void mark(ServerLevel level, ServerPlayer player, DustParticleOptions dust, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		level.sendParticles(player, dust, true, false, x, y + 0.6, z, 1, 0, 0, 0, 0);
	}

	/** La couleur du contour : celle de la culture. */
	static int color(Field.Crop crop) {
		return switch (crop) {
			case ANY -> 0xE0E0E0;
			case WHEAT -> 0xE8C547;
			case CARROTS -> 0xF08A24;
			case POTATOES -> 0xC9A066;
			case BEETROOTS -> 0xB0203A;
			case PUMPKINS -> 0xE07B1A;
			case MELONS -> 0x5DB33A;
		};
	}

	private static ItemStack held(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof FieldMarkerItem) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}
}
