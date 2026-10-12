// Le relais du formulaire de signalement (site/report.body.html) vers Notion : le site est statique
// et ne peut pas garder la clé de Notion, ce Cloudflare Worker la garde pour lui. Il reçoit le
// formulaire, envoie les captures et journaux à Notion, et crée une fiche dans la base des
// signalements. Voir README.md pour l'installer.
//
// Secrets : NOTION_TOKEN (la clé de l'intégration), NOTION_DATABASE (l'identifiant de la base),
// TURNSTILE_SECRET (facultatif : la vérification anti-robots). Variable : ALLOWED_ORIGIN.

const NOTION = "https://api.notion.com/v1";
const NOTION_VERSION = "2022-06-28";
const MAX_FILES = 6;
const MAX_SIZE = 5 * 1024 * 1024;
/** Un même visiteur : un signalement par minute au plus (par instance du relais). */
const SPACING_MS = 60_000;
const recent = new Map();

const KINDS = { bug: "Bug", crash: "Plantage", idea: "Idée", feedback: "Avis" };
const ENVS = { solo: "Solo", lan: "LAN", server: "Serveur" };
const FREQUENCIES = { always: "À chaque fois", sometimes: "Parfois", once: "Une seule fois" };
const SEVERITIES = { blocking: "Bloquant", major: "Gênant", minor: "Mineur", cosmetic: "Cosmétique" };
const TIERS = { village: "Village", town: "Ville", city: "Grande ville", megalopolis: "Mégalopole" };

export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": env.ALLOWED_ORIGIN || "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Vary": "Origin",
    };
    const reply = (status, body) => new Response(JSON.stringify(body), {
      status, headers: { ...cors, "Content-Type": "application/json" },
    });
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (request.method !== "POST") return reply(405, { error: "POST only" });
    const origin = request.headers.get("Origin");
    if (env.ALLOWED_ORIGIN && origin && origin !== env.ALLOWED_ORIGIN) return reply(403, { error: "origin" });

    let form;
    try {
      form = await request.formData();
    } catch {
      return reply(400, { error: "form" });
    }
    // Le piège à robots : un champ caché que seul un robot remplit. On fait mine d'accepter.
    if (text(form, "website")) return reply(200, { ok: true });

    const ip = request.headers.get("CF-Connecting-IP") || "";
    const now = Date.now();
    if (ip && now - (recent.get(ip) || 0) < SPACING_MS) return reply(429, { error: "too many reports, wait a minute" });

    if (env.TURNSTILE_SECRET) {
      const check = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
        method: "POST",
        body: new URLSearchParams({ secret: env.TURNSTILE_SECRET, response: text(form, "cf-turnstile-response"), remoteip: ip }),
      }).then(r => r.json()).catch(() => ({}));
      if (!check.success) return reply(403, { error: "captcha" });
    }

    const report = read(form);
    if (!report.title || !report.description) return reply(400, { error: "title and description are required" });
    const files = form.getAll("files").filter(f => typeof f === "object" && f.size > 0);
    if (files.length > MAX_FILES) return reply(400, { error: "too many files" });
    if (files.some(f => f.size > MAX_SIZE)) return reply(400, { error: "file over 5 MB" });

    try {
      const notion = client(env.NOTION_TOKEN);
      const uploads = [];
      for (const file of files) uploads.push(await upload(notion, file));
      const page = await notion("POST", "/pages", {
        parent: { database_id: env.NOTION_DATABASE },
        properties: properties(report, uploads),
        children: blocks(report),
      });
      // Les captures, visibles dans la fiche : ajoutées une fois la fiche créée, car un fichier ne
      // se réutilise qu'après avoir été attaché (ici, à la propriété Captures).
      const images = uploads.filter(u => u.image).map(u =>
        ({ object: "block", type: "image", image: { type: "file_upload", file_upload: { id: u.id } } }));
      if (images.length) await notion("PATCH", `/blocks/${page.id}/children`, { children: images });
      if (ip) recent.set(ip, now);
      return reply(200, { ok: true, ref: page.id.slice(0, 8) });
    } catch (e) {
      console.error(e);
      return reply(502, { error: "notion" });
    }
  },
};

function text(form, name, max = 2000) {
  const value = form.get(name);
  return typeof value === "string" ? value.trim().slice(0, max) : "";
}

function number(form, name) {
  const value = parseFloat(text(form, name));
  return Number.isFinite(value) ? value : null;
}

function read(form) {
  return {
    kind: KINDS[text(form, "kind")] || "Bug",
    title: text(form, "title", 150),
    description: text(form, "description", 6000),
    steps: text(form, "steps", 4000),
    expected: text(form, "expected"),
    actual: text(form, "actual"),
    frequency: FREQUENCIES[text(form, "frequency")] || null,
    severity: SEVERITIES[text(form, "severity")] || null,
    version: text(form, "version", 60) || "unknown",
    mc: text(form, "mc", 40),
    loader: text(form, "loader", 40),
    api: text(form, "api", 60),
    env: ENVS[text(form, "env")] || null,
    playtime: number(form, "playtime"),
    day: number(form, "day"),
    tier: TIERS[text(form, "tier")] || null,
    population: number(form, "population"),
    mods: text(form, "mods", 4000),
    rating: number(form, "rating"),
    likes: text(form, "likes"),
    wishes: text(form, "wishes"),
    contact: text(form, "contact", 200),
    client: text(form, "client", 500),
    lang: text(form, "lang", 5),
  };
}

function client(token) {
  const call = async (method, path, body) => {
    const res = await fetch(NOTION + path, {
      method,
      headers: { "Authorization": `Bearer ${token}`, "Notion-Version": NOTION_VERSION, "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
    });
    const json = await res.json();
    if (!res.ok) throw new Error(`${method} ${path}: ${res.status} ${JSON.stringify(json)}`);
    return json;
  };
  call.token = token;
  return call;
}

/** Envoie un fichier à Notion (une capture, ou un journal, renommé en .txt pour être accepté). */
async function upload(notion, file) {
  const image = /^image\//.test(file.type);
  const name = image ? file.name : file.name.replace(/\.(log|gz)$/i, "") + ".txt";
  const type = image ? file.type : "text/plain";
  const created = await notion("POST", "/file_uploads", { filename: name, content_type: type });
  const body = new FormData();
  body.append("file", new Blob([await file.arrayBuffer()], { type }), name);
  const res = await fetch(created.upload_url || `${NOTION}/file_uploads/${created.id}/send`, {
    method: "POST",
    headers: { "Authorization": `Bearer ${notion.token}`, "Notion-Version": NOTION_VERSION },
    body,
  });
  if (!res.ok) throw new Error(`upload ${name}: ${res.status} ${await res.text()}`);
  return { id: created.id, name, image };
}

function select(value) {
  return value ? { select: { name: value } } : { select: null };
}

function rich(value) {
  return { rich_text: value ? chunks(value) : [] };
}

/** Notion coupe un texte à 2000 caractères : on le découpe. */
function chunks(value) {
  const out = [];
  for (let i = 0; i < value.length; i += 2000) out.push({ type: "text", text: { content: value.slice(i, i + 2000) } });
  return out;
}

function properties(r, uploads) {
  const files = list => list.map(u => ({ type: "file_upload", file_upload: { id: u.id }, name: u.name }));
  return {
    "Titre": { title: chunks(r.title) },
    "Type": select(r.kind),
    "Statut": select("Nouveau"),
    "Version du mod": select(r.version.replace(/,/g, " ")),
    "Minecraft": rich(r.mc),
    "Fabric Loader": rich(r.loader),
    "Fabric API": rich(r.api),
    "Mode": select(r.env),
    "Fréquence": select(r.frequency),
    "Gravité": select(r.severity),
    "Temps de jeu (h)": { number: r.playtime },
    "Jour en jeu": { number: r.day },
    "Palier": select(r.tier),
    "Habitants": { number: r.population },
    "Note": { number: r.rating },
    "Captures": { files: files(uploads.filter(u => u.image)) },
    "Journaux": { files: files(uploads.filter(u => !u.image)) },
    "Contact": rich(r.contact),
    "Navigateur": rich(r.client + (r.lang ? " · site " + r.lang : "")),
  };
}

function blocks(r) {
  const out = [];
  const section = (title, body, code) => {
    if (!body) return;
    out.push({ object: "block", type: "heading_3", heading_3: { rich_text: chunks(title) } });
    out.push(code
      ? { object: "block", type: "code", code: { language: "plain text", rich_text: chunks(body) } }
      : { object: "block", type: "paragraph", paragraph: { rich_text: chunks(body) } });
  };
  section("Description", r.description);
  section("Étapes pour reproduire", r.steps);
  section("Attendu", r.expected);
  section("Observé", r.actual);
  section("Ce qu'il aime", r.likes);
  section("Ce qui manque", r.wishes);
  section("Autres mods", r.mods, true);
  return out;
}
