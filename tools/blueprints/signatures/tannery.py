"""
La tannerie : dans la cour, à l'est, une rangée de cuves de trempage maçonnées (l'eau claire et la
boue brune du tan, en alternance), et derrière, des séchoirs où les peaux sont tendues sous une
poutre (des bannières unies brunes, blanches et grises). Dès le niveau 3, les cuves sont en briques et
un tas d'écorces attend contre le séchoir ; au niveau 4, un second séchoir plus haut et des lanternes.
Pas de tonneau dans la cour : près de l'eau, un tonneau ferait un poste de pêcheur.
"""

KEY = {
    "α": "minecraft:mud",
    "β": "minecraft:brown_wall_banner[facing=north]",
    "γ": "minecraft:white_wall_banner[facing=north]",
    "δ": "minecraft:light_gray_wall_banner[facing=north]",
    "ε": "{frame}[axis=z]",
}

HIDES = "βγδβ"
# Les cuves, d'ouest en est : « ~ » l'eau, « α » la boue du tan.
VATS = {1: "~α", 2: "~α~", 3: "~α~", 4: "~α~"}
# Combien de peaux sur le premier séchoir.
RACK = {1: 2, 2: 3, 3: 3, 4: 3}


def rack(b, x0, z, n, height):
    """Un séchoir : deux poteaux, une poutre en haut, n peaux pendues à sa face nord."""
    for x in (x0, x0 + n + 1):
        b.fill(x, 1, z, x, height - 1, z, "P")
    b.fill(x0, height, z, x0 + n + 1, height, z, "|")
    for i in range(n):
        b.set(x0 + 1 + i, height, z - 1, HIDES[(i + height) % len(HIDES)])


def apply(b):
    x0 = b.width + 1  # une allée le long du mur est
    rim = "K" if b.level >= 3 else "O"
    vats = VATS[b.level]
    # Les cuves, au ras de la façade : un rebord maçonné tout autour, un fond sous chaque cuve.
    x1 = x0 + 2 * len(vats)
    b.fill(x0, 1, 0, x1, 1, 2, rim)
    for i, liquid in enumerate(vats):
        x = x0 + 1 + 2 * i
        b.set(x, 0, 1, rim)
        b.set(x, 1, 1, liquid)
    # Le séchoir, derrière les cuves.
    rack(b, x0, 4, RACK[b.level], 3)
    if b.level >= 3:
        # Un tas d'écorces pour le tan, au pied du séchoir.
        n = RACK[b.level]
        b.fill(x0 + n + 2, 1, 4, x0 + n + 3, 1, 5, "ε")
        b.set(x0 + n + 2, 2, 4, "ε")
        b.set(x0 + n + 2, 2, 5, "ε")
    if b.level == 4:
        # Un second séchoir, plus haut, au fond de la cour ; des lanternes sur le premier.
        rack(b, x0, b.d - 1, 4, 4)
        b.set(x0, 4, 4, "]")
        b.set(x0 + RACK[b.level] + 1, 4, 4, "]")
