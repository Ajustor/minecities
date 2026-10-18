package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.build.Blueprints;
import com.ajustor.minecities.build.BuildStyle;
import com.ajustor.minecities.build.Builds;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Happiness;
import com.ajustor.minecities.registry.McBlocks;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Budget;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Job;
import com.ajustor.minecities.town.Stock;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.Optional;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.command;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Le budget (MC-57) : sans boîte à impôts, l'impôt du matin n'est pas perçu ; avec, chaque adulte y
 * verse ce que demande le taux fixé par le maire, et les adultes s'en plaignent. Les gardes sont payés
 * sur le grenier ; sans émeraudes, ils traînent. Un chantier de niveau 2 coûte 4 émeraudes : refusé
 * sans elles, payé avec.
 *
 * <p>{@code ./gradlew :26.2:runGameTest -PminecitiesGametest=budget}
 */
public class BudgetScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos BARRACKS = new BlockPos(-18, Y, 4);
	private static final BlockPos HOUSE = new BlockPos(-6, Y, -16);
	private static final BlockPos BOX = new BlockPos(3, Y, -3);
	private static final BlockPos SITE = new BlockPos(8, Y, 8);

	@Override
	public String name() {
		return "budget";
	}

	@Override
	public void play(Stage stage) {
		Families.setMinimum(0);
		stage.command("time set 1000");
		stage.command("gamerule advance_time false");
		stage.command("gamerule spawn_mobs false");
		stage.waitTicks(20);

		// La ville : une caserne (deux gardes) et une grande maison.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			for (int x = -24; x <= 24; x++) {
				for (int z = -24; z <= 24; z++) {
					level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				}
			}
			RaidScenes.paste(level, "barracks", BARRACKS);
			RaidScenes.paste(level, "house_large", HOUSE);
			command(server, "setblock 0 %d 0 minecities:town_hall[facing=north]".formatted(Y));
			ServerPlayer player = player(server);
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			for (int i = 0; i < 6; i++) {
				TownService.immigrate(level, town);
			}
			RaidScenes.plaque(level, BARRACKS).ifPresent(p -> TownService.declareHouse(level, p, player));
			RaidScenes.plaque(level, HOUSE).ifPresent(p -> TownService.declareHouse(level, p, player));
			return town.workers(Job.GUARD) == 2 ? null : "pas deux gardes : " + town.workers(Job.GUARD);
		}));

		// Sans boîte à impôts : rien n'est perçu.
		check(stage.compute(server -> {
			Town town = town(server);
			Budget.morning(server.overworld(), town, town.chronicle().ledger().day() + 1);
			return town.chronicle().ledger().taxes() == 0 ? null : "un impôt perçu sans boîte";
		}));

		// La boîte posée, le taux normal : chaque adulte y verse 2 émeraudes ; les gardes, impayés, traînent.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			level.setBlockAndUpdate(BOX, McBlocks.TAX_BOX.defaultBlockState());
			Budget.placeTaxBox(level, BOX, player(server), new ItemStack(McItems.TAX_BOX));
			if (town.chronicle().ledger().taxBox().isEmpty()) {
				return "la boîte à impôts n'est pas adoptée";
			}
			Budget.nextRate(level, town);
			if (town.chronicle().ledger().rate() != Budget.TaxRate.NORMAL) {
				return "le taux n'est pas passé à normal : " + town.chronicle().ledger().rate();
			}
			Budget.morning(level, town, town.chronicle().ledger().day() + 1);
			long adults = town.residents().values().stream().filter(r -> !r.isChild()).count();
			int inBox = Budget.inTaxBox(level, town);
			if (inBox != adults * 2) {
				return "dans la boîte : " + inBox + " émeraudes pour " + adults + " adultes";
			}
			// Les gardes d'après les registres de la ville (l'entité ne relit sa fiche que de temps en temps).
			long slowed = town.residents().entrySet().stream()
					.filter(e -> e.getValue().job().filter(j -> j.job() == Job.GUARD).isPresent())
					.map(e -> level.getEntity(e.getKey()))
					.filter(e -> e instanceof CitizenEntity c && c.hasEffect(MobEffects.SLOWNESS)).count();
			return slowed == 2 ? null : "gardes impayés ralentis : " + slowed;
		}));

		// Les adultes se plaignent de l'impôt.
		waitOrExplain(stage, server -> citizens(server).stream().filter(c -> !c.isChild())
				.allMatch(c -> c.moodDetail().factors().contains(new Happiness.Factor("taxes", -1))), 400,
				"l'impôt ne pèse pas sur le moral");

		// Des émeraudes au grenier : les gardes sont payés.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			granary(server).ifPresent(g -> Stock.insert(g, new ItemStack(Items.EMERALD, 5)));
			Budget.morning(level, town, town.chronicle().ledger().day() + 1);
			int left = granary(server).map(g -> Stock.count(g, s -> s.is(Items.EMERALD))).orElse(-1);
			return town.chronicle().ledger().wages() == 2 && left == 3 ? null
					: "salaires : " + town.chronicle().ledger().wages() + ", reste au grenier : " + left;
		}));

		// Un chantier de niveau 2 : refusé avec 3 émeraudes, payé avec 13.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			var house = Blueprints.get(server, Identifier.fromNamespaceAndPath("minecities", "house_2")).orElseThrow();
			Optional<Component> refusal = Builds.check(level, town, house, SITE, Rotation.NONE);
			if (refusal.isEmpty()) {
				return "un chantier de niveau 2 accepté sans de quoi le payer";
			}
			granary(server).ifPresent(g -> Stock.insert(g, new ItemStack(Items.EMERALD, 10)));
			Optional<Component> ordered = Builds.order(level, town, house, BuildStyle.PLAINS, SITE, Rotation.NONE);
			if (ordered.isPresent()) {
				return "chantier refusé : " + ordered.get().getString();
			}
			int left = granary(server).map(g -> Stock.count(g, s -> s.is(Items.EMERALD))).orElse(-1);
			return left == 9 && town.chronicle().ledger().works() == 4 ? null
					: "après la commande : " + left + " émeraudes, chantiers " + town.chronicle().ledger().works();
		}));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElseThrow();
	}

	private static Optional<TownHallBlockEntity> granary(MinecraftServer server) {
		return server.overworld().getBlockEntity(HALL) instanceof TownHallBlockEntity hall ? Optional.of(hall)
				: Optional.empty();
	}
}
