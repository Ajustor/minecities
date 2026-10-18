package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Mine;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Les quatre niveaux et les filières (v0.9), dans le vrai jeu : les quatre niveaux de la maison côte à
 * côte ; puis une mine, une forge, une boulangerie et une ferme au niveau 1. Le mineur creuse son
 * escalier ; le boulanger cuit le blé ; le forgeron fond le fer brut, puis forge la houe que réclame le
 * fermier.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=industry}
 */
public class IndustryScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	/** La mine à l'ouest : son escalier part vers l'est, dans le champ libre avant la forge. */
	private static final BlockPos MINE = new BlockPos(-36, Y, -18);
	private static final BlockPos SMITHY = new BlockPos(-8, Y, -18);
	private static final BlockPos BAKERY = new BlockPos(4, Y, -18);
	private static final BlockPos FARM = new BlockPos(16, Y, -18);
	private static final BlockPos HOUSE = new BlockPos(-6, Y, 10);
	/** Les quatre niveaux de la maison, en vitrine, au sud. */
	private static final int SHOWCASE_Z = 30;

	@Override
	public String name() {
		return "industry";
	}

	@Override
	public void play(Stage stage) {
		Families.setMinimum(0);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.waitTicks(20);
		stage.run(IndustryScenes::build);
		stage.client(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

		// Les quatre niveaux de la maison.
		stage.look(-2, Y + 16, SHOWCASE_Z - 12, -2, Y + 2, SHOWCASE_Z + 5);
		stage.client(mc -> hideGui(mc, true));
		stage.waitTicks(30);
		stage.shot("industry_0_house_levels");

		// La ville (forcée au palier Ville, pour la forge) et ses travailleurs.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			town.forceTier(Optional.of(TownTier.TOWN));
			TownService.retier(level, town);
			for (int i = 0; i < 4; i++) {
				TownService.immigrate(level, town);
			}
			for (BlockPos origin : new BlockPos[]{MINE, SMITHY, BAKERY, FARM, HOUSE}) {
				RaidScenes.plaque(level, origin).ifPresent(p -> TownService.declareHouse(level, p, player));
			}
			return town.workers(Job.MINER) == 1 && town.workers(Job.SMITH) == 1 && town.workers(Job.BAKER) == 1
					&& town.workers(Job.FARMER) == 1 ? null : "métiers : " + town.residents() + " ; " + town.houses();
		}));
		stage.look(-6, Y + 16, 2, -6, Y, -16);
		stage.waitTicks(10);
		stage.shot("industry_1_workshops");

		// Le boulanger cuit, le forgeron fond le fer puis forge la houe du fermier.
		stage.film("industry", 40, 20, server -> granary(server, Items.BREAD) > 0
				&& farmerHasHoe(server));
		waitOrExplain(stage, server -> granary(server, Items.BREAD) >= 5, 2400, "le boulanger n'a pas cuit de pain",
				server -> "blé " + granary(server, Items.WHEAT) + ".");
		waitOrExplain(stage, IndustryScenes::farmerHasHoe, 3600, "le forgeron n'a pas forgé de houe",
				server -> "grenier " + contents(server) + ", besoins " + citizens(server).stream()
						.map(c -> c.getName().getString() + "=" + c.hasNeed("tool") + "/" + c.lastNeed()
								.map(net.minecraft.network.chat.Component::getString).orElse("-")).toList() + ".");

		// Le mineur creuse son escalier, et l'éclaire.
		waitOrExplain(stage, server -> mine(server).filter(m -> m.face().getY() <= m.entrance().getY() - 8).isPresent(),
				3600, "le mineur n'a pas creusé", server -> "galerie " + mine(server) + ".");
		// Il a suivi le filon de fer croisé en chemin.
		waitOrExplain(stage, server -> citizens(server).stream().anyMatch(c -> c.inventory().stream()
						.anyMatch(st -> st.is(Items.RAW_IRON))) || granary(server, Items.RAW_IRON) > 0, 1200,
				"le mineur n'a pas suivi le filon de fer", server -> "galerie " + mine(server) + ".");
		check(stage.compute(server -> {
			Mine mine = mine(server).orElseThrow();
			BlockPos step = mine.entrance().east(3).below(3);
			return server.overworld().getBlockState(step).isAir() && server.overworld().getBlockState(step.above()).isAir()
					? null : "l'escalier n'est pas dégagé en " + step + " : " + server.overworld().getBlockState(step);
		}));
		BlockPos entrance = stage.compute(server -> mine(server).orElseThrow().entrance());
		stage.look(entrance.getX() + 0.5, entrance.getY() + 1.2, entrance.getZ() + 0.5, entrance.getX() + 8,
				entrance.getY() - 7, entrance.getZ() + 0.5);
		stage.waitTicks(20);
		stage.shot("industry_2_mine");

		// Un chantier réclame une lanterne (niveau 3) : le forgeron la forge, avec le fer, le charbon et un
		// bâton du grenier.
		stage.run(server -> {
			if (server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
				com.ajustor.minecities.town.Stock.insert(hall, new ItemStack(Items.IRON_INGOT, 2));
				com.ajustor.minecities.town.Stock.insert(hall, new ItemStack(Items.COAL, 2));
				com.ajustor.minecities.town.Stock.insert(hall, new ItemStack(Items.STICK, 2));
			}
			citizens(server).getFirst().need("material:" + Items.LANTERN,
					net.minecraft.network.chat.Component.literal("il me faut une lanterne"));
		});
		waitOrExplain(stage, server -> granary(server, Items.LANTERN) > 0, 2400,
				"le forgeron n'a pas forgé la lanterne du chantier", server -> "grenier " + contents(server) + ".");
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static Optional<Mine> mine(MinecraftServer server) {
		return citizens(server).stream().filter(c -> c.job().filter(j -> j.job() == Job.MINER).isPresent())
				.findFirst().flatMap(c -> town(server).mine(c.job().get().station()));
	}

	private static boolean farmerHasHoe(MinecraftServer server) {
		return citizens(server).stream().filter(c -> c.job().filter(j -> j.job() == Job.FARMER).isPresent())
				.anyMatch(c -> c.getMainHandItem().is(Items.IRON_HOE) || c.getMainHandItem().is(Items.STONE_HOE));
	}

	private static String contents(MinecraftServer server) {
		if (!(server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall)) {
			return "?";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < hall.getContainerSize(); i++) {
			if (!hall.getItem(i).isEmpty()) {
				out.append(hall.getItem(i)).append(' ');
			}
		}
		return out.toString();
	}

	private static int granary(MinecraftServer server, Item item) {
		return server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall
				? Stock.count(hall, s -> s.is(item)) : 0;
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -44; x <= 44; x++) {
			for (int z = -30; z <= SHOWCASE_Z + 14; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = Y; y < Y + 14; y++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		// La vitrine des niveaux (sans plaque déclarée : elles ne comptent pas dans la ville).
		for (int n = 1; n <= 4; n++) {
			// De gauche à droite pour qui regarde vers le sud : de l'est à l'ouest.
			RaidScenes.paste(level, "house_" + n, new BlockPos(18 - 14 * (n - 1), Y, SHOWCASE_Z));
		}
		// De la vraie roche sous la mine, et un filon de fer sur le chemin de l'escalier.
		// (Le treuil est à z = -12 : l'escalier descend vers l'est le long de cette ligne, depuis x = -27.)
		for (int x = -29; x <= -6; x++) {
			for (int z = -15; z <= -9; z++) {
				for (int y = Y - 30; y <= Y - 2; y++) {
					level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
				}
			}
		}
		for (BlockPos ore : new BlockPos[]{new BlockPos(-22, Y - 4, -11), new BlockPos(-22, Y - 5, -11),
				new BlockPos(-21, Y - 5, -11), new BlockPos(-21, Y - 5, -10)}) {
			level.setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
		}
		level.setBlockAndUpdate(new BlockPos(-19, Y - 8, -13), Blocks.COAL_ORE.defaultBlockState());
		RaidScenes.paste(level, "mine_1", MINE);
		RaidScenes.paste(level, "smithy_1", SMITHY);
		RaidScenes.paste(level, "bakery_1", BAKERY);
		RaidScenes.paste(level, "farm_1", FARM);
		RaidScenes.paste(level, "house_2", HOUSE);
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
			hall.setItem(0, new ItemStack(Items.COOKED_BEEF, 32));
			hall.setItem(1, new ItemStack(Items.WHEAT, 30));
			hall.setItem(2, new ItemStack(Items.RAW_IRON, 8));
			hall.setItem(3, new ItemStack(Items.STICK, 16));
			hall.setItem(4, new ItemStack(Items.IRON_PICKAXE));
			hall.setItem(5, new ItemStack(Items.COAL, 8));
		}
		command(server, "gamemode spectator @p");
	}
}
