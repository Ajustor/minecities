package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.Action;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.client.screen.TownHallScreen;
import com.ajustor.minecities.network.TownNetwork;
import com.ajustor.minecities.network.TownSnapshot;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.MarriageRequest;
import com.ajustor.minecities.town.Resident;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.UUID;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Les familles, dans le vrai jeu : la ville fondée reçoit ses cinq premiers habitants ; deux d'entre
 * eux, qui s'entendent, demandent à se marier ; le maire les marie depuis l'écran de l'hôtel de ville
 * (photographié, onglet par onglet) ; ils emménagent ensemble, ont un enfant, qui grandit.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=family}
 */
public class FamilyScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos BIG_PLAQUE = new BlockPos(-13, Y + 1, 4);
	private static final BlockPos SMALL_PLAQUE = new BlockPos(5, Y + 1, 4);

	@Override
	public String name() {
		return "family";
	}

	@Override
	public void play(Stage stage) {
		Families.setMinimum(Families.MINIMUM);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.waitTicks(20);
		stage.run(FamilyScenes::build);
		stage.client(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

		// La fondation : les cinq premiers habitants arrivent d'eux-mêmes.
		stage.run(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			TownService.declareHouse(level, BIG_PLAQUE, player);
			TownService.declareHouse(level, SMALL_PLAQUE, player);
		});
		waitOrExplain(stage, server -> town(server).citizens() == Families.MINIMUM, 3600,
				"les cinq premiers habitants ne sont pas arrivés", server -> town(server).citizens() + " citoyens.");
		stage.waitTicks(200);
		check(stage.compute(server -> town(server).citizens() == Families.MINIMUM ? null
				: "au-delà du minimum, personne ne doit plus arriver : " + town(server).citizens()));

		// Deux d'entre eux s'entendent, et demandent à se marier.
		UUID[] couple = stage.compute(server -> {
			Town town = town(server);
			// Deux habitants de la grande maison, où le joueur ajoutera un lit pour l'enfant.
			List<UUID> ids = town.residents().entrySet().stream()
					.filter(e -> e.getValue().home().filter(BIG_PLAQUE::equals).isPresent())
					.map(java.util.Map.Entry::getKey).sorted().toList();
			town.addAffinity(ids.get(0), ids.get(1), 100);
			town.askToMarry(ids.get(0), ids.get(1), server.overworld().getGameTime()).orElseThrow();
			return new UUID[]{ids.get(0), ids.get(1)};
		});

		// L'écran de l'hôtel de ville, onglet par onglet.
		TownSnapshot snapshot = stage.compute(server -> TownNetwork.snapshot(server.overworld(),
				town(server), server.getPlayerList().getPlayers().getFirst()));
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new TownHallScreen(snapshot));
		});
		stage.waitTicks(5);
		stage.shot("family_0_summary");
		stage.client(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.CITIZENS));
		stage.waitTicks(3);
		stage.shot("family_1_citizens");
		stage.client(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.REGISTRY));
		stage.waitTicks(3);
		stage.shot("family_2_registry");
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});

		// Le maire les marie : ils emménagent ensemble.
		check(stage.compute(server -> {
			Town town = town(server);
			MarriageRequest request = town.marriageRequests().getFirst();
			if (!Families.accept(server.overworld(), town, request)) {
				return "le mariage a échoué";
			}
			Resident a = town.resident(couple[0]).orElseThrow();
			Resident b = town.resident(couple[1]).orElseThrow();
			return a.spouse().filter(couple[1]::equals).isPresent() && a.home().isPresent()
					&& a.home().equals(b.home()) ? null : "les mariés ne vivent pas ensemble : " + a + " / " + b;
		}));
		// La cérémonie (MC-81) : les deux mariés viennent devant l'hôtel de ville, puis la cérémonie s'achève.
		waitOrExplain(stage, server -> java.util.Arrays.stream(couple).allMatch(id -> server.overworld().getEntity(id)
				instanceof CitizenEntity c && c.distanceToSqr(town(server).hall().getX() + 0.5, c.getY(),
				town(server).hall().getZ() + 3.5) < 4 * 4), 1200, "les mariés ne viennent pas à l'hôtel de ville");
		waitOrExplain(stage, server -> java.util.Arrays.stream(couple).allMatch(id -> server.overworld().getEntity(id)
				instanceof CitizenEntity c && c.wedding().isEmpty()), 600, "la cérémonie ne finit pas");

		// Le joueur ajoute un lit dans leur maison, pour l'enfant à venir.
		stage.run(server -> {
			command(server, "setblock -15 %d 5 minecraft:yellow_bed[facing=south,part=foot]".formatted(Y));
			command(server, "setblock -15 %d 6 minecraft:yellow_bed[facing=south,part=head]".formatted(Y));
			TownService.declareHouse(server.overworld(), BIG_PLAQUE, server.getPlayerList().getPlayers().getFirst());
		});

		// Bien nourris, avec un lit libre chez eux : un enfant naît.
		UUID child = stage.compute(server -> {
			ServerLevel level = server.overworld();
			for (UUID id : couple) {
				if (level.getEntity(id) instanceof CitizenEntity parent) {
					parent.setHunger(20);
				}
			}
			return Families.birth(level, town(server), couple[0], couple[1], level.getGameTime())
					.map(CitizenEntity::getUUID).orElse(null);
		});
		if (child == null) {
			throw new AssertionError("aucun enfant n'est né : " + stage.compute(
					server -> town(server).residents().toString()));
		}
		check(stage.compute(server -> {
			Resident baby = town(server).resident(child).orElseThrow();
			boolean small = server.overworld().getEntity(child) instanceof CitizenEntity c && c.isChild();
			return baby.isChild() && small && baby.home().equals(town(server).resident(couple[0]).orElseThrow().home())
					? null : "l'enfant n'est pas chez ses parents, ou pas petit : " + baby;
		}));

		// La famille réunie devant l'objectif, les parents en liesse.
		stage.run(server -> {
			ServerLevel level = server.overworld();
			int i = 0;
			for (UUID id : new UUID[]{couple[0], child, couple[1]}) {
				if (level.getEntity(id) instanceof CitizenEntity c) {
					c.snapTo(-1 + i, Y, -4, 180, 0);
					c.getNavigation().stop();
					if (!c.isChild()) {
						c.act(Action.CHEER, 200);
					}
				}
				i++;
			}
		});
		stage.look(0, Y + 1.8, -8, 0, Y + 1, -4);
		stage.waitTicks(10);
		stage.shot("family_3_newborn");

		// Trois jours plus tard, l'enfant est grand.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Families.growUp(level, town(server), level.getGameTime() + Families.CHILDHOOD);
			boolean grown = level.getEntity(child) instanceof CitizenEntity c && !c.isChild();
			return grown && !town(server).resident(child).orElseThrow().isChild() ? null : "l'enfant n'a pas grandi";
		}));
		Families.setMinimum(0);
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -20; x <= 16; x++) {
			for (int z = -16; z <= 16; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 8; y++) {
					level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		// Une maison de quatre lits (de quoi loger un couple, son enfant et un voisin), et une de deux.
		WorkScenes.room(level, -15, 4, -9, 8, -12);
		for (int x : new int[]{-15, -13, -11, -9}) {
			command(server, "setblock %d %d 7 minecraft:white_bed[facing=south,part=foot]".formatted(x, Y));
			command(server, "setblock %d %d 8 minecraft:white_bed[facing=south,part=head]".formatted(x, Y));
		}
		command(server, "setblock -13 %d 4 minecities:house_plaque[facing=south]".formatted(Y + 1));
		WorkScenes.room(level, 4, 4, 8, 8, 6);
		for (int x : new int[]{4, 8}) {
			command(server, "setblock %d %d 7 minecraft:white_bed[facing=south,part=foot]".formatted(x, Y));
			command(server, "setblock %d %d 8 minecraft:white_bed[facing=south,part=head]".formatted(x, Y));
		}
		command(server, "setblock 5 %d 4 minecities:house_plaque[facing=south]".formatted(Y + 1));
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			hall.setItem(0, new ItemStack(Items.COOKED_BEEF, 64));
			hall.setItem(1, new ItemStack(Items.BREAD, 64));
		}
		command(server, "gamemode spectator @p");
	}
}
