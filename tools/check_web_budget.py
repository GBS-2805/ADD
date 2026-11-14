#!/usr/bin/env python3
"""Measure modern pages and enforce a measured +10% static byte budget.

    python3 tools/check_web_budget.py --write
    python3 tools/check_web_budget.py --check

The baseline in `tools/web_budget.json` records the measurement and the budget.
Classic pages have a separate frozen ceiling; a new modern page without a
baseline fails until it is measured deliberately.
"""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parent.parent
BASELINE = ROOT / "tools" / "web_budget.json"
HEADROOM = 0.10
FIELDS = ("html", "css", "js", "fonts", "first_viewport", "images", "total")
FONT_SUFFIXES = {".woff", ".woff2", ".ttf", ".otf"}


class Assets(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.stylesheets: list[str] = []
        self.scripts: list[str] = []
        self.images: list[str] = []
        self.styles: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs = {key: value or "" for key, value in attrs}
        if tag == "link" and "stylesheet" in attrs.get("rel", "").split():
            self.stylesheets.append(attrs.get("href", ""))
        elif tag == "script" and attrs.get("src"):
            self.scripts.append(attrs["src"])
        elif tag in {"img", "source"}:
            source = attrs.get("src", "")
            if not source and attrs.get("srcset"):
                source = attrs["srcset"].split(",", 1)[0].strip().split(" ", 1)[0]
            if source:
                self.images.append(source)
        elif tag == "style":
            self.styles.append("")


def _local(docs: Path, base: Path, reference: str) -> Path | None:
    reference = unquote(urlsplit(reference).path)
    if not reference or reference.startswith(("data:", "http:", "https:", "#")):
        return None
    path = docs / reference.lstrip("/") if reference.startswith("/") else base / reference
    return path if path.is_file() else None


def _pages(docs: Path) -> list[Path]:
    return sorted(path for path in docs.rglob("index.html")
                  if "classic" not in path.relative_to(docs).parts)


def measure_page(docs: Path, page: Path) -> dict[str, int]:
    data = page.read_bytes()
    parser = Assets()
    parser.feed(data.decode("utf-8", "replace"))
    css_paths: set[Path] = set()
    js_paths: set[Path] = set()
    image_paths: list[Path] = []
    font_paths: set[Path] = set()
    for reference in parser.stylesheets:
        path = _local(docs, page.parent, reference)
        if path:
            css_paths.add(path)
    for path in sorted(css_paths):
        css_text = path.read_text(encoding="utf-8", errors="replace")
        for match in re.finditer(r"url\(\s*['\"]?([^)'\"]+)", css_text):
            font = _local(docs, path.parent, match.group(1))
            if font and font.suffix.lower() in FONT_SUFFIXES:
                font_paths.add(font)
    for reference in parser.scripts:
        path = _local(docs, page.parent, reference)
        if path:
            js_paths.add(path)
    for reference in parser.images:
        path = _local(docs, page.parent, reference)
        if path:
            image_paths.append(path)

    values = {
        "html": len(data),
        "css": sum(path.stat().st_size for path in css_paths),
        "js": sum(path.stat().st_size for path in js_paths),
        "fonts": sum(path.stat().st_size for path in font_paths),
        "first_viewport": image_paths[0].stat().st_size if image_paths else 0,
        "images": sum(path.stat().st_size for path in dict.fromkeys(image_paths)),
    }
    values["total"] = sum(values[field] for field in FIELDS if field != "total")
    return values


def _key(docs: Path, page: Path) -> str:
    return page.relative_to(docs).as_posix()


def measure(root: Path = ROOT) -> tuple[dict[str, dict[str, int]], dict[str, int]]:
    docs = Path(root) / "docs"
    pages = {_key(docs, page): measure_page(docs, page) for page in _pages(docs)}
    classic = []
    classic_root = docs / "classic"
    if classic_root.is_dir():
        classic = [measure_page(docs, page) for page in classic_root.rglob("index.html")]
    ceiling = {
        "html": max((item["html"] for item in classic), default=0),
        "total": max((item["total"] for item in classic), default=0),
    }
    return pages, ceiling


def _budget(values: dict[str, int]) -> dict[str, int]:
    return {field: int(math.ceil(value * (1 + HEADROOM))) for field, value in values.items()}


def write_baseline(root: Path = ROOT, path: Path | None = None) -> Path:
    pages, ceiling = measure(root)
    target = path or (Path(root) / "tools" / "web_budget.json")
    payload = {
        "schema": 1,
        "headroom": HEADROOM,
        "pages": {key: {"measured": values, "budget": _budget(values)}
                  for key, values in sorted(pages.items())},
        "classic_ceiling": ceiling,
    }
    target.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    return target


def problems(root: Path = ROOT, baseline: Path | None = None) -> list[str]:
    root = Path(root)
    baseline = baseline or (root / "tools" / "web_budget.json")
    try:
        payload = json.loads(baseline.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        return ["%s: cannot read baseline (%s)" % (baseline, error)]
    pages, ceiling = measure(root)
    budgets = payload.get("pages", {})
    found = []
    for key, measured in sorted(pages.items()):
        if key not in budgets:
            found.append("%s: new page has no measured budget" % key)
            continue
        for field in FIELDS:
            allowed = int(budgets[key].get("budget", {}).get(field, -1))
            if measured[field] > allowed:
                found.append("%s: %s %d > budget %d" % (key, field, measured[field], allowed))
    for key in sorted(set(budgets) - set(pages)):
        found.append("%s: baseline lists a page that no longer exists" % key)
    frozen = payload.get("classic_ceiling", {})
    for field in ("html", "total"):
        if ceiling[field] > int(frozen.get(field, -1)):
            found.append("classic: %s %d > frozen ceiling %d"
                         % (field, ceiling[field], int(frozen.get(field, -1))))
    return found


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="check against the baseline (default)")
    parser.add_argument("--write", action="store_true", help="measure and rewrite the baseline")
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    parser.add_argument("--baseline", type=Path, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)

    if args.write:
        path = write_baseline(args.root, args.baseline)
        pages, _ = measure(args.root)
        print("budget: wrote %s for %d modern pages" % (path, len(pages)))
        return 0
    found = problems(args.root, args.baseline)
    if found:
        for item in found:
            print("  " + item)
        print("budget: %d problem(s)" % len(found))
        return 1
    pages, _ = measure(args.root)
    print("budget: OK - %d modern pages within measured +%.0f%%"
          % (len(pages), HEADROOM * 100))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
