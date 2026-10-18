package com.ajustor.minecities.town;

import com.ajustor.minecities.build.Construction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Une ville : son hôtel de ville, son maire et ses adjoints, ses maisons et ses citoyens. Elle
 * grandit de palier en palier ({@link TownTier}) avec sa population.
 *
 * <p>Les citoyens sont tenus ici par leur UUID, chargés ou non ; leur entité garde le reste (faim,
 * apparence).
 */
public final class Town {
	/** L'état civil de la ville (à part : un codec ne prend que 16 champs à la fois). */
	private record Head(UUID id, String name, ResourceKey<Level> dimension, BlockPos hall, UUID mayor,
			String mayorName, List<UUID> deputies, long founded, Membership membership, Election election,
			Map<UUID, BlockPos> whereabouts) {
		static final MapCodec<Head> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				UUIDUtil.CODEC.fieldOf("id").forGetter(Head::id),
				Codec.STRING.fieldOf("name").forGetter(Head::name),
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Head::dimension),
				BlockPos.CODEC.fieldOf("hall").forGetter(Head::hall),
				UUIDUtil.CODEC.fieldOf("mayor").forGetter(Head::mayor),
				Codec.STRING.optionalFieldOf("mayor_name", "").forGetter(Head::mayorName),
				UUIDUtil.CODEC.listOf().optionalFieldOf("deputies", List.of()).forGetter(Head::deputies),
				Codec.LONG.optionalFieldOf("founded", 0L).forGetter(Head::founded),
				Membership.CODEC.forGetter(Head::membership),
				Election.CODEC.forGetter(Head::election),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, BlockPos.CODEC).optionalFieldOf("whereabouts", Map.of())
						.forGetter(Head::whereabouts)
		).apply(i, Head::new));
	}

	public static final Codec<Town> CODEC = RecordCodecBuilder.create(i -> i.group(
			Head.CODEC.forGetter(t -> new Head(t.id, t.name, t.dimension, t.hall, t.mayor, t.mayorName,
					List.copyOf(t.deputies), t.founded, t.membership, t.election, Map.copyOf(t.whereabouts))),
			House.CODEC.listOf().optionalFieldOf("houses", List.of()).forGetter(t -> List.copyOf(t.houses.values())),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Resident.CODEC).optionalFieldOf("residents", Map.of())
					.forGetter(t -> t.residents),
			Codec.STRING.optionalFieldOf("tier", TownTier.VILLAGE.serializedName())
					.forGetter(t -> t.tier.serializedName()),
			Codec.LONG.optionalFieldOf("last_arrival", 0L).forGetter(t -> t.lastArrival),
			Codec.INT.optionalFieldOf("villagers", 0).forGetter(t -> t.villagers),
			Construction.CODEC.listOf().optionalFieldOf("constructions", List.of())
					.forGetter(t -> List.copyOf(t.constructions)),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("affinities", Map.of())
					.forGetter(t -> t.affinities),
			MarriageRequest.CODEC.listOf().optionalFieldOf("marriage_requests", List.of())
					.forGetter(t -> List.copyOf(t.marriageRequests)),
			Chronicle.CODEC.forGetter(t -> t.chronicle),
			BlockPos.CODEC.listOf().optionalFieldOf("annexes", List.of()).forGetter(t -> List.copyOf(t.annexes)),
			Codec.STRING.optionalFieldOf("forced_tier").forGetter(t -> t.forcedTier.map(TownTier::serializedName)),
			Codec.LONG.optionalFieldOf("orphaned_at").forGetter(t -> t.orphanedAt),
			Mine.CODEC.listOf().optionalFieldOf("mines", List.of()).forGetter(t -> List.copyOf(t.mines.values())),
			Codec.STRING.listOf().optionalFieldOf("decrees", List.of())
					.forGetter(t -> t.decrees.stream().map(Decree::serializedName).toList()),
			UUIDUtil.CODEC.listOf().optionalFieldOf("partners", List.of()).forGetter(t -> List.copyOf(t.partners))
	).apply(i, (head, houses, residents, tier, lastArrival, villagers, constructions, affinities, requests,
			chronicle, annexes, forced, orphaned, mines, decrees, partners) -> {
		Town town = new Town(head.id(), head.name(), head.dimension(), head.hall(), head.mayor(),
				head.mayorName(), head.deputies(), head.founded(), houses, residents, tier, lastArrival, villagers,
				constructions, affinities, requests, chronicle);
		town.annexes.addAll(annexes);
		town.forcedTier = forced.map(TownTier::fromSerializedName);
		town.orphanedAt = orphaned;
		mines.forEach(m -> town.mines.put(m.station(), m));
		decrees.forEach(d -> Decree.fromSerializedName(d).ifPresent(town.decrees::add));
		town.partners.addAll(partners);
		town.membership = head.membership();
		town.election = head.election();
		town.whereabouts.putAll(head.whereabouts());
		return town;
	}));

	/** Une mairie annexe étend le territoire de tant de blocs autour d'elle. */
	public static final int ANNEX_RADIUS = 48;
	/** Une annexe se pose à tant de blocs au moins de l'hôtel de ville (et des autres annexes). */
	public static final int ANNEX_SPACING = 40;

	/** Au-delà de cette affinité, deux citoyens s'entendent. */
	public static final int FRIENDS = 70;

	private final UUID id;
	private String name;
	private final ResourceKey<Level> dimension;
	private final BlockPos hall;
	private UUID mayor;
	private String mayorName;
	private final Set<UUID> deputies;
	private final long founded;
	private final Map<BlockPos, House> houses = new LinkedHashMap<>();
	private final Map<UUID, Resident> residents;
	private TownTier tier;
	private long lastArrival;
	/** Les villageois vanilla qui vivent sur le territoire : ils comptent dans la population. */
	private int villagers;
	/** Les lits où dorment ces villageois : on n'y loge pas de citoyen (non sauvegardé). */
	private Set<BlockPos> reservedBeds = Set.of();
	/** L'affinité de chaque paire de citoyens (clé : les deux UUID, dans l'ordre), de 0 à 100. */
	private final Map<String, Integer> affinities;
	/** Les demandes de mariage en attente de la décision du maire. */
	private final List<MarriageRequest> marriageRequests;
	/** Les chantiers commandés par le maire, en cours puis finis, dans l'ordre de commande. */
	private final List<Construction> constructions;
	/** Les décrets en vigueur. */
	private final Set<Decree> decrees = java.util.EnumSet.noneOf(Decree.class);
	/** Les villes avec lesquelles elle commerce (brief 07). */
	private final Set<UUID> partners = new java.util.LinkedHashSet<>();
	/** Les galeries des mineurs, par treuil. */
	private final Map<BlockPos, Mine> mines = new LinkedHashMap<>();
	/** Depuis quand la ville a perdu son hôtel de ville (détruit), s'il faut en reposer un. */
	private Optional<Long> orphanedAt = Optional.empty();
	/** Quand chaque citoyen a été vu pour la dernière fois (non sauvegardé). */
	private final Map<UUID, Long> seen = new HashMap<>();
	/** Où chaque citoyen a été vu pour la dernière fois : on ne le cherche que là où il peut être. */
	private final Map<UUID, BlockPos> whereabouts = new HashMap<>();
	/** Le palier imposé par un opérateur (serveurs, essais), au lieu de celui de la population. */
	private Optional<TownTier> forcedTier = Optional.empty();
	/** Les mairies annexes : elles étendent le territoire et servent de greniers secondaires. */
	private final List<BlockPos> annexes = new ArrayList<>();
	/** Ce qui s'est passé dans la ville : morts, fêtes, alerte. */
	private final Chronicle chronicle;
	/** Les joueurs de la ville : habitants, demandes, droits de chaque rang. */
	private Membership membership = Membership.founded();
	/** Le mandat du maire et l'élection en cours. */
	private Election election = Election.none();
	/** L'humeur de chaque citoyen, à son dernier calcul (non sauvegardé : l'entité la recalcule). */
	private final Map<UUID, Integer> moods = new HashMap<>();

	private Town(UUID id, String name, ResourceKey<Level> dimension, BlockPos hall, UUID mayor, String mayorName,
			List<UUID> deputies, long founded, List<House> houses, Map<UUID, Resident> residents, String tier,
			long lastArrival, int villagers, List<Construction> constructions, Map<String, Integer> affinities,
			List<MarriageRequest> marriageRequests, Chronicle chronicle) {
		this.id = id;
		this.name = name;
		this.dimension = dimension;
		this.hall = hall;
		this.mayor = mayor;
		this.mayorName = mayorName;
		this.deputies = new HashSet<>(deputies);
		this.founded = founded;
		houses.forEach(h -> this.houses.put(h.plaque(), h));
		this.residents = new LinkedHashMap<>(residents);
		this.tier = TownTier.fromSerializedName(tier);
		this.lastArrival = lastArrival;
		this.villagers = villagers;
		this.constructions = new ArrayList<>(constructions);
		this.affinities = new HashMap<>(affinities);
		this.marriageRequests = new ArrayList<>(marriageRequests);
		this.chronicle = chronicle;
	}

	public static Town found(String name, ResourceKey<Level> dimension, BlockPos hall, UUID mayor, String mayorName,
			long now) {
		return new Town(UUID.randomUUID(), name, dimension, hall, mayor, mayorName, List.of(), now, List.of(),
				Map.of(), TownTier.VILLAGE.serializedName(), now, 0, List.of(), Map.of(), List.of(), Chronicle.empty());
	}

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	public void rename(String name) {
		this.name = name;
	}

	public ResourceKey<Level> dimension() {
		return dimension;
	}

	public BlockPos hall() {
		return hall;
	}

	public UUID mayor() {
		return mayor;
	}

	public String mayorName() {
		return mayorName;
	}

	public void setMayor(UUID mayor, String mayorName) {
		this.mayor = mayor;
		this.mayorName = mayorName;
		deputies.remove(mayor);
	}

	public Set<UUID> deputies() {
		return Set.copyOf(deputies);
	}

	public boolean addDeputy(UUID player) {
		return !player.equals(mayor) && deputies.add(player);
	}

	public boolean removeDeputy(UUID player) {
		return deputies.remove(player);
	}

	/** Le maire et ses adjoints gèrent la ville. */
	public boolean canManage(UUID player) {
		return mayor.equals(player) || deputies.contains(player);
	}

	// Joueurs

	public Membership membership() {
		return membership;
	}

	/** Le rang de ce joueur dans la ville. */
	public Membership.Rank rank(UUID player) {
		if (mayor.equals(player)) {
			return Membership.Rank.MAYOR;
		}
		if (deputies.contains(player)) {
			return Membership.Rank.DEPUTY;
		}
		return membership.isResident(player) ? Membership.Rank.RESIDENT : Membership.Rank.VISITOR;
	}

	public Election election() {
		return election;
	}

	// Monuments

	/** Les monuments bâtis (les chantiers finis de leurs schémas). */
	public java.util.Set<Monument> monuments() {
		java.util.Set<Monument> built = java.util.EnumSet.noneOf(Monument.class);
		for (com.ajustor.minecities.build.Construction construction : constructions) {
			if (construction.done()) {
				Monument.of(construction.blueprint()).ifPresent(built::add);
			}
		}
		return built;
	}

	/** L'attrait de la ville : ce que ses monuments lui donnent. */
	public int appeal() {
		return monuments().stream().mapToInt(Monument::appeal).sum();
	}

	/** Ce joueur appartient-il à la ville (maire, adjoint ou habitant) ? */
	public boolean isMember(UUID player) {
		return rank(player) != Membership.Rank.VISITOR;
	}

	/** Ce joueur a-t-il ce droit dans la ville ? */
	public boolean may(UUID player, Membership.Permission permission) {
		return membership.permissions(rank(player)).contains(permission);
	}

	public long founded() {
		return founded;
	}

	public TownTier tier() {
		return tier;
	}

	public int radius() {
		return tier.radius();
	}

	public long lastArrival() {
		return lastArrival;
	}

	/**
	 * Dans le territoire de la ville : un disque autour de l'hôtel de ville, et un autre autour de
	 * chacune de ses mairies annexes en service, sur toute la hauteur.
	 */
	public boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
		if (!this.dimension.equals(dimension)) {
			return false;
		}
		if (within(hall, pos, radius())) {
			return true;
		}
		for (BlockPos annex : activeAnnexes()) {
			if (within(annex, pos, ANNEX_RADIUS)) {
				return true;
			}
		}
		return false;
	}

	private static boolean within(BlockPos center, BlockPos pos, int radius) {
		long dx = pos.getX() - center.getX();
		long dz = pos.getZ() - center.getZ();
		return dx * dx + dz * dz <= (long) radius * radius;
	}

	// Annexes

	/** Toutes les annexes posées. */
	public List<BlockPos> annexes() {
		return List.copyOf(annexes);
	}

	/** Les annexes en service : autant que le palier en permet (les premières posées). */
	public List<BlockPos> activeAnnexes() {
		return annexes.subList(0, Math.min(annexes.size(), tier.annexes()));
	}

	/** Pourquoi on ne peut pas poser d'annexe ici ; vide si on peut. */
	public Optional<String> annexRefusal(ResourceKey<Level> dimension, BlockPos pos) {
		if (annexes.size() >= tier.annexes()) {
			return Optional.of(tier.annexes() == 0 ? "tier" : "too_many");
		}
		if (!contains(dimension, pos)) {
			return Optional.of("outside");
		}
		if (within(hall, pos, ANNEX_SPACING - 1) || annexes.stream().anyMatch(a -> within(a, pos, ANNEX_SPACING - 1))) {
			return Optional.of("too_close");
		}
		return Optional.empty();
	}

	public void addAnnex(BlockPos pos) {
		annexes.add(pos.immutable());
	}

	public boolean removeAnnex(BlockPos pos) {
		return annexes.remove(pos);
	}

	/** La distance horizontale entre l'hôtel de ville et ce point. */
	public double distance(BlockPos pos) {
		double dx = pos.getX() - hall.getX();
		double dz = pos.getZ() - hall.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// Maisons

	public List<House> houses() {
		return List.copyOf(houses.values());
	}

	public Optional<House> house(BlockPos plaque) {
		return Optional.ofNullable(houses.get(plaque));
	}

	/** Une maison de la ville (n'importe laquelle) a-t-elle ce lit ? */
	public boolean hasBed(BlockPos bed) {
		return houses.values().stream().anyMatch(h -> h.beds().contains(bed));
	}

	/** Les coffres et tonneaux des entrepôts de la ville (brief 12). */
	public List<BlockPos> warehouseContainers() {
		return houses.values().stream().filter(House::warehouse).flatMap(h -> h.containers().stream()).toList();
	}

	/** Une autre maison de la ville a-t-elle déjà ce lit ? */
	public boolean bedClaimedElsewhere(BlockPos bed, BlockPos plaque) {
		return houses.values().stream().anyMatch(h -> !h.plaque().equals(plaque) && h.beds().contains(bed));
	}

	/** Les bâtiments où l'on dort ; ce sont eux qui comptent pour le palier. */
	public List<House> homes() {
		return houses.values().stream().filter(House::isHome).toList();
	}

	/**
	 * Enregistre (ou relève à nouveau) un bâtiment. Les habitants dont le lit a disparu se retrouvent
	 * sans logis, les travailleurs dont le poste a disparu sans emploi.
	 */
	public void putHouse(House house) {
		houses.put(house.plaque(), house);
		List<BlockPos> stations = house.workstations().stream().map(House.Workstation::pos).toList();
		residents.replaceAll((uuid, r) -> {
			if (r.home().filter(house.plaque()::equals).isPresent()
					&& r.bed().filter(house.beds()::contains).isEmpty()) {
				r = r.evicted();
			}
			if (r.job().filter(j -> j.workshop().equals(house.plaque()) && !stations.contains(j.station())).isPresent()) {
				r = r.unemployed();
			}
			return r;
		});
	}

	/**
	 * Oublie un bâtiment ; ses habitants se retrouvent sans logis, ses travailleurs sans emploi.
	 * Renvoie les UUID des habitants.
	 */
	public List<UUID> removeHouse(BlockPos plaque) {
		List<UUID> evicted = new ArrayList<>();
		if (houses.remove(plaque) != null) {
			residents.replaceAll((uuid, r) -> {
				if (r.home().filter(plaque::equals).isPresent()) {
					evicted.add(uuid);
					r = r.evicted();
				}
				if (r.job().filter(j -> j.workshop().equals(plaque)).isPresent()) {
					r = r.unemployed();
				}
				return r;
			});
		}
		return evicted;
	}

	/**
	 * Donne les postes de travail libres aux citoyens sans emploi, dans l'ordre d'arrivée. Renvoie
	 * les nouveaux emplois.
	 */
	public Map<UUID, Resident.Employment> assignJobs() {
		Set<BlockPos> taken = new HashSet<>();
		residents.values().forEach(r -> r.job().ifPresent(j -> taken.add(j.station())));
		Map<UUID, Resident.Employment> hired = new LinkedHashMap<>();
		for (House house : houses.values()) {
			for (House.Workstation station : house.workstations()) {
				// Un poste dont le métier n'est pas encore ouvert à ce palier attend.
				if (taken.contains(station.pos()) || !station.job().unlockedAt(tier)) {
					continue;
				}
				Optional<UUID> jobless = residents.entrySet().stream()
						.filter(e -> e.getValue().job().isEmpty() && !e.getValue().isChild())
						.map(Map.Entry::getKey).findFirst();
				if (jobless.isEmpty()) {
					return hired;
				}
				Resident.Employment employment = new Resident.Employment(station.job(), house.plaque(), station.pos());
				residents.put(jobless.get(), residents.get(jobless.get()).employed(employment));
				taken.add(station.pos());
				hired.put(jobless.get(), employment);
			}
		}
		return hired;
	}

	/** Combien de citoyens exercent ce métier. */
	public int workers(Job job) {
		return (int) residents.values().stream().filter(r -> r.job().filter(j -> j.job() == job).isPresent()).count();
	}

	/** Les postes de travail de la ville, et combien sont occupés. */
	public int workstations() {
		return houses.values().stream().mapToInt(h -> h.workstations().size()).sum();
	}

	public int capacity() {
		return houses.values().stream().mapToInt(House::capacity).sum();
	}

	public int freeBeds() {
		Set<BlockPos> taken = new HashSet<>(reservedBeds);
		residents.values().forEach(r -> r.bed().ifPresent(taken::add));
		return (int) houses.values().stream().flatMap(h -> h.beds().stream()).filter(b -> !taken.contains(b)).count();
	}

	/** Les lits des villageois vanilla du territoire, qu'aucun citoyen ne prendra. */
	public void reserveBeds(Set<BlockPos> beds) {
		reservedBeds = Set.copyOf(beds);
	}

	/** Un lit libre, et la maison où il se trouve. */
	public Optional<Map.Entry<House, BlockPos>> findFreeBed() {
		Set<BlockPos> taken = new HashSet<>(reservedBeds);
		residents.values().forEach(r -> r.bed().ifPresent(taken::add));
		for (House house : houses.values()) {
			for (BlockPos bed : house.beds()) {
				if (!taken.contains(bed)) {
					return Optional.of(Map.entry(house, bed));
				}
			}
		}
		return Optional.empty();
	}

	// Citoyens

	public Map<UUID, Resident> residents() {
		return Map.copyOf(residents);
	}

	public Optional<Resident> resident(UUID citizen) {
		return Optional.ofNullable(residents.get(citizen));
	}

	/** Les villageois vanilla du territoire, au dernier recensement. */
	public int villagers() {
		return villagers;
	}

	/** Recense les villageois du territoire ; renvoie vrai si leur nombre a changé. */
	public boolean setVillagers(int villagers) {
		if (this.villagers == villagers) {
			return false;
		}
		this.villagers = villagers;
		return true;
	}

	/** Les citoyens du mod, sans les villageois. */
	public int citizens() {
		return residents.size();
	}

	/** Toute la population : les citoyens et les villageois vanilla du territoire. */
	public int population() {
		return residents.size() + villagers;
	}

	/** Accueille un nouveau citoyen, et lui donne un lit s'il en reste un. */
	public Resident welcome(UUID citizen, String name, long now) {
		Resident resident = Resident.homeless(name);
		residents.put(citizen, resident);
		lastArrival = now;
		return house(citizen).orElse(resident);
	}

	/** Donne un lit libre à un citoyen sans logis. */
	public Optional<Resident> house(UUID citizen) {
		Resident resident = residents.get(citizen);
		if (resident == null || resident.hasHome()) {
			return Optional.ofNullable(resident);
		}
		return findFreeBed().map(e -> {
			Resident housed = resident.housed(e.getKey().plaque(), e.getValue());
			residents.put(citizen, housed);
			return housed;
		});
	}

	public void farewell(UUID citizen) {
		moods.remove(citizen);
		seen.remove(citizen);
		whereabouts.remove(citizen);
		Resident gone = residents.remove(citizen);
		if (gone != null) {
			gone.spouse().ifPresent(s -> residents.computeIfPresent(s, (id, r) -> r.widowed()));
		}
		marriageRequests.removeIf(r -> r.concerns(citizen));
		affinities.keySet().removeIf(k -> k.contains(citizen.toString()));
	}

	// Humeur

	public Chronicle chronicle() {
		return chronicle;
	}

	/** Note l'humeur d'un citoyen (son entité la recalcule régulièrement). */
	public void setMood(UUID citizen, int mood) {
		if (residents.containsKey(citizen)) {
			moods.put(citizen, mood);
		}
	}

	public Optional<Integer> mood(UUID citizen) {
		return Optional.ofNullable(moods.get(citizen));
	}

	/** Le bonheur moyen de la ville, sur les citoyens dont on connaît l'humeur ; vide si aucun. */
	public Optional<Double> averageMood() {
		return moods.isEmpty() ? Optional.empty()
				: Optional.of(moods.values().stream().mapToInt(Integer::intValue).average().orElse(0));
	}

	// Familles

	private static String pair(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	public int affinity(UUID a, UUID b) {
		return affinities.getOrDefault(pair(a, b), 0);
	}

	/** Fait varier l'affinité de deux citoyens ; renvoie la nouvelle valeur. */
	public int addAffinity(UUID a, UUID b, int delta) {
		int value = Math.clamp(affinity(a, b) + delta, 0, 100);
		affinities.put(pair(a, b), value);
		return value;
	}

	public List<MarriageRequest> marriageRequests() {
		return List.copyOf(marriageRequests);
	}

	/** Peuvent-ils demander à se marier : adultes, célibataires, qui s'entendent, sans demande en cours ? */
	public boolean canAskToMarry(UUID a, UUID b) {
		Resident ra = residents.get(a);
		Resident rb = residents.get(b);
		return ra != null && rb != null && !a.equals(b) && !ra.isChild() && !rb.isChild()
				&& ra.spouse().isEmpty() && rb.spouse().isEmpty() && affinity(a, b) >= FRIENDS
				&& marriageRequests.stream().noneMatch(r -> r.concerns(a) || r.concerns(b));
	}

	public Optional<MarriageRequest> askToMarry(UUID a, UUID b, long now) {
		if (!canAskToMarry(a, b)) {
			return Optional.empty();
		}
		MarriageRequest request = new MarriageRequest(UUID.randomUUID(), a, b, now);
		marriageRequests.add(request);
		return Optional.of(request);
	}

	public Optional<MarriageRequest> marriageRequest(UUID id) {
		return marriageRequests.stream().filter(r -> r.id().equals(id)).findFirst();
	}

	/** Le maire refuse : la demande tombe, et leur affinité baisse un peu. */
	public void refuse(MarriageRequest request) {
		marriageRequests.remove(request);
		addAffinity(request.first(), request.second(), -20);
	}

	/**
	 * Le maire accepte : ils sont mariés, et emménagent ensemble si une maison a deux lits pour eux.
	 * Renvoie faux si l'un d'eux n'est plus là.
	 */
	public boolean marry(MarriageRequest request) {
		marriageRequests.remove(request);
		Resident a = residents.get(request.first());
		Resident b = residents.get(request.second());
		if (a == null || b == null) {
			return false;
		}
		residents.put(request.first(), a.married(request.second()));
		residents.put(request.second(), b.married(request.first()));
		moveInTogether(request.first(), request.second());
		return true;
	}

	/** Les deux conjoints dans une même maison, s'il y en a une avec deux lits pour eux. */
	public boolean moveInTogether(UUID a, UUID b) {
		Resident ra = residents.get(a);
		Resident rb = residents.get(b);
		if (ra.home().isPresent() && ra.home().equals(rb.home())) {
			return true;
		}
		Set<BlockPos> taken = new HashSet<>(reservedBeds);
		residents.forEach((id, r) -> {
			if (!id.equals(a) && !id.equals(b)) {
				r.bed().ifPresent(taken::add);
			}
		});
		for (House house : houses.values()) {
			List<BlockPos> free = house.beds().stream().filter(bed -> !taken.contains(bed)).toList();
			if (free.size() >= 2) {
				residents.put(a, ra.housed(house.plaque(), free.get(0)));
				residents.put(b, rb.housed(house.plaque(), free.get(1)));
				return true;
			}
		}
		return false;
	}

	/** Les couples mariés, une fois chacun. */
	public List<UUID[]> couples() {
		List<UUID[]> couples = new ArrayList<>();
		residents.forEach((id, r) -> r.spouse().filter(s -> id.compareTo(s) < 0 && residents.containsKey(s))
				.ifPresent(s -> couples.add(new UUID[]{id, s})));
		return couples;
	}

	/** Un lit libre dans cette maison. */
	public Optional<BlockPos> freeBedIn(BlockPos home) {
		Set<BlockPos> taken = new HashSet<>(reservedBeds);
		residents.values().forEach(r -> r.bed().ifPresent(taken::add));
		return house(home).flatMap(h -> h.beds().stream().filter(bed -> !taken.contains(bed)).findFirst());
	}

	/** Un enfant naît d'un couple : il habite la maison de ses parents, et grandit. */
	public Resident born(UUID child, String name, UUID parentA, UUID parentB, long now, long grownUpAt) {
		Resident a = residents.get(parentA);
		Resident baby = Resident.child(name, grownUpAt);
		if (a != null && a.home().isPresent()) {
			BlockPos home = a.home().get();
			baby = freeBedIn(home).map(bed -> Resident.child(name, grownUpAt).housed(home, bed)).orElse(baby);
		}
		residents.put(child, baby);
		residents.computeIfPresent(parentA, (id, r) -> r.gaveBirth(now));
		residents.computeIfPresent(parentB, (id, r) -> r.gaveBirth(now));
		return baby;
	}

	/** Les enfants arrivés à l'âge adulte le deviennent ; renvoie leurs UUID. */
	public List<UUID> growUp(long now) {
		List<UUID> grown = new ArrayList<>();
		residents.replaceAll((id, r) -> {
			if (r.grownUpAt().filter(t -> t <= now).isPresent()) {
				grown.add(id);
				return r.grownUp();
			}
			return r;
		});
		return grown;
	}

	// Chantiers

	public List<Construction> constructions() {
		return List.copyOf(constructions);
	}

	/** Le chantier en cours : le premier commandé qui n'est pas fini. */
	public Optional<Construction> activeConstruction() {
		return constructions.stream().filter(c -> !c.done()).findFirst();
	}

	public void order(Construction construction) {
		constructions.add(construction);
	}

	public boolean cancel(Construction construction) {
		return constructions.remove(construction);
	}

	/** Le bâtiment bâti par la ville à cet endroit, s'il y en a un. */
	public Optional<Construction> builtAt(BlockPos pos) {
		return constructions.stream().filter(c -> c.done() && c.contains(pos)).reduce((a, b) -> b);
	}

	// Décrets et commerce

	public boolean decreed(Decree decree) {
		return decrees.contains(decree);
	}

	public Set<Decree> decrees() {
		return java.util.Collections.unmodifiableSet(decrees);
	}

	/** Prend ou lève un décret ; renvoie vrai s'il est désormais en vigueur. */
	public boolean toggle(Decree decree) {
		if (!decrees.remove(decree)) {
			decrees.add(decree);
			return true;
		}
		return false;
	}

	/** Ce que les décrets en vigueur font au moral. */
	public int decreeMood() {
		return decrees.stream().mapToInt(Decree::mood).sum();
	}

	public Set<UUID> partners() {
		return java.util.Collections.unmodifiableSet(partners);
	}

	public void addPartner(UUID town) {
		partners.add(town);
	}

	public boolean removePartner(UUID town) {
		return partners.remove(town);
	}

	/** La galerie creusée depuis ce treuil, s'il y en a une. */
	public Optional<Mine> mine(BlockPos station) {
		return Optional.ofNullable(mines.get(station));
	}

	public void putMine(Mine mine) {
		mines.put(mine.station(), mine);
	}

	public Optional<Long> orphanedAt() {
		return orphanedAt;
	}

	/** L'hôtel de ville a été détruit (ou reposé : vide). */
	public void orphan(Optional<Long> since) {
		orphanedAt = since;
	}

	/** Ce citoyen a été vu à l'instant, à cet endroit. */
	public void seen(UUID citizen, long now, BlockPos at) {
		seen.put(citizen, now);
		whereabouts.put(citizen, at.immutable());
	}

	/** Là où ce citoyen a été vu pour la dernière fois, si on le sait. */
	public Optional<BlockPos> whereabouts(UUID citizen) {
		return Optional.ofNullable(whereabouts.get(citizen));
	}

	/** Quand ce citoyen a été vu pour la dernière fois (à défaut : maintenant, et l'on s'en souvient). */
	public long lastSeen(UUID citizen, long now) {
		return seen.computeIfAbsent(citizen, id -> now);
	}

	/** Impose un palier (vide : le palier suit à nouveau la population). */
	public void forceTier(Optional<TownTier> tier) {
		forcedTier = tier;
	}

	public Optional<TownTier> forcedTier() {
		return forcedTier;
	}

	/**
	 * Le niveau du bâtiment de cette plaque (brief 11) : celui du schéma d'après lequel la ville l'a
	 * bâti ; un bâtiment bâti à la main vaut le niveau 1.
	 */
	public int buildingLevel(BlockPos plaque) {
		return builtAt(plaque).map(c -> com.ajustor.minecities.build.Blueprint.level(c.blueprint())).orElse(1);
	}

	/** Recalcule le palier ; renvoie l'ancien s'il a changé. */
	public Optional<TownTier> updateTier() {
		TownTier now = forcedTier.orElseGet(() -> TownTier.of(population(), homes().size()));
		if (now == tier) {
			return Optional.empty();
		}
		TownTier before = tier;
		tier = now;
		return Optional.of(before);
	}
}
