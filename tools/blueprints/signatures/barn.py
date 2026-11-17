"""
La grange : sur la façade, les grands vantaux d'une porte de grange dessinés autour de la porte (planches
du toit, linteau de rondin) ; une meule de foin au coin nord-ouest ; et dès le niveau 2 un grand toit en
mansarde (le pan raide puis le pan doux) dont le pignon nord ouvre une fenêtre de fenil pleine de foin.

L'enclos (pen, dans generate.py) se colle à l'est : on ne grandit pas de ce côté. Aucune botte de foin ne
touche la pièce (la botte du poste reste seule dans l'atelier) ; dehors, chacune a du plein au-dessus.
"""

KEY = {
    "α": "minecraft:hay_block[axis=z]",
    "β": "minecraft:lightning_rod[facing=up]",
}


def apply(b):
    door(b)
    stack(b)
    if b.level >= 2:
        gambrel(b)


def door(b):
    """Les vantaux de la grande porte, de part et d'autre de la vraie (x = 4), sous un linteau."""
    for x in (3, 5):
        for y in (1, 2):
            b.set(x, y, 0, "R")
    for x in (3, 4, 5):
        b.set(x, 3, 0, "|")


def stack(b):
    """La meule, au coin nord-ouest, dehors : deux bottes couchées, une troisième dessus, coiffée."""
    b.set(0, 1, 0, "α")
    b.set(0, 1, 1, "α")
    if b.level >= 3:
        b.set(0, 2, 0, "α")
        b.set(0, 3, 0, "S")


def profile(b):
    """Le toit en mansarde : pour chaque colonne x de la pièce, les cases (y, caractère) du toit."""
    east, west, ridge = b.m["roof"]
    center = (b.w + 1) // 2
    cells = {}
    for x in range(1, b.w + 1):
        i = min(x - 1, b.w - x)            # la distance au bord le plus proche
        stair = east if x < center else west
        if x == center:
            j = i - 2
            cells[x] = [(9 + j // 2, ridge)]          # le faîte
        elif i < 2:
            # Le pan raide : deux marches l'une sur l'autre par colonne.
            cells[x] = [(5 + 2 * i, stair), (6 + 2 * i, stair)]
        else:
            # Le pan doux : une marche, puis un bloc plein à la même hauteur, un cran plus haut, etc.
            j = i - 2
            cells[x] = [(9 + j // 2, stair if j % 2 == 0 else ridge)]
    return cells, center


def gambrel(b):
    cells, center = profile(b)
    top = max(y for column in cells.values() for y, _ in column)
    # Le feu de la cheminée de decorate monte s'il le faut au-dessus du nouveau faîte.
    fire = b.find("*")
    for x in range(1, b.w + 1):
        for z in range(0, b.d + 1):
            for y in range(5, b.height):
                if b.get(x, y, z) != ".":
                    b.set(x, y, z, "." if z == b.d else " ")
    for x, column in cells.items():
        for z in range(0, b.d + 1):
            for y, ch in column:
                b.set(x, y, z, ch)
    # Les pignons : la matière des murs entre les deux pans, une poutre à leur pied.
    for z in (0, b.d - 1):
        for y in range(5, top):
            left = [x for x in range(1, center) if any(cy == y for cy, _ in cells[x])]
            right = [x for x in range(center + 1, b.w + 1) if any(cy == y for cy, _ in cells[x])]
            if not left or not right:
                continue
            for x in range(max(left) + 1, min(right)):
                b.set(x, y, z, "|" if y == 5 else b.m["wall"])
    # La fenêtre du fenil, au milieu du pignon nord : deux bottes dans un cadre de rondins.
    for y in (6, 7):
        b.set(center, y, 0, "h")
        b.set(center - 1, y, 0, "P")
        b.set(center + 1, y, 0, "P")
    b.set(center, 8, 0, "|")
    # Une lucarne au pignon sud.
    b.set(center, 7, b.d - 1, "G")
    if b.level >= 4:
        b.set(center, top + 1, 0, "β")
    # La cheminée : son feu au-dessus du faîte.
    for (x, y, z) in fire:
        if y <= top + 1:
            stone = b.get(x, y - 1, z)
            b.fill(x, y, z, x, top + 1, z, stone)
            b.set(x, top + 2, z, "*")
