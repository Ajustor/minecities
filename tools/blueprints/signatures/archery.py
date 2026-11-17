"""
Le pas de tir : un couloir de tir de trois de large le long du flanc est, bordé de barrières, qui file
de la rue vers les cibles au fond (des bottes de foin, puis de vraies cibles dès le niveau 3) ; et une
tour de guet derrière l'atelier, dont la plateforme dépasse le faîte.

La tour sert le métier (ArcherGoal.lookout) : l'archer se poste sur la case libre la plus haute, au-dessus
d'un bloc plein, à 3 blocs au plus (en x et en z) de sa table. La rangée nord de la plateforme est donc
à z = b.d + 1 (3 au sud des tables, en z = b.d - 2), centrée entre les tables (à 3 au plus de chacune),
deux au-dessus du faîte, avec deux cases d'air au-dessus ; la rambarde ne borde que les rangées hors de
portée, sinon l'archer monterait dessus. Au niveau 1 (toit plat), c'est un nid de guet sur le toit.
"""

KEY = {
    "α": "minecraft:target",
    "β": "minecraft:ladder[facing=east]",
    "γ": "minecraft:red_wall_banner[facing=north]",
}


def ridge(b):
    """Le plus haut bloc du bâtiment, cheminée (x = 0) mise à part."""
    return max(y for y in range(b.height) for z in range(b.depth) for x in range(1, b.width)
               if b.get(x, y, z) not in ". ")


def apply(b):
    level = b.level
    tables = b.stations()
    top = ridge(b)
    deck = top + 2                      # la plateforme (l'archer se tient juste au-dessus)
    cx = (tables[0][0] + tables[-1][0]) // 2   # à 3 au plus de chaque table
    zt = tables[0][2]
    e = b.w + 2                          # la barrière intérieure du couloir
    end = 11                             # la rangée des cibles

    # La guette.
    if level == 1:
        # Un nid sur le toit plat : quatre piquets, un plancher de dalles, et une échelle au mur est.
        for x in (cx - 1, cx + 1):
            for z in (zt - 1, zt + 1):
                b.set(x, deck - 1, z, "E")
        b.fill(cx - 1, deck, zt - 1, cx + 1, deck, zt + 1, "S")
        b.fill(cx - 1, deck + 1, zt - 1, cx + 1, deck + 2, zt + 1, " ")
        # L'échelle s'accroche au mur, puis à une bûche à la place de la dalle du plafond.
        b.set(b.w, 4, b.d - 2, "P")
        b.fill(b.w + 1, 1, b.d - 2, b.w + 1, 4, b.d - 2, "β")
    else:
        z0, z2 = b.d + 1, b.d + 3
        assert z0 - zt <= 3
        for x in (cx - 1, cx + 1):
            for z in (z0, z2):
                b.fill(x, 0, z, x, deck - 1, z, "P")
                if level >= 3:
                    b.fill(x, 0, z, x, 1, z, "Z")
        b.fill(cx - 1, deck, z0, cx + 1, deck, z2, "f")
        # La rambarde, sur les rangées hors de portée ; un passage au coin de l'échelle.
        for x, z in ((cx - 1, z0 + 1), (cx - 1, z2), (cx, z2), (cx + 1, z0 + 1)):
            b.set(x, deck + 1, z, "E")
        b.fill(cx - 1, deck + 1, z0, cx + 1, deck + 2, z0, " ")
        b.set(cx, deck + 1, z0 + 1, " ")
        # L'échelle, sur la face est du poteau sud-est.
        b.fill(cx + 2, 1, z2, cx + 2, deck, z2, "β")
        if level >= 3:
            b.set(cx, deck - 1, z0 + 1, "J")
        if level >= 4:
            # Une bannière sous la plateforme, tournée vers la rue, au-dessus du toit.
            b.set(cx, deck, z0 - 1, "γ")

    # Le couloir de tir : dégagé, bordé de barrières, les cibles au fond.
    b.ensure(e + 4, 0, end)
    b.fill(e + 1, 1, 0, e + 3, 2, end - 1, " ")
    outer = range(1, end + 1) if level >= 2 else (1, 4, 7, 10)
    inner = range(1, end + 1) if level >= 3 else (1, 4, 7, 10)
    for z in outer:
        b.set(e + 4, 1, z, "E")
    for z in inner:
        b.set(e, 1, z, "E")
    if level == 1:
        b.fill(e + 2, 1, end, e + 2, 2, end, "h")
    else:
        b.fill(e + 1, 1, end, e + 3, 2, end, "h")
    if level >= 3:
        b.set(e + 2, 2, end, "α")
    if level >= 4:
        b.set(e + 1, 2, end, "α")
        b.set(e + 3, 2, end, "α")
        # La ligne de tir, marquée au sol, et deux lanternes à l'entrée du couloir.
        b.fill(e + 1, 0, 1, e + 3, 0, 1, "Q")
        b.set(e, 2, 1, "]")
        b.set(e + 4, 2, 1, "]")
