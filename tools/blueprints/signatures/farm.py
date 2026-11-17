"""
La ferme : un épouvantail au bord du champ (piquet, botte de foin, citrouille sculptée, bras de
barrière), un tas de citrouilles et de pastèques, et dès le niveau 3 un silo rond au coin nord-est, coiffé
d'un toit pointu (de cuivre à la demeure).

Le champ (field, dans generate.py) se colle à l'est de la grille et est irrigué par un seul trou d'eau
au rang z = 3 : on ne grandit pas au sud (des rangs trop loin de l'eau sécheraient), et d'une seule
colonne à l'est, celle de l'épouvantail et du silo.
"""

KEY = {
    "α": "minecraft:carved_pumpkin[facing=north]",
    # Le toit du silo : des marches tournées vers son axe, nord et sud (« < » et « > » font l'est et l'ouest).
    "β": "{roof_stairs}[facing=south,half=bottom,shape=straight]",
    "γ": "{roof_stairs}[facing=north,half=bottom,shape=straight]",
    "δ": "{fine_roof_stairs}[facing=south,half=bottom,shape=straight]",
    "ε": "{fine_roof_stairs}[facing=north,half=bottom,shape=straight]",
    "ζ": "minecraft:lightning_rod[facing=up]",
}


def apply(b):
    x = b.w + 2                    # la colonne ajoutée, entre l'atelier et le champ
    b.grow(east=1)
    # L'épouvantail : un piquet, un corps de foin, deux bras, une tête sculptée (la tête, pleine, empêche
    # la botte de foin de passer pour un poste d'éleveur).
    z = b.d - 2
    b.set(x, 1, z, "E")
    b.set(x, 2, z, "h")
    b.set(x, 2, z - 1, "E")
    b.set(x, 2, z + 1, "E")
    b.set(x, 3, z, "α")
    if b.level >= 2:
        # Un chapeau (un tapis) et la récolte au pied du mur.
        b.set(x, 4, z, "-")
        b.set(x, 1, 2, "6")
        b.set(x, 1, 3, "7")
    if b.level >= 3:
        silo(b, b.w + 1, 1)


def silo(b, cx, cz):
    """Un silo rond (une croix de 3 x 3) centré sur (cx, cz), plus haut que le faîte, au toit pointu."""
    top = b.height - 2             # le faîte de l'atelier (la dernière couche est le feu)
    cells = [(cx, cz - 1), (cx, cz), (cx, cz + 1), (cx - 1, cz), (cx + 1, cz)]
    for x, z in cells:
        b.set(x, 0, z, "Q")
        b.fill(x, 1, z, x, top, z, "W")
        # Deux cerclages de rondins.
        for y in (4, top - 1):
            b.set(x, y, z, "|")
    # Une lucarne de chargement, face au nord.
    b.set(cx, top - 3, cz - 1, "G")
    fine = b.level >= 4
    east, west, ridge = ("{", "}", "Y") if fine else ("<", ">", "R")
    north, south = ("δ", "ε") if fine else ("β", "γ")
    b.set(cx, top + 1, cz - 1, north)
    b.set(cx, top + 1, cz + 1, south)
    b.set(cx - 1, top + 1, cz, east)
    b.set(cx + 1, top + 1, cz, west)
    b.set(cx, top + 1, cz, ridge)
    b.set(cx, top + 2, cz, "ζ" if fine else "E")
