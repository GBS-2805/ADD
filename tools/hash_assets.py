#!/usr/bin/env python3
"""Point every page at content-hashed copies of the shared CSS and JS.

The stylesheet and the chrome change under names that never changed, so a host can
only give them a short cache. Hashing the name once per release lets the CDN keep
them for a year and a page still picks up a new palette the moment it is deployed.

    python3 tools/hash_assets.py [--check]

Writes `docs/assets/site.<hash>.css` and `.js`, records the pair in
`docs/data/assets.json`, and rewrites the reference in every page under `docs/`
(hand-written or generated), outside the frozen `docs/classic/` archive. Run it
after changing either file and before the generators; `--check` fails when a page
points at a stale name, which is what a generator run without this step looks like.
"""

import argparse
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
ASSETS = DOCS / "assets"
MAP = DOCS / "data/assets.json"
SHARED = ("site.css", "site.js")
ARCHIVE = DOCS / "classic"

# Matches a plain or already-hashed reference, at any depth, with an old query.
REFERENCE = re.compile(r"assets/(site(?:\.\w+)?\.(css|js))(?:\?[^\"']*)?")


def names():
    """The hashed filename for each shared asset, from what is on disk now."""
    found = {}
    for name in SHARED:
        digest = hashlib.sha256((ASSETS / name).read_bytes()).hexdigest()[:10]
        found[name] = "site.%s%s" % (digest, pathlib.Path(name).suffix)
    return found


def current():
    """The committed map, so a generator can write the name the pages already use."""
    return json.loads(MAP.read_text(encoding="utf-8"))["files"]


def pin(text, mapping=None):
    """Rewrite every shared-asset reference in `text` to its hashed name."""
    mapping = mapping or current()

    def swap(match):
        base = "site." + match.group(2)
        return "assets/" + mapping[base]

    return REFERENCE.sub(swap, text)


def pages():
    return sorted(page for page in DOCS.rglob("*.html") if ARCHIVE not in page.parents)


def pin_all(mapping):
    changed = []
    for page in pages():
        text = page.read_text(encoding="utf-8")
        after = pin(text, mapping)
        if after != text:
            page.write_text(after, encoding="utf-8")
            changed.append(page)
    return changed


def prune(keep):
    dropped = []
    for pattern in ("site.*.css", "site.*.js"):
        for path in ASSETS.glob(pattern):
            if path.name not in keep:
                path.unlink()
                dropped.append(path.name)
    return dropped


def stale(mapping):
    """Pages whose references are not the current hashed names."""
    return [page for page in pages()
            if pin(page.read_text(encoding="utf-8"), mapping)
            != page.read_text(encoding="utf-8")]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true",
                        help="write nothing; fail when a page points at a stale name")
    args = parser.parse_args()

    mapping = names()

    if args.check:
        problems = []
        if not MAP.exists():
            problems.append("docs/data/assets.json is missing")
        elif current() != mapping:
            problems.append("docs/data/assets.json predates the current CSS or JS")
        for page in stale(mapping):
            problems.append("%s points at an old name" % page.relative_to(ROOT).as_posix())
        if problems:
            print("assets: stale")
            for problem in problems[:10]:
                print("  " + problem)
            if len(problems) > 10:
                print("  ... and %d more" % (len(problems) - 10))
            print("run python3 tools/hash_assets.py, then commit the result")
            sys.exit(1)
        print("assets: OK - %s" % ", ".join(mapping[n] for n in SHARED))
        return

    for name, hashed in mapping.items():
        target = ASSETS / hashed
        source = ASSETS / name
        if not target.exists() or target.read_bytes() != source.read_bytes():
            target.write_bytes(source.read_bytes())
    dropped = prune(set(mapping.values()))
    MAP.parent.mkdir(parents=True, exist_ok=True)
    MAP.write_text(json.dumps({"files": mapping}, indent=2) + "\n", encoding="utf-8")
    changed = pin_all(mapping)
    print("assets: %s -> %s, %d pages rewritten%s" % (
        ", ".join(SHARED), ", ".join(mapping[n] for n in SHARED), len(changed),
        (", dropped " + ", ".join(dropped)) if dropped else ""))


if __name__ == "__main__":
    main()
