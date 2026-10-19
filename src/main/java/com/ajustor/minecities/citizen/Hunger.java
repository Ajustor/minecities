package com.ajustor.minecities.citizen;

/**
 * La faim d'un citoyen, comme la barre du joueur : de 0 (affamé) à 20 (repu). Elle se vide en une
 * journée de jeu ; un citoyen mange dès qu'il a faim, et quand il n'a plus rien il dépérit jusqu'à
 * en mourir.
 */
public final class Hunger {
	public static final float MAX = 20;
	/** Une barre pleine dure une journée (24 000 ticks, réglable). */
	public static float decayPerTick() {
		return MAX / Math.max(1, com.ajustor.minecities.McConfig.get().hungerDayTicks);
	}
	/** Ce que mange un adulte par jour, en portions : une barre pleine, à environ cinq de nourriture la portion. */
	public static final int PORTIONS_PER_DAY = 4;
	/** En dessous, il cherche à manger. */
	public static final float HUNGRY = 12;
	/** Il mange jusqu'à ce seuil. */
	public static final float SATED = 18;
	/** Au-dessus, il se soigne de lui-même. */
	public static final float WELL_FED = 16;
	/** Le ventre vide, il perd un cœur toutes les 30 secondes : il meurt en 5 minutes sans soins. */
	public static final int STARVATION_DAMAGE_INTERVAL = 600;

	public enum State {
		WELL_FED("well_fed"),
		FED("fed"),
		HUNGRY("hungry"),
		STARVING("starving");

		private final String key;

		State(String key) {
			this.key = key;
		}

		public String translationKey() {
			return "hunger.minecities." + key;
		}
	}

	private Hunger() {
	}

	public static State state(float hunger) {
		if (hunger <= 0) {
			return State.STARVING;
		}
		if (hunger < HUNGRY) {
			return State.HUNGRY;
		}
		return hunger >= WELL_FED ? State.WELL_FED : State.FED;
	}

	/** La faim après {@code ticks} ticks sans manger. */
	public static float decay(float hunger, int ticks) {
		return Math.max(0, hunger - decayPerTick() * ticks);
	}

	/** La faim après avoir mangé un aliment qui nourrit de {@code nutrition}. */
	public static float eat(float hunger, int nutrition) {
		return Math.min(MAX, hunger + nutrition);
	}
}
