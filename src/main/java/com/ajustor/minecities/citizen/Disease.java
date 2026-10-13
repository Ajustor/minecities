package com.ajustor.minecities.citizen;

import java.util.Locale;
import java.util.Optional;

/**
 * Les maladies des citoyens (MC-12). Chacune a son incubation (il couve sans le savoir), sa durée (les
 * symptômes, sans soins), sa convalescence une fois soignée par le médecin, sa contagion, et ce qu'elle
 * fait : le moral en baisse, le travail ralenti, et pour la plus grave, le lit. On n'en meurt pas.
 *
 * <ul>
 *   <li>le <b>rhume</b> : pris sous la pluie sans manteau ; bénin, mais il passe de l'un à l'autre ;</li>
 *   <li>la <b>dysenterie</b> : d'avoir mangé de la chair putréfiée ou un aliment empoisonné ;</li>
 *   <li>la <b>fièvre</b> : plus rarement sous la pluie, ou d'un fiévreux ; elle cloue au lit.</li>
 * </ul>
 * Les durées sont en ticks (un jour de jeu : 24 000).
 */
public enum Disease {
	COLD(6000, 24000, 2400, 0.06, -1, 1.25, false),
	DYSENTERY(1200, 24000, 3600, 0.03, -2, 1.5, false),
	FEVER(12000, 36000, 6000, 0.05, -2, 1.0, true);

	private final long incubation;
	private final long duration;
	private final long convalescence;
	private final double contagion;
	private final int mood;
	private final double pace;
	private final boolean bedridden;

	Disease(long incubation, long duration, long convalescence, double contagion, int mood, double pace,
			boolean bedridden) {
		this.incubation = incubation;
		this.duration = duration;
		this.convalescence = convalescence;
		this.contagion = contagion;
		this.mood = mood;
		this.pace = pace;
		this.bedridden = bedridden;
	}

	/** Le temps où il couve, sans symptôme (ni contagion). */
	public long incubation() {
		return incubation;
	}

	/** Le temps des symptômes, sans soins. */
	public long duration() {
		return duration;
	}

	/** Le temps qu'il lui faut encore, une fois soigné. */
	public long convalescence() {
		return convalescence;
	}

	/** La chance qu'un voisin malade le passe à un autre, à chaque contrôle (toutes les 5 secondes). */
	public double contagion() {
		return contagion;
	}

	/** Ce qu'elle fait au moral. */
	public int mood() {
		return mood;
	}

	/** Ce qu'elle fait au rythme du travail : les pauses durent d'autant. */
	public double pace() {
		return pace;
	}

	/** Cloue-t-elle au lit ? */
	public boolean bedridden() {
		return bedridden;
	}

	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "disease.minecities." + serializedName();
	}

	public static Optional<Disease> fromSerializedName(String name) {
		for (Disease disease : values()) {
			if (disease.serializedName().equals(name)) {
				return Optional.of(disease);
			}
		}
		return Optional.empty();
	}
}
