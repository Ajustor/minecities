pluginManagement {
	repositories {
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	// Plusieurs versions de Minecraft depuis les mêmes sources : https://stonecutter.kikugie.dev
	id("dev.kikugie.stonecutter") version "0.9.8"
}

// Une version de Minecraft (« nœud ») par jar. Les versions et leurs dépendances sont dans
// stonecutter.properties.toml.
stonecutter {
	create(rootProject) {
		versions("26.2", "26.3")
		// La version des sources telles qu'elles sont commitées.
		vcsVersion = "26.2"
	}
}

rootProject.name = "minecities"
