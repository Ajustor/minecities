"""
La signature d'un bâtiment (v1.16) : ce qui le distingue des autres au premier coup d'œil (un pas de
tir, un quai de chargement, un chevalement…). Chaque famille a son module dans signatures/<famille>.py :

    KEY = {"α": "minecraft:target"}   # ses caractères à elle (jamais ceux de generate.KEY)

    def apply(b):                      # b : un Building, déjà habillé et meublé
        b.grow(east=6)
        b.set(b.width - 2, 0, 3, "α")

Le repère (celui du bâtiment habillé par decorate) :
  - x d'ouest en est : x = 0 la colonne du débord ouest, murs en x = 1 et x = b.w, porte en x = 4 ;
  - y de bas en haut : y = 0 la fondation (posée sur le terrain), y = 1..3 la pièce, y = 4 le plafond,
    y >= 5 le toit, la dernière couche le feu de la cheminée ;
  - z du nord au sud : z = 0 la façade (la porte), z = b.d - 1 le mur du fond, z = b.d le débord du fond.
Le perron (une rangée au nord) est ajouté après : on ne grandit ni au nord ni à l'ouest, seulement à
l'est, au sud et vers le haut.

« . » laisse le terrain tel quel, « espace » exige de l'air.
"""


class Building:
    def __init__(self, family, level, layers, w, d, materials):
        self.family = family
        self.level = level
        self.layers = layers
        # La pièce, murs compris (avant habillage).
        self.w = w
        self.d = d
        # Les matériaux du niveau : base, low, wall, corner, window, roof (est, ouest, faîte) ou None,
        # light, ceiling, floor (voir generate.LEVELS).
        self.m = materials

    @property
    def width(self):
        return len(self.layers[0][0])

    @property
    def depth(self):
        return len(self.layers[0])

    @property
    def height(self):
        return len(self.layers)

    def grow(self, east=0, south=0, up=0):
        """Agrandit la grille (de « . ») à l'est, au sud, vers le haut."""
        for layer in self.layers:
            for row in layer:
                row.extend(["."] * east)
            for _ in range(south):
                layer.append(["."] * len(layer[0]))
        for _ in range(up):
            self.layers.append([["."] * self.width for _ in range(self.depth)])

    def ensure(self, x, y, z):
        """Agrandit la grille si besoin pour contenir (x, y, z)."""
        assert x >= 0 and z >= 0 and y >= 0, (x, y, z)
        self.grow(east=max(0, x + 1 - self.width), south=max(0, z + 1 - self.depth),
                  up=max(0, y + 1 - self.height))

    def get(self, x, y, z):
        if 0 <= y < self.height and 0 <= z < self.depth and 0 <= x < self.width:
            return self.layers[y][z][x]
        return "."

    def set(self, x, y, z, ch):
        self.ensure(x, y, z)
        self.layers[y][z][x] = ch

    def fill(self, x0, y0, z0, x1, y1, z1, ch, only=None):
        """Remplit le pavé (bornes comprises) ; only : ne remplace que ces caractères."""
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for x in range(min(x0, x1), max(x0, x1) + 1):
                    if only is None or self.get(x, y, z) in only:
                        self.set(x, y, z, ch)

    def find(self, chars):
        """Les positions (x, y, z) des caractères donnés."""
        return [(x, y, z) for y, layer in enumerate(self.layers) for z, row in enumerate(layer)
                for x, ch in enumerate(row) if ch in chars]

    def stations(self):
        """Les postes de travail (un par niveau, le long du mur du fond, en y = 1)."""
        return [(x, 1, self.d - 2) for x in range(6, 6 + 2 * self.level, 2)]
