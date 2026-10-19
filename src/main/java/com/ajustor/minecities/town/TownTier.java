package com.ajustor.minecities.town;

/**
 * Les paliers d'une ville, du hameau à la mégalopole. Une ville monte de palier quand elle a assez
 * d'habitants et de maisons, un bonheur moyen et des métiers assez variés, et le bâtiment public de ce
 * palier (MC-68) ; elle redescend si elle ne les a plus. Son territoire grandit avec elle.
 */
public enum TownTier {
	VILLAGE("village", 0, 0, 48, 0, 0, ""),
	TOWN("town", 10, 4, 80, 3, 5, "tavern"),
	CITY("city", 30, 12, 128, 5, 10, "monument"),
	MEGALOPOLIS("megalopolis", 75, 30, 224, 6, 16, "library");

	/** Une ville qui a déjà son palier le garde avec un point de bonheur de moins : pas de va-et-vient. */
	public static final double MOOD_MARGIN = 1;

	private final String serializedName;
	private final int minCitizens;
	private final int minHouses;
	private final int radius;
	private final double minMood;
	private final int minJobs;
	private final String building;

	TownTier(String serializedName, int minCitizens, int minHouses, int radius, double minMood, int minJobs,
			String building) {
		this.serializedName = serializedName;
		this.minCitizens = minCitizens;
		this.minHouses = minHouses;
		this.radius = radius;
		this.minMood = minMood;
		this.minJobs = minJobs;
		this.building = building;
	}

	/** Le bonheur moyen qu'il faut (sur 10). */
	public double minMood() {
		return minMood;
	}

	/** Combien de métiers différents la ville doit exercer. */
	public int minJobs() {
		return minJobs;
	}

	/** Le bâtiment public qu'il faut : {@code tavern}, {@code monument}, {@code library} ; vide pour le village. */
	public String building() {
		return building;
	}

	/** La ville remplit-elle les conditions de ce palier ? Celui qu'elle a déjà se garde avec un peu de marge. */
	public boolean reachedBy(Town town) {
		if (town.population() < minCitizens || town.homes().size() < minHouses) {
			return false;
		}
		if (this == VILLAGE) {
			return true;
		}
		double margin = town.tier().ordinal() >= ordinal() ? MOOD_MARGIN : 0;
		return town.averageMood().orElse(0.0) >= minMood - margin && town.distinctJobs() >= minJobs
				&& town.hasPublicBuilding(building);
	}

	/** Le plus haut palier que la ville atteint, chacun après le précédent. */
	public static TownTier reached(Town town) {
		TownTier tier = VILLAGE;
		for (TownTier t : values()) {
			if (!t.reachedBy(town)) {
				break;
			}
			tier = t;
		}
		return tier;
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

	/** Combien de mairies annexes la ville peut poser (brief 03) : dès la grande ville. */
	public int annexes() {
		return switch (this) {
			case VILLAGE, TOWN -> 0;
			case CITY -> 2;
			case MEGALOPOLIS -> 4;
		};
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
