#!/usr/bin/env python3
"""Check reduced-motion coverage and the 500 ms motion ceiling.

The shared stylesheet and every page-local style block that animates must carry
a `prefers-reduced-motion: reduce` block. Durations are measured statically;
this is a source gate, not a camera.

    python3 tools/check_motion.py --check
"""

from __future__ import annotations

import argparse
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAX_MS = 500
# Add (relative source, animation name) pairs here only with a reviewed reason.
ALLOWLIST: set[tuple[str, str]] = set()
DURATION = re.compile(r"(?<![\w.-])(\d*\.?\d+)(ms|s)\b")
MOTION = re.compile(r"(?:^|[;{])\s*(?:transition|animation)(?:-[a-z-]+)?\s*:")


class Styles(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.blocks: list[str] = []
        self.current: list[str] | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "style":
            self.current = []

    def handle_data(self, data: str) -> None:
        if self.current is not None:
            self.current.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag == "style" and self.current is not None:
            self.blocks.append("".join(self.current))
            self.current = None


def _pages(docs: Path) -> list[Path]:
    return sorted(path for path in docs.rglob("index.html")
                  if "classic" not in path.relative_to(docs).parts)


def _issues(label: str, text: str) -> list[str]:
    problems = []
    motion = list(MOTION.finditer(text))
    if motion and "@media (prefers-reduced-motion: reduce)" not in text:
        problems.append("%s: motion has no prefers-reduced-motion block" % label)
    for match in MOTION.finditer(text):
        declaration = text[match.start():text.find(";", match.start())]
        for number, unit in DURATION.findall(declaration):
            milliseconds = float(number) * (1000 if unit == "s" else 1)
            if milliseconds <= MAX_MS:
                continue
            name = next(iter(re.findall(r"animation(?:-name)?\s*:\s*([\w-]+)", declaration)), "")
            if (label, name) in ALLOWLIST:
                continue
            problems.append("%s: motion %.0fms exceeds %dms" % (label, milliseconds, MAX_MS))
    return problems


def problems(root: Path = ROOT) -> list[str]:
    root = Path(root)
    docs = root / "docs"
    found = []
    css_files = sorted((docs / "assets").glob("*.css"))
    for path in css_files:
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        if not MOTION.search(text):
            continue
        found.extend(_issues(path.relative_to(root).as_posix(), text))
    for page in _pages(docs):
        styles = Styles()
        styles.feed(page.read_text(encoding="utf-8"))
        for index, block in enumerate(styles.blocks, 1):
            if not MOTION.search(block):
                continue
            found.extend(_issues("%s:style%d" % (page.relative_to(root), index), block))
    return found


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="read-only check (default)")
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)

    found = problems(args.root)
    if found:
        for item in found:
            print("  " + item)
        print("motion: %d problem(s)" % len(found))
        return 1
    print("motion: OK - reduced-motion coverage, all durations <= %dms" % MAX_MS)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
