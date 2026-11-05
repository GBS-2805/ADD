#!/usr/bin/env python3
"""Download the font families the site serves and record them in the catalog.

The site serves its own copies: no page asks Google for a font. This script is
the only thing that talks to fonts.googleapis.com -- it resolves each style to
its latin woff2 and to a plain TTF, writes both under docs/assets/fonts/, and
rewrites the "fonts" block of tools/catalog.json as families of styles.

The three faces the apps bundle keep their existing files and their app_res
path, so the downloadable TTF stays byte-identical to the one in the APK; every
other style is downloaded.

    python3 tools/fetch_fonts.py            # missing files only
    python3 tools/fetch_fonts.py --refresh  # re-download everything fetched here
"""

import argparse
import json
import pathlib
import re
import sys
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools/catalog.json"
FONTS = ROOT / "docs/assets/fonts"

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")

POPPINS_NAMES = {100: "Thin", 200: "ExtraLight", 300: "Light", 400: "Regular",
                 500: "Medium", 600: "SemiBold", 700: "Bold", 800: "ExtraBold",
                 900: "Black"}

# The three app faces are already on disk and must not be replaced: their TTF is
# a byte-for-byte copy of app/res/font/.
APP_FACES = {
    (400, "normal"): "app/res/font/poppins_regular.ttf",
    (500, "normal"): "app/res/font/poppins_medium.ttf",
    (600, "normal"): "app/res/font/poppins_semibold.ttf",
}

USE = {
    (400, "normal"): "Body text, ledes and every label on this site.",
    (500, "normal"): "Row labels, icon names and the tab strip.",
    (600, "normal"): "Titles and the one number that matters on a screen.",
}

# Redaction is one design in seven grades of degradation: grade 0 is the plain
# serif, 100 is fully pixelated. Each grade is its own family at Google Fonts.
# Note that 50's capital A is missing its bar in the shipped typeface itself
# (the foundry's master has the same outline); grades 35 and 70 both keep theirs.
REDACTION_GRADES = [
    (0, "Redaction", "redaction",
     "Grade 0: the plain serif every degraded grade is cut from."),
    (10, "Redaction 10", "redaction-10",
     "Grade 10: the serifs begin to square off."),
    (20, "Redaction 20", "redaction-20",
     "Grade 20: light degradation, still readable in long lines."),
    (35, "Redaction 35", "redaction-35",
     "Grade 35: mid degradation; the capitals keep their bars."),
    (50, "Redaction 50", "redaction-50",
     "Grade 50: heavy degradation. The capital A loses its bar in this grade; "
     "35 and 70 both keep theirs."),
    (70, "Redaction 70", "redaction-70",
     "Grade 70: strokes collapse into coarse blocks."),
    (100, "Redaction 100", "redaction-100",
     "Grade 100: fully pixelated, the coarsest grade."),
]


def fetch(url, binary=True, ua=UA):
    headers = {"User-Agent": ua} if ua else {}
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=45) as response:
        data = response.read()
    return data if binary else data.decode("utf-8")


def css_url(family, weight, style, want):
    axis = "ital,wght@%d,%d" % (1 if style == "italic" else 0, weight)
    query = urllib.parse.urlencode({"family": family + ":" + axis, "display": "swap"})
    css = fetch("https://fonts.googleapis.com/css2?" + query, binary=False)
    blocks = re.findall(r"@font-face\s*\{(.*?)\}", css, re.S)
    chosen = None
    for block in blocks:
        if want not in block:
            continue
        if "unicode-range" in block and "U+0000-00FF" not in block:
            continue
        match = re.search(r"url\((https://[^)]+)\)", block)
        if match:
            chosen = match.group(1)
            if "unicode-range" not in block:
                break          # the TTF response has one block per style
    if not chosen:
        raise SystemExit("fetch_fonts: no %s URL for %s %s %s" % (want, family, weight, style))
    return chosen


def check_magic(path, kind):
    head = path.read_bytes()[:4]
    if kind == "woff2" and head != b"wOF2":
        raise SystemExit("fetch_fonts: %s is not a woff2" % path.name)
    if kind == "ttf" and head not in (b"\x00\x01\x00\x00", b"true", b"ttcf"):
        raise SystemExit("fetch_fonts: %s is not a TTF" % path.name)


def download(family, slug, weight, style, refresh):
    tag = "%d" % weight + ("-italic" if style == "italic" else "")
    base = "%s-%s" % (slug, tag)
    paths = {
        "woff2": FONTS / (base + ".woff2"),
        "ttf": FONTS / (base + ".ttf"),
    }
    for kind in paths:
        path = paths[kind]
        if path.exists() and not refresh:
            check_magic(path, kind)
            continue
        if kind == "woff2":
            url = css_url(family, weight, style, "format('woff2')")
        else:
            # Without a browser UA Google answers with a plain, unsubset TTF.
            axis = "ital,wght@%d,%d" % (1 if style == "italic" else 0, weight)
            query = urllib.parse.urlencode({"family": family + ":" + axis})
            css = fetch("https://fonts.googleapis.com/css2?" + query, binary=False, ua=None)
            match = re.search(r"url\((https://[^)]+\.ttf)\)", css)
            if not match:
                raise SystemExit("fetch_fonts: no TTF for %s %s %s" % (family, weight, style))
            url = match.group(1)
        data = fetch(url)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        check_magic(path, kind)
        print("  fetched %-34s %7d bytes" % (path.name, path.stat().st_size))
    return {kind: "/assets/fonts/" + path.name for kind, path in paths.items()}


def build_families(refresh):
    families = []
    poppins = {"family": "Poppins", "slug": "poppins", "kind": "Sans serif",
               "fallback": "sans-serif",
               "licence": "SIL Open Font License 1.1", "source": "Google Fonts",
               "note": "The three faces the app bundles are byte-for-byte copies of app/res/font/.",
               "styles": []}
    for weight in sorted(POPPINS_NAMES):
        for style in ("normal", "italic"):
            files = download("Poppins", "poppins", weight, style, refresh)
            entry = {
                "name": POPPINS_NAMES[weight] + (" Italic" if style == "italic" else ""),
                "weight": weight, "style": style,
                "woff2": files["woff2"], "ttf": files["ttf"],
            }
            app = APP_FACES.get((weight, style))
            if app:
                entry["app_res"] = app
                entry["use"] = USE[(weight, style)]
            if style == "normal" and weight in USE:
                entry["use"] = USE[(weight, style)]
            poppins["styles"].append(entry)
    families.append(poppins)

    # All seven grades, not just the one the site used to show: the grades are
    # seven cuts of one design, each a separate family at Google Fonts, so the
    # page can put them side by side and a reader can hear the difference.
    for grade, name, slug, note in REDACTION_GRADES:
        redaction = {"family": name, "slug": slug, "kind": "Display serif",
                     "fallback": "serif",
                     "licence": "SIL Open Font License 1.1", "source": "Google Fonts",
                     "note": note, "styles": []}
        names = {(400, "normal"): "Regular", (400, "italic"): "Italic", (700, "normal"): "Bold"}
        for (weight, style), style_name in sorted(names.items()):
            files = download(name, slug, weight, style, refresh)
            redaction["styles"].append({
                "name": style_name, "weight": weight, "style": style,
                "woff2": files["woff2"], "ttf": files["ttf"],
            })
        families.append(redaction)
    return families


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true", help="re-download fetched files")
    args = parser.parse_args()

    families = build_families(args.refresh)
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    catalog["fonts"] = families
    CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    styles = sum(len(f["styles"]) for f in families)
    print("catalog: %d families, %d styles" % (len(families), styles))
    for family in families:
        woff2 = sum((FONTS / s["woff2"].split("/")[-1]).stat().st_size for s in family["styles"])
        ttf = sum((FONTS / s["ttf"].split("/")[-1]).stat().st_size for s in family["styles"])
        print("  %-12s %2d styles  woff2 %7d  ttf %8d"
              % (family["family"], len(family["styles"]), woff2, ttf))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
