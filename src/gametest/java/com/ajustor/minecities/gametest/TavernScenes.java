package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.Happiness;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * La taverne (MC-58) : une taverne bâtie d'après son schéma et une grande maison ; le tavernier sert le
 * miel du grenier au comptoir, et la ville s'en réjouit. Au-delà du minimum d'habitants, la taverne
 * attire un nouveau venu quand le délai est passé (6 jours moins son niveau, au moins un), s'il reste un
 * lit libre.
 *
 * <p>{@code ./gradlew :26.2:runGameTest -PminecitiesGametest=tavern}
 */
public class TavernScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos TAVERN = new BlockPos(8, Y, 4);
	private static final BlockPos HOUSE = new BlockPos(-6, Y, -16);

	@Override
	public String name() {
		return "tavern";
	}

	@Override
	public void play(Stage stage) {
		Families.setMinimum(0);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.waitTicks(20);

		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			for (int x = -24; x <= 24; x++) {
				for (int z = -24; z <= 24; z++) {
					level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				}
			}
			RaidScenes.paste(level, "tavern_1", TAVERN);
			RaidScenes.paste(level, "house_large", HOUSE);
			command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			TownService.immigrate(level, town);
			TownService.immigrate(level, town);
			RaidScenes.plaque(level, HOUSE).ifPresent(p -> TownService.declareHouse(level, p, player));
			RaidScenes.plaque(level, TAVERN).ifPresent(p -> TownService.declareHouse(level, p, player));
			if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
				Stock.insert(hall, new ItemStack(Items.HONEY_BOTTLE, 8));
				Stock.insert(hall, new ItemStack(Items.BREAD, 16));
			}
			return town.workers(Job.TAVERNER) == 1 ? null : "pas de tavernier : " + town.houses();
		}));

		// Le tavernier sert ; la ville se réjouit.
		waitOrExplain(stage, server -> town(server).chronicle().tavernOpen(server.overworld().getGameTime()), 2400,
				"le tavernier n'a pas servi");
		waitOrExplain(stage, server -> citizens(server).stream()
				.allMatch(c -> c.moodDetail().factors().contains(new Happiness.Factor("tavern", 1))), 400,
				"la taverne ne réjouit personne");

		// La taverne attire un nouveau venu, une fois le délai passé.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			long now = level.getGameTime();
			Families.Growth growth = Families.growth(town, now);
			if (!growth.key().equals("tavern") || growth.days() < 1 || growth.level() != 1) {
				return "croissance : " + growth;
			}
			int before = town.citizens();
			if (!Families.attract(level, town, now + growth.days() * 24000L)) {
				return "la taverne n'a attiré personne";
			}
			return town.citizens() == before + 1 ? null : "pas de nouveau citoyen : " + town.citizens();
		}));
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}
}
