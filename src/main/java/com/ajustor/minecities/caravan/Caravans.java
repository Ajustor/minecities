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
 * qui l'a achetée.
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

	private static final Codec<Caravans> CODEC = RecordCodecBuilder.create(i -> i.group(
			Caravan.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(c -> List.copyOf(c.active.values())),
			Codec.unboundedMap(Codec.STRING, BlockPos.CODEC.listOf()).optionalFieldOf("waypoints", Map.of())
					.forGetter(c -> c.waypoints),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("departures", Map.of())
					.forGetter(c -> c.departures)
	).apply(i, Caravans::new));
	private static final SavedDataType<Caravans> TYPE = new SavedDataType<>(MineCities.id("caravans"), Caravans::new,
			CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Les membres visibles de chaque caravane : pas sauvegardés, recréés près des joueurs. */
	private static final Map<UUID, Crew> CREWS = new HashMap<>();

	/** Le chariot, le cheval et le marchand d'une caravane qui a pris corps. */
	private record Crew(UUID cart, UUID horse, UUID merchant) {
		boolean has(UUID id) {
			return id.equals(cart) || id.equals(horse) || id.equals(merchant);
		}
	}

	private final Map<UUID, Caravan> active = new LinkedHashMap<>();
	/** Les points de passage de chaque route (clé : les deux villes, la plus petite d'abord), dans ce sens. */
	private final Map<String, List<BlockPos>> waypoints = new HashMap<>();
	/** Le dernier départ dans chaque sens (clé : vendeur>acheteur). */
	private final Map<String, Long> departures = new HashMap<>();

	public Caravans() {
	}

	private Caravans(List<Caravan> active, Map<String, List<BlockPos>> waypoints, Map<String, Long> departures) {
		active.forEach(c -> this.active.put(c.id(), c));
		this.waypoints.putAll(waypoints);
		this.departures.putAll(departures);
	}

	public static Caravans get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Caravans::tick);
		ServerTickEvents.END_SERVER_TICK.register(RouteStaffItem::show);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CREWS.clear());
		// Un cheval ou un marchand de caravane relu d'une sauvegarde n'appartient plus à rien : il s'en va.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && CREWS.values().stream().noneMatch(c -> c.has(entity.getUUID()))) {
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

	/** Une caravane part, avec cette cargaison déjà payée. */
	public Caravan depart(ServerLevel level, Town seller, Town buyer, List<ItemStack> cargo, String merchant) {
		long now = level.getGameTime();
		Caravan caravan = Caravan.depart(seller.id(), buyer.id(), cargo, now, merchant, path(level, seller, buyer));
		active.put(caravan.id(), caravan);
		departures.put(seller.id() + ">" + buyer.id(), now);
		setDirty();
		TownService.notify(level.getServer(), seller, Component.translatable("message.minecities.caravan.departed",
				seller.name(), buyer.name(), describe(cargo)));
		TownService.notify(level.getServer(), buyer, Component.translatable("message.minecities.caravan.coming",
				seller.name(), buyer.name(), describe(cargo)));
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
		if (caravans.active.isEmpty() && CREWS.isEmpty()) {
			return;
		}
		Towns towns = Towns.get(server);
		boolean check = server.getTickCount() % 20 == 0;
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
			if (!night && !caravan.arrived()) {
				caravan.advance(SPEED);
				if (check) {
					caravans.setDirty();
				}
			}
			if (caravan.arrived() && caravans.arrive(level, caravan, from.get(), to.get())) {
				continue;
			}
			if (check) {
				embody(level, caravan);
			}
			move(level, caravan, night);
		}
	}

	private void remove(MinecraftServer server, Caravan caravan) {
		active.remove(caravan.id());
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
		Component message = Component.translatable("message.minecities.caravan.arrived", seller.name(), buyer.name(),
				describe(caravan.cargo()));
		TownService.notify(level.getServer(), seller, message);
		TownService.notify(level.getServer(), buyer, message);
		level.playSound(null, buyer.hall(), net.minecraft.sounds.SoundEvents.WANDERING_TRADER_YES,
				net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
		remove(level.getServer(), caravan);
		return true;
	}

	/** Près d'un joueur, elle prend corps ; loin de tous, ses membres s'effacent. */
	private static void embody(ServerLevel level, Caravan caravan) {
		Vec3 at = Itinerary.at(caravan.path(), caravan.traveled());
		BlockPos pos = BlockPos.containing(at);
		boolean seen = level.isLoaded(pos) && level.players().stream()
				.anyMatch(p -> p.distanceToSqr(at.x, p.getY(), at.z) < VISIBLE * VISIBLE);
		Crew crew = CREWS.get(caravan.id());
		if (seen && crew == null) {
			summon(level, caravan, at);
		} else if (!seen && crew != null) {
			disband(level.getServer(), caravan.id());
		}
	}

	private static void summon(ServerLevel level, Caravan caravan, Vec3 at) {
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
		// L'équipage d'abord : sans lui, le cheval et le marchand, marqués, seraient effacés dès leur arrivée.
		CREWS.put(caravan.id(), new Crew(cart.getUUID(), horse.getUUID(), merchant.getUUID()));
		level.addFreshEntity(cart);
		level.addFreshEntity(horse);
		level.addFreshEntity(merchant);
	}

	private static void disband(MinecraftServer server, UUID caravan) {
		Crew crew = CREWS.remove(caravan);
		if (crew == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (UUID id : new UUID[]{crew.cart, crew.horse, crew.merchant}) {
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
		place(level, crew.merchant, path, s - 2.5, 1.4, yaw);
		if (level.getEntity(crew.cart) instanceof CaravanCart cart) {
			cart.setCamping(night);
			if (night) {
				// Le camp, derrière le chariot (voir CaravanCartModel) : le marchand s'assoit au feu.
				Vec3 seat = camp(cart, CaravanCart.SEAT_X, CaravanCart.SEAT_Z);
				if (level.getEntity(crew.merchant) instanceof net.minecraft.world.entity.Entity merchant) {
					merchant.snapTo(seat.x, seat.y, seat.z, yaw + 120, 0);
				}
				if (level.getGameTime() % 10 == 0) {
					Vec3 fire = camp(cart, CaravanCart.FIRE_X, CaravanCart.FIRE_Z);
					level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, fire.x, fire.y + 0.6, fire.z, 1, 0.05, 0.05, 0.05, 0.01);
					level.sendParticles(ParticleTypes.FLAME, fire.x, fire.y + 0.2, fire.z, 2, 0.1, 0.05, 0.1, 0.005);
				}
			}
		}
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
