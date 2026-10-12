# Le relais des signalements vers Notion

Le formulaire du site (`report.html`, généré depuis `site/report.body.html`) envoie chaque
signalement à ce petit relais, un Cloudflare Worker (gratuit). Le site est statique et ne peut pas
garder la clé de Notion ; le relais la garde. Il envoie les captures et les journaux à Notion, puis
crée une fiche dans la base des signalements.

Tant que le relais n'est pas branché, le formulaire propose d'ouvrir une issue GitHub préremplie.

## Installation (une fois)

1. **L'intégration Notion** : sur <https://www.notion.so/profile/integrations>, créez une intégration
   interne (« MineCities signalements ») avec les droits *Lire*, *Mettre à jour* et *Insérer du
   contenu*. Copiez sa clé (`ntn_…`).
2. **La base** : dans Notion, créez une page (par exemple « MineCities »), puis, dans son menu `•••`,
   *Connexions* → ajoutez l'intégration. Créez ensuite la base sous cette page :

   ```sh
   cd tools/report-worker
   NOTION_TOKEN=ntn_… node setup.mjs https://www.notion.so/MineCities-0123456789abcdef0123456789abcdef
   ```

   Le script affiche l'identifiant de la base (`NOTION_DATABASE`).
3. **Le relais** : avec un compte Cloudflare (gratuit) :

   ```sh
   npx wrangler login
   npx wrangler secret put NOTION_TOKEN       # la clé ntn_…
   npx wrangler secret put NOTION_DATABASE    # l'identifiant affiché par setup.mjs
   npx wrangler deploy
   ```

   Wrangler affiche l'adresse du relais, par exemple `https://minecities-report.<compte>.workers.dev`.
4. **Le site** : dans le dépôt GitHub, *Settings* → *Secrets and variables* → *Actions* →
   *Variables*, ajoutez `REPORT_ENDPOINT` avec cette adresse, puis relancez le workflow `pages`.

## Contre les robots (facultatif)

Le relais refuse déjà les robots qui remplissent le champ piège, les envois venus d'un autre site,
et plus d'un signalement par minute depuis une même adresse. Pour aller plus loin, créez un widget
[Turnstile](https://dash.cloudflare.com/?to=/:account/turnstile) pour `ajustor.github.io`, puis
`npx wrangler secret put TURNSTILE_SECRET` (la clé secrète) et la variable de dépôt
`TURNSTILE_SITEKEY` (la clé publique).

## Ce que contient une fiche

Titre, type (bug, plantage, idée, avis), statut (« Nouveau » à l'arrivée), version du mod, de
Minecraft, de Fabric Loader et de Fabric API, mode de jeu, fréquence, gravité, temps de jeu, jour en
jeu, taille de la ville, note sur 5, captures, journaux, contact, navigateur. Le corps de la fiche
contient la description, les étapes, l'attendu et l'observé, l'avis et la liste des autres mods, puis
les captures. En jeu, `/ville bug` remplit d'avance les versions, les mods, le temps de jeu, le jour,
le mode et la ville.

Les fichiers : 6 au plus, de 5 Mo chacun (la limite d'un espace Notion gratuit). Les journaux
(`.log`) sont renommés en `.txt` pour que Notion les accepte.
