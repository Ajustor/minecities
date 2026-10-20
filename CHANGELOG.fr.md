# Journal des modifications

## 1.10.0 — 2026-10-20

### Nouveautés

- Le mod est disponible pour Minecraft 26.3 en plus de 26.2 : chaque version a son jar.
- Le livre de recettes montre toutes les recettes du mod (hôtel de ville, plaque, postes…), sans mod de recettes.
- Le budget de la ville : un impôt quotidien au taux fixé par le maire, versé dans une boîte à impôts, le salaire des gardes et des archers, et des chantiers payants à partir du niveau 2.
- La taverne : un tavernier sert à boire, égaie la ville et attire de nouveaux habitants ; le Résumé dit quand arrive le prochain.
- Les matériaux des bâtiments de niveau 3 et 4 se fabriquent en ville sur commande des chantiers (forgeron, charpentier, maçon, tisserand) ou s'achètent au marché ; le marchand fournit aussi la pioche du mineur et les cisailles de l'apiculteur.
- Le mineur garde un peu d'andésite (pour l'andésite polie du maçon) et de gravier (pour le silex).
- Le message de fondation explique la croissance : nouveaux venus jusqu'au minimum, puis la taverne, et où trouver le grenier.
- L'éleveur tond et trait, le garde ramasse le butin des monstres, le médecin prépare la pastèque scintillante, le marchand achète les matières premières qui manquent.
- Le marchand vend aussi ce que la ville fabrique (pain, plats, miel, livres, briques, verre…), plus cher que les ingrédients.
- Le logis compte pour le bonheur : belle maison (+1, +2 au niveau 4), maison surpeuplée ou sans lampe (−1).
- Deux décrets de plus, couvre-feu et frontières ouvertes ; les décrets se prennent à partir du palier Ville.
- Nouveaux bâtiments, niveaux 1 à 4 : le relais du livreur et le pas de tir de l'archer.
- La maîtrise des ateliers de niveau 4 demande un livre prêté par le bibliothécaire ; l'écran du poste dit où elle en est.
- Chaque métier a sa spécialité de maître (gâteau, double fournée, tapis teints, meilleurs cours, soins de zone, travail deux fois plus rapide…).
- L'hôtel de ville montre ce qui pèse sur le bonheur : dans le résumé, et en infobulle sur l'humeur de chaque citoyen.
- Les écrans de l'hôtel de ville, de la carte et des postes se mettent à jour d'eux-mêmes toutes les deux secondes.
- Survoler un plan montre le coût du bâtiment : émeraudes et matériaux, avec ce qu'en ont les réserves.
- Les objets du mod disent à quoi ils servent, et les boutons des écrans ont une infobulle.
- Un écran « Gérer la mairie » : renommer la ville, nommer des adjoints, transmettre la mairie, signer ou rompre des accords, sans commande.
- La faim et le malheur font baisser les affinités ; deux époux qui ne s'entendent plus se séparent.
- La commande /town, en anglais, fait la même chose que /ville.
- Le résumé de l'hôtel de ville estime combien de jours tient la nourriture du grenier.
- Le maire peut suspendre un chantier et le reprendre plus tard, depuis l'établi du bâtisseur.
- Les citoyens ne cassent plus de blocs sur un terrain protégé par un mod de protection.
- Les travailleurs gagnent de l'expérience dans leur métier : jusqu'à 30 % plus rapides, rang affiché sur leur poste.
- Le Mauvais présage attire un raid sur la ville, un ravageur se joint aux grosses vagues, et un marchand ambulant passe chaque semaine.
- Monter de palier demande aussi du bonheur, des métiers variés et un bâtiment public (taverne, monument, bibliothèque) ; une ville qui ne les a plus redescend.
- Le maire peut couper (et rétablir) les élections de sa ville ; un scrutin en cours est alors annulé.
- Les enfants suivent leurs parents et vont à l'école près de la bibliothèque ; les mariés se retrouvent devant l'hôtel de ville pour la cérémonie.
- Les caravanes de commerce prennent la route : un chariot bâché tiré par un cheval, mené par le marchand, qui campe la nuit ; le bâton d'itinéraire trace leur chemin.
- Les marchands des caravanes tiennent échoppe à l'entrée de la ville où ils arrivent et vendent aux joueurs les produits de leur ville.
- Les caravanes voyagent sous escorte et peuvent tomber dans une embuscade de bandits ; un joueur peut piller un chariot, au risque de devenir hors-la-loi.
- Les caravanes ont un grand chariot bâché de l'Ouest aux roues qui tournent, mené par le marchand assis au banc du cocher ; l'échoppe est plus grande et montre ses marchandises.

### Corrections

- Les artisans ne restent plus bloqués à rendre puis reprendre leurs matériaux sans jamais travailler.
- Dissoudre une ville pendant que ses citoyens travaillent ne fait plus planter le serveur.
- Retirer la tour d'un archer posté en haut ne fait plus planter le serveur.
- Un entonnoir ne peut plus vider le grenier de l'hôtel de ville ou d'une annexe (il peut toujours le remplir).
- Seuls le maire et ses adjoints peuvent démolir une mairie annexe.
- L'interface d'un bâtiment ne peut plus servir à déclarer une maison sans plaque ni à surcharger le serveur.
- Un citoyen resté loin des joueurs (au travail, hors de portée) n'est plus rayé de la ville pour disparition, et les citoyens ne prennent plus les portails.
- Un citoyen qui quitte la ville rend au grenier ce qu'il portait (ou le laisse au sol) au lieu de le faire disparaître.
- L'hôtel de ville se fabrique avec un lingot d'or au lieu d'une cloche, introuvable sans village.
- Les artisans ne restent plus bloqués quand les ingrédients d'un ouvrage sont rangés dans des coffres différents.
- Le cuisinier et le boulanger vont chercher leurs ingrédients dans toutes les réserves au lieu de rester bloqués sur un coffre incomplet.
- Les citoyens mangent ce qu'ils portent quand les garde-manger sont vides, et ceux qui nourrissent la ville continuent de travailler quand ils ont faim.
- Le mineur ne perd plus les minerais qui ne tiennent pas dans ses poches, ni ceux d'après une pioche cassée.
- Le marchand ne fait plus disparaître ce qu'il porte quand le grenier est plein.
- Les artisans et le forgeron interrompus (repas, nuit) reprennent leur ouvrage au lieu de rapporter leurs matières.
- Tous les citoyens combattent les incendies, et plus seulement la moitié d'entre eux.
- Un citoyen affamé pendant une fête va manger au lieu de danser jusqu'à défaillir.
- Un citoyen qui ne peut pas atteindre sa cible (derrière une clôture, sur un toit) y renonce et passe à autre chose.
- Le bâtisseur n'attend plus indéfiniment un livreur qui ne vient pas : il va chercher lui-même ses matériaux.
- Quand les réserves sont pleines, le livreur prévient le maire au lieu de rester planté devant le grenier.
- Une maison à cheval sur une zone non chargée n'est plus effacée (habitants expulsés, emplois perdus) à chaque relevé.
- Les élections ne retiennent plus que les candidats et les voix des membres de la ville, et ne relancent plus de tours sans fin faute de votes.
- La mairie ne se transmet qu'à un habitant (et pas pendant une élection), les adjoints sont choisis parmi les habitants, un palier mal écrit est refusé, les anciens schémas ne se commandent plus, et deux villes ne portent plus le même nom.
- L'armure des gardes, les émeraudes des caravanes et les objets posés au sol par les citoyens ne se perdent plus (ni ne s'accumulent à jamais).
- L'établi du bâtisseur n'est offert qu'une fois par joueur, l'espacement des raids survit au redémarrage du serveur, et le démontage de l'hôtel de ville est plus sûr.
- Le bâtisseur ne renonce plus à aller chercher ses matériaux au grenier quand il redescend de ses échafaudages.
- Les messages de refus et d'outil manquant disent juste (plus de « mariages » hors du registre, plus de « d'des cisailles »).
- Registre des mariages paginé, poste libre qui dit s'il manque un habitant, refus de fondation avec la distance et la direction.
- Deux villes voisines se partagent le terrain là où leurs territoires se chevauchent ; une ville ne se fonde plus contre une annexe.
- Un chantier ne peut plus écraser une maison déclarée, un bâtiment fini, l'hôtel de ville ou une annexe ; les murs mitoyens restent.
- Une ville abîmée dans la sauvegarde n'est plus effacée : elle est gardée telle quelle et ses citoyens restent.
- Un chantier ne se termine plus avec un lit ou une porte à moitié posé.
- L'écran de l'hôtel de ville tient à toutes les échelles d'interface et a un bouton Fermer.
- La bâche du chariot des caravanes est plus haute : le marchand y conduit tête couverte, sans la traverser.

### Performances

- Moins de calculs côté serveur : demandes réseau espacées, plans des chantiers gardés en mémoire, gardes et mineurs plus sobres.
## 1.9.1 — 2026-10-14

### Corrections

- Les menus du mod ne débordent plus, en français non plus : écran des bâtiments, hôtel de ville, bâtisseur, carte et guide.
## 1.9.0 — 2026-10-13

### Nouveautés

- Un bouton « Poste » renvoie un citoyen à son poste de travail (onglet Citoyens, interface du poste), sur une case sûre, sans changer son emploi.
- Le puits du mineur : son treuil montre ce qu'il peut donner, le mineur vérifie et répare son chemin de remontée avant de descendre, et un puits épuisé se rebouche depuis le treuil.
- Un seul plan pour tous les bâtiments : son interface choisit le bâtiment et le style, et tenu en main il montre les chantiers en cours.

### Corrections

- L'éleveur ne gaspille plus son blé : deux bêtes en amour éloignées dans le pré marchent l'une vers l'autre.
- Le forgeron porte lui-même aux gardes les pièces d'armure qu'ils attendent, même quand le grenier est plein, et ne perd plus une pièce forgée.
## 1.8.0 — 2026-10-13

### Nouveautés

- Un poste du mod pour chaque métier (fabriqué avec une table de craft et un objet du métier), l'établi du bâtisseur offert à la fondation, et des interfaces : celle d'un poste (son travailleur, ce qui manque, les chantiers) et celle d'un bâtiment (niveau, lits, amélioration avec son coût).
- Un bâtiment fait à la main peut être rebâti au niveau 2 de sa famille depuis son interface : le bâtisseur démolit l'ancien et bâtit le nouveau à sa place.
## 1.7.0 — 2026-10-13

### Nouveautés

- Le fermier récolte la canne à sucre (en laissant le pied) et cueille citrouilles et pastèques, sans semer là où pousse le fruit
- Le cuisinier fait le sucre de ses tartes à la citrouille avec la canne à sucre
- Vos propres bâtiments deviennent des schémas : enregistrez-les avec un bloc de structure (mode Sauvegarde), puis commandez-les avec /ville build
- Avant de commander un bâtiment, son fantôme apparaît en transparence : on le tourne, puis on le valide ou on l'abandonne
- Le plan : pris à l'hôtel de ville (onglet Plans, style au choix), il se pose au sol pour montrer le bâtiment, et resservira
- Compatibilité Jade (facultative) : l'infobulle d'un citoyen montre sa faim et son logement, celle d'une plaque de maison son dernier relevé.
- Habitants joueurs : on demande à s'installer dans une ville (/ville join, ou en dormant dans un de ses lits), le maire accepte, et il règle par rang qui peut déclarer une maison, ouvrir le grenier ou casser les bâtiments (onglet Habitants).
- Élections du maire : au terme du mandat, à l'appel du maire ou par pétition des habitants ; candidatures, vote dans l'onglet Élection ou par /ville vote, les citoyens heureux votent pour le maire sortant, second tour en cas d'égalité.
- Monuments : la mégalopole peut bâtir une statue, une fontaine et un beffroi (un de chaque) ; ils rendent les citoyens plus heureux et donnent de l'attrait à la ville.
- Maladies : rhume (pluie sans manteau), dysenterie (chair putréfiée, aliment empoisonné) et fièvre (qui cloue au lit), contagieuses ; le médecin les soigne avec miel, lait ou pastèque scintillante ; nouveau manteau pour les citoyens.
- Carte du territoire (bouton Carte de l'hôtel de ville) : le territoire, les annexes et les bâtiments à plat, avec zoom ; un clic sur un bâtiment le fait luire.
- Catastrophes rares et désactivables : incendie par orage (les citoyens l'éteignent avec les seaux d'eau du grenier), disette d'un jour, tremblement de terre qui frappe toutes les villes.

### Corrections

- À plusieurs, les bâtisseurs se prêtent matériaux et échafaudages : un chantier ne reste plus bloqué sur son dernier bloc
- Les citoyens ne restent plus plantés devant une porte : on ne la referme plus au nez d'un autre, et un citoyen coincé fait un pas de côté.
## 1.6.1 — 2026-10-12

### Corrections

- Quand l'envoi d'un signalement échoue, le formulaire affiche la raison
## 1.6.0 — 2026-10-12

### Nouveautés

- Le guide de la ville : un livre offert à la première venue, qui explique tout le mod (sommaire cliquable, /ville guide, recette : un livre et une planche)
- /ville bug ouvre le formulaire de signalement du site, déjà rempli avec votre version, vos mods et votre temps de jeu
- Le site a un formulaire pour signaler un bug ou donner son avis, avec captures, journaux, version du mod et temps de jeu

### Corrections

- Le site s'affiche mieux sur téléphone (formulaire, tableau des paliers)
## 1.5.0 — 2026-10-12

### Corrections

- Un citoyen aux poches pleines reprend ce qu'il a dû poser par terre, au lieu de le perdre
- Les bâtisseurs aux poches pleines font de la place avant d'aller chercher un outil ou un matériau, au lieu de le laisser tomber
- Le livreur ne remet que ce qui tient dans les poches du bâtisseur
## 1.4.0 — 2026-10-12

### Nouveautés

- Entrepôt : un registre d'entrepôt fait d'un bâtiment la réserve commune (le grenier garde la nourriture et le trésor)
- Les livreurs font la navette entre les ateliers et l'entrepôt, et garnissent les ateliers à l'avance
- Nouveau métier : charpentier (établi de charpentier), pour les planches et les ouvrages de bois des chantiers
- Le forgeron fait cisailles et blocs de fer, le verrier des fioles, l'archer ses flèches, l'inventeur sculpte ses citrouilles

### Corrections

- Les bâtisseurs récupèrent les lits et les portes qu'ils démontent, et scient leurs bûches quand les planches manquent
- Le livreur ne perd plus ce qu'il tient en main, et remet toujours sa livraison au bâtisseur
## 1.3.0 — 2026-10-12

### Nouveautés

- Intérieurs meublés selon le métier et le niveau du bâtiment (tables, bougies, comptoirs, jarres, plantes…)

### Corrections

- Correction d'un plantage rare quand le garde-manger d'un citoyen se vide pendant qu'il mange
## 1.2.0 — 2026-10-11

### Nouveautés

- Bâtiments habillés : débord de toit, volets, pignons à poutre et lucarne, cheminée qui fume, tapis et pots de fleurs
## 1.1.0 — 2026-10-11

### Nouveautés

- Décrets du maire : rationnement, heures supplémentaires, jour férié, et l'onglet Mairie de l'écran
- Commerce entre villes : accords signés par les deux maires, caravanes payées en émeraudes
- Outillage du maître au niveau 4 : fortune pour le fermier et le mineur, chance de la mer pour le pêcheur, armure complète des gardes
- Nouveau métier : tanneur, au séchoir à peaux (nouveau bloc)
- L'armure des citoyens se voit, et un casque cache le couvre-chef
## 1.0.0 — 2026-10-11

### Nouveautés

- Nouveaux métiers : joaillier (établi de joaillier), maçon (tailleur de pierre), verrier (four de verrier), tisserand (métier à tisser), apiculteur (ruche) et bibliothécaire (bibliothèque ciselée)
- Bijoux (broche d'améthyste, bague en or, diadème), vendus cher au marché
- Une bibliothèque rend la ville plus heureuse et les enfants grandissent en deux jours
- Schémas à quatre niveaux de la joaillerie, de la taillerie, de la verrerie, du tissage, du rucher et de la bibliothèque

### Corrections

- Le mineur et l'apiculteur vont chercher leur outil au grenier sans s'arrêter en chemin
## 0.9.0 — 2026-10-11

### Nouveautés

- Quatre niveaux pour chaque bâtiment (cabane, maison, bâtisse, demeure), de plus en plus chers et productifs
- Nouveau métier : mineur, au treuil de mine (nouveau bloc), qui creuse une vraie galerie et suit les filons
- Nouveau métier : forgeron (haut fourneau), qui fond les minerais et forge outils et plastrons
- Nouveau métier : boulanger (four), pour le pain, les biscuits et les tartes
- Couvre-chefs du marchand, du boulanger, du forgeron et du mineur
- Le grenier de l'hôtel de ville a deux fois plus de place (54 cases)
## 0.8.0 — 2026-10-11

### Nouveautés

- Fichier de configuration config/minecities.json (nouveaux venus, faim, raids, fêtes, marché…)
- Protection facultative des bâtiments déclarés : seuls le maire, ses adjoints et les opérateurs y cassent des blocs
- Un hôtel de ville détruit par une explosion peut être reposé au même endroit : la ville l'attend dix minutes
- Les citoyens disparus sans mourir (commande, autre mod) sont rayés au bout d'une heure
- Les relevés des bâtiments s'étalent dans le temps dans les grandes villes

### Corrections

- Les citoyens coincés à une porte pendant une alerte rejoignent tout de même leur abri
## 0.7.0 — 2026-10-11

### Nouveautés

- Nouveau métier : marchand (pupitre, dès la ville), qui achète les outils manquants et vend le surplus du grenier
- Le trésor (les émeraudes du grenier) et les gains du marché à l'écran de l'hôtel de ville
- Chaque palier débloque des métiers et des schémas (le marché à la ville), annoncés avec un titre et des feux d'artifice
- Mairies annexes pour les grandes villes : un territoire étendu et un grenier de quartier
- Commande d'administration pour imposer un palier à une ville
## 0.6.0 — 2026-10-11

### Nouveautés

- Bonheur des citoyens (0 à 10) : faim, variété des repas, logis, métier, famille, santé, sécurité, fêtes
- Les citoyens se lassent de manger toujours la même chose ; le cuisinier prépare soupes, ragoûts, tartes et biscuits
- Nouveau métier : médecin (alambic), et le schéma de l'infirmerie
- Fêtes offertes par le maire, avec danse et feux d'artifice ; la cloche d'alarme met la ville à l'abri
- Titre d'entrée sur le territoire, contour en particules, plaques vertes ou rouges, icônes au-dessus des citoyens
- Un couvre-chef par métier, et l'onglet Bâtiments de l'écran de l'hôtel de ville
## 0.5.0 — 2026-10-11

### Nouveautés

- Nouveaux métiers de défense : garde (meule), archer (table d'archerie) et inventeur (table de forge) qui fabrique des golems
- Schémas de la caserne, de la tour de garde et de l'atelier de l'inventeur
- Raids de pillards par vagues sur les villes d'au moins 8 habitants, plus nombreuses quand la ville grandit
## 0.4.0 — 2026-10-11

### Nouveautés

- Familles : cinq premiers habitants, puis la ville grandit par les naissances ; les citoyens qui s'entendent demandent au maire de les marier, les couples emménagent ensemble et ont des enfants qui grandissent
- L'hôtel de ville ouvre un écran : résumé, citoyens, registre des mariages (accepter ou refuser) et grenier
- Les citoyens ont un geste pour chaque action (manger, houe, hache, pêche, bâtir, cuisiner, nourrir, parler, se réjouir)
## 0.3.0 — 2026-10-11

### Nouveautés

- Fondez une ville avec l'hôtel de ville et devenez-en le maire ; déclarez vos maisons avec une plaque, nourrissez vos citoyens au grenier, et faites grandir le village jusqu'à la mégalopole
- Un joueur peut être maire de plusieurs villes ; les citoyens affamés dépérissent jusqu'à mourir de faim au lieu de partir
- L'hôtel de ville a son propre modèle : un petit bâtiment de pierre, de chêne et de sapin coiffé d'une cloche
- Métiers : un composteur ou un fumoir dans un bâtiment déclaré emploie un citoyen ; le fermier récolte et replante les champs avec une houe, le cuisinier cuit la viande et fait du pain au fumoir, et tout part au grenier
- Fonder une ville sur un village vanilla le reprend : ses villageois, qui gardent leurs échanges, comptent dans la population de la ville
- Dans le Nether et l'End, les citoyens ne se couchent pas (les lits y exploseraient)
- Nouveaux métiers : bûcheron (bûche écorcée posée au sol, hache), éleveur (botte de foin) et pêcheur (baril au bord de l'eau, canne à pêche)
- Dans un village repris, les maisons des villageois deviennent des maisons de la ville, et les villageois sans métier deviennent peu à peu citoyens
- Le bâtisseur (table d'artisan) construit les bâtiments commandés par le maire d'après huit schémas, en quatre styles (plaines, taïga, désert, savane), et agrandit les maisons ; /ville build, /ville blueprints, /ville upgrade
- Les bâtiments ont un perron devant la porte ; les améliorations ont un toit à deux pans en escaliers et une fondation de pierre taillée (manoir, grands ateliers à deux postes)
- Le bâtisseur dégage avec pioche, pelle et hache, monte sur des échafaudages, travaille du dehors, va cinq fois plus vite et partage le chantier avec les autres bâtisseurs ; on s'écarte de son chemin
- Nouveau métier : le livreur (table de cartographie) porte aux bâtisseurs les matériaux qu'ils réclament
- Les citoyens parlent (bulle et chat à proximité) quand il leur manque quelque chose ; un clic droit répète leurs besoins

### Corrections

- Une seconde plaque dans une maison déjà déclarée est refusée, au lieu de lui disputer ses lits
- Les citoyens ouvrent les portes de leur chemin et les referment derrière eux ; ils ne restent plus bloqués devant leur maison

