package com.ajustor.minecities.town;

import com.ajustor.minecities.MineCities;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Toutes les villes du monde, sauvegardées avec lui (dans le dossier data de l'Overworld). */
public final class Towns extends SavedData {
	/** Entre deux hôtels de ville, au moins ce qu'il faut pour que deux villages ne se chevauchent pas. */
	public static final int MIN_DISTANCE = TownTier.VILLAGE.radius() * 2;

	private static final Codec<Towns> CODEC = Town.CODEC.listOf().xmap(Towns::new, t -> List.copyOf(t.towns.values()));
	private static final SavedDataType<Towns> TYPE = new SavedDataType<>(MineCities.id("towns"), Towns::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, Town> towns = new LinkedHashMap<>();

	public Towns() {
	}

	private Towns(List<Town> towns) {
		towns.forEach(t -> this.towns.put(t.id(), t));
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

	/** Les villes qu'un joueur gère, comme maire ou adjoint. */
	public List<Town> managedBy(UUID player) {
		return towns.values().stream().filter(t -> t.canManage(player)).toList();
	}

	/** Un hôtel de ville trop près d'une autre ville ? Renvoie la ville gênante. */
	public Optional<Town> tooClose(ResourceKey<Level> dimension, BlockPos pos) {
		return towns.values().stream()
				.filter(t -> t.dimension().equals(dimension) && t.distance(pos) < Math.max(MIN_DISTANCE, t.radius() + TownTier.VILLAGE.radius()))
				.findFirst();
	}

	public void add(Town town) {
		towns.put(town.id(), town);
		setDirty();
	}

	public void remove(Town town) {
		towns.remove(town.id());
		setDirty();
	}

	/** Toute modification d'une ville doit passer par là pour être sauvegardée. */
	public void changed() {
		setDirty();
	}
}
