# Brief 08 — Robustesse, multijoueur et configuration

## Objectif

Rendre le mod sûr sur un serveur et réglable, avant une diffusion large (Modrinth/CurseForge).

## Configuration

Un fichier `config/minecities.json` (voir `FmabConfig` dans FMAB pour le patron) :
délai d'immigration, durée de la barre de faim, seuils et rayons des paliers, distance minimale
entre villes, taille maximale d'une maison, une ville par maire ou non, nourriture refusée.

## Multijoueur et protections

- Qui peut ouvrir le grenier, déclarer une maison, casser une plaque (brief 02 : rangs).
- Protection facultative des bâtiments déclarés contre les non-habitants (réglable, désactivée
  par défaut pour rester vanilla).
- Hôtel de ville détruit par une explosion : aujourd'hui très résistant (1200) mais la ville est
  dissoute s'il disparaît. Option : la ville survit et attend un nouvel hôtel de ville posé par
  le maire au même endroit pendant N minutes.

## Cas limites connus de la v0.1

- **Citoyens déchargés** : la ville les compte toujours (c'est voulu). Un citoyen supprimé par
  commande (`/kill` passe par `die`, mais `/tp` dans le vide ou un mod tiers) peut laisser un
  `Resident` fantôme : ajouter un ménage (citoyen jamais vu depuis N jours alors que son lit est
  chargé → retiré).
- **Maisons hors chunks chargés** : non relevées (on garde le dernier relevé).
- **Relevé coûteux** : 2048 blocs par maison toutes les 5 minutes. Avec 30+ maisons, étaler les
  relevés sur plusieurs ticks.
- **Dimensions** : une ville peut être fondée dans le Nether/l'End (le sommeil y est réglé sur
  l'heure de l'Overworld, et les lits explosent dans le Nether !). Interdire hors Overworld ?
- **Deux plaques dans la même pièce** : deux maisons se disputent les lits ; le premier relevé
  garde ses lits, l'autre devient invalide. Mieux : refuser la seconde plaque explicitement.

## Performance

- `TownService.tick` toutes les 100 ticks ; `Towns.at` parcourt toutes les villes (index
  spatial par région si beaucoup de villes).
- IA des citoyens : goals vanilla légers ; surveiller la recherche de chemin vers le grenier.

## Compatibilité

- Jade/WTHIT : afficher faim et logement d'un citoyen, état d'une plaque.
- REI/EMI : recettes (automatique pour les recettes vanilla).
- Mods de protection (FTB Chunks, etc.) : respecter leurs règles quand un citoyen casse/pose.
