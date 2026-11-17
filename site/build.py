"""Assemble le site GitHub Pages : l'accueil (site/index.html) et les pages Releases, Changelog et
Installation, en français et en anglais (sélecteur de langue comme sur l'accueil).

Usage : python3 site/build.py <dossier de sortie> [releases.json]

La page Bâtiments (buildings.html) montre les captures de site/img/buildings (faites en jeu par la
scène gallery, voir tools/captures/gallery.py), avec ce que disent les schémas et les styles du mod.

La page Signaler (report.html) vient de site/report.body.html ; elle envoie le formulaire au relais
dont l'adresse est dans REPORT_ENDPOINT (une variable du dépôt, voir tools/report-worker), et
TURNSTILE_SITEKEY, si elle est donnée, ajoute la vérification anti-robots de Cloudflare.

releases.json est la réponse de l'API GitHub (liste des releases) ; les changelogs viennent de
CHANGELOG.fr.md et CHANGELOG.md, recommités par le workflow de release. Sans eux, les pages le disent.
Bibliothèque standard uniquement.
"""
import html
import json
import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SITE = os.path.join(ROOT, "site")
REPO = "https://github.com/Ajustor/minecities"

NAV = [("index.html", "Accueil", "Home"), ("buildings.html", "Bâtiments", "Buildings"),
       ("releases.html", "Versions", "Releases"),
       ("changelog.html", "Changelog", "Changelog"), ("installation.html", "Installation", "Installation"),
       ("report.html", "Signaler un bug", "Report a bug")]


NAV_STYLE = ("<style>.site-nav{margin:12px 0 0;font-size:.95rem}.site-nav a{color:var(--accent)}"
             ".site-nav a[aria-current]{font-weight:bold;text-decoration:none;color:var(--ink)}"
             "html[lang=fr] .site-nav [data-lang=en],html[lang=en] .site-nav [data-lang=fr]{display:none!important}"
             ".release{border-bottom:1px solid var(--muted);padding:8px 0 16px}.badge{font-size:.8rem;"
             "padding:1px 8px;border-radius:10px;background:var(--accent);color:#fff}.badge.pre{background:var(--muted)}"
             "</style>")


def template():
    """La tête et le pied de l'accueil : même style, même script de langue."""
    page = open(os.path.join(SITE, "index.html"), encoding="utf-8").read()
    head = page[:page.index("<main>")]
    tail = page[page.index("<script>"):]
    footer = page[page.index("<footer>"):page.index("</footer>") + len("</footer>")]
    return head, footer, tail


def nav(current):
    links = []
    for href, fr, en in NAV:
        mark = ' aria-current="page"' if href == current else ""
        links.append(f'<a href="{href}"{mark}><span data-lang="fr" style="display:inline">{fr}</span>'
                     f'<span data-lang="en" style="display:inline">{en}</span></a>')
    return '<nav class="site-nav">' + " · ".join(links) + "</nav>"


def page(head, footer, tail, current, title_fr, title_en, body_fr, body_en, shared=""):
    lang_buttons = ('<div class="lang"><button data-set-lang="fr" aria-pressed="true">FR</button> '
                    '<button data-set-lang="en" aria-pressed="false">EN</button></div>')
    head = re.sub(r"<title>.*?</title>", f"<title>MineCities — {html.escape(title_en)}</title>", head, count=1)
    style = NAV_STYLE
    return (head.replace("</head>", style + "</head>") +
            f"<main>\n<header><div><h1 data-lang=\"fr\">{html.escape(title_fr)}</h1>"
            f"<h1 data-lang=\"en\">{html.escape(title_en)}</h1>{nav(current)}</div>{lang_buttons}</header>\n"
            f"<section data-lang=\"fr\">{body_fr}</section>\n<section data-lang=\"en\">{body_en}</section>\n"
            f"{shared}\n"
            f"{footer}\n</main>\n{tail}")


def inline(text):
    text = html.escape(text)
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    return re.sub(r"\[([^\]]+)\]\((https?://[^)]+)\)", r'<a href="\2">\1</a>', text)


def markdown(md):
    """Le peu de Markdown que produit git-cliff : titres, listes, paragraphes."""
    out, in_list = [], False
    for line in md.splitlines():
        if line.startswith("- "):
            if not in_list:
                out.append("<ul>")
                in_list = True
            out.append(f"<li>{inline(line[2:])}</li>")
            continue
        if in_list:
            out.append("</ul>")
            in_list = False
        m = re.match(r"^(#{1,4}) (.*)", line)
        if m:
            level = min(4, len(m.group(1)) + 1)
            out.append(f"<h{level}>{inline(m.group(2))}</h{level}>")
        elif line.strip():
            out.append(f"<p>{inline(line)}</p>")
    if in_list:
        out.append("</ul>")
    return "\n".join(out)


def changelog(name, missing):
    path = os.path.join(ROOT, name)
    if not os.path.exists(path):
        return f"<p>{missing}</p>"
    md = open(path, encoding="utf-8").read()
    # Le titre du fichier fait doublon avec celui de la page.
    md = re.sub(r"^# .*\n", "", md, count=1)
    return markdown(md)


def minecraft_of(jar):
    """La version de Minecraft d'un jar du mod : ce qui suit le « + » de son nom."""
    return jar.rsplit("+", 1)[-1].removesuffix(".jar") if "+" in jar else ""


def releases(data, fr):
    if not data:
        return "<p>" + ("Aucune version publiée pour l'instant." if fr else "No release published yet.") + "</p>"
    out = []
    for r in data:
        if r.get("draft"):
            continue
        tag = html.escape(r.get("tag_name", ""))
        date = (r.get("published_at") or "")[:10]
        pre = r.get("prerelease")
        badge = ('<span class="badge pre">' + ("préversion" if fr else "pre-release") + "</span>") if pre \
            else ('<span class="badge">' + ("stable" if fr else "stable") + "</span>")
        # Un jar par version de Minecraft (minecities-1.10.0+26.3.jar).
        jars = sorted((a for a in r.get("assets", []) if a.get("name", "").endswith(".jar")
                       and not a["name"].endswith("-sources.jar")), key=lambda a: minecraft_of(a["name"]))
        link = " ".join(f'<a class="button" href="{html.escape(a["browser_download_url"])}">'
                        + ("Télécharger" if fr else "Download") + f" {html.escape(a['name'])}</a>" for a in jars) \
            or f'<a href="{html.escape(r.get("html_url", REPO))}">GitHub</a>'
        versions = ", ".join(minecraft_of(a["name"]) for a in jars) or "26.2"
        details = f"Minecraft {versions} · Fabric Loader ≥ 0.19.3 · Fabric API · Java 25"
        out.append(f'<div class="release"><h2>{tag} {badge}</h2><p>{date} · {details}</p><p>{link}</p></div>')
    return "\n".join(out)


INSTALL_FR = """
<ol>
<li>Installez <strong>Java 25</strong> ou plus récent (le lanceur officiel le fournit).</li>
<li>Installez <a href="https://fabricmc.net/use/installer/">Fabric Loader</a> (0.19.3 ou plus) pour <strong>Minecraft 26.2 ou 26.3</strong>.</li>
<li>Téléchargez <a href="https://modrinth.com/mod/fabric-api">Fabric API</a> pour votre version de Minecraft, et le jar du mod de la même version (<code>minecities-…+26.3.jar</code> pour 26.3) depuis la page <a href="releases.html">Versions</a>.</li>
<li>Placez les deux jars dans le dossier <code>mods</code> de votre installation, puis lancez le profil Fabric.</li>
</ol>
<h2>Problèmes fréquents</h2>
<ul>
<li><strong>Le jeu refuse de démarrer, « Java 25 requis »</strong> : le profil utilise un Java trop ancien ; choisissez Java 25 dans les réglages du profil.</li>
<li><strong>« Fabric API manquant »</strong> : le mod en dépend ; ajoutez-le au dossier <code>mods</code>.</li>
<li><strong>Mauvaise version de Minecraft</strong> : chaque jar du mod ne marche qu'avec la version de Minecraft écrite après le « + » de son nom.</li>
<li><strong>Serveur</strong> : le même jar s'installe sur le serveur ; chaque joueur doit aussi l'avoir.</li>
</ul>
"""
INSTALL_EN = """
<ol>
<li>Install <strong>Java 25</strong> or newer (the official launcher ships it).</li>
<li>Install <a href="https://fabricmc.net/use/installer/">Fabric Loader</a> (0.19.3 or newer) for <strong>Minecraft 26.2 or 26.3</strong>.</li>
<li>Download <a href="https://modrinth.com/mod/fabric-api">Fabric API</a> for your Minecraft version, and the mod jar for that same version (<code>minecities-…+26.3.jar</code> for 26.3) from the <a href="releases.html">Releases</a> page.</li>
<li>Put both jars in your installation's <code>mods</code> folder, then launch the Fabric profile.</li>
</ol>
<h2>Common problems</h2>
<ul>
<li><strong>The game won't start, "Java 25 required"</strong>: the profile uses an old Java; pick Java 25 in the profile settings.</li>
<li><strong>"Missing Fabric API"</strong>: the mod depends on it; add it to the <code>mods</code> folder.</li>
<li><strong>Wrong Minecraft version</strong>: each mod jar only works with the Minecraft version written after the "+" in its name.</li>
<li><strong>Server</strong>: the same jar goes on the server; every player needs it too.</li>
</ul>
"""


BLUEPRINTS = os.path.join(ROOT, "src", "main", "resources", "data", "minecities", "blueprint")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "minecities", "lang")
STYLE_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "ajustor", "minecities", "build", "BuildStyle.java")
GALLERY_JAVA = os.path.join(ROOT, "src", "gametest", "java", "com", "ajustor", "minecities", "gametest",
                            "GalleryScenes.java")

# Ce qu'on montre de chaque style : les jetons qui font l'allure d'un bâtiment.
PALETTE = [("wall", "Murs", "Walls"), ("frame", "Charpente", "Frame"), ("roof", "Toit", "Roof"),
           ("floor", "Plancher", "Floor"), ("foundation", "Fondation", "Foundation"),
           ("masonry", "Maçonnerie (niveau 3)", "Masonry (level 3)"),
           ("fine_roof", "Toit (niveau 4)", "Roof (level 4)")]

# Les noms français des blocs des styles (le site n'a pas les fichiers de langue du jeu).
BLOCKS_FR = {
    "oak_planks": "planches de chêne", "spruce_planks": "planches d'épicéa", "birch_planks": "planches de bouleau",
    "acacia_planks": "planches d'acacia", "dark_oak_planks": "planches de chêne noir",
    "cherry_planks": "planches de cerisier", "mangrove_planks": "planches de palétuvier",
    "oak_log": "bûche de chêne", "spruce_log": "bûche d'épicéa", "acacia_log": "bûche d'acacia",
    "dark_oak_log": "bûche de chêne noir", "cherry_log": "bûche de cerisier", "mangrove_log": "bûche de palétuvier",
    "stripped_spruce_log": "bûche d'épicéa écorcée", "cobblestone": "pierres", "stone_bricks": "pierres taillées",
    "mossy_stone_bricks": "pierres taillées moussues", "bricks": "briques", "sandstone": "grès",
    "smooth_sandstone": "grès lisse", "cut_sandstone": "grès taillé", "chiseled_sandstone": "grès sculpté",
    "red_sandstone": "grès rouge", "cut_red_sandstone": "grès rouge taillé",
    "chiseled_red_sandstone": "grès rouge sculpté", "orange_terracotta": "terre cuite orange",
    "terracotta": "terre cuite", "white_terracotta": "terre cuite blanche", "red_terracotta": "terre cuite rouge",
    "polished_granite": "granite poli", "polished_diorite": "diorite polie", "polished_andesite": "andésite polie",
    "polished_deepslate": "ardoise polie", "deepslate_tiles": "tuiles d'ardoise", "deepslate_bricks": "briques d'ardoise",
    "snow_block": "bloc de neige", "white_concrete": "béton blanc", "waxed_cut_copper": "cuivre taillé",
    "waxed_oxidized_cut_copper": "cuivre taillé oxydé", "waxed_weathered_cut_copper": "cuivre taillé patiné", "packed_mud": "boue compacte", "mud_bricks": "briques de boue",
}

LEVELS_FR = [("Cabane", "rondins bruts, terre battue, toit plat"),
             ("Maison", "planches, fondation de pierre, vitres, toit à deux pans"),
             ("Bâtisse", "maçonnerie du style, barreaux de fer, lanternes ; dès la ville"),
             ("Demeure", "pierre polie, colonnes de quartz, vitraux, toit noble du style ; dès la grande ville")]
LEVELS_EN = [("Hut", "raw logs, packed dirt, flat roof"),
             ("House", "planks, stone foundation, glass, gable roof"),
             ("Hall", "the style's masonry, iron bars, lanterns; from town tier"),
             ("Mansion", "polished stone, quartz columns, stained glass, the style's fine roof; from city tier")]


def styles():
    """Les styles du mod, lus dans BuildStyle.java : (nom, description, {jeton: bloc})."""
    source = open(STYLE_JAVA, encoding="utf-8").read()
    out = []
    pattern = r'(?:/\*\* (.*?) \*/\s*)?[A-Z]+\("([a-z_]+)", Map\.ofEntries\((.*?)\)\)[,;]'
    for doc, name, body in re.findall(pattern, source, re.S):
        tokens = dict(re.findall(r'Map\.entry\("([a-z_]+)", "minecraft:([a-z_]+)"\)', body))
        out.append((name, doc, tokens))
    return out


def lang(code):
    return json.load(open(os.path.join(LANG, code + ".json"), encoding="utf-8"))


def blocks(name):
    """Le nombre de blocs que pose le bâtisseur pour ce schéma (ni l'air ni « laisser tel quel »)."""
    data = json.load(open(os.path.join(BLUEPRINTS, name + ".json"), encoding="utf-8"))
    key = data["key"]
    count = 0
    for layer in data["layers"]:
        for row in layer:
            for ch in row:
                block = key.get(ch, "minecraft:air")
                # La moitié haute d'une porte, la tête d'un lit : posées avec l'autre moitié.
                if ch != "." and block != "minecraft:air" and "half=upper" not in block and "part=head" not in block:
                    count += 1
    return count


def families():
    source = open(GALLERY_JAVA, encoding="utf-8").read()
    block = source[source.index("FAMILIES = List.of("):]
    block = block[:block.index(");")]
    return re.findall(r'"([a-z_]+)"', block)


def buildings(fr):
    """La page Bâtiments : les styles, les quatre niveaux, chaque bâtiment, les monuments."""
    names = lang("fr_fr" if fr else "en_us")
    img = "img/buildings/"
    style_names = {n: names.get("style.minecities." + n, n) for n, _, _ in styles()}
    out = []
    if fr:
        out.append("<p>Le bâtisseur construit d'après des schémas, à quatre niveaux, et dans le style de votre "
                   "choix (choisi sur le plan, ou dans l'onglet Plans de l'hôtel de ville). Chaque image montre "
                   "un bâtiment à ses quatre niveaux, <strong>de gauche à droite : du niveau 1 au niveau 4</strong>. "
                   "Ce sont des captures du jeu.</p>")
        out.append(f"<h2>Les {len(style_names)} styles</h2>")
        out.append("<p>Un même schéma change de matériaux selon le style : les quatre premiers sont ceux des "
                   "villages du jeu, les autres s'inspirent des constructions des joueurs (village enneigé, maison "
                   "japonaise, cabane du marais, adobe des badlands, maison à colombages). Ici, la maison.</p>")
    else:
        out.append("<p>The builder builds from blueprints, at four levels, in the style you pick (on the plan, or "
                   "in the town hall's Plans tab). Each picture shows a building at its four levels, "
                   "<strong>left to right: level 1 to level 4</strong>. These are in-game screenshots.</p>")
        out.append(f"<h2>The {len(style_names)} styles</h2>")
        out.append("<p>The same blueprint changes materials with the style: the first four are the game's own "
                   "village styles, the others draw on player builds (snowy village, Japanese house, swamp hut, "
                   "badlands adobe, half-timbered house). Here, the house.</p>")
    out.append('<div class="gallery">')
    for name, doc, tokens in styles():
        title = style_names[name]
        title = title[:1].upper() + title[1:]
        rows = []
        for token, lfr, len_ in PALETTE:
            block = tokens.get(token)
            if not block:
                continue
            label = BLOCKS_FR.get(block, block.replace("_", " ")) if fr else block.replace("_", " ")
            rows.append(f"<dt>{html.escape(lfr if fr else len_)}</dt><dd>{html.escape(label)}</dd>")
        out.append(f'<figure id="style-{name}"><a href="{img}style_{name}.jpg"><img loading="lazy" '
                   f'src="{img}style_{name}.jpg" alt="{html.escape(title)}"></a><figcaption><strong>{html.escape(title)}</strong>'
                   f'<dl class="palette">{"".join(rows)}</dl></figcaption></figure>')
    out.append("</div>")

    levels = LEVELS_FR if fr else LEVELS_EN
    out.append("<h2>Les quatre niveaux</h2>" if fr else "<h2>The four levels</h2>")
    out.append("<p>Un bâtiment s'agrandit d'un niveau à l'autre, sur place : une maison gagne deux lits, un "
               "atelier un poste de travail (et un travailleur). Chaque niveau coûte plus de matériaux.</p>"
               if fr else
               "<p>A building grows from one level to the next, in place: a house gains two beds, a workshop "
               "one more workstation (and worker). Each level costs more materials.</p>")
    out.append("<ol>" + "".join(f"<li><strong>{html.escape(t)}</strong> : {html.escape(d)}</li>" if fr else
                                f"<li><strong>{html.escape(t)}</strong>: {html.escape(d)}</li>"
                                for t, d in levels) + "</ol>")

    out.append("<h2>Les bâtiments</h2>" if fr else "<h2>The buildings</h2>")
    out.append("<p>Chaque bâtiment a sa signature, qui se reconnaît de loin et s'étoffe avec le niveau : le "
               "couloir de tir et la tour de guet de l'archer, le chevalement de la mine, le quai et le grenier "
               "de l'entrepôt, la halle du marché, l'étage en encorbellement de la taverne… Ici en style plaines. "
               "Le nombre de blocs est celui que pose le bâtisseur, du niveau 1 au niveau 4.</p>" if fr else
               "<p>Every building has its own signature, recognisable from afar and richer with each level: the "
               "archer's shooting lane and watchtower, the mine's headframe, the warehouse's loading dock and "
               "loft, the market hall, the tavern's jettied upper floor… Shown in the plains style. The block "
               "counts are what the builder places, level 1 to 4.</p>")
    out.append('<div class="gallery">')
    for family in ["house"] + families():
        label = names.get(f"blueprint.minecities.{family}_1", family)
        label = re.split(r",\s*(?:niveau|level)", label)[0]
        counts = " · ".join(str(blocks(f"{family}_{n}")) for n in range(1, 5))
        src = f"{img}style_plains.jpg" if family == "house" else f"{img}building_{family}.jpg"
        unit = "blocs" if fr else "blocks"
        out.append(f'<figure id="{family}"><a href="{src}"><img loading="lazy" src="{src}" '
                   f'alt="{html.escape(label)}"></a>'
                   f'<figcaption><strong>{html.escape(label)}</strong><br>{counts} {unit}</figcaption></figure>')
    out.append("</div>")

    out.append("<h2>Les monuments</h2>" if fr else "<h2>Monuments</h2>")
    monuments = ", ".join(names.get(f"blueprint.minecities.{m}", m) for m in ("statue", "fountain", "belfry"))
    out.append(f"<p>{html.escape(monuments)} : un de chaque pour devenir mégalopole.</p>" if fr else
               f"<p>{html.escape(monuments)}: one of each to become a megalopolis.</p>")
    out.append(f'<figure class="wide"><img loading="lazy" src="{img}monuments.jpg" '
               f'alt="{html.escape(monuments)}"></figure>')
    return "\n".join(out)


GALLERY_STYLE = ("<style>.gallery{display:grid;grid-template-columns:1fr;gap:20px;margin:16px 0 28px}"
                 "@media(min-width:640px){.gallery{grid-template-columns:1fr 1fr}}"
                 ".gallery figure,figure.wide{margin:0;background:var(--card);border-radius:8px;overflow:hidden}"
                 ".gallery img,figure.wide img{width:100%;height:auto;display:block;aspect-ratio:16/9;"
                 "object-fit:cover;background:var(--muted)}"
                 ".gallery figcaption{padding:8px 12px 12px;font-size:.92rem;color:var(--muted)}"
                 ".gallery figcaption strong{color:var(--ink);font-size:1rem}"
                 ".palette{display:grid;grid-template-columns:auto 1fr;gap:2px 10px;margin:6px 0 0}"
                 ".palette dt{color:var(--muted)}.palette dd{margin:0;color:var(--ink)}</style>")


def report(data):
    """Le formulaire de signalement : les deux langues dans un seul formulaire, les versions publiées
    dans le sélecteur (la plus récente d'abord, choisie par défaut)."""
    body = open(os.path.join(SITE, "report.body.html"), encoding="utf-8").read()
    versions = []
    for r in data:
        if r.get("draft"):
            continue
        tag = r.get("tag_name", "").lstrip("v")
        if tag:
            versions.append(f'          <option value="{html.escape(tag)}">{html.escape(tag)}</option>')
    sitekey = os.environ.get("TURNSTILE_SITEKEY", "").strip()
    turnstile = (f'<div class="cf-turnstile" data-sitekey="{html.escape(sitekey)}" style="margin-top:12px"></div>'
                 '<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>'
                 if sitekey else "")
    return (body.replace("{{VERSIONS}}", "\n".join(versions))
            .replace("{{REPORT_ENDPOINT}}", os.environ.get("REPORT_ENDPOINT", "").strip())
            .replace("{{TURNSTILE}}", turnstile))


def main(out, releases_json=None):
    if os.path.exists(out):
        shutil.rmtree(out)
    shutil.copytree(SITE, out, ignore=shutil.ignore_patterns("build.py", "*.body.html", "__pycache__"))
    head, footer, tail = template()
    # L'accueil reçoit le même menu que les autres pages.
    index = os.path.join(out, "index.html")
    home = open(index, encoding="utf-8").read()
    anchor = '    </div>\n    <div class="lang">'
    if anchor in home:
        home = home.replace(anchor, "      " + nav("index.html") + "\n" + anchor, 1)
        home = home.replace("</head>", NAV_STYLE + "</head>", 1)
        open(index, "w", encoding="utf-8", newline="\n").write(home)
    data = []
    if releases_json and os.path.exists(releases_json):
        data = json.load(open(releases_json, encoding="utf-8"))
    pages = {
        "releases.html": ("Versions", "Releases", releases(data, True), releases(data, False)),
        "changelog.html": ("Journal des modifications", "Changelog",
                           changelog("CHANGELOG.fr.md", "Le journal apparaîtra avec la première version publiée."),
                           changelog("CHANGELOG.md", "The changelog will appear with the first published release.")),
        "installation.html": ("Installation", "Installation", INSTALL_FR, INSTALL_EN),
    }
    for name, (tfr, ten, bfr, ben) in pages.items():
        with open(os.path.join(out, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(page(head, footer, tail, name, tfr, ten, bfr, ben))
    with open(os.path.join(out, "buildings.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write(page(head.replace("</head>", GALLERY_STYLE + "</head>", 1), footer, tail, "buildings.html",
                     "Bâtiments et styles", "Buildings and styles", buildings(True), buildings(False)))
    with open(os.path.join(out, "report.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write(page(head, footer, tail, "report.html", "Signaler un bug", "Report a bug", "", "", report(data)))
    print(f"site assemblé dans {out} ({len(data)} release(s))")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
