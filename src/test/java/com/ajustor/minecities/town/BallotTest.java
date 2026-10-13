package com.ajustor.minecities.town;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BallotTest {
	private static final UUID MAYOR = new UUID(0, 1);
	private static final UUID RIVAL = new UUID(0, 2);
	private static final UUID THIRD = new UUID(0, 3);
	private static final UUID A = new UUID(1, 1);
	private static final UUID B = new UUID(1, 2);
	private static final UUID C = new UUID(1, 3);

	@Test
	void theMostVotesWin() {
		Ballot.Result result = Ballot.count(List.of(MAYOR, RIVAL), MAYOR, Map.of(A, RIVAL, B, RIVAL, C, MAYOR),
				List.of(), new Random(1));
		assertEquals(Optional.of(RIVAL), result.winner());
		assertEquals(2, result.votes(RIVAL));
		assertEquals(1, result.votes(MAYOR));
	}

	@Test
	void aTieGivesTheTiedCandidates() {
		Ballot.Result result = Ballot.count(List.of(MAYOR, RIVAL, THIRD), MAYOR, Map.of(A, RIVAL, B, THIRD),
				List.of(), new Random(1));
		assertEquals(Optional.empty(), result.winner());
		assertEquals(List.of(RIVAL, THIRD), result.leaders());
	}

	@Test
	void happyCitizensVoteForTheMayor() {
		// Tout à fait heureux (10) : ils votent tous, pour le maire sortant ; leur voix pèse autant.
		Ballot.Result result = Ballot.count(List.of(MAYOR, RIVAL), MAYOR, Map.of(A, RIVAL, B, RIVAL),
				List.of(10, 10, 10), new Random(1));
		assertEquals(Optional.of(MAYOR), result.winner());
		assertEquals(3, result.votes(MAYOR));
	}

	@Test
	void miserableCitizensAbstain() {
		// Un bonheur de 0 : ils ne votent jamais.
		Ballot.Result result = Ballot.count(List.of(MAYOR, RIVAL), MAYOR, Map.of(A, RIVAL), List.of(0, 0, 0, 0),
				new Random(1));
		assertEquals(Optional.of(RIVAL), result.winner());
		assertEquals(0, result.votes(MAYOR));
	}

	@Test
	void unhappyCitizensVoteForAChallenger() {
		// Malheureux (5) : environ la moitié vote, toujours contre le maire.
		Ballot.Result result = Ballot.count(List.of(MAYOR, RIVAL), MAYOR, Map.of(), java.util.Collections.nCopies(
				200, 5), new Random(7));
		assertEquals(0, result.votes(MAYOR));
		int votes = result.votes(RIVAL);
		assertEquals(true, votes > 60 && votes < 140, "voix : " + votes);
	}

	@Test
	void votesForNonCandidatesDoNotCount() {
		Ballot.Result result = Ballot.count(List.of(MAYOR, RIVAL), MAYOR, Map.of(A, THIRD, B, RIVAL), List.of(),
				new Random(1));
		assertEquals(Optional.of(RIVAL), result.winner());
		assertEquals(1, result.tally().values().stream().mapToInt(Integer::intValue).sum());
	}

	@Test
	void withoutAnyVoteTheMayorStays() {
		Ballot.Result result = Ballot.count(List.of(MAYOR, RIVAL), MAYOR, Map.of(), List.of(), new Random(1));
		assertEquals(Optional.of(MAYOR), result.winner());
	}

	@Test
	void aRunoffWithoutTheMayorLeavesHappyCitizensOut() {
		Ballot.Result result = Ballot.count(List.of(RIVAL, THIRD), MAYOR, Map.of(A, RIVAL), List.of(10, 10),
				new Random(1));
		assertEquals(Optional.of(RIVAL), result.winner());
		assertEquals(0, result.votes(THIRD));
	}
}
