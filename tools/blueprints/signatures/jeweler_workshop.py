"""
La joaillerie : une boutique petite et raffinée. Sa façade s'ouvre en vitrine à droite de la porte
(des vitres à la place du mur, la pièce reste close), sous un linteau de blackstone poli ; une
enseigne, une bannière jaune pendue à un poteau, au coin est. Dès le niveau 3, des présentoirs de
blackstone portent des grappes d'améthyste ; au niveau 4, une clé dorée au linteau et un lingot d'or
exposé. L'or reste rare : deux blocs au plus.
"""

KEY = {
    "α": "minecraft:polished_blackstone",
    "β": "minecraft:gilded_blackstone",
    "γ": "minecraft:yellow_wall_banner[facing=north]",
    "δ": "minecraft:amethyst_cluster[facing=up]",
}


def apply(b):
    m = b.m
    walls = {m["wall"], m["low"], m["window"]}
    # La vitrine : de la porte au coin est, sur deux rangs ; les poteaux restent en meneaux.
    bay = []
    for x in range(5, b.w):
        cell = b.get(x, 1, 0)
        if b.level >= 2 and cell == m["corner"]:
            continue
        bay.append(x)
        for y in (1, 2):
            if b.get(x, y, 0) in walls:
                b.set(x, y, 0, "G")
    if b.level >= 2:
        # Le linteau de blackstone poli au-dessus de la vitrine ; dès le niveau 3, le seuil aussi.
        for x in bay:
            b.set(x, 3, 0, "α")
            if b.level >= 3:
                b.set(x, 0, 0, "α")
        key = bay[len(bay) // 2]
        if b.level >= 3:
            b.set(key, 3, 0, "$" if b.level == 4 else "β")
    # L'enseigne : un poteau au coin est, une bannière jaune à sa face nord.
    x0 = b.width
    post = "α" if b.level >= 3 else "P"
    b.fill(x0, 1, 1, x0, 3, 1, post)
    b.set(x0, 3, 0, "γ")
    if b.level >= 2:
        b.set(x0, 4, 1, "]")
    if b.level >= 3:
        # Les présentoirs : des socles de blackstone, des améthystes dessus.
        for x in (x0 + 1, x0 + 3):
            b.set(x, 1, 1, "α")
            b.set(x, 2, 1, "δ")
    if b.level == 4:
        # Entre les deux, un bloc d'or sur un socle doré.
        b.set(x0 + 2, 1, 1, "β")
        b.set(x0 + 2, 2, 1, "$")
