# MineCities — brief général

## Vision

Un mod de villes « à la MineColonies », mais **vanilla-friendly** :

- **On construit librement.** Pas de schématiques imposées ni de huttes à poser : le mod reconnaît
  ce que le joueur bâtit (une pièce close, une porte, des lits) et lui donne un rôle. Les blocs
  vanilla sont le langage du jeu : un lit est un logement, un coffre un garde-manger, un composteur
  désignera un fermier, un fourneau un forgeron (voir les briefs).
- **Peu de blocs ajoutés**, et chacun a une raison d'être : l'hôtel de ville (la ville), la plaque
  de maison (la déclaration d'un bâtiment). Pas d'inventaire de 200 objets.
- **Des citoyens qui vivent** à un rythme lisible : ils se promènent, dorment, mangent, et
  partent si on les néglige.
- **La ville grandit** : village → ville → grande ville → mégalopole, avec un territoire qui
  s'étend et, plus tard, des choses débloquées à chaque palier.
- **Le maire** : celui qui fonde la ville la dirige, avec ses adjoints ; plus tard, élections,
  budget et décrets.

## Ce que contient la v0.1

| Élément | Où |
|---|---|
| Hôtel de ville : fonde la ville, grenier de 27 cases, état de la ville (accroupi + clic) | `block/TownHallBlock`, `TownHallBlockEntity` |
| Plaque de maison : relève la maison par remplissage depuis l'intérieur | `block/HousePlaqueBlock`, `town/HouseScanner` |
| Villes : maire, adjoints, maisons, citoyens, palier, sauvegarde du monde | `town/Town`, `town/Towns` |
| Vie de la ville : immigration, relevés périodiques, paliers, notifications | `town/TownService` |
| Citoyen : 8 tenues, faim, sommeil dans son lit, repas au coffre ou au grenier, départ s'il meurt de faim | `citizen/*` |
| Commandes `/minecities` (alias `/ville`) | `command/TownCommands` |
| Tests unitaires (relevé des maisons, ville, faim, langues) et scène en jeu vérifiée | `src/test`, `src/gametest` |

### Règles de la v0.1

- **Maison** : depuis la plaque, on remplit l'espace libre (air, torches, tapis…). Les murs sont
  les blocs qui bloquent le mouvement, plus vitres, barreaux, clôtures et murets ; portes, trappes
  et portillons ferment une ouverture et comptent comme porte. L'intérieur doit rester sous
  2048 blocs et tenir dans un cube de 32. Il faut au moins une porte et un lit (la tête du lit
  compte). Un lit n'appartient qu'à une maison.
- **Immigration** : toutes les 2 minutes au plus, s'il reste un lit libre et que le grenier
  contient de la nourriture saine (ni chair putréfiée, ni œil d'araignée…).
- **Faim** : 0 à 20, la barre se vide en une journée de jeu. Sous 12, le citoyen va manger
  (coffres de sa maison, puis grenier) jusqu'à 18. À 0, il ralentit et perd des PV (jamais sous
  2) ; au bout d'une journée à 0, il quitte la ville. Repu (≥ 16), il se soigne.
- **Sommeil** : de 12 500 à 23 500 (heure de l'Overworld), chacun rejoint son lit.
- **Territoire** : un disque autour de l'hôtel de ville ; deux hôtels de ville doivent être à
  plus de 96 blocs (et hors du territoire l'un de l'autre). Un joueur n'est maire que d'une ville.

## Architecture

- `com.ajustor.minecities.town` : le modèle (`Town`, `House`, `Resident`, `TownTier`), la
  sauvegarde (`Towns`, un `SavedData` de l'Overworld) et la logique serveur (`TownService`).
  `HouseScanner`, `TownTier`, `Names` et `citizen.Hunger` n'utilisent pas Minecraft : ils se
  testent seuls.
- **La ville est la source de vérité** pour qui habite où (`Resident`), même entité déchargée.
  L'entité citoyen garde sa faim et sa tenue, et relit la ville toutes les 5 secondes.
- Tout tourne côté serveur, une fois toutes les 5 secondes (`TownService.tick`).
- Code Minecraft 26.2 non obfusqué (noms Mojang), Fabric API, Java 25. Même base que le mod FMAB.

## Conventions

- Code et commentaires en français, comme FMAB ; noms de classes en anglais.
- Tout texte affiché passe par `fr_fr.json` et `en_us.json` (même clés, même nombre de `%s` :
  `LangTest` le vérifie).
- Les textures viennent de `tools/textures/generate.py`.

### Commits et changelog

Commits conventionnels en français : `feat(ville): …`, `fix(citoyens): …`, `docs: …`, `test: …`,
`ci: …`, `chore: …`. Chaque `feat`/`fix`/`perf` porte deux lignes lues par git-cliff pour le
changelog bilingue de la release :

```
Changelog-fr: Les citoyens dorment dans leur lit la nuit
Changelog-en: Citizens sleep in their bed at night
```

`.github/scripts/check_commits.py` refuse une pull request qui oublie ces lignes.

## Feuille de route

Les briefs détaillés sont dans [docs/briefs](docs/briefs) ; les questions ouvertes dans
[QUESTIONS.md](QUESTIONS.md).

| # | Brief | Résumé |
|---|---|---|
| 01 | [Métiers et bâtiments de travail](docs/briefs/01-metiers.md) | Le poste de travail vanilla désigne le métier : fermier, boulanger, bûcheron, pêcheur, mineur, garde… |
| 02 | [Le maire et la vie politique](docs/briefs/02-maire-politique.md) | Élections, mandats, adjoints, décrets, budget et impôts en émeraudes |
| 03 | [Croissance et paliers](docs/briefs/03-croissance.md) | Ce que débloque chaque palier, adopter un village vanilla, quartiers de la mégalopole |
| 04 | [Faim, santé et bonheur](docs/briefs/04-faim-bonheur.md) | Variété des repas, bonheur, maladies, naissances |
| 05 | [Interface et rendu](docs/briefs/05-interface-rendu.md) | Écran de l'hôtel de ville, carte du territoire, modèles Blockbench, noms au-dessus des têtes |
| 06 | [Défense et événements](docs/briefs/06-defense-evenements.md) | Gardes, raids, cloche d'alarme, fêtes et marchés |
| 07 | [Économie et commerce](docs/briefs/07-economie.md) | Trésor, marché, échanges avec les villageois et entre villes |
| 08 | [Robustesse et multijoueur](docs/briefs/08-robustesse.md) | Permissions, protections, configuration, performance, compatibilité |
