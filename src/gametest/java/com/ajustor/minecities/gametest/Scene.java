package com.ajustor.minecities.gametest;

import java.util.List;

/**
 * Une scène de jeu : un monde neuf, une histoire jouée pas à pas et vérifiée à chaque étape. Elle se
 * joue avec fenêtre ({@code ./gradlew runClientGameTest}, pour les captures) ou sans
 * ({@code ./gradlew runGameTest}, bien plus vite) ; {@code -PminecitiesGametest=city,work} n'en joue que
 * quelques-unes.
 */
public interface Scene {
	/** Toutes les scènes, dans l'ordre où elles se jouent. */
	List<Scene> ALL = List.of(new DataScenes(), new CityScenes(), new WorkScenes(), new CountrysideScenes(), new VillageScenes(),
			new BuildScenes(), new FamilyScenes(), new RaidScenes(), new LifeScenes(), new MarketScenes(),
			new RobustScenes(), new IndustryScenes(), new CraftScenes(), new CouncilScenes(), new CaravanScenes(), new StyleScenes(),
			new LogisticsScenes(), new GuideScenes(), new StructureScenes(), new PlanScenes(),
			new JadeScenes(), new ResidentScenes(), new ElectionScenes(),
			new MonumentScenes(), new HealthScenes(), new MapScenes(), new DisasterScenes(),
			new StationScenes(), new MineScenes(), new BudgetScenes(), new ShortageScenes(),
			new TavernScenes(), new LanguageScenes());

	/** Son nom, pour {@code -PminecitiesGametest}. */
	String name();

	/** Une scène qui ne vérifie rien, faite seulement pour les captures : sans fenêtre, on la passe. */
	default boolean visual() {
		return false;
	}

	void play(Stage stage);

	/** Les scènes demandées par {@code -Dminecities.gametest} (toutes, sans la propriété). */
	static List<Scene> selected() {
		String only = System.getProperty("minecities.gametest");
		if (only == null || only.isBlank()) {
			return ALL;
		}
		List<String> names = List.of(only.split(","));
		return ALL.stream().filter(s -> names.contains(s.name())).toList();
	}
}
