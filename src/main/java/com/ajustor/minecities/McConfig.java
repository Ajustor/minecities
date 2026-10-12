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
	 * Les bâtiments déclarés sont-ils protégés : seuls le maire et ses adjoints y cassent des blocs (et
	 * les plaques) ? Désactivé par défaut, pour rester vanilla.
	 */
	@SerializedName("protect_buildings")
	public boolean protectBuildings = false;
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
