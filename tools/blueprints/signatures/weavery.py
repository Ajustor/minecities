"""
La tisserie : à l'est, un séchoir où pend la laine teinte de toutes les couleurs (des blocs de laine
sous une poutre portée par deux poteaux, des bannières unies dès le niveau 3), et derrière, un petit
enclos à moutons (vide : les bêtes viennent d'ailleurs), avec sa toison tondue en tas.
"""

KEY = {
    "α": "minecraft:red_wool",
    "β": "minecraft:yellow_wool",
    "γ": "minecraft:light_blue_wool",
    "δ": "minecraft:lime_wool",
    "ε": "minecraft:orange_wool",
    "ζ": "minecraft:magenta_wool",
    "η": "minecraft:blue_wall_banner[facing=north]",
    "θ": "minecraft:purple_wall_banner[facing=north]",
}

WOOLS = "αβγδεζ"
BANNERS = "ηθ"
# Combien d'écheveaux sous la poutre.
SPAN = {1: 3, 2: 5, 3: 5, 4: 5}
# L'enclos (barrières comprises) : largeur, et il va de z = 3 jusqu'au débord du fond.
PEN = {1: 4, 2: 5, 3: 5, 4: 6}


def apply(b):
    x0 = b.width + 1  # une allée le long du mur est
    n = SPAN[b.level]
    # Le séchoir : deux poteaux, une poutre au-dessus, en travers (est-ouest), au ras de la façade.
    z = 1
    for x in (x0, x0 + n + 1):
        b.fill(x, 1, z, x, 3, z, "P")
    b.fill(x0, 4, z, x0 + n + 1, 4, z, "|")
    for i in range(1, n + 1):
        x = x0 + i
        if b.level >= 3 and i % 2 == 0:
            # Une bannière unie accrochée à la face nord de la poutre.
            b.set(x, 4, z - 1, BANNERS[(i // 2) % len(BANNERS)])
            continue
        wool = WOOLS[(i + b.level) % len(WOOLS)]
        b.set(x, 3, z, wool)
        # Un écheveau sur deux pend plus bas.
        if i % 2 == 1 or b.level == 1:
            b.set(x, 2, z, wool)
    if b.level == 4:
        # Des lanternes posées au bout de la poutre.
        b.set(x0, 5, z, "]")
        b.set(x0 + n + 1, 5, z, "]")
    # L'enclos à moutons, derrière le séchoir.
    pw = PEN[b.level]
    z0, z1 = 3, b.d
    for x in range(x0, x0 + pw):
        for zz in range(z0, z1 + 1):
            if x in (x0, x0 + pw - 1) or zz in (z0, z1):
                b.set(x, 1, zz, "E")
    b.set(x0 + pw // 2, 1, z0, "g")
    if b.level >= 2:
        # La toison tondue, en tas dans un coin de l'enclos.
        b.set(x0 + pw - 2, 1, z1 - 1, "9")
        b.set(x0 + pw - 2, 1, z1 - 2, "9")
    if b.level >= 3:
        b.set(x0 + pw - 2, 2, z1 - 1, "9")
