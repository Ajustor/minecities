package com.ajustor.minecities.town;

import com.ajustor.minecities.block.HousePlaqueBlock;
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

	/** Quand chaque bâtiment a été relevé pour la dernière fois, par ville. */
	private static final Map<UUID, Map<BlockPos, Long>> LAST_SURVEY = new HashMap<>();
	/** Pas plus de tant de relevés par ville à chaque passage : les grandes villes étalent leurs relevés. */
	private static final int SURVEYS_PER_PASS = 4;
	/** Les villes dont le maire démonte l'hôtel de ville : elles disparaissent tout de suite. */
	private static final java.util.Set<UUID> DISMANTLED = new java.util.HashSet<>();
	private static int ticks;

	private TownService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TownService::tick);
		net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.START_SLEEPING.register((entity, bed) -> {
			if (entity instanceof ServerPlayer player) {
				sleptIn(player, bed);
			}
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel serverLevel)) {
				return true;
			}
			if (!state.is(McBlocks.TOWN_HALL)) {
				return mayBreak(serverLevel, player, pos);
			}
			Optional<Town> town = Towns.get(serverLevel.getServer()).byHall(level.dimension(), pos);
			if (town.isPresent() && !town.get().mayor().equals(player.getUUID())) {
				player.sendSystemMessage(Component.translatable("message.minecities.hall.not_mayor",
						town.get().name()).withStyle(ChatFormatting.RED));
				return false;
			}
			// Le maire démonte son hôtel de ville : la ville disparaît sans attendre.
			town.ifPresent(t -> DISMANTLED.add(t.id()));
			return true;
		});
	}

	/**
	 * Ne casse les blocs d'un bâtiment déclaré que celui dont le rang en a le droit (le maire et ses
	 * adjoints toujours, les opérateurs aussi).
	 */
	private static boolean mayBreak(ServerLevel level, Player player, BlockPos pos) {
		if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
			return true;
		}
		for (Town town : Towns.get(level.getServer()).all()) {
			if (town.dimension().equals(level.dimension()) && !town.may(player.getUUID(), Membership.Permission.BREAK)
					&& town.houses().stream().anyMatch(h -> h.encloses(pos))) {
				player.sendSystemMessage(Component.translatable("message.minecities.protected", town.name())
						.withStyle(ChatFormatting.RED));
				return false;
			}
		}
		return true;
	}

	// Fondation et dissolution

	public static void found(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
		Towns towns = Towns.get(level.getServer());
		Optional<Town> orphan = towns.byHall(level.dimension(), pos).filter(t -> t.orphanedAt().isPresent());
		if (orphan.isPresent()) {
			// L'hôtel de ville détruit est reposé à sa place : la ville le retrouve.
			if (orphan.get().canManage(player.getUUID())) {
				orphan.get().orphan(Optional.empty());
				towns.changed();
				level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
						"message.minecities.hall.restored", orphan.get().name()).withStyle(ChatFormatting.GREEN), false);
				level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0f, 1.0f);
				return;
			}
			player.sendSystemMessage(Component.translatable("message.minecities.hall.not_mayor", orphan.get().name())
					.withStyle(ChatFormatting.RED));
			level.removeBlock(pos, false);
			if (!player.isCreative()) {
				player.getInventory().placeItemBackInInventory(stack.copyWithCount(1));
			}
			return;
		}
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

	/**
	 * L'hôtel de ville a disparu. Démonté par le maire, la ville disparaît ; détruit autrement (une
	 * explosion, une commande), elle attend quelques minutes qu'on le repose au même endroit.
	 */
	public static void hallRemoved(ServerLevel level, BlockPos pos) {
		Towns towns = Towns.get(level.getServer());
		towns.byHall(level.dimension(), pos).ifPresent(town -> {
			int grace = com.ajustor.minecities.McConfig.get().hallGraceMinutes;
			if (DISMANTLED.remove(town.id()) || grace <= 0) {
				disband(level, town);
				return;
			}
			if (town.orphanedAt().isPresent()) {
				// Déjà en attente (la destruction se signale parfois deux fois).
				return;
			}
			town.orphan(Optional.of(level.getGameTime()));
			towns.changed();
			level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
					"message.minecities.hall.destroyed", town.name(), grace).withStyle(ChatFormatting.RED), false);
		});
	}

	private static void disband(ServerLevel level, Town town) {
		Towns.get(level.getServer()).remove(town);
		LAST_SURVEY.remove(town.id());
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
				"message.minecities.disbanded", town.name()).withStyle(ChatFormatting.GOLD), false);
	}

	// Annexes

	/** Une mairie annexe est posée : la ville l'accepte, ou le joueur la récupère avec la raison. */
	public static void placeAnnex(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
		Towns towns = Towns.get(level.getServer());
		Optional<Town> found = towns.at(level.dimension(), pos);
		Optional<String> refusal = found.isEmpty() ? Optional.of("outside")
				: found.get().canManage(player.getUUID()) ? found.get().annexRefusal(level.dimension(), pos)
				: Optional.of("not_mayor");
		if (refusal.isPresent()) {
			player.sendSystemMessage(Component.translatable("message.minecities.annex.refused." + refusal.get(),
					Component.translatable(TownTier.CITY.translationKey()), Town.ANNEX_SPACING)
					.withStyle(ChatFormatting.RED));
			level.removeBlock(pos, false);
			if (!player.isCreative()) {
				player.getInventory().placeItemBackInInventory(stack.copyWithCount(1));
			}
			return;
		}
		Town town = found.get();
		town.addAnnex(pos);
		towns.changed();
		notify(level.getServer(), town, Component.translatable("message.minecities.annex.placed", pos.toShortString(),
				Town.ANNEX_RADIUS));
		level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0f, 1.4f);
	}

	public static void annexRemoved(ServerLevel level, BlockPos pos) {
		Towns towns = Towns.get(level.getServer());
		for (Town town : towns.all()) {
			if (town.dimension().equals(level.dimension()) && town.removeAnnex(pos)) {
				notify(level.getServer(), town, Component.translatable("message.minecities.annex.removed",
						pos.toShortString()));
				towns.changed();
			}
		}
	}

	// Maisons

	/**
	 * Relève la maison de cette plaque, l'enregistre (ou l'oublie) et en informe le joueur. Faux si son
	 * rang ne lui permet pas de déclarer une maison dans cette ville.
	 */
	public static boolean declareHouse(ServerLevel level, BlockPos plaque, ServerPlayer player) {
		Towns towns = Towns.get(level.getServer());
		Optional<Town> found = towns.at(level.dimension(), plaque);
		if (found.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.minecities.house.no_town")
					.withStyle(ChatFormatting.RED));
			return true;
		}
		Town town = found.get();
		if (!town.may(player.getUUID(), Membership.Permission.DECLARE)) {
			player.sendSystemMessage(Component.translatable("message.minecities.house.forbidden", town.name(),
					Component.translatable(town.rank(player.getUUID()).translationKey())).withStyle(ChatFormatting.RED));
			return false;
		}
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
		return true;
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
		// Un registre d'entrepôt fait du bâtiment l'entrepôt de la ville.
		boolean warehouse = raw.workstations().stream().anyMatch(p -> level.getBlockState(pos(p))
				.is(McBlocks.WAREHOUSE_LEDGER));
		if (raw.valid() && declared) {
			result = raw.withStatus(HouseScanner.Status.ALREADY_DECLARED);
		} else if (raw.valid() && beds.isEmpty() && stations.isEmpty() && !warehouse) {
			result = raw.withStatus(HouseScanner.Status.NO_BED);
		}
		boolean village = town.house(plaque).map(House::village).orElse(false);
		if (result.valid() && village && beds.isEmpty()) {
			result = raw.withStatus(HouseScanner.Status.NO_BED);
		}
		if (result.valid()) {
			List<BlockPos> containers = raw.containers().stream().map(TownService::pos).toList();
			// Une maison de village garde ses postes de travail pour ses villageois.
			House house = new House(plaque, beds, containers, village ? List.of() : stations, raw.volume(), village)
					.within(pos(raw.min()).offset(-1, -1, -1), pos(raw.max()).offset(1, 1, 1));
			town.putHouse(warehouse ? house.asWarehouse() : house);
		} else {
			town.removeHouse(plaque);
		}
		// La plaque montre le résultat : porte verte si le bâtiment est enregistré, rouge sinon.
		BlockState state = level.getBlockState(plaque);
		if (state.is(McBlocks.HOUSE_PLAQUE)) {
			HousePlaqueBlock.Status status = result.valid() ? HousePlaqueBlock.Status.VALID
					: HousePlaqueBlock.Status.INVALID;
			if (state.getValue(HousePlaqueBlock.STATUS) != status) {
				level.setBlock(plaque, state.setValue(HousePlaqueBlock.STATUS, status), Block.UPDATE_CLIENTS);
			}
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
		if (Job.ofWorkstation(level, pos, state).isPresent() || state.is(McBlocks.WAREHOUSE_LEDGER)) {
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

	/** Un chantier fini : sa plaque déclare le bâtiment, sans joueur pour l'entendre. */
	public static void declareBuilt(ServerLevel level, Town town, BlockPos plaque) {
		HouseScanner.Result result = survey(level, town, plaque);
		if (!result.valid()) {
			notify(level.getServer(), town, Component.translatable("message.minecities.house.invalid",
					Component.translatable(result.status().translationKey())));
		}
		settle(level, town);
		Towns.get(level.getServer()).changed();
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
		town.assignJobs().forEach((citizen, job) -> {
			notify(level.getServer(), town, Component.translatable("message.minecities.citizen.hired",
					town.resident(citizen).map(Resident::name).orElse("?"),
					Component.translatable(job.job().translationKey())));
			if (level.getEntity(citizen) instanceof CitizenEntity entity) {
				entity.say("hired", Component.translatable("speech.minecities.hired",
						Component.translatable(job.job().translationKey())));
			}
		});
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
			if (town.orphanedAt().isEmpty() && !level.getBlockState(town.hall()).is(McBlocks.TOWN_HALL)) {
				// L'hôtel de ville a disparu sans prévenir (une commande, un autre mod) : la ville attend.
				hallRemoved(level, town.hall());
			}
			if (town.orphanedAt().isPresent()) {
				// Sans hôtel de ville : au bout du délai de grâce, la ville disparaît.
				if (now - town.orphanedAt().get() > com.ajustor.minecities.McConfig.get().hallGraceMinutes * 1200L) {
					disband(level, town);
				}
				continue;
			}
			Elections.tick(level, town, now);
			// Les bâtiments sont relevés à leur tour, quelques-uns à chaque passage.
			Map<BlockPos, Long> surveyed = LAST_SURVEY.computeIfAbsent(town.id(), id -> new HashMap<>());
			int budget = SURVEYS_PER_PASS;
			for (House house : town.houses()) {
				if (budget == 0) {
					break;
				}
				if (!level.isLoaded(house.plaque())
						|| now - surveyed.computeIfAbsent(house.plaque(), p -> now) < SURVEY_INTERVAL) {
					continue;
				}
				surveyed.put(house.plaque(), now);
				budget--;
				if (house.village() || level.getBlockState(house.plaque()).is(McBlocks.HOUSE_PLAQUE)) {
					survey(level, town, house.plaque());
				} else {
					town.removeHouse(house.plaque());
				}
			}
			forgetMissing(level, town, now);
			census(level, town);
			settle(level, town);
			// Plus d'immigration : sous 5 habitants des nouveaux venus arrivent, au-delà on naît.
			Families.tick(level, town);
			Raids.consider(level, town);
			towns.changed();
		}
	}

	/**
	 * Raye les citoyens disparus : jamais revus depuis une heure (réglage) alors que leur maison
	 * (ou l'hôtel de ville, pour un sans-logis) est chargée. Un citoyen supprimé par une commande ou un
	 * autre mod ne reste pas inscrit pour toujours.
	 */
	private static void forgetMissing(ServerLevel level, Town town, long now) {
		long limit = com.ajustor.minecities.McConfig.get().missingCitizenMinutes * 1200L;
		for (Map.Entry<UUID, Resident> e : town.residents().entrySet()) {
			UUID id = e.getKey();
			if (level.getEntity(id) != null) {
				town.seen(id, now);
				continue;
			}
			BlockPos anchor = e.getValue().home().orElse(town.hall());
			long seen = town.lastSeen(id, now);
			if (limit > 0 && level.isPositionEntityTicking(anchor) && now - seen > limit) {
				farewell(level, town.id(), id, Component.translatable("message.minecities.citizen.missing",
						e.getValue().name()));
			}
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
		// Un villageois sans métier peut devenir citoyen, un à la fois, au rythme de l'immigration :
		// deux fois plus vite si la ville est heureuse, jamais si elle est malheureuse.
		double mood = town.averageMood().orElse((double) com.ajustor.minecities.citizen.Happiness.BASE);
		long delay = mood >= com.ajustor.minecities.citizen.Happiness.HAPPY ? IMMIGRATION_DELAY / 2 : IMMIGRATION_DELAY;
		if (mood > com.ajustor.minecities.citizen.Happiness.UNHAPPY && level.getGameTime() - town.lastArrival() >= delay) {
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
						List.of(), raw.volume(), true).within(pos(raw.min()).offset(-1, -1, -1),
						pos(raw.max()).offset(1, 1, 1)));
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

	/** Recalcule le palier (après un palier imposé) et l'annonce s'il a changé. */
	public static void retier(ServerLevel level, Town town) {
		town.updateTier().ifPresent(before -> announceTier(level, town, before));
		settle(level, town);
	}

	private static void announceTier(ServerLevel level, Town town, TownTier before) {
		boolean up = town.tier().ordinal() > before.ordinal();
		Component tier = Component.translatable(town.tier().translationKey());
		if (up) {
			level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
					"message.minecities.tier.up", town.name(), tier).withStyle(ChatFormatting.GOLD), false);
			level.playSound(null, town.hall(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.0f);
			for (int i = 0; i < 3; i++) {
				Festivals.launch(level, town.hall());
			}
			for (ServerPlayer player : level.players()) {
				if (town.contains(level.dimension(), player.blockPosition())) {
					Borders.arrived(player, town);
					Borders.title(player, Component.literal(town.name()).withStyle(ChatFormatting.GOLD),
							Component.translatable("hud.minecities.tier_up", tier).withStyle(ChatFormatting.YELLOW));
				}
			}
			// Ce que le nouveau palier ouvre : des métiers, des schémas, un territoire plus grand.
			List<Component> unlocked = new ArrayList<>();
			for (Job job : Job.values()) {
				if (job.minTier() == town.tier()) {
					unlocked.add(Component.translatable(job.translationKey()));
				}
			}
			com.ajustor.minecities.build.Blueprints.all(level.getServer()).values().stream()
					.filter(b -> b.tier() == town.tier() && !b.legacy())
					.forEach(b -> unlocked.add(b.displayName()));
			if (town.tier().annexes() > 0) {
				unlocked.add(Component.translatable("message.minecities.tier.annexes", town.tier().annexes()));
			}
			notify(level.getServer(), town, Component.translatable("message.minecities.tier.radius", town.radius()));
			if (!unlocked.isEmpty()) {
				net.minecraft.network.chat.MutableComponent list = Component.empty();
				for (Component c : unlocked) {
					if (!list.getSiblings().isEmpty()) {
						list.append(", ");
					}
					list.append(c);
				}
				notify(level.getServer(), town, Component.translatable("message.minecities.tier.unlocks", list));
			}
		} else {
			notify(level.getServer(), town, Component.translatable("message.minecities.tier.down", town.name(), tier));
		}
	}

	/** Prend ou lève un décret, et l'annonce à la ville. */
	public static void decree(ServerLevel level, Town town, Decree decree) {
		boolean on = town.toggle(decree);
		Component message = Component.translatable(on ? "message.minecities.decree.on" : "message.minecities.decree.off",
				town.name(), Component.translatable(decree.translationKey()));
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (town.canManage(player.getUUID()) || town.contains(player.level().dimension(), player.blockPosition())) {
				player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GOLD));
			}
		}
		level.playSound(null, town.hall(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0f, 0.8f);
		Towns.get(level.getServer()).changed();
	}

	/** Peut-il ouvrir le grenier de la ville (l'hôtel de ville ou une annexe) ? Sinon, il le lui dit. */
	public static boolean mayOpenGranary(ServerPlayer player, Town town) {
		if (town.may(player.getUUID(), Membership.Permission.GRANARY)) {
			return true;
		}
		player.sendSystemMessage(Component.translatable("message.minecities.granary.forbidden", town.name(),
				Component.translatable(town.rank(player.getUUID()).translationKey())).withStyle(ChatFormatting.RED));
		return false;
	}

	// Habitants joueurs

	/**
	 * Le joueur demande à s'installer dans la ville : le maire ou un adjoint accepte ou refuse. On
	 * n'appartient qu'à une ville à la fois.
	 */
	public static boolean requestJoin(MinecraftServer server, Town town, ServerPlayer player) {
		UUID id = player.getUUID();
		if (town.isMember(id)) {
			player.sendSystemMessage(Component.translatable("message.minecities.join.already", town.name())
					.withStyle(ChatFormatting.GOLD));
			return false;
		}
		Optional<Town> other = Towns.get(server).memberOf(id).stream().findFirst();
		if (other.isPresent()) {
			player.sendSystemMessage(Component.translatable("message.minecities.join.elsewhere", other.get().name())
					.withStyle(ChatFormatting.RED));
			return false;
		}
		String name = player.getGameProfile().name();
		if (!town.membership().request(id, name)) {
			player.sendSystemMessage(Component.translatable("message.minecities.join.pending", town.name())
					.withStyle(ChatFormatting.GOLD));
			return false;
		}
		Towns.get(server).changed();
		player.sendSystemMessage(Component.translatable("message.minecities.join.sent", town.name())
				.withStyle(ChatFormatting.GREEN));
		notify(server, town, Component.translatable("message.minecities.join.request", name).append(" ")
				.append(Component.translatable("message.minecities.join.how").withStyle(style -> style
						.withColor(ChatFormatting.AQUA)
						.withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(
								"/ville resident accept " + name)))));
		return true;
	}

	/** Le maire ou un adjoint accepte (ou refuse) la demande de ce joueur. Faux s'il n'y en avait pas. */
	public static boolean answerJoin(MinecraftServer server, Town town, UUID player, boolean accept) {
		Optional<String> name = town.membership().requests().stream().filter(e -> e.getKey().equals(player))
				.map(Map.Entry::getValue).findFirst();
		if (name.isEmpty()) {
			return false;
		}
		// Entre-temps, il s'est peut-être installé ailleurs.
		Optional<Town> other = Towns.get(server).memberOf(player).stream().findFirst();
		town.membership().dropRequest(player);
		boolean joined = accept && other.isEmpty();
		if (joined) {
			town.membership().addResident(player, name.get());
		}
		Towns.get(server).changed();
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) {
			String key = joined ? "message.minecities.join.accepted"
					: other.isPresent() ? "message.minecities.join.elsewhere" : "message.minecities.join.refused";
			online.sendSystemMessage(Component.translatable(key, other.map(Town::name).orElse(town.name()))
					.withStyle(joined ? ChatFormatting.GREEN : ChatFormatting.RED));
		}
		notify(server, town, Component.translatable(joined ? "message.minecities.resident.welcome"
				: "message.minecities.resident.turned_away", name.get()));
		return true;
	}

	/** Le joueur quitte la ville (habitant ou adjoint) ; le maire doit d'abord passer l'écharpe. */
	public static boolean leave(MinecraftServer server, Town town, ServerPlayer player) {
		UUID id = player.getUUID();
		if (town.mayor().equals(id) || !town.isMember(id)) {
			return false;
		}
		town.removeDeputy(id);
		town.membership().removeResident(id);
		Towns.get(server).changed();
		player.sendSystemMessage(Component.translatable("message.minecities.leave.done", town.name())
				.withStyle(ChatFormatting.GOLD));
		notify(server, town, Component.translatable("message.minecities.resident.left", player.getGameProfile().name()));
		return true;
	}

	/** Le maire ou un adjoint renvoie un habitant (un adjoint, c'est au maire de le démettre d'abord). */
	public static boolean expel(MinecraftServer server, Town town, UUID player) {
		Optional<String> name = town.membership().residentList().stream().filter(e -> e.getKey().equals(player))
				.map(Map.Entry::getValue).findFirst();
		if (name.isEmpty() || !town.membership().removeResident(player)) {
			return false;
		}
		Towns.get(server).changed();
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) {
			online.sendSystemMessage(Component.translatable("message.minecities.resident.expelled", town.name())
					.withStyle(ChatFormatting.RED));
		}
		notify(server, town, Component.translatable("message.minecities.resident.removed", name.get()));
		return true;
	}

	/** Dormir dans le lit d'une maison déclarée d'une ville dont on n'est pas : c'est demander à y vivre. */
	private static void sleptIn(ServerPlayer player, BlockPos bed) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		Towns towns = Towns.get(level.getServer());
		towns.at(level.dimension(), bed)
				.filter(t -> t.houses().stream().anyMatch(h -> h.beds().contains(bed) || h.encloses(bed)))
				.filter(t -> !t.isMember(player.getUUID()) && !t.membership().hasRequested(player.getUUID()))
				.filter(t -> towns.memberOf(player.getUUID()).isEmpty())
				.ifPresent(t -> requestJoin(level.getServer(), t, player));
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

	/** La chair putréfiée et les aliments empoisonnés : affamé, on en mange, et l'on tombe malade (MC-12). */
	private static final Set<net.minecraft.world.item.Item> TAINTED = Set.of(Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SPIDER_EYE);

	public static boolean isTainted(ItemStack stack) {
		return TAINTED.contains(stack.getItem());
	}

	public static boolean isFood(ItemStack stack) {
		return stack.has(DataComponents.FOOD) && !UNHEALTHY.contains(stack.getItem());
	}

	public static boolean hasFood(Container container) {
		return hasFood(container, false);
	}

	/** Y a-t-il à manger ? Affamé ({@code desperate}), même du gâté. */
	public static boolean hasFood(Container container, boolean desperate) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (isFood(stack) || desperate && isTainted(stack)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Faute de mieux, un affamé prend de la chair putréfiée ou un aliment empoisonné. Vide s'il y a
	 * autre chose à manger, ou rien.
	 */
	public static ItemStack takeTainted(Container container) {
		if (hasFood(container)) {
			return ItemStack.EMPTY;
		}
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (isTainted(container.getItem(i))) {
				ItemStack taken = container.removeItem(i, 1);
				container.setChanged();
				return taken;
			}
		}
		return ItemStack.EMPTY;
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
		return takeFood(container, new com.ajustor.minecities.citizen.Diet());
	}

	/**
	 * Prend l'aliment qui le nourrira le mieux compte tenu de ses derniers repas : il varie de lui-même
	 * quand le garde-manger le permet, et préfère un plat élaboré. Vide si rien ne se mange.
	 */
	public static ItemStack takeFood(Container container, com.ajustor.minecities.citizen.Diet diet) {
		int best = -1;
		int bestNutrition = -1;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food == null || !isFood(stack)) {
				continue;
			}
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			// Ce qu'il n'a pas mangé dernièrement le tente plus qu'un peu plus nourrissant.
			int value = diet.nutrition(id, food.nutrition()) + (diet.times(id) == 0 ? 4 : 0)
					+ (com.ajustor.minecities.citizen.Diet.DISHES.contains(id) ? 3 : 0);
			if (value > bestNutrition) {
				best = i;
				bestNutrition = value;
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
		return pantries(level, town, resident, false);
	}

	/** Les garde-manger ; affamé ({@code desperate}), aussi ceux où il ne reste que du gâté. */
	public static List<BlockPos> pantries(ServerLevel level, Town town, Resident resident, boolean desperate) {
		List<BlockPos> pantries = new ArrayList<>();
		resident.home().flatMap(town::house).ifPresent(h -> pantries.addAll(h.containers()));
		// Le grenier de l'annexe du quartier, s'il est plus près de chez lui que l'hôtel de ville.
		BlockPos from = resident.home().orElse(town.hall());
		town.activeAnnexes().stream().filter(a -> a.distSqr(from) < town.hall().distSqr(from)).forEach(pantries::add);
		pantries.add(town.hall());
		return pantries.stream().filter(p -> level.isLoaded(p) && level.getBlockEntity(p) instanceof Container c
				&& hasFood(c, desperate)).toList();
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
		granary(level, town).ifPresent(g -> player.sendSystemMessage(Component.translatable("info.minecities.treasury",
				com.ajustor.minecities.town.Stock.count(g, s -> s.is(Items.EMERALD)),
				town.chronicle().earnedToday(level.getGameTime()))));
		town.averageMood().ifPresent(mood -> player.sendSystemMessage(Component.translatable("info.minecities.mood",
				String.format("%.1f", mood)).withStyle(mood >= com.ajustor.minecities.citizen.Happiness.HAPPY
				? ChatFormatting.GREEN : mood > com.ajustor.minecities.citizen.Happiness.UNHAPPY ? ChatFormatting.WHITE
				: ChatFormatting.RED)));
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
