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


def blueprint(name, kind, layers, upgrade=None, door_x=3):
    layers = porch(layers, door_x)
    used = {ch for layer in layers for row in layer for ch in row}
    data = {
        "kind": kind,
        "layers": layers,
        "key": {ch: KEY[ch] for ch in sorted(used) if ch != "."},
    }
    if upgrade:
        data["upgrade"] = upgrade
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

    OUT.mkdir(parents=True, exist_ok=True)
    for name, data in prints:
        path = OUT / f"{name}.json"
        path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
        print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
