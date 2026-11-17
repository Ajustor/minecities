"""
Le rucher : un jardin de fleurs clos de barrières à l'est, où se dressent des ruchettes (des blocs de
rayon de miel coiffés d'une dalle, sans vraie ruche : la ruche est le poste du rucher, elle reste
dedans). Le jardin grandit avec le niveau ; les ruchettes s'élèvent en hausses, une allée de terre
battue mène du portillon au fond, des tournesols et des lanternes viennent à la fin.
"""

KEY = {
    "α": "minecraft:grass_block",
    "β": "minecraft:honeycomb_block",
    "γ": "minecraft:dandelion",
    "δ": "minecraft:poppy",
    "ε": "minecraft:cornflower",
    "ζ": "minecraft:allium",
    "η": "minecraft:oxeye_daisy",
    "θ": "minecraft:azure_bluet",
    "ι": "minecraft:sunflower[half=lower]",
    "κ": "minecraft:sunflower[half=upper]",
    "λ": "minecraft:honey_block",
    "μ": "minecraft:dirt_path",
}

FLOWERS = "γδεζηθ"

# Le jardin (barrières comprises) : largeur à l'est, profondeur vers le sud.
GARDEN = {1: (5, 5), 2: (6, 6), 3: (7, 7), 4: (7, 8)}
# Les ruchettes, en cases du jardin (barrières comprises).
HIVES = {1: [(2, 2)],
         2: [(1, 2), (4, 4)],
         3: [(1, 2), (5, 2), (1, 5), (5, 5)],
         4: [(1, 2), (5, 2), (1, 5), (5, 5)]}


def apply(b):
    gw, gd = GARDEN[b.level]
    x0 = b.width  # au-delà du débord de toit est
    gate = gw // 2
    # Le clos, et son portillon au nord.
    for dx in range(gw):
        for dz in range(gd):
            if dx in (0, gw - 1) or dz in (0, gd - 1):
                b.set(x0 + dx, 1, dz, "E")
    b.set(x0 + gate, 1, 0, "g")
    # Le sol : de l'herbe fleurie, une allée de terre battue dès le niveau 2.
    hives = set(HIVES[b.level])
    for dx in range(1, gw - 1):
        for dz in range(1, gd - 1):
            x = x0 + dx
            if b.level >= 2 and dx == gate:
                b.set(x, 0, dz, "μ")
                b.set(x, 1, dz, " ")
                continue
            b.set(x, 0, dz, "α")
            if (dx, dz) in hives:
                continue
            if b.level == 4 and dz == gd - 2:
                # Une rangée de tournesols au fond, qui dépassent du clos.
                b.set(x, 1, dz, "ι")
                b.set(x, 2, dz, "κ")
            else:
                b.set(x, 1, dz, FLOWERS[(dx * 2 + dz) % len(FLOWERS)])
    # Les ruchettes : un rayon de miel coiffé d'une dalle ; deux hausses dès le niveau 3, la plus
    # haute en miel au niveau 4.
    for dx, dz in hives:
        x = x0 + dx
        b.set(x, 1, dz, "β")
        top = 1
        if b.level >= 3:
            b.set(x, 2, dz, "λ" if b.level == 4 and dz == 2 else "β")
            top = 2
        b.set(x, top + 1, dz, "S")
    # Des lanternes sur les poteaux d'angle du clos, côté rue (niveau 3 et plus).
    if b.level >= 3:
        b.set(x0, 2, 0, "]")
        b.set(x0 + gw - 1, 2, 0, "]")
    if b.level == 4:
        b.set(x0, 2, gd - 1, "]")
        b.set(x0 + gw - 1, 2, gd - 1, "]")
