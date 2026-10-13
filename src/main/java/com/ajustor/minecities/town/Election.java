package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * La vie électorale d'une ville : la fin du mandat du maire, la pétition des habitants, et l'élection
 * en cours (candidatures, puis vote, tour après tour tant que des candidats sont à égalité). Le maire
 * reste en place jusqu'au résultat. La mécanique est dans {@link Elections}.
 */
public final class Election {
	public static final MapCodec<Election> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.LONG.optionalFieldOf("term_ends", 0L).forGetter(e -> e.termEnds),
			Codec.LONG.optionalFieldOf("last_election", 0L).forGetter(e -> e.lastElection),
			UUIDUtil.CODEC.listOf().optionalFieldOf("petition", List.of()).forGetter(e -> List.copyOf(e.petition)),
			Codec.STRING.optionalFieldOf("election_phase", Phase.NONE.serializedName())
					.forGetter(e -> e.phase.serializedName()),
			Codec.LONG.optionalFieldOf("phase_ends", 0L).forGetter(e -> e.phaseEnds),
			Codec.INT.optionalFieldOf("round", 1).forGetter(e -> e.round),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).optionalFieldOf("candidates", Map.of())
					.forGetter(e -> e.candidates),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.CODEC).optionalFieldOf("votes", Map.of())
					.forGetter(e -> e.votes),
			Codec.STRING.optionalFieldOf("last_winner", "").forGetter(e -> e.lastWinner),
			Codec.INT.optionalFieldOf("last_votes", 0).forGetter(e -> e.lastVotes)
	).apply(i, Election::new));

	/** Où en est l'élection. */
	public enum Phase {
		NONE, CANDIDACY, VOTING;

		public String serializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Phase fromSerializedName(String name) {
			for (Phase phase : values()) {
				if (phase.serializedName().equals(name)) {
					return phase;
				}
			}
			return NONE;
		}
	}

	/** Quand le mandat du maire s'achève (0 : pas encore fixé, il le sera au premier passage). */
	private long termEnds;
	/** Quand la dernière élection s'est achevée (0 : jamais). */
	private long lastElection;
	private final Set<UUID> petition;
	private Phase phase;
	private long phaseEnds;
	private int round;
	/** Les candidats et leur nom, dans l'ordre des candidatures (le maire sortant d'abord). */
	private final Map<UUID, String> candidates;
	/** Le vote de chaque joueur, pour ce tour. */
	private final Map<UUID, UUID> votes;
	private String lastWinner;
	private int lastVotes;

	private Election(long termEnds, long lastElection, List<UUID> petition, String phase, long phaseEnds, int round,
			Map<UUID, String> candidates, Map<UUID, UUID> votes, String lastWinner, int lastVotes) {
		this.termEnds = termEnds;
		this.lastElection = lastElection;
		this.petition = new LinkedHashSet<>(petition);
		this.phase = Phase.fromSerializedName(phase);
		this.phaseEnds = phaseEnds;
		this.round = round;
		this.candidates = new LinkedHashMap<>(candidates);
		this.votes = new LinkedHashMap<>(votes);
		this.lastWinner = lastWinner;
		this.lastVotes = lastVotes;
	}

	public static Election none() {
		return new Election(0L, 0L, List.of(), Phase.NONE.serializedName(), 0L, 1, Map.of(), Map.of(), "", 0);
	}

	public long termEnds() {
		return termEnds;
	}

	public void setTermEnds(long termEnds) {
		this.termEnds = termEnds;
	}

	public long lastElection() {
		return lastElection;
	}

	public Phase phase() {
		return phase;
	}

	public long phaseEnds() {
		return phaseEnds;
	}

	public int round() {
		return round;
	}

	// Pétition

	public Set<UUID> petition() {
		return Set.copyOf(petition);
	}

	public boolean sign(UUID player) {
		return petition.add(player);
	}

	// Élection

	/** Ouvre les candidatures jusqu'à cette date ; le maire sortant est candidat d'office. */
	public void open(long until, UUID incumbent, String name) {
		phase = Phase.CANDIDACY;
		phaseEnds = until;
		round = 1;
		petition.clear();
		candidates.clear();
		votes.clear();
		candidates.put(incumbent, name);
	}

	/** Le vote, jusqu'à cette date, entre ces candidats (tous, ou les ex æquo d'un tour précédent). */
	public void vote(long until, List<UUID> between) {
		if (phase == Phase.VOTING) {
			round++;
		}
		phase = Phase.VOTING;
		phaseEnds = until;
		candidates.keySet().retainAll(between);
		votes.clear();
	}

	/** L'élection est finie : ce maire est élu (ou réélu) avec tant de voix, pour un mandat jusqu'à {@code term}. */
	public void close(long now, long term, String winner, int count) {
		phase = Phase.NONE;
		phaseEnds = 0L;
		round = 1;
		candidates.clear();
		votes.clear();
		petition.clear();
		lastElection = now;
		termEnds = term;
		lastWinner = winner;
		lastVotes = count;
	}

	public List<Map.Entry<UUID, String>> candidates() {
		return List.copyOf(candidates.entrySet());
	}

	public boolean isCandidate(UUID player) {
		return candidates.containsKey(player);
	}

	public boolean run(UUID player, String name) {
		return candidates.putIfAbsent(player, name) == null;
	}

	public Map<UUID, UUID> votes() {
		return Map.copyOf(votes);
	}

	public boolean hasVoted(UUID player) {
		return votes.containsKey(player);
	}

	public boolean cast(UUID voter, UUID candidate) {
		return candidates.containsKey(candidate) && votes.putIfAbsent(voter, candidate) == null;
	}

	/** Le dernier élu et ses voix (vide si la ville n'a jamais voté). */
	public Optional<String> lastWinner() {
		return lastWinner.isEmpty() ? Optional.empty() : Optional.of(lastWinner);
	}

	public int lastVotes() {
		return lastVotes;
	}
}
