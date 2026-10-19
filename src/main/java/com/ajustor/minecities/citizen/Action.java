package com.ajustor.minecities.citizen;

/**
 * Ce que fait un citoyen, pour que le client l'anime : chaque métier a son geste. Synchronisé par
 * son rang ({@code ordinal}) : ne pas réordonner, seulement ajouter à la fin.
 */
public enum Action {
	NONE,
	/** Porte la nourriture à la bouche. */
	EAT,
	/** Coups de houe vers le sol. */
	HOE,
	/** Grands coups de hache. */
	CHOP,
	/** Canne tendue vers l'eau. */
	FISH,
	/** Pose des blocs, à petits coups. */
	BUILD,
	/** Brasse devant le fumoir. */
	COOK,
	/** Tend la nourriture aux bêtes. */
	FEED,
	/** Parle en gesticulant. */
	TALK,
	/** Lève les bras de joie (mariage, naissance). */
	CHEER,
	/** Bande son arc, les deux bras tendus. */
	AIM,
	/** Soigne un patient, les mains posées sur lui. */
	HEAL,
	/** Danse à la fête. */
	DANCE,
	/** À terre, mis hors de combat (un membre de caravane, MC-80). */
	DOWN
}
