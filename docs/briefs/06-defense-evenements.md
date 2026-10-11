# Brief 06 — Défense et événements

## Objectif

Que la ville vive aussi des dangers et des fêtes. MineColonies a ses gardes, ses barbares et
pirates ; ici on s'appuie sur ce que le vanilla offre déjà : monstres nocturnes, raids
d'illageois, cloche de village.

## v0.1

Les citoyens fuient les monstres (`AvoidEntityGoal`) et paniquent quand on les frappe.
`doMobSpawning` reste vanilla : rien n'empêche les monstres d'apparaître en ville.

## État (v0.5)

- **Bâtiments** (schémas) : caserne (2 lits, 2 meules ; agrandie : 3 et 3), tour de garde (5×5,
  12 de haut, une table d'archerie au pied, le poste de l'archer au sommet), atelier de
  l'inventeur (table de forge, enclume).
- **Gardes et archers** (`GuardGoal`, `ArcherGoal`) : ils ne fuient pas, ne dorment pas, prennent
  pour cible les `Enemy` du territoire (les gardes laissent les creepers aux archers). Leur
  entraînement leur donne +20 PV, +6 d'armure et +2 de dégâts (modificateurs `minecities:drill`,
  retirés s'ils changent de métier).
- **Inventeur** (`InventorGoal`) : la ville est attaquée pendant un raid, ou quand 3 monstres au
  moins rôdent à 24 blocs de l'hôtel de ville ; il assemble alors un golem (fer d'abord, sinon
  neige) au plus toutes les 15 secondes, avec ce qu'il trouve au grenier. Les golems restent.
- **Abri** (`ShelterGoal`) : pendant un raid, les civils rentrent près de leur lit, ou à défaut à
  l'hôtel de ville.
- **Raids** (`town/Raids`, système propre, pas le raid vanilla) : une ville d'au moins 8 habitants
  tente les pillards ; au crépuscule, une chance sur 3, trois jours au moins entre deux raids.
  Vagues : `1 + habitants/8 + maisons/4` (1 à 7), pillards par vague : `2 + vague + habitants/10`
  (12 au plus), pillards et vindicateurs qui partent du bord du territoire et marchent sur
  l'hôtel de ville en s'en prenant aux citoyens. Barre de boss ; un pillard à l'hôtel de ville
  pille le grenier ; la victoire y dépose `vagues × 2` émeraudes et fait la fête ; un raid
  abandonné au bout d'un jour. `/ville admin raid|raid_stop` pour les tests.

Suite : cloche d'alarme, raids déclenchés par *Bad Omen*, montures et ravageurs dans les grosses
vagues, fêtes et marchés.

## Défense (pistes d'origine)

- **Gardes** (brief 01, poste : meule) : patrouillent entre les bâtiments la nuit, attaquent
  les monstres du territoire, portent l'équipement donné par le grenier (épée, arc, armure).
- **Cloche d'alarme** : sonner une cloche vanilla sur le territoire fait rentrer tous les
  citoyens chez eux (comme les villageois en raid). Très vanilla-friendly.
- **Raids** : un joueur avec *Bad Omen* qui entre sur le territoire déclenche un raid sur la
  ville (réutiliser le système de raid vanilla, qui cible les POI de village ? à vérifier : les
  lits des maisons déclarées pourraient compter).
- Golems de fer : une ville au palier Ville pourrait faire apparaître un golem, comme les
  villages.

## Événements

- **Fête** (décret du brief 02) : bannières, feux d'artifice, citoyens qui dansent à l'hôtel
  de ville, bonus de bonheur.
- **Marché** hebdomadaire : un marchand ambulant (vanilla) attiré vers l'hôtel de ville.
- **Catastrophes** légères et optionnelles : incendie (foudre), disette (hiver ?), à voir.

## Critères

- Scène en jeu : un zombie entre dans le territoire, le garde l'abat ; la cloche fait rentrer
  les citoyens.
