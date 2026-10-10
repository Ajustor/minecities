# Brief 03 — Croissance : du village à la mégalopole

## Objectif

Que chaque palier change quelque chose au jeu, au-delà du nom et du rayon. MineColonies fait
progresser par le niveau de l'hôtel de ville et des huttes ; ici le palier suit la population et
les bâtiments, et débloque des choses.

## Déjà en v0.1

`TownTier` : Village (0 citoyen), Ville (10 citoyens, 4 maisons), Grande ville (30, 12),
Mégalopole (75, 30). Rayon 48 → 80 → 128 → 224. Annonce à tous les joueurs à la montée.

## Ce que chaque palier pourrait débloquer

| Palier | Débloque |
|---|---|
| Village | maisons, fermier, boulanger |
| Ville | bûcheron, pêcheur, garde, marché ; décrets ; élections (si plusieurs joueurs) |
| Grande ville | mineur, constructeur, bibliothèque ; **quartiers** ; routes reconnues |
| Mégalopole | monuments, gare/transports (wagons), ambassades entre villes, titre de « gouverneur » |

## Pistes

- **Exigences qualitatives** en plus du nombre : diversité des métiers, bonheur moyen (brief 04),
  un bâtiment public (marché, église = pièce avec une cloche ?).
- **Adopter un village vanilla** : poser un hôtel de ville dans un village de villageois. Les
  maisons du village ont des lits et des portes : on pourrait les relever automatiquement (lits
  du POI vanilla → recherche de la pièce autour). Les villageois restent des villageois (on ne
  les convertit pas) mais **comptent pour la population** et le village démarre au palier
  correspondant ? Ou bien ils deviennent citoyens ? À trancher (QUESTIONS.md).
- **Quartiers** pour la mégalopole : un hôtel de ville unique ne suffit plus à 224 blocs ; des
  **mairies annexes** (bloc dérivé) étendent le territoire et servent de greniers secondaires.
- **Descente de palier** : aujourd'hui le palier redescend si la population baisse. Faut-il un
  palier « acquis » (MineColonies ne redescend pas) ? À trancher.
- **Limite de population** liée au palier (le village plafonne à 20 tant qu'il n'a pas 4
  maisons…), pour pousser à construire.

## Points d'entrée

- `TownTier` (seuils, déblocages : `Set<Feature>` par palier), `Town.updateTier()`,
  `TownService.announceTier()` (titre à l'écran, feu d'artifice ?).

## Critères

- Tests unitaires des seuils et déblocages ; scène en jeu d'une ville qui passe au palier Ville.
