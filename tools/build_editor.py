#!/usr/bin/env python3
"""Build the Editor into docs/editor/ as a page of the shelf.

    python3 tools/build_twin.py
    python3 tools/build_twin.py --check

The Editor lives at /editor/, not at the site root, so its bundle must be built
with that base or it would emit /assets/... and overwrite the shelf's own asset
directory. `docs/editor/` is therefore generated, never edited: the build writes it, then this
script adds the canonical/social metadata block every modern shelf page is required
to carry, because the block has to survive the next build.

`--check` fails when docs/editor/ is missing, stale against a fresh build, or has lost
its metadata block -- the same shape as the other derived-file gates.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "twin"          # the editor's source tree keeps its repo name
TARGET = ROOT / "docs" / "editor"
BASE = "/editor/"

sys.path.insert(0, str(ROOT / "tools"))
import seo_meta  # noqa: E402 - the tools directory is on the path above


def build(destination: Path) -> None:
    """Run Vite with the shelf's base path, writing into `destination`."""
    if not (APP / "node_modules").is_dir():
        raise SystemExit("build_editor: run `npm ci` in twin/ first")
    command = ["npx", "vite", "build", "--outDir", str(destination), "--emptyOutDir"]
    # TWIN_BASE is the app's own build switch; its value is the shelf path, so the
    # bundle emits /editor/assets/... instead of /assets/....
    env = {**os.environ, "TWIN_BASE": BASE}
    result = subprocess.run(command, cwd=APP, env=env, capture_output=True, text=True)
    if result.returncode != 0:
        sys.stderr.write(result.stdout[-2000:] + result.stderr[-2000:])
        raise SystemExit("build_editor: vite build failed")


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
                        help="compare docs/editor/ with a fresh build; write nothing")
    args = parser.parse_args()

    if not args.check:
        build(TARGET)
        with_metadata(TARGET)
        print("build_editor: wrote %s (%d files)" % (TARGET.relative_to(ROOT), len(files(TARGET))))
        return 0

    with tempfile.TemporaryDirectory() as scratch:
        fresh = Path(scratch) / "editor"
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
        print("build_editor: docs/editor/ is missing; run python3 tools/build_editor.py")
        return 1
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    changed = sorted(name for name in set(expected) & set(actual)
                     if expected[name] != actual[name])
    if missing or extra or changed:
        for label, names in (("missing", missing), ("stale", changed), ("unexpected", extra)):
            for name in names:
                print("build_editor: %s %s" % (label, name))
        print("build_editor: run python3 tools/build_twin.py, then commit the result")
        return 1
    print("build_editor: OK - %d files match a fresh build" % len(actual))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
