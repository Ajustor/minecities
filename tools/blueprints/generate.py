"""Génère les schémas de bâtiments du bâtisseur (data/minecities/blueprint/*.json).

Usage : python tools/blueprints/generate.py

Un schéma est un empilement de couches (de bas en haut), chacune une liste de rangées du nord au
sud, chaque caractère un bloc d'ouest en est ; la clé dit quel bloc est chaque caractère. Les blocs
s'écrivent avec des jetons de style ({wall}, {roof}, {door}…) que le style choisi à la commande
remplace (plaines, taïga, désert, savane : voir BuildStyle.java). « . » laisse le bloc tel quel,
« espace » exige de l'air.

La porte est au nord (z = 0) ; la plaque est accrochée à l'intérieur du mur nord, et déclare le
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
}


def grid(w, d, fill=" "):
    return [[fill] * w for _ in range(d)]


def shell(w, d, door_x, beds=(), extras=None):
    """Une pièce close de w x d (murs compris), trois blocs sous plafond, porte au nord."""
    extras = extras or {}
    layers = []
    # Le sol : fondation sur le pourtour, plancher dedans.
    floor = grid(w, d, "f")
    for x in range(w):
        floor[0][x] = floor[d - 1][x] = "F"
    for z in range(d):
        floor[z][0] = floor[z][w - 1] = "F"
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
    # Le toit : une dalle pleine, puis un gradin de demi-dalles.
    layers.append(grid(w, d, "R"))
    top = grid(w, d, ".")
    for z in range(1, d - 1):
        for x in range(1, w - 1):
            top[z][x] = "S"
    layers.append(top)
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


def blueprint(name, kind, layers, upgrade=None):
    used = {ch for layer in layers for row in layer for ch in row}
    data = {
        "kind": kind,
        "layers": layers,
        "key": {ch: KEY[ch] for ch in sorted(used) if ch != "."},
    }
    if upgrade:
        data["upgrade"] = upgrade
    return name, data


def workshop(extras):
    return shell(7, 7, 3, extras={(5, 1, 1): "C", (1, 1, 5): "t", **extras})


def main():
    prints = []
    prints.append(blueprint("house_small", "house", rows(shell(7, 7, 3, beds=[(1, 4), (5, 4)],
            extras={(5, 1, 1): "C", (3, 1, 5): "t"})), upgrade="minecities:house_large"))
    prints.append(blueprint("house_large", "house", rows(shell(9, 7, 3, beds=[(1, 4), (3, 4), (5, 4), (7, 4)],
            extras={(7, 1, 1): "C", (1, 1, 1): "t"}))))
    prints.append(blueprint("kitchen", "workshop", rows(workshop({(5, 1, 5): "s"}))))
    prints.append(blueprint("sawmill", "workshop", rows(workshop({(5, 1, 5): "l"}))))
    prints.append(blueprint("builder_hut", "workshop", rows(workshop({(5, 1, 5): "c"}))))

    # La ferme : l'atelier au composteur, et un champ labouré de 7 x 7 à l'est, irrigué au centre.
    farm = workshop({(5, 1, 5): "k"})
    prints.append(blueprint("farm", "workshop", extend(farm, 7, 7, 8, lambda y, x, z:
            ("~" if (x, z) == (4, 3) else "." if x == 0 else "T") if y == 0 else " " if y == 1 and x > 0 else ".")))

    # L'étable : l'atelier à la botte de foin, et un enclos de 7 x 7 à l'est, portillon au nord, posé
    # au niveau du sol (la couche 0, comme les fondations).
    def pen(y, x, z):
        if y != 0 or x == 0:
            return "."
        edge = x in (1, 7) or z in (0, 6)
        if not edge:
            return " "
        return "g" if (x, z) == (4, 0) else "E"
    prints.append(blueprint("barn", "workshop", extend(workshop({(5, 1, 5): "h"}), 7, 7, 8, pen)))

    # La pêcherie : l'atelier au baril, et un bassin de 3 x 5 à l'est, cerclé de pierre.
    def pond(y, x, z):
        if y != 0:
            return "." if y > 1 else " "
        if x == 0:
            return "."
        return "~" if 2 <= x <= 4 and 1 <= z <= 5 else "O" if 1 <= x <= 5 else "."
    prints.append(blueprint("fishery", "workshop", extend(workshop({(5, 1, 5): "r"}), 7, 7, 6, pond)))

    OUT.mkdir(parents=True, exist_ok=True)
    for name, data in prints:
        path = OUT / f"{name}.json"
        path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
        print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
