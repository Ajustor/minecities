# Brief 05 — Interface et rendu

## Objectif

La v0.1 parle par le chat et un écran de coffre. Il faut un vrai tableau de bord pour le maire,
et un rendu qui donne envie (modèles Blockbench, citoyens plus variés).

## État (v0.6)

- Écran de l'hôtel de ville : Résumé (bonheur moyen, fête, alerte, bouton de fête), Citoyens (humeur,
  bouton « Où ? » qui fait luire le citoyen), Bâtiments, Registre, Grenier.
- HUD : titre de bienvenue en entrant sur un territoire, mot en le quittant, contour en particules
  quand on tient un hôtel de ville ou une plaque (`town/Borders`).
- Plaque : propriété `status` (none, valid, invalid), porte verte ou rouge.
- Icônes au-dessus des têtes (faim, sans logis, malheureux, blessé) et couvre-chef de chaque métier
  (`client/render/CitizenHatModel`, texture générée `hats.png`).

Suite : carte du territoire, onglet Mairie (brief 02).

## Écran de l'hôtel de ville

Ouvert par clic sur l'hôtel de ville (le grenier devient un onglet), dans l'esprit des écrans
vanilla (texture de livre/parchemin, pas d'interface « tablette ») :

- **Résumé** : nom, palier et progression vers le suivant (barres citoyens/maisons), territoire,
  bonheur moyen, réserves (jours de nourriture restants estimés).
- **Citoyens** : liste avec faim, bonheur, métier, logement ; cliquer = le localiser (contour
  lumineux quelques secondes, comme l'effet *glowing*).
- **Bâtiments** : maisons et ateliers, leurs lits/postes, état du dernier relevé.
- **Mairie** : adjoints, décrets, élections (brief 02).
- **Registre** : demandes de mariage à accepter ou refuser, naissances, familles (brief 10).
- **Grenier** : l'inventaire actuel.

Technique : `ExtendedScreenHandlerType`/menu Fabric pour le grenier, et un paquet
serveur → client (`CustomPacketPayload`) qui envoie un instantané de la ville à l'ouverture. Voir
FMAB (`network/`, `screen/`) pour les patrons utilisés en 26.2.

## HUD et indications en jeu

- En entrant/sortant d'un territoire : message en surimpression « Bienvenue à Valombre (Ville) ».
- Bulle au-dessus d'un citoyen affamé ou sans logis (petite icône, comme les émotes).
- Contour du territoire en particules quand on tient l'hôtel de ville ou une plaque en main.
- Plaque de maison : un état visuel (vert = valide, rouge = invalide) via une propriété de bloc.

## Modèles Blockbench (MCP blockbench disponible)

- **Hôtel de ville** : aujourd'hui un cube texturé. Un modèle de pupitre municipal (registre
  ouvert, cloche, petit drapeau), à faire dans Blockbench via le MCP (`.mcp.json` du projet),
  exporté en modèle Java Block. Garder une boîte de collision pleine.
- **Plaque de maison** : plaque en relief avec le numéro de la maison ?
- **Citoyens** : garder le squelette du joueur (compatibilité des poses, du sommeil) ; ajouter
  des variantes (chapeaux, tabliers par métier en surcouche) et des enfants (brief 04).
  Générer les peaux avec `tools/textures/generate.py` et les prévisualiser dans Blockbench.

## Critères

- Captures en jeu (`./gradlew runClientGameTest`) de chaque écran et de chaque modèle, copiées
  dans `captures/` pour relecture.
