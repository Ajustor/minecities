package com.ajustor.minecities.gametest;

import com.ajustor.minecities.field.FieldMarkerItem;
import com.ajustor.minecities.field.FieldNetwork;
import com.ajustor.minecities.registry.McBlocks;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Field;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Les champs tracés au piquet de champ : un champ de carottes sur de l'herbe, loin de la ferme, avec
 * des herbes folles et un pied de blé mûr. Le fermier va chercher les carottes au grenier, arrache
 * l'herbe, laboure et sème tout le champ ; le blé, une fois récolté, laisse place aux carottes. Puis
 * le maire change la culture et supprime le champ ; un champ trop grand est refusé.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=fields}
 */
public class FieldScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos FARM = new BlockPos(8, Y, 4);
	private static final BlockPos HOUSE = new BlockPos(-16, Y, 4);
	/** Le champ : 5 x 5, à plus de 16 blocs du composteur (hors de son rayon d'avant). */
	private static final BlockPos CORNER_A = new BlockPos(-14, Y - 1, -20);
	private static final BlockPos CORNER_B = new BlockPos(-10, Y - 1, -16);
	private static final BlockPos WHEAT = new BlockPos(-12, Y, -18);

	@Override
	public String name() {
		return "fields";
	}

	@Override
	public void play(Stage stage) {
		com.ajustor.minecities.town.Families.setMinimum(0);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.waitTicks(20);
		stage.run(FieldScenes::build);

		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			TownService.declareHouse(level, plaque(level, HOUSE), player);
			TownService.immigrate(level, town);
			BlockPos farm = plaque(level, FARM);
			TownService.declareHouse(level, farm, player);
			Optional<BlockPos> chest = town.house(farm).flatMap(h -> h.containers().stream().findFirst());
			if (chest.isEmpty() || !(level.getBlockEntity(chest.get()) instanceof Container c)) {
				return "pas de coffre dans la ferme : " + town.houses();
			}
			c.setItem(0, new ItemStack(Items.IRON_HOE));
			if (!(level.getBlockEntity(HALL) instanceof Container hall)) {
				return "pas de grenier";
			}
			Stock.insert(hall, new ItemStack(Items.CARROT, 48));
			town.addField(Field.between(CORNER_A, CORNER_B, Field.Crop.CARROTS));
			// Un champ de 41 de côté est refusé, comme un champ qui empiète sur celui-ci.
			Field big = Field.between(CORNER_A, CORNER_A.offset(40, 0, 0), Field.Crop.WHEAT);
			Field across = Field.between(CORNER_B, CORNER_B.offset(3, 0, 3), Field.Crop.WHEAT);
			if (FieldMarkerItem.problem(level, town, big).isEmpty()
					|| FieldMarkerItem.problem(level, town, across).isEmpty()) {
				return "un champ trop grand ou qui empiète a été accepté";
			}
			return null;
		}));

		// Tout le champ en carottes, le blé compris (récolté puis remplacé).
		waitOrExplain(stage, server -> carrots(server) == 25, 6000, "le champ n'a pas été semé de carottes",
				server -> carrots(server) + " carottes sur 25, blé " + server.overworld().getBlockState(WHEAT)
						+ ", champs " + town(server).fields() + " ;");

		// En images : le champ et son contour (le maire tient le piquet), puis le choix de la culture.
		stage.run(server -> server.getPlayerList().getPlayers().getFirst().setItemInHand(
				net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(McItems.FIELD_MARKER)));
		stage.look(-6, Y + 7, -10, -12, Y, -18);
		stage.client(mc -> CityScenes.hideGui(mc, true));
		stage.waitTicks(40);
		stage.shot("fields_1_carrots");
		FieldNetwork.FieldSnapshot snapshot = stage.compute(server -> {
			Field field = town(server).fields().getFirst();
			return new FieldNetwork.FieldSnapshot(field.id().toString(), town(server).name(), field.width(),
					field.depth(), field.crop().serializedName());
		});
		stage.client(mc -> mc.gui.setScreen(new com.ajustor.minecities.client.screen.FieldScreen(snapshot)));
		stage.waitTicks(10);
		stage.shot("fields_2_menu");
		stage.client(mc -> mc.gui.setScreen(null));

		// Le maire change la culture (une culture inconnue est refusée), puis supprime le champ.
		check(stage.compute(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			Town town = town(server);
			Field field = town.fields().getFirst();
			if (!FieldNetwork.act(player, field.id().toString(), "crop:potatoes")
					|| town.field(field.id()).map(Field::crop).orElse(null) != Field.Crop.POTATOES) {
				return "la culture n'a pas changé : " + town.fields();
			}
			if (FieldNetwork.act(player, field.id().toString(), "crop:roses")) {
				return "une culture inconnue a été acceptée";
			}
			if (!FieldNetwork.act(player, field.id().toString(), "delete") || !town.fields().isEmpty()) {
				return "le champ n'a pas été supprimé : " + town.fields();
			}
			return null;
		}));
	}

	private static int carrots(MinecraftServer server) {
		ServerLevel level = server.overworld();
		int count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(CORNER_A.above(), CORNER_B.above())) {
			if (level.getBlockState(pos).getBlock() == Blocks.CARROTS
					&& level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
				count++;
			}
		}
		return count;
	}

	private static BlockPos plaque(ServerLevel level, BlockPos origin) {
		for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(20, 4, 12))) {
			if (level.getBlockState(pos).is(McBlocks.HOUSE_PLAQUE)) {
				return pos.immutable();
			}
		}
		throw new AssertionError("pas de plaque près de " + origin);
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -30; x <= 32; x++) {
			for (int z = -30; z <= 24; z++) {
				level.setBlock(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				for (int y = Y; y < Y + 14; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
					}
				}
			}
		}
		RaidScenes.paste(level, "farm_1", FARM);
		RaidScenes.paste(level, "house_1", HOUSE);
		command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
		// Des herbes folles dans le champ, et un pied de blé mûr sur une terre labourée.
		for (BlockPos weed : new BlockPos[]{new BlockPos(-14, Y, -20), new BlockPos(-11, Y, -17), new BlockPos(-10, Y, -16)}) {
			level.setBlockAndUpdate(weed, Blocks.SHORT_GRASS.defaultBlockState());
		}
		level.setBlockAndUpdate(WHEAT.below(), Blocks.FARMLAND.defaultBlockState());
		CropBlock wheat = (CropBlock) Blocks.WHEAT;
		level.setBlockAndUpdate(WHEAT, wheat.getStateForAge(wheat.getMaxAge()));
	}
}
