# MineCities

Des villes vivantes pour Minecraft 26.2 (Fabric), inspirées de MineColonies mais dans l'esprit
vanilla : pas de plans imposés ni de huttes préfabriquées. Vous bâtissez comme vous voulez, le mod
reconnaît vos maisons, et des citoyens viennent y vivre. Le village devient ville, puis grande
ville, puis mégalopole.

> État : **v0.2** (villes, maisons, citoyens, métiers de fermier et de cuisinier). La suite est
> décrite dans [BRIEF.md](BRIEF.md) et [docs/briefs](docs/briefs).

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
3. **Nourrir la ville** : ouvrez l'hôtel de ville, c'est le **grenier** commun. Tant qu'il y a des
   lits libres et de quoi manger au grenier, un nouveau citoyen arrive toutes les 2 minutes.
4. **Les citoyens** se promènent sur le territoire le jour, rentrent dormir dans leur lit la nuit,
   et mangent quand ils ont faim : d'abord dans les coffres et tonneaux de leur maison, sinon au
   grenier. Affamés, ils dépérissent et peuvent mourir de faim. On peut aussi les nourrir à la
   main. Un clic sur un citoyen donne son état.
5. **Des métiers** : un bâtiment déclaré qui contient un poste de travail vanilla est un atelier,
   et chaque poste emploie un citoyen.
   - **Composteur → fermier** : il lui faut une **houe** (dans un coffre de l'atelier ou au
     grenier). Il récolte les cultures mûres à 16 blocs du composteur, replante, et porte la
     récolte au grenier.
   - **Fumoir → cuisinier** : il cuit la viande, le poisson et les pommes de terre du grenier, et
     fait du pain avec le blé (3 blés = 1 pain).
6. **Grandir** : la ville monte de palier avec ses citoyens et ses maisons, et son territoire
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
| `admin immigrate\|feed\|starve` | opérateurs | tests : faire venir un citoyen, rassasier ou affamer la ville |

## Développer

Java 25 est requis (Loom 1.18.3, Minecraft 26.2).

```sh
./gradlew build                 # compile, tests unitaires, jar dans build/libs
./gradlew runClient             # lance le jeu avec le mod
./gradlew runClientGameTest     # scènes en jeu vérifiées et photographiées (build/run/clientGameTest/screenshots)
./gradlew runClientGameTest -PminecitiesGametest=work   # une seule scène (city, work)
python tools/textures/generate.py   # régénère les textures (peaux des citoyens, plaque, œuf)
python tools/models/town_hall.py    # régénère le modèle de l'hôtel de ville
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
