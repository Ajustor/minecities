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
 * Ce que l'écran de l'hôtel de ville montre d'une ville, envoyé au joueur à l'ouverture : le résumé
 * (avec le moral de la ville, ses fêtes et l'alerte), les citoyens, les bâtiments, et le registre
 * des demandes de mariage. Les textes sont des clés de traduction, traduites côté client.
 */
public record TownSnapshot(BlockPos hall, String name, String tier, int population, int citizens, int villagers,
		int capacity, int homes, int workstations, int employed, int meals, String nextTier, int nextPopulation,
		int nextHouses, boolean manager, Life life, Council council, List<CitizenRow> rows, List<BuildingRow> buildings,
		List<RequestRow> requests, List<PlanRow> plans, Members members, Ballot ballot, TownMap map) {
	/**
	 * La mairie : le maire, ses adjoints, les décrets (clé de chaque décret, et s'il est en vigueur), les
	 * villes partenaires (nom, et si l'accord est signé des deux côtés).
	 */
	/**
	 * L'onglet Mairie. Le budget (MC-57) : s'il est en service, le taux de l'impôt et ce qu'il demande à
	 * chaque adulte, l'impôt perçu, les salaires et les chantiers du jour, et les émeraudes de la boîte à
	 * impôts (-1 sans boîte).
	 */
	public record Council(String mayor, List<String> deputies, List<String> decrees, List<Boolean> decreed,
			List<String> partners, List<Boolean> open, boolean budget, String tax, int perAdult, int taxes, int wages,
			int works, int taxBox, List<String> towns, List<Boolean> offered, boolean mayorView) {
	}

	/**
	 * Le moral de la ville : bonheur moyen (en dixièmes, -1 si inconnu), la fête (en cours, possible,
	 * dans combien de ticks sinon, et son prix), l'alerte.
	 */
	/**
	 * La vie de la ville. La croissance (MC-58) : ce qui se passe ({@code growth} : newcomers, tavern,
	 * no_tavern, no_bed, unhappy), dans combien de jours arrive le prochain (ou le minimum d'habitants), et
	 * le niveau de la taverne.
	 */
	public record Life(int mood, boolean partying, boolean canCelebrate, long untilFestival, int festivalCost,
			boolean alarmed, int treasury, int earnedToday, int annexes, int maxAnnexes, int appeal, String disaster,
			String growth, int growthDays, int tavernLevel, List<String> worries, int jobs, int nextJobs,
			int nextMood, String nextBuilding, boolean hasBuilding) {
	}

	/**
	 * Les joueurs de la ville (onglet Habitants) : le rang de celui qui regarde (et s'il a demandé à
	 * s'installer), les joueurs de la ville avec leur rang, les demandes en attente, et les droits de
	 * l'habitant et du visiteur (dans l'ordre de {@code Membership.Permission}).
	 */
	public record Members(String rank, boolean requested, List<PlayerRow> players, List<PlayerRow> requests,
			List<Boolean> resident, List<Boolean> visitor) {
	}

	/**
	 * L'élection (onglet Élection) : la phase ({@code none}, {@code candidacy}, {@code voting}), le temps
	 * qui lui reste et celui du mandat (en ticks, -1 sans échéance), le tour, les candidats, si celui qui
	 * regarde a voté, la pétition (signatures, celles qu'il faut, s'il a signé), le temps avant qu'une
	 * élection puisse s'ouvrir, et le dernier élu (vide si aucun) avec ses voix.
	 */
	public record Ballot(String phase, long endsIn, long termIn, int round, List<PlayerRow> candidates, boolean voted,
			int signatures, int needed, boolean signed, long allowedIn, String lastWinner, int lastVotes) {
	}

	/**
	 * La carte du territoire (MC-13) : le rayon autour de l'hôtel de ville, les mairies annexes en
	 * service et leur rayon, et les bâtiments de la ville.
	 */
	public record TownMap(int radius, List<BlockPos> annexes, int annexRadius, List<Plot> plots) {
	}

	/**
	 * Un bâtiment sur la carte : sa plaque (ou l'origine d'un chantier), son emprise au sol, et son genre
	 * ({@code home}, {@code workshop}, {@code both}, {@code village}, {@code warehouse}, {@code monument},
	 * {@code site} pour un chantier en cours).
	 */
	public record Plot(BlockPos at, int minX, int minZ, int maxX, int maxZ, String kind) {
	}

	/** Un joueur de la ville : son UUID, son nom, son rang. */
	public record PlayerRow(UUID id, String name, String rank) {
	}

	/** Un citoyen : nom, métier et faim (clés), logé ou non, conjoint, enfant, humeur (-1 si inconnue), besoin. */
	public record CitizenRow(UUID id, String name, String job, String hunger, boolean housed, String spouse,
			boolean child, int mood, Optional<Component> need, List<String> factors) {
	}

	/**
	 * Un bâtiment : sa plaque, ses lits (et combien sont pris), ses métiers (clés ; « ! » devant un
	 * métier pas encore ouvert à ce palier), maison de village ou non, monument ou non (son nom est
	 * alors le premier « métier »).
	 */
	public record BuildingRow(BlockPos plaque, int beds, int sleepers, List<String> jobs, boolean village,
			boolean monument) {
	}

	/** Une demande de mariage en attente. */
	public record RequestRow(UUID id, String first, String second) {
	}

	/**
	 * Un schéma qu'on peut commander (onglet Plans) : son identifiant, son nom (clé de traduction, et
	 * le nom à montrer sans traduction), ses dimensions, maison ou atelier, et le palier qu'il attend
	 * (vide s'il est ouvert).
	 */
	public record PlanRow(String id, String key, String fallback, int width, int depth, int height, String kind,
			String locked) {
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
		buf.writeInt(s.life.mood());
		buf.writeBoolean(s.life.partying());
		buf.writeBoolean(s.life.canCelebrate());
		buf.writeVarLong(s.life.untilFestival());
		buf.writeVarInt(s.life.festivalCost());
		buf.writeBoolean(s.life.alarmed());
		buf.writeVarInt(s.life.treasury());
		buf.writeVarInt(s.life.earnedToday());
		buf.writeVarInt(s.life.annexes());
		buf.writeVarInt(s.life.maxAnnexes());
		buf.writeVarInt(s.life.appeal());
		buf.writeUtf(s.life.disaster());
		buf.writeUtf(s.life.growth());
		buf.writeVarInt(s.life.growthDays());
		buf.writeVarInt(s.life.tavernLevel());
		writeStrings(buf, s.life.worries());
		buf.writeVarInt(s.life.jobs());
		buf.writeVarInt(s.life.nextJobs());
		buf.writeVarInt(s.life.nextMood());
		buf.writeUtf(s.life.nextBuilding());
		buf.writeBoolean(s.life.hasBuilding());
		buf.writeUtf(s.council.mayor());
		writeStrings(buf, s.council.deputies());
		writeStrings(buf, s.council.decrees());
		writeFlags(buf, s.council.decreed());
		writeStrings(buf, s.council.partners());
		writeFlags(buf, s.council.open());
		buf.writeBoolean(s.council.budget());
		buf.writeUtf(s.council.tax());
		buf.writeVarInt(s.council.perAdult());
		buf.writeVarInt(s.council.taxes());
		buf.writeVarInt(s.council.wages());
		buf.writeVarInt(s.council.works());
		buf.writeInt(s.council.taxBox());
		writeStrings(buf, s.council.towns());
		writeFlags(buf, s.council.offered());
		buf.writeBoolean(s.council.mayorView());
		buf.writeVarInt(s.rows.size());
		for (CitizenRow row : s.rows) {
			UUIDUtil.STREAM_CODEC.encode(buf, row.id);
			buf.writeUtf(row.name);
			buf.writeUtf(row.job);
			buf.writeUtf(row.hunger);
			buf.writeBoolean(row.housed);
			buf.writeUtf(row.spouse);
			buf.writeBoolean(row.child);
			buf.writeInt(row.mood);
			NEED.encode(buf, row.need);
			writeStrings(buf, row.factors);
		}
		buf.writeVarInt(s.buildings.size());
		for (BuildingRow building : s.buildings) {
			buf.writeBlockPos(building.plaque);
			buf.writeVarInt(building.beds);
			buf.writeVarInt(building.sleepers);
			buf.writeVarInt(building.jobs.size());
			building.jobs.forEach(buf::writeUtf);
			buf.writeBoolean(building.village);
			buf.writeBoolean(building.monument);
		}
		buf.writeVarInt(s.requests.size());
		for (RequestRow request : s.requests) {
			UUIDUtil.STREAM_CODEC.encode(buf, request.id);
			buf.writeUtf(request.first);
			buf.writeUtf(request.second);
		}
		buf.writeVarInt(s.plans.size());
		for (PlanRow plan : s.plans) {
			buf.writeUtf(plan.id);
			buf.writeUtf(plan.key);
			buf.writeUtf(plan.fallback);
			buf.writeVarInt(plan.width);
			buf.writeVarInt(plan.depth);
			buf.writeVarInt(plan.height);
			buf.writeUtf(plan.kind);
			buf.writeUtf(plan.locked);
		}
		buf.writeUtf(s.members.rank());
		buf.writeBoolean(s.members.requested());
		writePlayers(buf, s.members.players());
		writePlayers(buf, s.members.requests());
		writeFlags(buf, s.members.resident());
		writeFlags(buf, s.members.visitor());
		Ballot b = s.ballot;
		buf.writeUtf(b.phase());
		buf.writeVarLong(b.endsIn() + 1);
		buf.writeVarLong(b.termIn() + 1);
		buf.writeVarInt(b.round());
		writePlayers(buf, b.candidates());
		buf.writeBoolean(b.voted());
		buf.writeVarInt(b.signatures());
		buf.writeVarInt(b.needed());
		buf.writeBoolean(b.signed());
		buf.writeVarLong(b.allowedIn());
		buf.writeUtf(b.lastWinner());
		buf.writeVarInt(b.lastVotes());
		TownMap m = s.map;
		buf.writeVarInt(m.radius());
		buf.writeVarInt(m.annexes().size());
		m.annexes().forEach(buf::writeBlockPos);
		buf.writeVarInt(m.annexRadius());
		buf.writeVarInt(m.plots().size());
		for (Plot p : m.plots()) {
			buf.writeBlockPos(p.at());
			buf.writeInt(p.minX());
			buf.writeInt(p.minZ());
			buf.writeInt(p.maxX());
			buf.writeInt(p.maxZ());
			buf.writeUtf(p.kind());
		}
	}

	private static void writePlayers(RegistryFriendlyByteBuf buf, List<PlayerRow> list) {
		buf.writeVarInt(list.size());
		for (PlayerRow row : list) {
			UUIDUtil.STREAM_CODEC.encode(buf, row.id);
			buf.writeUtf(row.name);
			buf.writeUtf(row.rank);
		}
	}

	private static List<PlayerRow> readPlayers(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<PlayerRow> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(new PlayerRow(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf()));
		}
		return list;
	}

	private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> list) {
		buf.writeVarInt(list.size());
		list.forEach(buf::writeUtf);
	}

	private static void writeFlags(RegistryFriendlyByteBuf buf, List<Boolean> list) {
		buf.writeVarInt(list.size());
		list.forEach(buf::writeBoolean);
	}

	private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<String> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(buf.readUtf());
		}
		return list;
	}

	private static List<Boolean> readFlags(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<Boolean> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(buf.readBoolean());
		}
		return list;
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
		Life life = new Life(buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarLong(), buf.readVarInt(),
				buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), readStrings(buf), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readBoolean());
		Council council = new Council(buf.readUtf(), readStrings(buf), readStrings(buf), readFlags(buf),
				readStrings(buf), readFlags(buf), buf.readBoolean(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readInt(), readStrings(buf), readFlags(buf), buf.readBoolean());
		int count = buf.readVarInt();
		List<CitizenRow> rows = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			rows.add(new CitizenRow(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf(), buf.readUtf(),
					buf.readBoolean(), buf.readUtf(), buf.readBoolean(), buf.readInt(), NEED.decode(buf),
					readStrings(buf)));
		}
		int buildingCount = buf.readVarInt();
		List<BuildingRow> buildings = new ArrayList<>(buildingCount);
		for (int i = 0; i < buildingCount; i++) {
			BlockPos plaque = buf.readBlockPos();
			int beds = buf.readVarInt();
			int sleepers = buf.readVarInt();
			int jobCount = buf.readVarInt();
			List<String> jobs = new ArrayList<>(jobCount);
			for (int j = 0; j < jobCount; j++) {
				jobs.add(buf.readUtf());
			}
			buildings.add(new BuildingRow(plaque, beds, sleepers, jobs, buf.readBoolean(), buf.readBoolean()));
		}
		int requestCount = buf.readVarInt();
		List<RequestRow> requests = new ArrayList<>(requestCount);
		for (int i = 0; i < requestCount; i++) {
			requests.add(new RequestRow(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf()));
		}
		int planCount = buf.readVarInt();
		List<PlanRow> plans = new ArrayList<>(planCount);
		for (int i = 0; i < planCount; i++) {
			plans.add(new PlanRow(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readUtf(), buf.readUtf()));
		}
		Members members = new Members(buf.readUtf(), buf.readBoolean(), readPlayers(buf), readPlayers(buf),
				readFlags(buf), readFlags(buf));
		Ballot ballot = new Ballot(buf.readUtf(), buf.readVarLong() - 1, buf.readVarLong() - 1, buf.readVarInt(),
				readPlayers(buf), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
				buf.readVarLong(), buf.readUtf(), buf.readVarInt());
		int radius = buf.readVarInt();
		int annexCount = buf.readVarInt();
		List<BlockPos> annexes = new ArrayList<>(annexCount);
		for (int i = 0; i < annexCount; i++) {
			annexes.add(buf.readBlockPos());
		}
		int annexRadius = buf.readVarInt();
		int plotCount = buf.readVarInt();
		List<Plot> plots = new ArrayList<>(plotCount);
		for (int i = 0; i < plotCount; i++) {
			plots.add(new Plot(buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf()));
		}
		return new TownSnapshot(hall, name, tier, population, citizens, villagers, capacity, homes, workstations,
				employed, meals, nextTier, nextPopulation, nextHouses, manager, life, council, rows, buildings, requests,
				plans, members, ballot, new TownMap(radius, annexes, annexRadius, plots));
	}
}
