package com.ajustor.minecities.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ajustor.minecities.town.TownTier;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Un schéma de bâtiment : des couches de caractères (de bas en haut, rangées du nord au sud, blocs
 * d'ouest en est) et la clé qui dit quel bloc est chaque caractère, écrit avec des jetons de style.
 * « . » laisse le bloc tel quel. La porte est au nord. Voir tools/blueprints/generate.py.
 *
 * @param kind    « house » (on y dort) ou « workshop » (on y travaille)
 * @param upgrade le schéma qui l'agrandit, bâti au même endroit
 * @param tier    le palier où la ville peut le commander (brief 03) : le marché attend la ville
 * @param legacy  un schéma d'avant les quatre niveaux (v0.9) : gardé pour les chantiers commencés,
 *                il n'est plus proposé
 */
public record Blueprint(Identifier id, String kind, int width, int height, int depth, List<Cell> cells,
		Optional<Identifier> upgrade, TownTier tier, boolean legacy) {
	/** Le nombre de niveaux d'un bâtiment (brief 11) ; d'autres, plus chers, pourront s'y ajouter. */
	public static final int LEVELS = 4;

	/**
	 * Le niveau du bâtiment : le chiffre qui termine son nom ({@code house_3} : 3). Les anciens schémas
	 * valent le niveau de leurs successeurs moins un ; un schéma sans niveau vaut 1.
	 */
	public int level() {
		return level(id);
	}

	public static int level(Identifier id) {
		String path = id.getPath();
		int underscore = path.lastIndexOf('_');
		if (underscore >= 0) {
			try {
				return Math.max(1, Integer.parseInt(path.substring(underscore + 1)));
			} catch (NumberFormatException e) {
				// Pas de chiffre : un ancien schéma, ou un bâtiment à un seul niveau.
			}
		}
		if (path.equals("manor")) {
			return 3;
		}
		return path.endsWith("_large") ? 2 : 1;
	}

	/** Le nom du bâtiment sans son niveau ({@code house_3} : {@code house}). */
	public String family() {
		String path = id.getPath();
		int underscore = path.lastIndexOf('_');
		return underscore >= 0 && path.substring(underscore + 1).chars().allMatch(Character::isDigit)
				? path.substring(0, underscore) : path;
	}
	/** Un bloc du schéma, à sa place locale, encore écrit avec ses jetons. */
	public record Cell(int x, int y, int z, String template) {
	}

	public String translationKey() {
		return "blueprint." + id.getNamespace() + "." + id.getPath();
	}

	public static Blueprint parse(Identifier id, JsonObject json) {
		JsonObject key = json.getAsJsonObject("key");
		Map<Character, String> templates = new TreeMap<>();
		for (Map.Entry<String, JsonElement> e : key.entrySet()) {
			if (e.getKey().length() != 1) {
				throw new IllegalArgumentException("clé de schéma invalide : " + e.getKey());
			}
			templates.put(e.getKey().charAt(0), e.getValue().getAsString());
		}
		JsonArray layers = json.getAsJsonArray("layers");
		List<Cell> cells = new ArrayList<>();
		int width = 0;
		int depth = 0;
		for (int y = 0; y < layers.size(); y++) {
			JsonArray rows = layers.get(y).getAsJsonArray();
			depth = Math.max(depth, rows.size());
			for (int z = 0; z < rows.size(); z++) {
				String row = rows.get(z).getAsString();
				width = Math.max(width, row.length());
				for (int x = 0; x < row.length(); x++) {
					char ch = row.charAt(x);
					if (ch == '.') {
						continue;
					}
					String template = templates.get(ch);
					if (template == null) {
						throw new IllegalArgumentException("caractère sans clé dans " + id + " : « " + ch + " »");
					}
					cells.add(new Cell(x, y, z, template));
				}
			}
		}
		String kind = json.has("kind") ? json.get("kind").getAsString() : "house";
		Optional<Identifier> upgrade = json.has("upgrade")
				? Optional.of(Identifier.parse(json.get("upgrade").getAsString())) : Optional.empty();
		TownTier tier = json.has("tier") ? TownTier.fromSerializedName(json.get("tier").getAsString())
				: TownTier.VILLAGE;
		boolean legacy = json.has("legacy") && json.get("legacy").getAsBoolean();
		return new Blueprint(id, kind, width, layers.size(), depth, List.copyOf(cells), upgrade, tier, legacy);
	}
}
