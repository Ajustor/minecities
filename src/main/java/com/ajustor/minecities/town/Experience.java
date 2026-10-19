package com.ajustor.minecities.town;

/**
 * L'expérience d'un travailleur (brief 01, MC-83) : plus il exerce son métier depuis longtemps, plus il
 * reprend vite son ouvrage, jusqu'à 30 % plus vite. Changer de métier repart de zéro.
 */
public enum Experience {
	APPRENTICE("apprentice", 0, 1.0f),
	JOURNEYMAN("journeyman", 2, 0.9f),
	SKILLED("skilled", 5, 0.8f),
	EXPERT("expert", 10, 0.7f);

	private final String serializedName;
	private final int days;
	private final float pace;

	Experience(String serializedName, int days, float pace) {
		this.serializedName = serializedName;
		this.days = days;
		this.pace = pace;
	}

	public String serializedName() {
		return serializedName;
	}

	/** Le facteur de ses pauses entre deux ouvrages (1 : comme un apprenti). */
	public float pace() {
		return pace;
	}

	/** Son rang, pour cet emploi, maintenant. */
	public static Experience of(Resident.Employment employment, long now) {
		if (employment.hiredAt() < 0) {
			return APPRENTICE;
		}
		long days = Math.max(0, now - employment.hiredAt()) / 24000L;
		Experience rank = APPRENTICE;
		for (Experience e : values()) {
			if (days >= e.days) {
				rank = e;
			}
		}
		return rank;
	}
}
