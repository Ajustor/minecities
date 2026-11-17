"""
La charpenterie : un auvent sur la cour d'atelier, contre le mur est, porté par des poteaux et des
chevrons apparents qui dépassent du toit ; dessous, des piles de planches et de dalles, et dès le
niveau 2 un chevalet de sciage (une bûche sur deux tréteaux, la lame de fer plantée dedans).

Les chevrons n'ont jamais un bloc plein dessous et de l'air dessus : dans un style à charpente
écorcée, une bûche écorcée ainsi posée serait un billot (le poste du bûcheron).
"""

KEY = {
    # Les piles de bois débité, de plusieurs essences.
    "α": "minecraft:oak_planks",
    "β": "minecraft:spruce_planks",
    "γ": "minecraft:birch_planks",
    "δ": "minecraft:oak_slab[type=bottom]",
    "ε": "minecraft:birch_slab[type=bottom]",
    # Le chevalet : une bûche couchée sur deux tréteaux, et la lame (une trappe de fer dressée).
    "ζ": "minecraft:oak_log[axis=z]",
    "η": "minecraft:iron_trapdoor[facing=north,half=bottom,open=true]",
    # Le toit de l'auvent, ourlé de marches au bord est.
    "θ": "{roof_stairs}[facing=west,half=bottom,shape=straight]",
}


def apply(b):
    e = b.w + 2           # la première colonne libre à l'est
    # Les poteaux, au bord de la cour : elle s'élargit d'un bloc dès le niveau 2 (place du chevalet).
    post = e + (2 if b.level == 1 else 3)
    # Sous le toit plat du niveau 1, l'auvent est un cran plus bas que sous les pans des niveaux suivants.
    rafter = 3 if b.level == 1 else 4
    z0, z1 = 1, b.d - (2 if b.level == 1 else 1)
    # Les chevrons, est-ouest, qui dépassent d'un bloc au-delà des poteaux ; le toit de dalles dessus.
    rafters = range(z0, z1 + 1, 2)
    for z in rafters:
        b.fill(e, rafter, z, post + 1, rafter, z, "|")
    # Sous les pans (niveau 2 et plus), le toit de l'auvent se glisse au-dessus du débord du bâtiment.
    b.fill(e - (0 if b.level == 1 else 1), rafter + 1, z0, post, rafter + 1, z1, "S")
    if b.level >= 2:
        b.fill(post + 1, rafter + 1, z0, post + 1, rafter + 1, z1, "θ")
    # Les poteaux (des piquets de barrière) sous les chevrons des bouts, et du milieu dès le niveau 3.
    posts = (z0, z1) + ((rafters[len(rafters) // 2],) if b.level >= 3 else ())
    for z in posts:
        b.fill(post, 1, z, post, rafter - 1, z, "E")
    # Les piles de planches, contre le mur : planches pleines, une dalle sur le dessus.
    b.fill(e, 1, 2, e, 1, 4, "α")
    b.fill(e, 2, 2, e, 2, 4, "δ")
    b.set(e + 1, 1, 2, "β")
    b.set(e + 1, 1, 3, "ε")
    if b.level >= 2:
        b.set(e, 2, 3, "β")
        b.set(e, 3, 3, "δ")
        b.fill(e, 1, 5, e, 1, z1, "γ")
        b.fill(e, 2, 5, e, 2, z1, "ε")
        sawhorse(b, e + 2, 4)
    if b.level >= 3:
        b.fill(e + 1, 1, 5, e + 1, 1, z1, "β")
        b.set(e + 1, 2, 5, "δ")
        # Une lanterne pendue au chevron du milieu.
        b.set(e + 1, rafter - 1, rafters[len(rafters) // 2], "J")
    if b.level >= 4:
        # Une seconde pile devant, sous le débord, et une autre lanterne.
        b.fill(e, 1, 1, e + 1, 1, 1, "γ")
        b.set(e, 2, 1, "ε")
        b.set(e + 1, rafter - 1, z0, "J")


def sawhorse(b, x, z):
    """Le chevalet : deux tréteaux, une bûche couchée dessus, la lame plantée devant."""
    b.set(x, 1, z - 1, "E")
    b.set(x, 1, z + 1, "E")
    b.fill(x, 2, z - 1, x, 2, z + 1, "ζ")
    b.set(x, 2, z - 2, "η")
