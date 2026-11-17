"""
La taillerie de pierre : une cour de pierre contre le mur est, des blocs taillés posés au sol, et une
chèvre de bois (un mât, une flèche qui revient vers la cour, une chaîne et un bloc suspendu) dont la
silhouette en potence se lit depuis la rue.

Le mât et la flèche sont en épicéa non écorcé : une bûche écorcée isolée serait un billot (le poste
du bûcheron), et le bûcheron n'abat que des troncs posés sur la terre, dans des feuilles.
"""

KEY = {
    "α": "minecraft:spruce_log[axis=y]",
    "β": "minecraft:spruce_log[axis=x]",
    # La contrefiche, sous la flèche, contre le mât.
    "γ": "minecraft:spruce_stairs[facing=east,half=top,shape=straight]",
    "δ": "minecraft:iron_chain[axis=y]",
    # Les blocs taillés, et le muret de la cour dès le niveau 3.
    "ε": "minecraft:smooth_stone",
    "ζ": "minecraft:stone_brick_wall",
    "η": "minecraft:smooth_stone_slab[type=bottom]",
    "θ": "minecraft:cobblestone",
}


def apply(b):
    e = b.w + 2              # la première colonne libre à l'est
    mast = e + 4
    zc = 3                   # la chèvre, au milieu du flanc
    top = 6 if b.level == 1 else 7
    # La cour : dallée dès le niveau 2 (pierre brute, puis la pierre fine du style).
    if b.level >= 2:
        b.fill(e, 0, 0, mast, 0, b.d - 1, "Q" if b.level >= 3 else "F")
    b.set(mast, 0, zc, "Q" if b.level >= 3 else "F")
    # La chèvre : le mât, la flèche vers l'ouest, la contrefiche, la chaîne et le bloc suspendu.
    b.fill(mast, 1, zc, mast, top - 1, zc, "α")
    b.fill(e + 1, top, zc, mast, top, zc, "β")
    b.set(mast - 1, top - 1, zc, "γ")
    b.fill(e + 1, 3, zc, e + 1, top - 1, zc, "δ")
    b.set(e + 1, 2, zc, "0")
    # Les blocs taillés posés au sol, devant (vus de la rue), et un tas de pierre brute au fond.
    b.set(e + 2, 1, 0, "0")
    b.set(e + 3, 1, 0, "X")
    b.set(e + 3, 2, 0, "η")
    b.set(e + 2, 1, 1, "ε")
    b.fill(e, 1, b.d - 2, e + 1, 1, b.d - 1, "θ")
    b.set(e, 2, b.d - 1, "θ")
    if b.level >= 2:
        b.set(e, 1, 1, "Z")
        b.set(e, 2, 1, "η")
        b.set(e + 3, 1, b.d - 1, "0")
        b.set(e + 3, 2, b.d - 1, "0")
        b.set(e + 2, 1, b.d - 1, "X")
    if b.level >= 3:
        # Le muret de la cour, au bord est, et un bloc ciselé.
        b.fill(mast + 1, 1, 0, mast + 1, 1, b.d - 1, "ζ")
        b.set(e + 4, 1, 0, ",")
        b.set(e + 4, 1, 1, "ε")
    if b.level >= 4:
        # Une lanterne au bout de la flèche, et des dalles polies prêtes à partir.
        b.set(e + 2, top - 1, zc, "J")
        b.fill(e + 3, 1, 5, e + 3, 1, 5, "η")
        b.set(e + 2, 2, 1, "η")
