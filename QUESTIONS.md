# Questions ouvertes

Réponds directement sous chaque question (ou barre-la si elle ne se pose plus). J'ai pris une
décision par défaut pour avancer ; elle est indiquée, et facile à changer.

## Projet

1. **Dépôt GitHub** : j'ai mis `https://github.com/Ajustor/minecities` dans `fabric.mod.json`.
   C'est bien le nom du dépôt à créer ? (Le dépôt local est sur `master`, sans remote.)
   > Réponse :

2. **Licence** : « All-Rights-Reserved » comme FMAB. Tu préfères une licence libre (MIT, LGPL)
   pour un mod de ce genre, qui pourrait attirer des contributeurs ?
   > Réponse :

3. **Site GitHub Pages** : FMAB a un site (pages.yml). Je ne l'ai pas repris pour MineCities
   (pas encore de contenu). Tu le veux dès maintenant ?
   > Réponse :

4. **Nom affiché** : « MineCities ». Une version française du nom en jeu (« Cités ») ?
   > Réponse :

## Gameplay

5. **« Que les villages deviennent des villes »** : tu parles des villes fondées par le joueur
   (ce que fait la v0.1 : village → ville → grande ville → mégalopole), ou aussi de **reprendre
   les villages vanilla** de villageois ? Si oui : les villageois restent-ils des villageois qui
   comptent dans la population, ou deviennent-ils des citoyens ? (brief 03)
   > Réponse :

6. **Seuils des paliers** : Ville à 10 citoyens/4 maisons, Grande ville 30/12, Mégalopole 75/30.
   MineColonies monte bien plus haut (centaines de citoyens). Tu vises quelle taille pour une
   mégalopole ? Faut-il que le palier ne redescende jamais une fois atteint ?
   > Réponse :

7. **Une ville par maire** : un joueur ne peut être maire que d'une ville. OK, ou plusieurs
   villes (avec un titre de « gouverneur » au-dessus) ?
   > Réponse :

8. **Qui peut déclarer une maison** : aujourd'hui n'importe quel joueur, sur le territoire de
   n'importe quelle ville (ça ne fait qu'ajouter des lits). Restreindre au maire/adjoints/habitants ?
   > Réponse :

9. **Faim** : à 0, le citoyen perd des PV mais jamais sous 2, et part au bout d'une journée.
   Tu veux qu'ils puissent **mourir de faim** (plus dur, plus MineColonies) ?
   > Réponse :

10. **Grenier ouvert à tous** : n'importe qui peut ouvrir l'hôtel de ville et prendre dans le
    grenier. Le réserver aux habitants ?
    > Réponse :

11. **Métiers (brief 01)** : je propose que le **poste de travail vanilla désigne le métier**
    (composteur = fermier, fumoir = boulanger, baril = pêcheur…) dans une pièce déclarée par la
    même plaque. Ça te va, ou tu préfères un bloc « atelier » dédié par métier, plus proche des
    huttes de MineColonies ?
    > Réponse :

12. **Outils des travailleurs** : comme MineColonies, les métiers réclament un outil (houe,
    hache…) qu'il faut fournir au grenier ? Ou ils travaillent à mains nues pour rester simple ?
    > Réponse :

13. **Dimensions** : on peut fonder une ville dans le Nether ou l'End (où les lits explosent).
    Je l'interdis hors Overworld ?
    > Réponse :

14. **Élections (brief 02)** : intéressé par des élections où **les citoyens PNJ votent selon
    leur bonheur** ? Ou le maire reste simplement le fondateur, avec passation manuelle ?
    > Réponse :

## Rendu

15. **Hôtel de ville** : en v0.1, un cube texturé (bannière à la cloche dorée, fenêtres). Je
    voulais en faire un modèle Blockbench (pupitre municipal avec registre et cloche) mais le MCP
    Blockbench n'était pas chargé dans cette session (j'ai copié le `.mcp.json` de FMAB dans le
    projet : il le sera à la prochaine). Tu as une idée précise du look ?
    > Réponse :

16. **Citoyens** : 8 tenues générées (paysans, artisans, tabliers). Style qui te convient, ou
    tu veux des citoyens plus proches des villageois vanilla (nez, robe) ?
    > Réponse :
