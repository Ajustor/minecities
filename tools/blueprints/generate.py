"""Génère les schémas de bâtiments du bâtisseur (data/minecities/blueprint/*.json).

Usage : python tools/blueprints/generate.py

Un schéma est un empilement de couches (de bas en haut), chacune une liste de rangées du nord au
sud, chaque caractère un bloc d'ouest en est ; la clé dit quel bloc est chaque caractère. Les blocs
s'écrivent avec des jetons de style ({wall}, {roof}, {door}…) que le style choisi à la commande
remplace (plaines, taïga, désert, savane : voir BuildStyle.java). « . » laisse le bloc tel quel,
« espace » exige de l'air.

La porte est au nord, précédée d'un perron (une marche d'escalier en z = 0, car la fondation
surélève le plancher d'un bloc) ; la plaque est accrochée à l'intérieur du mur nord, et déclare le
bâtiment une fois fini. Modifier ce script puis le relancer plutôt que retoucher les JSON.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/data/minecities/blueprint"

KEY = {
    " ": "minecraft:air",
    "F": "{foundation}",
    "f": "{floor}",
    "W": "{wall}",
    "P": "{frame}[axis=y]",
    "R": "{roof}",
    "S": "{roof_slab}[type=bottom]",
    "G": "{window}",
    "D": "{door}[facing=north,half=lower,hinge=left]",
    "d": "{door}[facing=north,half=upper,hinge=left]",
    "b": "{bed}[facing=south,part=foot]",
    "B": "{bed}[facing=south,part=head]",
    "C": "minecraft:chest[facing=south]",
    "t": "minecraft:torch",
    "L": "minecities:house_plaque[facing=south]",
    "k": "minecraft:composter",
    "s": "minecraft:smoker[facing=north]",
    "l": "{stripped_log}[axis=y]",
    "h": "minecraft:hay_block[axis=y]",
    "r": "minecraft:barrel[facing=up]",
    "c": "minecraft:crafter",
    "T": "minecraft:farmland",
    "~": "minecraft:water",
    "E": "{fence}",
    "g": "{fence_gate}[facing=north]",
    "O": "{foundation}",
    "Q": "{fine_foundation}",
    "^": "{steps}[facing=south,half=bottom,shape=straight]",
    "m": "minecraft:grindstone[face=floor,facing=north]",
    "a": "minecraft:fletching_table",
    "i": "minecraft:smithing_table",
    "n": "minecraft:anvil[facing=east]",
    "e": "minecraft:brewing_stand",
    "u": "minecraft:cauldron",
    "p": "minecraft:lectern[facing=north]",
    # Les matériaux des quatre niveaux (brief 11).
    "_": "minecraft:coarse_dirt",
    "K": "minecraft:bricks",
    "Z": "minecraft:stone_bricks",
    "I": "minecraft:iron_bars",
    "J": "minecraft:lantern[hanging=true]",
    "X": "minecraft:polished_andesite",
    "V": "minecraft:quartz_pillar[axis=y]",
    "H": "minecraft:white_stained_glass_pane",
    "$": "minecraft:gold_block",
    "{": "minecraft:waxed_cut_copper_stairs[facing=east,half=bottom,shape=straight]",
    "}": "minecraft:waxed_cut_copper_stairs[facing=west,half=bottom,shape=straight]",
    "Y": "minecraft:waxed_cut_copper",
    "o": "minecraft:furnace[facing=north]",
    "%": "minecraft:blast_furnace[facing=north]",
    "&": "minecities:mine_winch[facing=north]",
    "j": "minecities:jeweler_bench[facing=north]",
    "q": "minecraft:stonecutter[facing=north]",
    "v": "minecities:glass_kiln[facing=north]",
    "z": "minecraft:loom[facing=north]",
    "w": "minecraft:beehive[facing=north,honey_level=0]",
    "x": "minecraft:chiseled_bookshelf[facing=north]",
    "#": "minecraft:bookshelf",
    "N": "minecities:tanning_rack[facing=north]",
    "y": "minecraft:barrel[facing=north]",
    "<": "{roof_stairs}[facing=east,half=bottom,shape=straight]",
    ">": "{roof_stairs}[facing=west,half=bottom,shape=straight]",
}


def grid(w, d, fill=" "):
    return [[fill] * w for _ in range(d)]


def shell(w, d, door_x, beds=(), extras=None, fine=False):
    """
    Une pièce close de w x d (murs compris), trois blocs sous plafond, porte au nord. Simple, elle a
    un toit plat en gradins ; améliorée ({@code fine}), une fondation plus noble et un toit à deux
    pans en escaliers.
    """
    extras = extras or {}
    layers = []
    base = "Q" if fine else "F"
    # Le sol : fondation sur le pourtour, plancher dedans.
    floor = grid(w, d, "f")
    for x in range(w):
        floor[0][x] = floor[d - 1][x] = base
    for z in range(d):
        floor[z][0] = floor[z][w - 1] = base
    layers.append(floor)
    for y in (1, 2, 3):
        layer = grid(w, d, " ")
        for x in range(w):
            layer[0][x] = layer[d - 1][x] = "W"
        for z in range(d):
            layer[z][0] = layer[z][w - 1] = "W"
        for x, z in ((0, 0), (w - 1, 0), (0, d - 1), (w - 1, d - 1)):
            layer[z][x] = "P"
        if y == 1:
            layer[0][door_x] = "D"
        elif y == 2:
            layer[0][door_x] = "d"
            # Des fenêtres au milieu de chaque mur latéral et au fond.
            layer[d // 2][0] = layer[d // 2][w - 1] = "G"
            layer[d - 1][w // 2] = "G"
            if w >= 9:
                layer[d - 1][w // 4] = layer[d - 1][3 * w // 4] = "G"
            layer[1][door_x - 1] = "L"
        layers.append(layer)
    for (x, z) in beds:
        layers[1][z][x] = "b"
        layers[1][z + 1][x] = "B"
    for (x, y, z), ch in extras.items():
        layers[y][z][x] = ch
    # Le plafond, plein ; puis le toit.
    layers.append(grid(w, d, "R"))
    if not fine:
        # Un gradin de demi-dalles.
        top = grid(w, d, ".")
        for z in range(1, d - 1):
            for x in range(1, w - 1):
                top[z][x] = "S"
        layers.append(top)
        return layers
    # Deux pans d'escaliers qui montent vers un faîte nord-sud, pignons maçonnés au nord et au sud.
    k = 0
    while k <= w - 1 - k:
        layer = grid(w, d, " ")
        left, right = k, w - 1 - k
        for z in range(d):
            if left == right:
                layer[z][left] = "R"
            else:
                layer[z][left] = "<"
                layer[z][right] = ">"
        for x in range(left + 1, right):
            layer[0][x] = layer[d - 1][x] = "W"
        layers.append(layer)
        k += 1
    return layers


def extend(layers, w, d, ext_w, columns):
    """Ajoute à l'est une parcelle de ext_w de large : columns(y, x, z) donne le caractère."""
    out = []
    for y, layer in enumerate(layers):
        rows = []
        for z in range(d):
            row = "".join(layer[z]) + "".join(columns(y, x, z) for x in range(ext_w))
            rows.append(row)
        out.append(rows)
    return out


def rows(layers):
    return [["".join(r) for r in layer] for layer in layers]


def porch(layers, door_x=3):
    """Un perron au nord : une marche d'escalier devant la porte, pour monter sur la fondation."""
    out = []
    for y, layer in enumerate(layers):
        width = len(layer[0])
        row = ["."] * width
        if y == 0:
            row[door_x] = "^"
        elif y == 1:
            row[door_x] = " "
        out.append(["".join(row)] + list(layer))
    return out


def blueprint(name, kind, layers, upgrade=None, door_x=3, tier=None):
    layers = porch(layers, door_x)
    used = {ch for layer in layers for row in layer for ch in row}
    data = {
        "kind": kind,
        "layers": layers,
        "key": {ch: KEY[ch] for ch in sorted(used) if ch != "."},
    }
    if upgrade:
        data["upgrade"] = upgrade
    if tier:
        # Le palier où la ville peut le commander (village par défaut).
        data["tier"] = tier
    return name, data


def tower():
    """
    La tour de garde : 5 x 5, en pierre taillée. En bas, une salle (la table d'archerie, un coffre pour
    les flèches) ; un fût plein ; en haut, une plateforme crénelée où l'archer prend son poste.
    """
    w = d = 5
    layers = []
    floor = grid(w, d, "Q")
    for z in range(1, d - 1):
        for x in range(1, w - 1):
            floor[z][x] = "f"
    layers.append(floor)
    for y in (1, 2, 3):
        layer = grid(w, d, " ")
        for x in range(w):
            layer[0][x] = layer[d - 1][x] = "Q"
        for z in range(d):
            layer[z][0] = layer[z][w - 1] = "Q"
        if y == 1:
            layer[0][2] = "D"
            layer[3][3] = "a"
            layer[3][1] = "C"
        elif y == 2:
            layer[0][2] = "d"
            layer[1][1] = "L"
            layer[2][0] = layer[2][w - 1] = "G"
        layers.append(layer)
    layers.append(grid(w, d, "Q"))  # le plafond de la salle
    for _ in range(4):  # le fût
        layer = grid(w, d, " ")
        for x in range(w):
            layer[0][x] = layer[d - 1][x] = "Q"
        for z in range(d):
            layer[z][0] = layer[z][w - 1] = "Q"
        layers.append(layer)
    layers.append(grid(w, d, "Q"))  # la plateforme
    crenels = grid(w, d, " ")
    for x, z in ((0, 0), (2, 0), (4, 0), (0, 2), (4, 2), (0, 4), (2, 4), (4, 4)):
        crenels[z][x] = "Q"
    layers.append(crenels)
    layers.append(grid(w, d, " "))  # de l'air pour l'archer debout
    return layers


# Les quatre niveaux (brief 11) : ce qui change d'un niveau à l'autre.
LEVELS = {
    # Cabane : rondins bruts, terre battue, fenêtres à barreaux de bois, toit plat de dalles.
    1: dict(base="P", floor="_", wall="P", corner="P", low="P", window="E", ceiling="S", light="t",
            roof=None, tier=None),
    # Maison : planches, fondation de pierre, vitres, toit à deux pans.
    2: dict(base="F", floor="f", wall="W", corner="P", low="W", window="G", ceiling="R", light="t",
            roof=("<", ">", "R"), tier=None),
    # Bâtisse : pierre taillée, soubassement de briques, barreaux de fer, lanternes.
    3: dict(base="Q", floor="f", wall="W", corner="Z", low="K", window="I", ceiling="R", light="J",
            roof=("<", ">", "R"), tier="town"),
    # Demeure : pierre polie, colonnes de quartz, vitraux, toit de cuivre, or au fronton.
    4: dict(base="X", floor="f", wall="W", corner="V", low="W", window="H", ceiling="R", light="J",
            roof=("{", "}", "Y"), tier="city"),
}


def levelled(level, w, d, door_x=3, beds=(), extras=None):
    """
    Un bâtiment de niveau 1 à 4 : une pièce de w x d (murs compris), trois blocs sous plafond, porte au
    nord, dans les matériaux de son niveau.
    """
    m = LEVELS[level]
    extras = extras or {}
    layers = []
    floor = grid(w, d, m["floor"])
    for x in range(w):
        floor[0][x] = floor[d - 1][x] = m["base"]
    for z in range(d):
        floor[z][0] = floor[z][w - 1] = m["base"]
    layers.append(floor)
    for y in (1, 2, 3):
        layer = grid(w, d, " ")
        material = m["low"] if y == 1 else m["wall"]
        for x in range(w):
            layer[0][x] = layer[d - 1][x] = material
        for z in range(d):
            layer[z][0] = layer[z][w - 1] = material
        for x, z in ((0, 0), (w - 1, 0), (0, d - 1), (w - 1, d - 1)):
            layer[z][x] = m["corner"]
        if y == 1:
            layer[0][door_x] = "D"
        elif y == 2:
            layer[0][door_x] = "d"
            layer[d // 2][0] = layer[d // 2][w - 1] = m["window"]
            for x in range(2, w - 2, 3):
                layer[d - 1][x] = m["window"]
            layer[1][door_x - 1] = "L"
        layers.append(layer)
    for (x, z) in beds:
        layers[1][z][x] = "b"
        layers[1][z + 1][x] = "B"
    # La lumière : une torche au sol, ou des lanternes pendues au plafond.
    lights = [(1, 1), (w - 2, d - 2)] if w > 8 else [(1, 1)]
    for x, z in lights:
        y = 3 if m["light"] == "J" else 1
        if layers[y][z][x] == " " and (y == 3 or layers[1][z][x] == " "):
            layers[y][z][x] = m["light"]
    for (x, y, z), ch in extras.items():
        layers[y][z][x] = ch
    ceiling = grid(w, d, m["ceiling"])
    if level == 4:
        ceiling[0][door_x] = "$"
    layers.append(ceiling)
    if m["roof"] is None:
        return layers
    # Deux pans d'escaliers vers un faîte nord-sud, pignons dans la matière des murs.
    east, west, ridge = m["roof"]
    k = 0
    while k <= w - 1 - k:
        layer = grid(w, d, " ")
        left, right = k, w - 1 - k
        for z in range(d):
            if left == right:
                layer[z][left] = ridge
            else:
                layer[z][left] = east
                layer[z][right] = west
        for x in range(left + 1, right):
            layer[0][x] = layer[d - 1][x] = m["wall"]
        layers.append(layer)
        k += 1
    return layers


# La taille de chaque niveau : une maison gagne deux lits, un atelier un poste.
HOUSE_SIZES = {1: (7, 7), 2: (9, 7), 3: (11, 7), 4: (11, 9)}
WORKSHOP_SIZES = {1: (7, 7), 2: (9, 7), 3: (11, 7), 4: (13, 7)}


def house_level(level):
    w, d = HOUSE_SIZES[level]
    # Deux lits de plus à chaque niveau : 2, 4, 6 (une rangée), puis 8 (deux rangées).
    beds = {1: [(1, 4), (5, 4)],
            2: [(1, 4), (3, 4), (5, 4), (7, 4)],
            3: [(1, 4), (2, 4), (4, 4), (5, 4), (7, 4), (8, 4)],
            4: [(x, 3) for x in (1, 3, 7, 9)] + [(x, 6) for x in (1, 3, 7, 9)]}[level]
    return levelled(level, w, d, beds=beds, extras={(w - 2, 1, 1): "C"})


def workshop_level(level, station, beds=(), more=None):
    """Un atelier : un poste par niveau, le long du mur du fond ; un coffre près de la porte."""
    w, d = WORKSHOP_SIZES[level]
    extras = {(w - 2, 1, 1): "C"}
    for i in range(level):
        extras[(5 + 2 * i, 1, d - 2)] = station
    extras.update(more or {})
    return levelled(level, w, d, beds=beds, extras=extras)


def series(prints, name, make, outside=None, ext_width=8, min_tier=None):
    """Les quatre niveaux d'un bâtiment : nom_1 … nom_4, chacun s'améliore en le suivant."""
    for level in range(1, 5):
        layers = make(level)
        w = len(layers[0][0])
        d = len(layers[0])
        body = extend(layers, w, d, ext_width, outside(w)) if outside else rows(layers)
        tier = LEVELS[level]["tier"]
        if min_tier == "town" and tier is None:
            tier = "town"
        upgrade = f"minecities:{name}_{level + 1}" if level < 4 else None
        prints.append(blueprint(f"{name}_{level}", "house" if name == "house" else "workshop", body,
                                upgrade=upgrade, tier=tier))


def workshop(extras, fine=False):
    """Un atelier : 7 x 7, ou 9 x 7 amélioré (deux postes, deux travailleurs)."""
    if fine:
        return shell(9, 7, 3, extras={(7, 1, 1): "C", (1, 1, 5): "t", **extras}, fine=True)
    return shell(7, 7, 3, extras={(5, 1, 1): "C", (1, 1, 5): "t", **extras})


def field(width):
    """Un champ labouré de 7 x 7 à l'est de l'atelier, irrigué au centre."""
    return lambda y, x, z: (("~" if (x, z) == (4, 3) else "." if x == 0 else "T") if y == 0
                            else " " if y == 1 and x > 0 else ".")


def pen(y, x, z):
    """Un enclos de 7 x 7 à l'est, portillon au nord, au niveau du sol (la couche 0)."""
    if y != 0 or x == 0:
        return "."
    edge = x in (1, 7) or z in (0, 6)
    if not edge:
        return " "
    return "g" if (x, z) == (4, 0) else "E"


def pond(y, x, z):
    """Un bassin de 3 x 5 à l'est, cerclé de pierre."""
    if y != 0:
        return "." if y > 1 else " "
    if x == 0:
        return "."
    return "~" if 2 <= x <= 4 and 1 <= z <= 5 else "O" if 1 <= x <= 5 else "."


def main():
    prints = []
    # Les quatre niveaux de chaque bâtiment (brief 11).
    series(prints, "house", house_level)
    stations = {"kitchen": "s", "sawmill": "l", "builder_hut": "c", "bakery": "o", "smithy": "%",
                "infirmary": "e", "inventor_workshop": "i"}
    for name, station in stations.items():
        series(prints, name, lambda level, s=station: workshop_level(level, s))
    series(prints, "farm", lambda level: workshop_level(level, "k"), outside=field)
    series(prints, "barn", lambda level: workshop_level(level, "h"), outside=lambda w: pen)
    series(prints, "fishery", lambda level: workshop_level(level, "r"), outside=lambda w: pond, ext_width=6)
    series(prints, "market", lambda level: workshop_level(level, "p", more={(1, 1, 4): "y", (2, 1, 4): "y"}),
           min_tier="town")
    series(prints, "barracks", lambda level: workshop_level(level, "m",
                                                            beds=[(x, 2) for x in (1, 3, 5, 7)][:level + 1]))
    # La mine : le treuil, au-dessus du puits que le mineur creuse à l'est du bâtiment.
    series(prints, "mine", lambda level: workshop_level(level, "&"))
    # Les artisans de la v1.0 : joaillerie (dès la ville), taillerie de pierre, verrerie, tissage, rucher.
    series(prints, "jeweler_workshop", lambda level: workshop_level(level, "j"), min_tier="town")
    series(prints, "masonry", lambda level: workshop_level(level, "q"))
    series(prints, "glassworks", lambda level: workshop_level(level, "v"))
    series(prints, "weavery", lambda level: workshop_level(level, "z"))
    series(prints, "apiary", lambda level: workshop_level(level, "w"))
    series(prints, "tannery", lambda level: workshop_level(level, "N"))
    # La bibliothèque (dès la ville) : des bibliothèques ciselées, et des rayonnages le long des murs.
    series(prints, "library", lambda level: workshop_level(level, "x", more={(1, 1, 2): "#", (1, 1, 3): "#",
                                                                               (1, 1, 4): "#"}), min_tier="town")

    # Les anciens schémas (avant la v0.9) restent pour les chantiers en cours ; ils mènent aux nouveaux.
    # Les maisons : petite, grande, puis le manoir (toit à deux pans, fondation de pierre taillée).
    prints.append(blueprint("house_small", "house", rows(shell(7, 7, 3, beds=[(1, 4), (5, 4)],
            extras={(5, 1, 1): "C", (3, 1, 5): "t"})), upgrade="minecities:house_large"))
    prints.append(blueprint("house_large", "house", rows(shell(9, 7, 3, beds=[(1, 4), (3, 4), (5, 4), (7, 4)],
            extras={(7, 1, 1): "C", (1, 1, 1): "t"})), upgrade="minecities:manor"))
    prints.append(blueprint("manor", "house", rows(shell(9, 7, 3, beds=[(1, 4), (3, 4), (5, 4), (7, 4)],
            extras={(7, 1, 1): "C", (1, 1, 1): "t", (4, 1, 5): "t"}, fine=True))))

    # Les ateliers, simples puis agrandis : un toit à deux pans et un second poste de travail.
    simple = {
        "kitchen": ("s", None), "sawmill": ("l", None), "builder_hut": ("c", None),
        "farm": ("k", field), "barn": ("h", lambda w: pen), "fishery": ("r", lambda w: pond),
    }
    for name, (station, outside) in simple.items():
        small = workshop({(5, 1, 5): station})
        large = workshop({(5, 1, 5): station, (7, 1, 5): station}, fine=True)
        if outside is None:
            small_rows, large_rows = rows(small), rows(large)
        else:
            width = 6 if name == "fishery" else 8
            small_rows = extend(small, 7, 7, width, outside(7))
            large_rows = extend(large, 9, 7, width, outside(9))
        prints.append(blueprint(name, "workshop", small_rows, upgrade=f"minecities:{name}_large"))
        prints.append(blueprint(f"{name}_large", "workshop", large_rows))

    # La caserne : des lits pour les gardes, deux meules (deux gardes), un coffre pour les armes.
    prints.append(blueprint("barracks", "workshop", rows(shell(9, 7, 3, beds=[(1, 4), (3, 4)],
            extras={(7, 1, 1): "C", (6, 1, 5): "m", (7, 1, 4): "m", (1, 1, 1): "t"})),
            upgrade="minecities:barracks_large"))
    prints.append(blueprint("barracks_large", "workshop", rows(shell(9, 7, 3, beds=[(1, 4), (3, 4), (5, 4)],
            extras={(7, 1, 1): "C", (7, 1, 3): "m", (7, 1, 4): "m", (7, 1, 5): "m", (1, 1, 1): "t"}, fine=True))))
    prints.append(blueprint("guard_tower", "workshop", rows(tower()), door_x=2))
    # L'atelier de l'inventeur : une table de forge, une enclume, un coffre pour le fer et les citrouilles.
    prints.append(blueprint("inventor_workshop", "workshop", rows(workshop({(5, 1, 5): "i", (5, 1, 3): "n"}))))

    # L'infirmerie du médecin : un alambic et un chaudron ; agrandie, deux alambics (deux médecins).
    prints.append(blueprint("infirmary", "workshop", rows(workshop({(5, 1, 5): "e", (4, 1, 5): "u"})),
            upgrade="minecities:infirmary_large"))
    prints.append(blueprint("infirmary_large", "workshop", rows(workshop({(5, 1, 5): "e", (7, 1, 5): "e",
            (6, 1, 5): "u"}, fine=True))))

    # Le marché, dès la ville : un pupitre (le marchand), des tonneaux et un coffre pour les marchandises.
    prints.append(blueprint("market", "workshop", rows(shell(9, 7, 3, extras={(4, 1, 4): "p", (1, 1, 5): "y",
            (2, 1, 5): "y", (7, 1, 5): "y", (7, 1, 1): "C", (1, 1, 1): "t"})), upgrade="minecities:market_large",
            tier="town"))
    prints.append(blueprint("market_large", "workshop", rows(shell(9, 7, 3, extras={(3, 1, 4): "p", (5, 1, 4): "p",
            (1, 1, 5): "y", (2, 1, 5): "y", (7, 1, 5): "y", (6, 1, 5): "y", (7, 1, 1): "C", (1, 1, 1): "t"},
            fine=True)), tier="town"))

    upgrades = {"house_small": "house_2", "house_large": "house_3", "manor": "house_4",
                "barracks": "barracks_2", "barracks_large": "barracks_3", "infirmary": "infirmary_2",
                "infirmary_large": "infirmary_3", "market": "market_2", "market_large": "market_3",
                "inventor_workshop": "inventor_workshop_2"}
    for name in ("kitchen", "sawmill", "builder_hut", "farm", "barn", "fishery"):
        upgrades[name] = f"{name}_2"
        upgrades[f"{name}_large"] = f"{name}_3"
    kept = []
    names = set()
    for name, data in prints:
        if name in names:
            continue
        names.add(name)
        if name in upgrades:
            data["upgrade"] = "minecities:" + upgrades[name]
            data["legacy"] = True
        kept.append((name, data))
    prints = kept

    OUT.mkdir(parents=True, exist_ok=True)
    for name, data in prints:
        path = OUT / f"{name}.json"
        path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
        print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
