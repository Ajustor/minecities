// Le contrôleur de l'arbre : la version active (celle des sources commitées, que l'IDE affiche) et
// la version du mod, commune à tous les nœuds.
plugins {
	id("dev.kikugie.stonecutter")
	id("net.fabricmc.fabric-loom") version "1.18.3" apply false
}

stonecutter active "26.2"

// Ce qui ne fait que changer de nom d'une version de Minecraft à l'autre : les sources sont écrites
// pour la version active, Stonecutter les réécrit pour les autres. Les changements de forme passent
// par com.ajustor.minecities.compat (voir ARCHITECTURE.md).
stonecutter parameters {
	replacements {
		string(current.parsed >= "26.3") {
			replace("PushReaction.BLOCK", "PushReaction.IMMOVEABLE")
			replace("PushReaction.DESTROY", "PushReaction.POPPED")
			replace("server.getStructureManager()", "server.getStructureTemplateManager()")
			replace("BED_RULE, pos).explodes()", "BED_RULE, pos).destroyOnUse()")
		}
	}
}

// La version du mod suit les tags git (v1.2.3, v1.2.3-beta) :
// - sur un tag, c'est la version du tag (le workflow de release la passe aussi en -PreleaseVersion) ;
// - après un tag, la version suivante en développement : v1.2.3 + 5 commits → 1.2.4-dev.5,
//   v1.3.0-beta + 5 commits → 1.3.0-beta.dev.5 ;
// - sans tag (ou sans git), la version de développement de gradle.properties.
fun tagVersion(): String? = try {
	val described = providers.exec {
		commandLine("git", "describe", "--tags", "--long", "--match", "v[0-9]*")
		isIgnoreExitValue = true
	}.standardOutput.asText.get().trim()
	Regex("""^v(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z.-]+)?-(\d+)-g[0-9a-f]+$""").matchEntire(described)?.let { m ->
		val (major, minor, patch, pre, ahead) = m.destructured
		when {
			ahead == "0" -> "$major.$minor.$patch$pre"
			pre.isNotEmpty() -> "$major.$minor.$patch$pre.dev.$ahead"
			else -> "$major.$minor.${patch.toInt() + 1}-dev.$ahead"
		}
	}
} catch (_: Exception) {
	null
}

val releaseVersion = (findProperty("releaseVersion") ?: "").toString().removePrefix("v")
extra["modVersion"] = releaseVersion.ifEmpty { tagVersion() ?: property("mod_version").toString() }
