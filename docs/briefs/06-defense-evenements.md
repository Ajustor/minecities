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

En v0.6 : la **cloche d'alarme** (`town/Alarms`) : une cloche sonnée sur le territoire donne
l'alerte deux minutes (civils à l'abri, gardes à l'hôtel de ville, inventeur aux golems, monstres
luisants à 48 blocs) ; sonnée à nouveau, elle la lève. Les **fêtes** (`town/Festivals`) : 4 émeraudes
du grenier, une tous les trois jours, deux minutes de danse et de feux d'artifice devant l'hôtel de
ville, +2 de bonheur pendant un jour ; une victoire sur les pillards se fête d'elle-même.

Suite : raids déclenchés par *Bad Omen*, ravageurs dans les grosses vagues, marché, catastrophes.

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
- **Catastrophes** (MC-14, `town/Disasters`, `citizen/FirefightGoal`) : incendie (orage, dès la
  ville ; la foudre met le feu au toit d'un bâtiment déclaré ; les citoyens l'éteignent avec les
  seaux d'eau du grenier), disette (un jour sans récolte, sauf de quoi ressemer), tremblement de terre
  (toutes les villes à la fois, dix secondes : des blocs des bâtiments tombent, coups et morts
  possibles). Rares (une minute sur dix d'orage pour le feu, 1/400 et 1/800 par minute pour les
  autres), désactivables, au plus une par ville tous les `disaster_spacing_days` jours (le double
  pour le séisme). Annoncées, visibles dans le résumé, sauvegardées dans la chronique.

## Critères

- Scène en jeu : un zombie entre dans le territoire, le garde l'abat ; la cloche fait rentrer
  les citoyens.
