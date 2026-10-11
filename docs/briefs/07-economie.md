# Brief 07 — Économie et commerce

## Objectif

Donner une valeur à la production de la ville et relier les villes entre elles, en utilisant
l'émeraude comme monnaie (vanilla).

## État (v0.7)

- Économie fermée : le **trésor** est l'émeraude du grenier ; rien n'en crée, le marché convertit la
  production (`town/Market` : cours des villageois, réserve de 32 par marchandise).
- **Marchand** (pupitre, dès la ville ; `citizen/MerchantGoal`) : achète d'abord ce que réclament
  les travailleurs (outil du métier ou du bâtisseur, flèches, pain si le grenier est vide), sinon
  vend le surplus ; 32 émeraudes par jour et par marchand au plus (`Chronicle.earn`).
- Dépenses : fêtes (4 émeraudes), achats du marchand. Le trésor et les gains du jour sont à l'écran.

Suite : commerce entre villes (caravanes), négociant chez les villageois, salaires.

## Pistes

- **Trésor municipal** : un coffre-fort dans l'hôtel de ville (ou un onglet), en émeraudes.
- **Impôts** : un pourcentage de ce que produisent les métiers est converti en émeraudes au
  marché (ou les citoyens « paient » un loyer en émeraudes s'ils sont heureux).
- **Marché** : un bâtiment (pièce avec des tonneaux et une table de cartographie ?) où des
  marchands PNJ achètent le surplus du grenier contre émeraudes, et vendent ce qui manque.
- **Commerce avec les villageois vanilla** : un citoyen « négociant » va échanger avec les
  villageois d'un village proche.
- **Entre villes** : routes commerciales entre deux hôtels de ville (multijoueur), caravanes.
- **Dépenses** : salaires (les gardes refusent de travailler sans paie ?), agrandissements,
  fêtes.

## À trancher

- Économie fermée (tout vient du jeu) ou injection d'émeraudes ? L'injection casse
  l'économie vanilla des villageois ; préférer convertir la production existante.

## Critères

- Tests unitaires des prix et de la balance ; scène en jeu d'une vente au marché.
