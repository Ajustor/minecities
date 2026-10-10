# Brief 01 — Métiers et bâtiments de travail

## Objectif

Donner un travail aux citoyens pour que la ville produise elle-même ce dont elle a besoin, à
commencer par sa nourriture. C'est le cœur de MineColonies (huttes de fermier, boulanger,
bûcheron, mineur, constructeur…), à transposer sans huttes préfabriquées.

## Principe vanilla-friendly

**Le poste de travail vanilla désigne le métier**, comme pour les villageois. Un bâtiment de
travail est une pièce reconnue comme une maison (même `HouseScanner`, porte obligatoire, pas de
lit requis) qui contient un poste de travail ; on la déclare avec la même plaque (ou une
« plaque d'atelier », à trancher : voir QUESTIONS.md).

| Métier | Poste de travail | Ce qu'il fait | Priorité |
|---|---|---|---|
| Fermier | composteur | récolte les cultures mûres du territoire (blé, carottes, pommes de terre, betteraves), replante, dépose au grenier | **v0.2** |
| Boulanger | fumoir ou four (fourneau) | transforme blé → pain, cuit pommes de terre/viandes crues du grenier | v0.2 |
| Bûcheron | table de bûcheron (*stonecutter* ?) ou billot à définir | abat les arbres d'une zone désignée, replante les pousses | v0.3 |
| Pêcheur | baril (comme le villageois) | pêche dans l'eau proche, dépose le poisson | v0.3 |
| Éleveur | ? (pas de poste vanilla dédié) | nourrit et fait se reproduire les animaux d'un enclos, abat le surplus | v0.3 |
| Mineur | ? | creuse un puits et des galeries dans une zone marquée | plus tard |
| Constructeur | table d'artisan | répare/construit d'après un modèle (voir plus bas) | plus tard |
| Garde | établi de fabrication d'armes (*grindstone*/meule) | patrouille, combat les monstres (brief 06) | v0.3 |
| Bibliothécaire / instituteur | pupitre | augmente le savoir/bonheur, débloque des choses | plus tard |

Points de design :

- **Affectation** : automatique (le premier citoyen sans métier, ou le plus proche) ; le maire
  peut réaffecter par l'écran de l'hôtel de ville (brief 05). Un atelier a un nombre de postes =
  nombre de postes de travail dans la pièce.
- **Zone de travail** : le fermier travaille les champs du territoire **les plus proches de son
  atelier** (rayon 16 à 32, croissant avec le niveau ?). Pas de bloc de « champ » à poser.
- **Outils** : comme MineColonies, un métier peut demander un outil (houe, hache, canne à pêche)
  pris au grenier ou dans les coffres de l'atelier, avec usure. Vanilla-friendly et crée un
  besoin de ravitaillement. À valider (QUESTIONS.md).
- **Journée de travail** : travail le jour, repas, sommeil la nuit (le `SleepInBedGoal` passe
  avant). Le travail s'arrête s'il a faim (l'`EatGoal` passe avant).
- **Niveau / expérience** : chaque métier gagne de l'expérience (vitesse, rendement), visible
  sur sa tenue (comme les villageois). Facultatif pour la v0.2.

## Points d'entrée dans le code

- `town/House` → généraliser en `Building` avec un `kind` (`HOUSE`, `WORKSHOP`) et la liste des
  postes de travail relevés (`HouseScanner.Cell.WORKSTATION`, avec le type de bloc).
- `town/Resident` : ajouter `job` (métier + atelier).
- `citizen/` : un `WorkGoal` par métier (ou un `Job` abstrait avec `canWork`/`tick`), en priorité
  entre `EatGoal` et la promenade. Pour le fermier : chercher dans la zone un `CropBlock` à âge
  max, y aller, `level.destroyBlock` avec drops ramassés dans un inventaire de 9 cases du
  citoyen, replanter, rentrer déposer au grenier quand l'inventaire est plein ou le soir.
- Les citoyens doivent pouvoir **porter des objets** : `SimpleContainer` sauvegardé dans
  l'entité, affiché en main (l'outil).

## Critères d'acceptation

- Scène en jeu : une ferme de blé mûr à côté d'un atelier avec composteur ; un fermier récolte,
  replante, et le grenier gagne du blé (puis du pain avec un boulanger).
- Le fermier ne casse rien d'autre que des cultures mûres, ne sort pas du territoire.
- Tests unitaires du choix de la parcelle suivante et de l'affectation des postes.
