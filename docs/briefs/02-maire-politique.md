# Brief 02 — Le maire et la vie politique

## Objectif

Faire du maire un vrai rôle, surtout en multijoueur : qui dirige, comment on le choisit, ce qu'il
décide. MineColonies a un « propriétaire » de colonie et des rangs de permission ; ici on veut un
vocabulaire municipal et un peu de jeu politique.

## Déjà en place

- Le fondateur est maire ; **un joueur peut diriger plusieurs villes** (réponse à la question 7),
  et faire d'autres joueurs les maires ou adjoints de ses villes.
- Adjoints (`/ville deputy add|remove`), passation (`/ville mayor <joueur>`), renommage ; les
  commandes du maire visent la ville où il se trouve.
- Habitants joueurs (MC-16) : on demande à s'installer (`/ville join`, ou en dormant dans un lit
  d'une maison déclarée), le maire ou un adjoint accepte (`/ville resident accept|refuse`), on part
  par `/ville leave`. Un joueur n'est habitant que d'une ville. Rangs maire, adjoint, habitant,
  visiteur ; le maire règle par rang (onglet Habitants, ou `/ville permission`) trois droits :
  déclarer une maison, ouvrir le grenier, casser les bâtiments déclarés. Par défaut l'habitant
  déclare et ouvre le grenier, le visiteur rien ; les villes d'avant gardent tout ouvert.
- Élections (MC-17) : mandat de `term_days` jours (7) ; au terme, ou quand le maire le décide
  (`/ville election call`), ou par une pétition de la moitié des habitants (`/ville election sign`),
  un jour de candidatures (`run`) puis un jour de vote (onglet Élection, `/ville vote`). Pas plus
  d'une élection tous les 3 jours. Les citoyens votent avec une chance égale à leur bonheur (heureux
  pour le sortant, malheureux contre), leur voix pèse autant que celle d'un joueur. Égalité : on
  revote entre les ex æquo. Seul joueur, ou sans adversaire : réélu d'office. Pas d'intérim
  automatique (décision du 2026-10-13 : un maire absent, les habitants passent par la pétition).
- Seul le maire peut démolir l'hôtel de ville ; maire et adjoints reçoivent les nouvelles.

## À faire

### Décrets (lois municipales)
Des choix simples avec des effets lisibles, pris par le maire dans l'écran de l'hôtel de ville :
- **Couvre-feu** : citoyens rentrés plus tôt (moins de risques la nuit, moins de travail).
- **Rationnement** : ils mangent moins (faim plus lente), bonheur en baisse.
- **Fête** : un jour de fête, bonheur en hausse, travail arrêté (lié au brief 06).
- **Ouverture des frontières** : immigration plus rapide, mais exigences plus fortes.

### Budget
- Impôt en émeraudes prélevé sur la production des métiers (brief 07), un trésor municipal
  dans l'hôtel de ville, dépenses : salaires des gardes, fêtes, agrandissements.

## Points d'entrée

- `Town` : `residents` joueurs séparés des citoyens PNJ (`Map<UUID, Rank>`), mandat
  (`termEnds`), décrets actifs (`Set<Decree>`), trésor.
- `TownCommands` puis l'écran (brief 05).

## Critères

- Tests unitaires du dépouillement (égalités, abstention des citoyens malheureux, intérim).
- Un serveur à deux joueurs : élection, passation automatique, notifications.
