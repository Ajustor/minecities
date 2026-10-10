package com.ajustor.minecities.town;

/**
 * Les paliers d'une ville, du hameau à la mégalopole. Une ville monte de palier dès qu'elle a assez
 * d'habitants et de maisons ; son territoire (le rayon autour de l'hôtel de ville) grandit avec elle.
 */
public enum TownTier {
	VILLAGE("village", 0, 0, 48),
	TOWN("town", 10, 4, 80),
	CITY("city", 30, 12, 128),
	MEGALOPOLIS("megalopolis", 75, 30, 224);

	private final String serializedName;
	private final int minCitizens;
	private final int minHouses;
	private final int radius;

	TownTier(String serializedName, int minCitizens, int minHouses, int radius) {
		this.serializedName = serializedName;
		this.minCitizens = minCitizens;
		this.minHouses = minHouses;
		this.radius = radius;
	}

	public String serializedName() {
		return serializedName;
	}

	public int minCitizens() {
		return minCitizens;
	}

	public int minHouses() {
		return minHouses;
	}

	/** Le rayon du territoire, en blocs, autour de l'hôtel de ville. */
	public int radius() {
		return radius;
	}

	public String translationKey() {
		return "tier.minecities." + serializedName;
	}

	/** Le palier suivant, ou {@code null} pour la mégalopole. */
	public TownTier next() {
		return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
	}

	/** Le plus haut palier qu'atteignent ces habitants et ces maisons. */
	public static TownTier of(int citizens, int houses) {
		TownTier tier = VILLAGE;
		for (TownTier t : values()) {
			if (citizens >= t.minCitizens && houses >= t.minHouses) {
				tier = t;
			}
		}
		return tier;
	}

	public static TownTier fromSerializedName(String name) {
		for (TownTier t : values()) {
			if (t.serializedName.equals(name)) {
				return t;
			}
		}
		return VILLAGE;
	}
}
