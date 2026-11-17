"""
La taverne : une terrasse à l'est, des tables (piquet de barrière et tapis) entre des chaises, et une
grande enseigne de bannières pendue à une potence au coin de la rue. Dès le niveau 2, la taverne prend
un étage en encorbellement : il déborde d'un bloc à l'ouest, à l'est et au fond sur des corbeaux, sous
un toit à deux pans plus haut et plus large.

L'étage est posé sur le plafond plein de la pièce : il est clos et ne communique pas avec elle.
"""

KEY = {
    # Les corbeaux qui portent l'encorbellement (des marches retournées).
    "α": "{roof_stairs}[facing=east,half=top,shape=straight]",
    "β": "{roof_stairs}[facing=west,half=top,shape=straight]",
    "γ": "{roof_stairs}[facing=north,half=top,shape=straight]",
    # La sablière de l'étage, sur les côtés.
    "δ": "{frame}[axis=z]",
    # Les chaises, tournées vers leur table.
    "ε": "{roof_stairs}[facing=west,half=bottom,shape=straight]",
    "ζ": "{roof_stairs}[facing=east,half=bottom,shape=straight]",
    # L'enseigne : des bannières pendues sous la potence, tournées vers la rue.
    "η": "minecraft:red_wall_banner[facing=north]",
    "θ": "minecraft:yellow_wall_banner[facing=north]",
}


def apply(b):
    if b.level >= 2:
        upper(b)
    terrace(b)
    sign(b)


def upper(b):
    """L'étage : corbeaux au ras du plafond, deux rangs de murs à colombage, un toit nord-sud."""
    w, d, m = b.w, b.d, b.m
    chimney = b.get(0, 4, d - 2)
    del b.layers[5:]
    right = w + 1
    # Les corbeaux, tout autour sauf sur la rue.
    for z in range(d + 1):
        b.set(0, 4, z, "α")
        b.set(right, 4, z, "β")
    for x in range(1, right):
        b.set(x, 4, d, "γ")
    b.set(0, 4, d - 2, chimney)
    for y in (5, 6):
        for z in range(d + 1):
            for x in range(right + 1):
                edge = x in (0, right) or z in (0, d)
                if not edge:
                    b.set(x, y, z, " ")
                elif y == 5:
                    b.set(x, y, z, "|" if z in (0, d) else "δ")
                else:
                    b.set(x, y, z, m["wall"])
        for x, z in ((0, 0), (right, 0), (0, d), (right, d)):
            b.set(x, y, z, m["corner"])
    centre = right // 2
    for x in (centre - 3, centre, centre + 3):
        b.set(x, 6, 0, m["window"])
        b.set(x, 6, d, m["window"])
    for z in (2, d - 2):
        b.set(right, 6, z, m["window"])
    b.set(0, 6, 2, m["window"])
    # Le toit : deux pans d'escaliers vers un faîte nord-sud, pignons dans la matière des murs.
    east, west, ridge = m["roof"]
    k = 0
    while k <= right - k:
        y = 7 + k
        lo, hi = k, right - k
        for z in range(d + 2):
            if lo == hi:
                b.set(lo, y, z, ridge)
            else:
                b.set(lo, y, z, east)
                b.set(hi, y, z, west)
        for x in range(lo + 1, hi):
            for z in range(d + 1):
                b.set(x, y, z, m["wall"] if z in (0, d) else " ")
        k += 1
    top = 7 + right // 2
    b.set(centre, 9, 0, m["window"])
    for y in range(5, top + 1):
        b.set(0, y, d - 2, chimney)
    b.set(0, top + 1, d - 2, "*")


def terrace(b):
    """La terrasse, à l'est : un plancher et une rambarde dès le niveau 2, une table de plus par niveau."""
    w, d = b.w, b.d
    x0, x1 = w + 1, w + 6
    last = d if b.level == 2 else d + 1
    if b.level >= 2:
        b.fill(x0, 0, 1, x1, 0, last, "f")
        b.fill(x1, 1, 1, x1, 1, last, "E")
        b.fill(w + 4, 1, 1, x1, 1, 1, "E")
        b.fill(x0 + 1, 1, last, x1, 1, last, "E")
    tables = (3, 5, 7)[:min(b.level, 3)]
    tx = w + 4
    for z in tables:
        b.set(tx, 1, z, "E")
        b.set(tx, 2, z, "-")
        b.set(tx - 1, 1, z, "ε")
        b.set(tx + 1, 1, z, "ζ")
    if b.level >= 3:
        # Des lanternes aux coins de la rambarde.
        b.set(x1, 2, 1, "]")
        b.set(x1, 2, last, "]")
    if b.level >= 4:
        # Des fleurs sur la rambarde, des tonneaux couchés contre le mur.
        for z in (3, 5):
            b.set(x1, 2, z, "+")
        b.set(x0 + 1, 1, 1, "y")
        b.set(x0 + 1, 2, 1, "y")


def sign(b):
    """L'enseigne : une potence qui sort du mur est au coin de la rue, les bannières pendues dessous."""
    w = b.w
    length = 2 if b.level < 3 else 3
    b.fill(w + 1, 3, 1, w + length, 3, 1, "|")
    for i in range(length):
        b.set(w + 1 + i, 3, 0, "η" if i % 2 == 0 else "θ")
