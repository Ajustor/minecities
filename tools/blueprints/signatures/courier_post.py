"""
Le relais du livreur : une boîte aux lettres (un tonneau couché sur un piquet) au coin de la façade,
près de la porte, une barre d'attache pour les montures le long du flanc est, et un drapeau. Dès le
niveau 2, un pigeonnier se dresse sur le faîte, côté rue, le drapeau planté dessus.

Le pigeonnier est une petite tour pleine posée sur le toit, fermée : elle ne touche pas à la pièce.
"""

KEY = {
    # La boîte aux lettres : la bonde tournée vers la rue.
    "α": "minecraft:barrel[facing=north]",
    # Le drapeau du relais.
    "β": "minecraft:blue_banner[rotation=8]",
    "γ": "minecraft:blue_wall_banner[facing=east]",
    # Les perchoirs devant les trous du pigeonnier.
    "δ": "{trapdoor}[facing=north,half=bottom,open=false]",
    # Le chapeau du pigeonnier.
    "ε": "{roof_stairs}[facing=south,half=bottom,shape=straight]",
    "ζ": "{roof_stairs}[facing=north,half=bottom,shape=straight]",
    "η": "{roof_stairs}[facing=east,half=bottom,shape=straight]",
    "θ": "{roof_stairs}[facing=west,half=bottom,shape=straight]",
}


def apply(b):
    w, d = b.w, b.d
    # La boîte aux lettres, au coin nord-ouest, à trois pas de la porte.
    b.set(0, 1, 0, "E")
    b.set(0, 2, 0, "α")
    # La barre d'attache : une lisse de barrière le long du flanc est, un abreuvoir de dalles au bout.
    rail = w + 3
    last = 4 if b.level == 1 else d - 2
    b.fill(rail, 1, 1, rail, 1, last, "E")
    if b.level >= 2:
        b.set(rail, 2, 1, "E")
        b.set(rail, 2, last, "E")
    if b.level >= 3:
        # Une lanterne sur le premier poteau, des colis (tonneaux, coffre) au pied du mur.
        b.set(rail, 3, 1, "]")
        b.set(w + 2, 1, d - 2, "y")
        b.set(w + 2, 1, d - 3, "C")
        b.set(w + 2, 2, d - 2, "2")
    if b.level >= 4:
        # Une bannière accrochée au mur est, côté rue.
        b.set(w + 1, 3, 1, "γ")
        # Et une lanterne sur la boîte aux lettres.
        b.set(0, 3, 0, "]")
    if b.level == 1:
        # Sur le toit plat : un mât et le drapeau.
        x = (w + 1) // 2 + 1
        b.fill(x, 5, 1, x, 6, 1, "E")
        b.set(x, 7, 1, "β")
        return
    dovecote(b)


def dovecote(b):
    """Le pigeonnier : 3 x 3 sur le faîte, deux rangs de haut, des trous et leurs perchoirs, un chapeau."""
    w = b.w
    c = (w + 2) // 2
    ridge = max(y for y in range(b.height) if b.get(c, y, 1) in "RY")
    z0, z1 = 1, 3
    base = ridge + 1
    for y in (base, base + 1):
        for z in range(z0, z1 + 1):
            for x in range(c - 1, c + 2):
                edge = x != c or z != 2
                b.set(x, y, z, "W" if edge else " ")
        for x, z in ((c - 1, z0), (c + 1, z0), (c - 1, z1), (c + 1, z1)):
            b.set(x, y, z, "P")
    # Le pied du pigeonnier descend jusqu'aux pans du toit.
    for x in (c - 1, c + 1):
        for z in range(z0, z1 + 1):
            b.set(x, ridge, z, "W" if z == 2 else "P")
    # Des trous ronds au nord (et à l'est dès le niveau 3), un perchoir devant chacun.
    b.set(c, base + 1, z0, " ")
    b.set(c, base + 1, z0 - 1, "δ")
    if b.level >= 3:
        b.set(c + 1, base + 1, 2, " ")
    # Le chapeau en pyramide, et le drapeau au sommet.
    cap = base + 2
    for x in range(c - 1, c + 2):
        b.set(x, cap, z0, "ε")
        b.set(x, cap, z1, "ζ")
    b.set(c - 1, cap, 2, "η")
    b.set(c + 1, cap, 2, "θ")
    b.set(c, cap, 2, b.m["roof"][2])
    b.set(c, cap + 1, 2, "β")
