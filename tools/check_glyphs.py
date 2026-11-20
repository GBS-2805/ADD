#!/usr/bin/env python3
"""Fail when a page paints a character none of the shipped fonts contains.

    python3 tools/check_glyphs.py

Every page is set in a handful of webfonts that travel with the site. A character
outside all of them is not an error the browser reports: it silently substitutes a
system font. That is how the card arrow came to sit two pixels apart on CI and on
a laptop -- the artboards recorded "Poppins" for glyphs Poppins has never had, so
the exporter's measurement of the page drifted from every other machine's.

The site ships `add-symbols.woff2`, a 2.3 KB subset holding exactly the marks the
text faces lack, built by `tools/build_symbols.py`. This gate checks four things:

  * every shipped font file still hashes to what `tools/glyphs.json` recorded, so
    a font cannot change without the coverage numbers being rebuilt;
  * a character a page paints that no face covers fails, naming it and the file,
    so the fix is to add it to the subset rather than meet the drift in CI later;
  * a glyph in the subset that no page paints fails, so the file cannot quietly
    accumulate marks that were once needed;
  * every subset glyph is inside the `unicode-range` of its @font-face, because a
    glyph the browser is never told to fetch is not a glyph you shipped.

Standard library only: this runs in CI's shelf job, which installs nothing.
fontTools is needed only to *build* the subset, not to check it.
"""

from __future__ import annotations

import hashlib
import html
import json
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
FONTS = DOCS / "assets" / "fonts"
SUBSET = FONTS / "add-symbols.woff2"
SITE_CSS = DOCS / "assets" / "site.css"
MANIFEST = ROOT / "tools" / "glyphs.json"

# Characters a page may use with no glyph in any font: whitespace and ASCII
# punctuation, which the browser needs no face to paint here. Listed explicitly so
# a curly quote or an em dash cannot slip through unnoticed.
ALWAYS_FINE = set("\t\n\r ") | set("!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~")


def spans(values: list[str]) -> set[int]:
    """Codepoints from compact "0394" / "2190-2197" strings."""
    points: set[int] = set()
    for value in values:
        if "-" in value:
            a, b = value.split("-")
            points |= set(range(int(a, 16), int(b, 16) + 1))
        else:
            points.add(int(value, 16))
    return points


def load() -> dict:
    try:
        return json.loads(MANIFEST.read_text(encoding="utf-8"))
    except OSError:
        raise SystemExit("glyphs: FAIL - %s is missing; run tools/build_symbols.py"
                         % MANIFEST.relative_to(ROOT))
    except ValueError as exc:
        raise SystemExit("glyphs: FAIL - %s is not valid JSON: %s"
                         % (MANIFEST.relative_to(ROOT), exc))


def stale(data: dict) -> list[str]:
    """Font files whose bytes no longer match the recorded coverage."""
    recorded = data.get("sha256") or {}
    changed = []
    for path in sorted(FONTS.glob("*.woff2")):
        want = recorded.get(path.name)
        if want is None:
            changed.append("%s (not in the manifest)" % path.name)
        elif hashlib.sha256(path.read_bytes()).hexdigest() != want:
            changed.append("%s (bytes changed)" % path.name)
    for name in recorded:
        if not (FONTS / name).is_file():
            changed.append("%s (in the manifest, gone from disk)" % name)
    return changed


def painted(path: Path) -> list[str]:
    """The strings a page puts on screen, from markup and from its CSS."""
    # Decode entities first: the burger mark is written &#9776; in markup, and an
    # entity read literally looks like the harmless ASCII "&#9776;". That is
    # exactly the kind of miss this gate exists to prevent.
    body = html.unescape(path.read_text(encoding="utf-8", errors="replace"))
    chunks: list[str] = []

    stripped = re.sub(r"<script\b.*?</script>", " ", body, flags=re.S | re.I)
    stripped = re.sub(r"<style\b.*?</style>", " ", stripped, flags=re.S | re.I)
    stripped = re.sub(r"<!--.*?-->", " ", stripped, flags=re.S)
    for match in re.finditer(r">([^<>]+)<", stripped):
        chunks.append(match.group(1))
    for match in re.finditer(
            r'(?:content|placeholder|alt|title|aria-label)="([^"]*)"', body):
        chunks.append(match.group(1))

    for match in re.finditer(r'content:\s*"((?:[^"\\]|\\.)*)"', body):
        chunks.append(re.sub(r"\\([0-9a-fA-F]{1,6})\s?",
                             lambda m: chr(int(m.group(1), 16)), match.group(1)))
    return chunks


def unicode_range() -> set[int]:
    """The codepoints @font-face lets the browser fetch the subset for."""
    try:
        css = SITE_CSS.read_text(encoding="utf-8")
    except OSError:
        return set()
    block = re.search(r"@font-face\s*\{[^}]*ADD Symbols[^}]*\}", css)
    if not block:
        return set()
    span = re.search(r"unicode-range:\s*([^;]+);", block.group(0))
    if not span:
        return set()
    # "U+0394, U+2190-2197": only the first end of a range carries the U+ prefix.
    return spans([part.strip().replace("U+", "") for part in span.group(1).split(",")
                  if part.strip()])


def modern_pages() -> list[Path]:
    return [p for p in sorted(DOCS.rglob("index.html")) if "classic" not in p.parts]


def main() -> int:
    if not FONTS.is_dir():
        print("glyphs: FAIL - %s is missing" % FONTS.relative_to(ROOT))
        return 1
    if not SUBSET.is_file():
        print("glyphs: FAIL - %s is missing; the pages use marks no text face has"
              % SUBSET.relative_to(ROOT))
        return 1

    data = load()
    changed = stale(data)
    if changed:
        print("glyphs: FAIL - %d font file(s) no longer match %s"
              % (len(changed), MANIFEST.relative_to(ROOT)))
        for name in changed:
            print("  %s" % name)
        print("  fix: rerun /tmp/venv/bin/python tools/build_symbols.py, which"
              " rebuilds the coverage the gate reads.")
        return 1

    points = {ord(c) for c in ALWAYS_FINE}
    for ranges in data.get("faces", {}).values():
        points |= spans(ranges)

    missing: dict[int, str] = {}
    everywhere: set[int] = set()
    for path in modern_pages():
        for chunk in painted(path):
            for char in chunk:
                cp = ord(char)
                everywhere.add(cp)
                if cp not in points:
                    missing.setdefault(cp, str(path.relative_to(ROOT)))

    if missing:
        print("glyphs: FAIL - %d character(s) no shipped font covers" % len(missing))
        for cp, rel in sorted(missing.items()):
            print("  %-28s U+%04X  %s %r"
                  % (rel, cp, unicodedata.name(chr(cp), "?"), chr(cp)))
        print("  fix: add the mark to MARKS in tools/build_symbols.py and rebuild,"
              " or stop painting it.")
        print("       The browser substitutes a system font silently, which moves"
              " the artboards a pixel per machine.")
        return 1

    subset = spans(data["faces"].get("add-symbols.woff2", []))
    declared = unicode_range()
    unlisted = sorted(subset - declared)
    if unlisted:
        print("glyphs: FAIL - the subset has %d glyph(s) @font-face never fetches"
              % len(unlisted))
        for cp in unlisted:
            print("  U+%04X  %s  (absent from unicode-range in %s)"
                  % (cp, unicodedata.name(chr(cp), "?"), SITE_CSS.relative_to(ROOT)))
        print("  fix: widen unicode-range, or the browser skips the file and falls"
              " through to the system font.")
        return 1

    spare = sorted(subset - everywhere)
    if spare:
        print("glyphs: FAIL - %d glyph(s) in the subset are unused" % len(spare))
        for cp in spare:
            print("  U+%04X  %s" % (cp, unicodedata.name(chr(cp), "?")))
        print("  fix: drop the mark from MARKS in tools/build_symbols.py and"
              " rebuild, so every byte shipped is a mark a page paints.")
        return 1

    print("glyphs: OK - every painted character is in a shipped face; the subset"
          " holds %d marks, all used, all inside unicode-range" % len(subset))
    return 0


if __name__ == "__main__":
    sys.exit(main())
