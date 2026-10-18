# Brief 03 — Croissance : du village à la mégalopole

## Objectif

Que chaque palier change quelque chose au jeu, au-delà du nom et du rayon. MineColonies fait
progresser par le niveau de l'hôtel de ville et des huttes ; ici le palier suit la population et
les bâtiments, et débloque des choses.

## Déjà en v0.1

`TownTier` : Village (0 citoyen), Ville (10 citoyens, 4 maisons), Grande ville (30, 12),
Mégalopole (75, 30). Rayon 48 → 80 → 128 → 224. Annonce à tous les joueurs à la montée.

## État (v0.7)

- `Job.minTier()` : un poste dont le métier n'est pas ouvert attend (le marchand à la ville) ;
  l'écran le montre « verrouillé ». Les schémas ont un champ `tier` (le marché à la ville).
- Montée de palier : feux d'artifice, titre à l'écran, et la liste de ce qui s'ouvre (métiers,
  schémas, annexes, rayon).
- **Mairies annexes** (`block/TownAnnexBlock`) : 2 en grande ville, 4 en mégalopole ; posées par le
  maire sur le territoire, à 40 blocs au moins de l'hôtel de ville et des autres ; elles étendent le
  territoire de 48 blocs et gardent un grenier de quartier. Si le palier baisse, les dernières posées
  ne servent plus.
- `/ville admin tier <palier>|auto` impose un palier (serveurs, essais).

## Ce que chaque palier pourrait débloquer

Les **monuments** (MC-11) sont faits : statue, fontaine et beffroi (`town/Monument`), schémas de
genre `monument` ouverts à la mégalopole, sans plaque, un de chaque par ville (`Builds.check`). Bâtis
(chantier fini), ils donnent du bonheur (+1, au plus +2) et de l'attrait (`Town.appeal()`, 1, 1 et
2), qu'une taverne pourra utiliser pour attirer des nouveaux venus.

Ce que le code débloque aujourd'hui (`Job.minTier()`, le palier des schémas, `TownTier`), aligné le
18/10/2026 (MC-69) :

| Palier | Débloque |
|---|---|
| Village (rayon 48) | les maisons ; tous les métiers sauf ceux de la ville (fermier, cuisinier, boulanger, bûcheron, éleveur, pêcheur, bâtisseur, livreur, garde, archer, inventeur, médecin, mineur, maçon, verrier, tisserand, apiculteur, tanneur, charpentier, tavernier) ; les bâtiments de niveau 1 et 2 ; décrets ; élections (si plusieurs joueurs) |
| Ville (rayon 80) | marchand, forgeron, joaillier, bibliothécaire ; les bâtiments de niveau 3 |
| Grande ville (rayon 128) | les bâtiments de niveau 4 ; deux mairies annexes ; à venir : **routes reconnues**, **gare et wagonnets**, **marché hebdomadaire** (tickets MC-86 à MC-88) |
| Mégalopole (rayon 224) | quatre mairies annexes ; les monuments (MC-11 : statue, fontaine, beffroi) ; à venir : **titre de gouverneur**, **ambassades** (tickets MC-89, MC-90) |

## Pistes

- **Exigences qualitatives** en plus du nombre : diversité des métiers, bonheur moyen (brief 04),
  un bâtiment public (marché, église = pièce avec une cloche ?).
- **Adopter un village vanilla** (fait en v0.2) : les villageois du territoire restent des
  villageois (avec leurs échanges) mais **comptent dans la population** (`TownService.census`,
  toutes les 5 s). Suite possible : relever automatiquement les maisons du village (depuis les
  lits du POI vanilla), et laisser des villageois sans emploi devenir citoyens.
- **Quartiers** pour la mégalopole : un hôtel de ville unique ne suffit plus à 224 blocs ; des
  **mairies annexes** (bloc dérivé) étendent le territoire et servent de greniers secondaires.
- **Descente de palier** : le palier redescend si la population baisse (validé, question 6).
- **Limite de population** liée au palier (le village plafonne à 20 tant qu'il n'a pas 4
  maisons…), pour pousser à construire.

## Points d'entrée

- `TownTier` (seuils, déblocages : `Set<Feature>` par palier), `Town.updateTier()`,
  `TownService.announceTier()` (titre à l'écran, feu d'artifice ?).

## Critères

- Tests unitaires des seuils et déblocages ; scène en jeu d'une ville qui passe au palier Ville.
