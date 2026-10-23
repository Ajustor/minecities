package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.Builds;
import com.ajustor.minecities.build.Construction;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.registry.McBlocks;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Le bâtisseur à qui il manque un matériau : il passe les blocs qui en ont besoin (et ce qui ne
 * tiendrait pas sans eux), pose tout le reste, puis réclame ce qui manque ; une fois le matériau
 * déposé au grenier, il finit le chantier.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=shortage}
 */
public class ShortageScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos PLAQUE = new BlockPos(5, Y + 1, 4);
	/** Le coin nord-ouest du chantier, dans le terrain de la ville. */
	private static final BlockPos SITE = new BlockPos(-14, Y, -14);
	private static final Identifier HOUSE = Identifier.fromNamespaceAndPath("minecities", "house_1");

	/** Le matériau qu'on ne lui donne pas d'abord. */
	private Item withheld;
	private int withheldCount;

	@Override
	public String name() {
		return "shortage";
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

		// La ville, son bâtisseur dans la maison déclarée.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer mayor = mayor(server);
			mayor.getInventory().clearContent();
			mayor.removeTag("minecities.bench");
			TownService.found(level, HALL, mayor, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			TownService.declareHouse(level, PLAQUE, mayor);
			House house = town.house(PLAQUE).orElse(null);
			if (house == null) {
				return "pas de maison";
			}
			BlockPos spot = BlockPos.betweenClosedStream(house.min().orElseThrow().offset(1, 1, 1),
							house.max().orElseThrow().offset(-1, -1, -1))
					.filter(p -> level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolid()
							&& level.getBlockState(p.above()).isAir() && !p.equals(PLAQUE)
							&& house.beds().stream().noneMatch(b -> b.distManhattan(p) < 2))
					.map(BlockPos::immutable).findFirst().orElse(null);
			if (spot == null) {
				return "pas de place dans la maison";
			}
			level.setBlockAndUpdate(spot, McBlocks.STATIONS.get(Job.BUILDER).defaultBlockState());
			TownService.declareHouse(level, PLAQUE, mayor);
			TownService.immigrate(level, town);
			TownService.immigrate(level, town);
			return null;
		}));
		waitOrExplain(stage, server -> town(server).workers(Job.BUILDER) == 1, 600, "pas de bâtisseur");

		// Au grenier, tout sauf un matériau (pas des planches : il en scierait).
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Map<Item, Integer> needs = BuildPlan.materials(BuildPlan.of(Blueprints.get(server, HOUSE).orElseThrow(),
					BuildStyle.PLAINS, SITE, Rotation.NONE));
			withheld = needs.entrySet().stream().filter(e -> !net.minecraft.core.registries.BuiltInRegistries.ITEM
							.getKey(e.getKey()).getPath().endsWith("_planks"))
					.max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElseThrow();
			withheldCount = needs.get(withheld);
			if (!(level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall)) {
				return "pas de grenier";
			}
			hall.clearContent();
			Stock.insert(hall, new ItemStack(Items.BREAD, 32));
			Stock.insert(hall, new ItemStack(Items.IRON_PICKAXE));
			Stock.insert(hall, new ItemStack(Items.IRON_SHOVEL));
			Stock.insert(hall, new ItemStack(Items.IRON_AXE));
			for (Map.Entry<Item, Integer> e : needs.entrySet()) {
				if (e.getKey() == withheld) {
					continue;
				}
				for (int left = e.getValue(); left > 0; ) {
					int count = Math.min(left, e.getKey().getDefaultMaxStackSize());
					if (!Stock.insert(hall, new ItemStack(e.getKey(), count)).isEmpty()) {
						return "le grenier est trop petit";
					}
					left -= count;
				}
			}
			return Builds.order(level, town(server), Blueprints.get(server, HOUSE).orElseThrow(), BuildStyle.PLAINS,
					SITE, Rotation.NONE).map(Component::getString).orElse(null);
		}));

		// Il pose tout ce qu'il peut, puis réclame ce qui manque.
		waitOrExplain(stage, server -> builder(server).flatMap(CitizenEntity::lastNeed)
				.filter(n -> n.getContents() instanceof TranslatableContents t && t.getKey().equals("speech.minecities.short"))
				.isPresent(), 6000, "le bâtisseur n'a pas réclamé ce qui manque");
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Construction site = site(server).orElse(null);
			if (site == null || site.done()) {
				return "le chantier est fini sans le matériau";
			}
			List<BuildPlan.Placement> plan = Builds.plan(server, site);
			int skipped = 0;
			for (int i = 0; i < plan.size(); i++) {
				if (site.isPlaced(i)) {
					continue;
				}
				BuildPlan.Placement p = plan.get(i);
				if (BuildPlan.material(p.state()).filter(withheld::equals).isPresent()) {
					skipped++;
				} else if (!unsupported(level, p)) {
					return "un bloc posable reste à faire : " + p.state() + " à " + p.pos().toShortString();
				}
			}
			return skipped > 0 ? null : "aucun bloc n'attend " + withheld;
		}));

		// Le matériau déposé : il finit le chantier.
		check(stage.compute(server -> {
			if (!(server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall)) {
				return "pas de grenier";
			}
			for (int left = withheldCount; left > 0; left -= withheld.getDefaultMaxStackSize()) {
				if (!Stock.insert(hall, new ItemStack(withheld, Math.min(left, withheld.getDefaultMaxStackSize())))
						.isEmpty()) {
					return "le grenier est plein";
				}
			}
			return null;
		}));
		waitOrExplain(stage, server -> site(server).isEmpty(), 6000, "le chantier ne s'est pas fini",
				server -> "il manque " + Builds.shortages(server.overworld(), town(server)));
	}

	/** Ce bloc ne tiendrait pas encore à sa place (une torche sans son mur, du sable sur le vide) ? */
	private static boolean unsupported(ServerLevel level, BuildPlan.Placement p) {
		BlockState state = p.state();
		return !state.canSurvive(level, p.pos())
				|| state.getBlock() instanceof FallingBlock && FallingBlock.isFree(level.getBlockState(p.pos().below()));
	}

	private static Optional<Construction> site(MinecraftServer server) {
		return town(server).constructions().stream().filter(c -> !c.done()).findFirst();
	}

	private static Optional<CitizenEntity> builder(MinecraftServer server) {
		Town town = town(server);
		return town.residents().entrySet().stream()
				.filter(e -> e.getValue().job().filter(j -> j.job() == Job.BUILDER).isPresent())
				.map(e -> server.overworld().getEntity(e.getKey())).filter(CitizenEntity.class::isInstance)
				.map(CitizenEntity.class::cast).findFirst();
	}

	private static ServerPlayer mayor(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElse(null);
	}
}
