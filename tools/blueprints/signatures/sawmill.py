"""
La scierie : un appentis ouvert sur poteaux contre le mur est, des grumes couchées empilées dessous et
devant (le bout des bûches vu de la rue), et dès le niveau 3 une roue à aubes au bout de l'appentis.

Les grumes sont des bûches vanilla non écorcées, couchées : une bûche écorcée isolée serait un billot
(le poste du bûcheron), et le bûcheron n'abat que des troncs posés sur la terre, dans des feuilles.
"""

KEY = {
    # Les grumes, couchées nord-sud (on voit leurs cernes depuis la rue) ou est-ouest.
    "α": "minecraft:spruce_log[axis=z]",
    "β": "minecraft:oak_log[axis=z]",
    "γ": "minecraft:oak_log[axis=x]",
    # La roue à aubes : jante de planches, rayons de barrière, moyeu et arbre en bûche, aubes en trappes.
    "δ": "minecraft:spruce_planks",
    "ε": "minecraft:spruce_fence",
    "ζ": "minecraft:spruce_trapdoor[facing=east,half=bottom,open=true]",
    # Le bord de l'appentis, dès le niveau 2 : des marches qui ourlent le toit.
    "η": "{roof_stairs}[facing=west,half=bottom,shape=straight]",
    # Les planches débitées, dès le niveau 4.
    "θ": "minecraft:oak_slab[type=bottom]",
}


def apply(b):
    e = b.w + 2          # la première colonne libre à l'est (x = b.w + 1 : volets et débord)
    post = e + 2         # la rangée de poteaux
    # L'appentis couvre l'arrière du flanc est (z 2 à 6) ; le devant reste à ciel ouvert, pour les grumes.
    b.fill(e, 4, 2, post, 4, b.d - 1, "S")
    for z in (2, b.d - 1) + ((4,) if b.level >= 2 else ()):
        b.fill(post, 1, z, post, 3, z, "P")
    if b.level >= 2:
        # Le bord est du toit ourlé de marches, un cran plus bas que les dalles.
        b.fill(post + 1, 4, 2, post + 1, 4, b.d - 1, "η")
    # Sous l'appentis : une pile de grumes couchées contre le mur.
    b.fill(e, 1, 3, e + 1, 1, 5, "α")
    b.fill(e, 2, 3, e, 2, 5, "α")
    if b.level >= 2:
        b.fill(e + 1, 2, 4, e + 1, 2, 5, "β")
        b.fill(e, 3, 4, e, 3, 5, "β")
    # Devant, à ciel ouvert : des grumes en pyramide, le bout vers la rue.
    b.fill(e, 1, 0, post, 1, 1, "β")
    b.fill(e, 2, 0, e + 1, 2, 1, "α")
    if b.level >= 2:
        b.set(e, 3, 0, "β")
        b.set(e, 3, 1, "β")
    if b.level >= 3:
        wheel(b, post + 2, 4)
    if b.level >= 4:
        # Des planches débitées sous l'appentis, et une lanterne pendue aux dalles.
        b.fill(e + 1, 1, 2, e + 1, 1, 2, "θ")
        b.set(post - 1, 1, b.d - 1, "f")
        b.set(post - 1, 2, b.d - 1, "θ")
        b.set(e + 1, 3, 2, "J")


def wheel(b, x, zc):
    """La roue à aubes, dans le plan nord-sud, centrée en (x, 2, zc), et son arbre jusqu'au poteau."""
    for dz in (-1, 0, 1):
        b.set(x, 0, zc + dz, "δ")
        b.set(x, 4, zc + dz, "δ")
    for dy in (1, 2, 3):
        b.set(x, dy, zc - 2, "δ")
        b.set(x, dy, zc + 2, "δ")
    b.set(x, 2, zc, "γ")
    b.set(x - 1, 2, zc, "γ")
    for dz, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        b.set(x, 2 + dy, zc + dz, "ε")
    if b.level >= 4:
        # Les aubes, sur la jante.
        for z in (zc - 1, zc + 1):
            b.set(x + 1, 0, z, "ζ")
            b.set(x + 1, 4, z, "ζ")
        for y in (1, 3):
            b.set(x + 1, y, zc - 2, "ζ")
            b.set(x + 1, y, zc + 2, "ζ")
