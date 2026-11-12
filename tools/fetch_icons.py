#!/usr/bin/env python3
"""Build the icon pages and the two icon catalogs from free SVG sets.

Two MIT sets, both taken whole:

  * Lineicons -- LineiconsHQ/Lineicons, assets/svgs/regular (the free set; the
    Pro icons sold on lineicons.com are not in that repository and are not here);
  * Coolicons -- krystonschwarze/coolicons, the line style with regular corners
    (icons/svg/line/regular). That repository also publishes fill, duotone, bulk
    and duocolor styles with sharp and round corners; those are variants of the
    same drawings, so one style is the set.

Every icon is recoloured to currentColor so it inherits the surrounding text
colour, and keeps its own viewBox. Three pages come out of two templates:

  docs/icons/index.html                  the Icons app's landing page (brands + APK)
  docs/icons/<brand>/index.html          one page per brand: preview, variants, set
  docs/icons/all/index.html              all brands: a tab each, 20 icons one per category

Each brand page loads its own catalog at run time instead of inlining icons, so
the pages stay small and the files the phone app reads are the files the pages
render. The all-brands sheet is the exception: it draws one glyph per category,
so it downloads `docs/data/<brand>-sheet.json`, that subset and nothing else.

    python3 tools/fetch_icons.py                # fetch both sets, write data + pages
    python3 tools/fetch_icons.py --pages-only   # rewrite the pages from the catalogs
    python3 tools/fetch_icons.py --categories   # merge each vendor's categories, then pages
"""

import argparse
import datetime
import hashlib
import json
import pathlib
import re
import sys
import time
import tarfile
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

ROOT = pathlib.Path(__file__).resolve().parent.parent
HUB_TEMPLATE = ROOT / "tools/icons_hub_template.html"
BRAND_TEMPLATE = ROOT / "tools/icons_brand_template.html"
ALL_TEMPLATE = ROOT / "tools/icons_all_template.html"
sys.path.insert(0, str(ROOT / "tools"))
from hash_assets import pin  # noqa: E402 - the tools directory is on the path above

OUT_HUB = ROOT / "docs/icons/index.html"
APK = ROOT / "docs/Icons-1.2.apk"
DATA_LINE = ROOT / "docs/data/icons.json"
DATA_COOL = ROOT / "docs/data/coolicons.json"
DATA_PHOS = ROOT / "docs/data/phosphor.json"
DATA_STREAMLINE = {slug: ROOT / ("docs/data/streamline-%s.json" % slug)
                   for slug in ("line", "duo", "solid", "flat")}
# The two Figma sets have no repository to fetch from: they are exported by hand
# into these folders, and the brand is skipped until its folder has SVGs in it.
DATA_GEO1 = ROOT / "docs/data/geometric-shapes-1.json"
DATA_GEO2 = ROOT / "docs/data/geometric-shapes-2.json"
TREE_CACHE = pathlib.Path("/tmp/coolicons-tree.json")

# Streamline's free Core set comes from the vendor's own repository as one folder
# per style: core/line, core/duo, core/solid, core/flat, each holding the same
# drawings under category folders. Line and Solid are drawn in black, so they are
# recoloured to currentColor like the other monochrome sets; Duo and Flat carry the
# vendor's palette, and that palette is the style, so those keep it. CC BY 4.0.
# One tarball carries both the file list and the bytes: the repository is far past
# the anonymous GitHub listing quota, and 5,000 raw requests are slower than one
# archive. Only core/<style>/<category>/<icon>.svg is read out of it.
STREAMLINE_TARBALL = ("https://codeload.github.com/webalys-hq/streamline-vectors"
                      "/tar.gz/refs/heads/main")
STREAMLINE_ARCHIVE = pathlib.Path("/tmp/streamline-vectors.tar.gz")
STREAMLINE_STYLES = [
    {"slug": "line", "name": "Line", "mode": "stroke", "colour": False, "tune": True,
     "default": True},
    {"slug": "duo", "name": "Duo", "mode": "fill", "colour": True, "tune": False},
    {"slug": "solid", "name": "Solid", "mode": "fill", "colour": False, "tune": False},
    {"slug": "flat", "name": "Flat", "mode": "fill", "colour": True, "tune": False},
]
STREAMLINE_STYLE_FILES = [dict(style, data=DATA_STREAMLINE[style["slug"]])
                          for style in STREAMLINE_STYLES]

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

# Phosphor publishes one folder per weight in its npm package, and jsDelivr lists
# the package for us the way it lists the Lineicons repository. The regular weight
# is the outline set; thin, light, bold, fill and duotone are the same drawings.
PHOS_VER = "2.1.1"
PHOS_TREE = ("https://data.jsdelivr.com/v1/packages/npm/@phosphor-icons/core@%s"
             "?structure=flat" % PHOS_VER)
PHOS_URL = ("https://cdn.jsdelivr.net/npm/@phosphor-icons/core@%s/assets/regular/%s.svg"
            % (PHOS_VER, "%s"))
PHOS_DIR = "/assets/regular/"
PHOS_CACHE = pathlib.Path("/tmp/phosphor-tree.json")
# Phosphor's npm package ships no metadata, but its repository does: src/icons.ts
# carries one entry per icon listing the categories its own site filters by, and
# src/types.ts declares the IconCategory enum with those names and their order.
# Read those rather than inventing buckets.
PHOS_META = "https://raw.githubusercontent.com/phosphor-icons/core/main/src/types.ts"
PHOS_ICONS = "https://raw.githubusercontent.com/phosphor-icons/core/main/src/icons.ts"
PHOS_META_CACHE = pathlib.Path("/tmp/phosphor-types.ts")
PHOS_ICONS_CACHE = pathlib.Path("/tmp/phosphor-icons.ts")

# Coolicons' own category order, as its browser lists them. Anything the vendor
# adds later is appended alphabetically rather than dropped. Lineicons' order
# comes from its own sitemap and is stored beside the catalog.
CAT_ORDER = ["System", "Arrow", "Navigation", "Edit", "File", "Communication",
             "User", "Media", "Calendar", "Devices", "Development", "AI", "Design",
             "Shape", "Symbols", "Home", "Buildings", "Travel", "Nature", "Animals",
             "Food", "Health", "Science", "Sports & Games", "Faces", "Zodiac", "Brands"]

COLOR = re.compile(r'(fill|stroke)="#[0-9a-fA-F]{3,8}"')

# The all-brands sheet shows one icon per category, twelve per set. It ships
# that subset as its own small file rather than downloading both full catalogs
# to draw 24 glyphs.
CAP = 12

# slug, display name, vendor repository, how the style is described, catalog.
BRANDS = [
    {"slug": "lineicons", "name": "Lineicons", "source": "LineiconsHQ/Lineicons",
     "style": "line style, the free set", "data": DATA_LINE, "names": "lineicons",
     "cats": "lineicons", "licence": "MIT",
     "note": "The free Lineicons set, whole: __COUNT__ icons drawn as filled paths on a 24 px grid."},
    {"slug": "coolicons", "name": "Coolicons", "source": "krystonschwarze/coolicons",
     "style": "line style, regular corners", "data": DATA_COOL, "names": "coolicons",
     "cats": "coolicons", "licence": "MIT",
     "note": "Coolicons drawn as strokes with regular corners: __COUNT__ icons, the variant that matches the hairline chrome these pages use."},
    {"slug": "phosphor", "name": "Phosphor", "source": "phosphor-icons/core 2.1.1",
     "style": "regular weight, outline", "data": DATA_PHOS, "names": "phosphor",
     "cats": "phosphor", "licence": "MIT",
     "note": "Phosphor's regular weight, whole: __COUNT__ outline icons drawn on a 256 px grid, the weight its own site calls the default."},
    {"slug": "streamline", "name": "Streamline", "source": "webalys-hq/streamline-vectors",
     "style": "Line, Duo, Solid and Flat", "data": DATA_STREAMLINE["line"],
     "names": "streamline", "cats": "streamline", "licence": "CC BY 4.0",
     "styles": STREAMLINE_STYLE_FILES,
     "note": "__COUNT__ icons in four styles -- Line, Duo, Solid and Flat -- on a 14 px grid, with the vendor's own categories."},
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


def phos_names():
    """Every name in Phosphor's regular weight, from the package listing."""
    if PHOS_CACHE.exists():
        payload = json.loads(PHOS_CACHE.read_text(encoding="utf-8"))
    else:
        for attempt in range(3):
            try:
                raw = urllib.request.urlopen(PHOS_TREE, timeout=45).read()
                break
            except Exception as error:
                print("phosphor tree failed (%s), retrying" % error, file=sys.stderr)
                time.sleep(2 * (attempt + 1))
        else:
            raise SystemExit("could not list Phosphor; nothing written")
        payload = json.loads(raw)
        PHOS_CACHE.write_text(json.dumps(payload), encoding="utf-8")

    names = [entry["name"][len(PHOS_DIR):-4]
             for entry in payload.get("files", [])
             if entry.get("name", "").startswith(PHOS_DIR)
             and entry["name"].endswith(".svg")]
    if not names:
        raise SystemExit("Phosphor regular came back empty; nothing written")
    return sorted(set(names))


def phos_categories():
    """name -> Phosphor's own first category, and its category order.

    The enum values are lower case and sometimes phrased ("technology & development"),
    so each is title cased for display while the sheet matches on its plain key.
    """
    def text(url, cache):
        if cache.exists():
            return cache.read_text(encoding="utf-8")
        try:
            raw = urllib.request.urlopen(url, timeout=45).read().decode("utf-8", "replace")
        except Exception as error:
            print("phosphor categories unavailable (%s)" % error, file=sys.stderr)
            return None
        cache.write_text(raw, encoding="utf-8")
        return raw

    types = text(PHOS_META, PHOS_META_CACHE)
    icons = text(PHOS_ICONS, PHOS_ICONS_CACHE)
    if not types or not icons:
        return {}, []

    block = types.split("enum IconCategory")[1].split("}")[0]
    enum = dict(re.findall(r'([A-Z_]+)\s*=\s*"([^"]+)"', block))
    label = {key: " ".join(word.capitalize() for word in value.split())
             for key, value in enum.items()}

    found = {}
    for name, cats in re.findall(r'name:\s*"([^"]+)".{0,600}?categories:\s*\[([^\]]*)\]',
                                 icons, re.S):
        keys = re.findall(r"IconCategory\.([A-Z_]+)", cats)
        if keys and keys[0] in label:
            found[name] = label[keys[0]]
    used = set(found.values())
    return found, [name for name in label.values() if name in used]


def streamline_archive():
    """The repository tarball, downloaded once and kept for the run."""
    if STREAMLINE_ARCHIVE.exists() and STREAMLINE_ARCHIVE.stat().st_size > 1024:
        return STREAMLINE_ARCHIVE
    for attempt in range(3):
        try:
            request = urllib.request.Request(STREAMLINE_TARBALL,
                                             headers={"User-Agent": "compress-site"})
            with urllib.request.urlopen(request, timeout=300) as response:
                STREAMLINE_ARCHIVE.write_bytes(response.read())
            return STREAMLINE_ARCHIVE
        except Exception as error:
            print("streamline archive failed (%s), retrying" % error, file=sys.stderr)
            time.sleep(3 * (attempt + 1))
    raise SystemExit("could not download Streamline; nothing written")


def streamline_sources():
    """style -> {name: (category folder, raw SVG text)}, read out of the tarball."""
    tree = {style["slug"]: {} for style in STREAMLINE_STYLES}
    with tarfile.open(streamline_archive(), "r:gz") as archive:
        for member in archive:
            if not member.isfile():
                continue
            parts = member.name.split("/")
            if len(parts) != 5 or parts[1] != "core" or parts[2] not in tree:
                continue
            _, _, style, folder, filename = parts
            if not filename.endswith(".svg"):
                continue
            handle = archive.extractfile(member)
            if handle is None:
                continue
            tree[style][filename[:-4]] = (folder, handle.read().decode("utf-8", "replace"))
    for style, found in tree.items():
        if not found:
            raise SystemExit("Streamline %s came back empty; nothing written" % style)
    return tree


def streamline_label(folder):
    """The vendor's folder name as a category the picker can show."""
    return " ".join(word.capitalize() for word in folder.replace("-", " ").split())


def streamline_icons(style, sources):
    """One style: the same Core drawings, in its own ink or its own palette."""
    found = sources.get(style["slug"]) or {}
    if not found:
        raise SystemExit("Streamline %s came back empty; nothing written" % style["name"])

    icons, missing = [], []
    for name, (folder, raw) in sorted(found.items()):
        prepared = prepare(raw, keep_colour=style["colour"])
        if prepared is None:
            missing.append(name)
            continue
        attrs, body = prepared
        icons.append({"name": name, "attrs": attrs, "body": body,
                      "cat": streamline_label(folder)})
    icons.sort(key=lambda item: item["name"])
    return icons, missing


def local_names(folder):
    """Names from an exported folder: one SVG per icon, any nesting."""
    folder = pathlib.Path(folder)
    if not folder.exists():
        return []
    return sorted({path.stem for path in folder.rglob("*.svg")})


def local_icons(folder):
    """Read a folder of exported SVGs into the same shape as a fetched set."""
    folder = pathlib.Path(folder)
    icons, missing = [], []
    for path in sorted(folder.rglob("*.svg")):
        prepared = prepare(path.read_text(encoding="utf-8", errors="replace"))
        if prepared is None:
            missing.append(path.stem)
            continue
        attrs, body = prepared
        icons.append({"name": path.stem, "attrs": attrs, "body": body})
    icons.sort(key=lambda item: item["name"])
    return icons, missing


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
    actually in the free repository, so a Pro icon can never pull a icon of ours
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
    """The app's version and APK filename, so the hub cannot quote a stale build.

    One app now, so the catalog's single entry is the answer; the id is not consulted,
    which is what keeps this working when the app is renamed again.
    """
    catalog = json.loads((ROOT / "tools/catalog.json").read_text(encoding="utf-8"))
    app = catalog["tools"][0]
    return app["version"], app["apk"].lstrip("/")


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


def prepare(raw, keep_colour=False):
    """Returns (root attributes, body), recoloured unless the set owns its palette."""
    head = re.search(r"<svg([^>]*)>", raw, re.S)
    inner = re.search(r"<svg[^>]*>(.*)</svg>", raw, re.S)
    if not head or not inner:
        return None

    def attr(key, default=""):
        found = re.search(r'%s="([^"]*)"' % key, head.group(1))
        return found.group(1) if found else default

    body = re.sub(r"<desc>.*?</desc>", "", inner.group(1), flags=re.S)
    # Ids are copied with every inline glyph and nothing refers to them, so they are
    # dropped rather than shipped thousands of times.
    body = re.sub(r'\sid="[^"]*"', "", body)
    if not keep_colour:
        body = COLOR.sub(r'\1="currentColor"', body)
    body = re.sub(r"\s*\n\s*", "", body.strip())
    if not re.search(r"<(path|circle|rect|line|polyline|polygon|ellipse)\b", body):
        return None  # nothing to draw

    parts = ['width="%s"' % attr("width", "24"),
             'height="%s"' % attr("height", "24"),
             'viewBox="%s"' % attr("viewBox", "0 0 24 24")]
    # A set that keeps its palette gets the root's own fill="none" and nothing else:
    # the preview never tunes those, so the colours reach the page as authored.
    if keep_colour:
        if 'fill="none"' in head.group(1):
            parts.append('fill="none"')
        return " ".join(parts), body
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


CHUNK = 200


def write_plain(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
    print("data: %-28s %9d bytes" % (path.relative_to(ROOT), path.stat().st_size))


def write_catalog(path, payload):
    """Write one catalog as a names-first index plus markup chunks.

    The index carries the totals, the category counts and one glyph, so a page can
    paint its counts and preview before the markup arrives; the glyphs follow in
    chunks of CHUNK under `data/<stem>/`. A 1.4 MB catalog no longer has to arrive
    as one response, and an unchanged chunk stays cached when another changes.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    icons = payload.get("icons", [])
    index = {key: value for key, value in payload.items() if key != "icons"}
    index["count"] = len(icons)
    index["first"] = icons[0] if icons else None

    counts, order = {}, payload.get("cat_order", [])
    for item in icons:
        if item.get("cat"):
            counts[item["cat"]] = counts.get(item["cat"], 0) + 1
    named = [name for name in order if counts.get(name)]
    named += sorted(name for name in counts if name not in named)
    index["cats"] = [{"name": name, "count": counts[name]} for name in named]

    folder = path.parent / path.stem
    folder.mkdir(parents=True, exist_ok=True)
    chunks, keep = [], set()
    for number, start in enumerate(range(0, len(icons), CHUNK), 1):
        name = "%d.json" % number
        (folder / name).write_text(
            json.dumps({"icons": icons[start:start + CHUNK]}, ensure_ascii=False) + "\n",
            encoding="utf-8")
        chunks.append(path.stem + "/" + name)
        keep.add(name)
    for old in folder.glob("*.json"):
        if old.name not in keep:
            old.unlink()
    index["chunks"] = chunks
    path.write_text(json.dumps(index, ensure_ascii=False) + "\n", encoding="utf-8")
    print("data: %-28s %9d bytes, %d chunk(s)"
          % (path.relative_to(ROOT), path.stat().st_size, len(chunks)))


def load_catalog(path):
    """A catalog's full payload: the index, with its chunks merged back in."""
    payload = json.loads(path.read_text(encoding="utf-8"))
    chunks = payload.get("chunks") or []
    if chunks:
        icons = []
        for name in chunks:
            icons.extend(json.loads((path.parent / name).read_text(encoding="utf-8")).get("icons", []))
        payload["icons"] = icons
    return payload


def rechunk():
    """Rewrite every full catalog on disk in the chunked shape, without refetching."""
    skip = {"index", "tools", "fonts", "portfolio", "pages", "assets"}
    done = 0
    for path in sorted((ROOT / "docs/data").glob("*.json")):
        if path.stem.endswith("-sheet") or path.stem in skip:
            continue
        payload = json.loads(path.read_text(encoding="utf-8"))
        if "icons" not in payload:
            continue
        write_catalog(path, payload)
        done += 1
    print("rechunked %d catalogs" % done)


def fmt(number):
    return "{:,}".format(number)


def mode_of(payload):
    """stroke when the icons are drawn with strokes, fill when they are shapes."""
    sample = payload["icons"][0]["attrs"] if payload.get("icons") else ""
    return "stroke" if 'stroke="currentColor"' in sample else "fill"


def collect(brand):
    """One brand's icons, fetched from its source or read from its exported folder."""
    if brand.get("dir"):
        return local_icons(brand["dir"])
    if brand["names"] == "lineicons":
        return download(line_names(), LINE_URL)
    if brand["names"] == "coolicons":
        return download(cool_names(), COOL_URL)
    if brand["names"] == "phosphor":
        return download(phos_names(), PHOS_URL)
    if brand["names"] == "streamline":
        raise SystemExit("Streamline is fetched style by style, not through collect()")
    raise SystemExit("no source for %s" % brand["name"])


def categories_for(brand):
    """The vendor's own name -> category map and category order, when it has one."""
    if brand.get("cats") == "lineicons":
        return line_categories()
    if brand.get("cats") == "coolicons":
        return cool_categories(), CAT_ORDER
    if brand.get("cats") == "phosphor":
        return phos_categories()
    return {}, []


_BRANDS = None


def brands():
    """The catalogs on disk, with the numbers and modes the pages quote."""
    global _BRANDS
    if _BRANDS is not None:
        return _BRANDS
    out = []
    for brand in BRANDS:
        if not brand["data"].exists():
            # A brand whose folder has not been exported yet is left out rather than
            # failing the run: the pages stay whole for the sets that are here.
            print("skipping %s: no %s yet"
                  % (brand["name"], brand["data"].relative_to(ROOT)), file=sys.stderr)
            continue
        payload = load_catalog(brand["data"])
        entry = dict(brand)
        if brand.get("styles"):
            entry["styles"] = [
                dict(style, count=(json.loads(style["data"].read_text(encoding="utf-8"))["count"]
                                   if style["data"].exists() else 0))
                for style in brand["styles"]]
        if brand.get("styles"):
            payload = dict(payload, style=brand["style"])
        entry.update({
            "count": payload["count"],
            "cats": category_list(payload["icons"], payload.get("cat_order")),
            "mode": mode_of(payload),
            "licence": payload.get("licence", "MIT"),
            "style": payload.get("style", brand["style"]).replace("/", ", "),
            "file": "/data/" + brand["data"].name,
        })
        out.append(entry)
    _BRANDS = out
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


SET_NOTES = {
    "lineicons": "The free Lineicons set in full, drawn as filled paths on a 24 px grid.",
    "coolicons": "Stroke drawings with regular corners, the line style kept whole across 30 categories.",
    "phosphor": "The regular outline weight, whole, with every category drawn on a 256 px grid.",
    "streamline": "Four styles of the same drawings — Line, Duo, Solid and Flat — from the free Core set.",
}

HERO_ART = [
    ("heart", ["heart"]),
    ("home", ["house", "home", "home-2"]),
    ("search", ["search"]),
    ("camera", ["camera"]),
]

GALLERY_ART = [
    ("heart", ["heart"]),
    ("home", ["house", "home", "home-2"]),
    ("search", ["search"]),
    ("camera", ["camera"]),
    ("image", ["image"]),
    ("folder", ["folder"]),
    ("file", ["file"]),
    ("download", ["download"]),
    ("bell", ["bell"]),
    ("settings", ["settings"]),
    ("user", ["user"]),
    ("mail", ["mail"]),
    ("message", ["chat", "message"]),
    ("phone", ["phone"]),
    ("pin", ["map-pin", "pin"]),
    ("calendar", ["calendar"]),
    ("clock", ["clock"]),
    ("star", ["star"]),
    ("bookmark", ["bookmark"]),
    ("lock", ["lock"]),
    ("key", ["key"]),
    ("shield", ["shield"]),
    ("cloud", ["cloud"]),
    ("sun", ["sun"]),
    ("moon", ["moon"]),
    ("leaf", ["leaf"]),
    ("coffee", ["coffee"]),
    ("pencil", ["pencil"]),
    ("brush", ["brush"]),
    ("palette", ["palette"]),
]


def hub_glyphs(entries):
    """Resolve the hero and preview names, Coolicons first and whole."""
    ordered = sorted(entries, key=lambda brand: brand["slug"] != "coolicons")
    cache = {}
    picked = {}
    for slot, candidates in HERO_ART + GALLERY_ART:
        for brand in ordered:
            if brand["slug"] not in cache:
                cache[brand["slug"]] = {icon["name"]: icon
                                        for icon in load_catalog(brand["data"])["icons"]}
            for name in candidates:
                if name in cache[brand["slug"]]:
                    picked[slot] = cache[brand["slug"]][name]
                    break
            if slot in picked:
                break
    missing = [slot for slot, _ in HERO_ART + GALLERY_ART if slot not in picked]
    if missing:
        raise SystemExit("hub preview is missing: %s" % ", ".join(missing))
    return picked


def icon_svg(icon):
    return '<svg %s aria-hidden="true" focusable="false">%s</svg>' % (
        icon.get("attrs", ""), icon.get("body", ""))


def hero_markup(icons):
    return "\n".join(
        '      <span class="ih-hero-glyph">%s</span>' % icon_svg(icons[slot])
        for slot, _ in HERO_ART)


def gallery_markup(icons):
    rows = []
    for index, (slot, _) in enumerate(GALLERY_ART):
        rows.append(
            '      <button class="ih-tile" type="button" data-name="%s" '
            'aria-label="%s" aria-pressed="%s">%s</button>'
            % (slot, slot, "true" if index == 0 else "false", icon_svg(icons[slot])))
    return "\n".join(rows)


def sample_icons(icons, limit=12):
    """A dozen readable glyphs: one from each category before any repeats."""
    picks, seen = [], set()
    for icon in icons:
        cat = (icon.get("cat") or "").strip().lower()
        if cat and cat in seen:
            continue
        if cat:
            seen.add(cat)
        picks.append(icon)
        if len(picks) >= limit:
            return picks
    if len(picks) < limit:
        chosen = {id(icon) for icon in picks}
        for icon in icons:
            if id(icon) not in chosen:
                picks.append(icon)
            if len(picks) >= limit:
                break
    return picks


def brand_cards(entries):
    cards = []
    for brand in entries:
        note = SET_NOTES.get(
            brand["slug"], brand["note"].replace("__COUNT__", fmt(brand["count"])))
        cards.append(
            '<a class="ih-set" href="%s/">\n'
            '  <div class="ih-set-head"><h3>%s</h3>'
            '<span class="ih-licence">%s</span></div>\n'
            '  <p class="ih-count"><span class="ih-num">%s</span>'
            '<span class="ih-unit">icons</span></p>\n'
            '  <p class="ih-set-note">%s</p>\n'
            '</a>' % (brand["slug"], brand["name"], brand["licence"],
                      fmt(brand["count"]), note))
    return "\n    ".join(cards)


def sheet_key(name):
    """The sheet's own category key: case, plural, punctuation and & folded away."""
    text = str(name).lower().replace("&", " and ")
    text = re.sub(r"\s+", " ", re.sub(r"[^a-z0-9 ]+", " ", text)).strip()
    return text[:-1] if text.endswith("s") else text


def sheet_categories(icons, order):
    """Every category name the set carries, in the order the sheet lays rows out.

    Mirrors the page's `categories()` exactly -- the vendor's order first, then
    anything it did not list in the order the catalog introduces it -- so a sheet
    built from a subset still notes the same number of categories as one built
    from the whole catalog.
    """
    present, listed = {}, []
    for icon in icons:
        name = icon.get("cat")
        if name and name not in present:
            present[name] = True
            listed.append(name)
    vendor = [name for name in (order or []) if name in present]
    return vendor + [name for name in listed if name not in vendor]


def sheet_icons(icons):
    """One icon per category, plus the set's first CAP icons for the top-up.

    The page picks the first icon of a category out of what it downloaded, so the
    subset that keeps its picks identical is the first icon of each category by
    catalog order. A set with fewer than twelve categories tops itself up from the
    start of its catalog, which is why the first CAP icons travel too.
    """
    first = {}
    for index, icon in enumerate(icons):
        key = sheet_key(icon.get("cat") or "")
        if key and key not in first:
            first[key] = index
    keep = sorted(set(range(min(CAP, len(icons)))) | set(first.values()))
    return [icons[index] for index in keep]


def write_sheets(entries):
    """The all-brands sheet's own data: a glyph per category, not either catalog."""
    for brand in entries:
        payload = load_catalog(brand["data"])
        glyphs = sheet_icons(payload["icons"])
        sheet = {
            "set": payload.get("set", brand["name"]),
            "count": payload["count"],
            "cat_order": payload.get("cat_order", []),
            "cats": sheet_categories(payload["icons"], payload.get("cat_order")),
            "icons": glyphs,
        }
        path = ROOT / "docs/data" / (brand["slug"] + "-sheet.json")
        write_plain(path, sheet)
        print("sheet: %-28s %3d of %4d glyphs, %3d categories"
              % (path.name, len(glyphs), payload["count"], len(sheet["cats"])))


def write_all(entries):
    total = sum(brand["count"] for brand in entries)
    page = ALL_TEMPLATE.read_text(encoding="utf-8")
    sets, digest = [], hashlib.sha1()
    for brand in entries:
        path = ROOT / "docs/data" / (brand["slug"] + "-sheet.json")
        raw = path.read_bytes() if path.exists() else b""
        digest.update(raw)
        cats = json.loads(raw.decode("utf-8")).get("cats", []) if raw else []
        sets.append({"slug": brand["slug"], "name": brand["name"], "count": brand["count"],
                     "cats": cats,
                     "data": "../../data/" + brand["slug"] + "-sheet.json"})
    tokens = {
        "__ROOT__": "../../",
        "__TOTAL__": fmt(total),
        "__REV__": digest.hexdigest()[:10],
        "__SETS__": json.dumps(sets),
        "__LICENCES__": licence_line(entries),
        "__BRANDS__": menu("../../", "all"),
    }
    for token, value in tokens.items():
        page = page.replace(token, value)
    out = ROOT / "docs/icons/all/index.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(pin(page), encoding="utf-8")
    print("page: %-28s %9d bytes" % (out.relative_to(ROOT), out.stat().st_size))


def licence_line(entries):
    """One sentence for the licences the sets carry, MIT being the default."""
    others = [(brand["name"], brand["licence"]) for brand in entries
              if brand["licence"].upper() != "MIT"]
    if not others:
        return "Every set is MIT."
    if len(others) == 1:
        return "Every set is MIT except %s, which is %s." % others[0]
    listed = ", ".join("%s (%s)" % pair for pair in others[:-1])
    return "Every set is MIT except %s and %s (%s)." % (listed, others[-1][0], others[-1][1])



def data_rows(brand):
    """One row per catalog: a style is its own file, and says so."""
    styles = brand.get("styles") or [dict(brand, name=brand["name"] + "", data=brand["data"])]
    if brand.get("styles"):
        rows = []
        for style in brand["styles"]:
            rows.append(
                '      <li><b><a href="__ROOT__data/%s">/data/%s</a></b>'
                '<span>%s %s %s icons with their markup.</span></li>'
                % (style["data"].name, style["data"].name, fmt(style["count"]),
                   brand["name"], style["name"]))
        return "\n".join(rows)
    return ('      <li><b><a href="__ROOT__data/%s">/data/%s</a></b>'
            '<span>%s %s icons with their markup.</span></li>'
            % (brand["data"].name, brand["data"].name, fmt(brand["count"]), brand["name"]))


def write_hub(entries):
    total = sum(brand["count"] for brand in entries)
    page = HUB_TEMPLATE.read_text(encoding="utf-8")
    version, apk = release()
    licences = licence_line(entries)
    names = [brand["name"] for brand in entries]
    setlist = names[0] if len(names) == 1 else ", ".join(names[:-1]) + " and " + names[-1]
    rows = "\n".join(data_rows(brand) for brand in entries)
    others = [(brand["name"], brand["licence"]) for brand in entries
              if brand["licence"].upper() != "MIT"]
    tokens = {
        "__ROOT__": "../",
        "__VERSION__": version,
        "__LICENCES__": licences,
        "__SETLIST__": setlist,
        "__BRANDCOUNT_CAP__": {1: "One", 2: "Two", 3: "Three", 4: "Four",
                           5: "Five", 6: "Six"}.get(len(entries), str(len(entries))),
        "__BRANDCOUNT__": {1: "one", 2: "two", 3: "three", 4: "four",
                          5: "five", 6: "six"}.get(len(entries), str(len(entries))),
        "__DATALIST__": rows,
        "__APK__": apk,
        "__TOTAL__": fmt(total),
        "__BRANDS__": menu("../", None),
        "__BRANDCARDS__": brand_cards(entries),
    }
    for token, value in tokens.items():
        page = page.replace(token, value)
    # The data list is inserted by this loop, so its own __ROOT__ was not in the page
    # when that token was replaced. Sweep it once more rather than ordering the dict
    # so the bundle links stay absolute-free.
    page = page.replace("__ROOT__", "../")
    OUT_HUB.parent.mkdir(parents=True, exist_ok=True)
    OUT_HUB.write_text(pin(page), encoding="utf-8")
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
        "__STYLES__": json.dumps([{"slug": style["slug"], "name": style["name"],
                                   "data": "../../data/" + style["data"].name,
                                   "mode": style["mode"], "colour": style["colour"],
                                   "tune": style.get("tune", True)}
                                  for style in brand.get("styles", [])]),
    }
    for token, value in tokens.items():
        page = page.replace(token, value)
    out = ROOT / "docs/icons" / brand["slug"] / "index.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(pin(page), encoding="utf-8")
    print("page: %-28s %9d bytes" % (out.relative_to(ROOT), out.stat().st_size))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rechunk", action="store_true",
                        help="rewrite the catalogs on disk in the chunked shape, no network")
    parser.add_argument("--pages-only", action="store_true",
                        help="regenerate the pages from the catalogs already on disk")
    parser.add_argument("--categories", action="store_true",
                        help="re-read each vendor's categories into the catalogs, then write pages")
    parser.add_argument("--data-files", action="store_true",
                        help="print the data files the pages load, one per line, and stop")
    parser.add_argument("--refresh", metavar="SLUGS",
                        help="fetch only these brands (comma separated); the rest keep their catalogs")
    args = parser.parse_args()

    if args.rechunk:
        rechunk()
        return

    if args.data_files:
        # build.sh stages exactly this list into the app, so a brand added here cannot
        # leave the bundle short a catalog or a sheet.
        for brand in BRANDS:
            if brand.get("styles"):
                for style in brand["styles"]:
                    print(style["data"].name)
            else:
                print(brand["data"].name)
            print(brand["slug"] + "-sheet.json")
        return 0

    if args.categories:
        for brand in BRANDS:
            if not brand.get("cats") or not brand["data"].exists():
                continue
            payload = load_catalog(brand["data"])
            mapping, order = categories_for(brand)
            merged = 0
            for icon in payload["icons"]:
                name = mapping.get(icon["name"], "")
                if name:
                    icon["cat"] = name
                    merged += 1
            if order:
                payload["cat_order"] = order
            write_catalog(brand["data"], payload)
            print("%s categories: %d of %d icons" % (brand["name"], merged, payload["count"]))
    elif not args.pages_only:
        wanted = set(args.refresh.split(",")) if args.refresh else None
        generated = datetime.date.today().isoformat()
        for brand in BRANDS:
            if wanted and brand["slug"] not in wanted:
                continue
            if brand.get("styles"):
                sources = streamline_sources()
                for style in brand["styles"]:
                    icons, missing = streamline_icons(style, sources)
                    if not icons:
                        print("no icons for %s %s; left as it was"
                              % (brand["name"], style["name"]), file=sys.stderr)
                        continue
                    payload = {
                        "generated": generated,
                        "set": brand["name"],
                        "brand": brand["slug"],
                        "style": style["name"],
                        "mode": style["mode"],
                        "colour": style["colour"],
                        "tune": style.get("tune", True),
                        "source": brand["source"],
                        "licence": brand["licence"],
                        "cat_order": sorted({icon["cat"] for icon in icons if icon.get("cat")}),
                        "count": len(icons),
                        "missing": missing,
                        "icons": icons,
                    }
                    if style.get("default"):
                        payload["default"] = True
                    write_catalog(style["data"], payload)
                    print("%-16s %-6s %4d icons (missing %d)"
                          % (brand["name"], style["name"], len(icons), len(missing)))
                continue
            icons, missing = collect(brand)
            if not icons:
                print("no icons for %s: %s is empty or missing; left as it was"
                      % (brand["name"], brand.get("dir") or brand["names"]), file=sys.stderr)
                continue
            mapping, order = categories_for(brand)
            for icon in icons:
                icon["cat"] = mapping.get(icon["name"], "")
            payload = {
                "generated": generated,
                "set": brand["name"],
                "source": brand["source"],
                "licence": brand["licence"],
                "cat_order": order,
                "count": len(icons),
                "missing": missing,
                "icons": icons,
            }
            payload["style"] = brand.get("style", "")
            write_catalog(brand["data"], payload)
            print("%-16s %4d icons (missing %d)" % (brand["name"], len(icons), len(missing)))

    entries = brands()
    write_hub(entries)
    for brand in entries:
        write_brand(brand, entries)
    write_sheets(entries)
    write_all(entries)
    print("brands: %s | total %d icons"
          % (", ".join("%s %d" % (b["name"], b["count"]) for b in entries),
             sum(b["count"] for b in entries)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
