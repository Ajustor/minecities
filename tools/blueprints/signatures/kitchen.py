"""
La cuisine : un fumoir de pierre collé au coin nord-est, sa haute cheminée qui fume au-dessus du toit, et
un petit potager d'herbes en pots sur un établi le long du mur est (une jardinière à la fenêtre).
Rien de tout cela n'est un poste : le fumoir (le bloc) reste dans la pièce.
"""

KEY = {
    "α": "minecraft:potted_fern",
    "β": "minecraft:potted_azalea_bush",
    "γ": "minecraft:potted_flowering_azalea_bush",
    "δ": "minecraft:potted_cornflower",
    "ε": "minecraft:potted_dandelion",
}

HERBS = "αβγδε"


def apply(b):
    east = b.w + 1          # la colonne du débord est
    out = b.w + 2           # une colonne de plus à l'est
    stone = "O" if b.level <= 2 else "K"
    # La cheminée du fumoir, au coin nord-est : elle dépasse le faîte d'un bloc, et fume.
    ridge = b.height - 2
    top = ridge + 1
    b.fill(out, 0, 0, out, top, 0, stone)
    if b.level >= 4:
        # Un bandeau de pierre taillée à mi-hauteur.
        b.set(out, 4, 0, "Z")
    b.set(out, top + 1, 0, "*")
    # Le fumoir : un massif de pierre contre le mur, coiffé de dalles ; une grille devant le foyer.
    b.fill(east, 0, 0, east, 2, 1, stone)
    b.fill(out, 0, 1, out, 2, 1, stone)
    for x, z in ((east, 0), (east, 1), (out, 1)):
        b.set(x, 3, z, "S")
    if b.level >= 2:
        b.set(east, 1, 0, "I")
    # Le potager d'herbes : un établi de dalles le long du mur est, des pots d'herbes dessus.
    last = 5 if b.level < 4 else b.d - 1
    for i, z in enumerate(range(3, last + 1)):
        b.set(out, 1, z, ":")
        b.set(out, 2, z, HERBS[(i + b.level) % len(HERBS)])
    if b.level >= 3:
        # Une lanterne posée au bout de l'établi.
        b.set(out, 1, last + 1, "E")
        b.set(out, 2, last + 1, "]")
    # La jardinière : un pot de basilic (une fougère) sur l'appui de la fenêtre est.
    middle = b.d // 2
    b.set(east, 1, middle, "§")
    b.set(east, 2, middle, "α")
