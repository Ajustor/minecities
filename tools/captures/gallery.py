"""Copie les captures de la scène gallery dans le site (site/img/buildings), en JPEG plus légers.

Usage :
  ./gradlew :26.2:runClientGameTest -PminecitiesGametest=gallery
  python tools/captures/gallery.py [dossier des captures]

gallery_style_<style>.png devient style_<style>.jpg, gallery_building_<nom>.png building_<nom>.jpg,
gallery_monuments.png monuments.jpg. Il faut Pillow (pip install pillow).
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "versions/26.2/build/run/clientGameTest/screenshots"
OUT = ROOT / "site/img/buildings"
WIDTH = 1280


def frame(image):
    """
    Recadre en 16:9 autour des bâtiments, avec une marge. Sur chaque ligne, le fond (ciel, brume de
    l'horizon ou pelouse) est d'une couleur presque uniforme, la plus courante : un bâtiment, c'est ce
    qui s'en écarte nettement.
    """
    small = image.resize((image.width // 4, image.height // 4))
    px = small.load()
    medians = []
    for y in range(small.height):
        row = [px[x, y] for x in range(small.width)]
        medians.append([sorted(c[i] for c in row)[len(row) // 2] for i in range(3)])
    backgrounds = [list(m) for m in {tuple(m) for m in medians}]
    xs, ys = [], []
    for y in range(small.height):
        # Le fond de toutes les lignes : l'horizon est courbe, ses bords n'ont pas la couleur de leur ligne.
        near = backgrounds
        for x in range(small.width):
            color = px[x, y]
            if all(sum(abs(color[i] - m[i]) for i in range(3)) > 70 for m in near):
                xs.append(x)
                ys.append(y)
    if not xs:
        return image
    # Sans les quelques points égarés (les bords flous de l'horizon) : du 1er au 99e centile.
    xs.sort()
    ys.sort()
    cut = len(xs) // 100
    left, right = xs[cut] * 4, (xs[-1 - cut] + 1) * 4
    top, bottom = ys[cut] * 4, (ys[-1 - cut] + 1) * 4
    margin = 0.1 * max(right - left, bottom - top)
    left, right, top, bottom = left - margin, right + margin, top - margin, bottom + margin
    # Élargit le côté court pour tenir en 16:9, puis rentre dans l'image.
    width, height = right - left, bottom - top
    if width / height < 16 / 9:
        grow = (height * 16 / 9 - width) / 2
        left, right = left - grow, right + grow
    else:
        grow = (width * 9 / 16 - height) / 2
        top, bottom = top - grow, bottom + grow
    width, height = right - left, bottom - top
    if width > image.width or height > image.height:
        return image
    left = min(max(0, left), image.width - width)
    top = min(max(0, top), image.height - height)
    return image.crop((round(left), round(top), round(left + width), round(top + height)))


def main(shots):
    OUT.mkdir(parents=True, exist_ok=True)
    count = 0
    for shot in sorted(shots.glob("*gallery_*.png")):
        # Le jeu préfixe parfois les captures d'un numéro : on garde ce qui suit « gallery_ ».
        name = shot.stem[shot.stem.index("gallery_") + len("gallery_"):]
        image = frame(Image.open(shot).convert("RGB"))
        if image.width != WIDTH:
            image = image.resize((WIDTH, round(image.height * WIDTH / image.width)), Image.LANCZOS)
        image.save(OUT / f"{name}.jpg", "JPEG", quality=82, optimize=True, progressive=True)
        count += 1
    print(f"{count} image(s) dans {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else SHOTS)
