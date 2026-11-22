#!/usr/bin/env python3
"""One Editor row, and every build reaches the editor it carries.

The side panel used to carry a second "Editor · web" row. This gate keeps one
row and checks its two destinations: a build that stages www/editor opens the
browser canvas from the row and from the shelf's Editor Tools card, and a build
without it opens the native Quick editor.

    python3 tools/check_editor_rows.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DRAWER = ROOT / "app/res/layout/drawer.xml"
MAIN = ROOT / "app/src/add/MainActivity.java"
SHELF = ROOT / "app/src/add/ShelfActivity.java"


def audit(drawer: str, main: str, shelf: str) -> list[str]:
    """Problems found in the three files; an empty list means the invariant holds."""
    problems = []

    ids = re.findall(r'android:id="@\+id/(linkEditor[A-Za-z]*)"', drawer)
    if ids != ["linkEditor"]:
        problems.append(f"drawer editor ids {ids or 'none'} (want exactly ['linkEditor'])")

    labels = re.findall(r'android:text="(Editor[^"]*)"', drawer)
    if labels != ["Editor"]:
        problems.append(f"drawer editor labels {labels or 'none'} (want exactly ['Editor'])")

    row = main[main.find("R.id.linkEditor)"):][:300]
    for needle, why in (
        ("ShelfActivity.editorBundled(this)", "the row must ask what this build carries"),
        ("openEditor(", "the row must use the one editor route"),
    ):
        if needle not in row:
            problems.append(f"MainActivity Editor row: {why} ({needle!r} missing)")

    editor = main[main.find("void openEditor(boolean bundled)"):][:400]
    for needle, why in (
        ('openShelf("editor-web")', "the research build must open the bundled page"),
        ("EditorActivity.open(this)", "the shipping build must open Quick"),
    ):
        if needle not in editor:
            problems.append(f"MainActivity editor route: {why} ({needle!r} missing)")

    if "editorBundled(this)" not in shelf:
        problems.append("ShelfActivity Tools card must ask what this build carries")

    card = shelf[shelf.find('"editor".equals(slug)'):][:400]
    for needle, why in (
        ('"editor-web"', "the research build must open the bundled page"),
        ("EditorActivity.open(this)", "the shipping build must open Quick"),
    ):
        if needle not in card:
            problems.append(f"ShelfActivity Editor Tools card: {why} ({needle!r} missing)")

    if '"www/editor/index.html"' not in shelf:
        problems.append("editorBundled must test the staged page, www/editor/index.html")

    return problems


def main() -> int:
    problems = audit(
        DRAWER.read_text(encoding="utf-8"),
        MAIN.read_text(encoding="utf-8"),
        SHELF.read_text(encoding="utf-8"),
    )
    if problems:
        for problem in problems:
            print(f"editor rows: {problem}", file=sys.stderr)
        return 1
    print("editor rows: one Editor row; research opens editor-web, release opens Quick")
    return 0


if __name__ == "__main__":
    sys.exit(main())
