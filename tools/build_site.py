#!/usr/bin/env python3
"""Write the files that have to agree with the pages on disk.

`docs/data/pages.json`, `docs/sitemap.xml` and `docs/robots.txt` are derived from
`docs/**/index.html` rather than typed by hand, so the search index, the crawler map
and the deploy's route check can never name a page that is not there, or miss one
that is.

    python3 tools/build_site.py            # write them
    python3 tools/build_site.py --check    # fail when what is on disk is stale

The output is deterministic -- no dates, no ordering that depends on the filesystem --
which is what makes `--check` usable as a gate before a deploy.
"""

import argparse
import html
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
SITE = ROOT / "tools/site.json"

# Vercel consumes its config on upload and does not serve it, so it is not a route.
NOT_SERVED = {"vercel.json"}

PAGES = DOCS / "data/pages.json"
SITEMAP = DOCS / "sitemap.xml"
ROBOTS = DOCS / "robots.txt"

TITLE = re.compile(r"<title[^>]*>(.*?)</title>", re.S | re.I)
H1 = re.compile(r"<h1[^>]*>(.*?)</h1>", re.S | re.I)
META = re.compile(r"<meta\b[^>]*>", re.I)
ATTRIBUTE = re.compile(r'([\w:-]+)\s*=\s*"([^"]*)"')
TAG = re.compile(r"<[^>]+>")
SPACES = re.compile(r"\s+")

# One expected content type per suffix. A host that serves the APK as text/html
# still answers 200, which is the failure worth catching.
TYPES = {
    ".html": "text/html",
    ".css": "text/css",
    ".js": "javascript",
    ".json": "application/json",
    ".webp": "image/webp",
    ".svg": "image/svg+xml",
    ".apk": "android.package-archive",
    ".woff2": "font/woff2",
    ".ttf": "font/ttf",
    ".ico": "image/",
    ".png": "image/png",
    ".jpg": "image/jpeg",
}


def origin():
    return json.loads(SITE.read_text(encoding="utf-8"))["url"].rstrip("/")


def clean(text):
    return SPACES.sub(" ", html.unescape(TAG.sub(" ", text))).strip()


def description(text):
    for tag in META.findall(text):
        attrs = {k.lower(): v for k, v in ATTRIBUTE.findall(tag)}
        if attrs.get("name", "").lower() == "description" and attrs.get("content"):
            return html.unescape(attrs["content"]).strip()
    return ""


def route_of(path):
    rel = path.relative_to(DOCS)
    return "/" if rel == pathlib.Path("index.html") else "/" + rel.parent.as_posix() + "/"


def pages():
    """Every page in the shelf, in path order, with what a search box needs."""
    found = []
    for path in sorted(DOCS.rglob("index.html")):
        text = path.read_text(encoding="utf-8", errors="replace")
        title = TITLE.search(text)
        lede = H1.search(text)
        route = route_of(path)
        found.append({
            "path": route,
            "title": clean(title.group(1)) if title else route,
            "description": description(text),
            "heading": clean(lede.group(1)) if lede else "",
            "section": route.strip("/").split("/")[0],
        })
    return found


def routes():
    """Every file in docs/, as (route, expected content type), for the deploy check.

    Scanning the tree instead of listing routes by hand means a page added to the
    shelf is verified the next time it deploys, without anyone remembering to add it.
    """
    found = []
    for path in sorted(DOCS.rglob("*")):
        if not path.is_file():
            continue
        wanted = TYPES.get(path.suffix.lower())
        if not wanted:
            continue
        rel = path.relative_to(DOCS).as_posix()
        if rel in NOT_SERVED:
            continue
        found.append(("/" + rel, wanted))
    return found


def pages_json():
    data = {"origin": origin(), "count": len(pages()), "pages": pages()}
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def sitemap():
    base = origin()
    lines = ['<?xml version="1.0" encoding="UTF-8"?>',
             '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">']
    for page in pages():
        lines.append("  <url><loc>%s</loc></url>" % (base + page["path"]))
    lines.append("</urlset>")
    return "\n".join(lines) + "\n"


def robots():
    return "User-agent: *\nAllow: /\n\nSitemap: %s/sitemap.xml\n" % origin()


def outputs():
    return {PAGES: pages_json(), SITEMAP: sitemap(), ROBOTS: robots()}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true",
                        help="write nothing; fail when the generated files are stale")
    args = parser.parse_args()

    written = outputs()
    stale = []
    for path, text in written.items():
        on_disk = path.read_text(encoding="utf-8") if path.exists() else None
        if on_disk != text:
            stale.append(path)

    if args.check:
        if stale:
            print("stale: " + ", ".join(p.relative_to(ROOT).as_posix() for p in stale))
            print("run python3 tools/build_site.py, then commit the result")
            sys.exit(1)
        print("site: OK - %d pages, %d routes" % (len(pages()), len(routes())))
        return

    for path, text in written.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    empty = [p["path"] for p in pages() if not p["description"]]
    print("site: %d pages, %d routes -> data/pages.json, sitemap.xml, robots.txt"
          % (len(pages()), len(routes())))
    if empty:
        print("  no meta description: " + ", ".join(empty))


if __name__ == "__main__":
    main()
