package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.HousePlaqueBlock;
import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.Action;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Happiness;
import com.ajustor.minecities.client.screen.TownHallScreen;
import com.ajustor.minecities.network.TownNetwork;
import com.ajustor.minecities.network.TownSnapshot;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Alarms;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Festivals;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * La vie de la cité (v0.6), dans le vrai jeu : une grande maison et une infirmerie posées d'après
 * leurs schémas (plaques vertes), une plaque refusée (rouge) ; un repas toujours pareil rend moins
 * heureux qu'un repas varié, et un affamé choisit de varier au grenier ; le médecin soigne un blessé
 * et nourrit un affamé ; l'écran de l'hôtel de ville (bonheur, bâtiments) ; une fête où l'on danse ;
 * la cloche qui donne l'alerte ; le titre d'entrée sur le territoire et son contour en particules.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=life}
 */
public class LifeScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos HOUSE = new BlockPos(-6, Y, -16);
	private static final BlockPos INFIRMARY = new BlockPos(8, Y, -16);
	private static final BlockPos BELL = new BlockPos(3, Y, -3);
	/** Un pan de mur seul, avec une plaque : la ville la refuse (rien n'est clos). */
	private static final BlockPos LONE_WALL = new BlockPos(-4, Y, 4);

	@Override
	public String name() {
		return "life";
	}

	@Override
	public void play(Stage stage) {
		Families.setMinimum(0);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.waitTicks(20);
		stage.run(LifeScenes::build);
		stage.client(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

		// La ville : cinq citoyens, une maison de quatre lits et une infirmerie.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			for (int i = 0; i < 5; i++) {
				TownService.immigrate(level, town);
			}
			for (BlockPos origin : new BlockPos[]{HOUSE, INFIRMARY}) {
				RaidScenes.plaque(level, origin).ifPresent(p -> TownService.declareHouse(level, p, player));
			}
			command(server, "setblock %d %d %d minecities:house_plaque[facing=south]".formatted(LONE_WALL.getX(),
					LONE_WALL.getY() + 1, LONE_WALL.getZ() + 1));
			TownService.declareHouse(level, LONE_WALL.offset(0, 1, 1), player);
			return town.workers(Job.DOCTOR) == 1 ? null : "pas de médecin : " + town.houses();
		}));
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Optional<BlockPos> plaque = RaidScenes.plaque(level, HOUSE);
			HousePlaqueBlock.Status house = plaque.map(p -> level.getBlockState(p).getValue(HousePlaqueBlock.STATUS))
					.orElse(null);
			HousePlaqueBlock.Status lone = level.getBlockState(LONE_WALL.offset(0, 1, 1))
					.getValue(HousePlaqueBlock.STATUS);
			return house == HousePlaqueBlock.Status.VALID && lone == HousePlaqueBlock.Status.INVALID ? null
					: "plaques : maison " + house + ", mur seul " + lone;
		}));
		stage.look(-4, Y + 2, 7.5, -4, Y + 1.3, 5);
		stage.client(mc -> hideGui(mc, true));
		stage.waitTicks(20);
		stage.shot("life_0_red_plaque");

		// Le même pain cinq fois lasse ; des repas variés, avec un bon plat, rendent heureux.
		UUID[] eaters = stage.compute(server -> {
			// Deux citoyens logés : la maison n'a que quatre lits, et le sans-logis serait moins heureux
			// quoi qu'il mange.
			Town town = town(server);
			List<CitizenEntity> civilians = civilians(server).stream()
					.filter(c -> town.resident(c.getUUID()).filter(Resident::hasHome).isPresent()).toList();
			CitizenEntity bored = civilians.get(0);
			CitizenEntity gourmet = civilians.get(1);
			for (int i = 0; i < 5; i++) {
				bored.eat(new ItemStack(Items.BREAD));
			}
			for (var food : List.of(Items.BREAD, Items.COOKED_BEEF, Items.BAKED_POTATO, Items.CARROT,
					Items.MUSHROOM_STEW)) {
				gourmet.eat(new ItemStack(food));
			}
			return new UUID[]{bored.getUUID(), gourmet.getUUID()};
		});
		stage.waitTicks(220);
		check(stage.compute(server -> {
			CitizenEntity bored = (CitizenEntity) server.overworld().getEntity(eaters[0]);
			CitizenEntity gourmet = (CitizenEntity) server.overworld().getEntity(eaters[1]);
			boolean monotonous = bored.moodDetail().factors().contains(new Happiness.Factor("monotonous", -1));
			boolean varied = gourmet.moodDetail().factors().contains(new Happiness.Factor("varied", 2));
			return monotonous && varied && gourmet.mood() > bored.mood() ? null
					: "humeurs : lassé " + bored.mood() + " " + bored.moodDetail().factors() + ", gourmet "
							+ gourmet.mood() + " " + gourmet.moodDetail().factors();
		}));

		// Un affamé, devant un grenier varié, ne mange pas trois fois la même chose.
		UUID hungry = stage.compute(server -> {
			CitizenEntity c = civilians(server).get(2);
			c.setHunger(3);
			return c.getUUID();
		});
		waitOrExplain(stage, server -> ((CitizenEntity) server.overworld().getEntity(hungry)).hunger() >= 18, 1200,
				"l'affamé n'a pas mangé");
		check(stage.compute(server -> {
			CitizenEntity c = (CitizenEntity) server.overworld().getEntity(hungry);
			return c.diet().variety() >= 2 ? null : "il a mangé toujours la même chose : " + c.diet().meals();
		}));

		// Le médecin soigne un blessé, et nourrit un affamé.
		UUID[] patients = stage.compute(server -> {
			List<CitizenEntity> civilians = civilians(server);
			CitizenEntity hurt = civilians.get(0);
			CitizenEntity starving = civilians.get(3);
			hurt.setHealth(4);
			starving.setHunger(0);
			return new UUID[]{hurt.getUUID(), starving.getUUID()};
		});
		stage.look(3, Y + 13, 2, 1, Y, -12);
		stage.waitTicks(20);
		stage.film("doctor", 40, 15, server -> {
			CitizenEntity hurt = (CitizenEntity) server.overworld().getEntity(patients[0]);
			CitizenEntity starving = (CitizenEntity) server.overworld().getEntity(patients[1]);
			return hurt.getHealth() >= 18 && starving.hunger() > 0;
		});
		waitOrExplain(stage, server -> {
			CitizenEntity hurt = (CitizenEntity) server.overworld().getEntity(patients[0]);
			CitizenEntity starving = (CitizenEntity) server.overworld().getEntity(patients[1]);
			return hurt.getHealth() >= 18 && starving.hunger() > 0;
		}, 2400, "le médecin n'a pas soigné ses patients");

		// L'écran : le bonheur moyen, les citoyens et leur humeur, les bâtiments.
		stage.waitTicks(120);
		TownSnapshot snapshot = stage.compute(server -> TownNetwork.snapshot(server.overworld(),
				town(server), server.getPlayerList().getPlayers().getFirst()));
		check(snapshot.life().mood() >= 0 && snapshot.buildings().size() == 2 ? null
				: "écran : bonheur " + snapshot.life().mood() + ", bâtiments " + snapshot.buildings());
		// Les raisons de l'humeur arrivent à l'écran : celles de chaque citoyen, et ce qui pèse sur la ville (MC-71).
		check(snapshot.rows().stream().anyMatch(r -> !r.factors().isEmpty()) && snapshot.life().worries().size() <= 3
				&& snapshot.life().worries().stream().allMatch(w -> w.matches("[a-z_]+:[0-9]+")) ? null
				: "raisons : " + snapshot.life().worries() + " ; " + snapshot.rows().getFirst().factors());
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new TownHallScreen(snapshot));
		});
		stage.waitTicks(5);
		stage.shot("life_1_summary");
		stage.client(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.CITIZENS));
		stage.waitTicks(3);
		stage.shot("life_2_citizens");
		stage.client(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.BUILDINGS));
		stage.waitTicks(3);
		stage.shot("life_3_buildings");
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});

		// Une fête : les émeraudes du grenier paient, tout le monde danse devant l'hôtel de ville.
		check(stage.compute(server -> Festivals.offer(server.overworld(), town(server))
				.map(r -> "fête refusée : " + r).orElse(null)));
		check(stage.compute(server -> Festivals.offer(server.overworld(), town(server))
				.isPresent() ? null : "une seconde fête aussitôt après ne devrait pas être possible"));
		stage.look(0.5, Y + 7, -13, 0.5, Y + 3, 1);
		waitOrExplain(stage, server -> citizens(server).stream().filter(c -> c.action() == Action.DANCE
				&& c.distanceToSqr(0.5, Y, 0.5) < 8 * 8).count() >= 3, 600, "personne ne danse");
		stage.film("festival", 40, 8, server -> false);
		stage.waitTicks(100);
		check(stage.compute(server -> citizens(server).stream().allMatch(c ->
				c.moodDetail().factors().contains(new Happiness.Factor("festival", 2))) ? null
				: "la fête ne réjouit pas tout le monde"));
		stage.run(server -> town(server).chronicle().celebrate(-100000));

		// La cloche : l'alerte. Les civils rentrent, le monstre qui rôde luit.
		UUID husk = stage.compute(server -> {
			ServerLevel level = server.overworld();
			Mob mob = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
			mob.snapTo(-16.5, Y, -6.5, 0, 0);
			mob.setNoAi(true);
			level.addFreshEntity(mob);
			Alarms.ring(level, BELL, server.getPlayerList().getPlayers().getFirst());
			return mob.getUUID();
		});
		check(stage.compute(server -> town(server).chronicle().alarmed(
				server.overworld().getGameTime()) && ((Mob) server.overworld().getEntity(husk))
				.hasEffect(MobEffects.GLOWING) ? null : "l'alerte n'a pas été donnée"));
		waitOrExplain(stage, server -> civilians(server).stream().filter(c -> c.bed().isPresent())
				.allMatch(c -> c.bed().get().distSqr(c.blockPosition()) <= 9), 900,
				"les civils ne se sont pas mis à l'abri");
		stage.look(10, Y + 9, 6, -6, Y, -11);
		stage.waitTicks(10);
		stage.shot("life_4_alarm");
		check(stage.compute(server -> {
			Alarms.ring(server.overworld(), BELL, server.getPlayerList().getPlayers().getFirst());
			server.overworld().getEntity(husk).discard();
			return town(server).chronicle().alarmed(server.overworld().getGameTime())
					? "la cloche, à nouveau, devait lever l'alerte" : null;
		}));

		// Le titre d'entrée sur le territoire, et son contour, l'hôtel de ville en main.
		stage.command("gamemode creative @p");
		stage.command("item replace entity @p weapon.mainhand with minecities:town_hall");
		stage.look(70, Y + 3, 0.5, 0, Y, 0.5);
		stage.waitTicks(30);
		stage.look(44, Y + 3, 0.5, 47, Y, 20);
		stage.client(mc -> hideGui(mc, false));
		stage.waitTicks(25);
		stage.shot("life_5_welcome");
		stage.waitTicks(60);
		stage.shot("life_6_border");
		stage.client(mc -> hideGui(mc, true));

		// Un citoyen par métier, chacun son couvre-chef : un atelier factice offre tous les postes.
		stage.command("gamemode spectator @p");
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			for (int i = 0; i < Job.values().length - 1; i++) {
				TownService.immigrate(level, town);
			}
			List<com.ajustor.minecities.town.House.Workstation> stations = new java.util.ArrayList<>();
			int x = 30;
			for (Job job : Job.values()) {
				if (job != Job.DOCTOR) {
					stations.add(new com.ajustor.minecities.town.House.Workstation(new BlockPos(x++, Y, 30), job));
				}
			}
			town.putHouse(new com.ajustor.minecities.town.House(new BlockPos(29, Y, 30), List.of(), List.of(),
					stations, 10));
			town.assignJobs();
			return town.workers(Job.GUARD) == 1 && town.workers(Job.INVENTOR) == 1 ? null
					: "métiers : " + town.residents().values();
		}));
		stage.waitTicks(120);
		for (int half = 0; half < 2; half++) {
			int from = half * 6;
			stage.run(server -> {
				List<CitizenEntity> all = citizens(server).stream()
						.sorted(java.util.Comparator.comparing(c -> c.job().map(j -> j.job().ordinal()).orElse(99)))
						.toList();
				for (int i = 0; i < all.size(); i++) {
					CitizenEntity c = all.get(i);
					c.setNoAi(true);
					boolean shown = i >= from && i < from + 6 && c.job().isPresent();
					// Les autres attendent plus loin, hors du champ.
					c.snapTo(shown ? -4 + (i - from) * 1.6 : 40 + i, Y, shown ? 12.5 : 40, 180, 0);
					c.setYHeadRot(180);
					c.setYBodyRot(180);
				}
			});
			stage.look(0, Y + 2.3, 6.5, 0, Y + 1.5, 12.5);
			stage.waitTicks(10);
			stage.shot("life_7_hats_" + half);
		}
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	/** Les citoyens qui ne sont pas médecins, dans l'ordre de leurs UUID. */
	private static List<CitizenEntity> civilians(MinecraftServer server) {
		return citizens(server).stream().filter(c -> c.job().filter(j -> j.job() == Job.DOCTOR).isEmpty())
				.sorted(java.util.Comparator.comparing(CitizenEntity::getUUID)).toList();
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -60; x <= 80; x++) {
			for (int z = -40; z <= 40; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 14; y++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		RaidScenes.paste(level, "house_large", HOUSE);
		RaidScenes.paste(level, "infirmary", INFIRMARY);
		// Un pan de mur, seul.
		for (int x = -5; x <= -3; x++) {
			for (int y = Y; y <= Y + 2; y++) {
				level.setBlockAndUpdate(new BlockPos(x, y, LONE_WALL.getZ()), Blocks.OAK_PLANKS.defaultBlockState());
			}
		}
		command(server, "setblock %d %d %d minecraft:bell[attachment=floor,facing=north]".formatted(BELL.getX(),
				BELL.getY(), BELL.getZ()));
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			hall.setItem(0, new ItemStack(Items.BREAD, 32));
			hall.setItem(1, new ItemStack(Items.COOKED_BEEF, 16));
			hall.setItem(2, new ItemStack(Items.BAKED_POTATO, 16));
			hall.setItem(3, new ItemStack(Items.EMERALD, 8));
		}
		command(server, "gamemode spectator @p");
	}
}
