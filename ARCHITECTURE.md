# Architecture

Le mod tourne sur plusieurs versions de Minecraft (26.2, 26.3) depuis les mêmes sources, comme le mod
FMAB. Deux idées le permettent : un arbre [Stonecutter](https://stonecutter.kikugie.dev) qui construit
chaque version, et une couche de compatibilité pour ce qui change d'une version du jeu à l'autre.

## Les versions de Minecraft (Stonecutter)

```
src/                 les sources, écrites pour la version active (26.2)
  main/, client/     le mod
  gametest/          les scènes de jeu (Scene), sans fenêtre ou dans le vrai jeu
  test/              les tests unitaires
  overlays/<mc>/     les données au format des versions suivantes (générées, voir plus bas)
versions/<mc>/       un projet Gradle par version de Minecraft (« nœud », généré par Stonecutter)
```

Chaque nœud construit son jar, `build/libs/minecities-<version>+<mc>.jar`. Les tâches se lancent par
nœud : `./gradlew :26.3:runClient`, `./gradlew :26.2:runGameTest`. Sans préfixe (`./gradlew build`),
elles tournent sur toutes les versions.

Les sources commitées sont celles de la version active (`stonecutter active` dans
`stonecutter.gradle.kts`, 26.2). Les dépendances de chaque version (Fabric API, Jade, plage de versions
de `fabric.mod.json`) sont dans `stonecutter.properties.toml`.

Ce qui change d'une version à l'autre, du plus simple au plus rare :

1. **Un renommage** (classe, champ, constante) : une règle de remplacement dans
   `stonecutter.gradle.kts`. Le code reste écrit pour la version active, sans commentaire.
2. **Un appel dont la forme change** : une fonction de `com.ajustor.minecities.compat` (`EntityCompat`,
   `BlockCompat`, `SoundCompat`), seul endroit du code où vivent les conditions de version
   (`//? if >=26.3 {`). Le reste du mod appelle la fonction, sans savoir quelle version tourne.
3. **Une signature qu'on surcharge ou qu'un mixin vise** (une méthode de Minecraft redéfinie par une
   classe du mod, ou ciblée par un mixin) : la condition de version directement dans la classe, à
   défaut d'autre moyen (le `codec()` des blocs, disparu en 26.3). Le compilateur ne vérifie pas les
   cibles des mixins : c'est le jeu qui refuse de démarrer, d'où les scènes sur chaque version.

Mieux encore, quand le jeu sait faire lui-même : lire une palette de structure par
`NbtUtils.readBlockState` plutôt que par ses champs, dont les noms changent (`Blueprint.fromStructure`).

### Les données

Les JSON de `src/main/resources/data` sont écrits au format de la version active. Pour les versions
suivantes, `tools/data/migrate.py` en dérive les fichiers dont le format change (tables de butin…) dans
`src/overlays/<version>/`. Le build les pose par-dessus les sources pour cette version et les suivantes,
comme les « overlays » des packs du jeu. On n'écrit donc les données qu'une fois : après avoir touché à
`data/`, `python tools/data/migrate.py`, puis committer les overlays (la CI vérifie qu'ils sont à jour).
Une nouvelle version de Minecraft qui change un format : une fonction de migration de plus dans le
script, ses règles tirées des données du jeu lui-même, comparées d'une version à l'autre.

Les schémas du bâtisseur (`data/minecities/blueprint`) sont au format du mod et ne changent pas ; les
noms de blocs qu'ils citent, si.

La scène `data` vérifie, sur chaque version, que toutes les recettes, tables de butin et schémas du mod
sont bien chargés par le jeu, et que chaque bloc que nomme un schéma, dans chaque style, existe.

Pour travailler sur une autre version dans l'IDE : `./gradlew "Set active project to 26.3"` (les
sources sont réécrites pour elle), puis `./gradlew "Reset active project"` avant de committer.

## Ajouter une version de Minecraft

1. Sa section dans `stonecutter.properties.toml` et son nom dans `settings.gradle.kts`.
2. `./gradlew :<mc>:compileGametestJava` : les erreurs de compilation donnent la liste de ce qui a
   changé ; le corriger selon les trois cas plus haut.
3. Les formats de données : une migration dans `tools/data/migrate.py` si le jeu en a changé.
4. Les scènes sur cette version : `./gradlew :<mc>:runGameTest`, la scène `data` en premier. Ajouter
   la version à la matrice de `.github/workflows/build.yml`.
