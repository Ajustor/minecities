package com.ajustor.minecities.town;

import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Optional;

/**
 * Les monuments (MC-11) : la statue, la fontaine et le beffroi, ouverts à la mégalopole, un de chaque
 * par ville. Bâtis, ils rendent les citoyens plus heureux ({@link #MOOD_CAP} au plus) et donnent de
 * l'attrait à la ville : de quoi faire venir du monde, quand une taverne (ou autre chose) s'en servira.
 */
public enum Monument {
	STATUE(1), FOUNTAIN(1), BELFRY(2);

	/** Le bonheur que donnent les monuments, au plus. */
	public static final int MOOD_CAP = 2;

	private final int appeal;

	Monument(int appeal) {
		this.appeal = appeal;
	}

	/** Ce qu'il ajoute à l'attrait de la ville. */
	public int appeal() {
		return appeal;
	}

	/** Le schéma de ce monument. */
	public Identifier blueprint() {
		return Identifier.fromNamespaceAndPath("minecities", name().toLowerCase(Locale.ROOT));
	}

	/** Le monument que bâtit ce schéma, s'il en est un. */
	public static Optional<Monument> of(Identifier blueprint) {
		for (Monument monument : values()) {
			if (monument.blueprint().equals(blueprint)) {
				return Optional.of(monument);
			}
		}
		return Optional.empty();
	}
}
