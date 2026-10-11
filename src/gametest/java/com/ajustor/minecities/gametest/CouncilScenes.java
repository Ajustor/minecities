package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Happiness;
import com.ajustor.minecities.client.screen.TownHallScreen;
import com.ajustor.minecities.network.TownNetwork;
import com.ajustor.minecities.network.TownSnapshot;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Decree;
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
import static com.ajustor.minecities.gametest.CityScenes.look;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * La mairie et la maîtrise (v1.1), dans le vrai jeu : le forgeron d'une forge de niveau 4 habille le
 * garde de fer de pied en cap ; le tanneur tanne ; le maire décrète un jour férié (l'onglet Mairie) ;
 * deux villes signent un accord et une caravane porte le pain de l'une à l'autre.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=council}
 */
public class CouncilScenes implements FabricClientGameTest {
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
	public void runTest(ClientGameTestContext context) {
		if (!CityScenes.selected("council")) {
			return;
		}
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			Families.setMinimum(0);
			command(sp, "time set 1000");
			command(sp, "gamerule doDaylightCycle false");
			command(sp, "gamerule doMobSpawning false");
			command(sp, "weather clear");
			command(sp, "forceload add 184 -16 216 16");
			context.waitTicks(20);
			sp.getServer().runOnServer(CouncilScenes::build);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			check(sp.getServer().computeOnServer(server -> {
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
				return town.buildingLevel(RaidScenes.plaque(level, SMITHY).orElseThrow()) == 4 ? null
						: "la forge n'est pas de niveau 4";
			}));

			// Le maître forgeron habille le garde de fer, de pied en cap ; le tanneur tanne.
			waitOrExplain(sp, server -> citizens(server).stream().filter(CitizenEntity::isGuard).anyMatch(g ->
							g.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
									&& g.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS)
									&& g.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS)), 4800,
					"le garde n'est pas armé de pied en cap", server -> "lingots " + granary(server, HALL, Items.IRON_INGOT) + ".");
			waitOrExplain(sp, server -> granary(server, HALL, Items.LEATHER) >= 2, 2400, "le tanneur n'a pas tanné");
			sp.getServer().runOnServer(server -> citizens(server).stream().filter(CitizenEntity::isGuard).findFirst()
					.ifPresent(g -> {
						g.setNoAi(true);
						g.snapTo(0.5, Y, 6.5, 180, 0);
						g.setYHeadRot(180);
						g.setYBodyRot(180);
					}));
			look(sp, 0.5, Y + 1.8, 3.5, 0.5, Y + 1.2, 6.5);
			context.runOnClient(mc -> hideGui(mc, true));
			context.waitTicks(10);
			context.takeScreenshot("council_0_guard");
			sp.getServer().runOnServer(server -> citizens(server).forEach(c -> c.setNoAi(false)));

			// Le jour férié : la ville se réjouit (et ne travaille pas).
			sp.getServer().runOnServer(server -> TownService.decree(server.overworld(), town(server), Decree.HOLIDAY));
			waitOrExplain(sp, server -> citizens(server).stream().filter(c -> c.townId().filter(town(server).id()::equals)
					.isPresent()).allMatch(c -> c.moodDetail().factors().contains(new Happiness.Factor("decrees_good", 1))),
					400, "le jour férié ne réjouit personne");

			// Un accord de commerce : la voisine n'a pas de pain, la ville en a trop.
			sp.getServer().runOnServer(server -> {
				Town town = town(server);
				Town other = Towns.get(server).byHall(server.overworld().dimension(), OTHER_HALL).orElseThrow();
				town.addPartner(other.id());
				other.addPartner(town.id());
			});
			waitOrExplain(sp, server -> granary(server, OTHER_HALL, Items.BREAD) > 0, 2600, "pas de caravane",
					server -> "pain " + granary(server, HALL, Items.BREAD) + " / " + granary(server, OTHER_HALL, Items.BREAD)
							+ ", émeraudes " + granary(server, OTHER_HALL, Items.EMERALD) + ".");
			check(sp.getServer().computeOnServer(server -> granary(server, OTHER_HALL, Items.EMERALD) < 20
					? null : "la voisine n'a pas payé la caravane"));

			// L'onglet Mairie : le maire, les décrets, l'accord.
			TownSnapshot snapshot = sp.getServer().computeOnServer(server -> TownNetwork.snapshot(server.overworld(),
					town(server), server.getPlayerList().getPlayers().getFirst()));
			check(snapshot.council().partners().size() == 1 && snapshot.council().open().getFirst() ? null
					: "la mairie ne montre pas l'accord : " + snapshot.council());
			context.runOnClient(mc -> {
				hideGui(mc, false);
				mc.gui.setScreen(new TownHallScreen(snapshot));
			});
			context.waitTicks(3);
			context.runOnClient(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.COUNCIL));
			context.waitTicks(3);
			context.takeScreenshot("council_1_tab");
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				hideGui(mc, true);
			});
		}
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
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
}
