#!/usr/bin/env python3
"""Check the facts the shelf prints about itself.

The README, the agent reference, the catalog and the engine notes are all
hand-written, but they repeat numbers that other files already know. This gate
compares those claims with the catalog, the generated page tree and the engine
test XML so a stale sentence fails a push instead of reaching a reader.

    python3 tools/check_claims.py --check
    python3 tools/check_claims.py --check --engine

Engine mode is deliberately separate: it needs a completed Gradle run, which
is why `check_all.py` calls it after the engine gate in `--visual`. Historical
sentences are allowed; only a line containing `Current baseline:` is treated as
the live claim.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODERN_PAGES = 16
CLASSIC_PAGES = 16
ENGINE_BASELINE = 209


def _read(path: Path, problems: list[str]) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except OSError as error:
        problems.append("%s: cannot read (%s)" % (path, error))
        return ""


def _human_bytes(count: int) -> str:
    return "%.0f MB" % (count / (1024 * 1024))


def _modern_pages(root: Path) -> list[Path]:
    docs = root / "docs"
    if not docs.is_dir():
        return []
    return sorted(path for path in docs.rglob("index.html")
                  if "classic" not in path.relative_to(docs).parts)


def _classic_pages(root: Path) -> list[Path]:
    classic = root / "docs" / "classic"
    if not classic.is_dir():
        return []
    return sorted(classic.rglob("index.html"))


def _engine_totals(root: Path) -> tuple[int, int, int, int, int]:
    results = root / "tools" / "engine-spike" / "app" / "build" / "test-results"
    files = sorted(results.rglob("*.xml")) if results.is_dir() else []
    tests = failures = errors = skipped = 0
    for path in files:
        try:
            suite = ET.parse(path).getroot()
        except (OSError, ET.ParseError):
            continue
        tests += int(suite.get("tests", 0) or 0)
        failures += int(suite.get("failures", 0) or 0)
        errors += int(suite.get("errors", 0) or 0)
        skipped += int(suite.get("skipped", 0) or 0)
    return len(files), tests, failures, errors, skipped


def _current_baselines(root: Path, problems: list[str]) -> list[tuple[Path, int]]:
    claims: list[tuple[Path, int]] = []
    for relative in ("NATIVE_EDITOR_V2.md", "tools/engine-spike/README.md"):
        path = root / relative
        text = _read(path, problems)
        found = False
        for line in text.splitlines():
            if "Current baseline:" not in line:
                continue
            found = True
            match = re.search(r"(\d+)\s+tests?", line)
            if not match:
                problems.append("%s: Current baseline has no test count" % path)
                continue
            claims.append((path, int(match.group(1))))
        if text and not found:
            problems.append("%s: missing a Current baseline: line" % path)
    return claims


def problems(root: Path = ROOT, engine: bool = False) -> list[str]:
    """Return every claim that disagrees with the tree, with the source path."""
    root = Path(root)
    found: list[str] = []
    docs = root / "docs"
    readme = root / "README.md"
    reference = docs / "agent-reference.md"
    readme_text = _read(readme, found)
    reference_text = _read(reference, found)

    modern = _modern_pages(root)
    classic = _classic_pages(root)
    if len(modern) != MODERN_PAGES:
        found.append("%s: %d modern pages, expected %d" % (docs, len(modern), MODERN_PAGES))
    if len(classic) != CLASSIC_PAGES:
        found.append("%s: %d classic pages, expected %d"
                     % (docs / "classic", len(classic), CLASSIC_PAGES))

    if "Sixteen pages" not in readme_text:
        found.append("%s: missing the 'Sixteen pages' claim" % readme)
    if "docs/compress/" not in readme_text:
        found.append("%s: missing the docs/compress/ row" % readme)
    if "sixteen pages" not in reference_text:
        found.append("%s: missing the 'sixteen pages' claim" % reference)

    published_path = docs / "data" / "tools.json"
    catalog_path = root / "tools" / "catalog.json"
    try:
        published = json.loads(published_path.read_text(encoding="utf-8"))
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        found.append("%s: cannot read catalog data (%s)" % (root, error))
    else:
        published_tool = next((tool for tool in published.get("tools", [])
                               if tool.get("id") == "add"), None)
        catalog_tool = next((tool for tool in catalog.get("tools", [])
                             if tool.get("id") == "add"), None)
        if not published_tool or not catalog_tool:
            found.append("%s: no ADD tool in the published or source catalog" % root)
        else:
            if (catalog_tool.get("bytes") is not None
                    and published_tool.get("bytes") != catalog_tool.get("bytes")):
                found.append("%s: published bytes disagree with %s"
                             % (published_path, catalog_path))
            version = published_tool.get("version", "")
            byte_count = int(published_tool.get("bytes", 0) or 0)
            label = _human_bytes(byte_count)
            if "ADD %s" % version not in readme_text:
                found.append("%s: does not name ADD %s" % (readme, version))
            if label not in readme_text:
                found.append("%s: APK size %s does not match %s bytes"
                             % (readme, label, byte_count))

    if engine:
        count, tests, failures, errors, _skipped = _engine_totals(root)
        if count == 0:
            found.append("%s: no engine test XML; run tools/engine-spike/verify-suite.sh"
                         % (root / "tools/engine-spike/app/build/test-results"))
        elif tests != ENGINE_BASELINE or failures or errors:
            found.append("%s: %d tests, %d failures, %d errors; expected %d clean tests"
                         % (root / "tools/engine-spike/app/build/test-results",
                            tests, failures, errors, ENGINE_BASELINE))
        for path, claimed in _current_baselines(root, found):
            if claimed != tests:
                found.append("%s: Current baseline says %d tests; XML has %d"
                             % (path, claimed, tests))

    return found


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true",
                        help="read-only check (the default)")
    parser.add_argument("--engine", action="store_true",
                        help="also compare the engine baseline with test XML")
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)

    found = problems(args.root, args.engine)
    if found:
        for item in found:
            print("  " + item)
        print("claims: %d problem(s)" % len(found))
        return 1
    suffix = " and engine baseline" if args.engine else ""
    print("claims: OK - 16 modern pages, 16 classic pages, APK size%s" % suffix)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
