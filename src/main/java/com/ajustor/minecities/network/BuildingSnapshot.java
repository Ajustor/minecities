package com.ajustor.minecities.network;

import com.ajustor.minecities.MineCities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ce que l'interface d'un bâtiment montre (sa plaque, ou « Détails » dans l'onglet Bâtiments) : ce
 * qu'il est, son niveau, ses lits et ses postes, s'il est enregistré (et sinon pourquoi), le chantier
 * en cours à sa place, et son amélioration : le niveau suivant, ce qu'il coûte et ce que le grenier
 * en a, ou ce qui l'empêche.
 *
 * @param kind     {@code home}, {@code workshop}, {@code both}, {@code village}, {@code warehouse}
 * @param status   la clé de son dernier relevé (« enregistré », ou la raison du refus)
 * @param jobs     les clés des métiers de ses postes
 * @param placed   les blocs posés du chantier en cours ici (-1 s'il n'y en a pas)
 * @param next     le nom du niveau suivant (vide s'il n'y en a pas)
 * @param refusal  pourquoi on ne peut pas l'améliorer (vide si on peut)
 * @param manager  celui qui regarde peut-il commander l'amélioration ?
 */
public record BuildingSnapshot(BlockPos plaque, String town, String kind, boolean valid, Component status, int level,
		int beds, int sleepers, List<String> jobs, int placed, int total, Optional<Component> next,
		Optional<Component> refusal, List<Material> materials, boolean manager) implements CustomPacketPayload {
	public static final Type<BuildingSnapshot> TYPE = new Type<>(MineCities.id("open_building"));

	/** Un matériau de l'amélioration : l'objet, combien il en faut, combien le grenier en a. */
	public record Material(ItemStack item, int needed, int stocked) {
	}

	private static final StreamCodec<RegistryFriendlyByteBuf, Component> COMPONENT = ComponentSerialization.STREAM_CODEC;
	private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPTIONAL =
			ComponentSerialization.OPTIONAL_STREAM_CODEC;

	public static final StreamCodec<RegistryFriendlyByteBuf, BuildingSnapshot> CODEC = StreamCodec.of(
			BuildingSnapshot::write, BuildingSnapshot::read);

	private static void write(RegistryFriendlyByteBuf buf, BuildingSnapshot s) {
		buf.writeBlockPos(s.plaque);
		buf.writeUtf(s.town);
		buf.writeUtf(s.kind);
		buf.writeBoolean(s.valid);
		COMPONENT.encode(buf, s.status);
		buf.writeVarInt(s.level);
		buf.writeVarInt(s.beds);
		buf.writeVarInt(s.sleepers);
		buf.writeVarInt(s.jobs.size());
		s.jobs.forEach(buf::writeUtf);
		buf.writeVarInt(s.placed + 1);
		buf.writeVarInt(s.total);
		OPTIONAL.encode(buf, s.next);
		OPTIONAL.encode(buf, s.refusal);
		buf.writeVarInt(s.materials.size());
		for (Material m : s.materials) {
			ItemStack.STREAM_CODEC.encode(buf, m.item());
			buf.writeVarInt(m.needed());
			buf.writeVarInt(m.stocked());
		}
		buf.writeBoolean(s.manager);
	}

	private static BuildingSnapshot read(RegistryFriendlyByteBuf buf) {
		BlockPos plaque = buf.readBlockPos();
		String town = buf.readUtf();
		String kind = buf.readUtf();
		boolean valid = buf.readBoolean();
		Component status = COMPONENT.decode(buf);
		int level = buf.readVarInt();
		int beds = buf.readVarInt();
		int sleepers = buf.readVarInt();
		int jobCount = buf.readVarInt();
		List<String> jobs = new ArrayList<>(jobCount);
		for (int i = 0; i < jobCount; i++) {
			jobs.add(buf.readUtf());
		}
		int placed = buf.readVarInt() - 1;
		int total = buf.readVarInt();
		Optional<Component> next = OPTIONAL.decode(buf);
		Optional<Component> refusal = OPTIONAL.decode(buf);
		int count = buf.readVarInt();
		List<Material> materials = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			materials.add(new Material(ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt()));
		}
		return new BuildingSnapshot(plaque, town, kind, valid, status, level, beds, sleepers, jobs, placed, total, next,
				refusal, materials, buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
