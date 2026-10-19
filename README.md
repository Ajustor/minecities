# MineCities

*[English version](README.en.md)*

Des villes vivantes pour Minecraft 26.2 et 26.3 (Fabric), inspirées de MineColonies mais dans l'esprit
vanilla : pas de plans imposés ni de huttes préfabriquées. Vous bâtissez comme vous voulez, le mod
reconnaît vos maisons, et des citoyens viennent y vivre. Le village devient ville, puis grande
ville, puis mégalopole.

> État : **v1.9** (un plan unique pour tous les bâtiments et l'aperçu des chantiers en cours, puits du mineur
> estimé et rebouché, renvoi d'un citoyen à son poste, un poste du mod et une interface pour chaque métier, interface des bâtiments et
> amélioration de ceux faits à la main, habitants joueurs et rangs, élections du maire, monuments, maladies et manteau,
> carte du territoire, catastrophes, infobulles Jade, guide de la ville et formulaire de signalement, villes, maisons, familles, vingt-quatre métiers et leurs filières, de la mine au
> bijou et du blé au pain, bâtiments à quatre niveaux et maîtrise du niveau 4, décrets du maire,
> commerce entre villes, bâtiments décorés et meublés, entrepôt et logistique, bonheur et repas variés, marché et trésor, paliers qui
> débloquent, mairies annexes, fêtes, cloche d'alarme, raids de pillards, écran de l'hôtel de ville,
> configuration et protections pour les serveurs). La suite est décrite dans [BRIEF.md](BRIEF.md) et [docs/briefs](docs/briefs).

## Jouer

À sa première venue, chaque joueur reçoit le **guide de la ville**, un livre qui explique tout ce qui
suit, avec un sommaire cliquable (`/ville guide` en redonne un ; recette : un livre et une planche).
Un bug, une idée, un avis ? `/ville bug` ouvre le
[formulaire de signalement](https://ajustor.github.io/minecities/report.html), déjà rempli avec la
version du mod, les autres mods et le temps de jeu.

1. **Fonder une ville** : fabriquez un **hôtel de ville** et posez-le. Vous devenez maire. Pour
   choisir le nom de la ville, renommez l'hôtel de ville à l'enclume avant de le poser ; sinon, la
   ville reçoit un nom au hasard. Fondée au cœur d'un village de villageois, la ville le reprend :
   les villageois gardent leurs échanges mais comptent dans la population. On peut diriger
   plusieurs villes.
2. **Bâtir une maison**, avec les blocs que vous voulez : une pièce close (murs, sol, toit), au
   moins une **porte** et au moins un **lit**. Chaque lit loge un citoyen. Accrochez une
   **plaque de maison** sur un mur intérieur : la maison est relevée et enregistrée, et un message
   dit ce qui manque si elle n'est pas reconnue. Un clic sur la plaque la relève à nouveau.
3. **L'hôtel de ville** : un clic ouvre son **écran** : le résumé de la ville (et son bonheur moyen),
   la liste des citoyens (métier, faim, humeur, famille, ce qui leur manque ; « Où ? » les fait luire),
   les **bâtiments**, le **registre** des demandes de mariage, et le **grenier** commun où les citoyens
   mangent. Le bouton **Carte** montre le territoire à plat, comme une carte : le disque de l'hôtel
   de ville et ceux des annexes, chaque bâtiment de la couleur de son genre, et les chantiers. La
   molette zoome, on fait glisser pour se déplacer, et un clic sur un bâtiment le fait luire en jeu.
   Accroupi, un clic donne l'état de la ville dans le chat. En entrant sur un territoire, un
   titre vous souhaite la bienvenue ; un hôtel de ville ou une plaque en main, son contour se dessine
   en particules. Une plaque montre le dernier relevé : porte **verte** si le bâtiment est enregistré,
   **rouge** sinon.
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
   (houe, hache, canne, pose de blocs, brassage…), mange, parle, danse ou se réjouit, et le
   couvre-chef de son métier (chapeau de paille, toque, bonnet, chapeau de fer, lunettes…). Au-dessus
   de la tête d'un citoyen qui va mal, une petite icône dit pourquoi (faim, sans logis, malheureux,
   blessé).
   - **Le bonheur**, de 0 à 10 : repu, logé, employé, marié, il est content ; affamé, sans logis,
     sans outil, blessé, en deuil ou en danger, il l'est moins. Il retient ses **5 derniers repas** :
     manger toujours la même chose nourrit moins et lasse, un régime varié (et un bon plat : soupe,
     ragoût, tarte, biscuits) rend heureux ; plus la ville grandit, plus on attend de variété.
     Heureux, il travaille plus vite et peut avoir des enfants ; malheureux, il traîne ; au plus mal
     pendant un jour, il quitte la ville. Une ville heureuse attire plus vite les villageois.
6. **Des métiers** : un bâtiment déclaré qui contient un poste de travail vanilla est un atelier,
   et chaque poste emploie un citoyen. Chaque métier a aussi son **poste du mod** (établi du fermier,
   établi du bâtisseur, comptoir du marchand…), qui marche comme le bloc vanilla : il se fabrique
   avec une table de craft et un objet du métier (graines, bol, brique, papier, émeraude…), et un
   clic droit ouvre son **interface** : ce que fait le métier, si le poste sert (sinon pourquoi :
   bâtiment non déclaré, palier, eau), son travailleur et ce qui lui manque, « Où ? » pour le voir,
   et pour le bâtisseur les chantiers et leur avancement. À la fondation, le maire reçoit l'**établi
   du bâtisseur** : posé dans une maison déclarée, la ville a son premier bâtisseur.
   - **Composteur → fermier** : il lui faut une **houe** (dans un coffre de l'atelier ou au
     grenier). Il récolte les cultures mûres à 16 blocs du composteur, replante, et porte la
     récolte au grenier.
   - **Fumoir → cuisinier** : il cuit la viande, le poisson et les pommes de terre du grenier, fait
     du pain (3 blés), des biscuits (blé et cacao), des soupes (champignons, betteraves), le ragoût de
     lapin et la tarte à la citrouille quand le grenier a ce qu'il faut (et des bols).
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
     pour qu'ils ne quittent pas leur chantier ; le reste du temps, il fait la navette : il vide les
     coffres des ateliers de leur production vers l'entrepôt (la nourriture au grenier), et y dépose à
     l'avance ce que chaque métier consomme.
   - **Établi de charpentier → charpentier** : planches (de chaque essence), bâtons, bols, et sur
     commande les portes, barrières, escaliers, dalles et trappes que réclament les bâtisseurs.
   - **Meule → garde** : avec une **épée** (et l'armure qu'il trouve au grenier), il fait sa ronde,
     veille la nuit et se bat contre les monstres du territoire.
   - **Table d'archerie → archer** : avec un **arc** et des flèches, il monte au point le plus haut
     près de sa table (le haut d'une tour de garde) et tire sur les monstres jusqu'à 24 blocs.
   - **Alambic → médecin** : il soigne les blessés (et les empoisonnés) et porte à manger, pris au
     grenier, à ceux qui meurent de faim. Il soigne aussi les **malades**, avec le remède de leur
     maladie pris aux réserves : du miel pour le **rhume**, du lait pour la **dysenterie**, une
     tranche de pastèque scintillante pour la **fièvre**.
   - **Les maladies** : sous la pluie sans **manteau**, on prend un rhume (plus rarement la fièvre) ;
     de la chair putréfiée ou un aliment empoisonné (qu'un affamé mange faute de mieux) donnent la
     dysenterie. Chaque maladie couve d'abord, puis se déclare (☣ au-dessus de la tête), passe aux
     voisins, pèse sur le moral et ralentit le travail ; la fièvre cloue au lit. On n'en meurt pas ;
     on guérit seul avec le temps, bien plus vite une fois soigné, et l'on en est protégé deux jours.
     Le **manteau** (cuir et laine, ou fait par le tisserand) se prend au grenier, ou se donne d'un
     clic droit.
   - **Pupitre → marchand** (dès la ville) : il achète, avec les émeraudes du grenier, l'outil qu'un
     travailleur réclame (houe, hache, pioche, pelle, canne, épée, arc), des flèches, du pain si le
     grenier est vide ; sinon il vend au cours des villageois ce que le grenier a en trop (au-delà de
     32 de chaque), 32 émeraudes par jour au plus.
   - **Four → boulanger** : pain, biscuits et tartes ; le cuisinier lui laisse alors la boulangerie et
     garde viandes, poissons, soupes et ragoûts.
   - **Haut fourneau → forgeron** (dès la ville) : il forge l'outil qu'un travailleur réclame (en fer
     s'il y a des lingots, sinon en pierre), un plastron de fer pour le garde qui n'en a pas, et fond
     en lingots le fer, l'or et le cuivre bruts.
   - **Treuil de mine → mineur** (avec une **pioche**) : il creuse une vraie galerie, à l'est de la
     mine : un escalier jusqu'à la profondeur des minerais, puis des couloirs ; il suit les filons,
     pose des torches (charbon trouvé et bâtons du grenier), garde un peu de pierre et rapporte les
     minerais. Il ne perce jamais dans l'eau ni la lave, ni sous les maisons, ni hors du territoire.
     Le treuil (clic droit) dit ce que le puits peut donner (et ce qu'il a donné) ; avant de
     descendre, le mineur vérifie le chemin de remontée et le répare (un éboulement), et ne s'engage
     pas s'il est coupé. Plus de minerai à portée : le puits est épuisé, et le maire le rebouche
     depuis le treuil (pas tant qu'il reste du minerai, ni sur quelqu'un).
   - **Établi de joaillier → joaillier** (dès la ville) : broches d'améthyste (cuivre), bagues
     (or et lapis), diadèmes (or et diamant) ; le marchand les vend 2, 5 et 12 émeraudes pièce.
   - **Tailleur de pierre → maçon** : briques (argile), pierre taillée (pierre), andésite polie,
     pour les bâtisses et les demeures.
   - **Four de verrier → verrier** : verre (sable), vitres, vitraux blancs (avec de la teinture).
   - **Métier à tisser → tisserand** : laine (ficelle) et lits (laine et planches).
   - **Ruche → apiculteur** (avec des **cisailles**) : il récolte les ruches pleines du territoire, en
     fioles de miel s'il en a, sinon en rayons.
   - **Bibliothèque ciselée → bibliothécaire** (dès la ville) : des livres (papier et cuir, papier de
     canne à sucre) ; avec une bibliothèque, toute la ville est plus heureuse et les enfants grandissent
     en deux jours au lieu de trois.
   - **Séchoir à peaux → tanneur** : du cuir (peaux de lapin), et des tuniques de cuir pour les gardes.
   - **Comptoir de taverne → tavernier** : il sert à boire (miel, baies, pommes, pris aux
     réserves) ; le jour où la taverne a servi, toute la ville est plus gaie. Surtout, la taverne fait
     **grandir la ville** au-delà de cinq habitants : avec un lit libre et une ville assez heureuse, un
     nouveau venu tous les 6 jours moins le niveau de la taverne (et moins l'attrait des monuments), au
     moins un. Le Résumé dit quand arrive le prochain, ou ce qui l'en empêche.
   - **Table de forge → inventeur** : quand la ville est attaquée, il assemble devant l'hôtel de ville
     un **golem de fer** (4 blocs de fer et une citrouille sculptée, pris au grenier) ou, à défaut, un
     **golem de neige** (2 blocs de neige et une citrouille).
   Deux postes dans un même atelier font deux travailleurs : les ateliers agrandis en ont deux.
7. **Faire bâtir** : le maire (ou un adjoint) commande un bâtiment d'après un **schéma**, dans un
   **style** (plaines, taïga, désert, savane). Il prend son **plan** à l'hôtel de ville (onglet
   **Plans**, où il choisit le style) et le pose au sol, ou tape `/ville build minecities:house_1 taiga` :
   le **fantôme** du bâtiment apparaît, en transparence, pour lui et les joueurs proches. Accroupi avec
   le plan (ou `/ville build rotate`), il le tourne ; `/ville build confirm` (ou le bouton du chat) le
   commande, `/ville build cancel` l'abandonne sans rien dépenser. Le plan resservira. Chaque
   bâtiment a **quatre niveaux**, de plus en plus chers et utiles : 1 **cabane** (rondins, terre
   battue, toit plat), 2 **maison** (planches, pierre, vitres, toit à deux pans), 3 **bâtisse** (pierre
   taillée, briques, fer, lanternes ; dès la ville), 4 **demeure** (pierre polie, quartz, vitraux,
   cuivre, or ; dès la grande ville). Chaque bâtiment est habillé : débord de toit, volets ouverts aux
   fenêtres, poutre et lucarne aux pignons, cheminée de pierre ou de briques où fume un feu de camp,
   pot de fleurs, et dans les maisons un tapis de l'entrée au fond (et une bibliothèque dès le niveau
   3). Chaque bâtiment est **meublé** selon son métier, d'autant plus que son niveau est haut : établi,
   table et bougies, jarres, plantes et coffres dans les maisons ; comptoirs, chaudron et gâteau à la
   cuisine et à la boulangerie ; enclume et barreaux à la forge ; rails à la mine ; rayonnages à la
   bibliothèque ; citrouilles et melons à la ferme… (rien qui soit un poste de travail). Une maison gagne deux lits par niveau (2 à 8), un atelier un poste
   (1 à 4), et ses travailleurs vont plus vite (un dixième par niveau). `/ville upgrade`, ou le bouton
   **Améliorer** de l'interface du bâtiment (clic sur sa plaque, ou « Détails » dans l'onglet
   Bâtiments, qui montre aussi le coût et ce que le grenier en a), passe au niveau suivant. Au niveau 4, les travailleurs ont l'**outillage du maître** : le fermier et le mineur
   récoltent comme avec une Fortune II, le pêcheur pêche comme avec une Chance de la mer III, et le
   forgeron habille les gardes de fer de pied en cap. Le
   bâtiment se place devant lui, porte vers lui ; le bâtisseur dégage le terrain et pose les blocs
   en prenant les matériaux au grenier (le maire est prévenu de ce qui manque), et le bâtiment se
   déclare tout seul une fois fini. `/ville upgrade`, dans un bâtiment bâti par la ville,
   l'améliore : les premiers niveaux ont un toit plat en gradins, les améliorations un toit à deux
   pans en escaliers et une fondation de pierre taillée. Chaque bâtiment a un perron (une marche
   devant la porte), et il faut des échafaudages au grenier pour les toits. Petite maison → grande maison → manoir ;
   chaque atelier (ferme avec son champ, cuisine, scierie, étable avec son enclos, pêcherie avec son
   bassin, atelier du bâtisseur) → son grand atelier, à deux postes. Pour la défense : la **caserne**
   (deux lits, deux meules ; agrandie, trois), la **tour de garde** (une table d'archerie, un poste
   tout en haut), l'**atelier de l'inventeur**, l'**infirmerie**, la **mine**, la **forge**, la
   **boulangerie**, la **taillerie de pierre**, la **verrerie**, le **tissage**, le **rucher** et, dès la
   ville, le **marché**, la **joaillerie** et la **bibliothèque** ; chacun a ses quatre niveaux. Les datapacks peuvent ajouter
   des schémas (`data/<ns>/blueprint/*.json`). Vos propres bâtiments aussi : enregistrez-en un avec un
   **bloc de structure** vanilla (mode Sauvegarde, porte au nord, une plaque de maison à l'intérieur pour
   qu'il se déclare tout seul) ; quelques secondes après, `/ville build <nom>` le commande, comme
   `/ville build minecraft:ma_maison`.
7 bis. **L'entrepôt** : un **registre d'entrepôt** posé dans un bâtiment déclaré en fait l'entrepôt de la
   ville, et tous ses coffres la réserve commune. La nourriture et les émeraudes vont au grenier, tout
   le reste à l'entrepôt. Sans livreur, chacun va lui-même y déposer et y prendre ; avec des livreurs,
   les travailleurs laissent leur production dans le coffre de leur atelier et les livreurs font les
   voyages. Le forgeron fait aussi les cisailles (apiculteur) et les blocs de fer (golems), le verrier
   les fioles, l'archer taille ses flèches (silex, bâton, plume), l'inventeur sculpte ses citrouilles.
8. **Grandir** : la ville monte de palier avec ses citoyens et ses maisons (feux d'artifice, et la
   liste de ce qui s'ouvre) ; son territoire s'étend. La **ville** ouvre le marché et le marchand ;
   la **grande ville** peut poser 2 **mairies annexes** (4 en mégalopole) : posées sur le territoire
   à 40 blocs au moins de l'hôtel de ville, elles l'étendent de 48 blocs autour d'elles et gardent
   un grenier où mangent les citoyens du quartier. Le **trésor** est l'émeraude du grenier. La
   **ville** ouvre aussi les **monuments** : la statue, la fontaine et le beffroi, un de chaque par
   ville, en pierre polie, quartz et or. Bâtis, ils rendent les citoyens plus heureux (+1, +2 à partir
   de deux) et donnent de l'**attrait** à la ville (montré dans le résumé).
9. **Les raids** : une ville d'au moins 8 habitants attire les pillards. Au crépuscule, une fois tous
   les trois jours au plus, un raid peut partir du bord du territoire, comme un raid vanilla : une
   barre de boss, plusieurs vagues de pillards et de vindicateurs qui marchent sur l'hôtel de ville.
   Plus la ville a d'habitants et de maisons, plus il y a de vagues (jusqu'à 7) et de pillards par
   vague. Les civils se mettent à l'abri, les gardes accourent à l'hôtel de ville, les archers
   tirent de leurs tours et l'inventeur fait des golems. Les pillards qui atteignent l'hôtel de ville
   pillent le grenier ; une victoire y dépose des émeraudes, et la ville fait la fête.
10. **La cloche d'alarme** : sonner une cloche sur le territoire donne l'alerte pour deux minutes :
   les civils rentrent chez eux, les gardes accourent, l'inventeur fait ses golems, et les monstres
   alentour luisent. Sonnée à nouveau, elle lève l'alerte.
   **Les catastrophes**, rares et désactivables (`disasters`) : par orage, dès la ville, la foudre
   peut mettre le **feu** au toit d'un bâtiment ; les citoyens accourent avec les **seaux d'eau** du
   grenier (gardez-en). La **disette** fait perdre les récoltes un jour (les champs ne donnent que de
   quoi ressemer). Le **tremblement de terre** frappe toutes les villes à la fois : dix secondes de
   secousses qui font tomber des blocs et blessent. Le feu et les secousses peuvent tuer. Chaque
   catastrophe est annoncée et se voit dans le résumé ; au plus une par ville tous les dix jours.
11. **Les fêtes** : le maire offre une fête (`/ville festival` ou le bouton de l'écran) contre
   4 émeraudes du grenier, une tous les trois jours au plus. Les citoyens dansent devant l'hôtel de
   ville sous les feux d'artifice, et sont plus heureux pendant un jour.
12. **Les décrets** (onglet Mairie, ou `/ville decree <décret>`) : le **rationnement** (on mange moins,
   le moral baisse), les **heures supplémentaires** (on travaille de l'aube à la nuit, le moral baisse),
   le **jour férié** (personne ne travaille, le moral monte).
   **L'impôt**, juste en dessous : un taux (aucun, bas, normal, élevé) que chaque adulte verse chaque
   matin dans la **boîte à impôts** ; plus il est haut, plus la ville grogne. Les gardes et les archers
   sont payés chaque matin sur le grenier (une émeraude chacun) : au maire d'y porter l'argent de la
   boîte, sinon ils traînent. Les chantiers coûtent aussi des émeraudes à partir du niveau 2 (4, 12,
   32). Le bilan du jour s'affiche dans l'onglet Mairie (réglage `budget` dans `config/minecities.json`).
13. **Le commerce entre villes** : `/ville trade <ville>` signe un accord ; quand les deux maires ont
   signé, une caravane part chaque minute du marché de la ville qui a trop d'une marchandise vers
   celle qui en manque, contre des émeraudes au cours des villageois (il faut un marchand au départ, et
   les deux greniers chargés).

| Palier | Population (citoyens + villageois) | Maisons | Bonheur moyen | Métiers différents | Bâtiment public | Rayon du territoire |
|---|---|---|---|---|---|---|
| Village | 0 | 0 | — | — | — | 48 |
| Ville | 10 | 4 | 3 | 5 | une taverne | 80 |
| Grande ville | 30 | 12 | 5 | 10 | un monument | 128 |
| Mégalopole | 75 | 30 | 6 | 16 | une bibliothèque | 224 |

Une ville qui ne remplit plus les conditions de son palier redescend (un point de bonheur de marge) ;
tant qu'elle n'a pas monté, elle n'accueille plus de nouveaux venus au-delà de la population du palier
suivant.

### Configuration

`config/minecities.json`, créé au premier lancement (une clé absente reprend sa valeur par défaut) :

| Réglage | Défaut | Effet |
|---|---|---|
| `newcomer_minimum`, `newcomer_delay` | 5, 400 | sous ce nombre d'habitants, un nouveau venu tous les tant de ticks |
| `hunger_day_ticks` | 24000 | la faim se vide en tant de ticks |
| `miserable_citizens_leave` | vrai | les citoyens au plus mal pendant un jour s'en vont |
| `raids`, `raid_min_population`, `raid_odds`, `raid_spacing_days` | vrai, 8, 3, 3 | les raids de pillards |
| `festival_cost` | 4 | le prix d'une fête, en émeraudes |
| `market_reserve`, `market_daily_earnings` | 32, 32 | ce que le grenier garde, ce qu'un marchand gagne par jour |
| `protect_buildings` | faux | dans les villes fondées avant les rangs, seuls le maire et ses adjoints (et les opérateurs) cassent les blocs des bâtiments déclarés ; les villes nouvelles règlent ce droit par rang |
| `term_days`, `candidacy_days`, `voting_days`, `election_spacing_days` | 7, 1, 1, 3 | le mandat du maire (0 : pas d'élection d'office), les candidatures, le vote, l'écart entre deux élections |
| `disasters`, `disaster_spacing_days` | vrai, 10 | les catastrophes (incendie, disette, séisme), et le délai minimal entre deux dans une ville (le double pour le séisme) |
| `hall_grace_minutes` | 10 | un hôtel de ville détruit (explosion…) : le maire a tant de minutes pour le reposer au même endroit, sinon la ville disparaît |
| `missing_citizen_minutes` | 60 | un citoyen jamais revu depuis tant de minutes, sa maison chargée, est rayé (0 : jamais) |
| `give_guide` | vrai | chaque joueur reçoit le guide de la ville à sa première venue |

### Jade

Avec [Jade](https://modrinth.com/mod/jade) (facultatif), l'infobulle d'un citoyen dit sa faim et s'il
est logé, et celle d'une plaque de maison l'état de son dernier relevé. Chaque ligne se coupe dans la
configuration de Jade. Sans Jade, rien ne change.

### Recettes

- **Hôtel de ville** : lingot d'or au centre du haut, entre deux émeraudes ; pupitre au centre, entouré
  de planches ; coffre en bas au centre, entouré de planches.
- **Plaque de maison** (×2) : une dalle de bois, une pépite de fer, une teinture noire.
- **Mairie annexe** : planche, carte, planche ; planche, tonneau, planche ; trois bûches.
- **Boîte à impôts** : fer, émeraude, fer ; planche de chêne noir, coffre, planche ; trois planches de
  chêne noir.
- **Treuil de mine** : bûche, ficelle, bûche ; bûche, rien, bûche ; planche, chaîne, planche.
- **Établi de joaillier** : vitre, éclat d'améthyste, lingot de fer ; trois planches ; bûche, rien, bûche.
- **Four de verrier** : brique, rien, brique ; brique, fourneau, brique ; brique, sable, brique.
- **Séchoir à peaux** : trois bâtons ; bâton, cuir, bâton ; bâton, rien, bâton.
- **Registre d'entrepôt** : un livre, une plume, un tonneau (en colonne).
- **Établi de charpentier** : lingot de fer, bâton ; trois planches ; bûche, rien, bûche.
- **Guide de la ville** : un livre et une planche, sans forme.

### Le maire

Le fondateur est le maire ; un joueur peut diriger plusieurs villes, céder l'écharpe et nommer des
adjoints. Seul le maire peut démolir l'hôtel de ville (et la ville disparaît avec lui). Maire et
adjoints sont prévenus des arrivées, des morts, des embauches et des outils qui manquent. Les
commandes du maire s'appliquent à la ville où il se trouve.

Les autres joueurs sont des **visiteurs**. Pour devenir **habitant**, on demande à s'installer
(`/ville join`, le bouton de l'onglet Habitants, ou en dormant dans un lit d'une maison de la ville) ;
le maire ou un adjoint accepte. On n'est habitant que d'une ville à la fois. Le maire règle, dans
l'onglet Habitants de l'hôtel de ville, ce que l'habitant et le visiteur ont le droit de faire :
déclarer une maison, ouvrir le grenier (et les annexes), casser les blocs des bâtiments déclarés. Par
défaut, l'habitant déclare et ouvre le grenier, le visiteur rien ; le maire et ses adjoints ont tous
les droits. Les villes fondées avant les rangs gardent les droits d'avant (tout le monde déclare et
ouvre le grenier).

**Élections.** Le mandat du maire dure 7 jours de jeu ; à son terme, une élection s'ouvre. Le maire
peut aussi en ouvrir une à tout moment, et les habitants par une pétition signée par la moitié
d'entre eux ; pas plus d'une élection tous les 3 jours. Un jour de candidatures (le maire sortant est
candidat d'office), puis un jour de vote dans l'onglet Élection ou par `/ville vote`. Chaque joueur
de la ville vote une fois ; les citoyens aussi, avec une chance égale à leur bonheur : les heureux
pour le maire sortant, les autres contre lui, et leur voix pèse autant. En cas d'égalité, on revote
entre les ex æquo. Seul joueur de sa ville, ou sans adversaire, le maire est réélu d'office.

| Commande (`/minecities` ou `/ville`) | Qui | Effet |
|---|---|---|
| `info` | tous | état de la ville où l'on se trouve (ou que l'on gère) |
| `list` | tous | toutes les villes |
| `citizens` | tous | les citoyens, leur faim et leur logement |
| `rename <nom>` | maire | renomme la ville |
| `mayor <joueur>` | maire | passe l'écharpe à un autre joueur |
| `deputy add\|remove <joueur>` | maire | nomme ou démet un adjoint |
| `join [ville]` | tous | demande à s'installer dans cette ville (ou celle où l'on est) |
| `leave` | habitants, adjoints | quitte sa ville |
| `resident` | tous | les joueurs de la ville, les demandes, les droits de chaque rang |
| `resident accept\|refuse <joueur>` | maire, adjoints | répond à une demande |
| `resident remove <joueur>` | maire, adjoints | renvoie un habitant |
| `permission resident\|visitor declare\|granary\|break true\|false` | maire | donne ou retire un droit |
| `election` | tous | le mandat, la pétition, l'élection en cours |
| `election call` | maire | ouvre une élection |
| `election sign` | habitants, adjoints | signe la pétition pour une élection |
| `election run` | joueurs de la ville | se porte candidat, pendant les candidatures |
| `vote <candidat>` | joueurs de la ville | vote, une fois par tour |
| `blueprints` | tous | les schémas disponibles |
| `build <schéma> [style]` | maire, adjoints | propose un chantier devant soi, et montre son fantôme |
| `build rotate` | maire, adjoints | tourne le chantier proposé d'un quart de tour |
| `build confirm` | maire, adjoints | commande le chantier proposé |
| `build cancel` | maire, adjoints | abandonne le chantier proposé, sinon annule le chantier en cours |
| `upgrade` | maire, adjoints | agrandit le bâtiment où l'on se trouve |
| `festival` | maire, adjoints | offre une fête (4 émeraudes du grenier) |
| `decree <décret>` | maire, adjoints | prend ou lève un décret (`rationing`, `overtime`, `holiday`) |
| `trade [ville]` | maire, adjoints | les accords de commerce ; signe ou rompt celui avec cette ville |
| `guide` | tous | donne le guide de la ville |
| `bug` | tous | un lien vers le formulaire de signalement, prérempli (versions, mods, temps de jeu, ville) |
| `admin immigrate\|feed\|starve` | opérateurs | tests : faire venir un citoyen, rassasier ou affamer la ville |
| `admin raid\|raid_stop` | opérateurs | tests : lancer ou arrêter un raid |
| `admin festival` | opérateurs | tests : une fête, gratuite |
| `admin tier <palier>\|auto` | opérateurs | impose un palier à la ville (ou le rend à la population) |

## Développer

Java 25 est requis (Loom 1.18.3, Minecraft 26.2 et 26.3). Chaque version de Minecraft a son projet
Gradle (`:26.2`, `:26.3`) et son jar, des mêmes sources : voir [ARCHITECTURE.md](ARCHITECTURE.md).

```sh
./gradlew build                 # les deux versions : compile, tests unitaires, toutes les scènes sans fenêtre, un jar par version dans build/libs
./gradlew :26.2:runClient       # lance le jeu avec le mod (Minecraft 26.2 ; :26.3:runClient pour 26.3)
./gradlew :26.2:runGameTest     # les scènes de jeu, vérifiées sur le seul serveur, sans fenêtre (rapide)
./gradlew :26.2:runGameTest -PminecitiesGametest=work   # une scène (data, budget, tavern, city, work, countryside, village, build, family, raid, life, market, robust, industry, crafts,
council, logistics, guide, structures, plans, jade, residents, elections, monuments, health, map, disasters, stations, mine)
./gradlew :26.2:runClientGameTest   # les mêmes scènes dans le vrai jeu, photographiées (versions/26.2/build/run/clientGameTest/screenshots), plus styles
./gradlew :26.2:runClientGameTest -PminecitiesFilm   # avec les films image par image (lents)
python tools/textures/generate.py   # régénère les textures (peaux des citoyens, plaque, œuf)
python tools/models/town_hall.py    # régénère le modèle de l'hôtel de ville
python tools/blueprints/generate.py # régénère les schémas du bâtisseur
python tools/data/migrate.py        # après avoir touché à src/main/resources/data : les données des versions plus récentes (src/overlays/)
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
