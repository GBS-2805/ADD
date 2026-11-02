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
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
DATA = DOCS / "data"
CATALOG = ROOT / "tools/catalog.json"
ICONS = DATA / "icons.json"

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


def icon_count():
    if not ICONS.exists():
        fail("no docs/data/icons.json yet; run tools/fetch_icons.py first")
    return json.loads(ICONS.read_text(encoding="utf-8"))["count"]


def write(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("wrote %-28s %8d bytes" % (path.relative_to(ROOT), size(path)))


def build(catalog, count):
    generated = datetime.date.today().isoformat()
    index = {
        "generated": generated,
        "site": catalog["site"],
        "catalogs": [
            {"id": "tools", "title": "Tools", "href": "/tools/", "data": "/data/tools.json",
             "count": len(catalog["tools"])},
            {"id": "icons", "title": "Icons", "href": "/icons/", "data": "/data/icons.json",
             "count": count},
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
    count = icon_count()
    if not args.check:
        build(catalog, count)

    released = [app["name"] for app in catalog["tools"] if app.get("apk")]
    print("catalog: %d tools (%s shipping), %d fonts, %d projects, %d icons"
          % (len(catalog["tools"]), ", ".join(released), len(catalog["fonts"]),
             len(catalog["portfolio"]), count))

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
