package com.ajustor.minecities.town;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.registry.McBlocks;
import com.ajustor.minecities.registry.McEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * La vie des villes : fondation et dissolution, déclaration des maisons, arrivée des citoyens,
 * passage d'un palier à l'autre. Tout se passe côté serveur.
 */
public final class TownService {
	/** Les villes ne vivent qu'une fois toutes les 5 secondes : c'est bien assez. */
	private static final int TICK_INTERVAL = 100;
	/** Un nouveau citoyen au plus toutes les 2 minutes. */
	public static final int IMMIGRATION_DELAY = 2400;
	/** Les maisons sont relevées à nouveau toutes les 5 minutes. */
	private static final int SURVEY_INTERVAL = 6000;
	/** Ce qui se mange mais rend malade : les citoyens n'en veulent pas. */
	private static final Set<net.minecraft.world.item.Item> UNHEALTHY = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE,
			Items.POISONOUS_POTATO, Items.PUFFERFISH, Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW);

	private static final Map<UUID, Long> LAST_SURVEY = new HashMap<>();
	private static int ticks;

	private TownService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TownService::tick);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!state.is(McBlocks.TOWN_HALL) || !(level instanceof ServerLevel serverLevel)) {
				return true;
			}
			Optional<Town> town = Towns.get(serverLevel.getServer()).byHall(level.dimension(), pos);
			if (town.isPresent() && !town.get().mayor().equals(player.getUUID())) {
				player.sendSystemMessage(Component.translatable("message.minecities.hall.not_mayor",
						town.get().name()).withStyle(ChatFormatting.RED));
				return false;
			}
			return true;
		});
	}

	// Fondation et dissolution

	public static void found(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
		Towns towns = Towns.get(level.getServer());
		Optional<Town> neighbour = towns.tooClose(level.dimension(), pos);
		if (neighbour.isPresent()) {
			player.sendSystemMessage(Component.translatable("message.minecities.found.too_close",
					neighbour.get().name()).withStyle(ChatFormatting.RED));
			level.removeBlock(pos, false);
			if (!player.isCreative()) {
				player.getInventory().placeItemBackInInventory(stack.copyWithCount(1));
			}
			return;
		}
		Component customName = stack.get(DataComponents.CUSTOM_NAME);
		String name = customName != null ? customName.getString() : uniqueName(towns, level);
		Town town = Town.found(name, level.dimension(), pos, player.getUUID(), player.getGameProfile().name(),
				level.getGameTime());
		towns.add(town);
		census(level, town);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
				"message.minecities.found", player.getDisplayName(), name).withStyle(ChatFormatting.GOLD), false);
		if (town.villagers() > 0) {
			player.sendSystemMessage(Component.translatable("message.minecities.found.village", town.villagers(),
					Component.translatable(town.tier().translationKey())).withStyle(ChatFormatting.GREEN));
		}
		player.sendSystemMessage(Component.translatable("message.minecities.found.hint")
				.withStyle(ChatFormatting.GRAY));
		level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0f, 1.0f);
	}

	private static String uniqueName(Towns towns, ServerLevel level) {
		String name = Names.town(level.getRandom()::nextInt);
		for (int i = 0; i < 10 && towns.byName(name).isPresent(); i++) {
			name = Names.town(level.getRandom()::nextInt);
		}
		return name;
	}

	public static void hallRemoved(ServerLevel level, BlockPos pos) {
		Towns towns = Towns.get(level.getServer());
		towns.byHall(level.dimension(), pos).ifPresent(town -> {
			towns.remove(town);
			LAST_SURVEY.remove(town.id());
			level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
					"message.minecities.disbanded", town.name()).withStyle(ChatFormatting.GOLD), false);
		});
	}

	// Maisons

	/** Relève la maison de cette plaque, l'enregistre (ou l'oublie) et en informe le joueur. */
	public static void declareHouse(ServerLevel level, BlockPos plaque, ServerPlayer player) {
		Towns towns = Towns.get(level.getServer());
		Optional<Town> found = towns.at(level.dimension(), plaque);
		if (found.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.minecities.house.no_town")
					.withStyle(ChatFormatting.RED));
			return;
		}
		Town town = found.get();
		HouseScanner.Result result = survey(level, town, plaque);
		if (result.valid()) {
			House house = town.house(plaque).orElseThrow();
			player.sendSystemMessage(Component.translatable("message.minecities.house.valid", town.name(),
					house.beds().size(), house.workstations().size(), house.containers().size(), house.volume())
					.withStyle(ChatFormatting.GREEN));
			for (House.Workstation station : house.workstations()) {
				player.sendSystemMessage(Component.translatable("message.minecities.house.workstation",
						Component.translatable(station.job().translationKey())).withStyle(ChatFormatting.GRAY));
			}
		} else {
			player.sendSystemMessage(Component.translatable("message.minecities.house.invalid",
					Component.translatable(result.status().translationKey())).withStyle(ChatFormatting.RED));
		}
		settle(level, town);
		towns.changed();
	}

	/** Relève le bâtiment et met la ville à jour : enregistré s'il est valide, oublié sinon. */
	static HouseScanner.Result survey(ServerLevel level, Town town, BlockPos plaque) {
		HouseScanner.Result raw = HouseScanner.scan((x, y, z) -> classify(level, new BlockPos(x, y, z)),
				new HouseScanner.Pos(plaque.getX(), plaque.getY(), plaque.getZ()));
		List<BlockPos> beds = raw.beds().stream().map(TownService::pos)
				.filter(b -> !town.bedClaimedElsewhere(b, plaque)).toList();
		List<House.Workstation> stations = raw.workstations().stream().map(TownService::pos)
				.flatMap(p -> Job.ofWorkstation(level, p, level.getBlockState(p)).map(j -> new House.Workstation(p, j)).stream())
				.toList();
		HouseScanner.Result result = raw;
		boolean declared = raw.plaques().stream().anyMatch(p -> town.house(pos(p)).isPresent());
		if (raw.valid() && declared) {
			result = raw.withStatus(HouseScanner.Status.ALREADY_DECLARED);
		} else if (raw.valid() && beds.isEmpty() && stations.isEmpty()) {
			result = raw.withStatus(HouseScanner.Status.NO_BED);
		}
		boolean village = town.house(plaque).map(House::village).orElse(false);
		if (result.valid() && village && beds.isEmpty()) {
			result = raw.withStatus(HouseScanner.Status.NO_BED);
		}
		if (result.valid()) {
			List<BlockPos> containers = raw.containers().stream().map(TownService::pos).toList();
			// Une maison de village garde ses postes de travail pour ses villageois.
			town.putHouse(new House(plaque, beds, containers, village ? List.of() : stations, raw.volume(), village));
		} else {
			town.removeHouse(plaque);
		}
		return result;
	}

	private static BlockPos pos(HouseScanner.Pos p) {
		return new BlockPos(p.x(), p.y(), p.z());
	}

	/** Ce qu'est un bloc pour une maison : voir {@link HouseScanner.Cell}. */
	static HouseScanner.Cell classify(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			// Hors des chunks chargés : on ne sait pas, la maison est tenue pour ouverte.
			return HouseScanner.Cell.OPEN;
		}
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		if (block instanceof BedBlock) {
			return state.getValue(BedBlock.PART) == BedPart.HEAD ? HouseScanner.Cell.BED : HouseScanner.Cell.SOLID;
		}
		if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
			return HouseScanner.Cell.DOOR;
		}
		if (block instanceof CrossCollisionBlock || block instanceof WallBlock) {
			// Vitres, barreaux, clôtures et murets ferment une ouverture.
			return HouseScanner.Cell.SOLID;
		}
		if (Job.ofWorkstation(level, pos, state).isPresent()) {
			return HouseScanner.Cell.WORKSTATION;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity) {
			return HouseScanner.Cell.CONTAINER;
		}
		if (state.isAir()) {
			return HouseScanner.Cell.OPEN;
		}
		if (state.is(McBlocks.HOUSE_PLAQUE)) {
			return HouseScanner.Cell.PLAQUE;
		}
		return state.blocksMotion() ? HouseScanner.Cell.SOLID : HouseScanner.Cell.OPEN;
	}

	public static void plaqueRemoved(ServerLevel level, BlockPos plaque) {
		Towns towns = Towns.get(level.getServer());
		towns.at(level.dimension(), plaque).ifPresent(town -> {
			if (town.house(plaque).isPresent()) {
				town.removeHouse(plaque);
				notify(level.getServer(), town, Component.translatable("message.minecities.house.removed",
						plaque.toShortString()));
				settle(level, town);
				towns.changed();
			}
		});
	}

	/**
	 * Loge les sans-abri là où il reste des lits, donne les postes libres aux sans-emploi, et
	 * recalcule le palier.
	 */
	private static void settle(ServerLevel level, Town town) {
		for (UUID citizen : town.residents().keySet()) {
			town.house(citizen);
		}
		town.assignJobs().forEach((citizen, job) -> notify(level.getServer(), town, Component.translatable(
				"message.minecities.citizen.hired", town.resident(citizen).map(Resident::name).orElse("?"),
				Component.translatable(job.job().translationKey()))));
		town.updateTier().ifPresent(before -> announceTier(level, town, before));
	}

	// La vie de la ville

	private static void tick(MinecraftServer server) {
		if (++ticks % TICK_INTERVAL != 0) {
			return;
		}
		Towns towns = Towns.get(server);
		for (Town town : towns.all()) {
			ServerLevel level = server.getLevel(town.dimension());
			if (level == null || !level.isPositionEntityTicking(town.hall())) {
				continue;
			}
			long now = level.getGameTime();
			if (now - LAST_SURVEY.getOrDefault(town.id(), 0L) >= SURVEY_INTERVAL) {
				LAST_SURVEY.put(town.id(), now);
				for (House house : town.houses()) {
					if (!level.isLoaded(house.plaque())) {
						continue;
					}
					if (house.village() || level.getBlockState(house.plaque()).is(McBlocks.HOUSE_PLAQUE)) {
						survey(level, town, house.plaque());
					} else {
						town.removeHouse(house.plaque());
					}
				}
			}
			census(level, town);
			settle(level, town);
			if (now - town.lastArrival() >= IMMIGRATION_DELAY && town.freeBeds() > 0
					&& granary(level, town).map(TownService::hasFood).orElse(false)) {
				immigrate(level, town);
			}
			towns.changed();
		}
	}

	/**
	 * Recense les villageois vanilla du territoire : une ville fondée sur un village le reprend, ses
	 * villageois restent des villageois (et gardent leurs échanges) mais comptent dans la population.
	 */
	static void census(ServerLevel level, Town town) {
		BlockPos hall = town.hall();
		int r = town.radius();
		AABB area = new AABB(hall.getX() - r, hall.getY() - 48, hall.getZ() - r, hall.getX() + r + 1,
				hall.getY() + 48, hall.getZ() + r + 1);
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area,
				v -> v.isAlive() && town.contains(level.dimension(), v.blockPosition()));
		// Leurs lits leur restent ; les maisons où ils sont deviennent des maisons de la ville.
		java.util.Set<BlockPos> beds = new java.util.HashSet<>();
		for (Villager villager : villagers) {
			villager.getBrain().getMemory(MemoryModuleType.HOME)
					.filter(home -> home.dimension().equals(level.dimension()) && town.contains(level.dimension(), home.pos()))
					.ifPresent(home -> beds.add(home.pos()));
		}
		town.reserveBeds(beds);
		for (BlockPos bed : beds) {
			if (!town.hasBed(bed) && level.isLoaded(bed)) {
				adoptVillageHouse(level, town, bed);
			}
		}
		if (town.setVillagers(villagers.size())) {
			town.updateTier().ifPresent(before -> announceTier(level, town, before));
		}
		// Un villageois sans métier peut devenir citoyen, un à la fois, au rythme de l'immigration.
		if (level.getGameTime() - town.lastArrival() >= IMMIGRATION_DELAY) {
			villagers.stream().filter(TownService::jobless).findFirst().ifPresent(v -> naturalize(level, town, v));
		}
	}

	private static boolean jobless(Villager villager) {
		var profession = villager.getVillagerData().profession();
		return !villager.isBaby() && (profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT));
	}

	/**
	 * Relève la maison d'un village vanilla depuis le lit d'un de ses villageois, sans plaque : la
	 * première case libre autour du lit sert de point de départ.
	 */
	static void adoptVillageHouse(ServerLevel level, Town town, BlockPos bed) {
		List<BlockPos> starts = new ArrayList<>(List.of(bed.above()));
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			starts.add(bed.relative(d));
		}
		for (BlockPos start : starts) {
			if (classify(level, start) != HouseScanner.Cell.OPEN || town.house(start).isPresent()) {
				continue;
			}
			HouseScanner.Result raw = HouseScanner.scan((x, y, z) -> classify(level, new BlockPos(x, y, z)),
					new HouseScanner.Pos(start.getX(), start.getY(), start.getZ()));
			List<BlockPos> beds = raw.beds().stream().map(TownService::pos)
					.filter(b -> !town.bedClaimedElsewhere(b, start)).toList();
			if (raw.valid() && !beds.isEmpty() && raw.plaques().isEmpty()) {
				town.putHouse(new House(start, beds, raw.containers().stream().map(TownService::pos).toList(),
						List.of(), raw.volume(), true));
				return;
			}
		}
	}

	/** Un villageois sans métier devient citoyen : il garde sa place, change d'habit et de nom. */
	static void naturalize(ServerLevel level, Town town, Villager villager) {
		CitizenEntity citizen = McEntities.CITIZEN.create(level, EntitySpawnReason.CONVERSION);
		if (citizen == null) {
			return;
		}
		String name = Names.citizen(level.getRandom()::nextInt);
		citizen.snapTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
		citizen.joinTown(town, name);
		level.addFreshEntity(citizen);
		town.welcome(citizen.getUUID(), name, level.getGameTime());
		villager.discard();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, citizen.getX(), citizen.getY() + 1, citizen.getZ(), 12,
				0.4, 0.6, 0.4, 0);
		notify(level.getServer(), town, Component.translatable("message.minecities.citizen.naturalized", name,
				town.name()));
		town.setVillagers(Math.max(0, town.villagers() - 1));
		Towns.get(level.getServer()).changed();
	}

	/** Un nouveau citoyen arrive à l'hôtel de ville, attiré par un lit libre et un grenier garni. */
	public static Optional<CitizenEntity> immigrate(ServerLevel level, Town town) {
		BlockPos spot = arrivalSpot(level, town);
		CitizenEntity citizen = McEntities.CITIZEN.create(level, EntitySpawnReason.EVENT);
		if (citizen == null) {
			return Optional.empty();
		}
		String name = Names.citizen(level.getRandom()::nextInt);
		citizen.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		citizen.joinTown(town, name);
		level.addFreshEntity(citizen);
		town.welcome(citizen.getUUID(), name, level.getGameTime());
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5, 12,
				0.4, 0.6, 0.4, 0);
		notify(level.getServer(), town, Component.translatable("message.minecities.citizen.arrived", name,
				town.name()));
		town.updateTier().ifPresent(before -> announceTier(level, town, before));
		Towns.get(level.getServer()).changed();
		return Optional.of(citizen);
	}

	private static BlockPos arrivalSpot(ServerLevel level, Town town) {
		BlockPos hall = town.hall();
		for (int attempt = 0; attempt < 16; attempt++) {
			int x = hall.getX() + level.getRandom().nextInt(13) - 6;
			int z = hall.getZ() + level.getRandom().nextInt(13) - 6;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			if (Math.abs(y - hall.getY()) <= 3 && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(),
					net.minecraft.core.Direction.UP) && level.getBlockState(pos).isAir()
					&& level.getBlockState(pos.above()).isAir()) {
				return pos;
			}
		}
		return hall.above();
	}

	/** Un citoyen quitte la ville : mort, parti, ou ville disparue. */
	public static void farewell(ServerLevel level, UUID townId, UUID citizen, Component message) {
		Towns towns = Towns.get(level.getServer());
		towns.byId(townId).ifPresent(town -> {
			if (town.resident(citizen).isEmpty()) {
				return;
			}
			town.farewell(citizen);
			notify(level.getServer(), town, message);
			town.updateTier().ifPresent(before -> announceTier(level, town, before));
			towns.changed();
		});
	}

	private static void announceTier(ServerLevel level, Town town, TownTier before) {
		boolean up = town.tier().ordinal() > before.ordinal();
		Component tier = Component.translatable(town.tier().translationKey());
		if (up) {
			level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
					"message.minecities.tier.up", town.name(), tier).withStyle(ChatFormatting.GOLD), false);
			level.playSound(null, town.hall(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.0f);
		} else {
			notify(level.getServer(), town, Component.translatable("message.minecities.tier.down", town.name(), tier));
		}
	}

	/** Prévient le maire et ses adjoints connectés. */
	public static void notify(MinecraftServer server, Town town, Component message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (town.canManage(player.getUUID())) {
				player.sendSystemMessage(Component.translatable("message.minecities.prefix", town.name())
						.withStyle(ChatFormatting.DARK_AQUA).append(message.copy().withStyle(ChatFormatting.WHITE)));
			}
		}
	}

	// Vivres

	public static Optional<TownHallBlockEntity> granary(ServerLevel level, Town town) {
		return level.isLoaded(town.hall()) && level.getBlockEntity(town.hall()) instanceof TownHallBlockEntity hall
				? Optional.of(hall) : Optional.empty();
	}

	public static boolean isFood(ItemStack stack) {
		return stack.has(DataComponents.FOOD) && !UNHEALTHY.contains(stack.getItem());
	}

	public static boolean hasFood(Container container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (isFood(container.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	public static int countMeals(Container container) {
		int meals = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (isFood(stack)) {
				meals += stack.getCount();
			}
		}
		return meals;
	}

	/** Prend un aliment dans ce contenant, le plus nourrissant ; vide si rien ne se mange. */
	public static ItemStack takeFood(Container container) {
		int best = -1;
		int bestNutrition = -1;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food != null && isFood(stack) && food.nutrition() > bestNutrition) {
				best = i;
				bestNutrition = food.nutrition();
			}
		}
		if (best < 0) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = container.removeItem(best, 1);
		container.setChanged();
		return taken;
	}

	/**
	 * Où ce citoyen peut trouver à manger, du plus proche de lui au plus commun : les coffres de sa
	 * maison, puis le grenier de l'hôtel de ville.
	 */
	public static List<BlockPos> pantries(ServerLevel level, Town town, Resident resident) {
		List<BlockPos> pantries = new ArrayList<>();
		resident.home().flatMap(town::house).ifPresent(h -> pantries.addAll(h.containers()));
		pantries.add(town.hall());
		return pantries.stream().filter(p -> level.isLoaded(p) && level.getBlockEntity(p) instanceof Container c
				&& hasFood(c)).toList();
	}

	// Affichage

	public static Component granaryTitle(ServerLevel level, BlockPos pos, Component fallback) {
		return Towns.get(level.getServer()).byHall(level.dimension(), pos)
				.<Component>map(t -> Component.translatable("container.minecities.granary.of", t.name()))
				.orElse(fallback);
	}

	public static void describeHall(ServerLevel level, BlockPos pos, ServerPlayer player) {
		Optional<Town> town = Towns.get(level.getServer()).byHall(level.dimension(), pos);
		if (town.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.minecities.hall.orphan")
					.withStyle(ChatFormatting.RED));
			return;
		}
		describe(level, town.get(), player);
	}

	public static void describe(ServerLevel level, Town town, Player player) {
		player.sendSystemMessage(Component.translatable("info.minecities.title", town.name(),
				Component.translatable(town.tier().translationKey())).withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("info.minecities.mayor", town.mayorName()));
		player.sendSystemMessage(Component.translatable("info.minecities.population", town.citizens(),
				town.capacity(), town.homes().size()));
		if (town.villagers() > 0) {
			player.sendSystemMessage(Component.translatable("info.minecities.villagers", town.villagers(),
					town.population()));
		}
		if (town.workstations() > 0) {
			long employed = town.residents().values().stream().filter(r -> r.job().isPresent()).count();
			player.sendSystemMessage(Component.translatable("info.minecities.jobs", employed, town.workstations()));
		}
		player.sendSystemMessage(Component.translatable("info.minecities.territory", town.radius(),
				town.hall().toShortString()));
		granary(level, town).ifPresent(g -> player.sendSystemMessage(
				Component.translatable("info.minecities.granary", countMeals(g))));
		TownTier next = town.tier().next();
		if (next != null) {
			player.sendSystemMessage(Component.translatable("info.minecities.next",
					Component.translatable(next.translationKey()), next.minCitizens(), next.minHouses())
					.withStyle(ChatFormatting.GRAY));
		}
		if (town.capacity() == 0) {
			player.sendSystemMessage(Component.translatable("info.minecities.hint.no_house")
					.withStyle(ChatFormatting.YELLOW));
		} else if (granary(level, town).map(g -> !hasFood(g)).orElse(false)) {
			player.sendSystemMessage(Component.translatable("info.minecities.hint.no_food")
					.withStyle(ChatFormatting.YELLOW));
		}
	}
}
