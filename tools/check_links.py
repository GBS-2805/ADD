#!/usr/bin/env python3
"""Fail when a page links to a file or route the shelf does not carry.

Every `href` and `src` in `docs/**/*.html` is resolved the way a browser would:
relative to the page, `/...` from the site root, a directory through its
`index.html`, and the query and fragment dropped. The frozen `docs/classic/` tree
is included because it ships with the site. External addresses, `mailto:`,
`tel:`, `javascript:`, `data:` and bare fragments are not file claims and are
skipped.

The tags are read with `HTMLParser`, not a regular expression, so the runner
strings inside an inline script are not mistaken for links -- `src="' + item.ttf
+ '"` is JavaScript, not a broken file.

    python3 tools/check_links.py             # the whole shelf
    python3 tools/check_links.py --root DIR  # a fixture, for the checker itself

This is the gate `tools/deploy_vercel.py` runs before it uploads: a 404 discovered
after the upload is a reader's problem, and a dead link is invisible to a route
check that only asks whether the page itself answers.
"""

import argparse
import sys
import urllib.parse
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"

# Schemes and prefixes that do not name a file inside the shelf.
SKIP_SCHEMES = {"http", "https", "mailto", "tel", "javascript", "data", "ftp", "blob"}


class Links(HTMLParser):
    """Collect href/src values from real tags, ignoring script and style text."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.urls = []

    def handle_starttag(self, tag, attrs):
        for name, value in attrs:
            if name in ("href", "src") and value:
                self.urls.append(value.strip())


def pages_under(root):
    return sorted(root.rglob("*.html"))


def target_for(page, url, root):
    """The file a browser would request, or None when the URL makes no file claim."""
    if not url or url.startswith("#") or url.startswith("//"):
        return None
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme.lower() in SKIP_SCHEMES or parsed.netloc:
        return None
    path = urllib.parse.unquote(parsed.path)
    if not path:
        return None
    target = (root / path.lstrip("/")) if path.startswith("/") else (page.parent / path)
    if path.endswith("/") or target.is_dir():
        return target / "index.html"
    return target


def check(root):
    """Return the broken references, grouped by the page that makes them."""
    broken = {}
    checked = 0
    pages = pages_under(root)
    for page in pages:
        parser = Links()
        parser.feed(page.read_text(encoding="utf-8", errors="replace"))
        parser.close()
        for url in parser.urls:
            target = target_for(page, url, root)
            if target is None:
                continue
            checked += 1
            try:
                exists = target.exists()
            except OSError:
                exists = False
            if not exists:
                broken.setdefault(page, []).append((url, target))
    return pages, checked, broken


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=Path, default=DOCS,
                        help="the site folder to crawl (default: docs/)")
    args = parser.parse_args()

    root = args.root.resolve()
    if not root.is_dir():
        print("links: FAILED - %s is not a folder" % root, file=sys.stderr)
        return 2

    pages, checked, broken = check(root)
    if broken:
        print("broken internal links:")
        for page, missing in broken.items():
            print("  " + page.relative_to(root).as_posix())
            for url, target in missing:
                print("    %s  ->  %s" % (url, target.relative_to(root).as_posix()))
        count = sum(len(v) for v in broken.values())
        print("links: %d checked, %d broken across %d of %d pages"
              % (checked, count, len(broken), len(pages)))
        return 1

    print("links: %d checked, 0 broken across %d pages" % (checked, len(pages)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
