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
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
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
import static com.ajustor.minecities.gametest.CityScenes.look;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Les familles, dans le vrai jeu : la ville fondée reçoit ses cinq premiers habitants ; deux d'entre
 * eux, qui s'entendent, demandent à se marier ; le maire les marie depuis l'écran de l'hôtel de ville
 * (photographié, onglet par onglet) ; ils emménagent ensemble, ont un enfant, qui grandit.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=family}
 */
public class FamilyScenes implements FabricClientGameTest {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos BIG_PLAQUE = new BlockPos(-13, Y + 1, 4);
	private static final BlockPos SMALL_PLAQUE = new BlockPos(5, Y + 1, 4);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!CityScenes.selected("family")) {
			return;
		}
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			Families.setMinimum(Families.MINIMUM);
			command(sp, "time set 1000");
			command(sp, "gamerule doDaylightCycle false");
			command(sp, "gamerule doMobSpawning false");
			command(sp, "weather clear");
			context.waitTicks(20);
			sp.getServer().runOnServer(FamilyScenes::build);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// La fondation : les cinq premiers habitants arrivent d'eux-mêmes.
			sp.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
				TownService.declareHouse(level, BIG_PLAQUE, player);
				TownService.declareHouse(level, SMALL_PLAQUE, player);
			});
			waitOrExplain(sp, server -> town(server).citizens() == Families.MINIMUM, 3600,
					"les cinq premiers habitants ne sont pas arrivés", server -> town(server).citizens() + " citoyens.");
			context.waitTicks(200);
			check(sp.getServer().computeOnServer(server -> town(server).citizens() == Families.MINIMUM ? null
					: "au-delà du minimum, personne ne doit plus arriver : " + town(server).citizens()));

			// Deux d'entre eux s'entendent, et demandent à se marier.
			UUID[] couple = sp.getServer().computeOnServer(server -> {
				Town town = town(server);
				List<UUID> ids = List.copyOf(town.residents().keySet());
				town.addAffinity(ids.get(0), ids.get(1), 100);
				town.askToMarry(ids.get(0), ids.get(1), server.overworld().getGameTime()).orElseThrow();
				return new UUID[]{ids.get(0), ids.get(1)};
			});

			// L'écran de l'hôtel de ville, onglet par onglet.
			TownSnapshot snapshot = sp.getServer().computeOnServer(server -> TownNetwork.snapshot(server.overworld(),
					town(server), server.getPlayerList().getPlayers().getFirst()));
			context.runOnClient(mc -> {
				hideGui(mc, false);
				mc.gui.setScreen(new TownHallScreen(snapshot));
			});
			context.waitTicks(5);
			context.takeScreenshot("family_0_summary");
			context.runOnClient(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.CITIZENS));
			context.waitTicks(3);
			context.takeScreenshot("family_1_citizens");
			context.runOnClient(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.REGISTRY));
			context.waitTicks(3);
			context.takeScreenshot("family_2_registry");
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				hideGui(mc, true);
			});

			// Le maire les marie : ils emménagent ensemble.
			check(sp.getServer().computeOnServer(server -> {
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

			// Le joueur ajoute un lit dans leur maison, pour l'enfant à venir.
			sp.getServer().runOnServer(server -> {
				command(server, "setblock -15 %d 5 minecraft:yellow_bed[facing=south,part=foot]".formatted(Y));
				command(server, "setblock -15 %d 6 minecraft:yellow_bed[facing=south,part=head]".formatted(Y));
				TownService.declareHouse(server.overworld(), BIG_PLAQUE, server.getPlayerList().getPlayers().getFirst());
			});

			// Bien nourris, avec un lit libre chez eux : un enfant naît.
			UUID child = sp.getServer().computeOnServer(server -> {
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
				throw new AssertionError("aucun enfant n'est né : " + sp.getServer().computeOnServer(
						server -> town(server).residents().toString()));
			}
			check(sp.getServer().computeOnServer(server -> {
				Resident baby = town(server).resident(child).orElseThrow();
				boolean small = server.overworld().getEntity(child) instanceof CitizenEntity c && c.isChild();
				return baby.isChild() && small && baby.home().equals(town(server).resident(couple[0]).orElseThrow().home())
						? null : "l'enfant n'est pas chez ses parents, ou pas petit : " + baby;
			}));

			// La famille réunie devant l'objectif, les parents en liesse.
			sp.getServer().runOnServer(server -> {
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
			look(sp, 0, Y + 1.8, -8, 0, Y + 1, -4);
			context.waitTicks(10);
			context.takeScreenshot("family_3_newborn");

			// Trois jours plus tard, l'enfant est grand.
			check(sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				Families.growUp(level, town(server), level.getGameTime() + Families.CHILDHOOD);
				boolean grown = level.getEntity(child) instanceof CitizenEntity c && !c.isChild();
				return grown && !town(server).resident(child).orElseThrow().isChild() ? null : "l'enfant n'a pas grandi";
			}));
			Families.setMinimum(0);
		}
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
