"""
Le marché : des étals sous des auvents de laine rayée, contre le flanc est ; dès le niveau 2, une halle
ouverte les couvre (poteaux et toit à deux pans, sans murs), une allée au milieu. Un étal de plus à
chaque niveau ; au niveau 4, des fanions sur le faîte de la halle.

La halle est ouverte de tous côtés : elle n'est pas une pièce, et ne touche pas à celle du marchand.
"""

KEY = {
    # Les auvents : une couleur par étal, rayée de blanc.
    "α": "minecraft:red_wool",
    "β": "minecraft:yellow_wool",
    "γ": "minecraft:blue_wool",
    "δ": "minecraft:green_wool",
    # Les comptoirs : un tonneau couché et une planche.
    "ε": "minecraft:barrel[facing=east]",
    "ζ": "minecraft:barrel[facing=west]",
    # Le toit de la demeure (niveau 4) couvre aussi la halle.
    # Les fanions sur le faîte.
    "η": "minecraft:red_banner[rotation=8]",
    "θ": "minecraft:yellow_banner[rotation=8]",
}

# Les étals : (côté, première rangée, couleur, marchandises sur la planche et sur le tonneau).
STALLS = [
    ("west", 1, "α", "7", "6"),
    ("west", 4, "β", "5", "2"),
    ("east", 1, "γ", "6", "+"),
    ("east", 4, "δ", "2", "7"),
]


def apply(b):
    w, d = b.w, b.d
    west = w + 2         # la rangée ouest des poteaux de la halle
    east = w + 8         # la rangée est
    posts = (0, (d - 1) // 2, d - 1)
    if b.level >= 2:
        hall(b, west, east, posts)
    else:
        # Une seule échoppe : deux piquets de barrière portent son auvent.
        for z in (0, 3):
            b.fill(west, 1, z, west, 3, z, "E")
    for side, z0, wool, top, cask in STALLS[:b.level]:
        stall(b, west if side == "west" else east, 1 if side == "west" else -1, z0, wool, top, cask)


def hall(b, west, east, posts):
    """La halle : six poteaux, un sol pavé dès le niveau 3, un toit à deux pans nord-sud ouvert aux pignons."""
    m = b.m
    for x in (west, east):
        for z in posts:
            b.fill(x, 1, z, x, 3, z, "P")
    if b.level >= 3:
        b.fill(west, 0, 0, east, 0, b.d - 1, "Q" if b.level == 4 else "F")
    left, right, ridge = m["roof"]
    k = 0
    while west + k <= east - k:
        y = 4 + k
        if west + k == east - k:
            b.fill(west + k, y, 0, west + k, y, b.d - 1, ridge)
        else:
            b.fill(west + k, y, 0, west + k, y, b.d - 1, left)
            b.fill(east - k, y, 0, east - k, y, b.d - 1, right)
        k += 1
    mid = (west + east) // 2
    top = 4 + (east - west) // 2
    if b.level >= 3:
        # Des lanternes pendues sous le faîte.
        for z in (1, b.d - 2):
            b.set(mid, top - 1, z, "J")
    if b.level >= 4:
        # Des fanions aux deux bouts du faîte.
        b.set(mid, top + 1, 0, "η")
        b.set(mid, top + 1, b.d - 1, "θ")


def stall(b, post, step, z0, wool, top, cask):
    """
    Un étal sur deux rangées (z0, z0 + 1), entre deux poteaux : le comptoir juste dans la halle, et
    au-dessus un auvent rayé de trois blocs de large qui avance sur l'allée.
    """
    counter = post + step
    b.set(counter, 1, z0, "ε" if step > 0 else "ζ")
    b.set(counter, 1, z0 + 1, ":")
    b.set(counter, 2, z0, cask)
    b.set(counter, 2, z0 + 1, top)
    for i in range(3):
        x = post + i * step
        for z in (z0, z0 + 1):
            b.set(x, 3, z, wool if (i + z - z0) % 2 == 0 else "9")
