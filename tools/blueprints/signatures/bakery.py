"""
La boulangerie : un four à pain rond qui fait saillie au coin nord-est, sa gueule (un feu de camp éteint)
tournée vers la rue et sa petite cheminée qui fume ; puis une réserve de bûches pour le chauffer, une
lanterne et l'enseigne jaune du boulanger. Le four (le bloc, le poste) reste dans la pièce.
"""

KEY = {
    "α": "minecraft:campfire[lit=false,signal_fire=false,facing=north]",
    "β": "{frame}[axis=z]",
    "γ": "minecraft:yellow_wall_banner[facing=north]",
}


def apply(b):
    x0, x1 = b.w + 1, b.w + 3      # le four : 3 x 3, du débord est jusqu'à deux colonnes plus loin
    cx, cz = b.w + 2, 1            # son centre
    # La pierre au niveau 1, la brique ensuite.
    body = "O" if b.level == 1 else "K"
    base = "O" if b.level < 4 else "Q"
    # La sole, puis la voûte : un carré, une croix, le sommet ; les coins s'arrondissent.
    b.fill(x0, 0, 0, x1, 0, 2, base)
    b.fill(x0, 1, 0, x1, 1, 2, body)
    b.set(cx, 1, 0, "α")
    for x, z in ((cx, 0), (cx, 1), (cx, 2), (x0, 1), (x1, 1)):
        b.set(x, 2, z, body)
    # La cheminée du four, au sommet de la voûte, et sa fumée.
    top = 5 + b.level // 2
    b.fill(cx, 3, cz, cx, top, cz, body)
    b.set(cx, top + 1, cz, "*")
    if b.level >= 2:
        # La réserve de bûches, derrière le four, sous un appentis de dalles.
        b.fill(b.w + 2, 1, 3, b.w + 3, 2, 4, "β")
        b.fill(b.w + 2, 3, 3, b.w + 3, 3, 4, "S")
    if b.level >= 3:
        # Une lanterne sur un piquet, au coin de la voûte.
        b.set(x1, 2, 0, "E")
        b.set(x1, 3, 0, "]")
    if b.level >= 4:
        # L'enseigne : une bannière jaune (un épi de blé) pendue à la cheminée.
        b.set(cx, 4, 0, "γ")
