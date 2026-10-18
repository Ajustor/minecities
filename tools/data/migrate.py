"""Les données du mod pour les versions de Minecraft plus récentes que celle des sources.

Les fichiers de `src/main/resources/data` sont écrits au format de la version active (26.2). Ce script
en dérive ceux des versions suivantes, dans `src/overlays/<version>/`, que le build pose par-dessus
pour ces versions (voir ARCHITECTURE.md). On n'écrit donc les données qu'une fois :

    python tools/data/migrate.py           # régénère les overlays
    python tools/data/migrate.py --check   # vérifie que les overlays commités sont à jour (CI)

Chaque migration est une fonction pure (dossier, chemin, JSON) -> JSON, appliquée dans l'ordre des
versions. Un overlay ne garde que les fichiers que la migration change. Les règles viennent des
données du jeu lui-même, comparées d'une version à l'autre.
"""
from __future__ import annotations

import copy
import json
import os
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SOURCES = os.path.join(ROOT, "src", "main", "resources")
OVERLAYS = os.path.join(ROOT, "src", "overlays")
# Les familles de données au format du jeu (les autres, propres au mod, ne changent pas).
VANILLA_KINDS = {"advancement", "loot_table", "villager_trade", "worldgen", "dimension_type", "recipe", "predicate",
                 "item_modifier"}


# --------------------------------------------------------------------------------------------- 26.3

def _int_if_whole(node):
    """2.0 -> 2 : les fournisseurs entiers de la 26.3 refusent les flottants ; les flottants acceptent les entiers."""
    if isinstance(node, float) and node.is_integer():
        return int(node)
    if isinstance(node, dict):
        return {k: _int_if_whole(v) for k, v in node.items()}
    if isinstance(node, list):
        return [_int_if_whole(v) for v in node]
    return node


def _is_condition(node):
    return isinstance(node, dict) and "condition" in node


def _one_condition(conditions):
    """Une liste de conditions de butin devient une seule condition (« toutes » s'il y en a plusieurs)."""
    if len(conditions) == 1:
        return conditions[0]
    return {"type": "minecraft:all_of", "terms": conditions}


def _loot_26_3(node):
    """Conditions et modificateurs de butin : `condition`/`function` deviennent `type`, les listes
    `conditions` une seule `condition`, les listes `functions` un `modifier`."""
    if isinstance(node, list):
        return [_loot_26_3(v) for v in node]
    if not isinstance(node, dict):
        return node
    out = {}
    for key, value in node.items():
        value = _loot_26_3(value)
        if key in ("condition", "function") and isinstance(value, str):
            out["type"] = value
        elif key == "destination" and node.get("function") == "minecraft:exploration_map" \
                and isinstance(value, str) and not value.startswith("#"):
            # La destination d'une carte d'exploration : une étiquette de structures, écrite comme telle.
            out[key] = "#" + value
        elif key == "conditions" and isinstance(value, list):
            out["condition"] = _one_condition(value)
        elif key == "functions" and isinstance(value, list):
            out["modifier"] = value
        elif key == "given_item_modifiers":
            out["given_item_modifier"] = value
        elif key != "terms" and isinstance(value, list) and value and all(_is_condition(v) for v in node[key]):
            # Une liste de conditions sous un autre nom (le « player » d'un progrès) : une seule condition.
            out[key] = _one_condition(value)
        else:
            out[key] = value
    return out


def _spawn_26_3(entry):
    """minCount/maxCount deviennent count : un entier, ou un intervalle uniforme."""
    entry = dict(entry)
    low, high = entry.pop("minCount", None), entry.pop("maxCount", None)
    if low is not None:
        entry["count"] = low if low == high else {"type": "minecraft:uniform", "min_inclusive": low,
                                                  "max_inclusive": high}
    return entry


def migrate_26_3(kind, path, data):
    if kind in ("advancement", "loot_table", "villager_trade", "predicate", "item_modifier"):
        data = _loot_26_3(data)
    if kind == "worldgen" and path.startswith("worldgen/structure/") and "spawn_overrides" in data:
        data = copy.deepcopy(data)
        for override in data["spawn_overrides"].values():
            override["spawns"] = [_spawn_26_3(s) for s in override.get("spawns", [])]
    if kind == "worldgen" and path.startswith("worldgen/biome/") and "spawners" in data:
        # Les apparitions passent dans les attributs du biome.
        data = dict(data)
        spawners = data.pop("spawners")
        costs = data.pop("spawn_costs", {})
        attributes = dict(data.get("attributes", {}))
        attributes["minecraft:gameplay/natural_mob_spawns"] = {
            "argument": {
                "spawn_costs": costs,
                "spawns_by_category": {cat: [_spawn_26_3(s) for s in spawns] for cat, spawns in spawners.items()},
            },
            "modifier": "overlay",
        }
        data["attributes"] = attributes
    if kind == "dimension_type":
        data = copy.deepcopy(data)
        bed = data.get("attributes", {}).get("minecraft:gameplay/bed_rule")
        if isinstance(bed, dict) and "explodes" in bed:
            bed["destroy_on_use"] = bed.pop("explodes")
    return _int_if_whole(data)


# Dans l'ordre : chaque version part du résultat de la précédente.
MIGRATIONS = [("26.3", migrate_26_3)]


# --------------------------------------------------------------------------------------------- outil

def sources():
    data = os.path.join(SOURCES, "data")
    for base, _, files in os.walk(data):
        for name in sorted(files):
            if not name.endswith(".json"):
                continue
            full = os.path.join(base, name)
            rel = os.path.relpath(full, data).replace(os.sep, "/")
            namespace, kind_path = rel.split("/", 1)
            kind = kind_path.split("/", 1)[0]
            if kind in VANILLA_KINDS:
                yield rel, kind, kind_path, full


def render(data):
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def build():
    """Les fichiers de chaque overlay : {version: {chemin relatif à data/: texte}}."""
    out = {version: {} for version, _ in MIGRATIONS}
    for rel, kind, kind_path, full in sources():
        with open(full, encoding="utf-8") as f:
            original = json.load(f)
        current = original
        for version, migrate in MIGRATIONS:
            current = migrate(kind, kind_path, current)
            if current != original:
                out[version][rel] = render(current)
    return out


def main():
    check = "--check" in sys.argv
    wrong = []
    for version, files in build().items():
        target = os.path.join(OVERLAYS, version, "data")
        existing = {}
        if os.path.isdir(target):
            for base, _, names in os.walk(target):
                for name in names:
                    full = os.path.join(base, name)
                    with open(full, encoding="utf-8", newline="") as f:
                        existing[os.path.relpath(full, target).replace(os.sep, "/")] = f.read()
        if check:
            for rel in sorted(set(files) | set(existing)):
                if files.get(rel) != existing.get(rel):
                    wrong.append(f"{version}/data/{rel}")
            continue
        if os.path.isdir(target):
            shutil.rmtree(target)
        for rel, text in files.items():
            path = os.path.join(target, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
        print(f"{version} : {len(files)} fichiers")
    if wrong:
        print("Ces overlays ne correspondent pas aux données (lancer python tools/data/migrate.py et committer) :",
              file=sys.stderr)
        for w in wrong:
            print("  " + w, file=sys.stderr)
        sys.exit(1)
    if check:
        print("Overlays de données à jour.")


if __name__ == "__main__":
    main()
