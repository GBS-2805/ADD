#!/usr/bin/env python3
"""Write and check one canonical/social metadata block on every modern page.

    python3 tools/seo_meta.py --write
    python3 tools/seo_meta.py --check
    python3 tools/seo_meta.py --page docs/icons/index.html --origin https://example.test

The block is derived from the page's own <title> and description and the origin in
`tools/site.json`. `docs/classic/` is frozen and noindex, so it is explicitly out of
scope. A missing, duplicated or stale block fails the check.
"""

import argparse
import html
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
SITE = ROOT / "tools" / "site.json"
START = "<!-- seo:start -->"
END = "<!-- seo:end -->"
TITLE = re.compile(r"<title[^>]*>(.*?)</title>", re.S | re.I)
META = re.compile(r"<meta\b[^>]*>", re.I)
ATTRIBUTE = re.compile(r'([\w:-]+)\s*=\s*"([^"]*)"')
OG = "assets/og.png"
OG_WIDTH = "1200"
OG_HEIGHT = "630"
OG_ALT = "ADD — a free design shelf"


def origin():
    return json.loads(SITE.read_text(encoding="utf-8"))["url"].rstrip("/")


def page_url(path):
    rel = Path(path).resolve().relative_to(DOCS.resolve())
    if rel.as_posix() == "index.html":
        return "/"
    if rel.name == "index.html":
        return "/" + rel.parent.as_posix().strip("/") + "/"
    return "/" + rel.as_posix()


def modern_pages():
    pages = []
    for path in sorted(DOCS.rglob("index.html")):
        rel = path.relative_to(DOCS)
        if rel.parts and rel.parts[0] == "classic":
            continue
        pages.append(path)
    return pages


def description(text):
    for tag in META.findall(text):
        attrs = {key.lower(): value for key, value in ATTRIBUTE.findall(tag)}
        if attrs.get("name", "").lower() == "description" and attrs.get("content"):
            return html.unescape(attrs["content"]).strip()
    return ""


def read_page(path):
    text = Path(path).read_text(encoding="utf-8")
    title = TITLE.search(text)
    if not title:
        raise SystemExit("seo_meta: %s has no <title>" % path)
    return (html.unescape(re.sub(r"\s+", " ", title.group(1))).strip(),
            description(text), text)


def block(path, origin_value, page_text=None):
    if page_text is None:
        title, desc, _ = read_page(path)
    else:
        match = TITLE.search(page_text)
        if not match:
            raise SystemExit("seo_meta: %s has no <title>" % path)
        title = html.unescape(re.sub(r"\s+", " ", match.group(1))).strip()
        desc = description(page_text)
    url = origin_value.rstrip("/") + page_url(path)
    image = origin_value.rstrip("/") + "/" + OG
    safe = lambda value: html.escape(value, quote=True)
    lines = [
        START,
        '<link rel="canonical" href="%s" />' % safe(url),
        '<meta property="og:type" content="website" />',
        '<meta property="og:site_name" content="ADD" />',
        '<meta property="og:title" content="%s" />' % safe(title),
        '<meta property="og:description" content="%s" />' % safe(desc),
        '<meta property="og:url" content="%s" />' % safe(url),
        '<meta property="og:image" content="%s" />' % safe(image),
        '<meta property="og:image:width" content="%s" />' % OG_WIDTH,
        '<meta property="og:image:height" content="%s" />' % OG_HEIGHT,
        '<meta property="og:image:alt" content="%s" />' % safe(OG_ALT),
        '<meta name="twitter:card" content="summary_large_image" />',
        '<meta name="twitter:title" content="%s" />' % safe(title),
        '<meta name="twitter:description" content="%s" />' % safe(desc),
        '<meta name="twitter:image" content="%s" />' % safe(image),
    ]
    if page_url(path) == "/":
        schema = {
            "@context": "https://schema.org",
            "@type": "WebSite",
            "name": "ADD",
            "url": origin_value.rstrip("/") + "/",
            "potentialAction": {
                "@type": "SearchAction",
                "target": origin_value.rstrip("/") + "/search/?q={search_term_string}",
                "query-input": "required name=search_term_string",
            },
        }
        lines.append('<script type="application/ld+json">%s</script>'
                     % json.dumps(schema, ensure_ascii=False, separators=(",", ":")))
    lines.append(END)
    return "\n".join(lines)


def insert(text, new_block):
    if text.count(START) > 1 or text.count(END) > 1:
        raise SystemExit("seo_meta: more than one marker pair")
    if START in text or END in text:
        before, rest = text.split(START, 1)
        _, after = rest.split(END, 1)
        return before + new_block + after
    if "</head>" not in text:
        raise SystemExit("seo_meta: page has no </head>")
    return text.replace("</head>", new_block + "\n</head>", 1)


def check_page(path, origin_value):
    _, _, text = read_page(path)
    expected = block(path, origin_value, text)
    if START not in text or END not in text:
        return "missing marked metadata block"
    before, rest = text.split(START, 1)
    found, _ = rest.split(END, 1)
    actual = START + found + END
    if actual != expected:
        return "metadata block differs from the page title/description/origin"
    return None


def write_pages(origin_value=None):
    """Insert or refresh the block on every modern page; return the count."""
    origin_value = (origin_value or origin()).rstrip("/")
    pages = modern_pages()
    for path in pages:
        _, _, text = read_page(path)
        updated = insert(text, block(path, origin_value, text))
        if updated != text:
            path.write_text(updated, encoding="utf-8")
    return len(pages)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true", help="insert or replace the block")
    mode.add_argument("--check", action="store_true", help="fail on a missing or stale block")
    mode.add_argument("--page", metavar="PATH", help="print expected values for one page")
    parser.add_argument("--origin", help="override tools/site.json url")
    args = parser.parse_args()
    origin_value = (args.origin or origin()).rstrip("/")

    if args.page:
        path = Path(args.page)
        if not path.is_absolute():
            path = ROOT / path
        title, desc, text = read_page(path)
        print(json.dumps({"path": page_url(path), "title": title, "description": desc,
                          "url": origin_value + page_url(path),
                          "image": origin_value + "/" + OG,
                          "block": block(path, origin_value, text)}, indent=2))
        return 0

    pages = modern_pages()
    if args.write:
        print("seo_meta: wrote %d modern pages" % write_pages(origin_value))
        return 0

    failures = []
    for path in pages:
        problem = check_page(path, origin_value)
        if problem:
            failures.append((path.relative_to(ROOT).as_posix(), problem))
    if failures:
        for name, problem in failures:
            print("  %s: %s" % (name, problem))
        print("seo_meta: %d of %d pages are stale" % (len(failures), len(pages)))
        return 1
    print("seo_meta: OK - %d modern pages carry canonical and social metadata" % len(pages))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
