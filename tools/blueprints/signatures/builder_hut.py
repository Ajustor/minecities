"""
La cabane du bâtisseur : un échafaudage dressé contre le flanc est, à l'angle de la façade (des
piquets de barrière et des planchers de dalles, un étage de plus à chaque niveau jusqu'à dépasser
l'égout du toit), une échelle au mur, et un tas de matériaux au pied : pierre, planches, briques.

Pas de vrai bloc d'échafaudage : il tiendrait (posé de bas en haut sur du plein), mais il se fait au
bambou, que la ville ne produit pas ; des barrières et des dalles disent la même chose.
"""

KEY = {
    "α": "minecraft:ladder[facing=east]",
    # Le tas de matériaux.
    "β": "minecraft:cobblestone",
    "γ": "minecraft:oak_planks",
    "δ": "minecraft:bricks",
    "ε": "minecraft:oak_slab[type=bottom]",
    "ζ": "minecraft:brick_slab[type=bottom]",
}


def apply(b):
    e = b.w + 2                      # la première colonne libre à l'est
    stages = {1: 1, 2: 2, 3: 3, 4: 3}[b.level]
    # L'échafaudage : deux bloc de profondeur (x), trois de large (z), à l'angle nord-est.
    for stage in range(stages):
        y = 1 + 3 * stage
        for x in (e, e + 1):
            for z in (0, 2):
                b.fill(x, y, z, x, y + 1, z, "E")
        b.fill(e, y + 2, 0, e + 1, y + 2, 2, "S")
    if b.level >= 4:
        # Un garde-corps sur le dernier plancher.
        b.fill(e + 1, 1 + 3 * stages, 0, e + 1, 1 + 3 * stages, 2, "E")
    # L'échelle, contre le mur, jusqu'au premier plancher.
    b.fill(b.w + 1, 1, 1, b.w + 1, 3, 1, "α")
    # Le tas de matériaux, au pied de l'échafaudage.
    b.set(e, 1, 4, "β")
    b.set(e + 1, 1, 4, "δ")
    b.set(e, 1, 5, "γ")
    b.set(e, 2, 5, "ε")
    b.set(e + 1, 1, 5, "β")
    b.set(e, 1, 6, "δ")
    if b.level >= 2:
        b.set(e, 2, 4, "β")
        b.set(e + 1, 2, 4, "ζ")
        b.set(e + 2, 1, 5, "δ")
        b.set(e + 2, 2, 5, "δ")
        b.set(e + 1, 1, 6, "γ")
        b.set(e + 1, 2, 6, "ε")
    if b.level >= 3:
        b.set(e + 2, 1, 4, "0")
        b.set(e + 2, 1, 6, "0")
        b.set(e + 2, 2, 6, "X")
        b.set(e + 3, 1, 5, "γ")
        b.set(e + 3, 2, 5, "ε")
    if b.level >= 4:
        # Une lanterne sous le premier plancher.
        b.set(e + 1, 2, 1, "J")
