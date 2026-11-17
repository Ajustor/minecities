"""
La bibliothèque : de hautes fenêtres, du plancher au plafond, entre de hauts volets (on voit les
rayonnages par les vitres), et une tour de pierre carrée à l'angle nord-est, qui dépasse du toit, un
chapeau pointu dessus.

La tour est pleine (ou creuse et fermée) et seulement accolée au mur : elle ne touche pas à la pièce.
"""

KEY = {
    # Le chapeau de la tour.
    "α": "{roof_stairs}[facing=south,half=bottom,shape=straight]",
    "β": "{roof_stairs}[facing=north,half=bottom,shape=straight]",
    "γ": "{fine_roof_stairs}[facing=south,half=bottom,shape=straight]",
    "δ": "{fine_roof_stairs}[facing=north,half=bottom,shape=straight]",
    # La pointe du chapeau, au niveau 4.
    "ε": "minecraft:lightning_rod[facing=up]",
}


def apply(b):
    windows(b)
    tower(b)


def windows(b):
    """Les fenêtres du milieu des côtés montent du plancher au plafond, leurs volets aussi."""
    w, d, m = b.w, b.d, b.m
    mid = d // 2
    glass = m["window"]
    for y in (1, 3):
        b.set(1, y, mid, glass)
        b.set(w, y, mid, glass)
    # Plus d'appui sous une fenêtre qui descend au plancher ; des volets sur toute la hauteur.
    b.set(0, 1, mid, ".")
    b.set(w + 1, 1, mid, ".")
    for y in (1, 3):
        for dz in (-1, 1):
            b.set(0, y, mid + dz, "(")
            b.set(w + 1, y, mid + dz, ")")
    if b.level >= 2:
        # Sur la façade aussi, les deux fenêtres de part et d'autre de la porte.
        for x in (2, w - 1):
            for y in (1, 3):
                b.set(x, y, 0, glass)
    if b.level >= 3:
        # Et au fond, entre les postes, les fenêtres descendent au plancher.
        for x in range(3, w - 1, 3):
            if b.get(x, 2, d - 1) == glass:
                b.set(x, 3, d - 1, glass)


def tower(b):
    """La tour, à l'angle nord-est : 2 x 2 pleine au niveau 1, puis 3 x 3 creuse, plus haute que le faîte."""
    w, m = b.w, b.m
    x0 = w + 1
    if b.level == 1:
        b.fill(x0, 0, 0, x0 + 1, 5, 1, "F")
        b.set(x0 + 1, 3, 0, m["window"])
        b.fill(x0, 6, 0, x0 + 1, 6, 1, "S")
        return
    x1 = x0 + 2
    stone = "F" if b.level == 2 else "Q"
    ridge = max(y for y in range(5, b.height) if b.get((w + 2) // 2, y, 1) in "RY")
    top = ridge + (1 if b.level == 2 else 2)
    for y in range(0, top + 1):
        for z in range(3):
            for x in range(x0, x1 + 1):
                b.set(x, y, z, " " if (x, z) == (x0 + 1, 1) and 1 <= y else stone)
        if b.level >= 3:
            for x, z in ((x0, 0), (x1, 0), (x0, 2), (x1, 2)):
                b.set(x, y, z, "Z")
    b.set(x0 + 1, 0, 1, stone)
    # Des meurtrières au nord et à l'est, une par étage.
    for y in range(2, top, 4):
        b.set(x0 + 1, y, 0, m["window"])
        b.set(x1, y, 1, m["window"])
    # Le chapeau.
    cap = top + 1
    fine = b.level == 4
    north, south = ("γ", "δ") if fine else ("α", "β")
    east, west, ridge_block = m["roof"]
    for x in range(x0, x1 + 1):
        b.set(x, cap, 0, north)
        b.set(x, cap, 2, south)
    b.set(x0, cap, 1, east)
    b.set(x1, cap, 1, west)
    b.set(x0 + 1, cap, 1, ridge_block)
    b.set(x0 + 1, cap + 1, 1, "ε" if fine else "]")
    if b.level >= 3:
        # Une lanterne pendue sous la potence de pierre, au coin de la rue.
        b.set(x1 + 1, 3, 0, "Z")
        b.set(x1 + 1, 2, 0, "J")
