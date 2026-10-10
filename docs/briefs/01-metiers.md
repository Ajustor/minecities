# Brief 01 — Métiers et bâtiments de travail

## Objectif

Donner un travail aux citoyens pour que la ville produise elle-même ce dont elle a besoin, à
commencer par sa nourriture. C'est le cœur de MineColonies (huttes de fermier, boulanger,
bûcheron, mineur, constructeur…), à transposer sans huttes préfabriquées.

## Principe (validé, en place depuis la v0.2)

**Le poste de travail vanilla désigne le métier**, comme pour les villageois. Un atelier est une
pièce déclarée par la plaque de maison (même relevé `HouseScanner`, porte obligatoire, pas de lit
requis) qui contient des postes de travail. Un poste = un emploi, donné au premier citoyen sans
emploi (`Town.assignJobs`). Un bâtiment peut être maison et atelier à la fois.

Les métiers **réclament leurs outils** (réponse à la question 12) : pris dans les coffres de
l'atelier, sinon au grenier ; ils s'usent ; s'il n'y en a pas, le maire est prévenu (une fois par
jour) et le travail attend.

## État

| Métier | Poste | Outil | Ce qu'il fait | État |
|---|---|---|---|---|
| Fermier | composteur | houe | récolte les cultures mûres à 16 blocs du composteur, replante, sème la terre nue, livre au grenier en gardant 16 semences de chaque | **fait (v0.2)** |
| Cuisinier | fumoir | — | cuit viandes, poissons, pommes de terre, algues ; 3 blés = 1 pain ; tout revient au grenier | **fait (v0.2)** |
| Bûcheron | bûche écorcée posée seule (billot) | hache | abat les arbres à 16 blocs, replante, ramasse pousses, pommes, bâtons | **fait (v0.3)** |
| Pêcheur | baril à 6 blocs de l'eau (sinon garde-manger) | canne à pêche | pêche depuis la rive (table de pêche vanilla) | **fait (v0.3)** |
| Éleveur | botte de foin posée seule | — | fait naître des petits sous 6 têtes, abat au-delà de 8 adultes, ramasse le butin | **fait (v0.3)** |
| Garde | meule (*grindstone*, le poste du forgeron d'armes) | épée ou arc | patrouille, combat les monstres (brief 06) | v0.3 |
| Bâtisseur | table d'artisan (*crafter*) | — | construit d'après des schémas (brief 09) | **fait (v0.3)** |
| Mineur | ? | pioche | creuse dans une zone marquée | plus tard |
| Forgeron | haut fourneau | — | fond les minerais du grenier | plus tard |
| Bibliothécaire | pupitre | — | bonheur, savoir (brief 04) | plus tard |

Points à trancher pour la suite (ajoutés à QUESTIONS.md) :

- **Pêcheur et baril** : le relevé classe aujourd'hui le baril comme garde-manger. Soit le baril
  devient aussi un poste de pêcheur quand l'atelier touche l'eau, soit on choisit un autre poste.
- **Bûcheron et éleveur** n'ont pas de poste vanilla : choisir un bloc vanilla évocateur (billot =
  bûche écorcée ? botte de foin ?) plutôt qu'ajouter un bloc.

## Architecture en place

- `town/Job` : métier, poste de travail (bloc), outil (étiquette d'objets).
- `town/House.Workstation`, `town/Resident.Employment` : postes relevés et emplois, sauvegardés.
- `citizen/WorkGoal` : horaires (le jour, pas affamé, pas endormi), outil, allées et venues
  (`goTo`), réserves de l'atelier puis grenier, pause en temps de jeu (`rest`).
- `citizen/FarmerGoal`, `citizen/CookGoal`, `citizen/Cooking` : les deux premiers métiers.
- `citizen/CitizenEntity` : inventaire de 9 cases (sauvegardé, lâché à la mort), `walkTo` vise
  une case libre à côté d'un bloc plein, `complain` prévient le maire une fois par jour.
- Scène en jeu `WorkScenes` (`-PminecitiesGametest=work`).

## Pour ajouter un métier

1. Une valeur dans `Job` (poste, outil, clé de traduction `job.minecities.<nom>` et
   `tool.minecities.<nom>`).
2. Un `WorkGoal` dédié, ajouté dans `CitizenEntity.registerGoals` (priorité 4).
3. Une étape dans une scène en jeu, et les captures dans `captures/`.

## Idées pour plus tard

- Expérience par métier (vitesse, rendement), visible sur la tenue (surcouche par métier).
- Le maire choisit qui travaille où (écran de l'hôtel de ville, brief 05).
- Le fermier laboure la terre à côté de l'eau, plante des citrouilles/pastèques.
