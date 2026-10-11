# MineCities

*[English version](README.en.md)*

Des villes vivantes pour Minecraft 26.2 (Fabric), inspirées de MineColonies mais dans l'esprit
vanilla : pas de plans imposés ni de huttes préfabriquées. Vous bâtissez comme vous voulez, le mod
reconnaît vos maisons, et des citoyens viennent y vivre. Le village devient ville, puis grande
ville, puis mégalopole.

> État : **v0.4** (villes, maisons, familles, huit métiers dont le bâtisseur et le livreur, écran de
> l'hôtel de ville). La suite est décrite dans [BRIEF.md](BRIEF.md) et [docs/briefs](docs/briefs).

## Jouer

1. **Fonder une ville** : fabriquez un **hôtel de ville** et posez-le. Vous devenez maire. Pour
   choisir le nom de la ville, renommez l'hôtel de ville à l'enclume avant de le poser ; sinon, la
   ville reçoit un nom au hasard. Fondée au cœur d'un village de villageois, la ville le reprend :
   les villageois gardent leurs échanges mais comptent dans la population. On peut diriger
   plusieurs villes.
2. **Bâtir une maison**, avec les blocs que vous voulez : une pièce close (murs, sol, toit), au
   moins une **porte** et au moins un **lit**. Chaque lit loge un citoyen. Accrochez une
   **plaque de maison** sur un mur intérieur : la maison est relevée et enregistrée, et un message
   dit ce qui manque si elle n'est pas reconnue. Un clic sur la plaque la relève à nouveau.
3. **L'hôtel de ville** : un clic ouvre son **écran** : le résumé de la ville, la liste des citoyens
   (métier, faim, famille, ce qui leur manque), le **registre** des demandes de mariage, et le
   **grenier** commun où les citoyens mangent. Accroupi, un clic donne l'état de la ville dans le chat.
4. **La population** : la ville reçoit ses **cinq premiers habitants** ; en dessous de cinq, des
   nouveaux venus arrivent. Au-delà, elle ne grandit que par les **naissances** : les citoyens qui
   vivent et travaillent ensemble s'apprécient, ceux qui s'entendent demandent au maire de les
   **marier** (onglet Registre, ou `/ville marriages` et `/ville marry accept|refuse <n>`). Mariés, ils
   emménagent ensemble ; bien nourris, avec un lit libre chez eux, ils peuvent avoir un **enfant**,
   qui grandit en trois jours. Il faut donc de grandes maisons et un grenier garni.
5. **Les citoyens** se promènent sur le territoire le jour, rentrent dormir dans leur lit la nuit,
   et mangent quand ils ont faim : d'abord dans les coffres et tonneaux de leur maison, sinon au
   grenier. Affamés, ils dépérissent et peuvent mourir de faim. On peut aussi les nourrir à la
   main. Ils **parlent** : une bulle au-dessus de la tête et une ligne dans le chat des joueurs
   proches (outil ou matériau qui manque, faim, « laissez-moi la place »…). Un clic droit sur un
   citoyen donne son état et répète ce qui lui manque encore. Chacun a son geste quand il travaille
   (houe, hache, canne, pose de blocs, brassage…), mange, parle ou se réjouit.
6. **Des métiers** : un bâtiment déclaré qui contient un poste de travail vanilla est un atelier,
   et chaque poste emploie un citoyen.
   - **Composteur → fermier** : il lui faut une **houe** (dans un coffre de l'atelier ou au
     grenier). Il récolte les cultures mûres à 16 blocs du composteur, replante, et porte la
     récolte au grenier.
   - **Fumoir → cuisinier** : il cuit la viande, le poisson et les pommes de terre du grenier, et
     fait du pain avec le blé (3 blés = 1 pain).
   - **Bûche écorcée posée seule au sol (billot) → bûcheron** : avec une **hache**, il abat les
     arbres à 16 blocs, replante et ramasse pousses, pommes et bâtons.
   - **Botte de foin posée seule → éleveur** : il fait naître des petits (avec la nourriture du
     grenier) tant qu'une espèce a moins de 6 têtes, abat le surplus au-delà de 8 adultes.
   - **Baril au bord de l'eau → pêcheur** : avec une **canne à pêche**, il pêche depuis la rive.
     (Loin de l'eau, un baril reste un garde-manger.)
   - **Table d'artisan → bâtisseur** : il bâtit les chantiers commandés par le maire (voir plus bas),
     avec le bon outil pour dégager le terrain : **pioche** (pierre), **pelle** (terre), **hache** (bois).
     Il travaille depuis le pied du chantier ou du haut d'**échafaudages** qu'il dresse et démonte ;
     il demande à qui se trouve sur son chemin de s'écarter. Plusieurs bâtisseurs se partagent un
     chantier, chacun son bloc.
   - **Table de cartographie → livreur** : il porte aux bâtisseurs les matériaux qu'ils réclament,
     pour qu'ils ne quittent pas leur chantier.
   Deux postes dans un même atelier font deux travailleurs : les ateliers agrandis en ont deux.
7. **Faire bâtir** : le maire (ou un adjoint) commande un bâtiment d'après un **schéma**, dans un
   **style** (plaines, taïga, désert, savane) : `/ville build minecities:house_small taiga`. Le
   bâtiment se place devant lui, porte vers lui ; le bâtisseur dégage le terrain et pose les blocs
   en prenant les matériaux au grenier (le maire est prévenu de ce qui manque), et le bâtiment se
   déclare tout seul une fois fini. `/ville upgrade`, dans un bâtiment bâti par la ville,
   l'améliore : les premiers niveaux ont un toit plat en gradins, les améliorations un toit à deux
   pans en escaliers et une fondation de pierre taillée. Chaque bâtiment a un perron (une marche
   devant la porte), et il faut des échafaudages au grenier pour les toits. Petite maison → grande maison → manoir ;
   chaque atelier (ferme avec son champ, cuisine, scierie, étable avec son enclos, pêcherie avec son
   bassin, atelier du bâtisseur) → son grand atelier, à deux postes. Les datapacks peuvent ajouter
   des schémas (`data/<ns>/blueprint/*.json`).
8. **Grandir** : la ville monte de palier avec ses citoyens et ses maisons, et son territoire
   s'étend.

| Palier | Population (citoyens + villageois) | Maisons | Rayon du territoire |
|---|---|---|---|
| Village | 0 | 0 | 48 |
| Ville | 10 | 4 | 80 |
| Grande ville | 30 | 12 | 128 |
| Mégalopole | 75 | 30 | 224 |

### Recettes

- **Hôtel de ville** : cloche au centre du haut, entre deux émeraudes ; pupitre au centre, entouré
  de planches ; coffre en bas au centre, entouré de planches.
- **Plaque de maison** (×2) : une dalle de bois, une pépite de fer, une teinture noire.

### Le maire

Le fondateur est le maire ; un joueur peut diriger plusieurs villes, céder l'écharpe et nommer des
adjoints. Seul le maire peut démolir l'hôtel de ville (et la ville disparaît avec lui). Maire et
adjoints sont prévenus des arrivées, des morts, des embauches et des outils qui manquent. Les
commandes du maire s'appliquent à la ville où il se trouve.

| Commande (`/minecities` ou `/ville`) | Qui | Effet |
|---|---|---|
| `info` | tous | état de la ville où l'on se trouve (ou que l'on gère) |
| `list` | tous | toutes les villes |
| `citizens` | tous | les citoyens, leur faim et leur logement |
| `rename <nom>` | maire | renomme la ville |
| `mayor <joueur>` | maire | passe l'écharpe à un autre joueur |
| `deputy add\|remove <joueur>` | maire | nomme ou démet un adjoint |
| `blueprints` | tous | les schémas disponibles |
| `build <schéma> [style]` | maire, adjoints | commande un chantier devant soi |
| `build cancel` | maire, adjoints | annule le chantier en cours |
| `upgrade` | maire, adjoints | agrandit le bâtiment où l'on se trouve |
| `admin immigrate\|feed\|starve` | opérateurs | tests : faire venir un citoyen, rassasier ou affamer la ville |

## Développer

Java 25 est requis (Loom 1.18.3, Minecraft 26.2).

```sh
./gradlew build                 # compile, tests unitaires, jar dans build/libs
./gradlew runClient             # lance le jeu avec le mod
./gradlew runClientGameTest     # scènes en jeu vérifiées et photographiées (build/run/clientGameTest/screenshots)
./gradlew runClientGameTest -PminecitiesGametest=work   # une scène (city, work, countryside, village, build)
python tools/textures/generate.py   # régénère les textures (peaux des citoyens, plaque, œuf)
python tools/models/town_hall.py    # régénère le modèle de l'hôtel de ville
python tools/blueprints/generate.py # régénère les schémas du bâtisseur
```

Les textures sont produites par `tools/textures/generate.py` : modifier le script et le relancer,
plutôt que retoucher les PNG.

Les pipelines GitHub Actions :

- `build.yml` : build et tests à chaque push sur master et chaque pull request, jar en artefact ;
- `commits.yml` : vérifie la convention des commits d'une pull request ;
- `release.yml` : sur un tag `vX.Y.Z`, publie la release GitHub avec le jar et le changelog
  bilingue généré par git-cliff, puis republie le site ;
- `pages.yml` : le site GitHub Pages (`site/`), à chaque push sur master.

Licence : MIT.

Publier une version : `git tag v0.1.0 && git push origin v0.1.0`.
