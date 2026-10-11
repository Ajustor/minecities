"""Assemble les images filmées par les scènes en jeu en petites vidéos animées.

Usage : python tools/captures/animate.py <dossier des captures> <dossier de sortie>

Les scènes filment avec CityScenes.film(...) : des captures nommées « NNNN_film_<nom>_<image> ».
Chaque film devient <nom>.webp (léger, lu par les navigateurs et la plupart des visionneuses) et
<nom>.gif (lu partout) ; les captures fixes sont copiées telles quelles.
"""
import re
import shutil
import sys
from collections import defaultdict
from pathlib import Path

from PIL import Image

FRAME = re.compile(r"^\d+_film_(?P<name>.+)_(?P<index>\d{3})\.png$")
# 10 images par seconde : une image toutes les deux secondes de jeu passe en un dixième de seconde.
FRAME_MS = 100
GIF_WIDTH = 360


def main(source, target):
    source, target = Path(source), Path(target)
    target.mkdir(parents=True, exist_ok=True)
    films = defaultdict(list)
    for path in sorted(source.glob("*.png")):
        match = FRAME.match(path.name)
        if match:
            films[match["name"]].append((int(match["index"]), path))
        else:
            shutil.copy2(path, target / path.name)
    for name, frames in films.items():
        images = [Image.open(p).convert("RGB") for _, p in sorted(frames)]
        # La dernière image reste un peu à l'écran.
        durations = [FRAME_MS] * (len(images) - 1) + [1500]
        images[0].save(target / f"{name}.webp", save_all=True, append_images=images[1:], duration=durations,
                       loop=0, quality=80)
        # Le GIF, plus lourd : plus petit, une image sur deux (à vitesse égale).
        small = [im.resize((GIF_WIDTH, round(im.height * GIF_WIDTH / im.width))) for im in images[::2]]
        small_durations = [FRAME_MS * 2] * (len(small) - 1) + [1500]
        small[0].save(target / f"{name}.gif", save_all=True, append_images=small[1:], duration=small_durations,
                      loop=0, optimize=True)
        print(f"{name} : {len(images)} images -> {name}.webp, {name}.gif")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
