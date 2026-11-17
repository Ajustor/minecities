"""
L'infirmerie : une croix rouge sur fond blanc, qu'on voit de loin. Sous le toit plat de la cabane,
c'est un panneau dressé au-dessus de la porte ; dès que le toit a deux pans, c'est le pignon de façade
lui-même, blanchi, qui la porte. À l'est, un jardin d'herbes médicinales clos de barrières (fougères,
muguet, camomille, bleuets). Dès le niveau 3, un bandeau de calcite blanchit le haut de la façade,
puis des côtés au niveau 4, où un banc attend les convalescents.
"""

KEY = {
    "α": "minecraft:red_concrete",
    "β": "minecraft:white_concrete",
    "γ": "minecraft:calcite",
    "δ": "minecraft:grass_block",
    "ε": "minecraft:fern",
    "ζ": "minecraft:lily_of_the_valley",
    "η": "minecraft:oxeye_daisy",
    "θ": "minecraft:azure_bluet",
    "ι": "minecraft:potted_fern",
}

HERBS = "εζηθ"
# Le jardin (barrières comprises).
GARDEN = {1: (4, 4), 2: (5, 5), 3: (6, 6), 4: (7, 7)}


def cross(b, cx, cy, z, background):
    """Une croix rouge aux bras d'un bloc autour de (cx, cy) ; les cases données passent en blanc."""
    for x, y in background:
        b.set(x, y, z, "β")
    for x, y in ((cx, cy), (cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)):
        b.set(x, y, z, "α")


def apply(b):
    m = b.m
    if m["roof"] is None:
        # La cabane : un panneau de 3 x 3 au-dessus de la porte, posé sur le toit.
        cross(b, 4, 6, 0, [(x, y) for x in (3, 4, 5) for y in (5, 6, 7)])
    else:
        # Le pignon de façade blanchi, la croix au milieu (à la place de la lucarne).
        cx = b.width // 2
        gable = [(x, y) for y in range(6, b.height) for x in range(b.width)
                 if b.get(x, y, 0) in (m["wall"], "G")]
        cy = 8 if b.level == 4 else 7
        cross(b, cx, cy, 0, gable)
    if b.level >= 3:
        # Un bandeau de calcite en haut de la façade, puis des côtés.
        for x in range(2, b.w):
            if b.get(x, 3, 0) == m["wall"]:
                b.set(x, 3, 0, "γ")
        if b.level == 4:
            for x in (1, b.w):
                for z in range(1, b.d - 1):
                    if b.get(x, 3, z) == m["wall"]:
                        b.set(x, 3, z, "γ")
    # Le jardin d'herbes médicinales, à l'est.
    gw, gd = GARDEN[b.level]
    x0 = b.width
    gate = gw // 2
    for dx in range(gw):
        for dz in range(gd):
            x = x0 + dx
            if dx in (0, gw - 1) or dz in (0, gd - 1):
                b.set(x, 1, dz, "E")
                continue
            b.set(x, 0, dz, "δ")
            if b.level >= 3 and dx == gate:
                # Une allée au milieu des plates-bandes.
                b.set(x, 0, dz, "_")
                b.set(x, 1, dz, " ")
            elif b.level == 4 and dz == gd - 2 and dx in (1, 2):
                b.set(x, 1, dz, "^")  # le banc, dos au sud
            else:
                b.set(x, 1, dz, HERBS[(dx + 2 * dz) % len(HERBS)])
    b.set(x0 + gate, 1, 0, "g")
    if b.level >= 2:
        # Des fougères en pot sur les poteaux, de part et d'autre du portillon.
        b.set(x0, 2, 0, "ι")
        b.set(x0 + gw - 1, 2, 0, "ι")
    if b.level >= 3:
        b.set(x0, 2, gd - 1, "]")
        b.set(x0 + gw - 1, 2, gd - 1, "]")
