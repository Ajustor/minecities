// Crée, une fois, la base « Signalements MineCities » dans Notion, avec les propriétés qu'attend le
// relais (worker.js), sous une page à laquelle l'intégration a accès.
//
//   NOTION_TOKEN=ntn_… node setup.mjs <identifiant ou lien de la page parente>
//
// Affiche l'identifiant de la base, à donner au relais (secret NOTION_DATABASE).

const token = process.env.NOTION_TOKEN;
const parent = (process.argv[2] || "").match(/[0-9a-f]{32}|[0-9a-f-]{36}/i)?.[0];
if (!token || !parent) {
  console.error("Usage : NOTION_TOKEN=… node setup.mjs <page parente>");
  process.exit(1);
}

const options = names => ({ select: { options: names.map(name => ({ name })) } });
const text = { rich_text: {} };
const number = format => ({ number: { format } });
const files = { files: {} };

const res = await fetch("https://api.notion.com/v1/databases", {
  method: "POST",
  headers: { "Authorization": `Bearer ${token}`, "Notion-Version": "2022-06-28", "Content-Type": "application/json" },
  body: JSON.stringify({
    parent: { type: "page_id", page_id: parent },
    title: [{ type: "text", text: { content: "Signalements MineCities" } }],
    icon: { type: "emoji", emoji: "🐞" },
    properties: {
      "Titre": { title: {} },
      "Type": options(["Bug", "Plantage", "Idée", "Avis"]),
      "Statut": options(["Nouveau", "Confirmé", "En cours", "Corrigé", "Refusé", "Doublon"]),
      "Version du mod": options([]),
      "Minecraft": text,
      "Fabric Loader": text,
      "Fabric API": text,
      "Mode": options(["Solo", "LAN", "Serveur"]),
      "Fréquence": options(["À chaque fois", "Parfois", "Une seule fois"]),
      "Gravité": options(["Bloquant", "Gênant", "Mineur", "Cosmétique"]),
      "Temps de jeu (h)": number("number"),
      "Jour en jeu": number("number"),
      "Palier": options(["Village", "Ville", "Grande ville", "Mégalopole"]),
      "Habitants": number("number"),
      "Note": number("number"),
      "Captures": files,
      "Journaux": files,
      "Contact": text,
      "Navigateur": text,
      "Reçu le": { created_time: {} },
    },
  }),
});
const json = await res.json();
if (!res.ok) {
  console.error("Notion a refusé :", res.status, json.message || json);
  process.exit(1);
}
console.log("Base créée :", json.url);
console.log("NOTION_DATABASE =", json.id);
