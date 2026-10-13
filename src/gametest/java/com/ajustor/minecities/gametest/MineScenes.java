package com.ajustor.minecities.gametest;

import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.client.screen.StationScreen;
import com.ajustor.minecities.network.StationNetwork;
import com.ajustor.minecities.network.StationSnapshot;
import com.ajustor.minecities.registry.McEntities;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Mine;
import com.ajustor.minecities.town.Mines;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;

/**
 * Le puits du mineur (MC-20) : ce qu'il peut donner (les minerais sur son chemin, estimés dès le
 * treuil posé) ; le chemin de remontée, vérifié et réparé avant de descendre, et refusé s'il ne se
 * répare pas ; la galerie épuisée ; le puits rebouché, pas tant qu'il reste du minerai ni sur
 * quelqu'un. La galerie est taillée à la main dans un bloc de pierre, pour que l'essai soit rapide.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=mine}
 */
public class MineScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos MINE = new BlockPos(-30, Y, -18);
	/** La galerie : six marches taillées. */
	private static final int STEPS = 6;

	@Override
	public String name() {
		return "mine";
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

		// La ville, l'atelier du mineur et son treuil.
		BlockPos winch = stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer mayor = mayor(server);
			TownService.found(level, HALL, mayor, new ItemStack(McItems.TOWN_HALL));
			RaidScenes.paste(level, "mine_1", MINE);
			RaidScenes.plaque(level, MINE).ifPresent(p -> TownService.declareHouse(level, p, mayor));
			return town(server).houses().stream().flatMap(h -> h.workstations().stream())
					.filter(w -> w.job() == Job.MINER).findFirst().orElseThrow().pos();
		});

		// Sous l'entrée prévue : un bloc de pierre, l'escalier taillé, un filon de fer et du charbon.
		Mine planned = stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			BlockPos workshop = town.houses().stream().filter(h -> h.workstations().stream()
					.anyMatch(w -> w.pos().equals(winch))).findFirst().orElseThrow().plaque();
			// L'entrée prévue, posée au niveau du sol de la scène (la hauteur du monde n'y est pour rien).
			Mine mine = Mine.open(winch, new BlockPos(Mines.plan(level, town, winch, workshop).entrance().getX(), Y,
					winch.getZ()));
			BlockPos e = mine.entrance();
			command(server, "fill %d %d %d %d %d %d minecraft:stone".formatted(e.getX() - 2, e.getY() - STEPS - 3,
					e.getZ() - 3, e.getX() + STEPS + 4, e.getY() - 1, e.getZ() + 3));
			for (int k = 1; k <= STEPS; k++) {
				BlockPos step = e.relative(Direction.EAST, k).below(k);
				for (int h = 0; h < 3; h++) {
					level.setBlockAndUpdate(step.above(h), Blocks.AIR.defaultBlockState());
				}
			}
			// Le filon, dans la paroi nord des marches 2 et 3 ; du charbon dans la paroi sud de la 4.
			level.setBlockAndUpdate(e.relative(Direction.EAST, 2).below(2).north(), Blocks.IRON_ORE.defaultBlockState());
			level.setBlockAndUpdate(e.relative(Direction.EAST, 3).below(3).north(), Blocks.DEEPSLATE_IRON_ORE.defaultBlockState());
			level.setBlockAndUpdate(e.relative(Direction.EAST, 4).below(4).south(), Blocks.COAL_ORE.defaultBlockState());
			// La galerie, creusée jusqu'à la sixième marche.
			Mine dug = mine;
			for (int k = 1; k <= STEPS; k++) {
				dug = dug.advanced(e.relative(Direction.EAST, k).below(k));
			}
			town.putMine(dug);
			return dug;
		});

		// Ce que le puits peut donner : le fer (les deux sortes ensemble) et le charbon ; dans l'interface.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			Map<String, Integer> ores = Mines.prospect(level, town, planned);
			StationSnapshot s = StationNetwork.snapshot(level, winch, mayor(server)).orElseThrow();
			List<String> wrong = new ArrayList<>();
			if (ores.getOrDefault("iron_ore", 0) != 2 || ores.getOrDefault("coal_ore", 0) != 1) {
				BlockPos iron = planned.entrance().relative(Direction.EAST, 2).below(2).north();
				wrong.add("prospection : " + ores + " (entrée " + planned.entrance().toShortString() + ", fer "
						+ level.getBlockState(iron) + ", dans la ville " + town.contains(level.dimension(), iron)
						+ ", chargé " + level.isLoaded(iron) + ", bas " + Mines.bottom(level, planned).toShortString() + ")");
			}
			if (!s.mine().equals("digging") || s.ores().size() != 2 || !s.manager()) {
				wrong.add("interface : " + s.mine() + " " + s.ores());
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Le chemin de remontée : intact ; un éboulement se répare ; de l'eau ne se répare pas.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			List<String> wrong = new ArrayList<>();
			if (Mines.broken(level, planned).isPresent()) {
				wrong.add("chemin intact jugé coupé : " + Mines.broken(level, planned));
			}
			BlockPos third = planned.entrance().relative(Direction.EAST, 3).below(3);
			level.setBlockAndUpdate(third, Blocks.GRAVEL.defaultBlockState());
			if (!Mines.broken(level, planned).equals(Optional.of(third)) || !Mines.repair(level, third)
					|| Mines.broken(level, planned).isPresent()) {
				wrong.add("l'éboulement : " + Mines.broken(level, planned));
			}
			BlockPos fourth = planned.entrance().relative(Direction.EAST, 4).below(4);
			level.setBlockAndUpdate(fourth, Blocks.WATER.defaultBlockState());
			if (Mines.repair(level, fourth)) {
				wrong.add("l'eau s'est réparée");
			}
			level.setBlockAndUpdate(fourth, Blocks.AIR.defaultBlockState());
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Fermer : pas tant qu'il reste du minerai ; pas sur quelqu'un ; puis le puits est rebouché.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			List<String> wrong = new ArrayList<>();
			if (!Mines.closeRefusal(level, town, planned).equals(Optional.of("not_exhausted"))) {
				wrong.add("fermé avec du minerai : " + Mines.closeRefusal(level, town, planned));
			}
			// Le minerai extrait : la galerie est épuisée.
			BlockPos e = planned.entrance();
			level.setBlockAndUpdate(e.relative(Direction.EAST, 2).below(2).north(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(e.relative(Direction.EAST, 3).below(3).north(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(e.relative(Direction.EAST, 4).below(4).south(), Blocks.STONE.defaultBlockState());
			Mine dry = planned.extracted(Map.of("iron_ore", 2, "coal_ore", 1));
			if (!Mines.prospect(level, town, dry).isEmpty()) {
				wrong.add("reste du minerai : " + Mines.prospect(level, town, dry));
			}
			dry = dry.drained();
			town.putMine(dry);
			// Quelqu'un au fond.
			CitizenEntity down = McEntities.CITIZEN.create(level, EntitySpawnReason.COMMAND);
			BlockPos fifth = e.relative(Direction.EAST, 5).below(5);
			down.snapTo(fifth.getX() + 0.5, fifth.getY(), fifth.getZ() + 0.5, 0, 0);
			level.addFreshEntity(down);
			if (!Mines.close(level, town, dry).equals(Optional.of("occupied"))) {
				wrong.add("rebouché sur quelqu'un");
			}
			down.discard();
			StationSnapshot before = StationNetwork.snapshot(level, winch, mayor(server)).orElseThrow();
			if (!before.mine().equals("exhausted") || before.ores().stream().anyMatch(o -> o.remaining() > 0)
					|| before.ores().stream().mapToInt(StationSnapshot.Ore::mined).sum() != 3) {
				wrong.add("l'interface épuisée : " + before.mine() + " " + before.ores());
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// En image : l'interface du treuil, puits épuisé.
		StationSnapshot shown = stage.compute(server -> StationNetwork.snapshot(server.overworld(), winch, mayor(server))
				.orElseThrow());
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new StationScreen(shown));
		});
		stage.waitTicks(3);
		stage.shot("mine_0_exhausted");
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});

		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			Mine dry = town.mine(winch).orElseThrow();
			Optional<String> refusal = Mines.close(level, town, dry);
			BlockPos third = dry.entrance().relative(Direction.EAST, 3).below(3);
			boolean filled = level.getBlockState(third).is(Blocks.COBBLESTONE)
					&& level.getBlockState(third.above()).is(Blocks.COBBLESTONE);
			Mine sealed = town.mine(winch).orElseThrow();
			return refusal.isEmpty() && filled && sealed.closed() && sealed.exhausted()
					&& StationNetwork.snapshot(level, winch, mayor(server)).orElseThrow().mine().equals("closed") ? null
					: "rebouché : " + refusal + ", comblé " + filled + ", " + sealed.closed();
		}));
	}

	private static ServerPlayer mayor(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElse(null);
	}
}
