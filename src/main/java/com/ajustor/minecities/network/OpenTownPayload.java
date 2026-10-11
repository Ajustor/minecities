package com.ajustor.minecities.network;

import com.ajustor.minecities.MineCities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Le serveur ouvre (ou rafraîchit) l'écran de l'hôtel de ville, avec l'état de la ville. */
public record OpenTownPayload(TownSnapshot town) implements CustomPacketPayload {
	public static final Type<OpenTownPayload> TYPE = new Type<>(MineCities.id("open_town"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenTownPayload> CODEC = TownSnapshot.CODEC.map(
			OpenTownPayload::new, OpenTownPayload::town);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
