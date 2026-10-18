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
    "4": "minecities:taverner_station[facing=north]",
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
    "!": "minecities:carpenter_bench[facing=north]",
    "?": "minecities:warehouse_ledger[facing=south]",
    # Le décor (v1.2) : volets, cheminée, tapis, pot de fleurs.
    "(": "{trapdoor}[facing=west,half=top,open=true]",
    ")": "{trapdoor}[facing=east,half=top,open=true]",
    "=": "{trapdoor}[facing=south,half=top,open=true]",
    "*": "minecraft:campfire[lit=true,signal_fire=false,facing=north]",
    "-": "{carpet}",
    "+": "minecraft:potted_poppy",
    "|": "{frame}[axis=x]",
    # Le mobilier (v1.3) : rien qui soit un poste de travail, pour ne pas créer d'emploi par mégarde.
    "A": "minecraft:crafting_table",
    "1": "minecraft:candle[candles=3,lit=true]",
    "2": "minecraft:decorated_pot",
    "3": "minecraft:cauldron",
    "5": "minecraft:cake",
    "6": "minecraft:pumpkin",
    "7": "minecraft:melon",
    "8": "minecraft:rail",
    "@": "minecraft:potted_oak_sapling",
    ";": "minecraft:smooth_stone_slab[type=top]",
    ":": "{roof_slab}[type=top]",
    "9": "minecraft:white_wool",
    "y": "minecraft:barrel[facing=north]",
    "<": "{roof_stairs}[facing=east,half=bottom,shape=straight]",
    ">": "{roof_stairs}[facing=west,half=bottom,shape=straight]",
    # Les monuments (MC-11) : pierre taillée, quartz, or, une cloche.
    "M": "minecraft:chiseled_quartz_block",
    "U": "minecraft:smooth_quartz",
    ",": "minecraft:chiseled_stone_bricks",
    "'": "minecraft:sea_lantern",
    "[": "minecraft:bell[attachment=ceiling,facing=north]",
    "]": "minecraft:lantern[hanging=false]",
    "0": "minecraft:stone_bricks",
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


def monument(name, layers):
    """Un monument (MC-11) : pas de perron ni de plaque, ouvert à la mégalopole."""
    used = {ch for layer in layers for row in layer for ch in row}
    data = {
        "kind": "monument",
        "layers": layers,
        "key": {ch: KEY[ch] for ch in sorted(used) if ch != "."},
        "tier": "megalopolis",
    }
    return name, data


def square(w, ch, inner=None, ring=0):
    """Une couche de w x w : ch partout, ou seulement sur un anneau (ring = 0 : le bord)."""
    layer = grid(w, w, inner if inner is not None else ch)
    for z in range(w):
        for x in range(w):
            if inner is not None and ring <= min(x, z, w - 1 - x, w - 1 - z) <= ring:
                layer[z][x] = ch
    return layer


def statue():
    """
    La statue : un socle à degrés (andésite polie, pierre taillée, quartz), et dessus un personnage de
    quartz qui lève une torche d'or. 7 x 7, coins d'or au pied.
    """
    w = 7
    base = square(w, "X")
    for x, z in ((0, 0), (6, 0), (0, 6), (6, 6)):
        base[z][x] = "$"
    step = grid(w, w, " ")
    for z in range(1, 6):
        for x in range(1, 6):
            step[z][x] = "0"
    for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
        step[z][x] = ","
    pedestal = grid(w, w, " ")
    for z in range(2, 5):
        for x in range(2, 5):
            pedestal[z][x] = "U"
    pedestal[3][3] = "M"
    layers = [base, step, pedestal]

    def figure(cells):
        layer = grid(w, w, " ")
        for (x, z), ch in cells.items():
            layer[z][x] = ch
        return layer

    layers.append(figure({(2, 3): "V", (4, 3): "V"}))               # les jambes
    layers.append(figure({(2, 3): "V", (4, 3): "V"}))
    # Le buste, le bras gauche le long du corps, le droit levé.
    layers.append(figure({(1, 3): "U", (2, 3): "U", (3, 3): "U", (4, 3): "U"}))
    layers.append(figure({(1, 3): "U", (2, 3): "U", (3, 3): "U", (4, 3): "U", (5, 3): "U"}))
    layers.append(figure({(3, 3): "M", (5, 3): "U"}))               # la tête
    layers.append(figure({(5, 3): "$"}))                            # la torche d'or
    return [["".join(r) for r in layer] for layer in layers]


def fountain():
    """
    La fontaine : un parvis de 9 x 9, un bassin de pierre taillée, une colonne de quartz au milieu de
    l'eau, et en haut une vasque d'eau entre quatre blocs d'or. Des lanternes aux coins du bassin.
    """
    w = 9
    paving = square(w, "X")
    paving[4][4] = "'"
    basin = grid(w, w, " ")
    for z in range(2, 7):
        for x in range(2, 7):
            edge = x in (2, 6) or z in (2, 6)
            basin[z][x] = "0" if edge else "~"
    basin[4][4] = "V"
    for x, z in ((2, 2), (6, 2), (2, 6), (6, 6)):
        basin[z][x] = ","
    rim = grid(w, w, " ")
    rim[4][4] = "V"
    for x, z in ((2, 2), (6, 2), (2, 6), (6, 6)):
        rim[z][x] = "]"
    column = grid(w, w, " ")
    column[4][4] = "V"
    cup = grid(w, w, " ")
    for z in range(3, 6):
        for x in range(3, 6):
            cup[z][x] = "U"
    cup[4][4] = "M"
    bowl = grid(w, w, " ")
    for z in range(3, 6):
        for x in range(3, 6):
            bowl[z][x] = "U"
    bowl[4][4] = "~"
    for x, z in ((3, 3), (5, 3), (3, 5), (5, 5)):
        bowl[z][x] = "$"
    layers = [paving, basin, rim, column, cup, bowl]
    return [["".join(r) for r in layer] for layer in layers]


def belfry():
    """
    Le beffroi : une tour carrée de 7 x 7 en pierre taillée, chaînée de quartz aux angles, avec une
    porte au nord ; en haut, la chambre des cloches ouverte sur les quatre côtés, la cloche pendue
    sous un toit de cuivre en pyramide, et des lanternes.
    """
    w = 7
    layers = [square(w, "X")]
    for y in range(1, 10):
        layer = square(w, "0", inner=" ")
        for x, z in ((0, 0), (6, 0), (0, 6), (6, 6)):
            layer[z][x] = "V"
        if y in (1, 2):
            layer[0][3] = " "  # l'entrée
        if y in (4, 7):
            # Des meurtrières de verre au milieu de chaque face.
            for x, z in ((3, 0), (0, 3), (6, 3), (3, 6)):
                layer[z][x] = "H"
        layers.append(layer)
    # Le plancher de la chambre des cloches, bordé de pierre ciselée.
    floor = square(w, ",", inner="0")
    for x, z in ((0, 0), (6, 0), (0, 6), (6, 6)):
        floor[z][x] = "V"
    layers.append(floor)
    # La chambre des cloches : des piliers d'angle, ouverte entre eux.
    for y in range(3):
        layer = grid(w, w, " ")
        for x, z in ((0, 0), (6, 0), (0, 6), (6, 6)):
            layer[z][x] = "V"
        if y == 0:
            for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
                layer[z][x] = "]"
        if y == 2:
            layer[3][3] = "["
        layers.append(layer)
    # Le toit : une dalle de cuivre, puis une pyramide qui se resserre, une pointe d'or.
    layers.append(square(w, "Y"))
    for k in (1, 2):
        layer = grid(w, w, " ")
        for z in range(k, w - k):
            for x in range(k, w - k):
                layer[z][x] = "Y"
        layers.append(layer)
    top = grid(w, w, " ")
    top[3][3] = "$"
    layers.append(top)
    return [["".join(r) for r in layer] for layer in layers]


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


def decorate(level, layers, w, d, house):
    """
    Habille un bâtiment de w x d (brief v1.2) : un débord de toit tout autour, des volets ouverts aux
    fenêtres, une cheminée contre le mur ouest (et son feu de camp allumé), un pot de fleurs sur le
    coffre ; dans les maisons, un tapis de l'entrée au fond et, dès le niveau 3, une bibliothèque.
    Le bâtiment gagne une colonne de chaque côté et une rangée au fond (x + 1 dans le résultat).
    """
    m = LEVELS[level]
    out = []
    for layer in layers:
        rows = [["."] + list(row) + ["."] for row in layer]
        rows.append(["."] * (w + 2))
        out.append(rows)
    width = w + 2
    # L'intérieur : un pot de fleurs sur le coffre, un tapis, une bibliothèque.
    if out[2][1][w - 1] == " ":
        out[2][1][w - 1] = "+"
    if house and level >= 2:
        for z in range(1, d - 1):
            if out[1][z][4] == " ":
                out[1][z][4] = "-"
    if house and level >= 3 and out[1][1][2] == " ":
        out[1][1][2] = "#"
    # Des fenêtres sur la façade, de part et d'autre de la porte (dès le niveau 2).
    if level >= 2:
        for x in (2, width - 3):
            if out[2][0][x] in ("W", "K"):
                out[2][0][x] = m["window"]
    # Les pignons : une poutre de rondin à leur pied, une lucarne au milieu.
    if m["roof"] is not None:
        for z in (0, d - 1):
            for x in range(width):
                if out[5][z][x] == "W":
                    out[5][z][x] = "|"
            if len(out) > 7 and out[7][z][width // 2] == "W":
                out[7][z][width // 2] = "G"
    # Le débord du toit : des marches (ou des dalles) au ras du plafond, tout autour.
    if m["roof"] is None:
        for z in range(d + 1):
            out[4][z][0] = out[4][z][width - 1] = "S"
        for x in range(width):
            out[4][d][x] = "S"
    else:
        east, west, _ = m["roof"]
        for z in range(d):
            out[4][z][0] = east
            out[4][z][width - 1] = west
    # Des volets de part et d'autre des fenêtres des côtés et du fond.
    middle = d // 2
    for dz in (-1, 1):
        out[2][middle + dz][0] = "("
        out[2][middle + dz][width - 1] = ")"
    for wx in range(2, w - 2, 3):
        for dx in (-1, 1):
            if out[2][d][wx + 1 + dx] == ".":
                out[2][d][wx + 1 + dx] = "="
    # La cheminée, contre le mur ouest, jusqu'au-dessus du toit, et son feu.
    chimney = {1: "O", 2: "O", 3: "K", 4: "K"}[level]
    for y in range(len(out)):
        out[y][d - 2][0] = chimney
    top = [["."] * width for _ in range(d + 1)]
    top[d - 2][0] = "*"
    out.append(top)
    return out


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
    return decorate(level, levelled(level, w, d, beds=beds, extras={(w - 2, 1, 1): "C"}), w, d, True)


def workshop_level(level, station, beds=(), more=None):
    """Un atelier : un poste par niveau, le long du mur du fond ; un coffre près de la porte."""
    w, d = WORKSHOP_SIZES[level]
    extras = {(w - 2, 1, 1): "C"}
    for i in range(level):
        extras[(5 + 2 * i, 1, d - 2)] = station
    extras.update(more or {})
    return decorate(level, levelled(level, w, d, beds=beds, extras=extras), w, d, False)


# Le mobilier de chaque bâtiment, dans l'ordre où on le pose le long des murs ; « T » est une table
# (un piquet de barrière et une bougie dessus). Plus le niveau est haut, plus il y a de meubles.
FURNITURE = {
    "house": ["A", "T", "2", "@", "C", ":", "T", "@"],
    "kitchen": [";", ";", "3", "T", "5", "2", ";", "@"],
    "bakery": [";", "5", "2", ";", "6", "T", "5", "@"],
    "sawmill": ["P", "P", "2", "A", "@", "P", "T", "2"],
    "builder_hut": ["A", "n", "2", "T", "P", "@", "2", "A"],
    "smithy": ["n", "3", "I", "2", "T", "n", "I", "2"],
    "infirmary": ["3", "@", "T", "9", "@", "2", "T", "9"],
    "inventor_workshop": ["n", "A", "2", "T", "6", "@", "n", "2"],
    "farm": ["6", "7", "@", "2", "6", "T", "7", "@"],
    "barn": ["2", "6", "@", "T", "2", "7", "@", "2"],
    "fishery": ["2", "@", "T", "2", "3", "@", "2", "T"],
    "market": ["2", "6", "7", "T", "2", "C", "@", "2"],
    "barracks": ["I", "2", "n", "T", "I", "@", "2", "A"],
    "mine": ["8", "2", "n", "8", "T", "2", "8", "@"],
    "jeweler_workshop": ["T", "2", "@", "A", "T", "2", "@", "C"],
    "masonry": ["2", "Z", "Z", "T", "A", "2", "Z", "@"],
    "glassworks": ["2", "3", "T", "@", "2", "A", "T", "2"],
    "weavery": ["9", "9", "2", "T", "@", "9", "2", "A"],
    "apiary": ["@", "@", "2", "T", "@", "2", "6", "@"],
    "tannery": ["3", "2", "T", "@", "3", "2", "A", "@"],
    "library": ["#", "T", "#", "@", "#", "T", "#", "2"],
    "carpentry": ["P", "P", "2", "A", "T", "@", "P", "2"],
    "tavern": ["y", "T", "2", "T", "y", "2", "T", "@"],
    "warehouse": ["2", "T", "2", "@", "2", "T", "2", "@"],
}
# Combien de meubles à chaque niveau.
FURNISHED = {1: 2, 2: 3, 3: 5, 4: 8}


def furnish(level, out, family):
    """
    Meuble un bâtiment déjà habillé (décalé d'une colonne : murs en x = 1 et x = w, porte en x = 4) :
    le long des murs ouest et est puis du fond, sur les cases libres, en laissant l'allée centrale, le
    devant de la porte, les lits et les postes de travail dégagés.
    """
    props = FURNITURE.get(family, [])[:FURNISHED[level]]
    if not props:
        return out
    floor = out[1]
    depth = len(floor) - 1
    width = len(floor[0])
    w = width - 2

    def free(x, z):
        if not (2 <= x <= w - 1 and 1 <= z <= depth - 2) or floor[z][x] != " ":
            return False
        if x == 4 or z == 1 and abs(x - 4) <= 1:
            return False
        # Ne pas boucher l'accès d'un lit ni d'un poste (la case devant eux).
        for dz in (-1, 1):
            if 0 <= z + dz < depth and floor[z + dz][x] not in (" ", "-") and floor[z + dz][x] in "bBskloe%im&jqvzwxNphra":
                return False
        return True

    cells = [(2, z) for z in range(2, depth - 1)] + [(w - 1, z) for z in range(2, depth - 1)] \
        + [(x, depth - 2) for x in range(3, w - 1)]
    placed = 0
    for x, z in cells:
        if placed >= len(props):
            break
        if not free(x, z):
            continue
        prop = props[placed]
        if prop == "T":
            floor[z][x] = "E"
            if out[2][z][x] == " ":
                out[2][z][x] = "1"
        else:
            floor[z][x] = prop
        placed += 1
    return out


def warehouse_level(level):
    """
    L'entrepôt (brief 12) : son registre près de la porte, et des rangées de coffres le long des murs,
    quatre de plus à chaque niveau.
    """
    w, d = WORKSHOP_SIZES[level]
    extras = {(w - 2, 1, 1): "?"}
    spots = [(1, z) for z in range(2, d - 1)] + [(w - 2, z) for z in range(2, d - 1)] \
        + [(x, d - 2) for x in range(2, w - 2)]
    for x, z in spots[:4 * level]:
        extras[(x, 1, z)] = "C"
    return decorate(level, levelled(level, w, d, extras=extras), w, d, False)


def series(prints, name, make, outside=None, ext_width=8, min_tier=None):
    """Les quatre niveaux d'un bâtiment : nom_1 … nom_4, chacun s'améliore en le suivant."""
    for level in range(1, 5):
        layers = furnish(level, make(level), name)
        w = len(layers[0][0])
        d = len(layers[0])
        body = extend(layers, w, d, ext_width, outside(w)) if outside else rows(layers)
        tier = LEVELS[level]["tier"]
        if min_tier == "town" and tier is None:
            tier = "town"
        upgrade = f"minecities:{name}_{level + 1}" if level < 4 else None
        # La porte est en x = 4 : le décor a ajouté une colonne à l'ouest.
        prints.append(blueprint(f"{name}_{level}", "house" if name == "house" else "workshop", body,
                                upgrade=upgrade, tier=tier, door_x=4))


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
    # La logistique (v1.4) : la charpenterie et l'entrepôt.
    series(prints, "carpentry", lambda level: workshop_level(level, "!"))
    series(prints, "tavern", lambda level: workshop_level(level, "4"))
    series(prints, "warehouse", warehouse_level)
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

    # Les monuments (MC-11) : un de chaque par ville, à la mégalopole.
    prints.append(monument("statue", statue()))
    prints.append(monument("fountain", fountain()))
    prints.append(monument("belfry", belfry()))

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
