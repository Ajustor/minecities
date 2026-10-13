package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Hunger;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Une ville de bout en bout, dans le vrai jeu : on fonde la ville, on bâtit une maison de deux lits
 * avec sa plaque, on garnit le grenier ; deux citoyens arrivent, mangent quand ils ont faim et se
 * couchent la nuit. Chaque étape est vérifiée, et photographiée dans
 * {@code build/run/clientGameTest/screenshots}.
 *
 * <p>{@code ./gradlew runClientGameTest} ; {@code -Dminecities.gametest=city} pour ne jouer que
 * cette scène.
 */
public class CityScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	/** L'intérieur de la maison : [4, 8] x [Y, Y + 2] x [4, 8], la porte au nord. */
	private static final BlockPos PLAQUE = new BlockPos(5, Y + 1, 4);
	private static final BlockPos CHEST = new BlockPos(8, Y, 5);

	@Override
	public String name() {
		return "city";
	}

	@Override
	public void play(Stage stage) {
		// Cette scène compte ses citoyens : pas de nouveaux venus pour atteindre le minimum.
		com.ajustor.minecities.town.Families.setMinimum(0);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.waitTicks(20);
		stage.run(CityScenes::build);
		stage.client(mc -> {
			hideGui(mc, true);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});

		// Fondation, maison, grenier.
		String founded = stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			TownService.declareHouse(level, PLAQUE, player);
			Town town = town(server);
			if (town == null) {
				return "aucune ville fondée";
			}
			if (town.houses().size() != 1 || town.capacity() != 2) {
				return "maison mal relevée : " + town.houses();
			}
			return null;
		});
		check(founded);

		// Une maison ouverte n'en est plus une ; refermée, elle redevient valide.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			level.setBlockAndUpdate(new BlockPos(3, Y + 1, 7), Blocks.AIR.defaultBlockState());
			TownService.declareHouse(level, PLAQUE, player);
			boolean open = town(server).capacity() == 0;
			level.setBlockAndUpdate(new BlockPos(3, Y + 1, 7), Blocks.GLASS.defaultBlockState());
			TownService.declareHouse(level, PLAQUE, player);
			return open && town(server).capacity() == 2 ? null : "le trou dans le mur n'a pas été vu";
		}));

		// Deux citoyens arrivent et prennent chacun un lit.
		check(stage.compute(server -> {
			Town town = town(server);
			TownService.immigrate(server.overworld(), town);
			TownService.immigrate(server.overworld(), town);
			if (town.population() != 2 || town.freeBeds() != 0) {
				return "population " + town.population() + ", lits libres " + town.freeBeds();
			}
			return null;
		}));
		stage.look(-7, Y + 9, -9, 4, Y, 4);
		stage.waitTicks(120);
		stage.shot("city_0_overview");
		stage.look(-1.2, Y + 1.7, -2.6, 0.5, Y + 0.5, 0.5);
		stage.waitTicks(10);
		stage.shot("city_0_town_hall");
		stage.look(2, Y + 2.2, -3, 1, Y + 1, 2);
		stage.run(server -> citizens(server).forEach(c -> c.snapTo(1 + c.getId() % 2, Y,
				1, 180, 0)));
		stage.waitTicks(10);
		stage.shot("city_1_citizens");

		// Affamés, ils vont manger : au coffre de la maison d'abord.
		int before = stage.compute(server -> meals(server, CHEST) + meals(server, HALL));
		stage.run(server -> citizens(server).forEach(c -> c.setHunger(Hunger.HUNGRY - 4)));
		waitOrExplain(stage, server -> citizens(server).stream().allMatch(c -> c.hunger() >= Hunger.HUNGRY), 1200,
				"les citoyens n'ont pas mangé");
		int after = stage.compute(server -> meals(server, CHEST) + meals(server, HALL));
		if (after >= before) {
			throw new AssertionError("les citoyens ont mangé sans entamer les réserves : " + before + " -> " + after);
		}
		stage.look(6, Y + 1.8, 7.5, 7.5, Y + 0.8, 5);
		stage.waitTicks(10);
		stage.shot("city_2_after_meal");

		// La nuit, chacun dans son lit.
		stage.command("time set 13000");
		waitOrExplain(stage, server -> citizens(server).stream().allMatch(CitizenEntity::isSleeping), 1200,
				"les citoyens ne sont pas couchés");
		stage.look(6, Y + 2.4, 4.6, 6, Y + 0.3, 7.5);
		stage.waitTicks(20);
		stage.shot("city_3_night");
		stage.command("time set 1000");
		stage.waitFor(server -> citizens(server).stream().noneMatch(CitizenEntity::isSleeping), 400);

		// Des villageois vanilla sur le territoire comptent dans la population (sans devenir citoyens).
		for (int i = 0; i < 3; i++) {
			stage.command("summon minecraft:villager %d %d -6".formatted(-4 + i * 3, Y));
		}
		// (Sans métier, ils peuvent aussi devenir citoyens : la population reste la même.)
		waitOrExplain(stage, server -> town(server).population() == 5, 400, "les villageois n'ont pas été recensés",
				server -> "population " + town(server).population() + ", villageois " + town(server).villagers() + ".");
	}

	/** Attend la condition ; sinon échoue en décrivant chaque citoyen (faim, position, sommeil). */
	static void waitOrExplain(Stage stage, java.util.function.Predicate<MinecraftServer> done,
			int ticks, String what) {
		waitOrExplain(stage, done, ticks, what, server -> "");
	}

	/** Comme l'autre, avec un détail de plus calculé au moment de l'échec. */
	static void waitOrExplain(Stage stage, java.util.function.Predicate<MinecraftServer> done,
			int ticks, String what, java.util.function.Function<MinecraftServer, String> detail) {
		try {
			stage.waitFor(done, ticks);
		} catch (AssertionError e) {
			String state = stage.compute(server -> detail.apply(server) + " " + census(server));
			throw new AssertionError(what + " : " + state, e);
		}
	}

	static String describe(CitizenEntity c) {
		// Ce qu'il fait (ses buts en cours) et où il va : de quoi comprendre un citoyen immobile.
		String goals = c.runningGoals().toString();
		net.minecraft.core.BlockPos target = c.getNavigation().getTargetPos();
		net.minecraft.world.level.pathfinder.Path path = c.getNavigation().getPath();
		String route = path == null ? "aucun" : "%d/%d%s%s".formatted(path.getNextNodeIndex(), path.getNodeCount(),
				path.isDone() ? " fini" : " prochain " + path.getNextNodePos().toShortString(),
				path.canReach() ? "" : " inaccessible");
		return "%s faim=%.1f pv=%.0f pos=%s (%.2f, %.2f, %.2f) pose=%s buts=%s vers=%s chemin=%s métier=%s main=%s armure=%s poches=%s".formatted(
				c.getName().getString(), c.hunger(), c.getHealth(), c.blockPosition().toShortString(), c.getX(), c.getY(),
				c.getZ(), c.getPose(), goals, c.getNavigation().isDone() || target == null ? "-" : target.toShortString(),
				route,
				c.job().map(j -> j.job().serializedName()).orElse("-"), c.getMainHandItem(),
				java.util.stream.Stream.of(net.minecraft.world.entity.EquipmentSlot.HEAD,
								net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
								net.minecraft.world.entity.EquipmentSlot.FEET)
						.map(c::getItemBySlot).filter(st -> !st.isEmpty()).toList(),
				c.inventory().stream().filter(st -> !st.isEmpty()).toList());
	}

	static String census(MinecraftServer server) {
		return citizens(server).stream().map(CityScenes::describe).reduce((x, y) -> x + " ; " + y).orElse("aucun citoyen");
	}

	static void check(String failure) {
		if (failure != null) {
			throw new AssertionError(failure);
		}
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElse(null);
	}

	static List<CitizenEntity> citizens(MinecraftServer server) {
		List<CitizenEntity> all = new ArrayList<>();
		server.overworld().getAllEntities().forEach(e -> {
			if (e instanceof CitizenEntity c) {
				all.add(c);
			}
		});
		return all;
	}

	private static int meals(MinecraftServer server, BlockPos pos) {
		return server.overworld().getBlockEntity(pos) instanceof Container c ? TownService.countMeals(c) : 0;
	}

	/** Une esplanade, l'hôtel de ville au centre, et une maison de planches de deux lits au sud-est. */
	static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -16; x <= 16; x++) {
			for (int z = -16; z <= 16; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), (x + z) % 2 == 0
						? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState());
				for (int y = Y; y < Y + 8; y++) {
					level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		// Murs, sol et toit autour de l'intérieur [4, 8] x [Y, Y + 2] x [4, 8].
		for (int x = 3; x <= 9; x++) {
			for (int z = 3; z <= 9; z++) {
				for (int y = Y - 1; y <= Y + 3; y++) {
					boolean inside = x >= 4 && x <= 8 && z >= 4 && z <= 8 && y >= Y && y <= Y + 2;
					if (!inside) {
						boolean corner = (x == 3 || x == 9) && (z == 3 || z == 9);
						level.setBlockAndUpdate(new BlockPos(x, y, z), y == Y + 3 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
								: corner ? Blocks.STRIPPED_OAK_LOG.defaultBlockState()
								: y == Y - 1 ? Blocks.OAK_PLANKS.defaultBlockState()
								: Blocks.OAK_PLANKS.defaultBlockState());
					}
				}
			}
		}
		level.setBlockAndUpdate(new BlockPos(3, Y + 1, 7), Blocks.GLASS.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(9, Y + 1, 7), Blocks.GLASS.defaultBlockState());
		command(server, "setblock 6 %d 3 minecraft:oak_door[facing=south,half=lower]".formatted(Y));
		command(server, "setblock 6 %d 3 minecraft:oak_door[facing=south,half=upper]".formatted(Y + 1));
		for (int x : new int[]{4, 8}) {
			command(server, "setblock %d %d 7 minecraft:red_bed[facing=south,part=foot]".formatted(x, Y));
			command(server, "setblock %d %d 8 minecraft:red_bed[facing=south,part=head]".formatted(x, Y));
		}
		command(server, "setblock 8 %d 5 minecraft:chest[facing=west]".formatted(Y));
		command(server, "setblock 6 %d 8 minecraft:torch".formatted(Y));
		command(server, "setblock 5 %d 4 minecities:house_plaque[facing=south]".formatted(Y + 1));
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(CHEST) instanceof Container chest) {
			chest.setItem(0, new ItemStack(Items.BREAD, 3));
		}
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			hall.setItem(0, new ItemStack(Items.BAKED_POTATO, 16));
			hall.setItem(1, new ItemStack(Items.COOKED_BEEF, 8));
		}
		command(server, "gamemode spectator @p");
	}

	static void command(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}

	static void hideGui(Minecraft mc, boolean hidden) {
		if (mc.gui.hud.isHidden() != hidden) {
			mc.gui.hud.toggle();
		}
		mc.gui.hud.getChat().clearMessages(false);
	}
}
