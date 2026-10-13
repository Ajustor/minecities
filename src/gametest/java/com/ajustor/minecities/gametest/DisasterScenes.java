package com.ajustor.minecities.gametest;

import com.ajustor.minecities.McConfig;
import com.ajustor.minecities.block.TownHallBlockEntity;
import com.ajustor.minecities.network.TownNetwork;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Disasters;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.House;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.TownTier;
import com.ajustor.minecities.town.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;
import static com.ajustor.minecities.gametest.CityScenes.waitOrExplain;

/**
 * Les catastrophes (MC-14) : quand elles peuvent frapper (permises, espacées, l'incendie par orage et
 * dès la ville) ; l'incendie que la foudre met à un toit, et les citoyens qui l'éteignent avec les
 * seaux d'eau du grenier ; la disette ; le tremblement de terre qui fait tomber des blocs ; chacune
 * annoncée, visible dans le résumé, sauvegardée, puis finie.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=disasters}
 */
public class DisasterScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos PLAQUE = new BlockPos(5, Y + 1, 4);

	@Override
	public String name() {
		return "disasters";
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
		boolean disasters = McConfig.get().disasters;
		int spacing = McConfig.get().disasterSpacingDays;
		try {
			scenes(stage);
		} finally {
			McConfig.get().disasters = disasters;
			McConfig.get().disasterSpacingDays = spacing;
		}
	}

	private void scenes(Stage stage) {
		// Une ville et sa maison de bois, deux citoyens, des seaux d'eau au grenier.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer mayor = mayor(server);
			TownService.found(level, HALL, mayor, new ItemStack(McItems.TOWN_HALL));
			Town town = town(server);
			TownService.declareHouse(level, PLAQUE, mayor);
			TownService.immigrate(level, town);
			TownService.immigrate(level, town);
			if (level.getBlockEntity(HALL) instanceof TownHallBlockEntity hall) {
				hall.setItem(5, new ItemStack(Items.WATER_BUCKET));
				hall.setItem(6, new ItemStack(Items.WATER_BUCKET));
			}
			return town.house(PLAQUE).flatMap(House::min).isPresent() ? null : "pas de maison : " + town.houses();
		}));

		// Quand elles peuvent frapper.
		check(stage.compute(server -> {
			Town town = town(server);
			long now = server.overworld().getGameTime();
			List<String> wrong = new ArrayList<>();
			if (Disasters.due(town, now, 10 * 24000L)) {
				wrong.add("une ville nouvelle est déjà exposée");
			}
			if (!Disasters.due(town, now, 0)) {
				wrong.add("pas exposée sans délai");
			}
			McConfig.get().disasters = false;
			if (Disasters.due(town, now, 0)) {
				wrong.add("exposée malgré le réglage");
			}
			McConfig.get().disasters = true;
			if (Disasters.odds(Disasters.FIRE, false, TownTier.CITY) != 0
					|| Disasters.odds(Disasters.FIRE, true, TownTier.VILLAGE) != 0
					|| Disasters.odds(Disasters.FIRE, true, TownTier.TOWN) <= 0) {
				wrong.add("l'incendie : par orage, et dès la ville seulement");
			}
			if (Disasters.odds(Disasters.BLIGHT, false, TownTier.VILLAGE) <= 0
					|| Disasters.odds(Disasters.QUAKE, false, TownTier.VILLAGE) <= 0) {
				wrong.add("la disette et le séisme ne frappent jamais");
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// L'incendie : la foudre met le feu au toit ; ça se voit ; sauvegardé.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			if (!Disasters.ignite(level, town, new Random(5))) {
				return "rien n'a pris feu";
			}
			long now = level.getGameTime();
			Tag saved = Town.CODEC.encodeStart(NbtOps.INSTANCE, town).getOrThrow();
			Town read = Town.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
			String shown = TownNetwork.snapshot(level, town, mayor(server)).life().disaster();
			return !Disasters.fires(level, town).isEmpty() && shown.equals(Disasters.FIRE)
					&& read.chronicle().disaster(now).filter(Disasters.FIRE::equals).isPresent()
					&& read.chronicle().fireMin().isPresent() ? null
					: "le feu : " + Disasters.fires(level, town) + ", résumé « " + shown + " », relu "
							+ read.chronicle().disaster(now);
		}));
		stage.client(mc -> hideGui(mc, true));
		stage.look(5, Y + 9, -6, 6, Y + 3, 6);
		stage.waitTicks(10);
		stage.shot("disasters_0_fire");

		// Les citoyens accourent avec les seaux, et l'éteignent.
		waitOrExplain(stage, server -> town(server).chronicle().disasterKind().isEmpty(), 2400,
				"l'incendie n'a pas été éteint", server -> "flammes " + Disasters.fires(server.overworld(), town(server))
						+ " ;");
		check(stage.compute(server -> citizens(server).stream().anyMatch(c -> c.inventory().stream()
				.anyMatch(s -> s.is(Items.WATER_BUCKET))) ? null : "personne n'a pris de seau"));

		// La disette : les champs ne donnent rien un jour, puis tout revient.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			Disasters.blight(level, town);
			long now = level.getGameTime();
			if (!Disasters.blighted(town, now)) {
				return "pas de disette";
			}
			Disasters.follow(level, town, now + 24000);
			return !Disasters.blighted(town, now + 24000) && town.chronicle().disasterKind().isEmpty() ? null
					: "la disette ne finit pas";
		}));

		// Le séisme : toutes les villes ; des blocs tombent ; puis le calme, et plus de catastrophe avant longtemps.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			House house = town.house(PLAQUE).orElseThrow();
			int before = solid(level, house);
			Disasters.quake(server, new Random(3));
			long now = level.getGameTime();
			if (town.chronicle().disaster(now).filter(Disasters.QUAKE::equals).isEmpty()) {
				return "pas de séisme";
			}
			Random random = new Random(11);
			for (int i = 0; i < 10; i++) {
				Disasters.tremor(level, town, random);
			}
			int after = solid(level, house);
			Disasters.follow(level, town, now + 200);
			return after < before && town.chronicle().disasterKind().isEmpty()
					&& !Disasters.due(town, now + 300, 10 * 24000L)
					&& level.getBlockState(PLAQUE).is(com.ajustor.minecities.registry.McBlocks.HOUSE_PLAQUE) ? null
					: "le séisme : " + before + " blocs, puis " + after + ", " + town.chronicle().disasterKind();
		}));
	}

	/** Les blocs pleins du bâtiment. */
	private static int solid(ServerLevel level, House house) {
		int count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(house.min().orElseThrow(), house.max().orElseThrow())) {
			if (!level.getBlockState(pos).isAir()) {
				count++;
			}
		}
		return count;
	}

	private static ServerPlayer mayor(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElse(null);
	}
}
