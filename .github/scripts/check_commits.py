"""Vérifie les commits d'une pull request : titre conventionnel, et pour feat/fix/perf, les deux
lignes Changelog-fr / Changelog-en que lit git-cliff (voir BRIEF.md, « Commits et changelog »).

Usage : python3 check_commits.py <base> <head>
"""
import re
import subprocess
import sys

TITLE = re.compile(r"^(feat|fix|perf|refactor|docs|test|chore|build|ci|style|revert)(\([^)]+\))?!?: .+")
NEEDS_CHANGELOG = ("feat", "fix", "perf")


def main(base, head):
    raw = subprocess.run(["git", "log", "--no-merges", "--format=%H%x00%B%x01", f"{base}..{head}"],
                         check=True, capture_output=True, text=True, encoding="utf-8").stdout
    errors = []
    for entry in filter(None, (e.strip() for e in raw.split("\x01"))):
        sha, _, message = entry.partition("\x00")
        title = message.splitlines()[0] if message else ""
        short = sha[:8]
        match = TITLE.match(title)
        if not match:
            errors.append(f"{short} : titre non conventionnel : « {title} »")
            continue
        if match.group(1) in NEEDS_CHANGELOG:
            for key in ("Changelog-fr:", "Changelog-en:"):
                if not any(line.startswith(key) and line[len(key):].strip() for line in message.splitlines()):
                    errors.append(f"{short} : ligne « {key} » manquante ({title})")
    for e in errors:
        print(f"::error::{e}")
    if errors:
        print(f"{len(errors)} problème(s) : le changelog serait incomplet.")
        return 1
    print("Commits conformes.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
