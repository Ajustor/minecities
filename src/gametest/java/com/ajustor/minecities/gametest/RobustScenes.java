package com.ajustor.minecities.gametest;

import com.ajustor.minecities.McConfig;
import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;
import java.util.UUID;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * La robustesse (v0.8), dans le vrai jeu : un hôtel de ville détruit (par une commande, comme par
 * une explosion) laisse la ville en attente, et la ville le retrouve quand le maire le repose ; un
 * citoyen supprimé sans mourir (une commande, un autre mod) finit rayé des registres.
 *
 * <p>{@code ./gradlew runClientGameTest -PminecitiesGametest=robust}
 */
public class RobustScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);

	@Override
	public String name() {
		return "robust";
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
			for (int x = -8; x <= 8; x++) {
				for (int z = -8; z <= 8; z++) {
					level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.STONE.defaultBlockState());
				}
			}
			command(server, "setblock 0 %d 0 minecities:town_hall".formatted(Y));
			TownService.found(level, HALL, server.getPlayerList().getPlayers().getFirst(),
					new ItemStack(McItems.TOWN_HALL));
			Town town = town(server).orElseThrow();
			TownService.immigrate(level, town);
			TownService.immigrate(level, town);
			return town.citizens() == 2 ? null : "pas de citoyens";
		}));

		// L'hôtel de ville détruit (pas par le maire) : la ville attend.
		stage.command("setblock 0 %d 0 minecraft:air destroy".formatted(Y));
		stage.waitTicks(5);
		check(stage.compute(server -> town(server).isEmpty() ? "la ville a disparu tout de suite"
				: town(server).get().orphanedAt().isPresent() ? null : "la ville n'attend pas son hôtel de ville"));
		// Le maire le repose au même endroit : la ville le retrouve.
		check(stage.compute(server -> {
			command(server, "setblock 0 %d 0 minecities:town_hall".formatted(Y));
			TownService.found(server.overworld(), HALL, server.getPlayerList().getPlayers().getFirst(),
					new ItemStack(McItems.TOWN_HALL));
			return town(server).filter(t -> t.orphanedAt().isEmpty() && t.citizens() == 2).isPresent() ? null
					: "la ville n'a pas retrouvé son hôtel de ville";
		}));
		check(stage.compute(server -> Towns.get(server).all().size() == 1 ? null
				: "une seconde ville a été fondée au lieu de retrouver la première"));

		// Un citoyen supprimé sans mourir : au bout du délai, il est rayé.
		int before = McConfig.get().missingCitizenMinutes;
		McConfig.get().missingCitizenMinutes = 1;
		UUID gone = stage.compute(server -> {
			CitizenEntity citizen = (CitizenEntity) server.overworld().getEntity(
					town(server).orElseThrow().residents().keySet().iterator().next());
			citizen.discard();
			return citizen.getUUID();
		});
		waitOrExplain(stage, server -> town(server).orElseThrow().resident(gone).isEmpty(), 2400,
				"le citoyen disparu est toujours inscrit");
		McConfig.get().missingCitizenMinutes = before;
		check(stage.compute(server -> town(server).orElseThrow().citizens() == 1 ? null
				: "l'autre citoyen a été rayé aussi"));

		// Un citoyen qui quitte la ville rend au grenier ce qu'il porte.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			CitizenEntity citizen = (CitizenEntity) level.getEntity(
					town(server).orElseThrow().residents().keySet().iterator().next());
			citizen.carry(new ItemStack(Items.DIAMOND, 3));
			citizen.leaveTown(level, null);
			return level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall
					&& Stock.count(hall, s -> s.is(Items.DIAMOND)) == 3 ? null
					: "le citoyen parti a emporté ce qu'il portait";
		}));

		// Un entonnoir sous l'hôtel de ville n'en vide pas le grenier.
		stage.run(server -> {
			if (server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
				hall.setItem(0, new ItemStack(Items.EMERALD, 5));
			}
			command(server, "setblock 0 %d 0 minecraft:hopper".formatted(Y - 1));
		});
		stage.waitTicks(60);
		check(stage.compute(server -> server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall
				&& hall.getItem(0).getCount() == 5 ? null : "un entonnoir a puisé dans le grenier"));
	}

	private static Optional<Town> town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL);
	}
}
