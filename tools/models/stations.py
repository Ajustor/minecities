"""Génère les postes de travail du mod, un par métier : modèle, états, objet, butin, recette.

Usage : python tools/models/stations.py
Chaque poste est un établi (plateau, quatre pieds, une nappe) chargé d'accessoires qui disent le
métier, faits de textures vanilla : pas de PNG à dessiner. Les fichiers sont écrits dans
src/main/resources ; modifier ce script puis le relancer plutôt que de retoucher le JSON à la main.
Si le jar client de Minecraft est dans le cache de Gradle, le script vérifie que chaque texture existe.
"""
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/minecities"
DATA = ROOT / "src/main/resources/data/minecities"

# Pour chaque métier : le bois du plateau et des pieds, la nappe, les accessoires (de, à, texture),
# et ce qu'il faut ajouter à une table de craft pour le fabriquer.
STATIONS = {
    "farmer": ("oak", "green_wool", [((2, 13, 3), (8, 17, 9), "hay_block_top"), ((10, 13, 9), (14, 16, 13), "pumpkin_side")],
               "minecraft:wheat_seeds"),
    "cook": ("spruce", "white_wool", [((3, 13, 3), (9, 18, 9), "cauldron_side"), ((11, 13, 10), (14, 15, 13), "pumpkin_side")],
             "minecraft:bowl"),
    "lumberjack": ("oak", "brown_wool", [((2, 13, 4), (10, 18, 12), "stripped_oak_log"), ((11, 13, 6), (14, 14, 10), "iron_block")],
                   "#minecraft:saplings"),
    "breeder": ("spruce", "brown_wool", [((2, 13, 3), (9, 17, 10), "hay_block_side"), ((10, 13, 9), (14, 16, 13), "white_wool")],
                "minecraft:wheat"),
    "fisherman": ("birch", "blue_wool", [((2, 13, 3), (9, 18, 10), "barrel_side"), ((10, 13, 9), (14, 14, 13), "prismarine")],
                  "minecraft:string"),
    "builder": ("oak", "orange_wool", [((2, 13, 3), (8, 16, 9), "bricks"), ((9, 13, 8), (14, 17, 13), "stone_bricks"),
                                       ((2, 16, 3), (8, 18, 9), "scaffolding_top")],
                "minecraft:brick"),
    "courier": ("birch", "light_blue_wool", [((2, 13, 3), (12, 14, 11), "cartography_table_top"), ((11, 13, 10), (15, 17, 14), "barrel_side")],
                "minecraft:paper"),
    "guard": ("dark_oak", "red_wool", [((3, 13, 4), (9, 19, 6), "iron_block"), ((10, 13, 8), (14, 17, 12), "grindstone_side")],
              "minecraft:iron_ingot"),
    "archer": ("birch", "lime_wool", [((2, 13, 3), (10, 14, 11), "fletching_table_top"), ((10, 13, 11), (15, 18, 12), "target_side")],
               "minecraft:feather"),
    "inventor": ("dark_oak", "gray_wool", [((2, 13, 3), (10, 14, 11), "smithing_table_top"), ((10, 13, 9), (15, 18, 14), "carved_pumpkin")],
                 "minecraft:redstone"),
    "doctor": ("birch", "white_wool", [((3, 13, 3), (6, 17, 6), "glass"), ((7, 13, 3), (10, 16, 6), "glass"),
                                       ((10, 13, 9), (14, 14, 13), "brewing_stand_base")],
               "minecraft:glass_bottle"),
    "merchant": ("dark_oak", "purple_wool", [((2, 13, 3), (9, 17, 10), "barrel_top"), ((10, 13, 9), (14, 16, 13), "emerald_block")],
                 "minecraft:emerald"),
    "baker": ("spruce", "yellow_wool", [((2, 13, 3), (10, 19, 11), "furnace_front"), ((11, 13, 10), (14, 15, 13), "hay_block_side")],
              "minecraft:sugar"),
    "smith": ("dark_oak", "black_wool", [((2, 13, 4), (10, 17, 10), "anvil"), ((11, 13, 9), (15, 18, 14), "blast_furnace_front")],
              "minecraft:raw_iron"),
    "mason": ("spruce", "light_gray_wool", [((2, 13, 3), (10, 15, 11), "stonecutter_top"), ((11, 13, 9), (15, 17, 13), "stone_bricks")],
              "minecraft:cobblestone"),
    "weaver": ("birch", "pink_wool", [((2, 13, 3), (10, 14, 11), "loom_top"), ((11, 13, 9), (14, 16, 12), "white_wool"),
                                      ((11, 16, 9), (14, 18, 12), "red_wool")],
               "#minecraft:wool"),
    "beekeeper": ("oak", "yellow_wool", [((2, 13, 3), (9, 19, 10), "beehive_front"), ((10, 13, 9), (14, 16, 13), "honeycomb_block")],
                  "minecraft:honeycomb"),
    "librarian": ("dark_oak", "red_wool", [((2, 13, 3), (12, 19, 7), "bookshelf"), ((9, 13, 9), (14, 15, 13), "white_wool")],
                  "minecraft:book"),
    "taverner": ("spruce", "brown_wool", [((2, 13, 3), (9, 19, 10), "barrel_side"), ((10, 13, 9), (14, 17, 13), "cauldron_side")],
                 "minecraft:barrel"),
}

FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}


def element(frm, to, texture):
    """Un cube, et pour chaque face un coin de texture à sa taille (un accessoire peut dépasser du bloc)."""
    dx, dy, dz = (min(16, round(to[i] - frm[i], 3)) for i in range(3))
    uv = {"north": [0, 0, dx, dy], "south": [0, 0, dx, dy], "east": [0, 0, dz, dy], "west": [0, 0, dz, dy],
          "up": [0, 0, dx, dz], "down": [0, 0, dx, dz]}
    return {"from": list(frm), "to": list(to),
            "faces": {side: {"texture": texture, "uv": uv[side]} for side in uv}}


def model(wood, cloth, props):
    textures = {"particle": f"minecraft:block/{wood}_planks", "wood": f"minecraft:block/{wood}_planks",
                "leg": f"minecraft:block/stripped_{wood}_log", "cloth": f"minecraft:block/{cloth}"}
    elements = [element((0, 11, 1), (16, 13, 15), "#wood")]
    for x, z in ((1, 2), (13, 2), (1, 12), (13, 12)):
        elements.append(element((x, 0, z), (x + 2, 11, z + 2), "#leg"))
    elements.append(element((1, 13, 2), (15, 13.1, 14), "#cloth"))
    for i, (frm, to, texture) in enumerate(props):
        key = f"p{i}"
        textures[key] = f"minecraft:block/{texture}"
        # Les accessoires posent sur la nappe.
        elements.append(element((frm[0], frm[1] + 0.1, frm[2]), (to[0], to[1] + 0.1, to[2]), "#" + key))
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements,
            "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
                        "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                        "fixed": {"scale": [0.5, 0.5, 0.5]},
                        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                                  "scale": [0.375, 0.375, 0.375]},
                        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]}}}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(path.relative_to(ROOT))


def vanilla_textures():
    """Les textures de blocs du jeu, si le jar client est dans le cache de Gradle ; sinon rien."""
    cache = Path.home() / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf"
    jars = sorted(cache.glob("*/minecraft-clientonly-deobf-*.jar")) if cache.exists() else []
    if not jars:
        return None
    with zipfile.ZipFile(jars[-1]) as jar:
        prefix = "assets/minecraft/textures/block/"
        return {n[len(prefix):-4] for n in jar.namelist() if n.startswith(prefix) and n.endswith(".png")}


def main():
    known = vanilla_textures()
    axe = []
    for job, (wood, cloth, props, ingredient) in STATIONS.items():
        name = f"{job}_station"
        if known is not None:
            for texture in [f"{wood}_planks", f"stripped_{wood}_log", cloth] + [p[2] for p in props]:
                if texture not in known:
                    raise SystemExit(f"{name} : texture inconnue minecraft:block/{texture}")
        write(ASSETS / f"models/block/{name}.json", model(wood, cloth, props))
        write(ASSETS / f"blockstates/{name}.json", {"variants": {
            f"facing={f}": ({"model": f"minecities:block/{name}", "y": y} if y else {"model": f"minecities:block/{name}"})
            for f, y in FACINGS.items()}})
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"minecities:block/{name}"}})
        write(DATA / f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minecities:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        # Une table de craft et l'objet du métier.
        write(DATA / f"recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                             "ingredients": ["minecraft:crafting_table", ingredient],
                                             "result": {"id": f"minecities:{name}", "count": 1}})
        axe.append(f"minecities:{name}")
    # Les postes se cassent vite à la hache.
    tag = ROOT / "src/main/resources/data/minecraft/tags/block/mineable/axe.json"
    values = json.loads(tag.read_text(encoding="utf-8"))["values"]
    values = [v for v in values if not v.endswith("_station")] + axe
    write(tag, {"values": values})


if __name__ == "__main__":
    main()
