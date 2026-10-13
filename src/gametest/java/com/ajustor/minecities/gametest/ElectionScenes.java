package com.ajustor.minecities.gametest;

import com.ajustor.minecities.McConfig;
import com.ajustor.minecities.client.screen.TownHallScreen;
import com.ajustor.minecities.network.TownNetwork;
import com.ajustor.minecities.network.TownSnapshot;
import com.ajustor.minecities.registry.McItems;
import com.ajustor.minecities.town.Election;
import com.ajustor.minecities.town.Elections;
import com.ajustor.minecities.town.Families;
import com.ajustor.minecities.town.Membership.Rank;
import com.ajustor.minecities.town.Town;
import com.ajustor.minecities.town.TownService;
import com.ajustor.minecities.town.Towns;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ajustor.minecities.gametest.CityScenes.check;
import static com.ajustor.minecities.gametest.CityScenes.hideGui;

/**
 * Les élections du maire (MC-17), sur une ville de deux joueurs : seul, le maire est réélu d'office ;
 * pas de nouvelle élection trop tôt ; la pétition d'un habitant ouvre l'élection ; il se porte
 * candidat ; le vote, l'égalité et le second tour ; l'élu prend l'écharpe et l'ancien maire reste
 * habitant ; au terme du mandat, une élection sans adversaire réélit le maire. Le temps est avancé à
 * la main ({@link Elections#tick}). Tout cela se sauvegarde, et se montre dans l'onglet Élection.
 *
 * <p>{@code ./gradlew runGameTest -PminecitiesGametest=elections}
 */
public class ElectionScenes implements Scene {
	private static final int Y = 100;
	private static final BlockPos HALL = new BlockPos(0, Y, 0);
	private static final BlockPos PLAQUE = new BlockPos(5, Y + 1, 4);
	private static final GameProfile RIVAL = new GameProfile(UUID.fromString("00000000-0000-4000-8000-000000000018"),
			"Candidat");
	private static final long DAY = Elections.DAY;

	@Override
	public String name() {
		return "elections";
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
		int spacing = McConfig.get().electionSpacingDays;
		try {
			play(stage, stage.compute(server -> server.overworld().getGameTime()));
		} finally {
			McConfig.get().electionSpacingDays = spacing;
		}
	}

	private void play(Stage stage, long start) {
		// La ville, son mandat ; seul joueur, le maire qui ouvre une élection est réélu d'office.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer mayor = mayor(server);
			TownService.found(level, HALL, mayor, new ItemStack(McItems.TOWN_HALL));
			TownService.declareHouse(level, PLAQUE, mayor);
			Town town = town(server);
			Elections.tick(level, town, start);
			if (town.election().termEnds() != start + McConfig.get().termDays * DAY) {
				return "le mandat finit à " + town.election().termEnds();
			}
			Optional<Elections.Refusal> refusal = Elections.call(level, town, mayor);
			return refusal.isEmpty() && town.election().phase() == Election.Phase.NONE
					&& town.election().lastWinner().equals(Optional.of(town.mayorName())) ? null
					: "seul, le maire n'est pas réélu d'office : " + refusal + " " + town.election().phase();
		}));

		// Un habitant arrive ; trop tôt pour une nouvelle élection ; puis sa pétition l'ouvre.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			ServerPlayer rival = FakePlayer.get(level, RIVAL);
			TownService.requestJoin(server, town, rival);
			TownService.answerJoin(server, town, rival.getUUID(), true);
			List<String> wrong = new ArrayList<>();
			if (Elections.sign(level, town, rival).orElse(null) != Elections.Refusal.TOO_SOON) {
				wrong.add("pétition acceptée trop tôt");
			}
			if (Elections.call(level, town, rival).orElse(null) != Elections.Refusal.NOT_MAYOR) {
				wrong.add("un habitant ouvre l'élection");
			}
			McConfig.get().electionSpacingDays = 0;
			if (Elections.signaturesNeeded(town) != 1 || Elections.sign(level, town, rival).isPresent()) {
				wrong.add("la pétition (il faut " + Elections.signaturesNeeded(town) + ")");
			}
			if (town.election().phase() != Election.Phase.CANDIDACY || !town.election().isCandidate(town.mayor())) {
				wrong.add("la pétition n'a pas ouvert l'élection : " + town.election().phase());
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Il se porte candidat ; les candidatures closes, le vote s'ouvre ; une voix chacun : égalité.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			ServerPlayer rival = FakePlayer.get(level, RIVAL);
			List<String> wrong = new ArrayList<>();
			if (run(server, rival, "ville election run") == 0 || !town.election().isCandidate(rival.getUUID())) {
				wrong.add("pas candidat");
			}
			if (Elections.cast(level, town, rival, rival.getUUID()).orElse(null) != Elections.Refusal.NOT_VOTING) {
				wrong.add("un vote pendant les candidatures");
			}
			long phase = town.election().phaseEnds();
			Elections.tick(level, town, phase);
			if (town.election().phase() != Election.Phase.VOTING || town.election().round() != 1) {
				wrong.add("pas de vote : " + town.election().phase());
			}
			Elections.cast(level, town, mayor(server), mayor(server).getUUID());
			Elections.cast(level, town, rival, rival.getUUID());
			if (Elections.cast(level, town, rival, town.mayor()).orElse(null) != Elections.Refusal.VOTED) {
				wrong.add("il a voté deux fois");
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Sauvegardée au milieu du vote, l'élection se relit telle quelle.
		check(stage.compute(server -> {
			Town town = town(server);
			Tag saved = Town.CODEC.encodeStart(NbtOps.INSTANCE, town).getOrThrow();
			Election read = Town.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow().election();
			Election e = town.election();
			return read.phase() == e.phase() && read.phaseEnds() == e.phaseEnds() && read.votes().equals(e.votes())
					&& java.util.Set.copyOf(read.candidates()).equals(java.util.Set.copyOf(e.candidates()))
					&& read.termEnds() == e.termEnds() ? null
					: "relue : " + read.phase() + " " + read.votes() + " " + read.candidates();
		}));

		// L'onglet Élection, pendant le vote (le maire a voté).
		TownSnapshot snapshot = stage.compute(server -> TownNetwork.snapshot(server.overworld(), town(server),
				mayor(server)));
		check(stage.compute(server -> {
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
			TownSnapshot.CODEC.encode(buffer, snapshot);
			TownSnapshot.Ballot read = TownSnapshot.CODEC.decode(buffer).ballot();
			return read.equals(snapshot.ballot()) && read.phase().equals("voting") && read.candidates().size() == 2
					&& read.voted() ? null : "l'onglet : " + read;
		}));
		stage.client(mc -> {
			hideGui(mc, false);
			mc.gui.setScreen(new TownHallScreen(snapshot));
		});
		stage.waitTicks(3);
		stage.client(mc -> ((TownHallScreen) mc.gui.screen()).select(TownHallScreen.Tab.ELECTION));
		stage.waitTicks(3);
		stage.shot("elections_0_tab");
		stage.client(mc -> {
			mc.gui.setScreen(null);
			hideGui(mc, true);
		});

		// Égalité : on revote entre eux ; au second tour, le maire vote pour son rival, qui est élu.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			ServerPlayer mayor = mayor(server);
			ServerPlayer rival = FakePlayer.get(level, RIVAL);
			UUID before = town.mayor();
			List<String> wrong = new ArrayList<>();
			Elections.tick(level, town, town.election().phaseEnds());
			if (town.election().phase() != Election.Phase.VOTING || town.election().round() != 2
					|| town.election().candidates().size() != 2 || !town.election().votes().isEmpty()) {
				wrong.add("pas de second tour : " + town.election().phase() + " tour " + town.election().round());
			}
			if (run(server, mayor, "ville vote Candidat") == 0) {
				wrong.add("le maire n'a pas pu voter par la commande");
			}
			Elections.cast(level, town, rival, rival.getUUID());
			long end = town.election().phaseEnds();
			Elections.tick(level, town, end);
			if (!town.mayor().equals(rival.getUUID()) || town.rank(before) != Rank.RESIDENT) {
				wrong.add("maire " + town.mayorName() + ", l'ancien est " + town.rank(before));
			}
			if (town.election().phase() != Election.Phase.NONE
					|| !town.election().lastWinner().equals(Optional.of("Candidat")) || town.election().lastVotes() != 2
					|| town.election().termEnds() != end + McConfig.get().termDays * DAY) {
				wrong.add("après l'élection : " + town.election().phase() + " " + town.election().lastWinner() + " "
						+ town.election().lastVotes());
			}
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));

		// Au terme du mandat, l'élection s'ouvre d'elle-même ; sans adversaire, le maire est réélu.
		check(stage.compute(server -> {
			ServerLevel level = server.overworld();
			Town town = town(server);
			List<String> wrong = new ArrayList<>();
			long term = town.election().termEnds();
			Elections.tick(level, town, term - 1);
			if (town.election().phase() != Election.Phase.NONE) {
				wrong.add("ouverte avant le terme");
			}
			Elections.tick(level, town, term);
			if (town.election().phase() != Election.Phase.CANDIDACY) {
				wrong.add("pas ouverte au terme : " + town.election().phase());
			}
			Elections.tick(level, town, town.election().phaseEnds());
			if (town.election().phase() != Election.Phase.NONE || !town.mayor().equals(RIVAL.id())) {
				wrong.add("sans adversaire : " + town.election().phase() + ", maire " + town.mayorName());
			}
			// L'écharpe revient au maire du départ, pour les scènes suivantes.
			town.setMayor(mayor(server).getUUID(), mayor(server).getGameProfile().name());
			Towns.get(server).changed();
			return wrong.isEmpty() ? null : String.join(" ; ", wrong);
		}));
	}

	private static ServerPlayer mayor(MinecraftServer server) {
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
