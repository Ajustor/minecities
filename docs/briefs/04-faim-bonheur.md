# Brief 04 — Faim, santé et bonheur

## Objectif

Approfondir la gestion des citoyens au-delà de la barre de faim : une nourriture variée, un
bonheur qui pèse sur la ville, et une population qui peut aussi croître d'elle-même.

## Déjà en v0.1

`citizen/Hunger` : barre de 0 à 20 vidée en un jour ; repas sous 12 jusqu'à 18, dans les coffres
de la maison puis au grenier, l'aliment le plus nourrissant d'abord ; ralentissement et dégâts
légers à 0 ; départ après une journée affamé ; soin quand il est repu. Nourriture « malsaine »
refusée (`TownService.UNHEALTHY`).

## État (v0.6)

- `citizen/Diet` : les 5 derniers repas ; un aliment mangé 2 fois nourrit aux trois quarts, 3 fois
  et plus à moitié. Au garde-manger, le citoyen choisit ce qu'il n'a pas mangé dernièrement
  (`TownService.takeFood(container, diet)`), et préfère les plats.
- Le cuisinier prépare pain, biscuits, soupes de champignons et de betteraves, ragoût de lapin et
  tarte à la citrouille (`citizen/Cooking.RECIPES`).
- `citizen/Happiness` : note de 0 à 10, base 5 ; faim (−3 à +1), variété attendue `2 + palier`
  (−1 à +2), plat (+1), logis (+1 / −2), métier (+1, ou −1 sans outil), mariage (+1), blessure (−1),
  deuil (−1 par mort récente, −2 au plus), danger (raid, alerte, monstre à 12 blocs : −1), fête (+2).
  Effets : rythme de travail (`workPace` : ×0,65 à ×1,75 sur les pauses), naissances (5 au moins),
  naturalisation des villageois (deux fois plus vite au-dessus de 7, jamais à 3 ou moins), départ
  après un jour à 1 ou moins.
- Le médecin (alambic) soigne et nourrit (`citizen/DoctorGoal`), l'infirmerie a son schéma.
- Affichage : humeur au clic droit (avec ses facteurs), dans l'écran, et une icône au-dessus de la
  tête d'un citoyen qui va mal.

- Maladies (MC-12, `citizen/Disease`, `citizen/Health`) : rhume (pluie sans manteau), dysenterie
  (chair putréfiée, aliment empoisonné : un affamé en mange faute de mieux), fièvre (pluie, plus
  rarement ; elle cloue au lit). Incubation, durée, contagion entre voisins, moral et rythme de
  travail propres à chacune ; pas de mort. Le médecin soigne avec un remède (miel, lait, pastèque
  scintillante) : la convalescence est courte. Guéri, on est protégé deux jours. Le manteau
  (`McItems.COAT`, porté sur le buste) protège de la pluie : recette, tisserand, ou donné d'un clic.

Suite : décrets du maire (brief 02) qui pèsent sur le bonheur, lumière et surpeuplement des maisons.

## Pistes d'origine

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
