#!/usr/bin/env python3
"""Build Twin into docs/twin/ as a page of the shelf.

    python3 tools/build_twin.py
    python3 tools/build_twin.py --check

Twin lives at /twin/, not at the site root, so its bundle must be built with that
base or it would emit /assets/... and overwrite the shelf's own asset directory.
`docs/twin/` is therefore generated, never edited: the build writes it, then this
script adds the canonical/social metadata block every modern shelf page is required
to carry, because the block has to survive the next build.

`--check` fails when docs/twin/ is missing, stale against a fresh build, or has lost
its metadata block -- the same shape as the other derived-file gates.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TWIN = ROOT / "twin"
TARGET = ROOT / "docs" / "twin"
BASE = "/twin/"

sys.path.insert(0, str(ROOT / "tools"))
import seo_meta  # noqa: E402 - the tools directory is on the path above


def build(destination: Path) -> None:
    """Run Vite with the shelf's base path, writing into `destination`."""
    if not (TWIN / "node_modules").is_dir():
        raise SystemExit("build_twin: run `npm ci` in twin/ first")
    command = ["npx", "vite", "build", "--outDir", str(destination), "--emptyOutDir"]
    result = subprocess.run(command, cwd=TWIN, env={**__import__("os").environ,
                                                     "TWIN_BASE": BASE},
                            capture_output=True, text=True)
    if result.returncode != 0:
        sys.stderr.write(result.stdout[-2000:] + result.stderr[-2000:])
        raise SystemExit("build_twin: vite build failed")


def with_metadata(directory: Path) -> None:
    """Add the shelf's canonical/social block to the built index page."""
    path = directory / "index.html"
    text = path.read_text(encoding="utf-8")
    text = seo_meta.insert(text, seo_meta.block(path, seo_meta.origin(), text))
    path.write_text(text, encoding="utf-8")


def files(directory: Path) -> dict[str, bytes]:
    if not directory.is_dir():
        return {}
    return {p.relative_to(directory).as_posix(): p.read_bytes()
            for p in sorted(directory.rglob("*")) if p.is_file()}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true",
                        help="compare docs/twin/ with a fresh build; write nothing")
    args = parser.parse_args()

    if not args.check:
        build(TARGET)
        with_metadata(TARGET)
        print("build_twin: wrote %s (%d files)" % (TARGET.relative_to(ROOT), len(files(TARGET))))
        return 0

    with tempfile.TemporaryDirectory() as scratch:
        fresh = Path(scratch) / "twin"
        build(fresh)
        # The metadata block names the page's own path, so it is applied in place
        # before the comparison; comparing before it would flag every build stale.
        expected = files(fresh)
        expected_page = (fresh / "index.html").read_text(encoding="utf-8")
        fresh_page = seo_meta.insert(expected_page, seo_meta.block(TARGET / "index.html",
                                                                 seo_meta.origin(),
                                                                 expected_page))
        expected["index.html"] = fresh_page.encode("utf-8")

    actual = files(TARGET)
    if not actual:
        print("build_twin: docs/twin/ is missing; run python3 tools/build_twin.py")
        return 1
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    changed = sorted(name for name in set(expected) & set(actual)
                     if expected[name] != actual[name])
    if missing or extra or changed:
        for label, names in (("missing", missing), ("stale", changed), ("unexpected", extra)):
            for name in names:
                print("build_twin: %s %s" % (label, name))
        print("build_twin: run python3 tools/build_twin.py, then commit the result")
        return 1
    print("build_twin: OK - %d files match a fresh build" % len(actual))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
