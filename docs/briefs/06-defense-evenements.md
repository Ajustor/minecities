# Brief 06 — Défense et événements

## Objectif

Que la ville vive aussi des dangers et des fêtes. MineColonies a ses gardes, ses barbares et
pirates ; ici on s'appuie sur ce que le vanilla offre déjà : monstres nocturnes, raids
d'illageois, cloche de village.

## v0.1

Les citoyens fuient les monstres (`AvoidEntityGoal`) et paniquent quand on les frappe.
`doMobSpawning` reste vanilla : rien n'empêche les monstres d'apparaître en ville.

## Défense

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
