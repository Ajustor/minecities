package com.ajustor.minecities.town;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Le dépouillement d'une élection : une voix par joueur, et celles des citoyens. Un citoyen vote avec
 * une chance égale à son bonheur (sur 10) : heureux (au moins {@link #HAPPY}), il vote pour le maire
 * sortant ; malheureux, pour un autre candidat, au hasard. Sa voix pèse autant que celle d'un joueur.
 */
public final class Ballot {
	/** À partir de ce bonheur, un citoyen est content du maire sortant. */
	public static final int HAPPY = 6;

	/** Les voix de chaque candidat, et ceux qui en ont le plus (un seul : il est élu). */
	public record Result(Map<UUID, Integer> tally, List<UUID> leaders) {
		public Optional<UUID> winner() {
			return leaders.size() == 1 ? Optional.of(leaders.getFirst()) : Optional.empty();
		}

		public int votes(UUID candidate) {
			return tally.getOrDefault(candidate, 0);
		}
	}

	private Ballot() {
	}

	/**
	 * Compte les voix. Les votes des joueurs pour quelqu'un qui n'est pas candidat ne comptent pas.
	 * Sans aucune voix, le maire sortant (s'il se présente) reste en place.
	 *
	 * @param candidates les candidats, dans l'ordre
	 * @param incumbent le maire sortant
	 * @param votes le vote de chaque joueur
	 * @param moods le bonheur (de 0 à 10) de chaque citoyen en âge de voter
	 */
	public static Result count(List<UUID> candidates, UUID incumbent, Map<UUID, UUID> votes, List<Integer> moods,
			RandomGenerator random) {
		Map<UUID, Integer> tally = new LinkedHashMap<>();
		candidates.forEach(c -> tally.put(c, 0));
		votes.values().stream().filter(tally::containsKey).forEach(c -> tally.merge(c, 1, Integer::sum));
		List<UUID> challengers = candidates.stream().filter(c -> !c.equals(incumbent)).toList();
		for (int mood : moods) {
			if (random.nextInt(10) >= mood) {
				continue;
			}
			if (mood >= HAPPY) {
				if (tally.containsKey(incumbent)) {
					tally.merge(incumbent, 1, Integer::sum);
				}
			} else if (!challengers.isEmpty()) {
				tally.merge(challengers.get(random.nextInt(challengers.size())), 1, Integer::sum);
			}
		}
		int best = tally.values().stream().mapToInt(Integer::intValue).max().orElse(0);
		if (best == 0 && tally.containsKey(incumbent)) {
			return new Result(tally, List.of(incumbent));
		}
		List<UUID> leaders = new ArrayList<>();
		tally.forEach((candidate, n) -> {
			if (n == best) {
				leaders.add(candidate);
			}
		});
		return new Result(tally, leaders);
	}
}
