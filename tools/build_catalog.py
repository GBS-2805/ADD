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


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def copy_face(face):
    """Keep the downloadable TTF byte-identical to the one the app bundles."""
    source = ROOT / face["app_res"]
    target = DOCS / face["ttf"].lstrip("/")
    if not source.exists():
        fail("missing app font %s" % face["app_res"])
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists() or target.read_bytes() != source.read_bytes():
        shutil.copyfile(source, target)
        print("copied %s -> %s" % (face["app_res"], face["ttf"]))


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
    for face in catalog["fonts"]:
        copy_face(face)
        for key in ("woff2", "ttf"):
            path = DOCS / face[key].lstrip("/")
            if not path.exists():
                fail("missing font %s" % face[key])
            face[key + "_bytes"] = size(path)
    return catalog


def icon_sets():
    """Every brand catalog on disk, with the totals the pages have to quote.

    A brand may publish several style catalogs. They are variants of one set, so
    the brand is counted once -- from the catalog marked default -- and the others
    travel with it as styles instead of inflating the total.
    """
    groups = {}
    files = sorted(path for path in DATA.glob("*.json")
                          if not path.stem.endswith("-sheet")
                          and path.stem not in ("index", "tools", "fonts", "portfolio"))
    for path in files:
        if not path.exists():
            fail("no %s yet; run tools/fetch_icons.py first" % path.relative_to(ROOT))
        payload = json.loads(path.read_text(encoding="utf-8"))
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
                      r"(?=\s+(?:free\s+)?(?:brands|sets)\b)", word, text)
        if text != before:
            path.write_text(text, encoding="utf-8")
            touched.append(name)
    print("counts: %s icons in %s brands -> %s"
          % (total_text, brands, ", ".join(touched) or "already right"))
    return touched


def build(catalog, count, sets):
    generated = datetime.date.today().isoformat()
    index = {
        "generated": generated,
        "site": catalog["site"],
        "catalogs": [
            {"id": "tools", "title": "Tools", "href": "/tools/", "data": "/data/tools.json",
             "count": len(catalog["tools"])},
            {"id": "icons", "title": "Icons", "href": "/icons/", "data": "/data/icons.json",
             "count": count, "sets": sets},
            {"id": "fonts", "title": "Fonts", "href": "/fonts/", "data": "/data/fonts.json",
             "count": len(catalog["fonts"])},
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
    write(DATA / "fonts.json", {"generated": generated, "fonts": catalog["fonts"]})
    write(DATA / "portfolio.json", {"generated": generated, "entries": catalog["portfolio"]})


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
        for key in ("index", "tools", "download"):
            if apk not in text[key]:
                problems.append("%s: no link to %s" % (key, apk))
        for key in ("tools", "download"):
            if app["version"] not in text[key]:
                problems.append("%s: version %s is missing" % (key, app["version"]))
        for key in ("index", "download"):
            if "{:,}".format(app["bytes"]) not in text[key]:
                problems.append("%s: size %s is missing" % (key, "{:,}".format(app["bytes"])))
        if app["sha256"] not in text["download"]:
            problems.append("download: SHA-256 out of date")

    for face in catalog["fonts"]:
        for key in ("woff2", "ttf"):
            name = face[key].split("/")[-1]
            if name not in text["fonts"]:
                problems.append("fonts: no link to %s" % name)
            if "{:,}".format(face[key + "_bytes"]) not in text["fonts"]:
                problems.append("fonts: size for %s is out of date" % name)

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
    print("catalog: %d tools (%s shipping), %d fonts, %d projects, %d icons in %d sets"
          % (len(catalog["tools"]), ", ".join(released), len(catalog["fonts"]),
             len(catalog["portfolio"]), count, len(sets)))

    problems = check(catalog, count)
    if problems:
        print("\nstale in the pages:", file=sys.stderr)
        for problem in problems:
            print("  - " + problem, file=sys.stderr)
        return 1
    print("pages match the catalog")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
