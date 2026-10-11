# Brief 11 — Bâtiments à quatre niveaux et filières de production

## Objectif

Demande : « prépare aussi les upgrades des bâtiments pour la v1, je voudrais 4 paliers de bâtiments
avec des ressources qui coûtent de plus en plus cher, il faudra aussi prévoir un mineur, un
joaillier, un boulanger… tout pour faire tourner de vraies villes ».

Deux chantiers liés : chaque bâtiment se bâtit puis s'améliore en **quatre niveaux**, de plus en
plus coûteux ; et de **nouveaux métiers** ferment les filières (de la mine au bijou, du blé au
pain), pour que la ville produise elle-même ce que coûtent ses améliorations.

## État (v0.9)

- Le générateur (`tools/blueprints/generate.py`, `levelled()`) bâtit chaque série `nom_1` … `nom_4`
  : maison, cuisine, scierie, atelier du bâtisseur, boulangerie, forge, infirmerie, atelier de
  l'inventeur, ferme, étable, pêcherie, marché, caserne, mine. Les anciens schémas restent (marqués
  `legacy`, cachés) et mènent au niveau suivant de la nouvelle série.
- `Blueprint.level()` et `Town.buildingLevel(plaque)` : un atelier bâti au niveau N fait gagner un
  dixième de temps par niveau (`WorkGoal.rest`).
- Métiers : **boulanger** (four ; `Cooking.BAKER`, le cuisinier lui laisse alors le pain, les
  biscuits et les tartes), **forgeron** (haut fourneau, dès la ville ; `SmithGoal` : outils réclamés,
  en fer ou en pierre, plastron des gardes, fonte des minerais bruts), **mineur** (treuil de mine,
  bloc du mod et son modèle ; `MinerGoal` et `town/Mine` : escalier puis couloirs, filons, torches,
  sécurité eau/lave/maisons/territoire, galerie sauvegardée).
- Couvre-chefs du marchand, du boulanger, du forgeron et du mineur.

## Quatre niveaux par bâtiment

| Niveau | Matériaux | Ce que le niveau apporte |
|---|---|---|
| 1 — Cabane | bois brut, terre battue, toit plat de dalles | le poste de base, un travailleur |
| 2 — Maison | planches, pierre, vitres, toit à deux pans | un deuxième poste ou deux lits de plus |
| 3 — Bâtisse | pierre taillée, briques, lanternes, fer (portes, barreaux) | un troisième poste, un coffre de plus, rendement meilleur |
| 4 — Demeure | pierre polie, cuivre, quartz ou deepslate, verre teinté, or (décor) | un quatrième poste, et le savoir-faire du métier (voir plus bas) |

- Les schémas deviennent `nom_1` … `nom_4` (l'actuel `house_small` → `house_1`, etc.), chaque
  niveau `upgrade` le suivant ; le style (plaines, taïga, désert, savane) s'applique à tous.
- Le coût croît vite : au niveau 4 il faut des matériaux que seule une ville équipée produit (fer
  et cuivre du mineur et du forgeron, briques du briquetier, verre du verrier) ou achète au marché.
- Un niveau demande un palier de ville : 1–2 au village, 3 à la ville, 4 à la grande ville.
- Ce qu'apporte un niveau, par métier : plus de postes (déjà le cas), et un **rendement** (le
  niveau multiplie la production par tâche, ou raccourcit les pauses) ; au niveau 4, une
  spécialité (le fermier plante aussi des citrouilles et des melons, le cuisinier prépare des
  gâteaux, le garde porte une armure de fer complète…).
- Les maisons : 2 → 4 → 6 → 8 lits, et le confort (lumière, décor) compte pour le bonheur.

## Nouveaux métiers (filières)

| Métier | Poste vanilla | Ce qu'il fait | Filière |
|---|---|---|---|
| Mineur | treuil de mine (bloc du mod) | creuse une galerie en escalier sous son puits de mine, rapporte pierre, charbon, fer, cuivre, or, redstone, lapis, diamants (rares, selon la profondeur) | mine → forge, joaillier, bâtisseur |
| Forgeron | haut fourneau | fond les minerais en lingots, fait les outils et armures des métiers | mine → outils, armures, golems |
| Joaillier | établi de joaillier (bloc du mod) | taille les gemmes (émeraude, diamant, améthyste, lapis) en bijoux et blocs précieux qui se vendent cher au marché | mine → marché (trésor) |
| Boulanger | four (fourneau) | pain, gâteaux, tartes, biscuits ; décharge le cuisinier, qui garde les viandes et les soupes | ferme → boulangerie → grenier |
| Maçon / briquetier | tailleur de pierre | taille la pierre, cuit l'argile en briques, fait les blocs des niveaux 3–4 | mine, rivière → bâtisseur |
| Verrier | four de verrier (bloc du mod) | fond le sable en verre, teint le verre | sable → bâtisseur |
| Berger / tisserand | métier à tisser | tond les moutons, tisse laine, tapis et lits | élevage → maisons (lits) |
| Tanneur | chaudron | cuir, armures de cuir, livres (avec le bibliothécaire) | élevage → garde, bibliothèque |
| Bibliothécaire | bibliothèque ciselée | livres, savoir : débloque les spécialités de niveau 4 ; bonheur | tanneur, papetier → toute la ville |
| Apiculteur | ruche | miel, rayons ; bonheur (douceurs) | fleurs → cuisine |

Principes : chaque métier reste vanilla (un poste de travail existant, des objets vanilla), un poste
par travailleur, les outils au grenier. La production arrive au grenier ; les métiers d'aval y
prennent leurs matières. Le marchand vend ce qui dépasse la réserve.

## Décisions (questions 30 et 31)

- Les postes qui manquent au vanilla sont des **blocs du mod, avec leurs propres modèles** (réponse :
  « créer des modèles custom en cas de besoin ») : un **treuil de mine** pour le mineur (il creuse une
  vraie galerie en escalier sous le bâtiment, éclairée de torches, dans le territoire et pas sous les
  maisons), un **établi de joaillier** (loupe, petit étau), un **four de verrier**. Chacun a une
  recette vanilla et un modèle Blockbench.
- Coûts des niveaux : bois brut, puis planches et pierre, puis pierre taillée, briques et fer, puis
  pierre polie, cuivre, quartz et or ; les niveaux 3 et 4 demandent la ville puis la grande ville.
  Réponse : « ça me semble juste, ça nous permettra au besoin d'ajouter de nouveaux paliers encore
  plus chers » : le nombre de niveaux n'est pas figé (un schéma `nom_N` monte vers `nom_N+1`).

## Ordre proposé

1. v0.8 — robustesse (brief 08), pour que tout le reste tienne sur un serveur.
2. v0.9 — les quatre niveaux pour tous les schémas existants (générateur), les paliers requis, le
   rendement par niveau ; boulanger, mineur, forgeron.
3. v1.0 — joaillier, maçon, verrier, tisserand, tanneur, bibliothécaire, apiculteur ; spécialités
   de niveau 4 ; modèles et finitions.
