package com.ajustor.minecities.gametest;

import com.ajustor.minecities.build.Blueprint;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildPlan;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.PlanItem;
import com.ajustor.minecities.build.Proposals;
import com.ajustor.minecities.client.render.GhostRenderer;
import com.ajustor.minecities.client.screen.PlanScreen;
import com.ajustor.minecities.client.screen.TownHallScreen;
import com.ajustor.minecities.network.GhostPayload;
import com.ajustor.minecities.network.PlanNetwork;
import com.ajustor.minecities.network.TownActionPayload;
import com.ajustor.minecities.network.TownNetwork;
import com.ajustor.minecities.network.TownSnapshot;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;

/**
 * Le plan et le fantôme (brief 09) : {@code /ville build} propose un chantier sans le commander ;
 * {@code rotate} le tourne sur place, {@code cancel} l'abandonne sans rien poser. Un plan pris à
 * l'hôtel de ville, posé au sol, propose son bâtiment dans son style ; {@code confirm} le commande, et
 * le plan reste en main. Qui n'est ni maire ni adjoint ne peut rien proposer. L'onglet Plans montre
 * les bâtiments, fermés ou non à ce palier. Le plan est unique (MC-23) : vierge, son interface
 * choisit le bâtiment et le style (pas un bâtiment fermé), l'onglet Plans choisit celui qu'on a au
 * lieu d'en donner un autre ; tenu en main, il montre les chantiers en cours. Avec fenêtre : le
 * fantôme, l'onglet, les chantiers et l'interface du plan.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=plans}
 */
public class PlanScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final Identifier HOUSE = Identifier.fromNamespaceAndPath("minecities", "house_1");
	private static final Identifier MARKET = Identifier.fromNamespaceAndPath("minecities", "market_1");

	@Override
	public String name() {
		return "plans";
	}

	@Override
	public void play(Stage stage) {
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.command("weather clear");
		stage.waitTicks(20);

		stage.run(PlanScenes::build);

		// La ville, et le maire au sud de l'hôtel de ville, face au sud : le chantier s'élève devant lui.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			player.teleportTo(0.5, Y, 12.5);
			player.setYRot(0);
			player.setYHeadRot(0);
			return town(server) != null ? null : "pas de ville";
		}));

		// /ville build propose, sans commander : un fantôme, pas de chantier.
		check(stage.compute(server -> {
			ServerPlayer player = player(server);
			if (run(server, player, "ville build " + HOUSE) != 1) {
				return "/ville build a échoué";
			}
			if (!Proposals.blueprintOf(player.getUUID()).filter(HOUSE::equals).isPresent()) {
				return "pas de proposition : " + Proposals.blueprintOf(player.getUUID());
			}
			if (town(server).activeConstruction().isPresent()) {
				return "le chantier est commandé sans confirmation";
			}
			List<GhostPayload.Block> ghost = Proposals.ghost(Proposals.of(player.getUUID()).orElseThrow());
			boolean upperDoor = ghost.stream().anyMatch(b -> b.state().getBlock() instanceof DoorBlock
					&& b.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER);
			boolean air = ghost.stream().anyMatch(b -> b.state().isAir());
			return !ghost.isEmpty() && upperDoor && !air ? null
					: "le fantôme : " + ghost.size() + " blocs, porte entière " + upperDoor + ", de l'air " + air;
		}));

		// Avec fenêtre : le fantôme, vu du maire.
		stage.client(mc -> hideGui(mc, true));
		stage.waitTicks(20);
		stage.client(mc -> check(GhostRenderer.count() == 1 ? null : "le client n'a pas de fantôme"));
		stage.look(0.5, Y + 5, 3, 0.5, Y + 2, 18);
		stage.waitTicks(20);
		stage.shot("plans_0_ghost");

		// Tourné d'un quart de tour, sur place.
		check(stage.compute(server -> {
			ServerPlayer player = player(server);
			Proposals.Proposal before = Proposals.of(player.getUUID()).orElseThrow();
			BlockPos center = center(before.blueprint(), before.origin(), before.rotation());
			Rotation rotation = before.rotation();
			if (run(server, player, "ville build rotate") != 1) {
				return "/ville build rotate a échoué";
			}
			Proposals.Proposal after = Proposals.of(player.getUUID()).orElseThrow();
			BlockPos turned = center(after.blueprint(), after.origin(), after.rotation());
			return after.rotation() == rotation.getRotated(Rotation.CLOCKWISE_90) && center.distManhattan(turned) <= 1
					? null : "rotation " + rotation + " → " + after.rotation() + ", milieu " + center + " → " + turned;
		}));
		stage.waitTicks(10);
		stage.shot("plans_1_rotated");

		// Abandonné : ni proposition, ni chantier, ni fantôme.
		check(stage.compute(server -> {
			ServerPlayer player = player(server);
			run(server, player, "ville build cancel");
			return Proposals.of(player.getUUID()).isEmpty() && town(server).activeConstruction().isEmpty() ? null
					: "l'abandon a laissé une proposition ou un chantier";
		}));
		stage.waitTicks(10);
		stage.client(mc -> check(GhostRenderer.count() == 0 ? null : "le fantôme reste après l'abandon"));
		stage.shot("plans_2_cancelled");

		// L'onglet Plans : la petite maison est ouverte, le marché attend la ville.
		TownSnapshot snapshot = stage.compute(server -> TownNetwork.snapshot(server.overworld(), town(server),
				player(server)));
		check(plan(snapshot, HOUSE).filter(p -> p.locked().isEmpty()).isPresent()
				&& plan(snapshot, MARKET).filter(p -> !p.locked().isEmpty()).isPresent() ? null
				: "les plans : " + snapshot.plans());
		// Le coût d'un plan, au survol (MC-76) : une maison de niveau 2 se paie en émeraudes, ses matériaux
		// d'abord les plus nombreux, avec ce qu'en ont les réserves.
		check(stage.compute(server -> {
			Optional<PlanNetwork.CostPayload> cost = PlanNetwork.costOf(player(server),
					new PlanNetwork.CostRequest("minecities:house_2", "plains"));
			if (cost.isEmpty() || cost.get().emeralds() <= 0 || cost.get().materials().isEmpty()) {
				return "le coût : " + cost;
			}
			List<PlanNetwork.Material> m = cost.get().materials();
			for (int i = 1; i < m.size(); i++) {
				if (m.get(i).needed() > m.get(i - 1).needed()) {
					return "le coût n'est pas trié : " + m;
				}
			}
			return null;
		}));
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new TownHallScreen(snapshot));
		});
		stage.waitTicks(3);
		stage.client(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.PLANS));
		stage.waitTicks(3);
		stage.shot("plans_3_tab");
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});

		// Le plan posé au sol propose son bâtiment, dans son style ; confirmé, il est commandé.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			Blueprint house = Blueprints.get(server, HOUSE).orElseThrow();
			ItemStack plan = PlanItem.of(house, BuildStyle.TAIGA);
			// Un spectateur ne pose rien : le maire est en survie le temps de poser son plan.
			command(server, "gamemode survival @a");
			player.setItemInHand(InteractionHand.MAIN_HAND, plan);
			BlockPos ground = new BlockPos(0, Y - 1, 10);
			plan.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false)));
			command(server, "gamemode spectator @a");
			Optional<Proposals.Proposal> proposal = Proposals.of(player.getUUID());
			if (proposal.isEmpty() || !proposal.get().blueprint().id().equals(HOUSE)
					|| proposal.get().style() != BuildStyle.TAIGA) {
				return "le plan n'a rien proposé : " + proposal.map(p -> p.blueprint().id() + " " + p.style());
			}
			if (run(server, player, "ville build confirm") != 1) {
				return "/ville build confirm a échoué";
			}
			if (town(server).activeConstruction().filter(c -> c.blueprint().equals(HOUSE)).isEmpty()) {
				return "pas de chantier après confirmation";
			}
			if (!player.getMainHandItem().is(McItems.PLAN)) {
				return "le plan a disparu : " + player.getMainHandItem();
			}
			return Proposals.of(player.getUUID()).isEmpty() ? null : "la proposition reste après confirmation";
		}));

		// Le plan unique : vierge, son interface choisit le bâtiment et le style, pas un bâtiment fermé.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(McItems.PLAN));
			PlanNetwork.PlanSnapshot blank = PlanNetwork.snapshot(level, player).orElse(null);
			if (blank == null || !blank.blueprint().isEmpty() || !blank.manager() || blank.plans().isEmpty()) {
				return "l'interface du plan vierge : " + blank;
			}
			PlanNetwork.act(player, "select:desert:" + MARKET);
			if (PlanItem.blueprint(player.getMainHandItem()).isPresent()) {
				return "un bâtiment fermé a été choisi";
			}
			PlanNetwork.act(player, "select:desert:" + HOUSE);
			ItemStack plan = player.getMainHandItem();
			if (!PlanItem.blueprint(plan).filter(HOUSE::equals).isPresent() || PlanItem.style(plan) != BuildStyle.DESERT) {
				return "le choix : " + PlanItem.blueprint(plan) + " " + PlanItem.style(plan);
			}
			// L'onglet Plans ne donne pas un second plan : il choisit celui qu'on a (à portée de l'hôtel de ville).
			player.teleportTo(0.5, Y, 3.5);
			TownNetwork.act(player, new TownActionPayload(HALL, "plan:taiga:" + HOUSE, TownActionPayload.NONE));
			long plans = player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(McItems.PLAN)).count();
			return plans == 1 && PlanItem.style(player.getMainHandItem()) == BuildStyle.TAIGA ? null
					: plans + " plans, style " + PlanItem.style(player.getMainHandItem());
		}));

		// Tenu en main, le plan montre le chantier en cours ; posé, plus rien.
		stage.run(server -> PlanNetwork.show(player(server)));
		stage.waitTicks(10);
		stage.client(mc -> check(GhostRenderer.sites() == 1 ? null : "chantiers montrés : " + GhostRenderer.sites()));
		stage.look(0.5, Y + 6, 2, 0.5, Y + 1, 16);
		stage.waitTicks(20);
		stage.shot("plans_4_sites");
		PlanNetwork.PlanSnapshot shown = stage.compute(server -> PlanNetwork.snapshot(server.overworld(),
				player(server)).orElseThrow());
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new PlanScreen(shown));
		});
		stage.waitTicks(3);
		stage.shot("plans_5_screen");
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});
		stage.run(server -> {
			player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			PlanNetwork.show(player(server));
		});
		stage.waitTicks(10);
		stage.client(mc -> check(GhostRenderer.sites() == 0 ? null : "les chantiers restent sans plan en main"));

		// Qui n'est ni maire ni adjoint ne propose rien, ni ne choisit.
		check(stage.compute(server -> {
			ServerPlayer player = player(server);
			Town town = town(server);
			UUID mayor = town.mayor();
			String name = town.mayorName();
			town.setMayor(UUID.randomUUID(), "quelqu'un");
			int result = run(server, player, "ville build " + HOUSE);
			boolean proposed = Proposals.of(player.getUUID()).isPresent();
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(McItems.PLAN));
			PlanNetwork.act(player, "select:plains:" + HOUSE);
			boolean chosen = PlanItem.blueprint(player.getMainHandItem()).isPresent();
			town.setMayor(mayor, name);
			return result == 0 && !proposed && !chosen ? null
					: "un simple joueur a proposé un chantier (" + proposed + ") ou choisi un plan (" + chosen + ")";
		}));
	}

	/** Une esplanade d'herbe, pour voir le fantôme. */
	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int x = -24; x <= 24; x++) {
			for (int z = -12; z <= 36; z++) {
				level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), net.minecraft.world.level.block.Blocks.GRASS_BLOCK
						.defaultBlockState());
				for (int y = Y; y < Y + 12; y++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR
								.defaultBlockState());
					}
				}
			}
		}
	}

	private static Optional<TownSnapshot.PlanRow> plan(TownSnapshot snapshot, Identifier id) {
		return snapshot.plans().stream().filter(p -> p.id().equals(id.toString())).findFirst();
	}

	private static BlockPos center(Blueprint blueprint, BlockPos origin, Rotation rotation) {
		BlockPos[] bounds = BuildPlan.bounds(blueprint, origin, rotation);
		return new BlockPos((bounds[0].getX() + bounds[1].getX()) / 2, bounds[0].getY(),
				(bounds[0].getZ() + bounds[1].getZ()) / 2);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElse(null);
	}

	private static int run(MinecraftServer server, ServerPlayer player, String command) {
		try {
			return server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			return 0;
		}
	}
}
