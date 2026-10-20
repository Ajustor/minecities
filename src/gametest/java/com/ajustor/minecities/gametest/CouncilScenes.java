package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.caravan.CaravanCart;
import com.ajustor.minecities.caravan.CaravanStall;
import com.ajustor.minecities.caravan.Caravans;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Happiness;
import com.ajustor.minecities.client.screen.TownHallScreen;
import com.ajustor.minecities.network.TownNetwork;
import com.ajustor.minecities.network.TownSnapshot;
import com.ajustor.minecities.registry.McEntities;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Decree;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.TownTier;
import com.ajustor.minecities.town.Towns;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * La mairie et la maîtrise (v1.1), dans le vrai jeu : le forgeron d'une forge de niveau 4 habille le
 * garde de fer de pied en cap ; le tanneur tanne ; le maire décrète un jour férié (l'onglet Mairie) ;
 * deux villes signent un accord et une caravane porte le pain de l'une à l'autre (elle prend corps près
 * du joueur, campe la nuit, et son marchand tient échoppe à l'arrivée).
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=council}
 */
public class CouncilScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos SMITHY = new BlockPos(-30, Y, -18);
	private static final BlockPos BARRACKS = new BlockPos(-12, Y, -18);
	private static final BlockPos MARKET = new BlockPos(2, Y, -18);
	private static final BlockPos TANNERY = new BlockPos(14, Y, -18);
	private static final BlockPos HOUSE = new BlockPos(-6, Y, 10);
	/** La ville voisine, à deux cents blocs. */
	private static final BlockPos OTHER_HALL = new BlockPos(200, Y, 0);

	@Override
	public String name() {
		return "council";
	}

	@Override
	public void play(Stage stage) {
		Families.setMinimum(0);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.command("forceload add 184 -16 216 16");
		stage.waitTicks(20);
		stage.run(CouncilScenes::build);
		stage.client(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			TownService.found(level, OTHER_HALL, player, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			town.forceTier(Optional.of(TownTier.TOWN));
			TownService.retier(level, town);
			// La forge est une demeure, bâtie par la ville (niveau 4).
			Construction smithy = Construction.order(Blueprints.get(server,
					Identifier.fromNamespaceAndPath("minecities", "smithy_4")).orElseThrow(), BuildStyle.PLAINS,
					SMITHY, Rotation.NONE);
			smithy.finish();
			town.order(smithy);
			for (int i = 0; i < 4; i++) {
				TownService.immigrate(level, town);
			}
			// La forge (quatre postes) en dernier : un seul forgeron, les autres métiers d'abord.
			for (BlockPos origin : new BlockPos[]{BARRACKS, MARKET, TANNERY, HOUSE, SMITHY}) {
				RaidScenes.plaque(level, origin).ifPresent(p -> TownService.declareHouse(level, p, player));
			}
			for (Job job : new Job[]{Job.SMITH, Job.GUARD, Job.MERCHANT, Job.TANNER}) {
				if (town.workers(job) != 1) {
					return "pas de " + job.serializedName() + " : " + town.residents() + " ; " + town.houses();
				}
			}
			BlockPos forge = RaidScenes.plaque(level, SMITHY).orElseThrow();
			if (town.buildingLevel(forge) != 4) {
				return "la forge n'est pas de niveau 4";
			}
			// La maîtrise attend un livre du bibliothécaire (MC-63) ; la ville n'en a pas : on la lui donne.
			String before = com.ajustor.minecities.town.Mastery.state(level, town, forge);
			town.chronicle().master(forge);
			return before.equals("library") && com.ajustor.minecities.town.Mastery.active(town, forge) ? null
					: "maîtrise : " + before;
		}));

		// Le maître forgeron habille le garde de fer, de pied en cap ; le tanneur tanne.
		waitOrExplain(stage, server -> citizens(server).stream().filter(CitizenEntity::isGuard).anyMatch(g ->
						g.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
								&& g.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS)
								&& g.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS)), 4800,
				"le garde n'est pas armé de pied en cap", server -> "lingots " + granary(server, HALL, Items.IRON_INGOT)
						+ ", au grenier casques " + granary(server, HALL, Items.IRON_HELMET) + ", jambières "
						+ granary(server, HALL, Items.IRON_LEGGINGS) + ", bottes " + granary(server, HALL, Items.IRON_BOOTS)
						+ ", émeraudes " + granary(server, HALL, Items.EMERALD) + ", par terre " + dropped(server) + " ; "
						+ town(server).houses() + ".");
		waitOrExplain(stage, server -> granary(server, HALL, Items.LEATHER) >= 2, 2400, "le tanneur n'a pas tanné");
		stage.run(server -> citizens(server).stream().filter(CitizenEntity::isGuard).findFirst()
				.ifPresent(g -> {
					g.setNoAi(true);
					g.snapTo(0.5, Y, 6.5, 180, 0);
					g.setYHeadRot(180);
					g.setYBodyRot(180);
				}));
		stage.look(0.5, Y + 1.8, 3.5, 0.5, Y + 1.2, 6.5);
		stage.client(mc -> hideGui(mc, true));
		stage.waitTicks(10);
		stage.shot("council_0_guard");
		stage.run(server -> citizens(server).forEach(c -> c.setNoAi(false)));

		// Le jour férié : la ville se réjouit (et ne travaille pas).
		stage.run(server -> TownService.decree(server.overworld(), town(server), Decree.HOLIDAY));
		waitOrExplain(stage, server -> citizens(server).stream().filter(c -> c.townId().filter(town(server).id()::equals)
				.isPresent()).allMatch(c -> c.moodDetail().factors().contains(new Happiness.Factor("decrees_good", 1))),
				400, "le jour férié ne réjouit personne");
		// Un village ne décrète pas encore ; sous couvre-feu, on rentre dès la fin d'après-midi (MC-66).
		check(stage.compute(server -> {
			Town other = Towns.get(server).byHall(server.overworld().dimension(), OTHER_HALL).orElseThrow();
			if (other.tier() != TownTier.VILLAGE || TownService.decree(server.overworld(), other, Decree.CURFEW).isEmpty()
					|| other.decreed(Decree.CURFEW)) {
				return "un village a décrété le couvre-feu";
			}
			TownService.decree(server.overworld(), town(server), Decree.CURFEW);
			long time = server.overworld().getOverworldClockTime();
			CityScenes.command(server, "time set 11500");
			boolean home = citizens(server).stream().filter(c -> !c.isDefender())
					.allMatch(com.ajustor.minecities.citizen.SleepInBedGoal::bedtime);
			CityScenes.command(server, "time set " + Math.floorMod(time, 24000L));
			TownService.decree(server.overworld(), town(server), Decree.CURFEW);
			return home && !town(server).decreed(Decree.CURFEW) ? null : "le couvre-feu ne renvoie personne";
		}));

		// Un accord de commerce : la voisine n'a pas de pain, la ville en a trop ; et des bagues de
		// joaillier, que le marchand vendra à son échoppe (MC-80).
		stage.run(server -> {
			Town town = town(server);
			if (server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity granary) {
				Stock.insert(granary, new ItemStack(McItems.GOLD_RING, RINGS));
			}
			Town other = Towns.get(server).byHall(server.overworld().dimension(), OTHER_HALL).orElseThrow();
			town.addPartner(other.id());
			other.addPartner(town.id());
		});
		// Une caravane part (MC-80) : elle prend corps près du joueur, chariot, cheval et marchand.
		waitOrExplain(stage, server -> !Caravans.get(server).active().isEmpty(), 1400, "aucune caravane ne part",
				server -> "pain " + granary(server, HALL, Items.BREAD) + " / " + granary(server, OTHER_HALL, Items.BREAD)
						+ ", émeraudes " + granary(server, OTHER_HALL, Items.EMERALD) + ".");
		waitOrExplain(stage, server -> Caravans.get(server).active().getFirst().traveled() > 14
				&& Caravans.embodied(Caravans.get(server).active().getFirst().id()) && cart(server) != null, 400,
				"la caravane ne prend pas corps près du joueur");
		check(stage.compute(server -> server.overworld().getEntities(net.minecraft.world.entity.EntityTypes.HORSE,
				h -> h.entityTags().contains(Caravans.TAG)).size() == 1 ? null : "pas de cheval à la caravane"));
		shootCart(stage, "council_caravan");
		// La nuit, elle s'arrête et campe : tente, feu, le marchand assis.
		stage.command("time set 15000");
		stage.waitTicks(5);
		double before = stage.compute(server -> Caravans.get(server).active().getFirst().traveled());
		stage.waitTicks(60);
		check(stage.compute(server -> Caravans.get(server).active().getFirst().traveled() == before
				&& cart(server) != null && cart(server).camping() ? null : "la caravane ne campe pas la nuit"));
		shootCart(stage, "council_camp");
		stage.command("time set 1000");
		waitOrExplain(stage, server -> granary(server, OTHER_HALL, Items.BREAD) > 0, 4800, "pas de caravane",
				server -> "pain " + granary(server, HALL, Items.BREAD) + " / " + granary(server, OTHER_HALL, Items.BREAD)
						+ ", émeraudes " + granary(server, OTHER_HALL, Items.EMERALD) + ".");
		check(stage.compute(server -> granary(server, OTHER_HALL, Items.EMERALD) < 20
				? null : "la voisine n'a pas payé la caravane : émeraudes " + granary(server, HALL, Items.EMERALD) + " / "
						+ granary(server, OTHER_HALL, Items.EMERALD) + ", pain " + granary(server, HALL, Items.BREAD) + " / "
						+ granary(server, OTHER_HALL, Items.BREAD)));
		stall(stage);

		// L'onglet Mairie : le maire, les décrets, l'accord.
		TownSnapshot snapshot = stage.compute(server -> TownNetwork.snapshot(server.overworld(),
				town(server), server.getPlayerList().getPlayers().getFirst()));
		check(snapshot.council().partners().size() == 1 && snapshot.council().open().getFirst() ? null
				: "la mairie ne montre pas l'accord : " + snapshot.council());
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new TownHallScreen(snapshot));
		});
		stage.waitTicks(3);
		stage.client(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.COUNCIL));
		stage.waitTicks(3);
		stage.shot("council_1_tab");
		// Gérer la mairie sans commande (MC-75) : l'écran, puis les décisions qu'il envoie.
		stage.client(mc -> mc.gui.setScreen(new com.ajustor.minecities.client.screen.ManageScreen(snapshot,
				mc.gui.screen())));
		stage.waitTicks(3);
		stage.shot("council_2_manage");
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});
		check(snapshot.council().towns().size() == 1 && snapshot.council().offered().getFirst()
				&& snapshot.council().mayorView() ? null : "l'écran de gestion : " + snapshot.council());
		check(stage.compute(server -> {
			Town town = town(server);
			Town other = Towns.get(server).byHall(server.overworld().dimension(), OTHER_HALL).orElseThrow();
			String name = town.name();
			if (com.ajustor.minecities.town.Mayoralty.rename(server, town, other.name()).isEmpty()) {
				return "renommée du nom d'une autre ville";
			}
			if (com.ajustor.minecities.town.Mayoralty.rename(server, town, "Nouvelle " + name).isPresent()
					|| !town.name().equals("Nouvelle " + name)) {
				return "pas renommée : " + town.name();
			}
			com.ajustor.minecities.town.Mayoralty.rename(server, town, name);
			// Rompre l'accord, puis le reproposer : la voisine a signé, il se rouvre.
			com.ajustor.minecities.town.Mayoralty.trade(server, town, other);
			boolean broken = !com.ajustor.minecities.town.Trade.trading(town, other);
			com.ajustor.minecities.town.Mayoralty.trade(server, town, other);
			return broken && com.ajustor.minecities.town.Trade.trading(town, other) ? null : "l'accord ne se rompt pas";
		}));
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	/** Les objets qui traînent par terre (une pièce d'armure perdue, par exemple). */
	private static java.util.List<String> dropped(MinecraftServer server) {
		java.util.List<String> dropped = new java.util.ArrayList<>();
		server.overworld().getAllEntities().forEach(e -> {
			if (e instanceof net.minecraft.world.entity.item.ItemEntity item) {
				dropped.add(item.getItem() + " en " + item.blockPosition().toShortString());
			}
		});
		return dropped;
	}

	private static int granary(MinecraftServer server, BlockPos hall, Item item) {
		return server.overworld().getBlockEntity(hall) instanceof TownHallBlockEntity granary
				? Stock.count(granary, s -> s.is(item)) : 0;
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -36; x <= 24; x++) {
			for (int z = -24; z <= 24; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 14; y++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		for (int x = 196; x <= 204; x++) {
			for (int z = -4; z <= 4; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.STONE.defaultBlockState());
			}
		}
		RaidScenes.paste(level, "smithy_4", SMITHY);
		RaidScenes.paste(level, "barracks_1", BARRACKS);
		RaidScenes.paste(level, "market_1", MARKET);
		RaidScenes.paste(level, "tannery_1", TANNERY);
		RaidScenes.paste(level, "house_2", HOUSE);
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		command(server, "setblock 200 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			Item[] items = {Items.BREAD, Items.BREAD, Items.BREAD, Items.IRON_INGOT, Items.IRON_SWORD,
					Items.IRON_CHESTPLATE, Items.RABBIT_HIDE, Items.COOKED_BEEF};
			int[] counts = {64, 64, 64, 30, 1, 1, 8, 16};
			for (int i = 0; i < items.length; i++) {
				hall.setItem(i, new ItemStack(items[i], counts[i]));
			}
		}
		if (level.getBlockEntity(OTHER_HALL) instanceof TownHallBlockEntity hall) {
			hall.setItem(0, new ItemStack(Items.EMERALD, 20));
			hall.setItem(1, new ItemStack(Items.COOKED_COD, 4));
		}
		command(server, "gamemode spectator @p");
	}
	/** Les bagues mises au grenier pour l'échoppe ; le marchand en laisse deux à la ville. */
	private static final int RINGS = 5;

	/**
	 * L'échoppe (MC-80) : à l'arrivée, le marchand s'installe en bordure de la voisine avec les bagues de
	 * sa ville ; près du joueur, elle prend corps ; le troc s'ouvre ; chaque lot vendu quitte l'étal et
	 * paie ; tout vendu, il remballe, et les émeraudes rentrent au grenier de sa ville.
	 */
	private static void stall(Stage stage) {
		waitOrExplain(stage, server -> stall(server) != null, 200, "pas d'échoppe à l'arrivée",
				server -> "bagues au grenier " + granary(server, HALL, McItems.GOLD_RING) + ", échoppes "
						+ Caravans.get(server).stalls().size() + ".");
		check(stage.compute(server -> {
			com.ajustor.minecities.caravan.Stall stall = stall(server);
			Town other = Towns.get(server).byHall(server.overworld().dimension(), OTHER_HALL).orElseThrow();
			int rings = stall.wares().stream().filter(w -> w.is(McItems.GOLD_RING)).mapToInt(ItemStack::getCount).sum();
			// Le marchand en a peut-être vendu une au marché avant le départ ; au grenier, il en garde deux.
			if (rings < 1 || rings + granary(server, HALL, McItems.GOLD_RING) > RINGS
					|| granary(server, HALL, McItems.GOLD_RING) > 2) {
				return "l'échoppe n'a pas les bagues : " + rings + " à l'étal, " + granary(server, HALL, McItems.GOLD_RING)
						+ " au grenier";
			}
			// En bordure de la voisine : dans la ville, mais loin de l'hôtel de ville.
			return other.contains(server.overworld().dimension(), stall.pos())
					&& stall.pos().distSqr(OTHER_HALL) > 12 * 12 ? null : "l'échoppe n'est pas en bordure : " + stall.pos();
		}));
		// Le joueur s'approche : l'échoppe, le chariot dételé, le cheval et le marchand prennent corps.
		BlockPos at = stage.compute(server -> stall(server).pos());
		stage.command("tp @p " + at.getX() + " " + (at.getY() + 1) + " " + (at.getZ() + 6));
		waitOrExplain(stage, server -> Caravans.stallEmbodied(stall(server).id()) && booth(server) != null, 200,
				"l'échoppe ne prend pas corps près du joueur");
		double[] look = stage.compute(server -> {
			CaravanStall booth = booth(server);
			double yaw = Math.toRadians(booth.getYRot());
			double fx = -Math.sin(yaw);
			double fz = Math.cos(yaw);
			return new double[]{booth.getX() + fx * 6 + Math.cos(yaw) * 2.5, booth.getY() + 2.6,
					booth.getZ() + fz * 6 + Math.sin(yaw) * 2.5, booth.getX(), booth.getY() + 1.2, booth.getZ()};
		});
		stage.look(look[0], look[1], look[2], look[3], look[4], look[5]);
		stage.client(mc -> hideGui(mc, true));
		stage.waitTicks(20);
		stage.shot("council_stall");
		// Le troc, sur l'écran des villageois.
		stage.run(server -> Caravans.trade(server.getPlayerList().getPlayers().getFirst(), stall(server).id()));
		stage.waitTicks(10);
		check(stage.compute(server -> server.getPlayerList().getPlayers().getFirst().containerMenu
				instanceof net.minecraft.world.inventory.MerchantMenu ? null : "le troc ne s'ouvre pas"));
		stage.client(mc -> hideGui(mc, false));
		stage.waitTicks(5);
		stage.shot("council_stall_trade");
		stage.run(server -> server.getPlayerList().getPlayers().getFirst().closeContainer());
		stage.client(mc -> hideGui(mc, true));
		// Une bague vendue : une de moins à l'étal, son prix à la caisse ; puis les autres.
		check(stage.compute(server -> {
			com.ajustor.minecities.caravan.Stall stall = stall(server);
			int before = stall.wares().stream().mapToInt(ItemStack::getCount).sum();
			Caravans.get(server).sold(stall.id(), McItems.GOLD_RING);
			int rings = stall.wares().stream().mapToInt(ItemStack::getCount).sum();
			return rings == before - 1 && stall.earned() == com.ajustor.minecities.caravan.Stall.lot(McItems.GOLD_RING)
					.emeralds() ? null : "la vente ne se compte pas : " + rings + " bagues, " + stall.earned() + " émeraudes";
		}));
		int before = stage.compute(server -> granary(server, HALL, Items.EMERALD));
		int earned = stage.compute(server -> {
			com.ajustor.minecities.caravan.Stall stall = stall(server);
			for (int i = 0; i < RINGS && stall.hasWares(); i++) {
				Caravans.get(server).sold(stall.id(), McItems.GOLD_RING);
			}
			return stall.earned();
		});
		// L'échoppe vide ferme dès que le grenier de sa ville est chargé : le joueur y retourne.
		stage.command("tp @p 0.5 " + (Y + 1) + " 3.5");
		waitOrExplain(stage, server -> stall(server) == null, 200, "l'échoppe vide ne ferme pas");
		check(stage.compute(server -> granary(server, HALL, Items.EMERALD) >= before + earned
				&& booth(server) == null ? null : "la recette n'est pas rentrée : " + before + " + " + earned + " ≠ "
				+ granary(server, HALL, Items.EMERALD)));
	}

	/** L'échoppe de la ville chez la voisine, s'il y en a une. */
	private static com.ajustor.minecities.caravan.Stall stall(MinecraftServer server) {
		return Caravans.get(server).stalls().stream().filter(s -> s.from().equals(town(server).id())).findFirst()
				.orElse(null);
	}

	private static CaravanStall booth(MinecraftServer server) {
		return server.overworld().getEntities(McEntities.CARAVAN_STALL, c -> true).stream().findFirst().orElse(null);
	}

	/** Le chariot de la caravane, s'il a pris corps. */
	private static CaravanCart cart(MinecraftServer server) {
		return server.overworld().getEntities(McEntities.CARAVAN_CART, c -> true).stream().findFirst().orElse(null);
	}

	/** Une capture du chariot, vu de côté (le camp se dresse derrière lui). */
	private static void shootCart(Stage stage, String name) {
		double[] at = stage.compute(server -> {
			CaravanCart cart = cart(server);
			double yaw = Math.toRadians(cart.getYRot());
			// L'avant du chariot, puis sa gauche (voir CaravanCart.FIRE_X).
			double fx = -Math.sin(yaw);
			double fz = Math.cos(yaw);
			double lx = Math.cos(yaw);
			double lz = Math.sin(yaw);
			return new double[]{cart.getX() - fx * 3 - lx * 6, cart.getY() + 3.5, cart.getZ() - fz * 3 - lz * 6,
					cart.getX() - fx * 1.5 + lx * 0.5, cart.getY() + 0.8, cart.getZ() - fz * 1.5 + lz * 0.5};
		});
		stage.look(at[0], at[1], at[2], at[3], at[4], at[5]);
		stage.client(mc -> hideGui(mc, true));
		stage.waitTicks(20);
		stage.shot(name);
	}
}
