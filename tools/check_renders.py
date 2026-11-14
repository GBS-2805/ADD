#!/usr/bin/env python3
"""Fail when the published gallery no longer matches the pages it came from.

`render_pages.js` writes `docs/renders/manifest.json` with a hash of the page,
the asset map and the shared CSS/JS, plus the final WebP hash. This checker is
Python-only, so a stale gallery fails on a push even when no browser runs.

    python3 tools/check_renders.py --check
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
MANIFEST = DOCS / "renders/manifest.json"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def source_hash(docs: Path, page: str, assets: dict[str, str]) -> str:
    """Hash the same inputs, in the same order, as render_pages.js."""
    files = [
        docs / page / "index.html",
        docs / "data/assets.json",
        docs / "assets" / assets.get("site.css", "site.css"),
        docs / "assets" / assets.get("site.js", "site.js"),
    ]
    digest = hashlib.sha256()
    for path in files:
        digest.update(path.relative_to(docs).as_posix().encode("utf-8"))
        digest.update(path.read_bytes())
    return digest.hexdigest()


def check(docs: Path = DOCS) -> list[str]:
    manifest_path = docs / "renders/manifest.json"
    if not manifest_path.exists():
        return ["missing renders/manifest.json (run tools/render_pages.js)"]
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        return ["renders/manifest.json is not readable JSON: %s" % error]

    assets = json.loads((docs / "data/assets.json").read_text(encoding="utf-8"))["files"]
    problems = []
    shots = manifest.get("shots", {})
    expected = set()
    for name, shot in sorted(shots.items()):
        image = docs / "renders" / shot.get("image", "")
        expected.add(image.name)
        if not image.exists():
            problems.append("%s: missing %s" % (name, image.name))
            continue
        actual_image = sha256(image.read_bytes())
        if actual_image != shot.get("imageSha"):
            problems.append("%s: image %s changed" % (name, image.name))
        try:
            actual_source = source_hash(docs, shot.get("page", ""), assets)
        except OSError as error:
            problems.append("%s: source input missing: %s" % (name, error.filename))
        else:
            if actual_source != shot.get("source"):
                problems.append("%s: page source changed" % name)

    for image in sorted((docs / "renders").glob("*.webp")):
        if image.name not in expected:
            problems.append("%s: orphan capture not named by the manifest" % image.name)
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--docs", type=Path, default=DOCS)
    parser.add_argument("--check", action="store_true",
                        help="read-only freshness check (default)")
    args = parser.parse_args()
    problems = check(args.docs.resolve())
    if problems:
        for problem in problems:
            print("  " + problem)
        print("renders: %d problem(s)" % len(problems))
        return 1
    print("renders: manifest and captures are current")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
