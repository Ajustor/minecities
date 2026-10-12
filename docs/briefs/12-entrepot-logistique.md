# Brief 12 — Entrepôt, logistique et fabrications manquantes

## Demande

« Tu sais voir et me dire si on a des métiers de production qui savent crafter les items demandés par
les autres villageois ? Il faut aussi prévoir un bâtiment qui sert d'entrepôt où tout le monde peut
aller déposer/prendre ce dont il a besoin si aucun livreur n'est dispo ; si un ou des livreurs sont
dispo, alors ce sont eux qui font les voyages en prévision des demandes. »

## Ce que la ville fabrique déjà (v1.3)

| Besoin | Qui le réclame | Qui le fait |
|---|---|---|
| Houe, hache, pioche, pelle, épée | fermier, bûcheron, mineur, bâtisseur, garde | forgeron (fer, ou pierre) |
| Canne à pêche, arc | pêcheur, archer | forgeron (bâtons, ficelle) |
| Plastron, armure complète | garde | forgeron (niveau 4 pour l'armure complète), tanneur (cuir) |
| Briques, pierre taillée, andésite polie | bâtisseur | maçon |
| Verre, vitres, vitraux | bâtisseur | verrier |
| Lits, laine | bâtisseur, maisons | tisserand |
| Pierre, minerais | bâtisseur, forgeron, joaillier | mineur |
| Bûches, bâtons | bâtisseur, mineur (torches) | bûcheron |
| Nourriture | tous | fermier, éleveur, pêcheur, cuisinier, boulanger, apiculteur |
| Lingots d'or et de cuivre | joaillier | forgeron |
| Cuir, livres | bibliothécaire | tanneur, éleveur ; bibliothécaire |

## Ce que personne ne fabrique (le marchand en achète une partie)

- **Planches et ouvrages de bois** (portes, barrières, escaliers, trappes, dalles) : rien ne transforme
  les bûches ; c'est le premier besoin des bâtisseurs.
- **Flèches** (archer), **cisailles** (apiculteur).
- **Blocs de fer, citrouilles sculptées** (golems de l'inventeur).
- **Bols** (soupes), **fioles** (miel), **sucre** et **papier** (pas de canne à sucre), **ficelle**.

## État (v1.4)

Fait : le registre d'entrepôt (bloc et modèle), `town/Storage` (la nourriture et les émeraudes au
grenier, le reste à l'entrepôt), le dépôt des travailleurs (`WorkGoal.deliver` : l'atelier s'il y a
des livreurs, sinon la réserve qui revient), la navette des livreurs (`CourierGoal.shuttle`,
`citizen/Needs`), le charpentier (établi et modèle, planches de chaque essence, ouvrages de bois sur
commande des bâtisseurs), cisailles et blocs de fer (forgeron), fioles (verrier), flèches (archer),
citrouilles sculptées (inventeur), schémas de l'entrepôt et de la charpenterie.

Reste : la canne à sucre et les citrouilles aux champs du fermier (le sucre et le papier).

## Plan (v1.4)

1. **Entrepôt** : un bloc du mod, le *registre d'entrepôt*, fait d'un bâtiment déclaré l'entrepôt de
   la ville ; tous ses coffres et tonneaux forment la réserve commune.
   - La nourriture va au grenier ; le reste (outils, matériaux, produits) va à l'entrepôt s'il y en a
     un, au grenier sinon.
   - Tout le monde cherche ce qu'il lui faut dans : les coffres de son atelier, l'entrepôt, le grenier.
   - **Sans livreur**, chacun va lui-même à l'entrepôt déposer et prendre.
   - **Avec des livreurs**, les travailleurs déposent leur production dans le coffre de leur atelier,
     et les livreurs font la navette : ils vident les coffres des ateliers vers l'entrepôt (ou le
     grenier pour la nourriture), et y déposent à l'avance ce que chaque métier consomme (ses
     matières, son outil s'il en manque), en plus des matériaux qu'ils portent aux bâtisseurs.
2. **Charpentier** (établi de charpentier, bloc du mod) : planches, bâtons, portes, barrières,
   escaliers, trappes, dalles et bols, d'après ce que réclament les chantiers et ce qui manque.
3. **Le reste des manques** : le forgeron fait aussi cisailles et blocs de fer ; le verrier, des
   fioles ; l'archer taille ses flèches (silex, bâtons, plumes) ; le fermier plante aussi canne à sucre
   et citrouilles ; le cuisinier sculpte les citrouilles et fait le sucre.
