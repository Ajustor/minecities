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
		Optional<Town> already = towns.ofMayor(player.getUUID());
		Optional<Town> neighbour = towns.tooClose(level.dimension(), pos);
		Component refusal = null;
		if (already.isPresent()) {
			refusal = Component.translatable("message.minecities.found.already_mayor", already.get().name());
		} else if (neighbour.isPresent()) {
			refusal = Component.translatable("message.minecities.found.too_close", neighbour.get().name());
		}
		if (refusal != null) {
			player.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.RED));
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
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
				"message.minecities.found", player.getDisplayName(), name).withStyle(ChatFormatting.GOLD), false);
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
			player.sendSystemMessage(Component.translatable("message.minecities.house.valid", town.name(),
					result.beds().size(), result.containers().size(), result.volume()).withStyle(ChatFormatting.GREEN));
		} else {
			player.sendSystemMessage(Component.translatable("message.minecities.house.invalid",
					Component.translatable(result.status().translationKey())).withStyle(ChatFormatting.RED));
		}
		settle(level, town);
		towns.changed();
	}

	/** Relève la maison et met la ville à jour : enregistrée si elle est valide, oubliée sinon. */
	static HouseScanner.Result survey(ServerLevel level, Town town, BlockPos plaque) {
		HouseScanner.Result raw = HouseScanner.scan((x, y, z) -> classify(level, new BlockPos(x, y, z)),
				new HouseScanner.Pos(plaque.getX(), plaque.getY(), plaque.getZ()));
		List<BlockPos> beds = raw.beds().stream().map(p -> new BlockPos(p.x(), p.y(), p.z()))
				.filter(b -> !town.bedClaimedElsewhere(b, plaque)).toList();
		HouseScanner.Result result = raw;
		boolean declared = raw.plaques().stream()
				.anyMatch(p -> town.house(new BlockPos(p.x(), p.y(), p.z())).isPresent());
		if (raw.valid() && declared) {
			result = new HouseScanner.Result(HouseScanner.Status.ALREADY_DECLARED, raw.volume(), List.of(),
					raw.containers(), raw.doors(), raw.plaques());
		} else if (raw.valid() && beds.isEmpty()) {
			result = new HouseScanner.Result(HouseScanner.Status.NO_BED, raw.volume(), List.of(), raw.containers(),
					raw.doors(), raw.plaques());
		}
		if (result.valid()) {
			List<BlockPos> containers = raw.containers().stream().map(p -> new BlockPos(p.x(), p.y(), p.z())).toList();
			town.putHouse(new House(plaque, beds, containers, raw.volume()));
		} else {
			town.removeHouse(plaque);
		}
		return result;
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

	/** Loge les sans-abri là où il reste des lits, et recalcule le palier. */
	private static void settle(ServerLevel level, Town town) {
		for (UUID citizen : town.residents().keySet()) {
			town.house(citizen);
		}
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
					if (level.getBlockState(house.plaque()).is(McBlocks.HOUSE_PLAQUE)) {
						survey(level, town, house.plaque());
					} else {
						town.removeHouse(house.plaque());
					}
				}
			}
			settle(level, town);
			if (now - town.lastArrival() >= IMMIGRATION_DELAY && town.freeBeds() > 0
					&& granary(level, town).map(TownService::hasFood).orElse(false)) {
				immigrate(level, town);
			}
			towns.changed();
		}
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
		player.sendSystemMessage(Component.translatable("info.minecities.population", town.population(),
				town.capacity(), town.houses().size()));
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
