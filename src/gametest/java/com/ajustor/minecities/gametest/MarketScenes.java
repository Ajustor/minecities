package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.client.screen.TownHallScreen;
import com.ajustor.minecities.network.TownNetwork;
import com.ajustor.minecities.network.TownSnapshot;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.TownTier;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.look;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Le marché et les paliers (v0.7), dans le vrai jeu : le village n'a pas de marchand ; devenu ville,
 * son marché ouvre. Le fermier réclame une houe : le marchand l'achète avec les émeraudes du grenier,
 * puis vend le surplus de blé ; une grande ville pose une mairie annexe qui étend son territoire.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=market}
 */
public class MarketScenes implements FabricClientGameTest {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos HOUSE = new BlockPos(-6, Y, -16);
	private static final BlockPos MARKET = new BlockPos(8, Y, -16);
	private static final BlockPos FARM = new BlockPos(-20, Y, -2);
	private static final BlockPos ANNEX = new BlockPos(90, Y, 0);
	private static final int WHEAT = 92;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!CityScenes.selected("market")) {
			return;
		}
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			Families.setMinimum(0);
			command(sp, "time set 1000");
			command(sp, "gamerule doDaylightCycle false");
			command(sp, "gamerule doMobSpawning false");
			command(sp, "weather clear");
			context.waitTicks(20);
			sp.getServer().runOnServer(MarketScenes::build);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// Un village : le pupitre du marché attend, le fermier est embauché.
			check(sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
				Town town = town(server);
				for (int i = 0; i < 3; i++) {
					TownService.immigrate(level, town);
				}
				for (BlockPos origin : new BlockPos[]{HOUSE, MARKET, FARM}) {
					RaidScenes.plaque(level, origin).ifPresent(p -> TownService.declareHouse(level, p, player));
				}
				return town.workers(Job.FARMER) == 1 && town.workers(Job.MERCHANT) == 0 ? null
						: "au village : " + town.workers(Job.FARMER) + " fermier, " + town.workers(Job.MERCHANT)
								+ " marchand ; " + town.houses();
			}));

			// La ville : le marché ouvre, avec l'annonce et les feux d'artifice.
			look(sp, 4, Y + 6, 10, 2, Y + 2, -10);
			context.runOnClient(mc -> hideGui(mc, false));
			check(sp.getServer().computeOnServer(server -> {
				Town town = town(server);
				town.forceTier(Optional.of(TownTier.TOWN));
				TownService.retier(server.overworld(), town);
				return town.workers(Job.MERCHANT) == 1 ? null : "pas de marchand en ville : " + town.residents();
			}));
			context.waitTicks(30);
			context.takeScreenshot("market_0_tier_up");
			context.runOnClient(mc -> hideGui(mc, true));

			// Le fermier n'a pas de houe : le marchand l'achète, puis vend le surplus de blé.
			look(sp, 2, Y + 12, 4, 2, Y, -12);
			context.waitTicks(10);
			CityScenes.film(context, sp, "market", 60, 20, server -> farmerHasHoe(server)
					&& town(server).chronicle().earnedToday(server.overworld().getGameTime()) > 0);
			waitOrExplain(sp, MarketScenes::farmerHasHoe, 2400, "le marchand n'a pas acheté de houe",
					server -> "émeraudes " + granary(server, Items.EMERALD) + ".");
			waitOrExplain(sp, server -> town(server).chronicle().earnedToday(server.overworld().getGameTime()) >= 3,
					2400, "le marchand n'a pas vendu le blé",
					server -> "blé " + granary(server, Items.WHEAT) + ", émeraudes " + granary(server, Items.EMERALD) + ".");
			// Il rapporte les émeraudes au grenier ; la réserve de blé y est restée.
			waitOrExplain(sp, server -> granary(server, Items.WHEAT) >= 32 && granary(server, Items.EMERALD) >= 4, 600,
					"les comptes ne tombent pas juste",
					server -> "blé " + granary(server, Items.WHEAT) + ", émeraudes " + granary(server, Items.EMERALD) + ".");

			// Grande ville : une mairie annexe étend le territoire.
			check(sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				Town town = town(server);
				town.forceTier(Optional.of(TownTier.CITY));
				TownService.retier(level, town);
				BlockPos beyond = ANNEX.east(40);
				if (town.contains(level.dimension(), beyond)) {
					return "trop tôt : " + beyond + " est déjà dans la ville";
				}
				command(server, "setblock %d %d %d minecities:town_annex[facing=west]".formatted(ANNEX.getX(),
						ANNEX.getY(), ANNEX.getZ()));
				TownService.placeAnnex(level, ANNEX, server.getPlayerList().getPlayers().getFirst(),
						new ItemStack(McItems.TOWN_ANNEX));
				return town.contains(level.dimension(), beyond) ? null : "l'annexe n'étend pas le territoire";
			}));
			look(sp, ANNEX.getX() - 3.5, Y + 2.2, ANNEX.getZ() + 2.5, ANNEX.getX() + 0.5, Y + 0.6, ANNEX.getZ() + 0.5);
			context.waitTicks(20);
			context.takeScreenshot("market_1_annex");

			// L'écran : le trésor, les annexes, le marché parmi les bâtiments.
			TownSnapshot snapshot = sp.getServer().computeOnServer(server -> TownNetwork.snapshot(server.overworld(),
					town(server), server.getPlayerList().getPlayers().getFirst()));
			check(snapshot.life().treasury() >= 4 && snapshot.life().annexes() == 1 ? null
					: "écran : trésor " + snapshot.life().treasury() + ", annexes " + snapshot.life().annexes());
			context.runOnClient(mc -> {
				hideGui(mc, false);
				mc.gui.setScreen(new TownHallScreen(snapshot));
			});
			context.waitTicks(5);
			context.takeScreenshot("market_2_summary");
			context.runOnClient(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.BUILDINGS));
			context.waitTicks(3);
			context.takeScreenshot("market_3_buildings");
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				hideGui(mc, true);
			});
		}
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static boolean farmerHasHoe(MinecraftServer server) {
		return citizens(server).stream().filter(c -> c.job().filter(j -> j.job() == Job.FARMER).isPresent())
				.anyMatch(c -> c.getMainHandItem().is(ItemTags.HOES));
	}

	private static int granary(MinecraftServer server, Item item) {
		return server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall
				? Stock.count(hall, s -> s.is(item)) : 0;
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -40; x <= 150; x++) {
			for (int z = -30; z <= 30; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 12; y++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		RaidScenes.paste(level, "house_large", HOUSE);
		RaidScenes.paste(level, "market", MARKET);
		RaidScenes.paste(level, "farm", FARM);
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			hall.setItem(0, new ItemStack(Items.BREAD, 32));
			hall.setItem(1, new ItemStack(Items.COOKED_BEEF, 16));
			hall.setItem(2, new ItemStack(Items.WHEAT, 64));
			hall.setItem(3, new ItemStack(Items.WHEAT, WHEAT - 64));
			hall.setItem(4, new ItemStack(Items.EMERALD, 2));
		}
		command(server, "gamemode spectator @p");
	}
}
