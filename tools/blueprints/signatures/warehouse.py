"""
L'entrepôt : un quai de chargement contre le mur est, tonneaux couchés et caisses dessus ; dès le
niveau 2, un étage de grenier sous un toit bas et large (faîte est-ouest), le pignon est percé d'une
porte de chargement, une poutre-potence au-dessus et sa chaîne qui pend, une caisse au bout, sur le quai.

Le grenier est posé sur le plafond plein de la pièce : il ne communique pas avec elle (la plaque ne le
voit pas). Les coffres de l'intérieur (warehouse_level) ne bougent pas.
"""

KEY = {
    # Les pans du toit, au nord et au sud (le faîte court d'ouest en est).
    "α": "{roof_stairs}[facing=south,half=bottom,shape=straight]",
    "β": "{roof_stairs}[facing=north,half=bottom,shape=straight]",
    "γ": "{fine_roof_stairs}[facing=south,half=bottom,shape=straight]",
    "δ": "{fine_roof_stairs}[facing=north,half=bottom,shape=straight]",
    # La porte de chargement du grenier, sur le pignon est.
    "ε": "{door}[facing=east,half=lower,hinge=left]",
    "ζ": "{door}[facing=east,half=upper,hinge=left]",
    # La chaîne de la potence.
    "η": "minecraft:iron_chain[axis=y]",
    # Tonneaux couchés, la bonde vers l'est ou vers la rue.
    "θ": "minecraft:barrel[facing=east]",
    "ι": "minecraft:barrel[facing=north]",
    # La lisse du quai, une bûche couchée nord-sud.
    "κ": "{frame}[axis=z]",
}


def apply(b):
    if b.level >= 2:
        attic(b)
    quay(b)


def attic(b):
    """Le grenier : des murs sur le plafond, un toit à faîte est-ouest, la porte et la potence."""
    w, d, m = b.w, b.d, b.m
    chimney = b.get(0, 4, d - 2)
    # On repart du plafond : l'ancien toit à deux pans (et le haut de la cheminée) disparaît.
    del b.layers[5:]
    knee = 2 if b.level == 2 else 3
    for y in range(5, 5 + knee):
        for z in range(d):
            for x in range(1, w + 1):
                edge = x in (1, w) or z in (0, d - 1)
                b.set(x, y, z, m["wall"] if edge else " ")
        for x in (1, w):
            for z in (0, d - 1):
                b.set(x, y, z, m["corner"])
        for x in range(4, w - 1, 3):
            b.set(x, y, 0, m["corner"])
            b.set(x, y, d - 1, m["corner"])
    # Des lucarnes au nord et au sud, au second rang du grenier.
    for x in range(3, w - 1, 3):
        b.set(x, 6, 0, m["window"])
        b.set(x, 6, d - 1, m["window"])
    mid = d // 2
    b.set(1, 6, mid, m["window"])
    # Le toit : bas et large, il déborde d'une colonne sur chaque pignon.
    fine = b.level == 4
    north, south = ("γ", "δ") if fine else ("α", "β")
    ridge = m["roof"][2]
    base = 5 + knee
    k = 0
    while k <= d - 1 - k:
        y = base + k
        zl, zr = k, d - 1 - k
        for x in range(0, w + 2):
            if zl == zr:
                b.set(x, y, zl, ridge)
            else:
                b.set(x, y, zl, north)
                b.set(x, y, zr, south)
        for z in range(zl + 1, zr):
            b.set(1, y, z, m["wall"])
            b.set(w, y, z, m["wall"])
            for x in range(2, w):
                b.set(x, y, z, " ")
        k += 1
    top = base + (d - 1) // 2
    # La cheminée traverse le pan, son feu au-dessus du faîte.
    for y in range(5, top + 2):
        b.set(0, y, d - 2, chimney)
    b.set(0, top + 2, d - 2, "*")
    # La porte de chargement, la poutre au-dessus, la chaîne et sa caisse au-dessus du quai.
    b.set(w, 5, mid, "ε")
    b.set(w, 6, mid, "ζ")
    b.fill(w, 7, mid, w + 3, 7, mid, "|")
    b.fill(w + 3, 4, mid, w + 3, 6, mid, "η")
    b.set(w + 3, 3, mid, "θ")


def quay(b):
    """Le quai, au ras du plancher, contre le mur est (x = b.w + 1 garde ses volets et son appui)."""
    w, d = b.w, b.d
    big = b.level >= 2
    z0, z1 = (0, d - 1) if big else (1, d - 2)
    edge = w + 4 if big else w + 3
    b.fill(w + 1, 0, z0, edge - 1, 0, z1, "f")
    b.fill(edge, 0, z0, edge, 0, z1, "κ")
    # Tonneaux couchés et caisses, en tas au nord du quai (vus de la rue).
    e = w + 2
    b.set(e, 1, z0, "θ")
    b.set(e, 2, z0, "ι")
    b.set(e + 1, 1, z0, "C")
    if big:
        b.set(e, 1, z0 + 1, "θ")
        b.set(e + 1, 2, z0, "θ")
        # Et au sud, une seconde pile.
        b.set(e, 1, z1, "C")
        b.set(e + 1, 1, z1, "ι")
        # Des bittes d'amarrage aux coins.
        b.set(edge, 1, z0, "E")
        b.set(edge, 1, z1, "E")
    if b.level >= 3:
        b.set(e, 2, z0 + 1, "C")
        b.set(e, 1, z1 - 1, "θ")
        b.set(e, 2, z1, "θ")
        b.set(edge, 2, z0, "]")
        b.set(edge, 2, z1, "]")
    if b.level >= 4:
        # Un auvent de dalles sur poteaux couvre le quai ; la chaîne passe par une trappe.
        mid = d // 2
        b.fill(e, 4, z0, edge, 4, z1, "S")
        b.set(w + 3, 4, mid, "η")
        for z in (z0, z1):
            b.set(edge, 2, z, "E")
            b.set(edge, 3, z, "E")
        b.set(edge, 3, mid, "J")
        b.set(e + 1, 1, z1 - 1, "C")
        b.set(e + 1, 1, z0 + 1, "ι")
