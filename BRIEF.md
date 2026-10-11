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

## Ce que contient la version actuelle (v0.4)

| Élément | Où |
|---|---|
| Hôtel de ville : fonde la ville, grenier de 27 cases, état de la ville (accroupi + clic) | `block/TownHallBlock`, `TownHallBlockEntity` |
| Plaque de maison : relève la maison par remplissage depuis l'intérieur | `block/HousePlaqueBlock`, `town/HouseScanner` |
| Villes : maire, adjoints, maisons, citoyens, palier, sauvegarde du monde | `town/Town`, `town/Towns` |
| Vie de la ville : immigration, relevés périodiques, paliers, notifications | `town/TownService` |
| Citoyen : 8 tenues, faim, sommeil dans son lit, repas au coffre ou au grenier, mort de faim possible, ouvre les portes | `citizen/*` |
| Métiers : poste de travail vanilla = emploi ; fermier, cuisinier, bûcheron, éleveur, pêcheur, bâtisseur | `town/Job`, `citizen/*Goal` |
| Bâtisseur : schémas JSON (datapacks), 4 styles, chantiers commandés, agrandissements | `build/*`, `citizen/BuilderGoal` |
| Villages vanilla repris : villageois comptés, maisons reprises, villageois sans métier naturalisés | `TownService.census` |
| Familles : 5 fondateurs, plus d'immigration au-delà, affinités, mariages acceptés par le maire, naissances, enfants qui grandissent | `town/Families`, `town/MarriageRequest` |
| Écran de l'hôtel de ville : résumé, citoyens, registre des mariages, grenier | `network/*`, `client/screen/TownHallScreen` |
| Animations : un geste par action (manger, houe, hache, pêche, bâtir, cuisiner, nourrir, parler, liesse) | `citizen/Action`, `client/render/CitizenModel` |
| Hôtel de ville en bâtiment miniature (modèle conçu dans Blockbench, textures vanilla) | `tools/models/town_hall.py` |
| Commandes `/minecities` (alias `/ville`) | `command/TownCommands` |
| Tests unitaires (relevé des bâtiments, ville, emplois, faim, langues) et scènes en jeu vérifiées (`city`, `work`, `countryside`, `village`, `build`) | `src/test`, `src/gametest` |

### Règles

- **Bâtiment** : depuis la plaque, on remplit l'espace libre (air, torches, tapis…). Les murs sont
  les blocs qui bloquent le mouvement, plus vitres, barreaux, clôtures et murets ; portes, trappes
  et portillons ferment une ouverture et comptent comme porte. L'intérieur doit rester sous
  2048 blocs et tenir dans un cube de 32. Il faut au moins une porte, et des lits (une **maison** :
  un citoyen par lit, la tête du lit compte) et/ou des postes de travail (un **atelier** : un
  emploi par poste). Un lit n'appartient qu'à une maison, une pièce n'a qu'une plaque. N'importe
  quel joueur peut déclarer un bâtiment sur le territoire d'une ville.
- **Métiers** : composteur = fermier (houe requise, prise dans les coffres de l'atelier ou au
  grenier ; champs à 16 blocs du composteur), fumoir = cuisinier (cuit viandes, poissons, pommes
  de terre ; 3 blés = 1 pain). Les postes sont donnés aux citoyens sans emploi, dans l'ordre
  d'arrivée. Travail le jour, quand ils n'ont pas faim. Bûche écorcée (billot) = bûcheron (hache),
  botte de foin = éleveur, baril à 6 blocs d'eau = pêcheur (canne), table d'artisan = bâtisseur ;
  billot et botte ne comptent que posés seuls au sol (rien de plein dessus).
- **Chantiers** : `/ville build <schéma> [style]` place le bâtiment devant le maire ; le bâtisseur
  dégage puis pose (un bloc par demi-seconde), matériaux pris au grenier ; fini, le bâtiment se
  déclare. `/ville upgrade` rebâtit au même endroit d'après le schéma suivant (toit plat d'abord,
  toit à deux pans et pierre taillée ensuite ; ateliers agrandis à deux postes). Le bâtisseur
  dégage avec pioche, pelle ou hache selon le bloc ; il travaille du dehors (8 blocs de portée)
  ou du haut d'échafaudages qu'il dresse puis démonte ; plusieurs bâtisseurs se partagent un
  chantier en réservant chacun un bloc ; un livreur (table de cartographie) leur porte les
  matériaux ; qui se tient sur un bloc à poser est prié de s'écarter.
- **Parole** : les citoyens disent ce qui leur arrive (bulle et chat à 16 blocs) ; leurs besoins
  non satisfaits sont répétés à qui clique sur eux.
- **Sommeil** : pas de sommeil là où les lits explosent (Nether, End).
- **Population** : sous 5 habitants, un nouveau venu toutes les 20 secondes ; au-delà, pas
  d'immigration. Deux citoyens à moins de 5 blocs gagnent 3 points d'affinité toutes les
  5 secondes ; à 70, ils demandent à se marier (une demande à la fois). Marié, un couple emménage
  dans une maison qui a deux lits pour lui. La nuit, un couple logé ensemble, repu (faim ≥ 16),
  avec un lit libre chez lui et pas de naissance depuis un jour, a une chance sur 200 par relevé
  d'avoir un enfant ; l'enfant est adulte en 3 jours, ne travaille pas, mange moitié moins.
- **Faim** : 0 à 20, la barre se vide en une journée de jeu. Sous 12, le citoyen va manger
  (coffres de sa maison, puis grenier) jusqu'à 18. À 0, il ralentit et perd un cœur toutes les
  30 secondes, jusqu'à en mourir. Repu (≥ 16), il se soigne.
- **Sommeil** : de 12 500 à 23 500 (heure de l'Overworld), chacun rejoint son lit.
- **Territoire** : un disque autour de l'hôtel de ville ; deux hôtels de ville doivent être à
  plus de 96 blocs (et hors du territoire l'un de l'autre). Un joueur peut diriger plusieurs
  villes. Les villageois vanilla du territoire comptent dans la population (palier), sans devenir
  citoyens. Villes possibles dans toutes les dimensions.
- **Grenier** : ouvert à tous.

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
[QUESTIONS.md](QUESTIONS.md). Une branche par version, fusionnée par pull request dans `master`,
puis un tag `vX.Y.0` qui publie la release.

**Ordre retenu** (les briefs « dans l'ordre logique ») :

1. **v0.4** (fait) — brief 10 (familles, mariages, naissances, animations) avec l'écran de
   l'hôtel de ville du brief 05.
2. **v0.5** (fait) — brief 06, première partie : caserne, tour de garde, gardes, archers, inventeur
   et golems, raids de pillards par vagues.
3. **v0.6** (fait) — brief 04 (repas variés, bonheur, médecin), le reste du brief 05 (HUD,
   bâtiments à l'écran, plaques colorées, couvre-chefs des métiers) et du brief 06 (cloche, fêtes).
4. **v0.7** — brief 07 (trésor, marché, commerce) puis brief 03 (déblocages par palier).
5. **v0.8** — brief 08 (configuration, permissions, protections, performances, compatibilité).
6. **v1.x** — suites du brief 09 (aperçu fantôme, plans, structures enregistrées) et du brief 01
   (garde, mineur, forgeron, bibliothécaire).
7. **v2** — brief 02 (habitants joueurs, rangs, décrets, élections).

| # | Brief | Résumé |
|---|---|---|
| 01 | [Métiers et bâtiments de travail](docs/briefs/01-metiers.md) | Six métiers faits ; suite : garde, mineur, forgeron, bibliothécaire |
| 02 | [Le maire et la vie politique](docs/briefs/02-maire-politique.md) | Rangs et habitants joueurs, décrets, budget ; élections en v2 |
| 03 | [Croissance et paliers](docs/briefs/03-croissance.md) | Ce que débloque chaque palier, adopter un village vanilla, quartiers de la mégalopole |
| 04 | [Faim, santé et bonheur](docs/briefs/04-faim-bonheur.md) | Fait en v0.6 : repas variés, bonheur, médecin ; suite : maladies |
| 05 | [Interface et rendu](docs/briefs/05-interface-rendu.md) | Fait en v0.4–0.6 : écran, HUD, plaques, couvre-chefs ; suite : carte du territoire |
| 06 | [Défense et événements](docs/briefs/06-defense-evenements.md) | Gardes, archers, inventeur, raids (v0.5), cloche et fêtes (v0.6) ; suite : marché, catastrophes |
| 07 | [Économie et commerce](docs/briefs/07-economie.md) | Trésor, marché, échanges avec les villageois et entre villes |
| 08 | [Robustesse et multijoueur](docs/briefs/08-robustesse.md) | Permissions, protections, configuration, performance, compatibilité |
| 10 | [Familles, mariages, naissances et animations](docs/briefs/10-familles-animations.md) | Fait en v0.4 : 5 fondateurs, mariages acceptés par le maire, naissances, animations |
| 09 | [Bâtisseur et schémas](docs/briefs/09-batisseur-schemas.md) | Fait en v0.3 ; suite : aperçu fantôme, objet plan, schémas enregistrés au bloc de structure |
