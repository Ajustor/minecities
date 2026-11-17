"""
La mine : un chevalement de bois au-dessus de la bouche de la galerie, sa chaîne pendue dans le puits,
une voie de rails qui mène de la rue à la bouche, et un terril de pierre et de gravier.

La galerie compte (Mines.plan) : sa bouche est dehors en x = b.w + 2, z = b.d - 2 (la rangée de la table),
au premier air au-dessus du sol (la carte des hauteurs MOTION_BLOCKING) ; l'escalier descend vers l'est
d'un pas par marche, trois de haut. On ne pose donc rien de plein dans la colonne de la bouche (la bouche
monterait dessus), ni dans les premières marches : le chevalement enjambe l'escalier juste à l'est de
la bouche, ses jambes au nord et au sud de la rangée, sa poutre et sa chaîne assez haut pour que le
mineur passe dessous. Les couloirs, eux, partent du bas de l'escalier, seize blocs sous terre au moins.
"""

KEY = {
    "α": "minecraft:iron_chain[axis=y]",
    "β": "minecraft:rail[shape=north_south]",
    "γ": "minecraft:gravel",
    "δ": "minecraft:cobblestone",
    "ε": "minecraft:andesite",
    "ζ": "minecraft:tuff",
    "η": "minecraft:rail[shape=north_east]",
    "θ": "minecraft:rail[shape=east_west]",
    "ι": "{frame}[axis=z]",
}


def apply(b):
    level = b.level
    mouth = b.w + 2                  # la bouche de la galerie
    zm = b.d - 2                     # sa rangée (celle de la table)
    x0, x1 = mouth + 1, mouth + 3    # les jambes du chevalement, de part et d'autre du puits
    top = {1: 6, 2: 7, 3: 8, 4: 9}[level]     # la poutre maîtresse
    xs = mouth + 2                   # la chaîne, au-dessus de la deuxième marche

    # Le chevalement : quatre jambes, deux longrines, une traverse au-dessus du puits.
    for x in (x0, x1):
        for z in (zm - 1, zm + 1):
            b.fill(x, 0, z, x, top - 1, z, "P")
            if level >= 3:
                b.set(x, 0, z, "Z")
    for z in (zm - 1, zm + 1):
        b.fill(x0, top, z, x1, top, z, "|")
    b.set(xs, top, zm, "ι")
    if level >= 3:
        # Un petit toit de dalles sur le chevalement.
        b.fill(x0, top + 1, zm - 1, x1, top + 1, zm + 1, "S")
    # La chaîne pend jusqu'à deux blocs au-dessus de la bouche : le mineur passe dessous.
    b.fill(xs, 3, zm, xs, top - 1, zm, "α")
    # La bouche et les premières marches : de l'air au-dessus du sol, rien d'autre.
    b.fill(mouth, 1, zm, mouth, 3, zm, " ")
    b.fill(mouth + 1, 1, zm, mouth + 4, 2, zm, " ")
    if level >= 3:
        b.fill(mouth, 4, zm, mouth, top + 1, zm, " ")

    # La voie : de la rue à la bouche, le long du flanc est (on y marche, les rails ne gênent pas).
    rails = range(1, zm) if level >= 2 else range(zm - 3, zm)
    for z in rails:
        b.set(mouth, 0, z, "_")
        b.set(mouth, 1, z, "β")
    if level >= 3:
        # Un embranchement vers le terril.
        b.set(mouth, 1, 1, "η")
        for x in (mouth + 1, mouth + 2):
            b.set(x, 0, 1, "_")
            b.set(x, 1, 1, "θ")

    # Le terril, au nord-est (vu de la rue) : un lit de pierre, du gravier par-dessus.
    tx = mouth + 3 if level >= 3 else mouth + 2
    size = 2 if level == 1 else 3
    for x in range(tx, tx + size):
        for z in range(0, size):
            b.set(x, 0, z, "δ" if (x + z) % 2 else "ε")
    b.fill(tx, 1, 0, tx + size - 1, 1, size - 1, "γ")
    if size == 3:
        b.set(tx + 1, 2, 1, "γ")
        for x, z in ((tx, 0), (tx + 2, 2)):
            b.set(x, 1, z, "δ")
    if level >= 4:
        b.set(tx + 1, 3, 1, "γ")
        b.set(tx + 2, 1, 0, "ζ")
        b.set(tx, 1, 2, "ζ")
        # Des lanternes pendues sous les longrines.
        b.set(xs, top - 1, zm - 1, "J")
        b.set(xs, top - 1, zm + 1, "J")
