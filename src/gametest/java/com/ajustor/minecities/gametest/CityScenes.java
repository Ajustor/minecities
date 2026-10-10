package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Hunger;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
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
import java.util.Locale;

/**
 * Une ville de bout en bout, dans le vrai jeu : on fonde la ville, on bâtit une maison de deux lits
 * avec sa plaque, on garnit le grenier ; deux citoyens arrivent, mangent quand ils ont faim et se
 * couchent la nuit. Chaque étape est vérifiée, et photographiée dans
 * {@code build/run/clientGameTest/screenshots}.
 *
 * <p>{@code ./gradlew runClientGameTest} ; {@code -Dminecities.gametest=city} pour ne jouer que
 * cette scène.
 */
public class CityScenes implements FabricClientGameTest {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	/** L'intérieur de la maison : [4, 8] x [Y, Y + 2] x [4, 8], la porte au nord. */
	private static final BlockPos PLAQUE = new BlockPos(5, Y + 1, 4);
	private static final BlockPos CHEST = new BlockPos(8, Y, 5);

	static boolean selected(String name) {
		String only = System.getProperty("minecities.gametest");
		return only == null || only.isBlank() || List.of(only.split(",")).contains(name);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!selected("city")) {
			return;
		}
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			command(sp, "time set 1000");
			command(sp, "gamerule doDaylightCycle false");
			command(sp, "gamerule doMobSpawning false");
			command(sp, "weather clear");
			context.waitTicks(20);
			sp.getServer().runOnServer(CityScenes::build);
			context.runOnClient(mc -> {
				hideGui(mc, true);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});

			// Fondation, maison, grenier.
			String founded = sp.getServer().computeOnServer(server -> {
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
			check(sp.getServer().computeOnServer(server -> {
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
			check(sp.getServer().computeOnServer(server -> {
				Town town = town(server);
				TownService.immigrate(server.overworld(), town);
				TownService.immigrate(server.overworld(), town);
				if (town.population() != 2 || town.freeBeds() != 0) {
					return "population " + town.population() + ", lits libres " + town.freeBeds();
				}
				return null;
			}));
			look(sp, -7, Y + 9, -9, 4, Y, 4);
			context.waitTicks(120);
			context.takeScreenshot("city_0_overview");
			look(sp, -1.2, Y + 1.7, -2.6, 0.5, Y + 0.5, 0.5);
			context.waitTicks(10);
			context.takeScreenshot("city_0_town_hall");
			look(sp, 2, Y + 2.2, -3, 1, Y + 1, 2);
			sp.getServer().runOnServer(server -> citizens(server).forEach(c -> c.snapTo(1 + c.getId() % 2, Y,
					1, 180, 0)));
			context.waitTicks(10);
			context.takeScreenshot("city_1_citizens");

			// Affamés, ils vont manger : au coffre de la maison d'abord.
			int before = sp.getServer().computeOnServer(server -> meals(server, CHEST) + meals(server, HALL));
			sp.getServer().runOnServer(server -> citizens(server).forEach(c -> c.setHunger(Hunger.HUNGRY - 4)));
			waitOrExplain(sp, server -> citizens(server).stream().allMatch(c -> c.hunger() >= Hunger.HUNGRY), 1200,
					"les citoyens n'ont pas mangé");
			int after = sp.getServer().computeOnServer(server -> meals(server, CHEST) + meals(server, HALL));
			if (after >= before) {
				throw new AssertionError("les citoyens ont mangé sans entamer les réserves : " + before + " -> " + after);
			}
			look(sp, 6, Y + 1.8, 7.5, 7.5, Y + 0.8, 5);
			context.waitTicks(10);
			context.takeScreenshot("city_2_after_meal");

			// La nuit, chacun dans son lit.
			command(sp, "time set 13000");
			waitOrExplain(sp, server -> citizens(server).stream().allMatch(CitizenEntity::isSleeping), 1200,
					"les citoyens ne sont pas couchés");
			look(sp, 6, Y + 2.4, 4.6, 6, Y + 0.3, 7.5);
			context.waitTicks(20);
			context.takeScreenshot("city_3_night");
			command(sp, "time set 1000");
			sp.getServer().waitFor(server -> citizens(server).stream().noneMatch(CitizenEntity::isSleeping), 400);

			// Des villageois vanilla sur le territoire comptent dans la population (sans devenir citoyens).
			for (int i = 0; i < 3; i++) {
				command(sp, "summon minecraft:villager %d %d -6".formatted(-4 + i * 3, Y));
			}
			waitOrExplain(sp, server -> town(server).villagers() == 3, 400, "les villageois n'ont pas été recensés");
			check(sp.getServer().computeOnServer(server -> town(server).population() == 5
					&& town(server).citizens() == 2 ? null : "population mal comptée : " + town(server).population()));
		}
	}

	/** Attend la condition ; sinon échoue en décrivant chaque citoyen (faim, position, sommeil). */
	static void waitOrExplain(TestSingleplayerContext sp, java.util.function.Predicate<MinecraftServer> done,
			int ticks, String what) {
		waitOrExplain(sp, done, ticks, what, server -> "");
	}

	/** Comme l'autre, avec un détail de plus calculé au moment de l'échec. */
	static void waitOrExplain(TestSingleplayerContext sp, java.util.function.Predicate<MinecraftServer> done,
			int ticks, String what, java.util.function.Function<MinecraftServer, String> detail) {
		try {
			sp.getServer().waitFor(done, ticks);
		} catch (AssertionError e) {
			String state = sp.getServer().computeOnServer(server -> detail.apply(server) + " " + census(server));
			throw new AssertionError(what + " : " + state, e);
		}
	}

	static String describe(CitizenEntity c) {
		return "%s faim=%.1f pv=%.0f pos=%s pose=%s métier=%s main=%s poches=%s".formatted(c.getName().getString(),
				c.hunger(), c.getHealth(), c.blockPosition().toShortString(), c.getPose(),
				c.job().map(j -> j.job().serializedName()).orElse("-"), c.getMainHandItem(),
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
	private static void build(MinecraftServer server) {
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

	static void command(TestSingleplayerContext sp, String command) {
		sp.getServer().runCommand(command);
	}

	/** Se poste à (x, y, z), les yeux vers (tx, ty, tz). */
	static void look(TestSingleplayerContext sp, double x, double y, double z, double tx, double ty, double tz) {
		double dx = tx - x, dy = ty - y, dz = tz - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", x, y - 1.62, z, yaw, pitch));
	}

	static void hideGui(Minecraft mc, boolean hidden) {
		if (mc.gui.hud.isHidden() != hidden) {
			mc.gui.hud.toggle();
		}
		mc.gui.hud.getChat().clearMessages(false);
	}
}
