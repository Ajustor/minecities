"""
La pêcherie : un ponton de planches le long du mur est, du côté du bassin ;
des tonneaux couchés empilés au coin nord-est ; un séchoir à poissons (un portique de barrières où pendent
des trappes, puis des bottes d'algues séchées) ; dès le niveau 3, un second séchoir au fond, tendu de
ficelle.

Le bassin (pond, dans generate.py) se colle à l'est, et le baril du pêcheur doit rester à 6 blocs de son
eau : on ne grandit pas à l'est. Les tonneaux du décor sont dehors, loin de la pièce (le relevé ne les
voit pas).
"""

KEY = {
    "α": "minecraft:barrel[facing=east]",
    "β": "minecraft:barrel[facing=north]",
    "γ": "{trapdoor}[facing=east,half=top,open=true]",
    "δ": "minecraft:dried_kelp_block",
    "ε": "minecraft:tripwire[attached=false,disarmed=false,powered=false,north=false,south=false,east=true,west=true]",
    "ζ": "minecraft:blue_wall_banner[facing=east]",
}


def apply(b):
    x = b.w + 1                     # le débord est, du côté du bassin
    # Le ponton : des planches au ras du sol, tout le long du mur est, le séchoir au bout.
    for z in range(0, b.d + 1):
        b.set(x, 0, z, "f")
    # Les tonneaux couchés, au coin nord-est.
    b.set(x, 1, 0, "α")
    b.set(x, 1, 1, "α")
    if b.level >= 2:
        b.set(x, 2, 0, "β")
    # Le séchoir, au bout du ponton (après les volets) : deux poteaux, une traverse, des prises qui pendent.
    za, zb = b.d - 2, b.d
    for z in (za, zb):
        b.fill(x, 1, z, x, 3, z, "E")
    b.set(x, 3, za + 1, "E")
    b.set(x, 2, za + 1, "δ" if b.level >= 2 else "γ")
    if b.level >= 3:
        rack(b)
    if b.level >= 4:
        # Une lanterne sur les tonneaux, une bannière bleue sous l'avant-toit.
        b.set(x, 3, 0, "]")
        b.set(x, 3, 1, "ζ")


def rack(b):
    """Le grand séchoir, au fond : un portique de barrières le long du mur sud, tendu de ficelle."""
    z = b.d + 1
    posts = list(range(2, b.w, 3))
    for px in posts:
        b.fill(px, 1, z, px, 3, z, "E")
    for px in range(posts[0] + 1, posts[-1]):
        if px not in posts:
            b.set(px, 3, z, "ε")
            b.set(px, 2, z, "δ" if px % 2 else "=")
