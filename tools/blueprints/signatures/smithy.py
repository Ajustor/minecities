"""
La forge : un appentis ouvert contre le flanc est, adossé à une grande cheminée de pierre (deux de large,
puis deux sur deux) qui dépasse le faîte et fume ; dessous, le billot, le tas de charbon et le bac de
trempe (de l'eau dans un bac de pierre fermé sur ses quatre côtés). Pas d'enclume ni de chaudron dehors :
ce sont des postes.

Le billot est une bûche non écorcée : debout et écorcée, ce serait le poste du bûcheron.
"""

KEY = {
    "α": "minecraft:coal_block",
    "β": "minecraft:smooth_stone",
    "γ": "minecraft:iron_chain[axis=y]",
}


def ridge(b):
    """Le plus haut bloc du bâtiment, cheminée de decorate (x = 0) mise à part."""
    return max(y for y in range(b.height) for z in range(b.depth) for x in range(1, b.width)
               if b.get(x, y, z) not in ". ")


def trough(b, x, z0, z1):
    """Le bac de trempe : l'eau de z0 à z1 en x, fermée de pierre lisse dessous et tout autour."""
    b.fill(x - 1, 0, z0 - 1, x + 1, 1, z1 + 1, "β")
    b.fill(x, 1, z0, x, 1, z1, "~")


def apply(b):
    level = b.level
    a, c = b.w + 2, b.w + 3              # sous l'appentis, contre le mur, puis au milieu
    top = ridge(b) + 2                   # le haut de la cheminée
    zc = b.d - 1                         # la cheminée, au bout sud de l'appentis
    deep = level >= 2                    # un appentis de trois de profondeur dès le niveau 2
    edge = b.w + 4 if deep else c        # la rangée des poteaux
    z0 = 0 if deep else 1

    # La cheminée : brute, puis de briques sur un pied de maçonnerie, et son feu au sommet.
    stone = "O" if level <= 2 else "K"
    chimney = [(a, zc), (c, zc)] + ([(a, zc + 1), (c, zc + 1)] if level >= 3 else [])
    for x, z in chimney:
        b.fill(x, 0, z, x, top, z, stone)
        if level >= 3:
            b.fill(x, 0, z, x, 1, z, "Z")
    b.set(a, top + 1, zc, "*")
    if level >= 4:
        b.set(c, top + 1, zc + 1, "*")
        # Un bandeau de maçonnerie sous le couronnement.
        for x, z in chimney:
            b.set(x, top - 1, z, "Z")

    # L'appentis : son toit qui prolonge le débord, les poteaux du bord.
    roof_hi, roof_lo = (">", ">") if level >= 2 else ("S", "S")
    for x in range(a, edge):
        b.fill(x, 4, z0, x, 4, zc - 1, roof_hi)
    b.fill(edge, 3, z0, edge, 3, zc - 1, roof_lo)
    for z in (z0, zc - 1):
        b.fill(edge, 1, z, edge, 2, z, "P")
    if level >= 3:
        # Le sol de l'appentis, pavé.
        b.fill(a, 0, z0, edge, 0, zc - 1, "O", only=".")

    # Dessous : le bac de trempe, le billot, le charbon.
    if deep:
        trough(b, c, 3, 3)
        b.set(c, 1, 1, "P")
        b.set(a, 1, zc - 1, "α")
        if level >= 3:
            b.set(c, 1, zc - 1, "α")
            b.set(a, 2, zc - 1, "α")
            # Une lanterne pendue sous le toit, devant la cheminée.
            b.set(a, 3, 1, "J")
        if level >= 4:
            # Des chaînes et des barreaux pendus au bord de l'appentis, comme un râtelier d'outils.
            b.set(edge, 2, 3, "γ")
            b.set(edge, 2, 4, "γ")
            b.set(edge, 1, 1, "I")
    else:
        trough(b, c, 3, 3)
        b.set(a, 1, 1, "P")
        b.set(a, 1, zc - 1, "α")
