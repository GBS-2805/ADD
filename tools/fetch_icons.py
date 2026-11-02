#!/usr/bin/env python3
"""Build the icon pages and the two icon catalogs from free SVG sets.

Two MIT sets, both taken whole:

  * Lineicons -- LineiconsHQ/Lineicons, assets/svgs/regular (the free set; the
    Pro icons sold on lineicons.com are not in that repository and are not here);
  * Coolicons -- krystonschwarze/coolicons, the line style with regular corners
    (icons/svg/line/regular). That repository also publishes fill, duotone, bulk
    and duocolor styles with sharp and round corners; those are variants of the
    same drawings, so one style is the set.

Every glyph is recoloured to currentColor so it inherits the surrounding text
colour, and keeps its own viewBox. Three pages come out of two templates:

  docs/icons/index.html                  the Icons app's landing page (brands + APK)
  docs/icons/<brand>/index.html          one page per brand: preview, variants, set
  docs/icons/all/index.html              all brands: a tab each, 20 glyphs one per category

Each brand page loads its own catalog at run time instead of inlining glyphs, so
the pages stay small and the files the phone app reads are the files the pages
render.

    python3 tools/fetch_icons.py                # fetch both sets, write data + pages
    python3 tools/fetch_icons.py --pages-only   # rewrite the pages from the catalogs
    python3 tools/fetch_icons.py --categories   # merge each vendor's categories, then pages
"""

import argparse
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
HUB_TEMPLATE = ROOT / "tools/icons_hub_template.html"
BRAND_TEMPLATE = ROOT / "tools/icons_brand_template.html"
ALL_TEMPLATE = ROOT / "tools/icons_all_template.html"
OUT_HUB = ROOT / "docs/icons/index.html"
APK = ROOT / "docs/Icons-1.2.apk"
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
COOL_INDEX = ("https://raw.githubusercontent.com/krystonschwarze/coolicons/main"
              "/icons/data/index.json")
CAT_CACHE = pathlib.Path("/tmp/coolicons-index.json")

# Lineicons ships no category file in its repository, but its site indexes every
# icon with a category and a free/pro flag and serves that from this public search
# endpoint, with the same client key the site itself sends. Only free rows are read.
LINE_SEARCH = ("https://lineicons-search.deploy.pimjo.com"
               "/indexes/bucket-lineicon-icons/search")
LINE_SEARCH_KEY = "7KZpvPXJK61sOdRKKfOA0N4UekEQpz5z"
LINE_SITEMAP = "https://lineicons.com/sitemap-0.xml"
LINE_CAT_CACHE = pathlib.Path("/tmp/lineicons-categories.json")

# Coolicons' own category order, as its browser lists them. Anything the vendor
# adds later is appended alphabetically rather than dropped. Lineicons' order
# comes from its own sitemap and is stored beside the catalog.
CAT_ORDER = ["System", "Arrow", "Navigation", "Edit", "File", "Communication",
             "User", "Media", "Calendar", "Devices", "Development", "AI", "Design",
             "Shape", "Symbols", "Home", "Buildings", "Travel", "Nature", "Animals",
             "Food", "Health", "Science", "Sports & Games", "Faces", "Zodiac", "Brands"]

COLOR = re.compile(r'(fill|stroke)="#[0-9a-fA-F]{3,8}"')

# slug, display name, vendor repository, how the style is described, catalog.
BRANDS = [
    {"slug": "lineicons", "name": "Lineicons", "source": "LineiconsHQ/Lineicons",
     "style": "line style, the free set", "data": DATA_LINE, "note":
     "The free Lineicons set, whole: __COUNT__ glyphs drawn as filled paths on a 24 px grid."},
    {"slug": "coolicons", "name": "Coolicons", "source": "krystonschwarze/coolicons",
     "style": "line style, regular corners", "data": DATA_COOL, "note":
     "Coolicons drawn as strokes with regular corners: __COUNT__ glyphs, the variant that matches the hairline chrome these pages use."},
]


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


def cool_categories():
    """name -> the vendor's category, from Coolicons' own icons/data/index.json."""
    if CAT_CACHE.exists():
        payload = json.loads(CAT_CACHE.read_text(encoding="utf-8"))
    else:
        try:
            request = urllib.request.Request(COOL_INDEX, headers={"User-Agent": "compress-site"})
            raw = urllib.request.urlopen(request, timeout=45).read()
        except Exception as error:
            print("coolicons categories unavailable (%s)" % error, file=sys.stderr)
            return {}
        payload = json.loads(raw)
        CAT_CACHE.write_text(json.dumps(payload), encoding="utf-8")
    return {item["name"]: item.get("category", "")
            for item in payload.get("icons", []) if item.get("name")}


def line_categories():
    """name -> the vendor's category, and the vendor's category order.

    Reads only rows flagged free and keeps only the categories of names that are
    actually in the free repository, so a Pro icon can never pull a glyph of ours
    into its category. The order is the one lineicons.com lists on its own site.
    """
    if LINE_CAT_CACHE.exists():
        payload = json.loads(LINE_CAT_CACHE.read_text(encoding="utf-8"))
    else:
        rows, offset = [], 0
        while True:
            body = json.dumps({
                "q": "", "filter": ['variant = "free"'], "limit": 1000, "offset": offset,
                "attributesToRetrieve": ["name", "category_slug", "categoryName"],
            }).encode("utf-8")
            request = urllib.request.Request(
                LINE_SEARCH, data=body,
                headers={"Content-Type": "application/json",
                         "Authorization": "Bearer " + LINE_SEARCH_KEY,
                         "Origin": "https://lineicons.com",
                         "Referer": "https://lineicons.com/"})
            try:
                data = json.loads(urllib.request.urlopen(request, timeout=45).read())
            except Exception as error:
                print("lineicons categories unavailable (%s)" % error, file=sys.stderr)
                return {}, []
            rows += data.get("hits", [])
            offset += 1000
            if not data.get("hits") or offset >= data.get("estimatedTotalHits", 0):
                break
        found = {}
        for row in rows:
            found.setdefault(row["name"], [row.get("categoryName") or row["category_slug"],
                                           row["category_slug"]])
        order = []
        try:
            sitemap = urllib.request.urlopen(
                urllib.request.Request(LINE_SITEMAP, headers={"User-Agent": "compress-site"}),
                timeout=45).read().decode("utf-8", "replace")
            display = {slug: name for name, slug in found.values()}
            order = [display[slug] for slug in re.findall(r"/icons/category/([a-z0-9-]+)</loc>",
                                                          sitemap) if slug in display]
        except Exception as error:
            print("lineicons category order unavailable (%s)" % error, file=sys.stderr)
        payload = {"names": found, "order": order}
        LINE_CAT_CACHE.write_text(json.dumps(payload), encoding="utf-8")
    return ({name: value[0] for name, value in payload["names"].items()},
            payload.get("order", []))


def category_list(icons, order=None):
    """Categories with counts, in the vendor's order, for the page's picker."""
    counts = {}
    for icon in icons:
        name = icon.get("cat")
        if name:
            counts[name] = counts.get(name, 0) + 1
    if not counts:
        return []
    ordered = [name for name in (order or CAT_ORDER) if name in counts]
    ordered += sorted(name for name in counts if name not in ordered)
    return [{"name": name, "count": counts[name]} for name in ordered]


def release():
    """The Icons version and APK filename, so the hub cannot quote a stale build."""
    catalog = json.loads((ROOT / "tools/catalog.json").read_text(encoding="utf-8"))
    icons = next(tool for tool in catalog["tools"] if tool["id"] == "icons")
    return icons["version"], icons["apk"].lstrip("/")


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


def fmt(number):
    return "{:,}".format(number)


def mode_of(payload):
    """stroke when the glyphs are drawn with strokes, fill when they are shapes."""
    sample = payload["icons"][0]["attrs"] if payload.get("icons") else ""
    return "stroke" if 'stroke="currentColor"' in sample else "fill"


def brands():
    """The catalogs on disk, with the numbers and modes the pages quote."""
    out = []
    for brand in BRANDS:
        if not brand["data"].exists():
            raise SystemExit("no %s yet; run without --pages-only first"
                             % brand["data"].relative_to(ROOT))
        payload = json.loads(brand["data"].read_text(encoding="utf-8"))
        entry = dict(brand)
        entry.update({
            "count": payload["count"],
            "cats": category_list(payload["icons"], payload.get("cat_order")),
            "mode": mode_of(payload),
            "licence": payload.get("licence", "MIT"),
            "style": payload.get("style", brand["style"]).replace("/", ", "),
            "file": "/data/" + brand["data"].name,
        })
        out.append(entry)
    return out


def menu(root, current):
    entries = brands()
    total = sum(brand["count"] for brand in entries)
    marker = ' aria-current="page"' if current == "all" else ""
    rows = ['  <a href="%sicons/all/"%s>All<span>%s</span></a>'
            % (root, marker, fmt(total))]
    for brand in entries:
        marker = ' aria-current="page"' if brand["slug"] == current else ""
        rows.append('  <a href="%sicons/%s/"%s>%s<span>%s</span></a>'
                    % (root, brand["slug"], marker, brand["name"], fmt(brand["count"])))
    return "\n".join(rows)


def brand_cards(entries):
    cards = []
    for brand in entries:
        cards.append(
            '<a class="brand" href="%s/">\n'
            '        <span class="kind">Brand · %s · %s</span>\n'
            '        <h3>%s</h3>\n'
            '        <p>%s</p>\n'
            '        <span class="go">Open %s</span>\n'
            '      </a>' % (brand["slug"], brand["licence"], fmt(brand["count"]),
                             brand["name"],
                             brand["note"].replace("__COUNT__", fmt(brand["count"])),
                             brand["name"]))
    return "\n      ".join(cards)


def write_all(entries):
    total = sum(brand["count"] for brand in entries)
    page = ALL_TEMPLATE.read_text(encoding="utf-8")
    sets = [{"slug": brand["slug"], "name": brand["name"], "count": brand["count"],
             "cats": len(brand["cats"]), "data": "../../data/" + brand["data"].name}
            for brand in entries]
    tokens = {
        "__ROOT__": "../../",
        "__TOTAL__": fmt(total),
        "__SETS__": json.dumps(sets),
        "__BRANDS__": menu("../../", "all"),
    }
    for token, value in tokens.items():
        page = page.replace(token, value)
    out = ROOT / "docs/icons/all/index.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page, encoding="utf-8")
    print("page: %-28s %9d bytes" % (out.relative_to(ROOT), out.stat().st_size))


def write_hub(entries):
    total = sum(brand["count"] for brand in entries)
    page = HUB_TEMPLATE.read_text(encoding="utf-8")
    version, apk = release()
    tokens = {
        "__ROOT__": "../",
        "__VERSION__": version,
        "__APK__": apk,
        "__TOTAL__": fmt(total),
        "__LINE__": fmt(entries[0]["count"]),
        "__COOL__": fmt(entries[1]["count"]),
        "__BRANDS__": menu("../", None),
        "__BRANDCARDS__": brand_cards(entries),
    }
    for token, value in tokens.items():
        page = page.replace(token, value)
    OUT_HUB.parent.mkdir(parents=True, exist_ok=True)
    OUT_HUB.write_text(page, encoding="utf-8")
    print("page: %-28s %9d bytes" % (OUT_HUB.relative_to(ROOT), OUT_HUB.stat().st_size))


def write_brand(brand, entries):
    total = sum(entry["count"] for entry in entries)
    page = BRAND_TEMPLATE.read_text(encoding="utf-8")
    tokens = {
        "__ROOT__": "../../",
        "__BRAND__": brand["name"],
        "__SLUG__": brand["slug"],
        "__COUNT__": str(brand["count"]),
        "__COUNTFMT__": fmt(brand["count"]),
        "__TOTALFMT__": fmt(total),
        "__LICENCE__": brand["licence"],
        "__SOURCE__": brand["source"],
        "__STYLE__": brand["style"],
        "__MODE__": brand["mode"],
        "__DATA__": "../../data/" + brand["data"].name,
        "__DATAFILE__": brand["file"],
        "__BRANDS__": menu("../../", brand["slug"]),
        "__CATS__": json.dumps(brand["cats"]),
    }
    for token, value in tokens.items():
        page = page.replace(token, value)
    out = ROOT / "docs/icons" / brand["slug"] / "index.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page, encoding="utf-8")
    print("page: %-28s %9d bytes" % (out.relative_to(ROOT), out.stat().st_size))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pages-only", action="store_true",
                        help="regenerate the pages from the catalogs already on disk")
    parser.add_argument("--categories", action="store_true",
                        help="merge Coolicons' categories into the catalog, then write pages")
    args = parser.parse_args()

    if args.pages_only:
        entries = brands()
    elif args.categories:
        payload = json.loads(DATA_COOL.read_text(encoding="utf-8"))
        mapping = cool_categories()
        merged = 0
        for icon in payload["icons"]:
            name = mapping.get(icon["name"], "")
            if name:
                icon["cat"] = name
                merged += 1
        payload["cat_order"] = CAT_ORDER
        write_catalog(DATA_COOL, payload)
        print("coolicons categories: %d of %d glyphs" % (merged, payload["count"]))

        payload = json.loads(DATA_LINE.read_text(encoding="utf-8"))
        mapping, order = line_categories()
        merged = 0
        for icon in payload["icons"]:
            name = mapping.get(icon["name"], "")
            if name:
                icon["cat"] = name
                merged += 1
        payload["cat_order"] = order
        write_catalog(DATA_LINE, payload)
        print("lineicons categories: %d of %d glyphs" % (merged, payload["count"]))
        entries = brands()
    else:
        line_list = line_names()
        cool_list = cool_names()
        print("Lineicons: %d names | Coolicons line/regular: %d names"
              % (len(line_list), len(cool_list)))

        line_icons, line_missing = download(line_list, LINE_URL)
        cool_icons, cool_missing = download(cool_list, COOL_URL)
        mapping = cool_categories()
        for icon in cool_icons:
            icon["cat"] = mapping.get(icon["name"], "")
        line_mapping, line_order = line_categories()
        for icon in line_icons:
            icon["cat"] = line_mapping.get(icon["name"], "")
        generated = datetime.date.today().isoformat()
        write_catalog(DATA_LINE, {
            "generated": generated,
            "set": "Lineicons",
            "source": "LineiconsHQ/Lineicons",
            "licence": "MIT",
            "cat_order": line_order,
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
            "cat_order": CAT_ORDER,
            "count": len(cool_icons),
            "missing": cool_missing,
            "icons": cool_icons,
        })
        print("Lineicons %d (missing %d) | Coolicons %d (missing %d)"
              % (len(line_icons), len(line_missing), len(cool_icons), len(cool_missing)))
        if line_missing:
            print("line missing: %s" % ", ".join(line_missing[:12]), file=sys.stderr)
        if cool_missing:
            print("cool missing: %s" % ", ".join(cool_missing[:12]), file=sys.stderr)
        entries = brands()

    write_hub(entries)
    for brand in entries:
        write_brand(brand, entries)
    write_all(entries)
    print("brands: %s | total %d glyphs"
          % (", ".join("%s %d" % (b["name"], b["count"]) for b in entries),
             sum(b["count"] for b in entries)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
