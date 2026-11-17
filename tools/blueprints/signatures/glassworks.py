"""
La verrerie : un four-coupole de briques contre le flanc est, sa gueule rougeoyante tournée vers la
rue (un feu de camp au fond d'une niche) et sa haute cheminée qui dépasse le faîte ; et beaucoup de
verre en façade : les murs pleins de la façade deviennent des vitres (jamais de trou), avec une rangée
de vitraux de couleur en imposte dès le niveau 3.

La plaque est accrochée au mur de façade en x = 3, y = 2 : ce bloc-là reste plein.
"""

KEY = {
    "α": "minecraft:bricks",
    "β": "minecraft:brick_slab[type=bottom]",
    "γ": "minecraft:brick_stairs[facing=north,half=bottom,shape=straight]",
    "δ": "minecraft:brick_stairs[facing=south,half=bottom,shape=straight]",
    "ε": "minecraft:brick_stairs[facing=west,half=bottom,shape=straight]",
    "ζ": "minecraft:brick_stairs[facing=east,half=bottom,shape=straight]",
    "η": "minecraft:glass_pane",
    # Les vitraux de l'imposte, et le verre de couleur exposé devant le four.
    "θ": "minecraft:orange_stained_glass_pane",
    "ι": "minecraft:light_blue_stained_glass_pane",
    "κ": "minecraft:yellow_stained_glass_pane",
    "λ": "minecraft:light_blue_stained_glass",
    "μ": "minecraft:orange_stained_glass",
}

# Les blocs de mur qu'on peut vitrer (pas les poteaux ni les chaînes d'angle des niveaux 2 à 4).
WALLS = {1: "P", 2: "W", 3: "W", 4: "W"}


def apply(b):
    glaze(b)
    kiln(b, b.w + 2)


def glaze(b):
    """La façade vitrée : une fenêtre de chaque côté au niveau 1, puis toute la rangée du milieu, puis l'imposte."""
    wall = WALLS[b.level]
    pane = {1: "η", 2: "G", 3: "η", 4: "H"}[b.level]
    if b.level == 1:
        for x in (2, b.w - 1):
            b.set(x, 2, 0, pane)
        return
    for x in range(2, b.w):
        # Pas la porte (x = 4), ni le mur qui porte la plaque (x = 3, y = 2) ; au niveau 3, les
        # barreaux des fenêtres d'origine deviennent eux aussi des vitres.
        if x not in (3, 4) and b.get(x, 2, 0) in (wall, "I"):
            b.set(x, 2, 0, pane)
    # L'imposte, au-dessus (le linteau de la porte reste plein) : verre clair, puis vitraux.
    colours = "θικ" if b.level >= 3 else pane
    for i, x in enumerate(range(2, b.w)):
        if x != 4 and b.get(x, 3, 0) == wall:
            b.set(x, 3, 0, colours[i % len(colours)])


def kiln(b, e):
    """Le four-coupole (3 x 3, la gueule au nord) et sa cheminée, dans la colonne du milieu."""
    cx, cz = e + 1, 1
    # Le pied : un anneau de briques, la gueule ouverte au nord, le feu au fond.
    b.fill(e, 1, 0, e + 2, 1, 2, "α")
    b.set(cx, 1, 0, " ")
    b.set(cx, 1, cz, "*")
    # La voûte : des briques sur les côtés, des dalles aux coins, puis une couronne de marches.
    b.fill(e, 2, 0, e + 2, 2, 2, "α")
    for x in (e, e + 2):
        for z in (0, 2):
            b.set(x, 2, z, "β")
    b.set(cx, 3, 0, "δ")
    b.set(cx, 3, 2, "γ")
    b.set(e, 3, cz, "ζ")
    b.set(e + 2, 3, cz, "ε")
    # La cheminée, au-dessus du faîte, et son feu.
    top = b.height if b.level == 1 else b.height - 1
    b.fill(cx, 3, cz, cx, top, cz, "α")
    b.set(cx, top + 1, cz, "*")
    if b.level >= 3:
        # Du verre de couleur exposé devant le four, sur des piquets.
        b.set(e + 3, 1, 0, "E")
        b.set(e + 3, 2, 0, "λ")
    if b.level >= 4:
        b.set(e + 3, 1, 2, "E")
        b.set(e + 3, 2, 2, "μ")
        # Une couronne de barreaux en haut de la cheminée.
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            b.set(cx + dx, top, cz + dz, "I")
