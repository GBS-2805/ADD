#!/usr/bin/env python3
"""One edition, every surface: the site's Review tokens, the app, Studio and engine.

Fails when a colour stops following the site's edition:

  * a site surface that must take --page, --band or --tint carries a literal
    colour, or names a custom property nothing defines;
  * app/res/values/colors.xml has drifted from the newsprint block in
    docs/assets/site.css;
  * the one theme misses an attribute, or the band changes ink with the page;
  * the mark on the band does not wear the band's own ink;
  * a layout paints a colour directly instead of asking the theme, or uses a
    mono glyph without tinting it;
  * words on a band, page or accent fall below 4.5:1, or dim words below 3:1.

    python3 tools/check_palette.py                      # everything but the browser
    python3 tools/check_palette.py --browser http://127.0.0.1:8799/
                                                        # plus the rendered pages

The rendered half is tools/check_palette.js: it needs Playwright and a server over
docs/, and it is what proves a pull-down takes the palette and a disabled control
keeps its dim ink. It checks the active edition, not the Android theme.
"""

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import sync_edition  # noqa: E402

CSS = ROOT / "docs/assets/site.css"
COLORS = ROOT / "app/res/values/colors.xml"
ATTRS = ROOT / "app/res/values/attrs.xml"
STYLES = ROOT / "app/res/values/styles.xml"
LAYOUTS = sorted((ROOT / "app/res/layout").glob("*.xml"))
SURFACES = sorted((ROOT / "app/res/drawable").glob("bg_*.xml"))

# Every page that carries its own <style>: the palette has to reach all of them.
STYLED_PAGES = [
    ROOT / "tools/icons_brand_template.html",
    ROOT / "tools/icons_hub_template.html",
    ROOT / "tools/icons_all_template.html",
    ROOT / "tools/fonts_template.html",
    ROOT / "tools/search_template.html",
    ROOT / "docs/fonts/index.html",
]
STYLED_PAGES = [page for page in STYLED_PAGES if page.exists()]

THEME = "AppTheme"

# The colours a theme has to resolve; warn/warnBg/shadow are functional, not palette.
THEMED = ["page", "on", "onDim", "rule", "card", "panel2", "band", "bandInk",
          "bandDim", "bandLine", "accent", "accentInk", "label", "ink"]

# Set by a page's own markup or script, never by a stylesheet.
JS_VARS = {"--glyph", "--sw", "--c", "--size", "--w", "--darken", "--str"}

# xml attribute in colors.xml -> custom property in the newsprint block
SITE_OF_APP = {
    "band": "--band",
    "band_ink": "--band-ink",
    "band_dim": "--band-dim",
    "band_line": "--band-line",
    "accent": "--accent",
    "accent_ink": "--accent-ink",
    "label": "--label",
    "page": "--page",
    "on": "--on",
    "on_dim": "--on-dim",
    "rule": "--rule",
}

# Documented exceptions: a scrim is a scrim, and the launcher disc is a fixed asset.
LAYOUT_LITERALS_OK = {"#99000000"}
SURFACE_LITERALS_OK = {"bg_primary.xml": {"#33000000"}, "bg_remove.xml": {"#33000000"}}
GLYPHS_UNTINTED_OK = {"ic_launcher_foreground"}

failures = []
notes = []


def fail(group, message):
    failures.append("%s: %s" % (group, message))


def read(path):
    return path.read_text(encoding="utf-8")


def newsprint_css():
    """The final Review edition token block, shared with the generator."""
    return sync_edition.site_tokens()


def colours_xml():
    return {name: value.strip().upper()
            for name, value in re.findall(r'<color name="([\w]+)"\s*>([^<]+)</color>', read(COLORS))}


def themes_xml():
    themes = {}
    for name, body in re.findall(r'<style name="([\w.]+)"[^>]*>(.*?)</style>', read(STYLES), re.S):
        items = {}
        for key, value in re.findall(r'<item name="([\w:.]+)"\s*>([^<]+)</item>', body):
            items[key] = value.strip()
        themes[name] = items
    return themes


def attrs_xml():
    return re.findall(r'<attr name="(\w+)"', read(ATTRS))


# ---------- colour maths ----------

def parse_colour(value):
    """#AARRGGBB (Android), #RRGGBB or rgb()/rgba() -> (r, g, b, alpha)."""
    value = value.strip()
    if value.startswith("#"):
        digits = value[1:]
        if len(digits) == 8:
            return (int(digits[2:4], 16), int(digits[4:6], 16), int(digits[6:8], 16),
                    int(digits[0:2], 16) / 255.0)
        if len(digits) == 6:
            return (int(digits[0:2], 16), int(digits[2:4], 16), int(digits[4:6], 16), 1.0)
        return None
    match = re.match(r"rgba?\(([^)]+)\)", value)
    if not match:
        return None
    parts = [p.strip() for p in match.group(1).split(",")]
    if len(parts) < 3:
        return None
    alpha = float(parts[3]) if len(parts) > 3 else 1.0
    return (int(float(parts[0])), int(float(parts[1])), int(float(parts[2])), alpha)


def over(fg, bg):
    """fg composited on bg."""
    a = fg[3]
    return tuple(round(fg[i] * a + bg[i] * (1 - a)) for i in range(3)) + (1.0,)


def luminance(colour):
    def channel(value):
        value /= 255
        return value / 12.92 if value <= 0.03928 else ((value + 0.055) / 1.055) ** 2.4
    return 0.2126 * channel(colour[0]) + 0.7152 * channel(colour[1]) + 0.0722 * channel(colour[2])


def contrast(fg, bg):
    front, back = parse_colour(fg), parse_colour(bg)
    if not front or not back:
        return None
    a, b = luminance(over(front, back)) if front[3] < 1 else luminance(front), luminance(back)
    high, low = max(a, b), min(a, b)
    return round((high + 0.05) / (low + 0.05), 2)


# ---------- one palette reaches every surface ----------

def check_newsprint_tokens():
    css = newsprint_css()
    for key in ("--band", "--band-ink", "--page", "--on", "--on-dim",
                "--accent", "--accent-ink", "--label"):
        if key not in css:
            fail("site.css", "newsprint block is missing %s" % key)


def check_app_matches_site():
    css, app = newsprint_css(), colours_xml()
    for suffix, site_key in SITE_OF_APP.items():
        site = css.get(site_key)
        resource = "newsprint_%s" % suffix
        if resource not in app:
            fail("colors.xml", "%s is missing (%s)" % (resource, site_key))
            continue
        if site and parse_colour(site) and parse_colour(app[resource]):
            if parse_colour(site)[:3] != parse_colour(app[resource])[:3]:
                fail("colors.xml", "%s is %s but site.css %s is %s"
                     % (resource, app[resource], site_key, site))


def check_generated_edition():
    """The Android, Studio and engine copies must be the exact output for this site."""
    for path, expected in sync_edition.generated().items():
        if read(path) != expected:
            fail("generated", "%s is stale; run python3 tools/sync_edition.py"
                 % path.relative_to(ROOT))


def check_theme():
    themes, attrs, app = themes_xml(), attrs_xml(), colours_xml()
    palette_attrs = [a for a in attrs if a not in ("warn", "warnBg", "shadow")]
    if THEME not in themes:
        fail("styles.xml", "%s is missing" % THEME)
        return
    theme = themes[THEME]
    for attr in palette_attrs:
        if attr not in theme:
            fail("styles.xml", "%s does not set %s" % (THEME, attr))
    for attr, value in theme.items():
        if value.startswith("@color/"):
            resource = value.split("/", 1)[1]
            if resource not in app:
                fail("styles.xml", "%s points at %s, which colors.xml lacks" % (THEME, resource))
    if "ink" in theme and "bandInk" in theme and theme["ink"] != theme["bandInk"]:
        fail("styles.xml", "%s tints the mark %s on a band inked %s"
             % (THEME, theme["ink"], theme["bandInk"]))


def check_contrast():
    themes, app = themes_xml(), colours_xml()

    def value(theme, attr):
        raw = theme.get(attr, "")
        return app.get(raw.split("/", 1)[1]) if raw.startswith("@color/") else raw

    theme = themes.get(THEME)
    if not theme:
        return
    pairs = [
        ("bandInk", "band", 4.5, "words on the band"),
        ("on", "page", 4.5, "words on the page"),
        ("accentInk", "accent", 4.5, "words on the accent"),
        ("onDim", "page", 3.0, "dim words on the page"),
        ("label", "page", 3.0, "the side-panel labels"),
    ]
    for fg, bg, floor, what in pairs:
        ratio = contrast(value(theme, fg), value(theme, bg))
        if ratio is None:
            fail("contrast", "%s could not be resolved" % what)
        elif ratio < floor:
            fail("contrast", "%s measures %.2f:1, under %.1f:1" % (what, ratio, floor))
        elif what == "words on the band":
            notes.append("contrast: band words %.2f:1" % ratio)


def check_custom_properties():
    """Every var(--x) a stylesheet uses has a definition somewhere it can see."""
    sources = [CSS] + STYLED_PAGES
    defined, used = set(), {}
    for path in sources:
        text = read(path)
        defined |= set(re.findall(r"(--[\w-]+)\s*:", text))
        for name in re.findall(r"var\(\s*(--[\w-]+)", text):
            used.setdefault(name, set()).add(path.name)
    for name in sorted(used):
        if name in defined or name in JS_VARS:
            continue
        fail("css", "%s is used in %s but never defined"
             % (name, ", ".join(sorted(used[name]))))


def check_layouts():
    for path in LAYOUTS:
        text = read(path)
        for literal in re.findall(r'android:[\w]*(?:Color|background|tint)="(#[0-9a-fA-F]{6,8})"', text):
            if literal.upper() not in {value.upper() for value in LAYOUT_LITERALS_OK}:
                fail("layout", "%s paints %s directly instead of ?attr/" % (path.name, literal))
        for resource in re.findall(r'"@color/(\w+)"', text):
            fail("layout", "%s reaches for @color/%s instead of ?attr/" % (path.name, resource))
        for glyph in sorted(set(re.findall(r'"@drawable/(ic_[\w]+)"', text))):
            if glyph in GLYPHS_UNTINTED_OK:
                continue
            if not re.search(r'@drawable/%s"[^>]*tint=' % re.escape(glyph), text, re.S):
                fail("layout", "%s draws @drawable/%s without a tint, so it keeps a fixed colour"
                     % (path.name, glyph))


def check_drawable_surfaces():
    for path in SURFACES:
        text = read(path)
        allowed = SURFACE_LITERALS_OK.get(path.name, set())
        for literal in re.findall(r'(#[0-9a-fA-F]{6,8})', text):
            if literal.upper() not in {value.upper() for value in allowed}:
                fail("drawable", "%s paints %s on a themed surface" % (path.name, literal))
        for resource in re.findall(r'"@color/(\w+)"', text):
            fail("drawable", "%s reaches for @color/%s instead of ?attr/" % (path.name, resource))


# The browser sits outside node's own lookup path on a bare box: the module and the
# browsers are installed under /tmp by tools/render_env.py, and their libs are unpacked
# rootlessly beside them. A value already exported wins, the way build.sh does it.
BROWSER_ENV = {
    "NODE_PATH": os.environ.get("NODE_PATH", str(ROOT / "node_modules")),
    "PLAYWRIGHT_BROWSERS_PATH": os.environ.get("PLAYWRIGHT_BROWSERS_PATH", "/tmp/pw"),
    "LD_LIBRARY_PATH": os.environ.get(
        "LD_LIBRARY_PATH",
        "/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu"),
    "FONTCONFIG_FILE": os.environ.get("FONTCONFIG_FILE", "/tmp/rootfs/fonts.conf"),
}


def check_browser(base):
    script = ROOT / "tools/check_palette.js"
    print("== the rendered pages ==")
    result = subprocess.run(["node", str(script), base], cwd=str(ROOT),
                            env=dict(os.environ, **BROWSER_ENV))
    if result.returncode != 0:
        failures.append("browser: tools/check_palette.js reported a failure")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--browser", metavar="BASE",
                        help="also drive the pages (needs Playwright and a server over docs/)")
    args = parser.parse_args()

    check_newsprint_tokens()
    check_app_matches_site()
    check_generated_edition()
    check_theme()
    check_contrast()
    check_custom_properties()
    check_layouts()
    check_drawable_surfaces()

    if args.browser:
        if not args.browser.endswith("/"):
            parser.error("the base URL must end in a slash")
        check_browser(args.browser)
    else:
        notes.append("browser half skipped: pass --browser http://127.0.0.1:8799/ "
                     "to check the pull-downs and disabled states as rendered")

    for note in notes:
        print("  " + note)

    if failures:
        print("palette: FAILED")
        for problem in failures:
            print("  " + problem)
        sys.exit(1)
    print("palette: OK - one newsprint edition, app and tokens agree")


if __name__ == "__main__":
    main()
