package com.ajustor.minecities.gametest;

import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Builds;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.citizen.Happiness;
import com.ajustor.minecities.client.screen.TownHallScreen;
import com.ajustor.minecities.network.TownNetwork;
import com.ajustor.minecities.network.TownSnapshot;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Monument;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.TownTier;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Les monuments (MC-11) : la statue, la fontaine et le beffroi s'ouvrent à la mégalopole ; un seul
 * de chaque par ville ; bâtis, ils rendent les citoyens plus heureux et donnent de l'attrait à la
 * ville ; ils se sauvegardent et se montrent dans l'onglet Bâtiments. Les schémas coûtent de la
 * pierre polie, du quartz et de l'or.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=monuments}
 */
public class MonumentScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos STATUE = new BlockPos(14, Y, -6);
	private static final BlockPos FOUNTAIN = new BlockPos(13, Y, 4);
	private static final BlockPos BELFRY = new BlockPos(25, Y, -1);

	@Override
	public String name() {
		return "monuments";
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
		stage.run(server -> TownService.found(server.overworld(), HALL, mayor(server),
				new ItemStack(McItems.TOWN_HALL)));

		// Les schémas : des monuments, ouverts à la mégalopole, faits de matériaux nobles.
		check(stage.compute(server -> {
			List<String> wrong = new ArrayList<>();
			for (Monument monument : Monument.values()) {
				Optional<Blueprint> blueprint = Blueprints.get(server, monument.blueprint());
				if (blueprint.isEmpty()) {
					wrong.add("pas de schéma " + monument);
					continue;
				}
				Blueprint b = blueprint.get();
				Map<net.minecraft.world.item.Item, Integer> cost = BuildPlan.materials(BuildPlan.of(b, BuildStyle.PLAINS,
						BlockPos.ZERO, Rotation.NONE));
				if (!b.kind().equals("monument") || b.tier() != TownTier.MEGALOPOLIS || b.width() > 11 || b.depth() > 11
						|| !cost.containsKey(Items.GOLD_BLOCK) || !cost.containsKey(Items.QUARTZ_PILLAR)) {
					wrong.add(monument + " : " + b.kind() + " " + b.tier() + " " + b.width() + "x" + b.depth() + " "
							+ cost);
				}
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Fermés avant la mégalopole, ouverts ensuite ; un seul de chaque.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			Blueprint statue = blueprint(server, Monument.STATUE);
			List<String> wrong = new ArrayList<>();
			town.forceTier(Optional.of(TownTier.CITY));
			TownService.retier(level, town);
			Optional<Component> locked = Builds.check(level, town, statue, STATUE, Rotation.NONE);
			if (locked.isEmpty() || !text(locked.get()).contains("megalopolis")) {
				wrong.add("ouverte à la cité : " + locked.map(MonumentScenes::text));
			}
			town.forceTier(Optional.of(TownTier.MEGALOPOLIS));
			TownService.retier(level, town);
			Optional<Component> refusal = Builds.order(level, town, statue, BuildStyle.PLAINS, STATUE, Rotation.NONE);
			if (refusal.isPresent()) {
				wrong.add("la statue refusée à la mégalopole : " + text(refusal.get()));
			}
			Optional<Component> twice = Builds.check(level, town, statue, STATUE.offset(0, 0, -12), Rotation.NONE);
			if (twice.isEmpty() || !text(twice.get()).contains("monument_once")) {
				wrong.add("une deuxième statue : " + twice.map(MonumentScenes::text));
			}
			if (!town.monuments().isEmpty()) {
				wrong.add("un monument avant d'être bâti : " + town.monuments());
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// La statue bâtie (posée d'un coup, chantier fini), puis la fontaine et le beffroi.
		stage.run(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			RaidScenes.paste(level, "statue", STATUE);
			town.constructions().stream().filter(c -> Monument.of(c.blueprint()).isPresent()).forEach(Construction::finish);
			Towns.get(server).changed();
		});
		check(stage.compute(server -> {
			Town town = town(server);
			Happiness.Mood mood = Happiness.of(new Happiness.Factors(18, 0, 0, false, 2, true, false, false, false, false,
					false, 0, false, false, false, 0, town.monuments().size()));
			return town.monuments().equals(EnumSet.of(Monument.STATUE)) && town.appeal() == 1
					&& mood.factors().contains(new Happiness.Factor("monuments", 1)) ? null
					: "statue bâtie : " + town.monuments() + ", attrait " + town.appeal() + ", " + mood.factors();
		}));
		stage.run(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			for (Map.Entry<Monument, BlockPos> e : Map.of(Monument.FOUNTAIN, FOUNTAIN, Monument.BELFRY, BELFRY).entrySet()) {
				Optional<Component> refusal = Builds.order(level, town, blueprint(server, e.getKey()), BuildStyle.PLAINS,
						e.getValue(), Rotation.NONE);
				if (refusal.isPresent()) {
					throw new AssertionError(e.getKey() + " refusé : " + text(refusal.get()));
				}
				RaidScenes.paste(level, e.getKey().blueprint().getPath(), e.getValue());
			}
			town.constructions().forEach(Construction::finish);
			Towns.get(server).changed();
		});
		check(stage.compute(server -> {
			Town town = town(server);
			Happiness.Mood mood = Happiness.of(new Happiness.Factors(18, 0, 0, false, 2, true, false, false, false, false,
					false, 0, false, false, false, 0, town.monuments().size()));
			return town.monuments().size() == 3 && town.appeal() == 4
					&& mood.factors().contains(new Happiness.Factor("monuments", Monument.MOOD_CAP)) ? null
					: "trois monuments : " + town.monuments() + ", attrait " + town.appeal() + ", " + mood.factors();
		}));

		// Un citoyen s'en réjouit.
		stage.run(server -> TownService.immigrate(server.overworld(), town(server)));
		waitOrExplain(stage, server -> !citizens(server).isEmpty() && citizens(server).stream().allMatch(c -> c
				.moodDetail().factors().contains(new Happiness.Factor("monuments", Monument.MOOD_CAP))), 600,
				"les monuments ne réjouissent personne");

		// Sauvegardés, ils se relisent ; l'onglet Bâtiments les montre, le résumé dit l'attrait.
		check(stage.compute(server -> {
			Town town = town(server);
			Tag saved = Town.CODEC.encodeStart(NbtOps.INSTANCE, town).getOrThrow();
			Town read = Town.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
			TownSnapshot snapshot = TownNetwork.snapshot(server.overworld(), town, mayor(server));
			long rows = snapshot.buildings().stream().filter(TownSnapshot.BuildingRow::monument).count();
			return read.monuments().size() == 3 && rows == 3 && snapshot.life().appeal() == 4 ? null
					: "relus " + read.monuments() + ", lignes " + rows + ", attrait " + snapshot.life().appeal();
		}));

		// En images : les trois monuments, puis l'onglet.
		stage.run(server -> citizens(server).forEach(c -> c.setNoAi(true)));
		stage.client(mc -> hideGui(mc, true));
		stage.look(6, Y + 12, -20, 19, Y + 4, 0);
		stage.waitTicks(20);
		stage.shot("monuments_0_view");
		stage.look(17.5, Y + 6, -15, 17.5, Y + 5, -3);
		stage.waitTicks(10);
		stage.shot("monuments_1_statue");
		TownSnapshot snapshot = stage.compute(server -> TownNetwork.snapshot(server.overworld(), town(server),
				mayor(server)));
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new TownHallScreen(snapshot));
		});
		stage.waitTicks(3);
		stage.client(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.BUILDINGS));
		stage.waitTicks(3);
		stage.shot("monuments_2_tab");
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});
		// Le terrain rendu aux scènes suivantes : les monuments rasés.
		stage.run(server -> {
			for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(12, Y, -8), new BlockPos(33, Y + 20, 14))) {
				server.overworld().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		});
	}

	private static String text(Component component) {
		return component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
				? t.getKey() + " " + java.util.Arrays.toString(t.getArgs()) : component.getString();
	}

	private static Blueprint blueprint(MinecraftServer server, Monument monument) {
		return Blueprints.get(server, monument.blueprint()).orElseThrow();
	}

	private static ServerPlayer mayor(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElse(null);
	}
}
