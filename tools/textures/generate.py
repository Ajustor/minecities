"""Génère les textures du mod : peaux des citoyens, plaque de maison, œuf. (L'hôtel de ville est un
modèle en textures vanilla : voir tools/models/town_hall.py.)

Usage : python tools/textures/generate.py
Les PNG sont écrits dans src/main/resources/assets/minecities/textures. Modifier ce script puis le
relancer plutôt que de retoucher les PNG à la main : la prochaine génération les écraserait.
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEXTURES = ROOT / "src/main/resources/assets/minecities/textures"


def hexc(value, alpha=255):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


def shade(color, factor):
    r, g, b, a = color
    return (max(0, min(255, int(r * factor))), max(0, min(255, int(g * factor))),
            max(0, min(255, int(b * factor))), a)


def jitter(rng, color, amount=0.06):
    return shade(color, 1 + rng.uniform(-amount, amount))


def fill(img, rng, x, y, w, h, color, amount=0.05):
    for i in range(x, x + w):
        for j in range(y, y + h):
            img.putpixel((i, j), jitter(rng, color, amount))


# Peaux de joueur 64x64 : chaque membre est une boîte dont les faces sont dépliées autour de (u, v).
# Les faces : dessus, dessous, droite, devant, gauche, dos.

def box_faces(u, v, w, h, d):
    """Rectangles (x, y, largeur, hauteur) des faces d'une boîte dépliée à (u, v)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


HEAD = box_faces(0, 0, 8, 8, 8)
HAT = box_faces(32, 0, 8, 8, 8)
BODY = box_faces(16, 16, 8, 12, 4)
JACKET = box_faces(16, 32, 8, 12, 4)
RIGHT_ARM = box_faces(40, 16, 4, 12, 4)
RIGHT_SLEEVE = box_faces(40, 32, 4, 12, 4)
LEFT_ARM = box_faces(32, 48, 4, 12, 4)
LEFT_SLEEVE = box_faces(48, 48, 4, 12, 4)
RIGHT_LEG = box_faces(0, 16, 4, 12, 4)
RIGHT_PANTS = box_faces(0, 32, 4, 12, 4)
LEFT_LEG = box_faces(16, 48, 4, 12, 4)
LEFT_PANTS = box_faces(0, 48, 4, 12, 4)

SKINS = ["#f1c7a1", "#e0ac83", "#c68a5e", "#8d5a3b", "#5c3a24", "#f5d5bb"]
HAIRS = ["#3b2414", "#1d1410", "#7a4b23", "#c99b4a", "#a33f1c", "#5a5a5a", "#e6d29a"]

# Les tenues des citoyens : (chemise, pantalon, chaussures, tablier ou None, cheveux longs).
OUTFITS = [
    ("#3f6fb5", "#4b3a2a", "#2e2018", None, False),        # paysan en bleu
    ("#a63a2f", "#3a3f4a", "#251a12", None, True),          # robe rouge
    ("#dcd3bd", "#5b4a33", "#2e2018", "#8a6a43", False),    # artisan au tablier de cuir
    ("#4f7f3a", "#5d4c38", "#2e2018", None, True),          # vert forestier
    ("#e2c85a", "#3e4f6b", "#251a12", None, False),         # chemise jaune
    ("#6b4b8f", "#2f2f38", "#1d1612", "#e9e4d8", True),     # boulangère au tablier blanc
    ("#b9b9b9", "#4a4a52", "#262626", None, False),         # gris
    ("#c9773a", "#4b3a2a", "#2e2018", "#e9e4d8", True),     # orange au tablier
]


def face(img, rng, rect, color, amount=0.05):
    x, y, w, h = rect
    fill(img, rng, x, y, w, h, color, amount)


def paint_box(img, rng, faces, color, amount=0.05):
    for rect in faces.values():
        face(img, rng, rect, color, amount)


def citizen_skin(index):
    rng = random.Random(1000 + index)
    shirt, pants, shoes, apron, long_hair = OUTFITS[index]
    skin = hexc(SKINS[(index * 5 + 1) % len(SKINS)])
    hair = hexc(HAIRS[(index * 3 + 2) % len(HAIRS)])
    shirt, pants, shoes = hexc(shirt), hexc(pants), hexc(shoes)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    # Tête : peau, cheveux sur le dessus, l'arrière et le haut des côtés, puis le visage.
    paint_box(img, rng, HEAD, skin, 0.03)
    face(img, rng, HEAD["top"], hair, 0.1)
    face(img, rng, HEAD["back"], hair, 0.1)
    hair_down = 8 if long_hair else 3
    for side in ("right", "left"):
        x, y, w, h = HEAD[side]
        fill(img, rng, x, y, w, 3, hair, 0.1)
        fill(img, rng, x + (0 if side == "left" else w - 3), y + 3, 3, hair_down - 3 if long_hair else 0, hair, 0.1)
    fx, fy, _, _ = HEAD["front"]
    fill(img, rng, fx, fy, 8, 2, hair, 0.1)                          # frange
    if long_hair:
        fill(img, rng, fx, fy + 2, 1, 5, hair, 0.1)
        fill(img, rng, fx + 7, fy + 2, 1, 5, hair, 0.1)
    white, iris = hexc("#f4f4f4"), hexc("#3b5f8a" if index % 2 else "#4a3220")
    for ex in (1, 5):
        img.putpixel((fx + ex, fy + 4), white)
        img.putpixel((fx + ex + 1, fy + 4), iris)
    brow = shade(hair, 0.8)
    for ex in (1, 2, 5, 6):
        img.putpixel((fx + ex, fy + 3), brow)
    mouth = shade(skin, 0.7)
    for mx in (3, 4):
        img.putpixel((fx + mx, fy + 6), mouth)
    img.putpixel((fx + 3, fy + 5), shade(skin, 0.88))                # nez
    img.putpixel((fx + 4, fy + 5), shade(skin, 0.88))
    if long_hair:
        # Les cheveux longs retombent sur la nuque : la surcouche du chapeau, au dos.
        x, y, w, h = HAT["back"]
        fill(img, rng, x, y + 2, w, 6, hair, 0.1)

    # Corps : chemise, ceinture, tablier éventuel.
    paint_box(img, rng, BODY, shirt)
    belt = hexc("#3a2616")
    for name in ("front", "back", "right", "left"):
        x, y, w, h = BODY[name]
        fill(img, rng, x, y + 8, w, 1, belt, 0.04)
    x, y, w, h = BODY["front"]
    fill(img, rng, x + 3, y, 2, 2, shade(shirt, 0.8))                  # col
    if apron:
        a = hexc(apron)
        fill(img, rng, x + 1, y + 3, 6, 9, a, 0.04)
        x, y, w, h = JACKET["front"]
        fill(img, rng, x + 1, y + 6, 6, 6, a, 0.04)                     # le bas du tablier dépasse

    # Bras : manches puis mains.
    for arm in (RIGHT_ARM, LEFT_ARM):
        paint_box(img, rng, arm, skin, 0.03)
        for name in ("front", "back", "right", "left"):
            x, y, w, h = arm[name]
            fill(img, rng, x, y, w, 7 if index % 3 else 10, shirt)
        face(img, rng, arm["top"], shirt)

    # Jambes : pantalon, chaussures.
    for leg in (RIGHT_LEG, LEFT_LEG):
        paint_box(img, rng, leg, pants)
        for name in ("front", "back", "right", "left"):
            x, y, w, h = leg[name]
            fill(img, rng, x, y + 10, w, 2, shoes, 0.04)
        face(img, rng, leg["bottom"], shoes, 0.04)
    if long_hair and index % 2:
        # Une robe : le haut des jambes prend la couleur de la chemise.
        for leg in (RIGHT_LEG, LEFT_LEG):
            for name in ("front", "back", "right", "left"):
                x, y, w, h = leg[name]
                fill(img, rng, x, y, w, 6, shade(shirt, 0.92))
    return img


# Blocs 16x16

OAK = hexc("#a2824e")
OAK_DARK = hexc("#6b5230")


def planks(rng, img, y0=0, y1=16):
    for y in range(y0, y1):
        for x in range(16):
            c = jitter(rng, OAK, 0.05)
            if y % 4 == 3:
                c = shade(OAK_DARK, 1.05)
            elif (x + (y // 4) * 5) % 8 == 0:
                c = shade(OAK, 0.85)
            img.putpixel((x, y), c)



def frame(img, color):
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, color)





def house_plaque(status=None):
    """La plaque ; une fois relevée, sa porte est verte (bâtiment enregistré) ou rouge (refusé)."""
    rng = random.Random(4)
    img = Image.new("RGBA", (16, 16))
    planks(rng, img)
    frame(img, OAK_DARK)
    ink = hexc("#2a1d12")
    house = [
        "....##....",
        "...####...",
        "..######..",
        ".########.",
        "##########",
        ".#......#.",
        ".#.##...#.",
        ".#.##...#.",
    ]
    for j, row in enumerate(house):
        for i, ch in enumerate(row):
            if ch == "#":
                img.putpixel((3 + i, 4 + j), ink)
    if status:
        door = hexc("#3fae4a") if status == "valid" else hexc("#c3261d")
        light = hexc("#f6d76b") if status == "valid" else hexc("#5a2a22")
        for x, y in ((6, 10), (7, 10), (6, 11), (7, 11)):
            img.putpixel((x, y), shade(door, 1.15) if y == 10 else door)
        for x, y in ((9, 10), (10, 10)):
            img.putpixel((x, y), light)
    return img


def annex_board():
    """Le panneau de la mairie annexe : une affiche sur du bois, avec le plan du quartier."""
    rng = random.Random(7)
    img = Image.new("RGBA", (16, 16))
    planks(rng, img)
    paper = hexc("#e9dcb5")
    ink = hexc("#3b2a1a")
    road = hexc("#b89a6a")
    roof = hexc("#a3402e")
    green = hexc("#6f8f45")
    # L'affiche (dans la partie visible du panneau : x 1 à 14, y 1 à 7).
    for y in range(2, 8):
        for x in range(2, 14):
            img.putpixel((x, y), jitter(rng, paper, 0.03))
    # Une route en croix, des maisons aux toits rouges, un arbre.
    for x in range(2, 14):
        img.putpixel((x, 5), road)
    for y in range(2, 8):
        img.putpixel((8, y), road)
    for x, y in ((4, 3), (5, 3), (10, 3), (11, 6), (4, 7), (12, 3)):
        img.putpixel((x, y), roof)
    img.putpixel((6, 7), green)
    img.putpixel((13, 6), green)
    # Le titre, une ligne d'encre, et deux punaises.
    for x in range(3, 8):
        img.putpixel((x, 2), ink)
    img.putpixel((2, 2), hexc("#c3261d"))
    img.putpixel((13, 2), hexc("#c3261d"))
    return img


def spawn_egg():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base, spot = hexc("#7b5536"), hexc("#3f6fb5")
    outline = hexc("#2a1d12")
    rng = random.Random(5)
    cx, cy = 7.5, 8.5
    for y in range(16):
        for x in range(16):
            dx = (x - cx) / 5.2
            dy = (y - cy) / (6.6 if y < cy else 5.8)
            d = dx * dx + dy * dy
            if d <= 1:
                c = jitter(rng, base, 0.05)
                if d > 0.72:
                    c = outline
                elif (x * 7 + y * 3) % 11 == 0 or (x, y) in ((6, 6), (9, 9), (5, 11), (10, 5)):
                    c = spot
                elif x < cx - 1 and y < cy - 1:
                    c = shade(c, 1.15)
                img.putpixel((x, y), c)
    return img


# Les couleurs des chapeaux de métier, dans l'ordre de CitizenHatModel.Tile.
HAT_TILES = ["#d8b45a", "#8a6a2c", "#f2f0ea", "#b3312c", "#6b4a2b", "#4e6a8a", "#e07b24", "#2f4f9e",
             "#a9adb3", "#3f7a33", "#9fd4e8", "#5a3b22", "#26262b", "#e8c13a", "#6a2d8a", "#7d7f84", "#e2c33b"]


def hats():
    """La texture des chapeaux : 256 x 256, des pavés de 64 x 32, chacun d'une couleur (un peu bruitée)."""
    rng = random.Random(6)
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    for i, color in enumerate(HAT_TILES):
        x0, y0 = (i % 4) * 64, (i // 4) * 32
        base = hexc(color)
        for y in range(32):
            for x in range(64):
                c = jitter(rng, base, 0.06)
                # La paille est tressée : des rangs plus sombres.
                if i == 0 and (x + y) % 4 == 0:
                    c = shade(c, 0.85)
                # Le verre des lunettes : un reflet.
                if i == 10 and (x + y) % 5 == 0:
                    c = shade(c, 1.2)
                img.putpixel((x0 + x, y0 + y), c)
    return img


def sprite(rows, palette):
    """Un objet de 16 x 16 dessiné en caractères : chaque caractère est une couleur de la palette."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                img.putpixel((x, y), hexc(palette[ch]))
    return img


# Les bijoux du joaillier : o or ou cuivre, h reflet, s ombre, g gemme, w éclat de la gemme.
JEWELS = {
    "amethyst_brooch": ([
        "................",
        "................",
        "......ssss......",
        "....soooooos....",
        "...soohgggoos...",
        "...sohgwgggos...",
        "..soogwggggoos..",
        "..sooggggggoos..",
        "..soogggggsoos..",
        "...soggggssos...",
        "...soosgssoos...",
        "....soooooos....",
        "......ssss......",
        "................",
        "................",
        "................"], {"o": "#d27a4a", "h": "#f0b48c", "s": "#7a3f22", "g": "#9a5cc6", "w": "#e5c8f5"}),
    "gold_ring": ([
        "................",
        "................",
        "......gggg......",
        ".....gwggsg.....",
        "......gssg......",
        ".....oohhoo.....",
        "....oh....oo....",
        "...oh......os...",
        "...o.......os...",
        "...o.......os...",
        "...oo.....oos...",
        "....oo...oos....",
        ".....oooooss....",
        "......ssss......",
        "................",
        "................"], {"o": "#f2c94c", "h": "#fff1a8", "s": "#a07418", "g": "#2f55c8", "w": "#9ab4ff"}),
    "diadem": ([
        "................",
        "................",
        "................",
        ".......w........",
        "......wgw.......",
        "..o...ggg...o...",
        "..oo..sgs..oo...",
        "..ooo.ooo.ooo...",
        "..ohooohooohoo..",
        "..ooooooooooooo.",
        "..hhhhhhhhhhhhh.",
        "..sssssssssssss.",
        "................",
        "................",
        "................",
        "................"], {"o": "#f2c94c", "h": "#fff1a8", "s": "#a07418", "g": "#5fe3e0", "w": "#dffcff"}),
}


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print(path.relative_to(ROOT))


def main():
    for i in range(len(OUTFITS)):
        save(citizen_skin(i), TEXTURES / f"entity/citizen/citizen_{i}.png")
    save(house_plaque(), TEXTURES / "block/house_plaque.png")
    save(house_plaque("valid"), TEXTURES / "block/house_plaque_valid.png")
    save(house_plaque("invalid"), TEXTURES / "block/house_plaque_invalid.png")
    save(spawn_egg(), TEXTURES / "item/citizen_spawn_egg.png")
    save(hats(), TEXTURES / "entity/citizen/hats.png")
    save(annex_board(), TEXTURES / "block/annex_board.png")
    for name, (rows, palette) in JEWELS.items():
        save(sprite(rows, palette), TEXTURES / f"item/{name}.png")


if __name__ == "__main__":
    main()
