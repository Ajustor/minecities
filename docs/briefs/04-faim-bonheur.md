# Brief 04 — Faim, santé et bonheur

## Objectif

Approfondir la gestion des citoyens au-delà de la barre de faim : une nourriture variée, un
bonheur qui pèse sur la ville, et une population qui peut aussi croître d'elle-même.

## Déjà en v0.1

`citizen/Hunger` : barre de 0 à 20 vidée en un jour ; repas sous 12 jusqu'à 18, dans les coffres
de la maison puis au grenier, l'aliment le plus nourrissant d'abord ; ralentissement et dégâts
légers à 0 ; départ après une journée affamé ; soin quand il est repu. Nourriture « malsaine »
refusée (`TownService.UNHEALTHY`).

## À faire

### Variété (inspiré de MineColonies 1.20+)
- Chaque citoyen retient ses **5 derniers repas**. Manger toujours la même chose réduit la
  satiété gagnée et le bonheur ; un régime varié le monte. Le grenier devient un vrai sujet.
- Les plats élaborés (ragoûts, tartes, gâteau entamé posé dans une maison ?) valent plus.
- Paliers : à partir de Ville, les citoyens exigent au moins 2 aliments différents par jour, etc.

### Bonheur
Une note de 0 à 10 par citoyen, moyenne affichée pour la ville. Facteurs :
- faim et variété ; logement (a-t-il un lit ? surpeuplement ? lumière dans la maison ?) ;
- sécurité (morts récentes, monstres sur le territoire) ;
- travail (a-t-il un métier ? outil fourni ?) ; décrets du maire ; fêtes.
Effets : vitesse de travail, immigration, **votes** (brief 02), départs si très bas.

### Santé
- Blessés soignés par un **médecin** (poste : alambic) ; maladies légères si nourriture
  insuffisante (à garder léger).

### Naissances
- Remplacé et précisé par le **brief 10** : les citoyens naissent de couples mariés (mariage
  accepté par le maire), et il n'y a plus d'immigration au-delà d'un minimum de 5 habitants.

## Points d'entrée

- `CitizenEntity` : historique des repas, `happiness()` calculée toutes les 5 s.
- `Town` : moyenne de bonheur mise en cache pour l'immigration et l'écran.
- Afficher la faim/le bonheur : bulle au-dessus de la tête (brief 05).

## Critères

- Tests unitaires du calcul de satiété selon l'historique et de la note de bonheur.
- Scène en jeu : grenier à un seul aliment → bonheur qui baisse ; varié → qui monte.
