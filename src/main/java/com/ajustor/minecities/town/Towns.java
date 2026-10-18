package com.ajustor.minecities.town;

import com.ajustor.minecities.MineCities;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.ListBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Toutes les villes du monde, sauvegardées avec lui (dans le dossier data de l'Overworld). */
public final class Towns extends SavedData {
	/** Entre deux hôtels de ville, au moins ce qu'il faut pour que deux villages ne se chevauchent pas. */
	public static final int MIN_DISTANCE = TownTier.VILLAGE.radius() * 2;

	/**
	 * Ville par ville : une ville illisible (une donnée abîmée, un mod retiré) ne fait pas disparaître
	 * les autres, et son NBT brut est gardé tel quel à la sauvegarde suivante (MC-48).
	 */
	static final Codec<Towns> CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<Towns, T>> decode(DynamicOps<T> ops, T input) {
			return ops.getStream(input).map(stream -> {
				Towns towns = new Towns();
				stream.forEach(element -> Town.CODEC.parse(ops, element).ifSuccess(t -> towns.towns.put(t.id(), t))
						.ifError(error -> {
							MineCities.LOGGER.error("Ville illisible, gardée telle quelle dans la sauvegarde : {}",
									error.message());
							towns.unreadable.add(new Dynamic<>(ops, element));
							UUIDUtil.CODEC.parse(ops, ops.get(element, "id").result().orElse(ops.empty()))
									.ifSuccess(towns.unreadableIds::add);
						}));
				towns.link();
				return Pair.of(towns, ops.empty());
			});
		}

		@Override
		public <T> DataResult<T> encode(Towns towns, DynamicOps<T> ops, T prefix) {
			ListBuilder<T> list = ops.listBuilder();
			towns.towns.values().forEach(t -> list.add(Town.CODEC.encodeStart(ops, t)));
			towns.unreadable.forEach(raw -> list.add(raw.convert(ops).getValue()));
			return list.build(prefix);
		}
	};
	private static final SavedDataType<Towns> TYPE = new SavedDataType<>(MineCities.id("towns"), Towns::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, Town> towns = new LinkedHashMap<>();
	/** Les villes qu'on n'a pas su relire, et leurs identifiants : leurs citoyens attendent sans partir. */
	private final List<Dynamic<?>> unreadable = new ArrayList<>();
	private final Set<UUID> unreadableIds = new HashSet<>();

	public Towns() {
	}

	/** Cette ville est-elle dans la sauvegarde, mais illisible ? */
	public boolean unreadable(UUID id) {
		return unreadableIds.contains(id);
	}

	public static Towns get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public Collection<Town> all() {
		return List.copyOf(towns.values());
	}

	public Optional<Town> byId(UUID id) {
		return Optional.ofNullable(id == null ? null : towns.get(id));
	}

	public Optional<Town> byName(String name) {
		return towns.values().stream().filter(t -> t.name().equalsIgnoreCase(name)).findFirst();
	}

	public Optional<Town> byHall(ResourceKey<Level> dimension, BlockPos hall) {
		return towns.values().stream().filter(t -> t.dimension().equals(dimension) && t.hall().equals(hall)).findFirst();
	}

	/** La ville dont le territoire couvre ce point ; la plus proche si plusieurs se chevauchent. */
	public Optional<Town> at(ResourceKey<Level> dimension, BlockPos pos) {
		return towns.values().stream().filter(t -> t.contains(dimension, pos))
				.min(Comparator.comparingDouble(t -> t.distance(pos)));
	}

	/** Les villes dont ce joueur est le maire (il peut en diriger plusieurs). */
	public List<Town> ofMayor(UUID player) {
		return towns.values().stream().filter(t -> t.mayor().equals(player)).toList();
	}

	/** Les villes dont un joueur fait partie : maire, adjoint ou habitant. */
	public List<Town> memberOf(UUID player) {
		return towns.values().stream().filter(t -> t.isMember(player)).toList();
	}

	/** Les villes qu'un joueur gère, comme maire ou adjoint. */
	public List<Town> managedBy(UUID player) {
		return towns.values().stream().filter(t -> t.canManage(player)).toList();
	}

	/** Un hôtel de ville trop près d'une autre ville ? Renvoie la ville gênante. */
	public Optional<Town> tooClose(ResourceKey<Level> dimension, BlockPos pos) {
		// Loin de l'hôtel de ville, mais aussi des annexes : une nouvelle ville n'y aurait presque rien.
		return towns.values().stream()
				.filter(t -> t.dimension().equals(dimension) && (t.distance(pos) < spacing(t) || t.activeAnnexes()
						.stream().anyMatch(a -> Math.hypot(pos.getX() - a.getX(), pos.getZ() - a.getZ())
								< Town.ANNEX_RADIUS + TownTier.VILLAGE.radius())))
				.findFirst();
	}

	/** La distance à laisser entre l'hôtel de ville de cette ville et celui d'une nouvelle. */
	public static int spacing(Town town) {
		return Math.max(MIN_DISTANCE, town.radius() + TownTier.VILLAGE.radius());
	}

	public void add(Town town) {
		towns.put(town.id(), town);
		link();
		setDirty();
	}

	public void remove(Town town) {
		towns.remove(town.id());
		town.neighbours(List.of());
		link();
		setDirty();
	}

	/** Chaque ville connaît ses voisines, pour partager le territoire là où il se chevauche. */
	private void link() {
		List<Town> all = List.copyOf(towns.values());
		all.forEach(t -> t.neighbours(all));
	}

	/** Toute modification d'une ville doit passer par là pour être sauvegardée. */
	public void changed() {
		setDirty();
	}
}
