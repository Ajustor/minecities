package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Les joueurs de la ville (à part des citoyens) : ses habitants, les demandes pour s'y installer, et
 * ce que chaque rang a le droit d'y faire. Le maire et ses adjoints ont toujours tous les droits ;
 * l'habitant et le visiteur ont ceux que le maire leur donne.
 *
 * <p>Une ville fondée avant les rangs n'a pas de permissions enregistrées : on lui laisse les droits
 * d'avant (tout le monde déclare une maison et ouvre le grenier), pour ne rien casser.
 */
public final class Membership {
	public static final MapCodec<Membership> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).optionalFieldOf("player_residents", Map.of())
					.forGetter(m -> m.residents),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).optionalFieldOf("join_requests", Map.of())
					.forGetter(m -> m.requests),
			Codec.STRING.listOf().optionalFieldOf("resident_permissions")
					.forGetter(m -> Optional.of(Permission.names(m.resident))),
			Codec.STRING.listOf().optionalFieldOf("visitor_permissions")
					.forGetter(m -> Optional.of(Permission.names(m.visitor)))
	).apply(i, (residents, requests, resident, visitor) -> new Membership(residents, requests,
			resident.map(Permission::parse).orElseGet(Membership::before),
			visitor.map(Permission::parse).orElseGet(Membership::before))));

	/** Les rangs des joueurs, du plus haut au plus bas. */
	public enum Rank {
		MAYOR, DEPUTY, RESIDENT, VISITOR;

		public String serializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public String translationKey() {
			return "rank.minecities." + serializedName();
		}

		public static Optional<Rank> fromSerializedName(String name) {
			for (Rank rank : values()) {
				if (rank.serializedName().equals(name)) {
					return Optional.of(rank);
				}
			}
			return Optional.empty();
		}
	}

	/** Ce qu'un rang peut avoir le droit de faire dans la ville. */
	public enum Permission {
		/** Déclarer (ou relever) une maison par sa plaque. */
		DECLARE,
		/** Ouvrir le grenier : l'hôtel de ville et les annexes. */
		GRANARY,
		/** Casser les blocs des bâtiments déclarés. */
		BREAK;

		public String serializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public String translationKey() {
			return "permission.minecities." + serializedName();
		}

		public static Optional<Permission> fromSerializedName(String name) {
			for (Permission permission : values()) {
				if (permission.serializedName().equals(name)) {
					return Optional.of(permission);
				}
			}
			return Optional.empty();
		}

		static List<String> names(Set<Permission> permissions) {
			return permissions.stream().map(Permission::serializedName).toList();
		}

		static Set<Permission> parse(List<String> names) {
			Set<Permission> permissions = EnumSet.noneOf(Permission.class);
			names.forEach(n -> fromSerializedName(n).ifPresent(permissions::add));
			return permissions;
		}
	}

	/** Les habitants joueurs, par UUID, avec leur nom. */
	private final Map<UUID, String> residents;
	/** Les joueurs qui demandent à s'installer, avec leur nom, dans l'ordre des demandes. */
	private final Map<UUID, String> requests;
	private final EnumSet<Permission> resident;
	private final EnumSet<Permission> visitor;

	private Membership(Map<UUID, String> residents, Map<UUID, String> requests, Set<Permission> resident,
			Set<Permission> visitor) {
		this.residents = new LinkedHashMap<>(residents);
		this.requests = new LinkedHashMap<>(requests);
		this.resident = EnumSet.noneOf(Permission.class);
		this.resident.addAll(resident);
		this.visitor = EnumSet.noneOf(Permission.class);
		this.visitor.addAll(visitor);
	}

	/** Une ville nouvelle : l'habitant déclare une maison et ouvre le grenier, le visiteur rien. */
	public static Membership founded() {
		return new Membership(Map.of(), Map.of(), EnumSet.of(Permission.DECLARE, Permission.GRANARY),
				EnumSet.noneOf(Permission.class));
	}

	/**
	 * Les droits d'avant les rangs : tout le monde déclarait une maison et ouvrait le grenier ; on
	 * cassait les bâtiments sauf si le serveur les protégeait (réglage {@code protect_buildings}).
	 */
	private static Set<Permission> before() {
		Set<Permission> permissions = EnumSet.of(Permission.DECLARE, Permission.GRANARY);
		if (!com.ajustor.minecities.McConfig.get().protectBuildings) {
			permissions.add(Permission.BREAK);
		}
		return permissions;
	}

	public Map<UUID, String> residents() {
		return Map.copyOf(residents);
	}

	/** Les habitants joueurs, dans l'ordre d'arrivée. */
	public List<Map.Entry<UUID, String>> residentList() {
		return List.copyOf(residents.entrySet());
	}

	public boolean isResident(UUID player) {
		return residents.containsKey(player);
	}

	public boolean addResident(UUID player, String name) {
		requests.remove(player);
		return residents.put(player, name) == null;
	}

	public boolean removeResident(UUID player) {
		return residents.remove(player) != null;
	}

	/** Les demandes en attente, dans l'ordre. */
	public List<Map.Entry<UUID, String>> requests() {
		return List.copyOf(requests.entrySet());
	}

	public boolean hasRequested(UUID player) {
		return requests.containsKey(player);
	}

	public boolean request(UUID player, String name) {
		return requests.putIfAbsent(player, name) == null;
	}

	public boolean dropRequest(UUID player) {
		return requests.remove(player) != null;
	}

	/** Les droits d'un rang. Le maire et ses adjoints les ont tous. */
	public Set<Permission> permissions(Rank rank) {
		return switch (rank) {
			case MAYOR, DEPUTY -> EnumSet.allOf(Permission.class);
			case RESIDENT -> EnumSet.copyOf(resident);
			case VISITOR -> EnumSet.copyOf(visitor);
		};
	}

	/** Donne ou retire un droit à l'habitant ou au visiteur ; faux si rien n'a changé. */
	public boolean set(Rank rank, Permission permission, boolean allowed) {
		Set<Permission> permissions = switch (rank) {
			case RESIDENT -> resident;
			case VISITOR -> visitor;
			default -> null;
		};
		if (permissions == null) {
			return false;
		}
		return allowed ? permissions.add(permission) : permissions.remove(permission);
	}
}
