package com.ajustor.minecities.gametest;

import com.ajustor.minecities.caravan.Bandit;
import com.ajustor.minecities.caravan.Caravan;
import com.ajustor.minecities.caravan.CaravanCart;
import com.ajustor.minecities.caravan.Caravans;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.registry.McEntities;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Les caravanes sur la route (MC-80, étape c), entre deux villes reliées par un chemin : l'escorte
 * repousse un bandit ; sans escorte, les bandits mettent le marchand à terre, pillent la moitié du
 * chargement et s'enfuient ; un joueur qui pille un chariot dont un défenseur a survécu devient
 * hors-la-loi, et ce qu'il prend quitte bien le chariot ; loin de tout joueur, l'embuscade se règle sur
 * le papier.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=caravans}
 */
public class CaravanScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos A = new BlockPos(0, Y, 0);
	private static final BlockPos B = new BlockPos(160, Y, 0);
	/** Où l'embuscade attend, le long de la route (assez loin pour sortir de la première ville). */
	private static final double AMBUSH = 42;
	private static final int BREAD = 32;

	@Override
	public String name() {
		return "caravans";
	}

	@Override
	public void play(Stage stage) {
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.command("difficulty normal");
		stage.command("forceload add -24 -24 184 24");
		stage.waitTicks(20);
		stage.run(CaravanScenes::build);
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			TownService.found(level, A, player, new ItemStack(McItems.TOWN_HALL));
			TownService.found(level, B, player, new ItemStack(McItems.TOWN_HALL));
			return town(server, A) != null && town(server, B) != null ? null : "les deux villes ne sont pas fondées";
		}));

		escort(stage);
		ambush(stage);
		theft(stage);
		paper(stage);
	}

	/** L'escorte de deux gardes repousse un bandit : rien n'est perdu, la caravane repart. */
	private static void escort(Stage stage) {
		UUID id = launch(stage, 2, 1, AMBUSH);
		watch(stage, id);
		waitOrExplain(stage, server -> !Caravans.bandits(id).isEmpty(), 600, "pas d'embuscade",
				server -> "parcouru " + caravan(server, id).map(Caravan::traveled).orElse(-1.0));
		stage.waitTicks(25);
		shoot(stage, id, "caravans_0_escort");
		waitOrExplain(stage, server -> Caravans.bandits(id).isEmpty(), 900, "l'escorte ne repousse pas le bandit",
				server -> "bandits " + server.overworld().getEntities(McEntities.BANDIT, b -> true).size()
						+ ", défenseurs debout " + Caravans.fray(server.overworld(), id).orElse(-1));
		check(stage.compute(server -> bread(server, id) == BREAD ? null
				: "l'escorte a vaincu mais la cargaison a fondu : " + bread(server, id)));
		double before = stage.compute(server -> caravan(server, id).orElseThrow().traveled());
		stage.waitTicks(40);
		check(stage.compute(server -> caravan(server, id).orElseThrow().traveled() > before ? null
				: "la caravane ne repart pas après l'escarmouche"));
		stage.run(server -> Caravans.get(server).cancel(server, id));
	}

	/**
	 * Sans escorte, deux bandits : le marchand à terre, la moitié du pain s'en va avec eux, puis il se
	 * relève et repart.
	 */
	private static void ambush(Stage stage) {
		UUID id = launch(stage, 0, 2, AMBUSH);
		watch(stage, id);
		waitOrExplain(stage, server -> !Caravans.bandits(id).isEmpty(), 600, "pas d'embuscade");
		stage.waitTicks(30);
		shoot(stage, id, "caravans_1_ambush");
		waitOrExplain(stage, server -> bread(server, id) < BREAD, 900, "les bandits ne pillent pas",
				server -> "défenseurs debout " + Caravans.fray(server.overworld(), id).orElse(-1));
		check(stage.compute(server -> bread(server, id) == BREAD / 2 ? null
				: "le pillage ne prend pas la moitié : " + bread(server, id)));
		stage.waitTicks(10);
		shoot(stage, id, "caravans_2_plundered");
		waitOrExplain(stage, server -> Caravans.fray(server.overworld(), id).isEmpty(), 400,
				"le marchand ne se relève pas après la fuite des bandits");
		stage.run(server -> Caravans.get(server).cancel(server, id));
	}

	/**
	 * Un joueur s'en prend à une caravane : il tue le marchand, le garde est à terre (vivant) ; il
	 * fouille le chariot, prend du pain : le pain quitte bien le chariot, et le garde survivant le
	 * dénonce : hors-la-loi dans les deux villes.
	 */
	private static void theft(Stage stage) {
		UUID id = launch(stage, 1, 0, -1);
		watch(stage, id);
		// Il l'attend au bord de la route.
		waitOrExplain(stage, server -> caravan(server, id).orElseThrow().traveled() > AMBUSH - 4
				&& Caravans.defenders(id).size() == 2 && Caravans.defenders(id).stream()
				.allMatch(m -> server.overworld().getEntity(m) != null), 600, "la caravane ne prend pas corps");
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			List<UUID> defenders = Caravans.defenders(id);
			CitizenEntity guard = (CitizenEntity) level.getEntity(defenders.getFirst());
			CitizenEntity merchant = (CitizenEntity) level.getEntity(defenders.getLast());
			guard.hurtServer(level, level.damageSources().generic(), 100);
			merchant.hurtServer(level, player.damageSources().playerAttack(player), 100);
			return guard.downed() && guard.isAlive() && !merchant.isAlive() ? null
					: "le garde devrait être à terre, le marchand mort : " + guard.downed() + " / " + merchant.isAlive();
		}));
		stage.waitTicks(5);
		check(stage.compute(server -> Caravans.fray(server.overworld(), id).orElse(-1) == 0 ? null
				: "il reste des défenseurs debout : " + Caravans.fray(server.overworld(), id)));
		shoot(stage, id, "caravans_3_downed");
		stage.run(server -> {
			ServerPlayer player = player(server);
			Caravans.loot(player, (CaravanCart) server.overworld().getEntity(Caravans.cartOf(id).orElseThrow()));
		});
		stage.waitTicks(5);
		check(stage.compute(server -> player(server).containerMenu instanceof ChestMenu ? null : "le chariot ne s'ouvre pas"));
		stage.client(mc -> hideGui(mc, false));
		stage.waitTicks(5);
		stage.shot("caravans_4_loot");
		stage.client(mc -> hideGui(mc, true));
		int taken = stage.compute(server -> {
			ServerPlayer player = player(server);
			ChestMenu menu = (ChestMenu) player.containerMenu;
			ItemStack loaf = menu.getContainer().removeItem(0, 20);
			player.getInventory().add(loaf.copy());
			player.closeContainer();
			return loaf.getCount();
		});
		check(stage.compute(server -> {
			int inHand = player(server).getInventory().countItem(Items.BREAD);
			if (taken != 20 || inHand != 20 || bread(server, id) != BREAD - 20) {
				return "le pillage ne tombe pas juste : pris " + taken + ", en poche " + inHand + ", au chariot "
						+ bread(server, id);
			}
			long now = server.overworld().getGameTime();
			UUID thief = player(server).getUUID();
			return Caravans.get(server).isOutlaw(thief, town(server, A).id(), now)
					&& Caravans.get(server).isOutlaw(thief, town(server, B).id(), now) ? null
					: "le garde survivant n'a pas dénoncé le voleur";
		}));
		stage.run(server -> {
			player(server).getInventory().clearContent();
			Caravans.get(server).cancel(server, id);
		});
	}

	/** Loin de tout joueur, quatre bandits contre une caravane sans escorte : la moitié est perdue. */
	private static void paper(Stage stage) {
		stage.command("tp @p 80 " + (Y + 30) + " 120");
		UUID id = stage.compute(server -> {
			ServerLevel level = server.overworld();
			Caravans caravans = Caravans.get(server);
			return caravans.launch(level, town(server, A), town(server, B), List.of(new ItemStack(Items.BREAD, BREAD)),
					List.of(), "Marchand", caravans.path(level, town(server, A), town(server, B)), 0, 4, 2).id();
		});
		waitOrExplain(stage, server -> bread(server, id) < BREAD, 200, "l'embuscade sur le papier ne pille rien");
		check(stage.compute(server -> bread(server, id) == BREAD / 2
				&& server.overworld().getEntities(McEntities.BANDIT, b -> true).isEmpty() ? null
				: "l'embuscade sur le papier : " + bread(server, id) + " pains, des bandits en chair"));
		stage.run(server -> Caravans.get(server).cancel(server, id));
	}

	/** Une caravane de A vers B, avec tant de gardes, et tant de bandits qui l'attendent là (négatif : aucun). */
	private static UUID launch(Stage stage, int escort, int bandits, double ambushAt) {
		return stage.compute(server -> {
			ServerLevel level = server.overworld();
			Caravans caravans = Caravans.get(server);
			Town a = town(server, A);
			Town b = town(server, B);
			return caravans.launch(level, a, b, List.of(new ItemStack(Items.BREAD, BREAD)), List.of(), "Marchand",
					caravans.path(level, a, b), escort, bandits, ambushAt).id();
		});
	}

	/** Le joueur se poste au bord de la route, là où l'embuscade attend. */
	private static void watch(Stage stage, UUID id) {
		stage.command("tp @p " + (int) AMBUSH + " " + (Y + 1) + " 10");
		stage.look(AMBUSH - 6, Y + 4, 12, AMBUSH + 2, Y + 1, 0);
		stage.client(mc -> hideGui(mc, true));
	}

	/** Une capture de l'escarmouche, vue de côté : le chariot et les bandits dans le cadre. */
	private static void shoot(Stage stage, UUID id, String name) {
		double[] at = stage.compute(server -> {
			ServerLevel level = server.overworld();
			net.minecraft.world.entity.Entity cart = Caravans.cartOf(id).map(level::getEntity).orElse(null);
			double x = cart == null ? AMBUSH : cart.getX();
			double y = cart == null ? Y : cart.getY();
			double z = cart == null ? 4 : cart.getZ();
			int n = 1;
			for (UUID bandit : Caravans.bandits(id)) {
				if (level.getEntity(bandit) instanceof Bandit b && b.isAlive() && b.distanceToSqr(x, y, z) < 20 * 20) {
					x += b.getX();
					z += b.getZ();
					n++;
				}
			}
			x /= n;
			z /= n;
			return new double[]{x - 1, y + 3.5, z + 9, x, y + 0.6, z};
		});
		stage.look(at[0], at[1], at[2], at[3], at[4], at[5]);
		stage.waitTicks(3);
		stage.shot(name);
	}

	private static Optional<Caravan> caravan(MinecraftServer server, UUID id) {
		return Caravans.get(server).active().stream().filter(c -> c.id().equals(id)).findFirst();
	}

	/** Le pain que porte encore cette caravane. */
	private static int bread(MinecraftServer server, UUID id) {
		return caravan(server, id).map(c -> c.cargo().stream().filter(s -> s.is(Items.BREAD))
				.mapToInt(ItemStack::getCount).sum()).orElse(-1);
	}

	private static Town town(MinecraftServer server, BlockPos hall) {
		return Towns.get(server).byHall(server.overworld().dimension(), hall).orElse(null);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Deux places d'herbe autour des hôtels de ville, reliées par un chemin de terre battue. */
	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -16; x <= 176; x++) {
			for (int z = -16; z <= 16; z++) {
				boolean plaza = x <= 16 || x >= 144;
				boolean verge = Math.abs(z - 4) <= 7;
				boolean road = Math.abs(z - 4) <= 1;
				if (!plaza && !verge) {
					continue;
				}
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z),
						(road && !plaza ? Blocks.DIRT_PATH : Blocks.GRASS_BLOCK).defaultBlockState());
				for (int y = Y; y < Y + 6; y++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		command(server, "setblock 160 %d 0 minecities:town_hall[facing=north]".formatted(Y));
	}
}
