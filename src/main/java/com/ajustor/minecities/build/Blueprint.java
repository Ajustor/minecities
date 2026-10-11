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
 */
public record Blueprint(Identifier id, String kind, int width, int height, int depth, List<Cell> cells,
		Optional<Identifier> upgrade, TownTier tier) {
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
		return new Blueprint(id, kind, width, layers.size(), depth, List.copyOf(cells), upgrade, tier);
	}
}
