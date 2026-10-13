package com.ajustor.minecities.gametest;

import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Builds;
import com.ajustor.minecities.client.screen.TownMapScreen;
import com.ajustor.minecities.network.TownNetwork;
import com.ajustor.minecities.network.TownSnapshot;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Highlights;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.TownTier;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;

/**
 * La carte du territoire (MC-13) : le rayon de la ville, ses annexes en service, ses bâtiments déclarés
 * et ses chantiers, à leur place, et rien d'une autre ville ; un clic sur un bâtiment le fait luire.
 * Avec une fenêtre : la carte d'une ville sans annexe, puis avec deux annexes, zoomée ; un clic au bon
 * endroit tombe sur le bon bâtiment.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=map}
 */
public class MapScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos PLAQUE = new BlockPos(5, Y + 1, 4);
	private static final BlockPos SITE = new BlockPos(-14, Y, -14);
	private static final BlockPos[] ANNEXES = {new BlockPos(90, Y, 0), new BlockPos(-20, Y, 95),
			new BlockPos(0, Y, -110)};
	private static final BlockPos ELSEWHERE = new BlockPos(3000, Y, 0);

	@Override
	public String name() {
		return "map";
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

		// Une ville sans annexe : son rayon, sa maison, un chantier ; rien d'une autre ville.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer mayor = mayor(server);
			TownService.found(level, HALL, mayor, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			TownService.declareHouse(level, PLAQUE, mayor);
			Builds.order(level, town, Blueprints.get(server, Identifier.fromNamespaceAndPath("minecities", "house_1"))
					.orElseThrow(), BuildStyle.PLAINS, SITE, Rotation.NONE);
			Town other = Town.found("Ailleurs", level.dimension(), ELSEWHERE, UUID.randomUUID(), "autre", 0L);
			Towns.get(server).add(other);
			TownSnapshot.TownMap map = TownNetwork.map(town);
			List<String> wrong = new ArrayList<>();
			if (map.radius() != town.radius() || !map.annexes().isEmpty()) {
				wrong.add("rayon " + map.radius() + " au lieu de " + town.radius() + ", annexes " + map.annexes());
			}
			Optional<TownSnapshot.Plot> house = plot(map, PLAQUE);
			if (house.isEmpty() || !house.get().kind().equals("both") && !house.get().kind().equals("home")
					|| PLAQUE.getX() < house.get().minX() || PLAQUE.getX() > house.get().maxX()
					|| PLAQUE.getZ() < house.get().minZ() || PLAQUE.getZ() > house.get().maxZ()) {
				wrong.add("la maison : " + house + " ; " + map.plots());
			}
			if (plot(map, SITE).filter(p -> p.kind().equals("site")).isEmpty()) {
				wrong.add("le chantier : " + map.plots());
			}
			if (map.plots().size() != town.houses().size() + 1
					|| map.plots().stream().anyMatch(p -> !town.contains(level.dimension(), p.at()))) {
				wrong.add("des bâtiments en trop : " + map.plots());
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Un clic : le bâtiment luit ; un clic à côté, rien.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			int before = Highlights.active();
			boolean house = TownNetwork.glow(level, town, mayor(server), PLAQUE);
			boolean nothing = TownNetwork.glow(level, town, mayor(server), PLAQUE.offset(40, 0, 40));
			return house && !nothing && Highlights.active() == before + 1 ? null
					: "luire : maison " + house + ", rien " + nothing + ", " + Highlights.active();
		}));

		// Avec une fenêtre : la carte sans annexe ; un clic sur la maison tombe sur elle.
		shoot(stage, "map_0_village", false);

		// Grande ville : deux annexes en service (la troisième attend la mégalopole).
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			town.forceTier(Optional.of(TownTier.CITY));
			TownService.retier(level, town);
			for (BlockPos annex : ANNEXES) {
				town.addAnnex(annex);
			}
			TownSnapshot.TownMap map = TownNetwork.map(town);
			return map.radius() == TownTier.CITY.radius() && map.annexes().equals(town.activeAnnexes())
					&& map.annexes().size() == 2 && map.annexRadius() == Town.ANNEX_RADIUS ? null
					: "annexes : " + map.annexes() + " rayon " + map.radius();
		}));
		shoot(stage, "map_1_annexes", true);
		stage.run(server -> Towns.get(server).all().stream().filter(t -> t.name().equals("Ailleurs")).toList()
				.forEach(t -> Towns.get(server).remove(t)));
	}

	/** La carte en image ; un clic au centre de la maison doit la désigner. */
	private static void shoot(Stage stage, String name, boolean zoom) {
		TownSnapshot snapshot = stage.compute(server -> TownNetwork.snapshot(server.overworld(), town(server),
				mayor(server)));
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new TownMapScreen(snapshot, null));
		});
		stage.waitTicks(3);
		stage.client(mc -> {
			TownMapScreen screen = (TownMapScreen) mc.gui.screen();
			if (zoom) {
				int[] hall = screen.toScreen(HALL.getX() + 0.5, HALL.getZ() + 0.5);
				screen.zoom(hall[0], hall[1], -1);
			}
			int[] at = screen.toScreen(PLAQUE.getX() + 0.5, PLAQUE.getZ() + 0.5);
			Optional<TownSnapshot.Plot> clicked = screen.plotAt(at[0], at[1]);
			if (clicked.map(TownSnapshot.Plot::at).filter(PLAQUE::equals).isEmpty()) {
				throw new AssertionError("le clic sur la maison tombe sur " + clicked);
			}
		});
		stage.waitTicks(3);
		stage.shot(name);
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});
	}

	private static Optional<TownSnapshot.Plot> plot(TownSnapshot.TownMap map, BlockPos at) {
		return map.plots().stream().filter(p -> p.at().equals(at)).findFirst();
	}

	private static ServerPlayer mayor(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElse(null);
	}
}
