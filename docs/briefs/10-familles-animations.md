# Brief 10 — Familles, mariages, naissances et animations

Demandé le 2026-10-11 : « le minimum de villageois dans la cité doit être de 5, les villageois
n'apparaissent pas par magie, ils doivent naître d'union entre deux autres villageois mariés. Deux
villageois qui s'entendent peuvent demander à se marier au maire via l'interface d'admin de la
cité ; ajouter aussi des animations aux villageois pour les diverses actions. »

## État (v0.4)

Fait : minimum de 5 (`town/Families`), affinités par proximité, demandes de mariage, écran de
l'hôtel de ville avec le registre (accepter/refuser), cérémonie (cloche, cœurs), emménagement
commun, naissances (couple logé ensemble, repu, lit libre, une par jour au plus), enfants plus
petits qui grandissent en 3 jours, animations par action. Reste : affinité qui baisse avec la
faim et le malheur (brief 04), enfants qui suivent leurs parents, démarche d'enfant, scène de
mariage où les deux se rendent à l'hôtel de ville.

## Décisions (validées)

- **Moins de 5 habitants : de nouvelles personnes apparaissent** pour revenir à 5 ; au-delà, la
  population ne croît que par les naissances.
- **L'écran de l'hôtel de ville se fait dans la même v0.4** (brief 05), puisqu'il faut y accepter
  les mariages.

## Ce qui change par rapport à la v0.3

Aujourd'hui, un citoyen « immigre » toutes les 2 minutes tant qu'il y a un lit libre et de quoi
manger. C'est la part de magie à retirer :

| v0.3 | Cible (v0.4) |
|---|---|
| La ville fondée est vide ; les citoyens arrivent un à un | **Cinq fondateurs** arrivent avec la fondation (ou dès qu'il y a des lits pour eux) |
| Immigration continue tant qu'il y a des lits | **Plus d'immigration** au-delà du minimum : la population ne croît que par les **naissances** |
| — | Si la population tombe **sous 5** (morts, départs), des nouveaux venus la ramènent à 5, pour qu'une ville ne s'éteigne pas |
| Villageois vanilla sans métier naturalisés | Inchangé (ils ne naissent pas de rien : ils étaient déjà là) |

## Affinités

Chaque paire de citoyens a une **affinité** (0 à 100), qui monte quand ils passent du temps ensemble
(voisins de maison, même atelier, repas pris au même grenier, conversations via le but
`InteractGoal` qui existe déjà) et baisse avec la faim et le malheur (brief 04). Au-delà de 70, ils
« s'entendent » : des cœurs apparaissent parfois au-dessus d'eux.

Données : `Town.affinities` (paire d'UUID → valeur), mise à jour toutes les 5 s par `TownService`
d'après la proximité des entités chargées.

## Mariage

1. Deux citoyens adultes, non mariés, qui s'entendent, **demandent** à se marier : la demande
   apparaît dans l'**écran de l'hôtel de ville** (l'interface d'admin de la cité, brief 05), onglet
   « Registre », et le maire et les adjoints en sont prévenus.
2. Le maire (ou un adjoint) **accepte** ou **refuse** dans cet écran. En attendant l'écran : `/ville
   marriages` (liste) et `/ville marry accept|refuse <demande>`.
3. Accepté : une petite cérémonie à l'hôtel de ville (les deux viennent devant, la cloche sonne,
   cœurs et feux d'artifice), puis le couple emménage ensemble : la ville leur attribue deux lits
   **de la même maison** (déménagement si besoin).

Données : `Resident.spouse` (UUID), `Town.marriageRequests` (liste de paires, date).

## Naissances

- Un couple marié, **logé dans la même maison**, tous deux **rassasiés** (et heureux, brief 04),
  avec un **lit libre dans leur maison**, peut avoir un enfant : au plus un par couple et par
  journée de jeu (délai réglable), avec une probabilité (par exemple 1 chance sur 3 par nuit
  passée dans leur lit).
- L'enfant est un petit citoyen (entité bébé, comme les villageois) qui suit ses parents, ne
  travaille pas, mange moins, et devient adulte après N jours (réglable, 3 par défaut). Il prend un
  nom de famille : celui d'un des parents.
- Le maire est prévenu de chaque naissance ; la population compte les enfants.

Conséquence de gameplay : pour grandir, il faut des maisons **assez grandes pour des familles**
(d'où l'intérêt des agrandissements du bâtisseur), de la nourriture en abondance et du bonheur.

## Animations

Le citoyen est aujourd'hui un humanoïde au squelette de joueur, sans geste propre. À ajouter, côté
client, dans `CitizenRenderer` (état de rendu étendu, comme FMAB le fait avec ses « tempéraments ») :

| Action | Animation |
|---|---|
| Manger | la main porte l'aliment à la bouche, miettes (particules d'objet) — l'objet visible en main |
| Travailler aux champs | coups de houe vers le sol, bras droit en arc |
| Abattre un arbre | grands coups de hache à deux mains |
| Pêcher | canne tendue, ligne lancée (bouchon) ; relevé du poisson |
| Bâtir | coups de marteau/pose de bloc, le bloc en main |
| Cuisiner | brassage devant le fumoir |
| Éleveur | tend la nourriture aux bêtes |
| Discuter (affinité) | têtes qui se tournent, bras qui gesticulent |
| Mariage | les deux se font face, se tiennent la main ; cœurs |
| Dormir | déjà en place (pose couchée vanilla) |
| Enfant | démarche bondissante, tête plus grosse (proportions de bébé) |

Technique : un champ synchronisé (`EntityDataAccessor<Integer> ACTION`) posé par les buts
(`WorkGoal` et suivants) ; le modèle (`HumanoidModel` étendu) anime bras et tête selon l'action et
le temps (`ageInTicks`). L'objet de travail est tenu en main (déjà le cas pour les outils). Les
animations peuvent être prototypées dans Blockbench (MCP) puis traduites en code, comme pour
FMAB.

## Interface d'admin (brief 05)

Ce brief rend l'écran de l'hôtel de ville indispensable : onglets Résumé, Citoyens (familles,
couples, enfants, affinités), **Registre** (demandes de mariage à accepter/refuser, naissances),
Bâtiments, Chantiers, Grenier.

## Critères

- Tests unitaires : affinité (montée/descente), conditions de demande, conditions de naissance,
  minimum de 5.
- Scène en jeu : fondation → 5 fondateurs ; deux citoyens qui s'entendent demandent à se marier ;
  le maire accepte ; ils emménagent ensemble ; un enfant naît ; il grandit.
