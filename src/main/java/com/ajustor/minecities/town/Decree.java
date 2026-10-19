package com.ajustor.minecities.town;

import java.util.Optional;

/**
 * Les décrets du maire (brief 02) : des règles qu'il impose à toute la ville, chacune avec son
 * avantage et son prix sur le moral des citoyens.
 */
public enum Decree {
	/** Rationnement : on mange jusqu'à être rassasié à moitié ; le grenier dure plus longtemps, la ville grogne. */
	RATIONING("rationing", -1),
	/** Heures supplémentaires : les travailleurs commencent à l'aube et finissent à la nuit ; ils fatiguent. */
	OVERTIME("overtime", -1),
	/** Jour férié : personne ne travaille (sauf les défenseurs) ; tout le monde se réjouit. */
	HOLIDAY("holiday", 1),
	/** Couvre-feu : tout le monde rentre dès la fin d'après-midi (sauf les défenseurs) ; moins de pertes la nuit. */
	CURFEW("curfew", -1),
	/** Frontières ouvertes : la taverne attire plus vite, mais seulement une ville plus heureuse. */
	OPEN_BORDERS("open_borders", 0);

	/** Les décrets ne se prennent qu'à partir de ce palier (brief 03). */
	public static final TownTier TIER = TownTier.TOWN;
	/** L'heure du couvre-feu : la fin d'après-midi, avant la tombée de la nuit (12 500). */
	public static final long CURFEW_TIME = 11000;

	private final String serializedName;
	private final int mood;

	Decree(String serializedName, int mood) {
		this.serializedName = serializedName;
		this.mood = mood;
	}

	public String serializedName() {
		return serializedName;
	}

	/** Ce que le décret fait au moral de chaque citoyen. */
	public int mood() {
		return mood;
	}

	public String translationKey() {
		return "decree.minecities." + serializedName;
	}

	public static Optional<Decree> fromSerializedName(String name) {
		for (Decree decree : values()) {
			if (decree.serializedName.equals(name)) {
				return Optional.of(decree);
			}
		}
		return Optional.empty();
	}
}
