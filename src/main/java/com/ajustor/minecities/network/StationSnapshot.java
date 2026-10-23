package com.ajustor.minecities.network;

import com.ajustor.minecities.MineCities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ce que l'interface d'un poste de travail montre : le métier, la ville, ce qui empêche le poste de
 * servir ({@code status} : {@code ok}, {@code no_town}, {@code undeclared}, {@code locked},
 * {@code no_water}, {@code vacant}), ses travailleurs, et pour le bâtisseur les chantiers de la ville.
 *
 * @param block  la clé du nom du bloc
 * @param detail le palier attendu (pour {@code locked}), vide sinon
 * @param manager celui qui regarde gère-t-il la ville (maire, adjoint) ?
 * @param mine   pour le mineur : l'état du puits ({@code new}, {@code digging}, {@code exhausted},
 *               {@code closed}), vide sinon
 * @param ores   pour le mineur : ce que le puits peut encore donner, et ce qu'il a déjà donné
 * @param missing pour le bâtisseur : ce qui manque aux chantiers, que les réserves n'ont pas
 */
public record StationSnapshot(BlockPos pos, String block, String job, String town, String status, String detail,
		List<Worker> workers, List<Site> sites, boolean manager, String mine, List<Ore> ores, String mastery,
		List<Shortage> missing)
		implements CustomPacketPayload {
	public static final Type<StationSnapshot> TYPE = new Type<>(MineCities.id("open_station"));

	/** Un travailleur du poste : son nom, ce qu'il fait ({@code working}, {@code sleeping}…), ce qui lui manque. */
	public record Worker(UUID id, String name, String state, Optional<Component> need, String rank) {
	}

	/** Un minerai du puits : de quoi le montrer, combien il en reste à portée, combien le mineur en a tiré. */
	public record Ore(net.minecraft.world.item.ItemStack item, int remaining, int mined) {
	}

	/** Un chantier : le bâtiment, où, combien de blocs posés sur combien, et s'il est fini. */
	public record Site(Component name, BlockPos origin, int placed, int total, UUID id, boolean paused) {
	}

	/** Un matériau qui manque aux chantiers, et combien. */
	public record Shortage(net.minecraft.world.item.Item item, int count) {
	}

	private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPTIONAL =
			ComponentSerialization.OPTIONAL_STREAM_CODEC;
	private static final StreamCodec<RegistryFriendlyByteBuf, Component> COMPONENT = ComponentSerialization.STREAM_CODEC;

	public static final StreamCodec<RegistryFriendlyByteBuf, StationSnapshot> CODEC = StreamCodec.of(
			StationSnapshot::write, StationSnapshot::read);

	private static void write(RegistryFriendlyByteBuf buf, StationSnapshot s) {
		buf.writeBlockPos(s.pos);
		buf.writeUtf(s.block);
		buf.writeUtf(s.job);
		buf.writeUtf(s.town);
		buf.writeUtf(s.status);
		buf.writeUtf(s.detail);
		buf.writeVarInt(s.workers.size());
		for (Worker w : s.workers) {
			UUIDUtil.STREAM_CODEC.encode(buf, w.id());
			buf.writeUtf(w.name());
			buf.writeUtf(w.state());
			OPTIONAL.encode(buf, w.need());
			buf.writeUtf(w.rank());
		}
		buf.writeVarInt(s.sites.size());
		for (Site site : s.sites) {
			COMPONENT.encode(buf, site.name());
			buf.writeBlockPos(site.origin());
			buf.writeVarInt(site.placed());
			buf.writeVarInt(site.total());
			UUIDUtil.STREAM_CODEC.encode(buf, site.id());
			buf.writeBoolean(site.paused());
		}
		buf.writeBoolean(s.manager);
		buf.writeUtf(s.mine);
		buf.writeVarInt(s.ores.size());
		for (Ore ore : s.ores) {
			net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(buf, ore.item());
			buf.writeVarInt(ore.remaining());
			buf.writeVarInt(ore.mined());
		}
		buf.writeUtf(s.mastery);
		buf.writeVarInt(s.missing.size());
		for (Shortage shortage : s.missing) {
			buf.writeVarInt(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(shortage.item()));
			buf.writeVarInt(shortage.count());
		}
	}

	private static StationSnapshot read(RegistryFriendlyByteBuf buf) {
		BlockPos pos = buf.readBlockPos();
		String block = buf.readUtf();
		String job = buf.readUtf();
		String town = buf.readUtf();
		String status = buf.readUtf();
		String detail = buf.readUtf();
		int count = buf.readVarInt();
		List<Worker> workers = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			workers.add(new Worker(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf(), OPTIONAL.decode(buf),
					buf.readUtf()));
		}
		int siteCount = buf.readVarInt();
		List<Site> sites = new ArrayList<>(siteCount);
		for (int i = 0; i < siteCount; i++) {
			sites.add(new Site(COMPONENT.decode(buf), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
					UUIDUtil.STREAM_CODEC.decode(buf), buf.readBoolean()));
		}
		boolean manager = buf.readBoolean();
		String mine = buf.readUtf();
		int oreCount = buf.readVarInt();
		List<Ore> ores = new ArrayList<>(oreCount);
		for (int i = 0; i < oreCount; i++) {
			ores.add(new Ore(net.minecraft.world.item.ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt()));
		}
		String mastery = buf.readUtf();
		int missingCount = buf.readVarInt();
		List<Shortage> missing = new ArrayList<>(missingCount);
		for (int i = 0; i < missingCount; i++) {
			missing.add(new Shortage(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(buf.readVarInt()),
					buf.readVarInt()));
		}
		return new StationSnapshot(pos, block, job, town, status, detail, workers, sites, manager, mine, ores,
				mastery, missing);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
