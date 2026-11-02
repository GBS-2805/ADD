#!/usr/bin/env python3
"""Build docs/icons/index.html from the whole free Lineicons SVG set.

Lineicons publishes its free icons under MIT in the LineiconsHQ/Lineicons
repository (assets/svgs/regular, 600+ glyphs). The Pro set on lineicons.com is
a paid product and is not part of that repository, so it is neither fetched
nor reproduced here — this script takes every SVG the free repo ships and
nothing else.

Each glyph is recoloured to currentColor so it inherits the surrounding text
colour, and keeps its own viewBox (most are 24x24, a few are 25x24).

Re-run to refresh:  python3 tools/fetch_icons.py
"""

import datetime
import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEMPLATE = ROOT / "tools/icons_template.html"
OUTPUT = ROOT / "docs/icons/index.html"
DATA = ROOT / "docs/data/icons.json"

TREE_URL = "https://data.jsdelivr.com/v1/packages/gh/LineiconsHQ/Lineicons@main"
SOURCE = ("https://cdn.jsdelivr.net/gh/LineiconsHQ/Lineicons@main"
          "/assets/svgs/regular/%s.svg")
SVG_DIR = "assets/svgs/regular/"

COLOR = re.compile(r'(fill|stroke)="#[0-9a-fA-F]{3,8}"')


def free_names():
    """Every icon name the free repository publishes, alphabetically."""
    for attempt in range(3):
        try:
            raw = urllib.request.urlopen(TREE_URL, timeout=30).read()
            break
        except Exception as error:  # network hiccup, retry
            print("tree fetch failed (%s), retrying" % error, file=sys.stderr)
            time.sleep(2 * (attempt + 1))
    else:
        raise SystemExit("could not list the repository; nothing written")

    def walk(nodes, prefix=""):
        for node in nodes:
            path = prefix + "/" + node["name"] if prefix else node["name"]
            if node.get("type") == "directory":
                yield from walk(node.get("files", []), path)
            else:
                yield path

    names = []
    for path in walk(json.loads(raw).get("files", [])):
        if path.startswith(SVG_DIR) and path.endswith(".svg"):
            names.append(path[len(SVG_DIR):-4])
    if not names:
        raise SystemExit("free icon directory came back empty; nothing written")
    return sorted(set(names))


def fetch(name):
    """The raw SVG text, or None when that name is not served."""
    for attempt in range(4):
        try:
            return urllib.request.urlopen(SOURCE % name, timeout=30).read().decode("utf-8")
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            time.sleep(1 + attempt)
        except Exception:
            time.sleep(1 + attempt)
    return None


def prepare(raw):
    """Returns (root attributes, body) with every hard-coded colour removed."""
    head = re.search(r"<svg([^>]*)>", raw, re.S)
    inner = re.search(r"<svg[^>]*>(.*)</svg>", raw, re.S)
    if not head or not inner:
        return None

    def attr(key, default=""):
        found = re.search(r'%s="([^"]*)"' % key, head.group(1))
        return found.group(1) if found else default

    body = COLOR.sub(r'\1="currentColor"', inner.group(1))
    body = re.sub(r"\s*\n\s*", "", body.strip())
    if not re.search(r"<(path|circle|rect|line|polyline|polygon|ellipse)\b", body):
        return None  # nothing to draw

    # Lineicons draws with filled paths, so the root keeps fill="none" and the
    # colour arrives on the children. A stroke-drawn file keeps its stroke.
    parts = ['width="%s"' % attr("width", "24"),
             'height="%s"' % attr("height", "24"),
             'viewBox="%s"' % attr("viewBox", "0 0 24 24")]
    if 'stroke=' in head.group(1) or 'stroke="currentColor"' in body:
        parts.append('stroke="currentColor"')
    parts.append('fill="none"' if "fill=\"currentColor\"" in body else 'fill="currentColor"')
    return " ".join(parts), body


def build(name):
    raw = fetch(name)
    if raw is None:
        return name, None
    prepared = prepare(raw)
    if prepared is None:
        return name, None
    attrs, body = prepared
    return name, {"name": name, "attrs": attrs, "body": body}


def main():
    names = free_names()
    print("free set: %d names" % len(names))

    icons, missing = [], []
    with ThreadPoolExecutor(max_workers=8) as pool:
        for name, icon in pool.map(build, names):
            if icon is None:
                missing.append(name)
            else:
                icons.append(icon)
    icons.sort(key=lambda item: item["name"])

    page = TEMPLATE.read_text(encoding="utf-8")
    page = page.replace("__COUNT__", str(len(icons)))
    page = page.replace("__ICONS__", json.dumps(icons, ensure_ascii=False))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(page, encoding="utf-8")

    # The same glyphs as a JSON catalog, which is what the Icons app reads and
    # what tools/build_catalog.py counts when it checks the pages.
    DATA.parent.mkdir(parents=True, exist_ok=True)
    DATA.write_text(json.dumps({
        "generated": datetime.date.today().isoformat(),
        "source": "LineiconsHQ/Lineicons",
        "licence": "MIT",
        "count": len(icons),
        "missing": missing,
        "icons": icons,
    }, ensure_ascii=False) + "\n", encoding="utf-8")
    print("data: %s (%d bytes)" % (DATA.relative_to(ROOT), DATA.stat().st_size))

    print("icons: %d" % len(icons))
    print("missing: %s" % (", ".join(missing) if missing else "none"))
    print("wrote %s (%d bytes)" % (OUTPUT.relative_to(ROOT), OUTPUT.stat().st_size))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
