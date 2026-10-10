package com.ajustor.minecities.town;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Des noms pour les villes et leurs habitants, quand le joueur n'en donne pas. Le tirage se fait
 * par {@code random.applyAsInt(n)}, un entier dans [0, n) : {@code level.getRandom()::nextInt}.
 */
public final class Names {
	private static final List<String> TOWN_PREFIXES = List.of("Haute", "Basse", "Belle", "Vieille", "Neuve", "Clair",
			"Roche", "Val", "Mont", "Pré", "Bois", "Fleur", "Pierre", "Sable", "Ombre", "Rive");
	private static final List<String> TOWN_SUFFIXES = List.of("ville", "bourg", "court", "mont", "val", "pierre",
			"fontaine", "lieu", "brune", "rive", "combe", "lande", "mare", "fort");

	private static final List<String> FIRST_NAMES = List.of("Alice", "Bastien", "Camille", "Damien", "Élise",
			"Fabien", "Gaëlle", "Hugo", "Inès", "Jules", "Katia", "Léon", "Manon", "Noé", "Olivia", "Paul",
			"Quentin", "Rose", "Simon", "Théa", "Ugo", "Victor", "Wendy", "Yanis", "Zoé", "Agathe", "Basile",
			"Clémence", "Dorian", "Eva", "Félix", "Garance", "Hector", "Iris", "Jeanne", "Lucas", "Margot",
			"Nina", "Oscar", "Pauline", "Raphaël", "Sacha", "Tom", "Valentine", "Louise", "Arthur", "Lina", "Gabin");
	private static final List<String> LAST_NAMES = List.of("Martin", "Bernard", "Dubois", "Moreau", "Laurent",
			"Lefèvre", "Roux", "Fournier", "Girard", "Bonnet", "Dupont", "Lambert", "Fontaine", "Rousseau",
			"Vincent", "Muller", "Faure", "André", "Mercier", "Blanc", "Guérin", "Boyer", "Garnier", "Chevalier",
			"Meunier", "Boulanger", "Charpentier", "Tisserand", "Potier", "Forgeron", "Berger", "Pêcheur");

	private Names() {
	}

	public static String town(IntUnaryOperator random) {
		String prefix = TOWN_PREFIXES.get(random.applyAsInt(TOWN_PREFIXES.size()));
		String suffix = TOWN_SUFFIXES.get(random.applyAsInt(TOWN_SUFFIXES.size()));
		return prefix + suffix;
	}

	public static String citizen(IntUnaryOperator random) {
		return FIRST_NAMES.get(random.applyAsInt(FIRST_NAMES.size())) + " "
				+ LAST_NAMES.get(random.applyAsInt(LAST_NAMES.size()));
	}
}
