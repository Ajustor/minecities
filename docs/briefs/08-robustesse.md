# Brief 08 — Robustesse, multijoueur et configuration

## Objectif

Rendre le mod sûr sur un serveur et réglable, avant une diffusion large (Modrinth/CurseForge).

## État (v0.8)

- `McConfig` (`config/minecities.json`, Gson) : nouveaux venus, faim, départs, raids, fêtes, marché,
  protection, délai de grâce, disparus. Réécrit à chaque lancement pour montrer les nouvelles clés.
- **Protection** (réglage, désactivée par défaut) : un bâtiment déclaré garde son étendue (intérieur
  et murs, relevée par `HouseScanner`) ; hors maire, adjoints et opérateurs, personne n'y casse.
- **Hôtel de ville détruit** autrement que par le maire : la ville attend `hall_grace_minutes`
  (sauvegardé : `orphaned_at`) ; reposé au même endroit par le maire ou un adjoint, elle le retrouve ;
  sinon elle disparaît. Démonté par le maire, elle disparaît aussitôt.
- **Disparus** : un citoyen jamais revu depuis `missing_citizen_minutes`, sa maison (ou l'hôtel de
  ville) chargée, est rayé.
- **Relevés étalés** : 4 bâtiments au plus par ville à chaque passage (toutes les 5 s), chacun
  toutes les 5 minutes.
- Sauvegarde : un test relit une ville avec tous les champs ajoutés depuis la v0.4, et une ville
  d'avant (sans eux).

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
- **Dimensions** : les villes sont permises partout (question 13). Dans le Nether et l'End les
  lits explosent : prévoir que les citoyens n'y dorment pas (ou dorment dans des « lits » propres
  à ces dimensions : à imaginer).

## Performance

- `TownService.tick` toutes les 100 ticks ; `Towns.at` parcourt toutes les villes (index
  spatial par région si beaucoup de villes).
- IA des citoyens : goals vanilla légers ; surveiller la recherche de chemin vers le grenier.

## Compatibilité

- Jade/WTHIT : afficher faim et logement d'un citoyen, état d'une plaque.
- REI/EMI : recettes (automatique pour les recettes vanilla).
- Mods de protection (FTB Chunks, etc.) : respecter leurs règles quand un citoyen casse/pose.
