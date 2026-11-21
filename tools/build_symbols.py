#!/usr/bin/env python3
"""Build `docs/assets/fonts/add-symbols.woff2`, the marks no text face carries.

    python3 tools/build_symbols.py [--check]

The pages are set in Poppins, Spectral, Archivo, Plex Mono, Libre Franklin and
Instrument Serif. None of them has the arrows or the burger mark, so the browser
quietly substitutes whichever system font happens to hold them -- and the
artboard exporter, which measures the page, recorded the *text* face for a glyph
that face cannot paint. That disagreement is what moved boards a pixel per
machine.

This builds a tiny face from DejaVu Sans holding exactly the marks the pages
paint, renamed to the family "ADD Symbols" so it never collides with a system
DejaVu. `tools/check_glyphs.py` then holds both ends: a painted character with no
glyph here fails, and a glyph here that no page paints fails.

Requires fontTools + brotli (the venv at /tmp/venv has both):

    /tmp/venv/bin/python tools/build_symbols.py
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/assets/fonts/add-symbols.woff2"
# The gate that enforces this can only use the standard library (CI's shelf job
# installs nothing), so the coverage it needs is written out here: which
# codepoints every shipped face has, and the hash of the subset those numbers
# describe. Ranges, not a list, so this file stays a few hundred bytes.
MANIFEST = ROOT / "tools/glyphs.json"
FONT_DIR = ROOT / "docs/assets/fonts"

# One glyph per line, with the page that paints it. Adding a mark means adding a
# line here; check_glyphs.py fails until the subset on disk matches.
MARKS = {
    0x0394: "Delta: the dE00 colour-distance labels on the figma page",
    0x2190: "left arrow",
    0x2192: "right arrow: 'All 606 Lineicons icons ->'",
    0x2194: "left-right arrow",
    0x2197: "up-right arrow: the outbound link mark",
    0x2630: "trigram burger: the menu button mark",
}

FAMILY = "ADD Symbols"
STYLE = "Book"
PSNAME = "ADDSymbols-Regular"

# A DejaVu build, in either of the usual places. Any DejaVu Sans with these
# marks will do; the glyphs are drawn the same.
SOURCES = (
    "/tmp/dejavu/dejavu-fonts-ttf-2.37/ttf/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
)


def source() -> Path:
    for name in SOURCES:
        path = Path(name)
        if path.is_file():
            return path
    raise SystemExit(
        "build_symbols: no DejaVu Sans found. Install fonts-dejavu or point\n"
        "  SOURCES at a copy; the marks come from there.")


def build(dest: Path) -> int:
    from fontTools import subset
    from fontTools.ttLib import TTFont

    src = source()
    f = TTFont(str(src))
    have = f.getBestCmap()
    missing = [cp for cp in MARKS if cp not in have]
    if missing:
        raise SystemExit("build_symbols: %s lacks %s" % (
            src.name, ", ".join("U+%04X" % cp for cp in missing)))

    # Keep only the marks (and what they need to lay out), then rename the family
    # so a machine with real DejaVu never resolves this file by accident.
    opts = subset.Options()
    opts.flavor = "woff2"
    opts.desubroutinize = True
    opts.drop_tables += ["MATH", "GDEF", "GPOS", "GSUB"]
    opts.notdef_outline = False
    opts.layout_features = []
    options_text = ",".join("U+%04X" % cp for cp in MARKS)
    subsetter = subset.Subsetter(options=opts)
    subsetter.populate(unicodes=[cp for cp in MARKS])
    subsetter.subset(f)

    for record in f["name"].names:
        if record.nameID in (1, 16):
            record.string = FAMILY
        elif record.nameID in (2, 17):
            record.string = STYLE
        elif record.nameID == 4:
            record.string = FAMILY
        elif record.nameID == 6:
            record.string = PSNAME
    # A build that stamps the clock cannot be checked in: two runs would produce
    # different bytes and the hash in glyphs.json would never match. Creating a
    # TTFont recalculates head.modified on save, so turn that off and pin both
    # dates. (The epoch is not representable in the 1904-based LONGDATETIME, so
    # use its first valid second.)
    f.recalcTimestamp = False
    f["head"].created = 0
    f["head"].modified = 0
    f["head"].fontRevision = 1.0
    f.flavor = "woff2"
    dest.parent.mkdir(parents=True, exist_ok=True)
    f.save(str(dest))
    return dest.stat().st_size


def ranges(points) -> list[str]:
    """Sorted codepoints as compact "start-end" strings."""
    out: list[str] = []
    run = None
    for cp in sorted(points):
        if run and cp == run[1] + 1:
            run[1] = cp
            continue
        if run:
            out.append("%04X" % run[0] if run[0] == run[1]
                       else "%04X-%04X" % (run[0], run[1]))
        run = [cp, cp]
    if run:
        out.append("%04X" % run[0] if run[0] == run[1]
                   else "%04X-%04X" % (run[0], run[1]))
    return out


def write_manifest() -> dict:
    """Record every shipped face's coverage, so the gate needs no fontTools."""
    import hashlib
    import json
    from fontTools.ttLib import TTFont

    faces: dict[str, list[str]] = {}
    digests: dict[str, str] = {}
    for path in sorted(FONT_DIR.glob("*.woff2")):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        digests[path.name] = digest
        try:
            faces[path.name] = ranges(TTFont(str(path)).getBestCmap())
        except Exception:  # a face fontTools cannot read is not this gate's problem
            continue
    data = {
        "subset": "add-symbols.woff2",
        "subset_sha256": digests["add-symbols.woff2"],
        "faces": faces,
        "sha256": digests,
    }
    MANIFEST.write_text(json.dumps(data, indent=1, sort_keys=True) + "\n",
                        encoding="utf-8")
    return data


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true",
                    help="report the current size without rewriting the file")
    args = ap.parse_args()

    if args.check:
        if not OUT.is_file():
            print("symbols: FAIL - %s is missing" % OUT.relative_to(ROOT))
            return 1
        import hashlib
        import json
        want = hashlib.sha256(OUT.read_bytes()).hexdigest()
        try:
            have = json.loads(MANIFEST.read_text(encoding="utf-8"))["subset_sha256"]
        except (OSError, ValueError, KeyError):
            print("symbols: FAIL - %s is missing or unreadable; run without --check"
                  % MANIFEST.relative_to(ROOT))
            return 1
        if want != have:
            print("symbols: FAIL - %s is stale for the subset on disk"
                  % MANIFEST.relative_to(ROOT))
            print("  fix: rerun tools/build_symbols.py so the gate sees the new face.")
            return 1
        print("symbols: %d bytes, %d marks" % (OUT.stat().st_size, len(MARKS)))
        return 0

    size = build(OUT)
    data = write_manifest()
    print("symbols: wrote %s, %d bytes, %d marks (%s)"
          % (OUT.relative_to(ROOT), size, len(MARKS), ", ".join(
              "U+%04X" % cp for cp in sorted(MARKS))))
    print("symbols: %s covers %d faces" % (MANIFEST.relative_to(ROOT),
                                           len(data["faces"])))
    return 0


if __name__ == "__main__":
    sys.exit(main())
