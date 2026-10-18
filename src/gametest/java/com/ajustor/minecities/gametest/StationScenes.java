package com.ajustor.minecities.gametest;

import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Builds;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.client.screen.BuildingScreen;
import com.ajustor.minecities.client.screen.StationScreen;
import com.ajustor.minecities.network.BuildingNetwork;
import com.ajustor.minecities.network.BuildingSnapshot;
import com.ajustor.minecities.network.StationNetwork;
import com.ajustor.minecities.network.StationSnapshot;
import com.ajustor.minecities.registry.McBlocks;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Les postes de travail du mod et les interfaces (v1.8) : un poste pour chaque métier, reconnu comme
 * le bloc vanilla ; l'établi du bâtisseur offert à la fondation, qui donne tout de suite un bâtisseur ;
 * l'interface d'un poste (son état, ses travailleurs, les chantiers du bâtisseur) ; l'interface d'un
 * bâtiment, et son amélioration commandée d'un bouton.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=stations}
 */
public class StationScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos PLAQUE = new BlockPos(5, Y + 1, 4);
	/** La rangée des postes, au nord, à montrer. */
	private static final int ROW_Z = -12;
	/** Une maison bâtie par la ville, à améliorer. */
	private static final BlockPos BUILT = new BlockPos(-16, Y, 8);
	private static final BlockPos SITE = new BlockPos(16, Y, -26);

	@Override
	public String name() {
		return "stations";
	}

	@Override
	public void play(Stage stage) {
		Families.setMinimum(0);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.waitTicks(20);
		stage.run(CityScenes::build);

		// La fondation : l'établi du bâtisseur, offert.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer mayor = mayor(server);
			mayor.getInventory().clearContent();
			// L'établi n'est offert qu'une fois par joueur : le joueur des scènes a déjà fondé d'autres villes.
			mayor.removeTag("minecities.bench");
			TownService.found(level, HALL, mayor, new ItemStack(McItems.TOWN_HALL));
			// De quoi payer les chantiers à partir du niveau 2 (MC-57).
			if (level.getBlockEntity(HALL) instanceof com.ajustor.minecities.block.TownHallBlockEntity hall) {
				com.ajustor.minecities.town.Stock.insert(hall,
						new ItemStack(net.minecraft.world.item.Items.EMERALD, 64));
			}
			return mayor.getInventory().contains(new ItemStack(McItems.STATIONS.get(Job.BUILDER))) ? null
					: "pas d'établi du bâtisseur offert";
		}));

		// Chaque poste du mod est reconnu comme celui de son métier (le pêcheur, près de l'eau).
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			List<String> wrong = new ArrayList<>();
			int x = -18;
			for (Map.Entry<Job, Block> e : McBlocks.STATIONS.entrySet()) {
				BlockPos pos = new BlockPos(x, Y, ROW_Z);
				level.setBlockAndUpdate(pos, e.getValue().defaultBlockState());
				if (e.getKey() == Job.FISHERMAN) {
					level.setBlockAndUpdate(pos.north(), Blocks.WATER.defaultBlockState());
				}
				Optional<Job> job = Job.ofWorkstation(level, pos, level.getBlockState(pos));
				if (!job.equals(Optional.of(e.getKey()))) {
					wrong.add(e.getKey() + " → " + job);
				}
				x += 2;
			}
			if (McBlocks.STATIONS.size() != 19) {
				wrong.add(McBlocks.STATIONS.size() + " postes");
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// L'établi posé dans la maison, déclarée : la ville a son bâtisseur.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer mayor = mayor(server);
			Town town = town(server);
			TownService.declareHouse(level, PLAQUE, mayor);
			House house = town.house(PLAQUE).orElse(null);
			if (house == null) {
				return "pas de maison";
			}
			BlockPos spot = floorSpot(level, house).orElse(null);
			if (spot == null) {
				return "pas de place dans la maison";
			}
			level.setBlockAndUpdate(spot, McBlocks.STATIONS.get(Job.BUILDER).defaultBlockState());
			TownService.declareHouse(level, PLAQUE, mayor);
			TownService.immigrate(level, town);
			TownService.immigrate(level, town);
			return null;
		}));
		waitOrExplain(stage, server -> town(server).workers(Job.BUILDER) == 1, 600, "pas de bâtisseur");

		// Renvoyé à son poste (MC-19) : de loin, il arrive près de l'établi, sans changer d'emploi ; un
		// citoyen sans emploi, ou un poste sans place libre autour, ne part pas.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			List<String> wrong = new ArrayList<>();
			java.util.UUID id = town.residents().entrySet().stream()
					.filter(e -> e.getValue().job().filter(j -> j.job() == Job.BUILDER).isPresent())
					.map(Map.Entry::getKey).findFirst().orElseThrow();
			com.ajustor.minecities.citizen.CitizenEntity builder = (com.ajustor.minecities.citizen.CitizenEntity)
					level.getEntity(id);
			BlockPos station = town.resident(id).flatMap(com.ajustor.minecities.town.Resident::job).orElseThrow().station();
			builder.teleportTo(30.5, Y, 30.5);
			com.ajustor.minecities.town.Postings.Outcome sent = com.ajustor.minecities.town.Postings.send(level, town,
					builder.getUUID());
			if (!sent.sent() || builder.distanceToSqr(station.getX() + 0.5, station.getY(), station.getZ() + 0.5) > 3.5 * 3.5
					|| !town.resident(id).flatMap(com.ajustor.minecities.town.Resident::job)
							.map(j -> j.station().equals(station) && j.job() == Job.BUILDER).orElse(false)) {
				wrong.add("renvoyé : " + sent + " à " + builder.blockPosition().toShortString() + ", poste " + station);
			}
			town.residents().entrySet().stream().filter(e -> e.getValue().job().isEmpty()).findFirst().ifPresent(jobless -> {
				if (com.ajustor.minecities.town.Postings.send(level, town, jobless.getKey()).sent()) {
					wrong.add("un sans-emploi est parti");
				}
			});
			// Plus de place : tout l'air autour du poste comblé, le temps de l'essai.
			builder.teleportTo(30.5, Y, 30.5);
			List<BlockPos> filled = new ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosed(station.offset(-2, -1, -2), station.offset(2, 2, 2))) {
				if (level.getBlockState(pos).isAir()) {
					filled.add(pos.immutable());
					level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 2);
				}
			}
			com.ajustor.minecities.town.Postings.Outcome blocked = com.ajustor.minecities.town.Postings.send(level, town,
					builder.getUUID());
			filled.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2));
			if (blocked.sent() || builder.distanceToSqr(30.5, Y, 30.5) > 1) {
				wrong.add("parti sans place : " + blocked);
			}
			com.ajustor.minecities.town.Postings.send(level, town, builder.getUUID());
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// L'interface du poste : il sert, son bâtisseur, le chantier commandé.
		BlockPos bench = stage.compute(server -> town(server).houses().stream()
				.flatMap(h -> h.workstations().stream()).filter(w -> w.job() == Job.BUILDER).findFirst().orElseThrow().pos());
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			Builds.order(level, town, Blueprints.get(server, Identifier.fromNamespaceAndPath("minecities", "house_1"))
					.orElseThrow(), BuildStyle.PLAINS, SITE, Rotation.NONE);
			StationSnapshot s = StationNetwork.snapshot(level, bench).orElseThrow();
			List<String> wrong = new ArrayList<>();
			if (!s.status().equals("ok") || s.workers().size() != 1 || s.sites().size() != 1
					|| s.sites().getFirst().total() <= 0 || !s.job().equals("builder")) {
				wrong.add("le poste : " + s);
			}
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
			StationSnapshot.CODEC.encode(buffer, s);
			if (!StationSnapshot.CODEC.decode(buffer).equals(s)) {
				wrong.add("relu autrement");
			}
			// Hors de la ville, puis non déclaré, puis fermé à ce palier, puis loin de l'eau.
			BlockPos far = new BlockPos(500, Y, 500);
			level.setBlockAndUpdate(far, McBlocks.STATIONS.get(Job.COOK).defaultBlockState());
			String outside = StationNetwork.snapshot(level, far).map(StationSnapshot::status).orElse("-");
			String loose = StationNetwork.snapshot(level, new BlockPos(-16, Y, ROW_Z)).map(StationSnapshot::status)
					.orElse("-");
			BlockPos merchant = new BlockPos(-18 + 2 * List.copyOf(McBlocks.STATIONS.keySet()).indexOf(Job.MERCHANT), Y, ROW_Z);
			String locked = StationNetwork.snapshot(level, merchant).map(StationSnapshot::status).orElse("-");
			BlockPos dry = new BlockPos(20, Y, 20);
			level.setBlockAndUpdate(dry, McBlocks.STATIONS.get(Job.FISHERMAN).defaultBlockState());
			String noWater = StationNetwork.snapshot(level, dry).map(StationSnapshot::status).orElse("-");
			if (!outside.equals("no_town") || !loose.equals("undeclared") || !locked.equals("locked")
					|| !noWater.equals("no_water")) {
				wrong.add("états : hors ville " + outside + ", libre " + loose + ", palier " + locked + ", sec " + noWater);
			}
			// Un poste libre, quand tout le monde a déjà un emploi : personne ne viendra (MC-56).
			if (town.residents().values().stream().allMatch(r -> r.isChild() || r.job().isPresent())
					!= StationNetwork.vacancy(town).equals("vacant_none")) {
				wrong.add("poste libre : " + StationNetwork.vacancy(town));
			}
			level.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// L'interface d'un bâtiment bâti à la main : pas d'amélioration ; d'un bâtiment bâti par la ville : si.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			ServerPlayer mayor = mayor(server);
			List<String> wrong = new ArrayList<>();
			BuildingSnapshot handmade = BuildingNetwork.snapshot(level, PLAQUE, mayor).orElseThrow();
			if (!handmade.valid() || handmade.level() != 1 || handmade.next().isEmpty() || !handmade.rebuild()
					|| handmade.materials().isEmpty()) {
				wrong.add("à la main : " + handmade.next() + " " + handmade.rebuild() + " " + handmade.refusal());
			}
			// Une maison bâtie par la ville (posée d'un coup, chantier fini).
			Construction built = Construction.order(Blueprints.get(server, Identifier.fromNamespaceAndPath("minecities",
					"house_1")).orElseThrow(), BuildStyle.PLAINS, BUILT, Rotation.NONE);
			built.finish();
			town.order(built);
			RaidScenes.paste(level, "house_1", BUILT);
			BlockPos plaque = RaidScenes.plaque(level, BUILT).orElseThrow();
			TownService.declareHouse(level, plaque, mayor);
			BuildingSnapshot b = BuildingNetwork.snapshot(level, plaque, mayor).orElseThrow();
			if (!b.valid() || b.next().isEmpty() || b.refusal().isPresent() || b.materials().isEmpty() || !b.manager()) {
				wrong.add("bâtie par la ville : " + b.next() + " " + b.refusal() + " " + b.materials().size());
			}
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
			BuildingSnapshot.CODEC.encode(buffer, b);
			BuildingSnapshot read = BuildingSnapshot.CODEC.decode(buffer);
			if (!read.next().equals(b.next()) || read.materials().size() != b.materials().size()) {
				wrong.add("relu autrement");
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// En images : la rangée des postes, l'interface de l'établi, puis celle de la maison bâtie.
		stage.client(mc -> hideGui(mc, true));
		// Les dix-neuf postes, chacun de près.
		int index = 0;
		for (Job job : McBlocks.STATIONS.keySet()) {
			double cx = -18 + 2 * index + 0.5;
			stage.look(cx + 1.3, Y + 1.7, ROW_Z + 2.2, cx, Y + 0.75, ROW_Z + 0.5);
			stage.waitTicks(index == 0 ? 20 : 4);
			stage.shot("stations_0_" + job.serializedName());
			index++;
		}
		StationSnapshot station = stage.compute(server -> StationNetwork.snapshot(server.overworld(), bench).orElseThrow());
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new StationScreen(station));
		});
		stage.waitTicks(3);
		stage.shot("stations_1_builder");
		BuildingSnapshot house = stage.compute(server -> BuildingNetwork.snapshot(server.overworld(),
				RaidScenes.plaque(server.overworld(), BUILT).orElseThrow(), mayor(server)).orElseThrow());
		stage.client(mc -> mc.gui.setScreen(new BuildingScreen(house)));
		stage.waitTicks(3);
		stage.shot("stations_2_building");
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});

		// En image : un bâtiment fait à la main (beaucoup de matériaux, et l'avertissement).
		BuildingSnapshot byHand = stage.compute(server -> BuildingNetwork.snapshot(server.overworld(), PLAQUE,
				mayor(server)).orElseThrow());
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new BuildingScreen(byHand));
		});
		stage.waitTicks(3);
		stage.shot("stations_3_handmade");
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});

		// Fait à la main : rebâti au niveau 2, l'ancien dégagé d'abord (du toit vers le sol).
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			House handmade = town.house(PLAQUE).orElseThrow();
			Optional<net.minecraft.network.chat.Component> refusal = Builds.rebuild(level, town, PLAQUE);
			Optional<Construction> site = town.constructions().stream().filter(c -> !c.done() && c.clearing().isPresent())
					.findFirst();
			if (refusal.isPresent() || site.isEmpty()) {
				return "rebâtir : " + refusal;
			}
			List<com.ajustor.minecities.build.BuildPlan.Placement> plan = Builds.plan(server, site.get());
			int blueprint = com.ajustor.minecities.build.BuildPlan.of(Blueprints.get(server, site.get().blueprint())
					.orElseThrow(), site.get().style(), site.get().origin(), site.get().rotation()).size();
			boolean clearsFirst = plan.subList(0, plan.size() - blueprint).stream().allMatch(p -> p.state().isAir())
					&& plan.getFirst().pos().getY() >= plan.get(plan.size() - blueprint - 1).pos().getY();
			boolean covers = site.get().contains(handmade.min().orElseThrow()) && site.get().contains(handmade.max().orElseThrow());
			town.cancel(site.get());
			return plan.size() > blueprint && clearsFirst && covers
					&& site.get().blueprint().getPath().equals("house_2") ? null
					: "le plan : " + plan.size() + " contre " + blueprint + ", dégage d'abord " + clearsFirst + ", couvre "
							+ covers + ", " + site.get().blueprint();
		}));

		// L'amélioration commandée : un chantier s'ouvre à sa place.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			BlockPos plaque = RaidScenes.plaque(level, BUILT).orElseThrow();
			Optional<net.minecraft.network.chat.Component> refusal = Builds.upgrade(level, town, plaque);
			BuildingSnapshot b = BuildingNetwork.snapshot(level, plaque, mayor(server)).orElseThrow();
			return refusal.isEmpty() && b.placed() >= 0 && b.refusal().isPresent() ? null
					: "amélioration : " + refusal + ", chantier " + b.placed() + ", " + b.refusal();
		}));
		// En image : le chantier commandé, l'amélioration refusée tant qu'il dure.
		BuildingSnapshot ordered = stage.compute(server -> BuildingNetwork.snapshot(server.overworld(),
				RaidScenes.plaque(server.overworld(), BUILT).orElseThrow(), mayor(server)).orElseThrow());
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new BuildingScreen(ordered));
		});
		stage.waitTicks(3);
		stage.shot("stations_4_in_progress");
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});
	}

	/** Une case libre sur le sol de la maison, loin des lits. */
	private static Optional<BlockPos> floorSpot(ServerLevel level, House house) {
		BlockPos min = house.min().orElseThrow();
		BlockPos max = house.max().orElseThrow();
		for (BlockPos pos : BlockPos.betweenClosed(min.offset(1, 1, 1), max.offset(-1, -1, -1))) {
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolid()
					&& level.getBlockState(pos.above()).isAir() && house.beds().stream().noneMatch(b -> b.distManhattan(pos) < 2)
					&& !pos.equals(PLAQUE)) {
				return Optional.of(pos.immutable());
			}
		}
		return Optional.empty();
	}

	private static ServerPlayer mayor(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElse(null);
	}
}
