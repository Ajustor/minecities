package com.ajustor.minecities.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ce que l'écran de l'hôtel de ville montre d'une ville, envoyé au joueur à l'ouverture : le résumé,
 * les citoyens, et le registre des demandes de mariage. Les textes sont des clés de traduction,
 * traduites côté client.
 */
public record TownSnapshot(BlockPos hall, String name, String tier, int population, int citizens, int villagers,
		int capacity, int homes, int workstations, int employed, int meals, String nextTier, int nextPopulation,
		int nextHouses, boolean manager, List<CitizenRow> rows, List<RequestRow> requests) {
	/** Un citoyen : nom, métier et faim (clés), logé ou non, conjoint, enfant, ce qui lui manque. */
	public record CitizenRow(String name, String job, String hunger, boolean housed, String spouse, boolean child,
			Optional<Component> need) {
	}

	/** Une demande de mariage en attente. */
	public record RequestRow(UUID id, String first, String second) {
	}

	private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> NEED =
			ComponentSerialization.OPTIONAL_STREAM_CODEC;

	public static final StreamCodec<RegistryFriendlyByteBuf, TownSnapshot> CODEC = StreamCodec.of(TownSnapshot::write,
			TownSnapshot::read);

	private static void write(RegistryFriendlyByteBuf buf, TownSnapshot s) {
		buf.writeBlockPos(s.hall);
		buf.writeUtf(s.name);
		buf.writeUtf(s.tier);
		buf.writeVarInt(s.population);
		buf.writeVarInt(s.citizens);
		buf.writeVarInt(s.villagers);
		buf.writeVarInt(s.capacity);
		buf.writeVarInt(s.homes);
		buf.writeVarInt(s.workstations);
		buf.writeVarInt(s.employed);
		buf.writeVarInt(s.meals);
		buf.writeUtf(s.nextTier);
		buf.writeVarInt(s.nextPopulation);
		buf.writeVarInt(s.nextHouses);
		buf.writeBoolean(s.manager);
		buf.writeVarInt(s.rows.size());
		for (CitizenRow row : s.rows) {
			buf.writeUtf(row.name);
			buf.writeUtf(row.job);
			buf.writeUtf(row.hunger);
			buf.writeBoolean(row.housed);
			buf.writeUtf(row.spouse);
			buf.writeBoolean(row.child);
			NEED.encode(buf, row.need);
		}
		buf.writeVarInt(s.requests.size());
		for (RequestRow request : s.requests) {
			UUIDUtil.STREAM_CODEC.encode(buf, request.id);
			buf.writeUtf(request.first);
			buf.writeUtf(request.second);
		}
	}

	private static TownSnapshot read(RegistryFriendlyByteBuf buf) {
		BlockPos hall = buf.readBlockPos();
		String name = buf.readUtf();
		String tier = buf.readUtf();
		int population = buf.readVarInt();
		int citizens = buf.readVarInt();
		int villagers = buf.readVarInt();
		int capacity = buf.readVarInt();
		int homes = buf.readVarInt();
		int workstations = buf.readVarInt();
		int employed = buf.readVarInt();
		int meals = buf.readVarInt();
		String nextTier = buf.readUtf();
		int nextPopulation = buf.readVarInt();
		int nextHouses = buf.readVarInt();
		boolean manager = buf.readBoolean();
		int count = buf.readVarInt();
		List<CitizenRow> rows = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			rows.add(new CitizenRow(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readUtf(),
					buf.readBoolean(), NEED.decode(buf)));
		}
		int requestCount = buf.readVarInt();
		List<RequestRow> requests = new ArrayList<>(requestCount);
		for (int i = 0; i < requestCount; i++) {
			requests.add(new RequestRow(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf()));
		}
		return new TownSnapshot(hall, name, tier, population, citizens, villagers, capacity, homes, workstations,
				employed, meals, nextTier, nextPopulation, nextHouses, manager, rows, requests);
	}
}
