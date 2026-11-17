"""
La caserne : une cour d'exercice contre le flanc est, avec ses mannequins (un piquet, une botte de foin,
une citrouille sculptée), fermée dès le niveau 2 d'un mur crénelé à portail, et des bannières rouges.
Au niveau 1 (toit plat), ce sont les créneaux du toit qui donnent l'allure de fortin.

Tout est dehors : la botte de foin d'un mannequin n'est pas dans un mur, le relevé de la plaque ne la
voit pas (elle n'est donc pas prise pour un poste d'éleveur).
"""

KEY = {
    "α": "minecraft:carved_pumpkin[facing=north]",
    "β": "minecraft:red_banner[rotation=8]",
}


def ridge(b):
    """Le faîte : le plus haut bloc du bâtiment, cheminée (x = 0) mise à part, et sa colonne."""
    y = max(y for y in range(b.height) for z in range(b.depth) for x in range(1, b.width)
            if b.get(x, y, z) not in ". ")
    x = min(x for x in range(1, b.width) if b.get(x, y, 0) not in ". ")
    return x, y


def dummy(b, x, z):
    b.set(x, 1, z, "E")
    b.set(x, 2, z, "h")
    b.set(x, 3, z, "α")


def apply(b):
    level = b.level
    e = b.w + 1                      # le pied du mur ouest de la cour, contre le débord du toit
    east = b.w + 7                   # le mur est de la cour
    south = b.d                      # le mur sud, dans le prolongement du débord du fond
    gate = b.w + 4

    if level == 1:
        # Les créneaux sur le pourtour du toit plat, une bannière au-dessus de la porte.
        y = 5
        for x in range(1, b.w + 1):
            for z in range(0, b.d):
                edge = x in (1, b.w) or z in (0, b.d - 1)
                if edge and (x + z) % 2 == 0 and b.get(x, y, z) in ". ":
                    b.set(x, y, z, "O")
        b.set(4, 5, 0, "β")
        # La cour, juste marquée de piquets aux coins.
        for x, z in ((e + 1, 0), (east, 0), (east, south), (e + 1, south)):
            b.set(x, 1, z, "E")
        dummy(b, gate, 2)
        dummy(b, gate, 5)
        return

    # Le mur de la cour : plein jusqu'à « wall », un merlon sur deux au-dessus.
    stone = "Z" if level >= 3 else "O"
    wall = 2 if level >= 3 else 1
    ring = [(x, 0) for x in range(e, east + 1)] + [(east, z) for z in range(1, south + 1)] \
        + [(x, south) for x in range(east - 1, e - 1, -1)]
    for i, (x, z) in enumerate(ring):
        b.set(x, 0, z, "O")
        b.fill(x, 1, z, x, wall, z, stone)
        if i % 2 == 0:
            b.set(x, wall + 1, z, stone)
    # Le portail au nord, entre deux piliers plus hauts coiffés de bannières.
    b.set(gate, 1, 0, "g")
    b.fill(gate, 2, 0, gate, wall + 1, 0, " ")
    for x in (gate - 1, gate + 1):
        b.fill(x, 1, 0, x, wall + 1, 0, stone)
        b.set(x, wall + 2, 0, "β")
    # La cour dégagée, et ses mannequins.
    b.fill(e + 1, 1, 1, east - 1, 3, south - 1, " ")
    spots = [(gate - 1, 2), (gate + 1, 4), (gate - 1, 6), (gate + 1, 2)][:level]
    for x, z in spots:
        dummy(b, x, z)
    if level >= 3:
        # Une bannière au sommet du pignon nord.
        x, y = ridge(b)
        b.set(x, y + 1, 0, "β")
    if level >= 4:
        # Des tourelles aux coins est, plus hautes d'un cran, une lanterne sur chacune.
        for z in (0, south):
            b.fill(east, 1, z, east, wall + 2, z, stone)
            b.set(east, wall + 3, z, "]")
