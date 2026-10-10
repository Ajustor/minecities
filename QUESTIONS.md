# Questions ouvertes

Réponds directement sous chaque question (ou barre-la si elle ne se pose plus). J'ai pris une
décision par défaut pour avancer ; elle est indiquée, et facile à changer.

## Projet

1. **Dépôt GitHub** : j'ai mis `https://github.com/Ajustor/minecities` dans `fabric.mod.json`.
   C'est bien le nom du dépôt à créer ? (Le dépôt local est sur `master`, sans remote.)
   > Réponse : oui

2. **Licence** : « All-Rights-Reserved » comme FMAB. Tu préfères une licence libre (MIT, LGPL)
   pour un mod de ce genre, qui pourrait attirer des contributeurs ?
   > Réponse : oui

3. **Site GitHub Pages** : FMAB a un site (pages.yml). Je ne l'ai pas repris pour MineCities
   (pas encore de contenu). Tu le veux dès maintenant ?
   > Réponse : oui

4. **Nom affiché** : « MineCities ». Une version française du nom en jeu (« Cités ») ?
   > Réponse : non

## Gameplay

5. **« Que les villages deviennent des villes »** : tu parles des villes fondées par le joueur
   (ce que fait la v0.1 : village → ville → grande ville → mégalopole), ou aussi de **reprendre
   les villages vanilla** de villageois ? Si oui : les villageois restent-ils des villageois qui
   comptent dans la population, ou deviennent-ils des citoyens ? (brief 03)
   > Réponse : OUI

6. **Seuils des paliers** : Ville à 10 citoyens/4 maisons, Grande ville 30/12, Mégalopole 75/30.
   MineColonies monte bien plus haut (centaines de citoyens). Tu vises quelle taille pour une
   mégalopole ? Faut-il que le palier ne redescende jamais une fois atteint ?
   > Réponse : le palier peut descendre

7. **Une ville par maire** : un joueur ne peut être maire que d'une ville. OK, ou plusieurs
   villes (avec un titre de « gouverneur » au-dessus) ?
   > Réponse : plusieurs ville et définir d´autres joueurs maires ou adjoints

8. **Qui peut déclarer une maison** : aujourd'hui n'importe quel joueur, sur le territoire de
   n'importe quelle ville (ça ne fait qu'ajouter des lits). Restreindre au maire/adjoints/habitants ?
   > Réponse : tout le monde, prévoir aussi un bâtisseur et des schemas de bâtiments pour les constructions

9. **Faim** : à 0, le citoyen perd des PV mais jamais sous 2, et part au bout d'une journée.
   Tu veux qu'ils puissent **mourir de faim** (plus dur, plus MineColonies) ?
   > Réponse : mourir de faim est possible

10. **Grenier ouvert à tous** : n'importe qui peut ouvrir l'hôtel de ville et prendre dans le
    grenier. Le réserver aux habitants ?
    > Réponse : tout le monde

11. **Métiers (brief 01)** : je propose que le **poste de travail vanilla désigne le métier**
    (composteur = fermier, fumoir = boulanger, baril = pêcheur…) dans une pièce déclarée par la
    même plaque. Ça te va, ou tu préfères un bloc « atelier » dédié par métier, plus proche des
    huttes de MineColonies ?
    > Réponse : ça me vas

12. **Outils des travailleurs** : comme MineColonies, les métiers réclament un outil (houe,
    hache…) qu'il faut fournir au grenier ? Ou ils travaillent à mains nues pour rester simple ?
    > Réponse :les outils

13. **Dimensions** : on peut fonder une ville dans le Nether ou l'End (où les lits explosent).
    Je l'interdis hors Overworld ?
    > Réponse : partout

14. **Élections (brief 02)** : intéressé par des élections où **les citoyens PNJ votent selon
    leur bonheur** ? Ou le maire reste simplement le fondateur, avec passation manuelle ?
    > Réponse : on pourra déclencher des élections mais pas avant la v2

## Rendu

15. **Hôtel de ville** : en v0.1, un cube texturé (bannière à la cloche dorée, fenêtres). Je
    voulais en faire un modèle Blockbench (pupitre municipal avec registre et cloche) mais le MCP
    Blockbench n'était pas chargé dans cette session (j'ai copié le `.mcp.json` de FMAB dans le
    projet : il le sera à la prochaine). Tu as une idée précise du look ?
    > Réponse : pas spécialement, tu peux faire un truc qui ressemble a celui de minecolonies mais plus dans le style de minecraft vanilla

16. **Citoyens** : 8 tenues générées (paysans, artisans, tabliers). Style qui te convient, ou
    tu veux des citoyens plus proches des villageois vanilla (nez, robe) ?
    > Réponse : parfait pour ce style

## Suite donnée à tes réponses (2026-10-10)

| # | Réponse | Ce qui a été fait |
|---|---|---|
| 1 | dépôt `Ajustor/minecities` | remote `origin` en place ; rien n'est encore poussé (je te laisse confirmer) |
| 2 | licence libre | **MIT** (`LICENSE`, `fabric.mod.json`) |
| 3 | site Pages | `site/` + `pages.yml`, republié après chaque release (comme FMAB) |
| 5 | reprendre les villages vanilla | les villageois du territoire **comptent dans la population** et restent des villageois (avec leurs échanges) ; voir Q17 pour aller plus loin |
| 6 | le palier peut descendre | déjà le cas |
| 7 | plusieurs villes, d'autres joueurs maires/adjoints | un joueur peut diriger plusieurs villes ; passation et adjoints par commande |
| 8 | tout le monde + bâtisseur et schémas | déjà ouvert à tous ; bâtisseur et schémas : **brief 09** |
| 9 | mourir de faim possible | un cœur toutes les 30 s à 0 de faim, jusqu'à la mort |
| 10 | grenier à tous | déjà le cas |
| 11 | poste de travail = métier | **fermier** (composteur) et **cuisinier** (fumoir) en place |
| 12 | les outils | le fermier exige une houe (coffres de l'atelier ou grenier), qui s'use ; le maire est prévenu s'il en manque |
| 13 | partout | aucune restriction de dimension (voir Q20) |
| 14 | élections, pas avant la v2 | noté dans le brief 02 |
| 15 | style MineColonies, version vanilla | l'hôtel de ville est un **bâtiment miniature** (socle de pierre, chêne, toit de sapin, bannière, cloche), conçu dans Blockbench |

## Nouvelles questions

17. **Villages vanilla, suite** : aujourd'hui les villageois comptent dans la population mais
    restent des villageois. Veux-tu aussi que les **maisons du village soient déclarées
    automatiquement** (les lits des villageois deviendraient des maisons de la ville), ou que des
    villageois **sans métier puissent devenir citoyens** ?
    > Réponse : oui

18. **Métiers sans poste vanilla** : le bûcheron et l'éleveur n'ont pas de poste de travail
    vanilla. Je propose une **bûche écorcée** (billot) pour le bûcheron et une **botte de foin**
    pour l'éleveur. D'accord, ou une autre idée ?
    > Réponse : ça me vas

19. **Pêcheur et baril** : le baril est le poste du pêcheur vanilla, mais le mod s'en sert déjà
    comme garde-manger (comme les coffres). Le baril devient-il un poste de pêcheur seulement si
    l'atelier est au bord de l'eau, ou on garde le baril pour le stockage et on choisit un autre
    poste ?
    > Réponse : parfait

20. **Dormir dans le Nether / l'End** : les lits y explosent. Les citoyens d'une ville du Nether
    ne dorment pas (et n'en souffrent pas), ou on imagine un « lit » propre à ces dimensions
    (ancre de réapparition ?) ?
    > Réponse : oui

21. **Bâtisseur (brief 09)** : quels schémas livrer d'abord (maison 2 lits, maison 4 lits, ferme,
    cuisine, entrepôt…), et dans quel style (plaines, désert, taïga, comme les villages vanilla) ?
    Le bâtisseur doit-il aussi pouvoir agrandir/améliorer un bâtiment existant ?
    > Réponse : tout si possible

22. **Pousser sur GitHub** : le remote est prêt. Je pousse `master` (ce qui lance le build, le site
    Pages et rend le dépôt visible selon ses réglages) ? Et je crée un premier tag `v0.2.0` pour
    déclencher une release ?
    > Réponse : fals une grosse branche par avancée de version
