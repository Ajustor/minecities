package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.registry.McBlocks;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Raids;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
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
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.look;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Un raid, dans le vrai jeu : une caserne (deux gardes), une tour de garde (un archer), l'atelier
 * d'un inventeur (de quoi faire un golem de fer) et une grande maison, posées d'après leurs schémas ;
 * les gardes s'équipent au grenier, l'archer monte à son poste ; un raid de pillards arrive du bord du
 * territoire ; les civils se mettent à l'abri, l'inventeur assemble un golem, les défenseurs
 * repoussent les pillards, et la victoire rapporte des émeraudes. Le raid est filmé.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=raid}
 */
public class RaidScenes implements FabricClientGameTest {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos BARRACKS = new BlockPos(-18, Y, 4);
	private static final BlockPos TOWER = new BlockPos(6, Y, 4);
	private static final BlockPos HOUSE = new BlockPos(-6, Y, -16);
	private static final BlockPos WORKSHOP = new BlockPos(8, Y, -16);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!CityScenes.selected("raid")) {
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
			sp.getServer().runOnServer(RaidScenes::build);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// La ville, ses bâtiments posés d'après les schémas, et ses défenseurs.
			check(sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
				Town town = town(server);
				for (int i = 0; i < 7; i++) {
					TownService.immigrate(level, town);
				}
				for (BlockPos[] building : new BlockPos[][]{{BARRACKS}, {TOWER}, {HOUSE}, {WORKSHOP}}) {
					plaque(level, building[0]).ifPresent(p -> TownService.declareHouse(level, p, player));
				}
				int guards = town.workers(Job.GUARD);
				int archers = town.workers(Job.ARCHER);
				int inventors = town.workers(Job.INVENTOR);
				return guards == 2 && archers == 1 && inventors == 1 ? null
						: "défenseurs mal embauchés : " + guards + " gardes, " + archers + " archers, " + inventors
								+ " inventeurs ; " + town.houses();
			}));
			waitOrExplain(sp, server -> citizens(server).stream().filter(CitizenEntity::isGuard)
							.allMatch(c -> c.getMainHandItem().is(Items.IRON_SWORD)
									&& c.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)),
					1200, "les gardes ne se sont pas équipés");
			waitOrExplain(sp, server -> citizens(server).stream().filter(c -> c.job()
							.filter(j -> j.job() == Job.ARCHER).isPresent())
							.allMatch(c -> c.getY() >= Y + 9), 1200, "l'archer n'est pas monté à son poste");

			look(sp, -2, Y + 14, -24, -2, Y, 6);
			context.runOnClient(mc -> hideGui(mc, false));
			context.waitTicks(20);
			context.takeScreenshot("raid_0_town");

			// Le raid : on le filme jusqu'à la victoire.
			check(sp.getServer().computeOnServer(server -> Raids.start(server.overworld(), town(server)) ? null
					: "le raid n'a pas commencé"));
			context.waitTicks(40);
			CityScenes.film(context, sp, "raid", 360, 20, server -> !Raids.underAttack(town(server).id()));
			waitOrExplain(sp, server -> !Raids.underAttack(town(server).id()), 6000, "le raid n'est pas fini",
					server -> "vague " + Raids.progress(town(server).id())[0] + "/" + Raids.progress(town(server).id())[1]
							+ ", " + Raids.raiders(town(server).id()).size() + " pillards.");
			check(sp.getServer().computeOnServer(server -> granary(server, Items.EMERALD) > 0 ? null
					: "pas d'émeraudes : le raid a-t-il été repoussé ?"));
			context.waitTicks(20);
			context.takeScreenshot("raid_1_victory");
			context.runOnClient(mc -> hideGui(mc, true));
		}
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static int granary(MinecraftServer server, net.minecraft.world.item.Item item) {
		return server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall ? Stock.count(hall, s -> s.is(item)) : 0;
	}

	/** La plaque d'un bâtiment posé d'après son schéma. */
	static Optional<BlockPos> plaque(ServerLevel level, BlockPos origin) {
		for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(9, 3, 9))) {
			if (level.getBlockState(pos).is(McBlocks.HOUSE_PLAQUE)) {
				return Optional.of(pos.immutable());
			}
		}
		return Optional.empty();
	}

	/** Pose d'un coup un bâtiment d'après son schéma (sans bâtisseur). */
	static void paste(ServerLevel level, String name, BlockPos origin) {
		paste(level, name, origin, BuildStyle.PLAINS);
	}

	/** Pose d'un coup un bâtiment d'après son schéma, dans ce style (sans bâtisseur). */
	static void paste(ServerLevel level, String name, BlockPos origin, BuildStyle style) {
		Blueprint blueprint = Blueprints.get(level.getServer(), Identifier.fromNamespaceAndPath("minecities", name))
				.orElseThrow(() -> new AssertionError("schéma introuvable : " + name));
		for (BuildPlan.Placement p : BuildPlan.of(blueprint, style, origin, Rotation.NONE)) {
			level.setBlock(p.pos(), p.state(), 3);
			p.state().getBlock().setPlacedBy(level, p.pos(), p.state(), null, ItemStack.EMPTY);
		}
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -48; x <= 48; x++) {
			for (int z = -48; z <= 48; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 14; y++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		paste(level, "barracks", BARRACKS);
		paste(level, "guard_tower", TOWER);
		paste(level, "house_large", HOUSE);
		paste(level, "inventor_workshop", WORKSHOP);
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			hall.setItem(0, new ItemStack(Items.BREAD, 64));
			hall.setItem(1, new ItemStack(Items.IRON_SWORD));
			hall.setItem(2, new ItemStack(Items.IRON_SWORD));
			hall.setItem(3, new ItemStack(Items.IRON_CHESTPLATE));
			hall.setItem(4, new ItemStack(Items.IRON_CHESTPLATE));
			hall.setItem(5, new ItemStack(Items.BOW));
			hall.setItem(6, new ItemStack(Items.ARROW, 64));
			hall.setItem(7, new ItemStack(Items.IRON_BLOCK, 4));
			hall.setItem(8, new ItemStack(Items.CARVED_PUMPKIN));
		}
		command(server, "gamemode spectator @p");
	}
}
