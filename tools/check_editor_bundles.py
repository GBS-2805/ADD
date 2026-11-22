#!/usr/bin/env python3
"""Prove the real APKs carry the editor the routes expect.

    python3 tools/check_editor_bundles.py
    python3 tools/check_editor_bundles.py --research dist/ADD-4.0-editor.apk \\
        --release dist/ADD-3.9.apk

The Robolectric routing test proves both branches, but it passes a build flag. This
gate opens the two signed bundles instead: the research APK must ship
assets/www/editor/index.html and every local asset that page loads, and the release
APK must ship no editor assets at all.

Exit 2 means "no APKs here", which is normal in CI: APKs are built locally. The
defaults are the newest matching bundle in dist/: ADD-*-editor.apk for research and
the newest other ADD-*.apk for release. The retired twin lane is excluded so a
stale research build cannot stand in for the shipping APK.
"""

from __future__ import annotations

import argparse
import fnmatch
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"
EDITOR_ROOT = "assets/www/editor/"
INDEX = EDITOR_ROOT + "index.html"
LOCAL_REF = re.compile(r'(?:src|href)="(\./[^"]+)"')

# Could not run: the bundles are not here, which is the normal state on a clean
# checkout and in CI. Distinct from pass and fail, as check_all.py expects.
SKIP = 2


def newest(pattern: str, *exclude: str, directory: Path = DIST) -> Path | None:
    """The latest bundle matching a glob, ignoring other lanes' builds."""
    found = [
        path for path in directory.glob(pattern)
        if not any(fnmatch.fnmatch(path.name, skip) for skip in exclude)
    ]
    return max(found, key=lambda path: path.stat().st_mtime, default=None)


def research_problems(research: Path) -> list[str]:
    """A research APK must ship the page and every local asset it loads."""
    found = []
    try:
        with zipfile.ZipFile(research) as apk:
            names = set(apk.namelist())
            if INDEX not in names:
                found.append(f"research APK has no {INDEX}")
            else:
                page = apk.read(INDEX).decode("utf-8", "replace")
                for ref in LOCAL_REF.findall(page):
                    entry = EDITOR_ROOT + ref[2:]
                    if entry not in names:
                        found.append(f"research APK page loads missing {entry}")
            if not any(name.startswith(EDITOR_ROOT) for name in names):
                found.append(f"research APK has no {EDITOR_ROOT} entries")
    except (OSError, zipfile.BadZipFile) as exc:
        found.append(f"cannot read research APK {research}: {exc}")
    return found


def release_problems(release: Path) -> list[str]:
    """A release APK must ship no editor assets at all."""
    found = []
    try:
        with zipfile.ZipFile(release) as apk:
            leaked = sorted(name for name in apk.namelist() if name.startswith(EDITOR_ROOT))
            if leaked:
                found.append(
                    f"release APK still ships {len(leaked)} editor asset(s), starting {leaked[0]}")
    except (OSError, zipfile.BadZipFile) as exc:
        found.append(f"cannot read release APK {release}: {exc}")
    return found


def problems(research: Path, release: Path) -> list[str]:
    """What is wrong with the two bundles; empty means the invariant holds."""
    return research_problems(research) + release_problems(release)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--research", type=Path, help="the research APK to inspect")
    parser.add_argument("--release", type=Path, help="the release APK to inspect")
    parser.add_argument("--expect", choices=("research", "release"),
                        help="check one freshly built APK instead of comparing the pair")
    parser.add_argument("--apk", type=Path, help="the APK for --expect")
    args = parser.parse_args()

    if args.expect:
        if not args.apk:
            parser.error("--expect needs --apk")
        found = research_problems(args.apk) if args.expect == "research" else release_problems(args.apk)
        if found:
            for problem in found:
                print(f"editor bundle: {problem}", file=sys.stderr)
            return 1
        if args.expect == "research":
            with zipfile.ZipFile(args.apk) as apk:
                shipped = sum(1 for name in apk.namelist() if name.startswith(EDITOR_ROOT))
            print(f"editor bundle: research ships {shipped} editor asset(s) and the page")
        else:
            print("editor bundle: release ships none")
        return 0

    research = args.research or newest("ADD-*-editor.apk")
    release = args.release or newest("ADD-*.apk", "*-editor.apk", "*-twin.apk")
    missing = [name for name, path in (("research", research), ("release", release)) if not path]
    if missing:
        print(f"editor bundles: no {' or '.join(missing)} APK in dist/; "
              "run ./build.sh editor and ./build.sh", file=sys.stderr)
        return SKIP

    found = problems(research, release)
    if found:
        for problem in found:
            print(f"editor bundles: {problem}", file=sys.stderr)
        return 1

    with zipfile.ZipFile(research) as apk:
        shipped = sum(1 for name in apk.namelist() if name.startswith(EDITOR_ROOT))
    print(f"editor bundles: research ships {shipped} editor asset(s); "
          f"release ships none")
    return 0


if __name__ == "__main__":
    sys.exit(main())
