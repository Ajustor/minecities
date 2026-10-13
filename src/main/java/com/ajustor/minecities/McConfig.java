package com.ajustor.minecities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Les réglages du mod, dans {@code config/minecities.json} (brief 08). Le fichier est créé avec les
 * valeurs par défaut au premier lancement, et réécrit à chaque lancement pour y faire paraître les
 * nouveaux réglages ; une clé absente reprend sa valeur par défaut.
 */
public final class McConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static McConfig current = new McConfig();

	/** Sous ce nombre d'habitants, des nouveaux venus arrivent d'eux-mêmes. */
	@SerializedName("newcomer_minimum")
	public int newcomerMinimum = 5;
	/** Un nouveau venu toutes les tant de ticks au plus (sous le minimum). */
	@SerializedName("newcomer_delay")
	public int newcomerDelay = 400;
	/** La faim se vide en tant de ticks (24 000 : une journée). */
	@SerializedName("hunger_day_ticks")
	public int hungerDayTicks = 24000;
	/** Les citoyens au plus mal pendant un jour quittent-ils la ville ? */
	@SerializedName("miserable_citizens_leave")
	public boolean miserableCitizensLeave = true;
	/** Des raids de pillards attaquent-ils les villes ? */
	@SerializedName("raids")
	public boolean raids = true;
	/** À partir de combien d'habitants une ville tente les pillards. */
	@SerializedName("raid_min_population")
	public int raidMinPopulation = 8;
	/** Au crépuscule, une chance sur tant d'un raid (quand l'espacement le permet). */
	@SerializedName("raid_odds")
	public int raidOdds = 3;
	/** Au moins tant de jours entre deux raids sur une même ville. */
	@SerializedName("raid_spacing_days")
	public int raidSpacingDays = 3;
	/** Ce que coûte une fête, en émeraudes. */
	@SerializedName("festival_cost")
	public int festivalCost = 4;
	/** Ce que le grenier garde de chaque marchandise avant que le marchand ne vende. */
	@SerializedName("market_reserve")
	public int marketReserve = 32;
	/** Ce qu'un marchand gagne au plus par jour, en émeraudes. */
	@SerializedName("market_daily_earnings")
	public int marketDailyEarnings = 32;
	/**
	 * Pour les villes fondées avant les rangs d'habitants : leurs bâtiments déclarés sont-ils protégés
	 * (seuls le maire et ses adjoints y cassent des blocs et les plaques) ? Désactivé par défaut, comme
	 * avant. Les villes nouvelles règlent ce droit par rang, dans l'onglet Habitants de l'hôtel de ville.
	 */
	@SerializedName("protect_buildings")
	public boolean protectBuildings = false;
	/** Les catastrophes (incendie par orage, disette, tremblement de terre) frappent-elles les villes ? */
	@SerializedName("disasters")
	public boolean disasters = true;
	/** Pas plus d'une catastrophe par ville en tant de jours de jeu (le tremblement de terre : le double). */
	@SerializedName("disaster_spacing_days")
	public int disasterSpacingDays = 10;
	/** Le mandat du maire dure tant de jours de jeu ; à son terme, une élection (0 : pas d'élection d'office). */
	@SerializedName("term_days")
	public int termDays = 7;
	/** Les candidatures durent tant de jours de jeu. */
	@SerializedName("candidacy_days")
	public int candidacyDays = 1;
	/** Le vote (et chaque nouveau tour en cas d'égalité) dure tant de jours de jeu. */
	@SerializedName("voting_days")
	public int votingDays = 1;
	/** Pas de nouvelle élection, à la demande du maire ou par pétition, avant tant de jours après la précédente. */
	@SerializedName("election_spacing_days")
	public int electionSpacingDays = 3;
	/**
	 * Un hôtel de ville détruit autrement que par le maire (explosion, commande) : la ville attend
	 * qu'on en repose un au même endroit pendant tant de minutes avant de disparaître.
	 */
	@SerializedName("hall_grace_minutes")
	public int hallGraceMinutes = 10;
	/**
	 * Un citoyen jamais revu depuis tant de minutes (60 : trois jours de jeu), alors que sa maison est
	 * chargée, est rayé ; 0 : jamais.
	 */
	@SerializedName("missing_citizen_minutes")
	public int missingCitizenMinutes = 60;
	/** Chaque joueur reçoit le guide de la ville à sa première venue. */
	@SerializedName("give_guide")
	public boolean giveGuide = true;

	public static McConfig get() {
		return current;
	}

	/** Pour les essais : remplace les réglages. */
	public static void set(McConfig config) {
		current = config;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("minecities.json");
		try {
			if (Files.exists(file)) {
				McConfig read = GSON.fromJson(Files.readString(file), McConfig.class);
				current = read != null ? read : new McConfig();
			}
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(current));
		} catch (IOException | RuntimeException e) {
			MineCities.LOGGER.error("Configuration {} illisible : valeurs par défaut", file, e);
			current = new McConfig();
		}
	}
}
