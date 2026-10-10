"""Assemble le site GitHub Pages : l'accueil (site/index.html) et les pages Releases, Changelog et
Installation, en français et en anglais (sélecteur de langue comme sur l'accueil).

Usage : python3 site/build.py <dossier de sortie> [releases.json]

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

NAV = [("index.html", "Accueil", "Home"), ("releases.html", "Versions", "Releases"),
       ("changelog.html", "Changelog", "Changelog"), ("installation.html", "Installation", "Installation")]


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


def page(head, footer, tail, current, title_fr, title_en, body_fr, body_en):
    lang_buttons = ('<div class="lang"><button data-set-lang="fr" aria-pressed="true">FR</button> '
                    '<button data-set-lang="en" aria-pressed="false">EN</button></div>')
    head = re.sub(r"<title>.*?</title>", f"<title>MineCities — {html.escape(title_en)}</title>", head, count=1)
    style = NAV_STYLE
    return (head.replace("</head>", style + "</head>") +
            f"<main>\n<header><div><h1 data-lang=\"fr\">{html.escape(title_fr)}</h1>"
            f"<h1 data-lang=\"en\">{html.escape(title_en)}</h1>{nav(current)}</div>{lang_buttons}</header>\n"
            f"<section data-lang=\"fr\">{body_fr}</section>\n<section data-lang=\"en\">{body_en}</section>\n"
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
        jars = [a for a in r.get("assets", []) if a.get("name", "").endswith(".jar")]
        link = (f'<a class="button" href="{html.escape(jars[0]["browser_download_url"])}">'
                + ("Télécharger" if fr else "Download") + f" {html.escape(jars[0]['name'])}</a>") if jars \
            else f'<a href="{html.escape(r.get("html_url", REPO))}">GitHub</a>'
        details = ("Minecraft 26.2 · Fabric Loader ≥ 0.19.3 · Fabric API · Java 25")
        out.append(f'<div class="release"><h2>{tag} {badge}</h2><p>{date} · {details}</p><p>{link}</p></div>')
    return "\n".join(out)


INSTALL_FR = """
<ol>
<li>Installez <strong>Java 25</strong> ou plus récent (le lanceur officiel le fournit).</li>
<li>Installez <a href="https://fabricmc.net/use/installer/">Fabric Loader</a> (0.19.3 ou plus) pour <strong>Minecraft 26.2</strong>.</li>
<li>Téléchargez <a href="https://modrinth.com/mod/fabric-api">Fabric API</a> pour 26.2 et le jar du mod depuis la page <a href="releases.html">Versions</a>.</li>
<li>Placez les deux jars dans le dossier <code>mods</code> de votre installation, puis lancez le profil Fabric.</li>
</ol>
<h2>Problèmes fréquents</h2>
<ul>
<li><strong>Le jeu refuse de démarrer, « Java 25 requis »</strong> : le profil utilise un Java trop ancien ; choisissez Java 25 dans les réglages du profil.</li>
<li><strong>« Fabric API manquant »</strong> : le mod en dépend ; ajoutez-le au dossier <code>mods</code>.</li>
<li><strong>Mauvaise version de Minecraft</strong> : le mod cible 26.2 uniquement.</li>
<li><strong>Serveur</strong> : le même jar s'installe sur le serveur ; chaque joueur doit aussi l'avoir.</li>
</ul>
"""
INSTALL_EN = """
<ol>
<li>Install <strong>Java 25</strong> or newer (the official launcher ships it).</li>
<li>Install <a href="https://fabricmc.net/use/installer/">Fabric Loader</a> (0.19.3 or newer) for <strong>Minecraft 26.2</strong>.</li>
<li>Download <a href="https://modrinth.com/mod/fabric-api">Fabric API</a> for 26.2 and the mod jar from the <a href="releases.html">Releases</a> page.</li>
<li>Put both jars in your installation's <code>mods</code> folder, then launch the Fabric profile.</li>
</ol>
<h2>Common problems</h2>
<ul>
<li><strong>The game won't start, "Java 25 required"</strong>: the profile uses an old Java; pick Java 25 in the profile settings.</li>
<li><strong>"Missing Fabric API"</strong>: the mod depends on it; add it to the <code>mods</code> folder.</li>
<li><strong>Wrong Minecraft version</strong>: the mod targets 26.2 only.</li>
<li><strong>Server</strong>: the same jar goes on the server; every player needs it too.</li>
</ul>
"""


def main(out, releases_json=None):
    if os.path.exists(out):
        shutil.rmtree(out)
    shutil.copytree(SITE, out, ignore=shutil.ignore_patterns("build.py", "__pycache__"))
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
    print(f"site assemblé dans {out} ({len(data)} release(s))")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
