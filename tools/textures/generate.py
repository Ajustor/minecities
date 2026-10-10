"""Génère les textures du mod : peaux des citoyens, hôtel de ville, plaque de maison, œuf.

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
STONE = hexc("#8f8f8f")
STONE_DARK = hexc("#616161")
GOLD = hexc("#f2c94c")
BLUE = hexc("#2f4f8f")


def planks(rng, img, y0=0, y1=16):
    for y in range(y0, y1):
        for x in range(16):
            c = jitter(rng, OAK, 0.05)
            if y % 4 == 3:
                c = shade(OAK_DARK, 1.05)
            elif (x + (y // 4) * 5) % 8 == 0:
                c = shade(OAK, 0.85)
            img.putpixel((x, y), c)


def stone_base(rng, img, y0):
    for y in range(y0, 16):
        for x in range(16):
            c = jitter(rng, STONE, 0.07)
            if (y - y0) % 4 == 3 or (x + ((y - y0) // 4) * 4) % 8 == 0:
                c = jitter(rng, STONE_DARK, 0.05)
            img.putpixel((x, y), c)


def frame(img, color):
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, color)


def town_hall_side():
    rng = random.Random(1)
    img = Image.new("RGBA", (16, 16))
    planks(rng, img, 0, 12)
    stone_base(rng, img, 12)
    for y in range(12):
        img.putpixel((0, y), OAK_DARK)
        img.putpixel((15, y), OAK_DARK)
    # Une fenêtre à croisillons.
    for y in range(3, 9):
        for x in range(5, 11):
            img.putpixel((x, y), hexc("#9cc7d9") if (x + y) % 5 else hexc("#c9e4ef"))
    for i in range(5, 11):
        img.putpixel((i, 5), OAK_DARK)
    for j in range(3, 9):
        img.putpixel((7, j), OAK_DARK)
    for i in range(4, 12):
        img.putpixel((i, 2), OAK_DARK)
        img.putpixel((i, 9), OAK_DARK)
    for j in range(2, 10):
        img.putpixel((4, j), OAK_DARK)
        img.putpixel((11, j), OAK_DARK)
    return img


def town_hall_front():
    rng = random.Random(2)
    img = Image.new("RGBA", (16, 16))
    planks(rng, img, 0, 12)
    stone_base(rng, img, 12)
    for y in range(12):
        img.putpixel((0, y), OAK_DARK)
        img.putpixel((15, y), OAK_DARK)
    # Une bannière bleue frappée d'une cloche dorée.
    for y in range(1, 12):
        for x in range(4, 12):
            img.putpixel((x, y), jitter(rng, BLUE, 0.05))
    img.putpixel((4, 11), OAK)
    img.putpixel((11, 11), OAK)
    for x in range(3, 13):
        img.putpixel((x, 1), OAK_DARK)
    bell = [
        "..##..",
        ".####.",
        ".####.",
        ".####.",
        "######",
        "..##..",
    ]
    for j, row in enumerate(bell):
        for i, ch in enumerate(row):
            if ch == "#":
                img.putpixel((5 + i, 3 + j), shade(GOLD, 0.85 if i > 3 else 1.0))
    return img


def town_hall_top():
    rng = random.Random(3)
    img = Image.new("RGBA", (16, 16))
    planks(rng, img)
    frame(img, OAK_DARK)
    for y in range(5, 11):
        for x in range(5, 11):
            if abs(x - 7.5) + abs(y - 7.5) <= 3.5:
                img.putpixel((x, y), jitter(rng, GOLD, 0.06))
    return img


def house_plaque():
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


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print(path.relative_to(ROOT))


def main():
    for i in range(len(OUTFITS)):
        save(citizen_skin(i), TEXTURES / f"entity/citizen/citizen_{i}.png")
    save(town_hall_side(), TEXTURES / "block/town_hall_side.png")
    save(town_hall_front(), TEXTURES / "block/town_hall_front.png")
    save(town_hall_top(), TEXTURES / "block/town_hall_top.png")
    save(house_plaque(), TEXTURES / "block/house_plaque.png")
    save(spawn_egg(), TEXTURES / "item/citizen_spawn_egg.png")


if __name__ == "__main__":
    main()
