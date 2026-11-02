#!/usr/bin/env python3
"""Build docs/icons/index.html and the two icon catalogs from free SVG sets.

Two MIT sets, both taken whole:

  * Lineicons -- LineiconsHQ/Lineicons, assets/svgs/regular (the free set; the
    Pro icons sold on lineicons.com are not in that repository and are not here);
  * Coolicons -- krystonschwarze/coolicons, the line style with regular corners
    (icons/svg/line/regular). That repository also publishes fill, duotone, bulk
    and duocolor styles with sharp and round corners; those are variants of the
    same drawings, so one style is the set.

Every glyph is recoloured to currentColor so it inherits the surrounding text
colour, and keeps its own viewBox. The page loads the two JSON catalogs at run
time instead of inlining 1,864 glyphs twice, so the HTML stays small and the
files the phone app reads are the files the page renders.

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
DATA_LINE = ROOT / "docs/data/icons.json"
DATA_COOL = ROOT / "docs/data/coolicons.json"
TREE_CACHE = pathlib.Path("/tmp/coolicons-tree.json")

LINE_TREE = "https://data.jsdelivr.com/v1/packages/gh/LineiconsHQ/Lineicons@main"
LINE_URL = ("https://cdn.jsdelivr.net/gh/LineiconsHQ/Lineicons@main"
            "/assets/svgs/regular/%s.svg")
LINE_DIR = "assets/svgs/regular/"

COOL_TREE = ("https://api.github.com/repos/krystonschwarze/coolicons"
             "/git/trees/main?recursive=1")
COOL_URL = ("https://raw.githubusercontent.com/krystonschwarze/coolicons/main"
            "/icons/svg/line/regular/%s.svg")
COOL_DIR = "icons/svg/line/regular/"

COLOR = re.compile(r'(fill|stroke)="#[0-9a-fA-F]{3,8}"')


def line_names():
    """Every icon name the free Lineicons repository publishes, alphabetically."""
    for attempt in range(3):
        try:
            raw = urllib.request.urlopen(LINE_TREE, timeout=30).read()
            break
        except Exception as error:
            print("lineicons tree failed (%s), retrying" % error, file=sys.stderr)
            time.sleep(2 * (attempt + 1))
    else:
        raise SystemExit("could not list Lineicons; nothing written")

    def walk(nodes, prefix=""):
        for node in nodes:
            path = prefix + "/" + node["name"] if prefix else node["name"]
            if node.get("type") == "directory":
                yield from walk(node.get("files", []), path)
            else:
                yield path

    names = [path[len(LINE_DIR):-4]
             for path in walk(json.loads(raw).get("files", []))
             if path.startswith(LINE_DIR) and path.endswith(".svg")]
    if not names:
        raise SystemExit("Lineicons free directory came back empty; nothing written")
    return sorted(set(names))


def cool_names():
    """Every name in the Coolicons line style with regular corners."""
    # The GitHub tree endpoint is rate limited for anonymous callers, so a tree
    # already fetched by a probe is reused before spending another request.
    cached = next((path for path in (TREE_CACHE, pathlib.Path("/tmp/cool.json")) if path.exists()), None)
    if cached:
        payload = json.loads(cached.read_text(encoding="utf-8"))
    else:
        for attempt in range(3):
            try:
                request = urllib.request.Request(COOL_TREE, headers={"User-Agent": "compress-site"})
                payload = json.loads(urllib.request.urlopen(request, timeout=45).read())
                break
            except Exception as error:
                print("coolicons tree failed (%s), retrying" % error, file=sys.stderr)
                time.sleep(2 * (attempt + 1))
        else:
            raise SystemExit("could not list Coolicons; nothing written")
        TREE_CACHE.write_text(json.dumps(payload), encoding="utf-8")

    names = [node["path"][len(COOL_DIR):-4]
             for node in payload.get("tree", [])
             if node.get("type") == "blob"
             and node["path"].startswith(COOL_DIR)
             and node["path"].endswith(".svg")]
    if not names:
        raise SystemExit("Coolicons line style came back empty; nothing written")
    return sorted(set(names))


def fetch(url):
    for attempt in range(4):
        try:
            return urllib.request.urlopen(url, timeout=30).read().decode("utf-8")
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

    parts = ['width="%s"' % attr("width", "24"),
             'height="%s"' % attr("height", "24"),
             'viewBox="%s"' % attr("viewBox", "0 0 24 24")]
    # Lineicons draws with filled paths, Coolicons with strokes. Take the mode
    # from the body so neither set comes out as a solid blob.
    if 'fill="currentColor"' in body:
        parts.append('fill="currentColor"')
    elif 'stroke="currentColor"' in body or "stroke=" in head.group(1):
        parts.append('fill="none"')
        parts.append('stroke="currentColor"')
    else:
        parts.append('fill="currentColor"')
    return " ".join(parts), body


def download(names, template):
    def build(name):
        raw = fetch(template % name)
        if raw is None:
            return name, None
        prepared = prepare(raw)
        if prepared is None:
            return name, None
        attrs, body = prepared
        return name, {"name": name, "attrs": attrs, "body": body}

    out, missing = [], []
    with ThreadPoolExecutor(max_workers=8) as pool:
        for name, icon in pool.map(build, names):
            if icon is None:
                missing.append(name)
            else:
                out.append(icon)
    out.sort(key=lambda item: item["name"])
    return out, missing


def write_catalog(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
    print("data: %-28s %9d bytes" % (path.relative_to(ROOT), path.stat().st_size))


def main():
    line_list = line_names()
    cool_list = cool_names()
    print("Lineicons: %d names | Coolicons line/regular: %d names" % (len(line_list), len(cool_list)))

    line_icons, line_missing = download(line_list, LINE_URL)
    cool_icons, cool_missing = download(cool_list, COOL_URL)
    total = len(line_icons) + len(cool_icons)

    page = TEMPLATE.read_text(encoding="utf-8")
    for token, value in (("__TOTAL__", "{:,}".format(total)),
                         ("__LINE__", "{:,}".format(len(line_icons))),
                         ("__COOL__", "{:,}".format(len(cool_icons)))):
        page = page.replace(token, value)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(page, encoding="utf-8")

    generated = datetime.date.today().isoformat()
    write_catalog(DATA_LINE, {
        "generated": generated,
        "set": "Lineicons",
        "source": "LineiconsHQ/Lineicons",
        "licence": "MIT",
        "count": len(line_icons),
        "missing": line_missing,
        "icons": line_icons,
    })
    write_catalog(DATA_COOL, {
        "generated": generated,
        "set": "Coolicons",
        "source": "krystonschwarze/coolicons",
        "licence": "MIT",
        "style": "line/regular",
        "count": len(cool_icons),
        "missing": cool_missing,
        "icons": cool_icons,
    })

    print("Lineicons %d (missing %d) | Coolicons %d (missing %d) | total %d"
          % (len(line_icons), len(line_missing), len(cool_icons), len(cool_missing), total))
    print("wrote %s (%d bytes)" % (OUTPUT.relative_to(ROOT), OUTPUT.stat().st_size))
    if line_missing:
        print("line missing: %s" % ", ".join(line_missing[:12]), file=sys.stderr)
    if cool_missing:
        print("cool missing: %s" % ", ".join(cool_missing[:12]), file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
