package com.ajustor.minecities.network;

import com.ajustor.minecities.MineCities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Le joueur agit depuis l'écran de l'hôtel de ville : ouvrir le grenier, accepter ou refuser une
 * demande de mariage.
 *
 * @param action  {@code granary}, {@code accept} ou {@code refuse}
 * @param request la demande visée (sans objet pour le grenier)
 */
public record TownActionPayload(BlockPos hall, String action, UUID request) implements CustomPacketPayload {
	public static final Type<TownActionPayload> TYPE = new Type<>(MineCities.id("town_action"));
	public static final UUID NONE = new UUID(0, 0);

	public static final StreamCodec<FriendlyByteBuf, TownActionPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, TownActionPayload::hall,
			ByteBufCodecs.STRING_UTF8, TownActionPayload::action,
			UUIDUtil.STREAM_CODEC, TownActionPayload::request,
			TownActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
