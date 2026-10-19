package com.ajustor.minecities.caravan;

import com.ajustor.minecities.MineCities;
import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.SleepInBedGoal;
import com.ajustor.minecities.registry.McEntities;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les caravanes du monde et les routes qui relient les villes (MC-80). Une caravane avance le jour,
 * campe la nuit ; elle ne prend corps (chariot, cheval, marchand) que lorsqu'un joueur est près d'elle,
 * et se contente sinon d'avancer sur le papier. À l'arrivée, sa cargaison entre au grenier de la ville
 * qui l'a achetée, et le marchand monte une échoppe en bordure de la ville ({@link Stall}) pour vendre
 * aux joueurs, une journée durant, des marchandises de sa ville.
 *
 * <p>En route, des gardes de sa ville l'escortent, et des bandits l'attendent parfois ({@link Bandit}) :
 * près d'un joueur, l'escarmouche se joue pour de vrai (les gardes se battent, l'équipage à terre se
 * fait piller) ; loin de tous, elle se règle sur le papier. Un joueur peut aussi l'attaquer : les
 * défenseurs vaincus, il pille le chariot ; vu d'un autre joueur, ou si un membre de l'équipage
 * survit, il devient hors-la-loi dans les deux villes.
 */
public final class Caravans extends SavedData {
	/** Son pas, en blocs par tick : celui d'un attelage au trot tranquille. */
	public static final double SPEED = 0.15;
	/** À cette distance d'un joueur, elle prend corps ; au-delà, elle redevient une simple position. */
	public static final double VISIBLE = 64;
	/** L'étiquette de ses membres (cheval, marchand) : ils ne doivent pas lui survivre dans la sauvegarde. */
	public static final String TAG = "minecities.caravan";
	/** Une caravane par route, dans chaque sens, et par jour au plus. */
	public static final long DAILY = 24000L;
	/** Une échoppe reste ouverte un jour (moins si tout est vendu). */
	public static final long STALL_TIME = 24000L;
	/** L'échoppe s'installe à tant de blocs en deçà de la limite de la ville, et à côté de la route. */
	private static final double EDGE = 4;
	private static final double ROADSIDE = 3.5;
	/** Une caravane sur tant tombe dans une embuscade, entre deux villes. */
	private static final double AMBUSH_CHANCE = 0.35;
	/** Les gardes d'une ville qui partent avec une caravane, au plus. */
	public static final int MAX_ESCORT = 2;
	/** Un voleur reste hors-la-loi tant de ticks (trois jours). */
	public static final long OUTLAW_TIME = 3 * 24000L;
	/** Le coup d'un garde d'escorte, et sa cadence. */
	private static final float STRIKE = 7;
	private static final int STRIKE_EVERY = 12;
	/** Un témoin du pillage : un autre joueur à cette distance du chariot. */
	private static final double WITNESS = 48;

	private static final Codec<Caravans> CODEC = RecordCodecBuilder.create(i -> i.group(
			Caravan.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(c -> List.copyOf(c.active.values())),
			Codec.unboundedMap(Codec.STRING, BlockPos.CODEC.listOf()).optionalFieldOf("waypoints", Map.of())
					.forGetter(c -> c.waypoints),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("departures", Map.of())
					.forGetter(c -> c.departures),
			Stall.CODEC.listOf().optionalFieldOf("stalls", List.of()).forGetter(c -> List.copyOf(c.stalls.values())),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("outlaws", Map.of()).forGetter(c -> c.outlaws)
	).apply(i, Caravans::new));
	private static final SavedDataType<Caravans> TYPE = new SavedDataType<>(MineCities.id("caravans"), Caravans::new,
			CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Les membres visibles de chaque caravane : pas sauvegardés, recréés près des joueurs. */
	private static final Map<UUID, Crew> CREWS = new HashMap<>();
	/** De même, l'échoppe, le chariot dételé, le cheval et le marchand de chaque échoppe. */
	private static final Map<UUID, StallCrew> STALL_CREWS = new HashMap<>();

	/** Le chariot, le cheval, le marchand et les gardes d'escorte d'une caravane qui a pris corps. */
	private record Crew(UUID cart, UUID horse, UUID merchant, List<UUID> escorts) {
		boolean has(UUID id) {
			return id.equals(cart) || id.equals(horse) || id.equals(merchant) || escorts.contains(id);
		}

		List<UUID> defenders() {
			List<UUID> defenders = new ArrayList<>(escorts);
			defenders.add(merchant);
			return defenders;
		}
	}

	/** Une escarmouche en cours autour d'une caravane qui a pris corps : ses bandits, son agresseur. */
	private static final class Fray {
		final java.util.Set<UUID> bandits = new java.util.HashSet<>();
		/** Le joueur qui s'en est pris à l'équipage, s'il y en a un. */
		UUID aggressor;
		boolean plundered;
		/** Le pillage du chariot en cours (un joueur à la fois). */
		boolean looting;
		/** Les joueurs déjà jugés pour ce pillage. */
		final java.util.Set<UUID> judged = new java.util.HashSet<>();
	}

	/** Les escarmouches en cours (pas sauvegardées : elles ne durent qu'autour d'un joueur). */
	private static final Map<UUID, Fray> FRAYS = new HashMap<>();

	private record StallCrew(UUID stall, UUID cart, UUID horse, UUID merchant) {
		boolean has(UUID id) {
			return id.equals(stall) || id.equals(cart) || id.equals(horse) || id.equals(merchant);
		}
	}

	private final Map<UUID, Caravan> active = new LinkedHashMap<>();
	private final Map<UUID, Stall> stalls = new LinkedHashMap<>();
	/** Les points de passage de chaque route (clé : les deux villes, la plus petite d'abord), dans ce sens. */
	private final Map<String, List<BlockPos>> waypoints = new HashMap<>();
	/** Le dernier départ dans chaque sens (clé : vendeur>acheteur). */
	private final Map<String, Long> departures = new HashMap<>();
	/** Les hors-la-loi (clé : joueur|ville), jusqu'à quand. */
	private final Map<String, Long> outlaws = new HashMap<>();

	public Caravans() {
	}

	private Caravans(List<Caravan> active, Map<String, List<BlockPos>> waypoints, Map<String, Long> departures,
			List<Stall> stalls, Map<String, Long> outlaws) {
		this.outlaws.putAll(outlaws);
		active.forEach(c -> this.active.put(c.id(), c));
		stalls.forEach(st -> this.stalls.put(st.id(), st));
		this.waypoints.putAll(waypoints);
		this.departures.putAll(departures);
	}

	public static Caravans get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Caravans::tick);
		ServerTickEvents.END_SERVER_TICK.register(RouteStaffItem::show);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CREWS.clear();
			STALL_CREWS.clear();
			FRAYS.clear();
		});
		// Un cheval ou un marchand de caravane relu d'une sauvegarde n'appartient plus à rien : il s'en va.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && CREWS.values().stream().noneMatch(c -> c.has(entity.getUUID()))
					&& STALL_CREWS.values().stream().noneMatch(c -> c.has(entity.getUUID()))) {
				entity.discard();
			}
		});
	}

	public List<Caravan> active() {
		return List.copyOf(active.values());
	}

	// Routes

	private static String route(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	/** Les points de passage de la route de a vers b, dans ce sens. */
	public List<BlockPos> waypoints(UUID a, UUID b) {
		List<BlockPos> points = new ArrayList<>(waypoints.getOrDefault(route(a, b), List.of()));
		if (a.compareTo(b) > 0) {
			java.util.Collections.reverse(points);
		}
		return points;
	}

	/** Remplace les points de passage de la route de a vers b (donnés dans ce sens). */
	public void setWaypoints(UUID a, UUID b, List<BlockPos> points) {
		List<BlockPos> stored = new ArrayList<>(points);
		if (a.compareTo(b) > 0) {
			java.util.Collections.reverse(stored);
		}
		if (stored.isEmpty()) {
			waypoints.remove(route(a, b));
		} else {
			waypoints.put(route(a, b), List.copyOf(stored));
		}
		setDirty();
	}

	/** Le devant de l'hôtel de ville, d'où partent et où arrivent les caravanes. */
	public static BlockPos front(ServerLevel level, Town town) {
		BlockPos front = town.hall().south(4);
		return Itinerary.ground(level, front.getX(), front.getZ(), town.hall().getY());
	}

	/** L'itinéraire complet de la ville a à la ville b. */
	public List<BlockPos> path(ServerLevel level, Town a, Town b) {
		List<BlockPos> points = new ArrayList<>();
		points.add(front(level, a));
		points.addAll(waypoints(a.id(), b.id()));
		points.add(front(level, b));
		return Itinerary.route(level, points);
	}

	// Départs

	/** Une caravane peut-elle partir de ce vendeur vers cet acheteur (une par jour et par sens) ? */
	public boolean mayDepart(Town seller, Town buyer, long now) {
		String key = seller.id() + ">" + buyer.id();
		boolean onTheRoad = active.values().stream().anyMatch(c -> c.from().equals(seller.id())
				&& c.to().equals(buyer.id()));
		return !onTheRoad && now - departures.getOrDefault(key, now - DAILY) >= DAILY;
	}

	/**
	 * Une caravane part, avec cette cargaison déjà payée, et ces marchandises que le marchand vendra à
	 * son échoppe.
	 */
	public Caravan depart(ServerLevel level, Town seller, Town buyer, List<ItemStack> cargo, List<ItemStack> wares,
			String merchant) {
		List<BlockPos> path = path(level, seller, buyer);
		// Une embuscade, parfois, quelque part entre les deux villes (hors de l'une et de l'autre).
		double ambushAt = -1;
		int bandits = 0;
		net.minecraft.util.RandomSource random = level.getRandom();
		if (random.nextDouble() < AMBUSH_CHANCE) {
			double at = Itinerary.length(path) * (0.3 + 0.4 * random.nextDouble());
			BlockPos spot = BlockPos.containing(Itinerary.at(path, at));
			if (!seller.contains(level.dimension(), spot) && !buyer.contains(level.dimension(), spot)) {
				ambushAt = at;
				bandits = 2 + random.nextInt(3);
			}
		}
		int escort = Math.min(MAX_ESCORT, seller.workers(com.ajustor.minecities.town.Job.GUARD));
		return launch(level, seller, buyer, cargo, wares, merchant, path, escort, bandits, ambushAt);
	}

	/** Une caravane part, son escorte et son embuscade déjà décidées (les scènes en choisissent). */
	public Caravan launch(ServerLevel level, Town seller, Town buyer, List<ItemStack> cargo, List<ItemStack> wares,
			String merchant, List<BlockPos> path, int escort, int bandits, double ambushAt) {
		long now = level.getGameTime();
		Caravan caravan = Caravan.depart(seller.id(), buyer.id(), cargo, wares, now, merchant, path, escort, bandits,
				ambushAt);
		List<ItemStack> load = new ArrayList<>(cargo);
		load.addAll(wares);
		active.put(caravan.id(), caravan);
		departures.put(seller.id() + ">" + buyer.id(), now);
		setDirty();
		TownService.notify(level.getServer(), seller, Component.translatable("message.minecities.caravan.departed",
				seller.name(), buyer.name(), describe(load)));
		TownService.notify(level.getServer(), buyer, Component.translatable("message.minecities.caravan.coming",
				seller.name(), buyer.name(), describe(load)));
		return caravan;
	}

	/** « 32 × Pain, 16 × Blé ». */
	public static Component describe(List<ItemStack> cargo) {
		net.minecraft.network.chat.MutableComponent text = Component.empty();
		for (int i = 0; i < cargo.size(); i++) {
			if (i > 0) {
				text.append(", ");
			}
			text.append(cargo.get(i).getCount() + " × ").append(cargo.get(i).getHoverName());
		}
		return text;
	}

	// La route

	private static void tick(MinecraftServer server) {
		Caravans caravans = get(server);
		if (caravans.active.isEmpty() && CREWS.isEmpty() && caravans.stalls.isEmpty() && STALL_CREWS.isEmpty()) {
			return;
		}
		Towns towns = Towns.get(server);
		boolean check = server.getTickCount() % 20 == 0;
		if (check) {
			caravans.tickStalls(server, towns);
		}
		for (Caravan caravan : List.copyOf(caravans.active.values())) {
			Optional<Town> from = towns.byId(caravan.from());
			Optional<Town> to = towns.byId(caravan.to());
			ServerLevel level = to.map(t -> server.getLevel(t.dimension())).orElse(null);
			if (from.isEmpty() || to.isEmpty() || level == null || caravan.path().size() < 2) {
				// Une des villes a disparu : la caravane se perd.
				caravans.remove(server, caravan);
				continue;
			}
			boolean night = SleepInBedGoal.isNight(level);
			if (caravan.ambushDue()) {
				caravans.ambush(level, caravan, from.get(), to.get());
			}
			if (!night && !caravan.arrived() && !FRAYS.containsKey(caravan.id())) {
				caravan.advance(SPEED);
				if (check) {
					caravans.setDirty();
				}
			}
			if (caravan.arrived() && caravans.arrive(level, caravan, from.get(), to.get())) {
				continue;
			}
			if (check) {
				caravans.embody(level, caravan, from.get(), to.get());
			}
			move(level, caravan, night);
			if (FRAYS.containsKey(caravan.id())) {
				caravans.fight(level, caravan, from.get(), to.get());
			}
		}
	}

	private void remove(MinecraftServer server, Caravan caravan) {
		active.remove(caravan.id());
		FRAYS.remove(caravan.id());
		disband(server, caravan.id());
		setDirty();
	}

	/** À l'arrivée : la cargaison entre au grenier de l'acheteur (qui doit être chargé) ; vrai si c'est fait. */
	private boolean arrive(ServerLevel level, Caravan caravan, Town seller, Town buyer) {
		Optional<TownHallBlockEntity> granary = TownService.granary(level, buyer);
		if (granary.isEmpty()) {
			// Le grenier n'est pas chargé : elle attend devant l'hôtel de ville.
			return false;
		}
		for (ItemStack goods : caravan.cargo()) {
			ItemStack rest = Stock.insert(granary.get(), goods.copy());
			if (!rest.isEmpty()) {
				BlockPos hall = buyer.hall();
				level.addFreshEntity(new ItemEntity(level, hall.getX() + 0.5, hall.getY() + 1, hall.getZ() + 2.5, rest));
			}
		}
		if (!caravan.cargo().isEmpty()) {
			Component message = Component.translatable("message.minecities.caravan.arrived", seller.name(),
					buyer.name(), describe(caravan.cargo()));
			TownService.notify(level.getServer(), seller, message);
			TownService.notify(level.getServer(), buyer, message);
		}
		if (!caravan.wares().isEmpty()) {
			Stall stall = edge(level, caravan, buyer);
			stalls.put(stall.id(), stall);
			Component message = Component.translatable("message.minecities.stall.opened", caravan.merchant(),
					seller.name(), buyer.name(), describe(caravan.wares()));
			TownService.notify(level.getServer(), seller, message);
			TownService.notify(level.getServer(), buyer, message);
		}
		level.playSound(null, buyer.hall(), net.minecraft.sounds.SoundEvents.WANDERING_TRADER_YES,
				net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
		remove(level.getServer(), caravan);
		return true;
	}

	// Les échoppes

	/**
	 * L'échoppe s'installe en bordure de la ville : en remontant la route depuis l'hôtel de ville, au
	 * dernier point encore dans la ville (à quelques blocs de la limite), sur le bas-côté, le comptoir
	 * tourné vers la route.
	 */
	private static Stall edge(ServerLevel level, Caravan caravan, Town buyer) {
		List<BlockPos> path = caravan.path();
		double length = Itinerary.length(path);
		double s = Math.max(0, length - 12);
		for (double back = length; back >= 0; back -= 2) {
			if (!buyer.contains(level.dimension(), BlockPos.containing(Itinerary.at(path, back)))) {
				s = Math.min(length, back + EDGE);
				break;
			}
		}
		Vec3 at = beside(path, s, ROADSIDE);
		BlockPos ground = Itinerary.ground(level, (int) Math.floor(at.x), (int) Math.floor(at.z), (int) Math.floor(at.y));
		return Stall.open(caravan, ground, Itinerary.yaw(path, s) - 90, level.getGameTime());
	}

	public List<Stall> stalls() {
		return List.copyOf(stalls.values());
	}

	public Optional<Stall> stall(UUID id) {
		return Optional.ofNullable(stalls.get(id));
	}

	/** Un joueur a acheté un lot de cette marchandise à l'échoppe. */
	public void sold(UUID id, net.minecraft.world.item.Item item) {
		Stall stall = stalls.get(id);
		if (stall != null && stall.sell(item)) {
			setDirty();
		}
	}

	/** Un joueur s'adresse à l'échoppe : le troc s'ouvre (l'écran des villageois). */
	public static void trade(ServerPlayer player, UUID id) {
		MinecraftServer server = player.level().getServer();
		Caravans caravans = get(server);
		Stall stall = caravans.stalls.get(id);
		if (stall == null) {
			return;
		}
		if (!stall.hasWares()) {
			player.sendSystemMessage(Component.translatable("message.minecities.stall.empty", stall.merchant()));
			return;
		}
		long now = player.level().getGameTime();
		if (caravans.isOutlaw(player.getUUID(), stall.from(), now) || caravans.isOutlaw(player.getUUID(), stall.to(), now)) {
			player.sendSystemMessage(Component.translatable("message.minecities.stall.outlaw", stall.merchant())
					.withStyle(net.minecraft.ChatFormatting.RED));
			return;
		}
		String seller = Towns.get(server).byId(stall.from()).map(Town::name).orElse("?");
		StallMerchant merchant = new StallMerchant(server, stall);
		merchant.setTradingPlayer(player);
		merchant.openTradingScreen(player, Component.translatable("screen.minecities.stall", seller), 0);
	}

	/** Chaque seconde : les échoppes prennent corps près des joueurs, et ferment au bout d'un jour. */
	private void tickStalls(MinecraftServer server, Towns towns) {
		for (Stall stall : List.copyOf(stalls.values())) {
			Optional<Town> seller = towns.byId(stall.from());
			Optional<Town> buyer = towns.byId(stall.to());
			ServerLevel level = buyer.map(t -> server.getLevel(t.dimension())).orElse(null);
			if (seller.isEmpty() || buyer.isEmpty() || level == null) {
				// Une des villes a disparu : le marchand remballe sans rien rendre.
				stalls.remove(stall.id());
				disbandStall(server, stall.id());
				setDirty();
				continue;
			}
			if (level.getGameTime() - stall.opened() >= STALL_TIME || !stall.hasWares()) {
				if (close(server, stall, seller.get())) {
					continue;
				}
			}
			embodyStall(level, stall);
		}
	}

	/**
	 * L'échoppe ferme : les émeraudes gagnées et ce qui reste repartent au grenier du vendeur (qui doit
	 * être chargé, sinon on attend) ; vrai si c'est fait.
	 */
	private boolean close(MinecraftServer server, Stall stall, Town seller) {
		ServerLevel home = server.getLevel(seller.dimension());
		Optional<TownHallBlockEntity> granary = home == null ? Optional.empty() : TownService.granary(home, seller);
		if (granary.isEmpty()) {
			return false;
		}
		List<ItemStack> back = new ArrayList<>(stall.wares());
		if (stall.earned() > 0) {
			back.add(new ItemStack(net.minecraft.world.item.Items.EMERALD, stall.earned()));
			seller.chronicle().earn(home.getGameTime(), stall.earned());
		}
		for (ItemStack goods : back) {
			ItemStack rest = Stock.insert(granary.get(), goods.copy());
			if (!rest.isEmpty()) {
				BlockPos hall = seller.hall();
				home.addFreshEntity(new ItemEntity(home, hall.getX() + 0.5, hall.getY() + 1, hall.getZ() + 2.5, rest));
			}
		}
		TownService.notify(server, seller, Component.translatable("message.minecities.stall.closed", stall.merchant(),
				stall.earned()));
		stalls.remove(stall.id());
		disbandStall(server, stall.id());
		setDirty();
		Towns.get(server).changed();
		return true;
	}

	private static void embodyStall(ServerLevel level, Stall stall) {
		Vec3 at = Vec3.atBottomCenterOf(stall.pos());
		boolean seen = level.isLoaded(stall.pos()) && level.players().stream()
				.anyMatch(p -> p.distanceToSqr(at.x, p.getY(), at.z) < VISIBLE * VISIBLE);
		StallCrew crew = STALL_CREWS.get(stall.id());
		if (seen && crew == null) {
			CaravanStall booth = McEntities.CARAVAN_STALL.create(level, EntitySpawnReason.EVENT);
			CaravanCart cart = McEntities.CARAVAN_CART.create(level, EntitySpawnReason.EVENT);
			Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.EVENT);
			CitizenEntity merchant = McEntities.CITIZEN.create(level, EntitySpawnReason.EVENT);
			if (booth == null || cart == null || horse == null || merchant == null) {
				return;
			}
			float yaw = stall.yaw();
			booth.snapTo(at.x, at.y, at.z, yaw, 0);
			// Le chariot dételé à côté de l'échoppe, le long de la route ; le cheval devant lui.
			Vec3 parked = local(at, yaw, -3.6, -1.2);
			cart.snapTo(parked.x, at.y, parked.z, yaw + 90, 0);
			Vec3 ahead = local(parked, yaw + 90, 0, 3.2);
			horse.snapTo(ahead.x, at.y, ahead.z, yaw + 90, 0);
			horse.setNoAi(true);
			com.ajustor.minecities.compat.EntityCompat.invulnerable(horse);
			horse.setPersistenceRequired();
			horse.addTag(TAG);
			// Le marchand derrière son comptoir.
			Vec3 behind = local(at, yaw, 0, -0.75);
			merchant.snapTo(behind.x, at.y, behind.z, yaw, 0);
			merchant.setYHeadRot(yaw);
			merchant.setYBodyRot(yaw);
			merchant.travel(stall.merchant());
			merchant.addTag(TAG);
			STALL_CREWS.put(stall.id(), new StallCrew(booth.getUUID(), cart.getUUID(), horse.getUUID(),
					merchant.getUUID()));
			level.addFreshEntity(booth);
			level.addFreshEntity(cart);
			level.addFreshEntity(horse);
			level.addFreshEntity(merchant);
			crew = STALL_CREWS.get(stall.id());
		} else if (!seen && crew != null) {
			disbandStall(level.getServer(), stall.id());
			return;
		}
		if (crew != null && level.getEntity(crew.stall()) instanceof CaravanStall booth) {
			booth.show(stall.id(), stall.wares());
		}
	}

	/** Un point donné en blocs dans le repère d'une entité de ce lacet (x vers sa gauche, z vers l'avant). */
	static Vec3 local(Vec3 origin, double yaw, double x, double z) {
		double r = Math.toRadians(yaw);
		return origin.add(x * Math.cos(r) - z * Math.sin(r), 0, x * Math.sin(r) + z * Math.cos(r));
	}

	private static void disbandStall(MinecraftServer server, UUID stall) {
		StallCrew crew = STALL_CREWS.remove(stall);
		if (crew == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (UUID id : new UUID[]{crew.stall(), crew.cart(), crew.horse(), crew.merchant()}) {
				Entity entity = level.getEntity(id);
				if (entity != null) {
					entity.discard();
				}
			}
		}
	}

	/** Le joueur voit-il cette échoppe ? (pour les scènes) */
	public static boolean stallEmbodied(UUID stall) {
		return STALL_CREWS.containsKey(stall);
	}

	/** Près d'un joueur, elle prend corps ; loin de tous, ses membres s'effacent. */
	private void embody(ServerLevel level, Caravan caravan, Town seller, Town buyer) {
		Vec3 at = Itinerary.at(caravan.path(), caravan.traveled());
		BlockPos pos = BlockPos.containing(at);
		boolean seen = level.isLoaded(pos) && level.players().stream()
				.anyMatch(p -> p.distanceToSqr(at.x, p.getY(), at.z) < VISIBLE * VISIBLE);
		Crew crew = CREWS.get(caravan.id());
		if (seen && crew == null) {
			summon(level, caravan, at, seller);
		} else if (!seen && crew != null) {
			// Plus personne pour voir l'escarmouche : elle se règle sur le papier.
			Fray fray = FRAYS.remove(caravan.id());
			if (fray != null && !fray.plundered && fray.bandits.stream().anyMatch(b -> level.getEntity(b) instanceof Bandit
					bandit && bandit.isAlive() && !bandit.fleeing())) {
				simulate(level, caravan, seller, buyer, (int) fray.bandits.stream().filter(b -> level.getEntity(b) != null)
						.count());
			}
			fray = fray == null ? new Fray() : fray;
			fray.bandits.forEach(b -> {
				if (level.getEntity(b) instanceof Bandit bandit && !bandit.fleeing()) {
					bandit.discard();
				}
			});
			disband(level.getServer(), caravan.id());
		}
	}

	private static void summon(ServerLevel level, Caravan caravan, Vec3 at, Town seller) {
		CaravanCart cart = McEntities.CARAVAN_CART.create(level, EntitySpawnReason.EVENT);
		Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.EVENT);
		CitizenEntity merchant = McEntities.CITIZEN.create(level, EntitySpawnReason.EVENT);
		if (cart == null || horse == null || merchant == null) {
			return;
		}
		cart.snapTo(at.x, at.y, at.z, 0, 0);
		horse.snapTo(at.x, at.y, at.z, 0, 0);
		horse.setNoAi(true);
		com.ajustor.minecities.compat.EntityCompat.invulnerable(horse);
		horse.setPersistenceRequired();
		horse.addTag(TAG);
		merchant.snapTo(at.x, at.y, at.z, 0, 0);
		merchant.travel(caravan.merchant());
		merchant.addTag(TAG);
		// L'escorte : des gardes de la ville, armés et casqués.
		List<CitizenEntity> escorts = new ArrayList<>();
		for (int i = 0; i < caravan.escort(); i++) {
			CitizenEntity guard = McEntities.CITIZEN.create(level, EntitySpawnReason.EVENT);
			if (guard == null) {
				continue;
			}
			guard.snapTo(at.x, at.y, at.z, 0, 0);
			guard.travel(Component.translatable("entity.minecities.escort", seller.name()),
					com.ajustor.minecities.town.Job.GUARD);
			guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
					new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
			guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
					new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
			guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
					new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
			for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
				guard.setDropChance(slot, 0);
			}
			guard.addTag(TAG);
			escorts.add(guard);
		}
		// L'équipage d'abord : sans lui, le cheval et le marchand, marqués, seraient effacés dès leur arrivée.
		CREWS.put(caravan.id(), new Crew(cart.getUUID(), horse.getUUID(), merchant.getUUID(),
				escorts.stream().map(Entity::getUUID).toList()));
		level.addFreshEntity(cart);
		level.addFreshEntity(horse);
		level.addFreshEntity(merchant);
		escorts.forEach(level::addFreshEntity);
	}

	private static void disband(MinecraftServer server, UUID caravan) {
		Crew crew = CREWS.remove(caravan);
		if (crew == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			List<UUID> members = new ArrayList<>(crew.escorts);
			members.addAll(List.of(crew.cart, crew.horse, crew.merchant));
			for (UUID id : members) {
				Entity entity = level.getEntity(id);
				if (entity != null) {
					entity.discard();
				}
			}
		}
	}

	/**
	 * Place ses membres le long de la route : le cheval devant, le chariot derrière lui, le marchand à
	 * côté du chariot. La nuit, le camp : le chariot dételé, le marchand près du feu.
	 */
	private static void move(ServerLevel level, Caravan caravan, boolean night) {
		Crew crew = CREWS.get(caravan.id());
		if (crew == null) {
			return;
		}
		List<BlockPos> path = caravan.path();
		double s = caravan.traveled();
		float yaw = Itinerary.yaw(path, s);
		place(level, crew.horse, path, s, 0, yaw);
		place(level, crew.cart, path, s - 3.2, 0, yaw);
		boolean fray = FRAYS.containsKey(caravan.id());
		if (!fray) {
			place(level, crew.merchant, path, s - 2.5, 1.4, yaw);
			// L'escorte marche de part et d'autre du chariot.
			for (int i = 0; i < crew.escorts.size(); i++) {
				place(level, crew.escorts.get(i), path, s - 3.0 - (i / 2) * 3.0, i % 2 == 0 ? -1.8 : 1.8, yaw);
			}
		}
		if (level.getEntity(crew.cart) instanceof CaravanCart cart) {
			cart.setCamping(night && !fray);
			if (night && !fray) {
				// Le camp, derrière le chariot (voir CaravanCartModel) : le marchand s'assoit au feu, les
				// gardes veillent de part et d'autre, tournés vers la nuit.
				Vec3 seat = camp(cart, CaravanCart.SEAT_X, CaravanCart.SEAT_Z);
				if (level.getEntity(crew.merchant) instanceof net.minecraft.world.entity.Entity merchant) {
					merchant.snapTo(seat.x, seat.y, seat.z, yaw + 120, 0);
				}
				for (int i = 0; i < crew.escorts.size(); i++) {
					Vec3 watch = camp(cart, i % 2 == 0 ? 2.2 : -2.6, CaravanCart.FIRE_Z - 1.2 - (i / 2) * 1.5);
					if (level.getEntity(crew.escorts.get(i)) instanceof net.minecraft.world.entity.LivingEntity guard) {
						float out = yaw + (i % 2 == 0 ? 90 : -90) + 180;
						guard.snapTo(watch.x, seat.y, watch.z, out, 0);
						guard.setYHeadRot(out);
						guard.setYBodyRot(out);
					}
				}
				if (level.getGameTime() % 10 == 0) {
					Vec3 fire = camp(cart, CaravanCart.FIRE_X, CaravanCart.FIRE_Z);
					level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, fire.x, fire.y + 0.6, fire.z, 1, 0.05, 0.05, 0.05, 0.01);
					level.sendParticles(ParticleTypes.FLAME, fire.x, fire.y + 0.2, fire.z, 2, 0.1, 0.05, 0.1, 0.005);
				}
			}
		}
	}

	// Les embuscades

	/**
	 * L'embuscade se déclenche : près d'un joueur, les bandits surgissent devant la caravane, qui
	 * s'arrête ; loin de tous, elle se règle sur le papier.
	 */
	void ambush(ServerLevel level, Caravan caravan, Town seller, Town buyer) {
		caravan.setAmbushed();
		setDirty();
		Crew crew = CREWS.get(caravan.id());
		if (crew == null) {
			simulate(level, caravan, seller, buyer, caravan.bandits());
			return;
		}
		Fray fray = FRAYS.computeIfAbsent(caravan.id(), id -> new Fray());
		Vec3 ahead = Itinerary.at(caravan.path(), caravan.traveled() + 9);
		for (int i = 0; i < caravan.bandits(); i++) {
			Bandit bandit = McEntities.BANDIT.create(level, EntitySpawnReason.EVENT);
			if (bandit == null) {
				continue;
			}
			double angle = level.getRandom().nextDouble() * Math.PI * 2;
			double x = ahead.x + Math.cos(angle) * 4;
			double z = ahead.z + Math.sin(angle) * 4;
			BlockPos ground = Itinerary.ground(level, (int) Math.floor(x), (int) Math.floor(z), (int) Math.floor(ahead.y));
			bandit.snapTo(x, ground.getY(), z, 0, 0);
			bandit.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), EntitySpawnReason.EVENT, null);
			// Il ne disparaît qu'à la fin de l'escarmouche (ou de sa fuite), pas faute de joueur alentour.
			bandit.setPersistenceRequired();
			level.addFreshEntity(bandit);
			fray.bandits.add(bandit.getUUID());
		}
		Component message = Component.translatable("message.minecities.caravan.ambush", seller.name(), buyer.name());
		TownService.notify(level.getServer(), seller, message);
		TownService.notify(level.getServer(), buyer, message);
	}

	/**
	 * L'embuscade sur le papier : l'escorte (trois points par garde, un pour le marchand) contre les
	 * bandits (deux chacun, un peu de chance en plus). Perdue, la moitié du chargement s'en va.
	 */
	void simulate(ServerLevel level, Caravan caravan, Town seller, Town buyer, int bandits) {
		boolean repelled = repels(caravan.escort(), bandits, level.getRandom().nextInt(4));
		Component message;
		if (repelled) {
			message = Component.translatable("message.minecities.caravan.repelled", seller.name(), buyer.name());
		} else {
			List<ItemStack> taken = caravan.plunder();
			message = Component.translatable("message.minecities.caravan.plundered", seller.name(), buyer.name(),
					describe(taken));
		}
		TownService.notify(level.getServer(), seller, message);
		TownService.notify(level.getServer(), buyer, message);
		setDirty();
	}

	/** L'escorte repousse-t-elle les bandits (sur le papier) ? */
	public static boolean repels(int escort, int bandits, int luck) {
		return escort * 3 + 1 >= bandits * 2 + luck;
	}

	/** Un joueur frappe un membre d'une caravane : l'escarmouche commence, contre lui. */
	public static void attacked(CitizenEntity member, net.minecraft.world.entity.player.Player player) {
		CREWS.entrySet().stream().filter(e -> e.getValue().has(member.getUUID())).findFirst()
				.ifPresent(e -> FRAYS.computeIfAbsent(e.getKey(), id -> new Fray()).aggressor = player.getUUID());
	}

	/**
	 * L'escarmouche, à chaque tick : les gardes debout vont au plus proche ennemi et frappent ; quand
	 * plus aucun défenseur ne tient debout, les bandits pillent et s'enfuient, ou le chariot est à la
	 * merci du joueur ; quand il n'y a plus d'ennemi, l'équipage se relève et repart (ou, s'il est
	 * tout entier tué, la caravane est perdue, sa cargaison laissée sur place).
	 */
	void fight(ServerLevel level, Caravan caravan, Town seller, Town buyer) {
		Crew crew = CREWS.get(caravan.id());
		Fray fray = FRAYS.get(caravan.id());
		if (crew == null || fray == null) {
			return;
		}
		Entity cartEntity = level.getEntity(crew.cart);
		Vec3 cart = cartEntity != null ? cartEntity.position() : Itinerary.at(caravan.path(), caravan.traveled());
		List<net.minecraft.world.entity.LivingEntity> foes = new ArrayList<>();
		for (UUID id : fray.bandits) {
			if (level.getEntity(id) instanceof Bandit bandit && bandit.isAlive() && !bandit.fleeing()
					&& bandit.distanceToSqr(cart) < 32 * 32) {
				foes.add(bandit);
			}
		}
		net.minecraft.world.entity.player.Player aggressor = fray.aggressor == null ? null
				: level.getPlayerByUUID(fray.aggressor);
		if (aggressor != null && aggressor.isAlive() && aggressor.distanceToSqr(cart) < 32 * 32) {
			foes.add(aggressor);
		} else {
			aggressor = null;
		}
		List<CitizenEntity> alive = new ArrayList<>();
		for (UUID id : crew.defenders()) {
			if (level.getEntity(id) instanceof CitizenEntity member && member.isAlive()) {
				alive.add(member);
			}
		}
		List<CitizenEntity> standing = alive.stream().filter(m -> !m.downed()).toList();
		long tick = level.getGameTime();
		// Les gardes d'escorte se battent.
		for (CitizenEntity member : standing) {
			if (!crew.escorts.contains(member.getUUID()) || foes.isEmpty()) {
				continue;
			}
			net.minecraft.world.entity.LivingEntity foe = foes.stream()
					.min(java.util.Comparator.comparingDouble(member::distanceToSqr)).orElseThrow();
			Vec3 to = foe.position().subtract(member.position());
			double distance = Math.sqrt(to.x * to.x + to.z * to.z);
			float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
			if (distance > 1.8) {
				double step = Math.min(0.25, distance - 1.6);
				double x = member.getX() + to.x / distance * step;
				double z = member.getZ() + to.z / distance * step;
				BlockPos ground = Itinerary.ground(level, (int) Math.floor(x), (int) Math.floor(z), (int) Math.floor(member.getY()));
				member.setPos(x, ground.getY(), z);
			}
			member.setYRot(yaw);
			member.setYHeadRot(yaw);
			member.setYBodyRot(yaw);
			if (distance < 2.8 && (tick + member.getId()) % STRIKE_EVERY == 0) {
				com.ajustor.minecities.compat.EntityCompat.swing(member, net.minecraft.world.InteractionHand.MAIN_HAND);
				foe.hurtServer(level, member.damageSources().mobAttack(member), STRIKE);
			}
		}
		boolean bandits = foes.stream().anyMatch(f -> f instanceof Bandit);
		if (standing.isEmpty() && bandits && !fray.plundered) {
			// Plus personne debout : les bandits prennent la moitié du chargement et s'enfuient.
			List<ItemStack> taken = caravan.plunder();
			List<Bandit> robbers = foes.stream().filter(f -> f instanceof Bandit).map(f -> (Bandit) f).toList();
			List<List<ItemStack>> shares = new ArrayList<>();
			robbers.forEach(r -> shares.add(new ArrayList<>()));
			for (int i = 0; i < taken.size(); i++) {
				shares.get(i % shares.size()).add(taken.get(i));
			}
			for (int i = 0; i < robbers.size(); i++) {
				robbers.get(i).flee(shares.get(i), cart);
			}
			fray.plundered = true;
			setDirty();
			Component message = Component.translatable("message.minecities.caravan.plundered", seller.name(),
					buyer.name(), describe(taken));
			TownService.notify(level.getServer(), seller, message);
			TownService.notify(level.getServer(), buyer, message);
			return;
		}
		if (!foes.isEmpty() || fray.looting) {
			return;
		}
		// Plus d'ennemi : fin de l'escarmouche.
		FRAYS.remove(caravan.id());
		if (alive.isEmpty()) {
			// Tout l'équipage est mort : la caravane est perdue ; ce qu'elle portait reste au sol.
			List<ItemStack> left = new ArrayList<>(caravan.cargo());
			left.addAll(caravan.wares());
			left.forEach(stack -> level.addFreshEntity(new ItemEntity(level, cart.x, cart.y + 0.8, cart.z, stack.copy())));
			caravan.cargo().clear();
			caravan.wares().clear();
			Component message = Component.translatable("message.minecities.caravan.lost", seller.name(), buyer.name());
			TownService.notify(level.getServer(), seller, message);
			TownService.notify(level.getServer(), buyer, message);
			remove(level.getServer(), caravan);
			return;
		}
		alive.forEach(CitizenEntity::recover);
		if (!fray.bandits.isEmpty() && !fray.plundered) {
			Component message = Component.translatable("message.minecities.caravan.repelled", seller.name(), buyer.name());
			TownService.notify(level.getServer(), seller, message);
			TownService.notify(level.getServer(), buyer, message);
		}
	}

	/** L'escarmouche de cette caravane : y en a-t-il une, et combien de défenseurs tiennent debout ? (scènes) */
	public static Optional<Integer> fray(ServerLevel level, UUID caravan) {
		Fray fray = FRAYS.get(caravan);
		Crew crew = CREWS.get(caravan);
		if (fray == null || crew == null) {
			return Optional.empty();
		}
		return Optional.of((int) crew.defenders().stream()
				.filter(id -> level.getEntity(id) instanceof CitizenEntity member && member.isAlive() && !member.downed())
				.count());
	}

	/** Les bandits de l'escarmouche de cette caravane (scènes). */
	public static List<UUID> bandits(UUID caravan) {
		Fray fray = FRAYS.get(caravan);
		return fray == null ? List.of() : List.copyOf(fray.bandits);
	}

	/** Le chariot de cette caravane, s'il a pris corps (scènes). */
	public static Optional<UUID> cartOf(UUID caravan) {
		return Optional.ofNullable(CREWS.get(caravan)).map(Crew::cart);
	}

	/** Retire cette caravane, sans rien livrer (scènes). */
	public void cancel(MinecraftServer server, UUID caravan) {
		Caravan gone = active.get(caravan);
		if (gone != null) {
			remove(server, gone);
		}
	}

	/** Les défenseurs (marchand, escorte) de cette caravane, s'ils ont pris corps (scènes). */
	public static List<UUID> defenders(UUID caravan) {
		Crew crew = CREWS.get(caravan);
		return crew == null ? List.of() : crew.defenders();
	}

	// Le pillage du chariot

	/**
	 * Un joueur fouille le chariot : seulement si plus aucun défenseur ne tient debout et qu'aucun
	 * bandit n'est là. Le chargement passe dans le coffre qu'il voit ; ce qu'il y laisse retourne au
	 * chariot quand il le ferme (rien ne se double). Vu d'un autre joueur, ou si un membre de
	 * l'équipage survit, il devient hors-la-loi dans les deux villes.
	 */
	public static void loot(ServerPlayer player, CaravanCart cartEntity) {
		MinecraftServer server = player.level().getServer();
		Caravans caravans = get(server);
		Optional<Map.Entry<UUID, Crew>> owner = CREWS.entrySet().stream()
				.filter(e -> e.getValue().cart.equals(cartEntity.getUUID())).findFirst();
		if (owner.isEmpty() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		UUID id = owner.get().getKey();
		Crew crew = owner.get().getValue();
		Caravan caravan = caravans.active.get(id);
		Fray fray = FRAYS.get(id);
		boolean defended = crew.defenders().stream()
				.anyMatch(m -> level.getEntity(m) instanceof CitizenEntity member && member.isAlive() && !member.downed());
		if (caravan == null || fray == null || defended || fray.bandits.stream()
				.anyMatch(b -> level.getEntity(b) instanceof Bandit bandit && bandit.isAlive() && !bandit.fleeing())) {
			player.sendSystemMessage(Component.translatable("message.minecities.caravan.guarded")
					.withStyle(net.minecraft.ChatFormatting.GRAY));
			return;
		}
		if (fray.looting) {
			return;
		}
		Towns towns = Towns.get(server);
		if (fray.judged.add(player.getUUID())) {
			boolean survivor = crew.defenders().stream()
					.anyMatch(m -> level.getEntity(m) instanceof CitizenEntity member && member.isAlive());
			boolean witness = level.players().stream().anyMatch(p -> p != player && !p.isSpectator()
					&& p.distanceToSqr(cartEntity) < WITNESS * WITNESS);
			if (survivor || witness) {
				long until = level.getGameTime() + OUTLAW_TIME;
				for (UUID town : List.of(caravan.from(), caravan.to())) {
					caravans.outlaws.put(player.getUUID() + "|" + town, until);
					towns.byId(town).ifPresent(t -> TownService.notify(server, t, Component.translatable(
							"message.minecities.caravan.thief", player.getName())));
				}
				caravans.setDirty();
				player.sendSystemMessage(Component.translatable("message.minecities.caravan.outlaw")
						.withStyle(net.minecraft.ChatFormatting.RED));
			}
		}
		fray.looting = true;
		net.minecraft.world.SimpleContainer box = new net.minecraft.world.SimpleContainer(27) {
			@Override
			public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
				super.stopOpen(user);
				// Ce qui reste au coffre retourne au chariot.
				caravan.cargo().clear();
				for (int i = 0; i < getContainerSize(); i++) {
					if (!getItem(i).isEmpty()) {
						caravan.cargo().add(getItem(i).copy());
					}
				}
				clearContent();
				fray.looting = false;
				caravans.setDirty();
				if (!caravans.active.containsKey(caravan.id())) {
					// La caravane a disparu entre-temps : le reste tombe au sol.
					caravan.cargo().forEach(stack -> level.addFreshEntity(new ItemEntity(level, cartEntity.getX(),
							cartEntity.getY() + 0.8, cartEntity.getZ(), stack)));
					caravan.cargo().clear();
				}
			}
		};
		List<ItemStack> load = new ArrayList<>(caravan.cargo());
		load.addAll(caravan.wares());
		caravan.cargo().clear();
		caravan.wares().clear();
		for (int i = 0; i < load.size(); i++) {
			if (i < box.getContainerSize()) {
				box.setItem(i, load.get(i));
			} else {
				caravan.cargo().add(load.get(i));
			}
		}
		caravans.setDirty();
		player.openMenu(new net.minecraft.world.SimpleMenuProvider((window, inventory, p) ->
				net.minecraft.world.inventory.ChestMenu.threeRows(window, inventory, box),
				Component.translatable("container.minecities.caravan")));
	}

	// Les hors-la-loi

	/** Ce joueur est-il hors-la-loi dans cette ville ? */
	public boolean isOutlaw(UUID player, UUID town, long now) {
		Long until = outlaws.get(player + "|" + town);
		return until != null && now < until;
	}

	/** Pour les gardes : ce joueur est-il hors-la-loi dans leur ville ? */
	public static boolean outlaw(MinecraftServer server, UUID player, UUID town) {
		return server != null && get(server).isOutlaw(player, town, server.overworld().getGameTime());
	}


	/** Un point du camp, donné en blocs dans le repère du chariot (x vers sa gauche, z vers l'avant). */
	static Vec3 camp(CaravanCart cart, double x, double z) {
		double yaw = Math.toRadians(cart.getYRot());
		return cart.position().add(x * Math.cos(yaw) - z * Math.sin(yaw), 0, x * Math.sin(yaw) + z * Math.cos(yaw));
	}

	/** Le point à cette distance, décalé de côté (vers la droite de la marche). */
	static Vec3 beside(List<BlockPos> path, double s, double side) {
		Vec3 here = Itinerary.at(path, s);
		double yaw = Math.toRadians(Itinerary.yaw(path, s));
		return here.add(-Math.cos(yaw) * side, 0, -Math.sin(yaw) * side);
	}

	private static void place(ServerLevel level, UUID id, List<BlockPos> path, double s, double side, float yaw) {
		Entity entity = level.getEntity(id);
		if (entity == null) {
			return;
		}
		Vec3 at = beside(path, Math.max(0, s), side);
		BlockPos ground = Itinerary.ground(level, (int) Math.floor(at.x), (int) Math.floor(at.z), (int) Math.floor(at.y));
		entity.setPos(at.x, ground.getY(), at.z);
		entity.setYRot(yaw);
		entity.setYHeadRot(yaw);
		if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
			living.setYBodyRot(yaw);
		}
	}

	/** Le joueur voit-il cette caravane prendre corps ? (pour les scènes) */
	public static boolean embodied(UUID caravan) {
		return CREWS.containsKey(caravan);
	}

	/** Le joueur le plus proche, s'il y en a un (pour les messages des scènes). */
	static Optional<ServerPlayer> nearest(ServerLevel level, Vec3 at) {
		return level.players().stream().min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(at)))
				.map(p -> (ServerPlayer) p);
	}
}
