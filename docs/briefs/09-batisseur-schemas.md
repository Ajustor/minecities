# Brief 09 — Le bâtisseur et les schémas de bâtiments

## État (v0.3)

Fait : le bâtisseur (table d'artisan), 15 schémas JSON générés par `tools/blueprints/generate.py`
(petite maison → grande maison → manoir ; ferme, cuisine, scierie, étable, pêcherie, atelier du
bâtisseur, chacun suivi de son grand atelier à deux postes ; toits plats d'abord, toits à deux pans
et pierre taillée pour les améliorations), les outils pour dégager (pioche, pelle, hache), plusieurs
bâtisseurs sur un même chantier, 4 styles
(`build/BuildStyle` : plaines, taïga, désert, savane), les commandes `blueprints`, `build`,
`build cancel`, `upgrade`, la liste des matériaux à la commande, l'emprise en particules, la
déclaration automatique du bâtiment fini, et les schémas des datapacks (`data/<ns>/blueprint/`).
Choix faits : schémas en JSON plutôt qu'en `.nbt` (lisibles, déclinables en styles). Reste à faire : l'objet « plan » et l'aperçu fantôme côté client, les schémas
enregistrés avec un bloc de structure vanilla, plusieurs bâtisseurs sur plusieurs chantiers, et
davantage de niveaux d'amélioration pour les ateliers.

## Objectif

Réponse à la question 8 : tout le monde peut déclarer une maison, et il faut **aussi** un
bâtisseur qui construit d'après des **schémas**, comme le constructeur de MineColonies. Le joueur
garde la liberté de bâtir à la main ; le bâtisseur est une aide pour faire grandir la ville.

## Principe vanilla-friendly

- **Les schémas sont des structures vanilla** : le format `.nbt` des blocs de structure. On en
  fournit quelques-uns avec le mod (maison de 2 lits, maison de 4 lits, atelier de fermier avec
  champ, cuisine, entrepôt), et **le joueur peut enregistrer les siens** avec un bloc de structure
  vanilla (mode SAVE) : ils deviennent disponibles dans sa ville.
- **Le bâtisseur** travaille à un poste vanilla (proposition : la **table d'artisan** ou
  l'**établi**). Il construit bloc par bloc, en prenant les matériaux au grenier et dans les
  coffres de son atelier ; ce qui manque est signalé au maire (liste des matériaux).
- Chaque schéma livré contient déjà sa **plaque de maison** : une fois fini, le bâtiment est
  déclaré tout seul.

## Commander une construction

1. Le maire (ou un adjoint) prend un **plan** : un objet qu'on obtient à l'hôtel de ville (onglet
   « Plans » de l'écran du brief 05) ou par commande `/ville build <schéma>` en attendant l'écran.
2. Il pose le plan au sol : un **aperçu fantôme** (rendu client des blocs en transparence) montre
   l'emprise ; on peut le tourner (comme un bloc orienté) avant de valider.
3. La commande s'ajoute à la **file de chantiers** de la ville ; le bâtisseur s'y met.

## Le chantier

- Ordre de pose : fondations, murs, toit, puis portes, lits et décor (les blocs « attachés »
  comme les torches et les portes en dernier).
- Le bâtisseur dégage d'abord le terrain (les blocs retirés vont au grenier) ; il ne casse jamais
  un bloc d'un bâtiment déclaré ni hors du territoire.
- Vitesse : un bloc par seconde au début, plus avec l'expérience.
- Il lui faut une **pelle** et une **pioche** pour dégager (outils, réponse à la question 12).
- Un chantier peut être suspendu, annulé (les matériaux posés restent), repris.

## Données et code

- `town/Blueprint` : un schéma (id, nom, taille, liste de blocs avec états, matériaux requis),
  lu depuis `data/minecities/blueprints/*.nbt` (ressources du mod et datapacks) et depuis les
  structures enregistrées dans le monde (`StructureTemplateManager`).
- `town/Construction` : chantier (schéma, origine, rotation, étape, blocs restants), sauvegardé
  dans la ville ; file de chantiers dans `Town`.
- `citizen/BuilderGoal` (un `WorkGoal`).
- Client : rendu de l'aperçu (comme Litematica, en simple), item `blueprint`.

## Critères

- Scène en jeu : le maire commande une maison de 2 lits, le grenier contient les matériaux, le
  bâtisseur dégage, construit, et la maison est déclarée ; deux citoyens y emménagent.
- Tests unitaires : ordre de pose, matériaux requis après rotation.

## Questions

- Liste des schémas livrés avec le mod, et style (plaines, désert, taïga… comme les villages
  vanilla ?).
- Le bâtisseur peut-il aussi **agrandir/améliorer** un bâtiment existant (MineColonies a des
  niveaux de huttes) ?
