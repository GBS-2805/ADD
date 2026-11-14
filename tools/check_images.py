#!/usr/bin/env python3
"""Check images on the modern pages: dimensions, loading and formats.

Classic pages are frozen and deliberately excluded. Images without a `src`
are ignored because the compressor fills its previews from File objects and
has no intrinsic source to describe until a photo is chosen.

    python3 tools/check_images.py --check
"""

from __future__ import annotations

import argparse
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parent.parent
GALLERY = "renders"
LARGE_SVG = 500 * 1024


class Images(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.found: list[tuple[int, dict[str, str]]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "img":
            self.found.append((self.getpos()[0], {key: value or "" for key, value in attrs}))


def _pages(docs: Path) -> list[Path]:
    return sorted(path for path in docs.rglob("index.html")
                  if "classic" not in path.relative_to(docs).parts)


def _local_image(page: Path, src: str, docs: Path) -> Path | None:
    if not src or src.startswith(("data:", "http:", "https:", "//")):
        return None
    path = unquote(urlsplit(src).path)
    if not path:
        return None
    if path.startswith("/"):
        return docs / path.lstrip("/")
    return page.parent / path


def check_page(page: Path, docs: Path) -> list[str]:
    try:
        text = page.read_text(encoding="utf-8")
    except OSError as error:
        return ["%s: cannot read (%s)" % (page, error)]
    parser = Images()
    parser.feed(text)
    problems = []
    high = []
    for index, (line, attrs) in enumerate(parser.found):
        src = attrs.get("src", "").strip()
        if not src:
            continue
        label = "%s:%d" % (page.relative_to(docs.parent), line)
        for name in ("width", "height"):
            if not attrs.get(name, "").strip().isdigit():
                problems.append("%s: missing or non-numeric %s" % (label, name))
        if attrs.get("decoding", "").lower() != "async":
            problems.append("%s: missing decoding=async" % label)
        priority = attrs.get("fetchpriority", "").lower()
        if priority == "high":
            high.append((index, label))
        elif attrs.get("loading", "").lower() != "lazy":
            problems.append("%s: image below the fold is not loading=lazy" % label)

        local = _local_image(page, src, docs)
        if local is None:
            continue
        if page.parent.name == GALLERY:
            suffix = local.suffix.lower()
            if suffix not in {".webp", ".avif"}:
                problems.append("%s: gallery capture is %s, expected WebP or AVIF"
                                % (label, suffix or "no extension"))
        if local.suffix.lower() == ".svg" and local.exists() and local.stat().st_size > LARGE_SVG:
            problems.append("%s: large SVG (%.0f KiB)" % (label, local.stat().st_size / 1024))

    if len(high) > 1:
        problems.append("%s: %d fetchpriority=high images; expected at most one"
                        % (page.relative_to(docs.parent), len(high)))
    if high and high[0][0] != 0:
        problems.append("%s: fetchpriority=high is not the first image"
                        % page.relative_to(docs.parent))
    return problems


def problems(root: Path = ROOT) -> list[str]:
    docs = Path(root) / "docs"
    found = []
    for page in _pages(docs):
        found.extend(check_page(page, docs))
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
        print("images: %d problem(s)" % len(found))
        return 1
    count = 0
    for page in _pages(Path(args.root) / "docs"):
        parser = Images()
        parser.feed(page.read_text(encoding="utf-8"))
        count += sum(1 for _, attrs in parser.found if attrs.get("src"))
    print("images: OK - %d local images across the modern pages" % count)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
