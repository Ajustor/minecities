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
- Seul le maire peut démolir l'hôtel de ville ; maire et adjoints reçoivent les nouvelles.

## À faire

### Élections (pas avant la v2, réponse à la question 14)

Le maire ou les habitants pourront **déclencher** une élection ; d'ici là, le maire reste le
fondateur ou celui à qui il a passé l'écharpe.

- Mandat de N jours de jeu (réglable, défaut 7). À la fin, élection : tout habitant joueur peut se
  porter candidat pendant 1 jour ; vote via l'écran de l'hôtel de ville (ou un **bulletin**
  objet à déposer dans l'urne = l'hôtel de ville, plus vanilla).
- **Les citoyens PNJ votent aussi** selon leur bonheur (brief 04) : le maire sortant récolte les
  voix des citoyens heureux. Mécanique simple et lisible qui récompense la bonne gestion.
- Ville d'un seul joueur : pas d'élection (ou réélection automatique).
- Si le maire est absent N jours, un adjoint assure l'intérim.

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
