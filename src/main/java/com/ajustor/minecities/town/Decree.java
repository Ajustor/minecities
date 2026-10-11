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
	HOLIDAY("holiday", 1);

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
