#!/usr/bin/env python3
"""Build the site's JSON catalogs from tools/catalog.json, then check the pages.

The site doubles as the database behind the tool apps, so the numbers an app
reads and the numbers printed on the pages have to come from the same files.
This script:

  * re-measures everything that actually ships -- the APK, the font binaries,
    the icon set -- instead of trusting a hand-typed size;
  * copies each app font into docs/assets/fonts as the downloadable TTF, so the
    file the phone bundles and the file the site offers cannot drift apart;
  * writes docs/data/{index,tools,fonts,portfolio}.json;
  * checks that every page still quotes the measured numbers, and fails loudly
    when one has gone stale.

    python3 tools/build_catalog.py            # write, then check
    python3 tools/build_catalog.py --check    # check the pages as they are
"""

import argparse
import datetime
import hashlib
import json
import re
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
DATA = DOCS / "data"
CATALOG = ROOT / "tools/catalog.json"
ICONS = DATA / "icons.json"
COOLICONS = DATA / "coolicons.json"

PAGES = {
    "index": DOCS / "index.html",
    "tools": DOCS / "tools/index.html",
    "icons": DOCS / "icons/index.html",
    "fonts": DOCS / "fonts/index.html",
    "portfolio": DOCS / "portfolio/index.html",
    "download": DOCS / "download/index.html",
}


def fail(message):
    raise SystemExit("build_catalog: " + message)


def size(path):
    return path.stat().st_size


def human_bytes(number):
    """A size a reader can scan: whole MB, whole KB, then bytes. No decimals.

    The catalogs keep the exact count -- the JSON is the app's data and a checksum
    needs the last byte -- but a page only has to be readable, and 2,348,788 bytes
    spelled out is noise where 2 MB is the fact.
    """
    if number >= 1048576:
        return "%d MB" % round(number / 1048576)
    if number >= 1024:
        return "%d KB" % round(number / 1024)
    return "%d bytes" % number


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def copy_face(style):
    """Keep an app-bundled TTF byte-identical to the one the app ships.

    Most styles are fetched from Google Fonts and have no app_res; the three the
    apps bundle do, and those files must never be replaced by a download.
    """
    if not style.get("app_res"):
        return
    source = ROOT / style["app_res"]
    target = DOCS / style["ttf"].lstrip("/")
    if not source.exists():
        fail("missing app font %s" % style["app_res"])
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists() or target.read_bytes() != source.read_bytes():
        shutil.copyfile(source, target)
        print("copied %s -> %s" % (style["app_res"], style["ttf"]))


def measure(catalog):
    for app in catalog["tools"]:
        apk = app.get("apk")
        if not apk:
            app["bytes"] = None
            app["sha256"] = None
            continue
        path = DOCS / apk.lstrip("/")
        if not path.exists():
            fail("missing apk %s" % apk)
        app["bytes"] = size(path)
        app["sha256"] = digest(path)
    for family in catalog["fonts"]:
        for style in family["styles"]:
            copy_face(style)
            for key in ("woff2", "woff", "ttf"):
                path = DOCS / style[key].lstrip("/")
                if not path.exists():
                    fail("missing font %s" % style[key])
                style[key + "_bytes"] = size(path)
        family["styles_count"] = len(family["styles"])
        family["woff2_bytes"] = sum(style["woff2_bytes"] for style in family["styles"])
        family["woff_bytes"] = sum(style["woff_bytes"] for style in family["styles"])
        family["ttf_bytes"] = sum(style["ttf_bytes"] for style in family["styles"])
    return catalog


def load(path):
    """A catalog index with its markup chunks merged back in.

    fetch_icons.py writes each catalog as a names-first index plus chunks under
    data/<stem>/: the index alone carries the counts, the merged form the glyphs.
    """
    payload = json.loads(path.read_text(encoding="utf-8"))
    for name in payload.get("chunks") or []:
        part = json.loads((path.parent / name).read_text(encoding="utf-8"))
        payload.setdefault("icons", []).extend(part.get("icons", []))
    return payload


def icon_sets():
    """Every brand catalog on disk, with the totals the pages have to quote.

    A brand may publish several style catalogs. They are variants of one set, so
    the brand is counted once -- from the catalog marked default -- and the others
    travel with it as styles instead of inflating the total.
    """
    groups = {}
    files = sorted(path for path in DATA.glob("*.json")
                          if not path.stem.endswith("-sheet")
                          and path.stem not in ("index", "tools", "fonts", "portfolio",
                                 "pages", "assets"))
    for path in files:
        if not path.exists():
            fail("no %s yet; run tools/fetch_icons.py first" % path.relative_to(ROOT))
        payload = load(path)
        if "count" not in payload:
            continue
        groups.setdefault(payload.get("brand") or path.stem, []).append((path, payload))

    sets, total = [], 0
    for key, items in groups.items():
        path, primary = next(((p, d) for p, d in items if d.get("default")), items[0])
        total += primary["count"]
        entry = {"id": primary.get("set", key), "source": primary.get("source"),
                 "licence": primary.get("licence"), "style": primary.get("style"),
                 "count": primary["count"], "data": "/data/" + path.name}
        if len(items) > 1:
            entry["styles"] = [{"name": d.get("style", p.stem), "count": d["count"],
                                "data": "/data/" + p.name} for p, d in items]
        sets.append(entry)
    return total, sets


def write(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("wrote %-28s %8d bytes" % (path.relative_to(ROOT), size(path)))


# The two icon pages are written by fetch_icons.py from tokens (__TOTAL__,
# __BRANDCOUNT__) and carry a count per brand in prose. Rewriting "N icons" there
# replaced each brand's own number with the shelf total, so they are left out.
COUNT_PAGES = ["docs/index.html", "docs/tools/index.html", "docs/portfolio/index.html",
               "docs/download/index.html", "docs/fonts/index.html"]
BRAND_WORDS = {1: "one", 2: "two", 3: "three", 4: "four", 5: "five", 6: "six",
               7: "seven", 8: "eight", 9: "nine", 10: "ten"}


def refresh_counts(total, brands):
    """Rewrite every icon total and brand count the pages state.

    The shelf quotes those numbers in headings, meta descriptions, drawers and
    footers. Rather than chase them by hand whenever a brand is added, the
    generator rewrites the phrases it owns -- "N icons", "N-icon", "in four
    brands", "four free sets" -- from the catalogs it has just counted. The
    releases page is deliberately left out: its notes are history and keep the
    numbers they were written with.
    """
    total_text = "{:,}".format(total)
    word = BRAND_WORDS.get(brands, str(brands))
    touched = []
    for name in COUNT_PAGES:
        path = ROOT / name
        if not path.exists():
            continue
        before = path.read_text(encoding="utf-8")
        text = re.sub(r"(?<![\d,])\d[\d,]*(?=\s+(?:icons|shapes)\b)", total_text, before)
        text = re.sub(r"\b\d[\d,]*(?=-icon\b)", total_text, text)
        text = re.sub(r"\b(?:one|two|three|four|five|six|seven|eight|nine|ten|\d+)"
                      r"(?=\s+(?:free\s+)?(?:icon\s+)?(?:brands|sets)\b)", word, text)
        # The stats list states the total with no unit beside it, and a material
        # card repeats it after the brand count.
        text = re.sub(r"(?<=<span>Icons:</span><b>)\d[\d,]*(?=</b>)", total_text, text)
        text = re.sub(r"(?<=Icons ·)([^<]*· )\d[\d,]*(?!\d)", r"\g<1>" + total_text, text)
        if text != before:
            path.write_text(text, encoding="utf-8")
            touched.append(name)
    print("counts: %s icons in %s brands -> %s"
          % (total_text, brands, ", ".join(touched) or "already right"))
    return touched


def build(catalog, count, sets):
    generated = datetime.date.today().isoformat()
    font_styles = sum(family["styles_count"] for family in catalog["fonts"])
    index = {
        "generated": generated,
        "site": catalog["site"],
        "catalogs": [
            {"id": "tools", "title": "Tools", "href": "/tools/", "data": "/data/tools.json",
             "count": len(catalog["tools"])},
            {"id": "icons", "title": "Icons", "href": "/icons/", "data": "/data/icons.json",
             "count": count, "sets": sets},
            {"id": "fonts", "title": "Fonts", "href": "/fonts/", "data": "/data/fonts.json",
             "count": font_styles},
            {"id": "portfolio", "title": "Portfolio", "href": "/portfolio/",
             "data": "/data/portfolio.json", "count": len(catalog["portfolio"])},
        ],
        "downloads": [
            {"id": app["id"], "name": app["name"], "version": app["version"],
             "apk": app["apk"], "bytes": app["bytes"], "sha256": app["sha256"]}
            for app in catalog["tools"] if app.get("apk")
        ],
    }
    write(DATA / "index.json", index)
    write(DATA / "tools.json", {"generated": generated, "tools": catalog["tools"]})
    write(DATA / "fonts.json", {"generated": generated, "styles": font_styles,
                                "families": catalog["fonts"]})
    write(DATA / "portfolio.json", {"generated": generated, "entries": catalog["portfolio"]})


def category_sizes():
    """Category tallies from the catalogs, so a caption like "102 icons" is legal."""
    sizes = set()
    for path in DATA.glob("*.json"):
        if path.stem.endswith("-sheet") or path.stem in ("index", "tools", "fonts",
                                                         "portfolio", "pages", "assets"):
            continue
        try:
            payload = load(path)
        except ValueError:
            continue
        if "count" not in payload:
            continue
        tally = {}
        for glyph in payload.get("icons", []):
            cat = glyph.get("cat")
            if cat:
                tally[cat] = tally.get(cat, 0) + 1
        sizes.update(tally.values())
    return sizes


def audit(total, sets):
    """Every "N icons" and every "N brands" on the site must match the catalogs.

    refresh_counts fixes the phrases it knows, but a sentence phrased some other
    way escapes it -- which is how a landing page kept saying 3,876 while its own
    meta description said 4,373. This reads the built pages back and refuses any
    count that is neither the total, a set's own count, nor a category size.
    """
    allowed = {total} | {entry["count"] for entry in sets} | category_sizes()
    word = BRAND_WORDS.get(len(sets), str(len(sets)))
    problems = []
    for path in sorted(DOCS.rglob("*.html")):
        rel = path.relative_to(ROOT).as_posix()
        if rel.startswith(("docs/releases/", "docs/figma/")):
            continue
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(r"(?<![\w.-])(\d[\d,]*)\s+icons\b", text):
            value = int(match.group(1).replace(",", ""))
            if value not in allowed:
                problems.append("%s: says %s icons, which is no total, set or category here"
                                % (rel, match.group(1)))
        for match in re.finditer(r"<span>Icons:</span><b>(\d[\d,]*)</b>", text):
            if int(match.group(1).replace(",", "")) != total:
                problems.append("%s: stats list says %s icons, not %s"
                                % (rel, match.group(1), "{:,}".format(total)))
        for match in re.finditer(r"Icons ·[^<]*·\s*(\d[\d,]*)", text):
            if int(match.group(1).replace(",", "")) != total:
                problems.append("%s: material card says %s icons, not %s"
                                % (rel, match.group(1), "{:,}".format(total)))
        for match in re.finditer(r"\b(one|two|three|four|five|six|seven|eight|nine|ten|\d+)"
                                 r"\s+(?:free\s+)?(?:icon\s+)?(?:brands|sets)\b",
                                 text, re.IGNORECASE):
            stated = match.group(1).lower()
            if stated not in (word, str(len(sets))):
                problems.append("%s: says \"%s\" but %s brands are built"
                                % (rel, match.group(0), len(sets)))
    return problems


def check(catalog, count):
    """Every number a page states must match the files that ship."""
    text, problems = {}, []
    for key, path in PAGES.items():
        if path.exists():
            text[key] = path.read_text(encoding="utf-8")
        else:
            text[key] = ""
            problems.append("missing page %s" % path.relative_to(ROOT))

    for app in catalog["tools"]:
        if not app.get("apk"):
            continue
        apk = app["apk"].lstrip("/")
        # The download page is the only page that carries the APK's address and the
        # figures measured from it; every other page just points there.
        if apk not in text["download"]:
            problems.append("download: no link to %s" % apk)
        if app["version"] not in text["download"]:
            problems.append("download: version %s is missing" % app["version"])
        if human_bytes(app["bytes"]) not in text["download"]:
            problems.append("download: size %s is missing" % human_bytes(app["bytes"]))
        if app["sha256"] not in text["download"]:
            problems.append("download: SHA-256 out of date")

    # The fonts page renders its sheets from /data/fonts.json, so its sizes are
    # never typed into HTML; check the page still reads that catalog and keeps
    # the family picker the sheet is driven by.
    if "data/fonts.json" not in text["fonts"]:
        problems.append("fonts: no link to /data/fonts.json")
    if 'id="families"' not in text["fonts"]:
        problems.append("fonts: the family sheets are missing")
    for family in catalog["fonts"]:
        if not family.get("styles"):
            problems.append("fonts: %s has no styles" % family["family"])

    if "{:,}".format(count) not in text["index"]:
        problems.append("index: icon count %s is missing" % "{:,}".format(count))
    if "{:,}".format(count) not in text["icons"]:
        problems.append("icons: icon count %s is missing" % "{:,}".format(count))

    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="check the pages, write nothing")
    args = parser.parse_args()

    catalog = measure(json.loads(CATALOG.read_text(encoding="utf-8")))
    count, sets = icon_sets()
    refresh_counts(count, len(sets))
    if not args.check:
        build(catalog, count, sets)

    released = [app["name"] for app in catalog["tools"] if app.get("apk")]
    font_styles = sum(len(family["styles"]) for family in catalog["fonts"])
    print("catalog: %d tools (%s shipping), %d font families / %d styles, %d projects, %d icons in %d sets"
          % (len(catalog["tools"]), ", ".join(released), len(catalog["fonts"]), font_styles,
             len(catalog["portfolio"]), count, len(sets)))

    problems = check(catalog, count) + audit(count, sets)
    if problems:
        print("\nstale in the pages:", file=sys.stderr)
        for problem in problems:
            print("  - " + problem, file=sys.stderr)
        return 1
    print("pages match the catalog")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
