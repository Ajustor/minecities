package com.ajustor.minecities.town;

import com.ajustor.minecities.McConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les élections du maire (brief 02). Le mandat dure {@code term_days} jours ; à son terme, ou quand
 * le maire le décide, ou quand la moitié des habitants a signé la pétition, une élection s'ouvre :
 * un jour de candidatures (le maire sortant est candidat d'office), puis un jour de vote. Chaque
 * joueur de la ville vote une fois ; les citoyens aussi, selon leur bonheur ({@link Ballot}). Des
 * candidats à égalité ? On revote entre eux. Pas de nouvelle élection avant {@code election_spacing_days}
 * jours. Un maire seul joueur de sa ville est réélu d'office.
 */
public final class Elections {
	public static final long DAY = 24000L;

	/** Pourquoi on ne peut pas faire ce qu'on demande. */
	public enum Refusal {
		NOT_MAYOR, NOT_MEMBER, MAYOR, RUNNING, NOT_RUNNING, TOO_SOON, SIGNED, CANDIDATE, NOT_CANDIDACY, NOT_VOTING,
		VOTED, NOT_CANDIDATE;

		public String translationKey() {
			return "message.minecities.election.refused." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private Elections() {
	}

	/** Les joueurs qui votent : le maire, ses adjoints et les habitants. */
	public static List<UUID> voters(Town town) {
		List<UUID> voters = new ArrayList<>();
		voters.add(town.mayor());
		voters.addAll(town.deputies());
		town.membership().residents().keySet().stream().filter(p -> !voters.contains(p)).forEach(voters::add);
		return voters;
	}

	/** Les signatures qu'il faut à la pétition : la moitié des habitants et adjoints (arrondie au-dessus). */
	public static int signaturesNeeded(Town town) {
		int others = voters(town).size() - 1;
		return Math.max(1, (others + 1) / 2);
	}

	/** Le temps avant qu'une élection puisse de nouveau s'ouvrir (0 : maintenant). */
	public static long untilAllowed(Town town, long now) {
		long last = town.election().lastElection();
		long spacing = McConfig.get().electionSpacingDays * DAY;
		return last == 0 ? 0 : Math.max(0, last + spacing - now);
	}

	/** Le maire ouvre une élection. */
	public static Optional<Refusal> call(ServerLevel level, Town town, ServerPlayer player) {
		if (!town.mayor().equals(player.getUUID())) {
			return Optional.of(Refusal.NOT_MAYOR);
		}
		return open(level, town, level.getGameTime(), true);
	}

	/** Un habitant (ou un adjoint) signe la pétition ; à la moitié des signatures, l'élection s'ouvre. */
	public static Optional<Refusal> sign(ServerLevel level, Town town, ServerPlayer player) {
		UUID id = player.getUUID();
		Election election = town.election();
		if (!town.isMember(id)) {
			return Optional.of(Refusal.NOT_MEMBER);
		}
		if (town.mayor().equals(id)) {
			return Optional.of(Refusal.MAYOR);
		}
		if (election.phase() != Election.Phase.NONE) {
			return Optional.of(Refusal.RUNNING);
		}
		if (untilAllowed(town, level.getGameTime()) > 0) {
			return Optional.of(Refusal.TOO_SOON);
		}
		if (!election.sign(id)) {
			return Optional.of(Refusal.SIGNED);
		}
		Towns.get(level.getServer()).changed();
		int signed = (int) election.petition().stream().filter(town::isMember).count();
		announce(level.getServer(), town, Component.translatable("message.minecities.election.signed",
				player.getGameProfile().name(), signed, signaturesNeeded(town)));
		if (signed >= signaturesNeeded(town)) {
			open(level, town, level.getGameTime(), true);
		}
		return Optional.empty();
	}

	/** Un joueur de la ville se porte candidat, pendant les candidatures. */
	public static Optional<Refusal> run(ServerLevel level, Town town, ServerPlayer player) {
		if (!town.isMember(player.getUUID())) {
			return Optional.of(Refusal.NOT_MEMBER);
		}
		if (town.election().phase() != Election.Phase.CANDIDACY) {
			return Optional.of(Refusal.NOT_CANDIDACY);
		}
		if (!town.election().run(player.getUUID(), player.getGameProfile().name())) {
			return Optional.of(Refusal.CANDIDATE);
		}
		Towns.get(level.getServer()).changed();
		announce(level.getServer(), town, Component.translatable("message.minecities.election.candidate",
				player.getGameProfile().name()));
		return Optional.empty();
	}

	/** Un joueur de la ville vote, une fois par tour. */
	public static Optional<Refusal> cast(ServerLevel level, Town town, ServerPlayer player, UUID candidate) {
		Election election = town.election();
		if (!town.isMember(player.getUUID())) {
			return Optional.of(Refusal.NOT_MEMBER);
		}
		if (election.phase() != Election.Phase.VOTING) {
			return Optional.of(Refusal.NOT_VOTING);
		}
		if (election.hasVoted(player.getUUID())) {
			return Optional.of(Refusal.VOTED);
		}
		if (!election.cast(player.getUUID(), candidate)) {
			return Optional.of(Refusal.NOT_CANDIDATE);
		}
		Towns.get(level.getServer()).changed();
		return Optional.empty();
	}

	/** Les échéances : fin du mandat, fin des candidatures, fin du vote. */
	public static void tick(ServerLevel level, Town town, long now) {
		Election election = town.election();
		int termDays = McConfig.get().termDays;
		if (election.termEnds() == 0 && termDays > 0) {
			// Une ville d'avant les élections, ou une ville nouvelle : son mandat commence maintenant.
			election.setTermEnds(now + termDays * DAY);
			Towns.get(level.getServer()).changed();
		}
		switch (election.phase()) {
			case NONE -> {
				if (termDays > 0 && now >= election.termEnds()) {
					open(level, town, now, false);
				}
			}
			case CANDIDACY -> {
				if (now >= election.phaseEnds()) {
					if (election.candidates().size() <= 1) {
						// Personne ne se présente contre le maire : il est réélu.
						finish(level, town, now, town.mayor(), town.mayorName(), 0);
					} else {
						election.vote(now + McConfig.get().votingDays * DAY,
								election.candidates().stream().map(Map.Entry::getKey).toList());
						Towns.get(level.getServer()).changed();
						announce(level.getServer(), town, Component.translatable("message.minecities.election.vote",
								names(election)));
					}
				}
			}
			case VOTING -> {
				if (now >= election.phaseEnds()) {
					count(level, town, now);
				}
			}
		}
	}

	/** Ouvre l'élection, si rien ne l'empêche ; seul joueur de sa ville, le maire est réélu d'office. */
	private static Optional<Refusal> open(ServerLevel level, Town town, long now, boolean early) {
		Election election = town.election();
		if (election.phase() != Election.Phase.NONE) {
			return Optional.of(Refusal.RUNNING);
		}
		if (early && untilAllowed(town, now) > 0) {
			return Optional.of(Refusal.TOO_SOON);
		}
		if (voters(town).size() <= 1) {
			finish(level, town, now, town.mayor(), town.mayorName(), 0);
			return Optional.empty();
		}
		election.open(now + McConfig.get().candidacyDays * DAY, town.mayor(), town.mayorName());
		Towns.get(level.getServer()).changed();
		announce(level.getServer(), town, Component.translatable("message.minecities.election.opened",
				McConfig.get().candidacyDays));
		return Optional.empty();
	}

	/** Le dépouillement : un élu, ou un nouveau tour entre les ex æquo. */
	private static void count(ServerLevel level, Town town, long now) {
		Election election = town.election();
		// Ceux qui ont quitté la ville (ou en ont été chassés) pendant le vote n'y sont plus candidats, et
		// leurs voix ne comptent plus.
		List<UUID> candidates = election.candidates().stream().map(Map.Entry::getKey).filter(town::isMember).toList();
		if (candidates.isEmpty()) {
			finish(level, town, now, town.mayor(), town.mayorName(), 0);
			return;
		}
		Map<UUID, UUID> votes = new java.util.HashMap<>();
		election.votes().forEach((voter, choice) -> {
			if (town.isMember(voter) && candidates.contains(choice)) {
				votes.put(voter, choice);
			}
		});
		List<Integer> moods = new ArrayList<>();
		town.residents().forEach((id, resident) -> {
			if (!resident.isChild()) {
				town.mood(id).ifPresent(moods::add);
			}
		});
		java.util.Random random = new java.util.Random(level.getRandom().nextLong());
		Ballot.Result result = Ballot.count(candidates, town.mayor(), votes, moods, random);
		Optional<UUID> winner = result.winner();
		// Personne n'a voté, et le maire sortant n'est plus en lice : un tour de plus ne changerait rien. Le
		// sort départage, au lieu de relancer des tours sans fin.
		if (winner.isEmpty() && !result.leaders().isEmpty()
				&& result.leaders().stream().allMatch(l -> result.votes(l) == 0)) {
			winner = Optional.of(result.leaders().get(random.nextInt(result.leaders().size())));
		}
		if (winner.isEmpty()) {
			election.vote(now + McConfig.get().votingDays * DAY, result.leaders());
			Towns.get(level.getServer()).changed();
			announce(level.getServer(), town, Component.translatable("message.minecities.election.tie",
					names(election), election.round()));
			return;
		}
		UUID elected = winner.get();
		String name = election.candidates().stream().filter(e -> e.getKey().equals(elected))
				.map(Map.Entry::getValue).findFirst().orElse("?");
		finish(level, town, now, elected, name, result.votes(elected));
	}

	/** L'élu prend l'écharpe ; l'ancien maire reste habitant. */
	private static void finish(ServerLevel level, Town town, long now, UUID winner, String name, int votes) {
		MinecraftServer server = level.getServer();
		UUID before = town.mayor();
		long term = McConfig.get().termDays > 0 ? now + McConfig.get().termDays * DAY : 0L;
		town.election().close(now, term, name, votes);
		if (!winner.equals(before)) {
			town.membership().addResident(before, town.mayorName());
			town.setMayor(winner, name);
			announce(server, town, Component.translatable("message.minecities.election.elected", name, votes));
		} else {
			announce(server, town, Component.translatable("message.minecities.election.reelected", name, votes));
		}
		Towns.get(server).changed();
	}

	private static String names(Election election) {
		return String.join(", ", election.candidates().stream().map(Map.Entry::getValue).toList());
	}

	/** Prévient les joueurs de la ville connectés. */
	public static void announce(MinecraftServer server, Town town, Component message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (town.isMember(player.getUUID())) {
				player.sendSystemMessage(Component.translatable("message.minecities.prefix", town.name())
						.withStyle(ChatFormatting.DARK_AQUA).append(message.copy().withStyle(ChatFormatting.WHITE)));
			}
		}
	}
}
