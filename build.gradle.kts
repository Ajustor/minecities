// Le mod, une fois par version de Minecraft (un « nœud » de Stonecutter, dans versions/<version>/) :
// les mêmes sources (src/), un jar par version, build/libs/minecities-<version>+<minecraft>.jar.
plugins {
	id("net.fabricmc.fabric-loom")
}

val minecraft = sc.current.version
version = "${rootProject.extra["modVersion"]}+$minecraft"
group = property("maven_group").toString()

base {
	archivesName = property("archives_base_name").toString()
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("minecities") {
			sourceSet(sourceSets["main"])
			sourceSet(sourceSets["client"])
		}
	}
}

repositories {
	// Jade, pour l'intégration facultative (compat/jade) : Modrinth sert ses versions en Maven.
	exclusiveContent {
		forRepository {
			maven("https://api.modrinth.com/maven") { name = "Modrinth" }
		}
		filter {
			includeGroup("maven.modrinth")
		}
	}
}

dependencies {
	val jade = "maven.modrinth:jade:${sc.properties.get<String>("deps.jade")}"
	minecraft("com.mojang:minecraft:$minecraft")
	implementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
	implementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")
	// Jade n'est pas requis : on compile contre lui, sans l'embarquer. -PminecitiesJade le met dans le jeu
	// des runs et des scènes, pour voir les infobulles.
	compileOnly(jade)
	if (hasProperty("minecitiesJade")) {
		runtimeOnly(jade)
	}

	testImplementation(platform("org.junit:junit-bom:5.13.4"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Les données dont le format change d'une version de Minecraft à l'autre : src/overlays/<version>/
// remplace, pour cette version et les suivantes, les fichiers de même chemin de src/main/resources
// (comme les « overlays » des packs du jeu). Le code, lui, passe par com.ajustor.minecities.compat.
val overlays = rootProject.file("src/overlays").listFiles().orEmpty()
	.filter { it.isDirectory && sc.current.parsed >= it.name }
	.sortedWith(compareBy<File>({ it.name.split('.').getOrNull(0)?.toIntOrNull() }, { it.name.split('.').getOrNull(1)?.toIntOrNull() }))
tasks.processResources {
	overlays.forEach { from(it) }
	// Le dernier copié l'emporte : la version la plus récente qui s'applique.
	duplicatesStrategy = DuplicatesStrategy.INCLUDE

	val props = mapOf("version" to version.toString(), "minecraft" to sc.properties.get<String>("mod.mc_compat"))
	inputs.properties(props)
	filesMatching("fabric.mod.json") { expand(props) }
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
	options.encoding = "UTF-8"
}

java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.test {
	useJUnitPlatform()
	// Les tests lisent les fichiers de langue directement dans le dépôt.
	systemProperty("minecities.resources", rootProject.file("src/main/resources").absolutePath)
	testLogging {
		events("failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
}

tasks.jar {
	from(rootProject.file("LICENSE")) { rename { "${it}_minecities" } }
	// Les jars de chaque version au même endroit.
	destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
}

tasks.named<Jar>("sourcesJar") {
	destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
}

// Les scènes de jeu (src/gametest, voir Scene) : ./gradlew :26.2:runGameTest les joue sur le seul serveur,
// sans fenêtre (la suite d'avant envoi) ; ./gradlew :26.2:runClientGameTest dans le vrai jeu, pour les captures.
fabricApi {
	configureTests {
		createSourceSet = true
		modId = "minecities-gametest"
		enableGameTests = true
		enableClientGameTests = true
		eula = true
	}
}

// -PminecitiesGametest=city,work : ne joue que ces scènes ; -PminecitiesFilm : les films image par image ;
// -PminecitiesLang=fr_fr : les captures dans cette langue.
loom.runs.matching { it.name == "clientGameTest" || it.name == "gameTest" }.configureEach {
	findProperty("minecitiesGametest")?.let { vmArg("-Dminecities.gametest=$it") }
	if (hasProperty("minecitiesFilm")) {
		vmArg("-Dminecities.film=true")
	}
	findProperty("minecitiesLang")?.let { vmArg("-Dminecities.lang=$it") }
}

// Chaque suite sans fenêtre part d'un monde neuf : rien ne reste d'une suite à l'autre.
tasks.matching { it.name == "runGameTest" }.configureEach {
	val world = layout.buildDirectory.dir("run/gameTest/world").get().asFile
	doFirst {
		world.deleteRecursively()
	}
}
