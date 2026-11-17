"""
L'atelier de l'inventeur : une tourelle étroite au coin nord-est, plus haute que le faîte, coiffée d'un
paratonnerre de cuivre ; dès le niveau 3, des lampes de redstone (éteintes) en façade, une couronne de
grilles de cuivre et des ampoules de cuivre allumées, puis un rouage (cuivre ciselé) au niveau 4.

Les lampes de redstone allumées s'éteignent à la première mise à jour sans courant : elles restent ici
éteintes, comme des appareils au repos. La lumière vient des ampoules de cuivre, qui gardent leur état
sans courant. Le cuivre est ciré, pour ne pas verdir.
"""

KEY = {
    "α": "minecraft:lightning_rod[facing=up]",
    "β": "minecraft:redstone_lamp[lit=false]",
    "γ": "minecraft:waxed_copper_grate",
    "δ": "minecraft:waxed_copper_bulb[lit=true,powered=false]",
    "ε": "minecraft:waxed_copper_block",
    "ζ": "minecraft:waxed_chiseled_copper",
    "η": "minecraft:waxed_cut_copper_slab[type=bottom]",
}


def ridge(b):
    """Le plus haut bloc du bâtiment, cheminée (x = 0) mise à part."""
    return max(y for y in range(b.height) for z in range(b.depth) for x in range(1, b.width)
               if b.get(x, y, z) not in ". ")


def apply(b):
    level = b.level
    a = b.w + 2                      # la tourelle : x = a, a + 1 ; z = 0, 1
    top = ridge(b) + (3 if level == 1 else 1)   # le dernier rang de la tourelle
    body = "Z" if level >= 3 else "P"
    cells = [(a, 0), (a + 1, 0), (a, 1), (a + 1, 1)]

    for x, z in cells:
        b.set(x, 0, z, "O")
        b.fill(x, 1, z, x, top, z, body)
    if level >= 3:
        # Des chaînages de briques, et le pied de la tourelle.
        for x, z in cells:
            b.set(x, 1, z, "K")
            b.set(x, top - 3, z, "K")

    if level == 1:
        # Un mât de fortune, et une lanterne au sommet.
        b.set(a, top + 1, 0, "E")
        b.set(a, top + 2, 0, "α")
        b.set(a + 1, top + 1, 1, "]")
        b.set(a, 3, 0, "I")
        return

    # Une grille en façade, puis (la pierre lumineuse vient du Nether) des lampes de redstone.
    b.set(a, 3, 0, "β" if level >= 3 else "I")
    if level >= 3:
        b.set(a + 1, 5, 0, "β")
        # La couronne de grilles, les ampoules allumées, le chapeau de cuivre et le paratonnerre.
        for x, z in cells:
            b.set(x, top, z, "γ")
            b.set(x, top + 1, z, "η")
        b.set(a, top - 1, 0, "δ")
        b.set(a + 1, top - 1, 0, "δ")
        b.set(a, top + 1, 0, "ε")
        b.set(a, top + 2, 0, "α")
    else:
        b.set(a, top + 1, 0, "α")
    if level >= 4:
        # Le rouage en façade, des ampoules sur le flanc est, un second paratonnerre.
        b.set(a, 7, 0, "ζ")
        b.set(a + 1, 7, 0, "γ")
        b.set(a + 1, top - 1, 1, "δ")
        b.set(a + 1, top + 1, 1, "ε")
        b.set(a + 1, top + 2, 1, "α")
        # Un bloc de cuivre au pied, comme une chaudière.
        b.set(a + 2, 1, 0, "ε")
        b.set(a + 2, 2, 0, "γ")
