package com.ajustor.minecities.gametest;

import com.ajustor.minecities.block.HousePlaqueBlock;
import com.ajustor.minecities.citizen.CitizenEntity;
import com.ajustor.minecities.citizen.Hunger;
import com.ajustor.minecities.compat.CitizenInfo;
import com.ajustor.minecities.registry.McEntities;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.citizens;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;

/**
 * L'intégration Jade (MC-15) : ce que l'infobulle dit d'un citoyen (faim, logement) et d'une plaque de
 * maison (son dernier relevé), d'après l'état du serveur. Sans Jade, le mod tourne et le greffon ne se
 * charge pas. Avec Jade ({@code -PminecitiesJade}) et une fenêtre, les infobulles sont photographiées.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=jade}
 */
public class JadeScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos PLAQUE = new BlockPos(5, Y + 1, 4);
	/** Un mur de la maison : sans lui, elle n'est plus close. */
	private static final BlockPos WINDOW = new BlockPos(3, Y + 1, 7);

	@Override
	public String name() {
		return "jade";
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

		// Sans Jade, le greffon ne se charge pas, et rien ne le demande.
		boolean jade = FabricLoader.getInstance().isModLoaded("jade");
		if (!jade) {
			check(loadable("snownee.jade.api.IWailaPlugin") ? "Jade est sur le chemin des classes sans être chargé"
					: null);
		}

		// La ville, une maison de deux lits, deux citoyens logés ; un autre, venu d'ailleurs, sans logis.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			TownService.found(level, HALL, player, new ItemStack(McItems.TOWN_HALL));
			TownService.declareHouse(level, PLAQUE, player);
			Town town = town(server);
			TownService.immigrate(level, town);
			TownService.immigrate(level, town);
			CitizenEntity stray = McEntities.CITIZEN.create(level, EntitySpawnReason.COMMAND);
			stray.snapTo(-3.5, Y, 6.5, 0, 0);
			level.addFreshEntity(stray);
			return town.population() == 2 && citizens(server).size() == 3 ? null
					: "population " + town.population() + ", citoyens " + citizens(server).size();
		}));
		// Le citoyen relit ce que la ville sait de lui toutes les 5 secondes.
		stage.waitTicks(110);

		// Le citoyen logé et repu, puis affamé ; le vagabond sans logis.
		check(stage.compute(server -> {
			CitizenEntity housed = resident(server, true);
			CitizenEntity stray = resident(server, false);
			if (housed == null || stray == null) {
				return "citoyens : " + citizens(server).stream().map(CityScenes::describe).toList();
			}
			housed.setHunger(Hunger.MAX);
			CitizenInfo fed = CitizenInfo.of(housed);
			housed.setHunger(0);
			CitizenInfo starving = CitizenInfo.of(housed);
			CitizenInfo homeless = CitizenInfo.of(stray);
			List<String> wrong = new ArrayList<>();
			if (!fed.housed() || fed.hunger() != Hunger.State.WELL_FED) {
				wrong.add("logé et repu : " + fed);
			}
			if (starving.hunger() != Hunger.State.STARVING) {
				wrong.add("affamé : " + starving);
			}
			if (homeless.housed()) {
				wrong.add("sans logis : " + homeless);
			}
			// Ce que le serveur envoie, le client le relit tel quel.
			for (CitizenInfo info : List.of(fed, starving, homeless)) {
				ByteBuf buffer = Unpooled.buffer();
				CitizenInfo.STREAM_CODEC.encode(buffer, info);
				CitizenInfo read = CitizenInfo.STREAM_CODEC.decode(buffer);
				if (!read.equals(info)) {
					wrong.add("transmis " + info + ", relu " + read);
				}
			}
			for (CitizenInfo info : List.of(fed, homeless)) {
				info.lines().forEach(line -> untranslated(line, wrong));
			}
			housed.setHunger(Hunger.MAX);
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// La plaque : enregistrée, puis non reconnue quand la maison s'ouvre.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			List<String> wrong = new ArrayList<>();
			HousePlaqueBlock.Status valid = level.getBlockState(PLAQUE).getValue(HousePlaqueBlock.STATUS);
			level.setBlockAndUpdate(WINDOW, Blocks.AIR.defaultBlockState());
			TownService.declareHouse(level, PLAQUE, player);
			HousePlaqueBlock.Status invalid = level.getBlockState(PLAQUE).getValue(HousePlaqueBlock.STATUS);
			if (valid != HousePlaqueBlock.Status.VALID || invalid != HousePlaqueBlock.Status.INVALID) {
				wrong.add("plaque " + valid + " puis " + invalid);
			}
			for (HousePlaqueBlock.Status status : HousePlaqueBlock.Status.values()) {
				untranslated(CitizenInfo.plaque(status), wrong);
			}
			level.setBlockAndUpdate(WINDOW, Blocks.GLASS.defaultBlockState());
			TownService.declareHouse(level, PLAQUE, player);
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Avec Jade et une fenêtre : l'infobulle du citoyen logé, du vagabond, puis de la plaque.
		if (!jade) {
			return;
		}
		stage.run(server -> citizens(server).forEach(c -> c.setNoAi(true)));
		stage.run(server -> {
			resident(server, true).snapTo(6.5, Y, 6.5, 0, 0);
			resident(server, false).setHunger(Hunger.HUNGRY - 4);
		});
		stage.client(mc -> hideGui(mc, false));
		stage.look(6.5, Y + 1.6, 4.6, 6.5, Y + 1.2, 6.5);
		stage.client(mc -> mc.gui.hud.getChat().clearMessages(false));
		stage.waitTicks(20);
		stage.shot("jade_0_citizen");
		stage.look(-3.5, Y + 1.6, 4.4, -3.5, Y + 1.2, 6.5);
		stage.client(mc -> mc.gui.hud.getChat().clearMessages(false));
		stage.waitTicks(20);
		stage.shot("jade_1_homeless");
		stage.look(5.5, Y + 1.6, 6.5, 5.5, Y + 1.5, 4.1);
		stage.client(mc -> mc.gui.hud.getChat().clearMessages(false));
		stage.waitTicks(20);
		stage.shot("jade_2_plaque");
		stage.client(mc -> hideGui(mc, true));
	}

	private static boolean loadable(String name) {
		try {
			Class.forName(name, false, JadeScenes.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	/** Les clés de cette ligne qui n'ont pas de traduction. */
	private static void untranslated(Component component, List<String> wrong) {
		if (component.getContents() instanceof TranslatableContents translatable) {
			if (!Language.getInstance().has(translatable.getKey())) {
				wrong.add("sans traduction : " + translatable.getKey());
			}
			for (Object arg : translatable.getArgs()) {
				if (arg instanceof Component inner) {
					untranslated(inner, wrong);
				}
			}
		}
		component.getSiblings().forEach(sibling -> untranslated(sibling, wrong));
	}

	/** Le citoyen logé (ou non) de la scène. */
	private static CitizenEntity resident(MinecraftServer server, boolean housed) {
		return citizens(server).stream().filter(c -> c.resident().flatMap(r -> r.home()).isPresent() == housed)
				.findFirst().orElse(null);
	}

	private static Town town(MinecraftServer server) {
		return Towns.get(server).byHall(server.overworld().dimension(), HALL).orElse(null);
	}
}
