package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Happiness;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.TownTier;
import com.ajustor.minecities.town.Towns;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Les artisans (v1.0), dans le vrai jeu : joaillier, maçon, verrier, tisserand, apiculteur et
 * bibliothécaire travaillent ce que le grenier leur offre ; le marchand vend les bijoux ; la
 * bibliothèque rend la ville plus heureuse.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=crafts}
 */
public class CraftScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final List<String> WORKSHOPS = List.of("jeweler_workshop_1", "masonry_1", "glassworks_1",
			"weavery_1", "apiary_1", "library_1", "market_1");
	private static final BlockPos HOUSE = new BlockPos(-6, Y, 12);
	/** Une ruche pleine, à portée de l'apiculteur (son rucher est au nord-ouest). */
	private static final BlockPos HIVE = new BlockPos(-20, Y, -2);

	@Override
	public String name() {
		return "crafts";
	}

	@Override
	public void play(Stage stage) {
		Families.setMinimum(0);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.waitTicks(20);
		stage.run(CraftScenes::build);
		stage.client(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			town.forceTier(Optional.of(TownTier.TOWN));
			TownService.retier(level, town);
			for (int i = 0; i < WORKSHOPS.size(); i++) {
				TownService.immigrate(level, town);
			}
			for (int i = 0; i < WORKSHOPS.size(); i++) {
				RaidScenes.plaque(level, origin(i)).ifPresent(p -> TownService.declareHouse(level, p, player));
			}
			RaidScenes.plaque(level, HOUSE).ifPresent(p -> TownService.declareHouse(level, p, player));
			for (Job job : List.of(Job.JEWELER, Job.MASON, Job.GLASSBLOWER, Job.WEAVER, Job.BEEKEEPER,
					Job.LIBRARIAN, Job.MERCHANT)) {
				if (town.workers(job) != 1) {
					return "pas de " + job.serializedName() + " : " + town.residents() + " ; " + town.houses();
				}
			}
			return null;
		}));
		// L'éclat d'améthyste de la broche est dans un coffre de l'atelier du joaillier, le cuivre au grenier :
		// les ingrédients d'un même ouvrage dans deux contenants.
		check(stage.compute(server -> {
			Town town = town(server);
			Optional<BlockPos> chest = town.residents().values().stream().flatMap(r -> r.job().stream())
					.filter(j -> j.job() == Job.JEWELER).findFirst()
					.flatMap(j -> town.house(j.workshop())).flatMap(h -> h.containers().stream().findFirst());
			if (chest.isEmpty() || !(server.overworld().getBlockEntity(chest.get()) instanceof Container c)) {
				return "pas de coffre dans l'atelier du joaillier";
			}
			Stock.insert(c, new ItemStack(Items.AMETHYST_SHARD));
			return null;
		}));
		// Le bibliothécaire prête un livre du grenier à l'atelier de niveau 4 (MC-63) : celui du joaillier,
		// tenu pour une demeure bâtie par la ville. Le livre est consommé.
		BlockPos jewelry = stage.compute(server -> {
			Town town = town(server);
			com.ajustor.minecities.build.Construction built = com.ajustor.minecities.build.Construction.order(
					com.ajustor.minecities.build.Blueprints.get(server, net.minecraft.resources.Identifier
							.fromNamespaceAndPath("minecities", "jeweler_workshop_4")).orElseThrow(),
					com.ajustor.minecities.build.BuildStyle.PLAINS, origin(0), net.minecraft.world.level.block.Rotation.NONE);
			built.finish();
			town.order(built);
			if (server.overworld().getBlockEntity(HALL) instanceof Container hall) {
				Stock.insert(hall, new ItemStack(Items.BOOK));
			}
			return town.residents().values().stream().flatMap(r -> r.job().stream()).filter(j -> j.job() == Job.JEWELER)
					.findFirst().orElseThrow().workshop();
		});
		waitOrExplain(stage, server -> com.ajustor.minecities.town.Mastery.active(town(server), jewelry)
				&& server.overworld().getBlockEntity(HALL) instanceof Container hall
				&& Stock.count(hall, st -> st.is(Items.BOOK)) == 0, 400, "le bibliothécaire n'a pas prêté de livre",
				server -> "niveau " + town(server).buildingLevel(jewelry) + ", état "
						+ com.ajustor.minecities.town.Mastery.state(server.overworld(), town(server), jewelry) + " ;");
		stage.look(6, Y + 16, 2, 6, Y, -16);
		stage.client(mc -> hideGui(mc, true));
		stage.waitTicks(20);
		stage.shot("crafts_0_workshops");
		stage.film("crafts", 40, 20, server -> false);

		// Chacun son ouvrage.
		waitOrExplain(stage, server -> granary(server, Items.BRICKS) >= 2 && granary(server, Items.GLASS) >= 1
						&& granary(server, Items.BED.white()) >= 1 && granary(server, Items.BOOK) >= 1
						&& granary(server, Items.HONEY_BOTTLE) >= 1, 3600, "les artisans n'ont pas tout fait",
				server -> "briques " + granary(server, Items.BRICKS) + ", verre " + granary(server, Items.GLASS)
						+ ", lits " + granary(server, Items.BED.white()) + ", livres " + granary(server, Items.BOOK)
						+ ", miel " + granary(server, Items.HONEY_BOTTLE) + ".");
		// Les bijoux se vendent : le diadème, la bague et la broche rapportent 19 émeraudes.
		waitOrExplain(stage, server -> town(server).chronicle().earnedToday(server.overworld().getGameTime()) >= 19,
				7200, "les bijoux ne se sont pas vendus", server -> "diadème " + granary(server, McItems.DIADEM)
						+ ", bague " + granary(server, McItems.GOLD_RING) + ", broche "
						+ granary(server, McItems.AMETHYST_BROOCH) + ", gains "
						+ town(server).chronicle().earnedToday(server.overworld().getGameTime()) + ".");
		// La bibliothèque réjouit la ville.
		waitOrExplain(stage, server -> citizens(server).stream().allMatch(c -> c.moodDetail().factors()
				.contains(new Happiness.Factor("culture", 1))), 400, "la bibliothèque ne réjouit personne");

		// Leurs couvre-chefs.
		stage.run(server -> {
			List<CitizenEntity> all = citizens(server).stream()
					.sorted(java.util.Comparator.comparing(c -> c.job().map(j -> j.job().ordinal()).orElse(99)))
					.toList();
			for (int i = 0; i < all.size(); i++) {
				CitizenEntity c = all.get(i);
				c.setNoAi(true);
				c.snapTo(-4.8 + i * 1.6, Y, 34.5, 180, 0);
				c.setYHeadRot(180);
				c.setYBodyRot(180);
			}
		});
		stage.look(0, Y + 2.3, 28, 0, Y + 1.5, 34.5);
		stage.waitTicks(10);
		stage.shot("crafts_1_hats");

		// La ville disparaît en pleine journée, ses artisans au travail : ils s'en vont, sans planter le
		// serveur (un artisan sans ville recevait encore un tick de travail).
		stage.run(server -> {
			citizens(server).forEach(c -> c.setNoAi(false));
			Towns.get(server).remove(town(server));
		});
		waitOrExplain(stage, server -> citizens(server).isEmpty(), 400, "les artisans ne sont pas partis");
	}

	private static BlockPos origin(int i) {
		return new BlockPos(-30 + 12 * (i % 4), Y, i < 4 ? -30 : -16);
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static int granary(MinecraftServer server, Item item) {
		return server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall
				? Stock.count(hall, s -> s.is(item)) : 0;
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -36; x <= 24; x++) {
			for (int z = -34; z <= 40; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 12; y++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		for (int i = 0; i < WORKSHOPS.size(); i++) {
			RaidScenes.paste(level, WORKSHOPS.get(i), origin(i));
		}
		RaidScenes.paste(level, "house_4", HOUSE);
		command(server, "setblock %d %d %d minecraft:beehive[facing=south,honey_level=5]".formatted(HIVE.getX(),
				HIVE.getY(), HIVE.getZ()));
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			Item[] items = {Items.COOKED_BEEF, Items.GOLD_INGOT, Items.DIAMOND, Items.LAPIS_LAZULI, Items.COPPER_INGOT,
					Items.AMETHYST_SHARD, Items.CLAY_BALL, Items.SAND, Items.WOOL.white(), Items.OAK_PLANKS, Items.PAPER,
					Items.LEATHER, Items.GLASS_BOTTLE, Items.SHEARS, Items.BAKED_POTATO};
			int[] counts = {32, 5, 1, 2, 2, 0, 8, 4, 3, 3, 3, 1, 4, 1, 16};
			for (int i = 0; i < items.length; i++) {
				hall.setItem(i, new ItemStack(items[i], counts[i]));
			}
		}
		command(server, "gamemode spectator @p");
	}
}
