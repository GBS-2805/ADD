#!/usr/bin/env python3
"""Write the Pantone reference for every palette colour, as a Figma-importable sheet.

`docs/assets/site.css` is the only source: this reads the four palette blocks out of
it, matches each solid colour to the nearest Pantone chip in two directions (Solid
Coated for print, TCX for textile), and picks two further alternatives for each.

    python3 tools/pantone_sheet.py           # writes figma/pantone.svg
    python3 tools/pantone_sheet.py --check   # fails when the sheet is stale

Pantone publishes no official hex for any chip; the values in tools/pantone-data.json
are the widely published conversions of its L*a*b* data. Distance is CIEDE2000, so
0 is a perfect match and anything under about 3 cannot be told apart by eye.
"""
import json
import math
import pathlib
import re
import sys
from html import escape

CSS = pathlib.Path("docs/assets/site.css")
DATA = pathlib.Path("tools/pantone-data.json")
OUT = pathlib.Path("figma/pantone.svg")

PALETTES = ["green", "moss", "navy", "ash"]
# The order a designer reads the roles in, not the order CSS happens to declare them.
ROLE_ORDER = ["band", "band-ink", "band-hover", "accent", "accent-ink", "label",
              "page", "ink", "on", "page-light", "ink-light", "on-light"]

W, PAD, ROW = 1300, 40, 96
CREAM, INK, DIM, RULE = "#f2eee3", "#131513", "#5c6157", "#13151333"
FONT = "Poppins, Helvetica Neue, Arial, sans-serif"
COL_ROLE, COL_MATCH, COL_ALT1, COL_ALT2 = 124, 340, 640, 900
HEADINGS = {"green": "Green", "moss": "Moss", "navy": "Navy", "ash": "Ash"}


def lin(v):
    v /= 255.0
    return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4


def lab(hexv):
    h = hexv.lstrip("#")
    r, g, b = (lin(int(h[i:i + 2], 16)) for i in (0, 2, 4))
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
    f = lambda t: t ** (1 / 3) if t > 0.008856 else 7.787 * t + 16 / 116
    fx, fy, fz = f(x), f(y), f(z)
    return (116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))


def delta_e(a, b):
    """CIEDE2000 between two hex colours."""
    L1, a1, b1 = lab(a)
    L2, a2, b2 = lab(b)
    c1, c2 = math.hypot(a1, b1), math.hypot(a2, b2)
    cb = (c1 + c2) / 2
    g = 0.5 * (1 - math.sqrt(cb ** 7 / (cb ** 7 + 25 ** 7))) if cb else 0
    a1p, a2p = (1 + g) * a1, (1 + g) * a2
    c1p, c2p = math.hypot(a1p, b1), math.hypot(a2p, b2)
    h1p = math.degrees(math.atan2(b1, a1p)) % 360
    h2p = math.degrees(math.atan2(b2, a2p)) % 360
    dl, dc = L2 - L1, c2p - c1p
    if c1p * c2p == 0:
        dh = 0
    elif abs(h2p - h1p) <= 180:
        dh = h2p - h1p
    else:
        dh = h2p - h1p - 360 if h2p > h1p else h2p - h1p + 360
    dhp = 2 * math.sqrt(c1p * c2p) * math.sin(math.radians(dh / 2))
    lbp, cbp = (L1 + L2) / 2, (c1p + c2p) / 2
    if c1p * c2p == 0:
        hbp = h1p + h2p
    elif abs(h1p - h2p) <= 180:
        hbp = (h1p + h2p) / 2
    else:
        hbp = (h1p + h2p + 360) / 2 if h1p + h2p < 360 else (h1p + h2p - 360) / 2
    t = (1 - 0.17 * math.cos(math.radians(hbp - 30)) + 0.24 * math.cos(math.radians(2 * hbp))
         + 0.32 * math.cos(math.radians(3 * hbp + 6)) - 0.20 * math.cos(math.radians(4 * hbp - 63)))
    sl = 1 + 0.015 * (lbp - 50) ** 2 / math.sqrt(20 + (lbp - 50) ** 2)
    sc, sh = 1 + 0.045 * cbp, 1 + 0.015 * cbp * t
    rt = -2 * math.sqrt(cbp ** 7 / (cbp ** 7 + 25 ** 7)) * math.sin(
        math.radians(60 * math.exp(-(((hbp - 275) / 25) ** 2))))
    return math.sqrt((dl / sl) ** 2 + (dc / sc) ** 2 + (dhp / sh) ** 2
                     + rt * (dc / sc) * (dhp / sh))


def palettes():
    """Every solid colour each palette declares, with the roles it fills."""
    text = CSS.read_text()
    found = {}
    for name, body in re.findall(r'html\[data-palette="(\w+)"\]\s*\{(.*?)\n\}', text, re.S):
        roles = {}
        for role, value in re.findall(r"--pal-([\w-]+):\s*([^;]+);", body):
            value = value.strip()
            if re.fullmatch(r"#[0-9a-fA-F]{6}", value):
                roles.setdefault(value.lower(), []).append(role)
        found[name] = roles
    missing = [p for p in PALETTES if p not in found]
    if missing:
        sys.exit("no palette block for %s in %s" % (", ".join(missing), CSS))
    return [(p, found[p]) for p in PALETTES]


def order_roles(roles):
    rank = {r: i for i, r in enumerate(ROLE_ORDER)}
    return sorted(roles, key=lambda r: (rank.get(r, 99), r))


def nearest(table, hexv, taken=(), gap=3.0, count=3):
    ranked = sorted(table, key=lambda c: delta_e(hexv, c["hex"]))
    picked = []
    for chip in ranked:
        d = delta_e(hexv, chip["hex"])
        if picked and any(abs(d - p[0]) < gap or chip["hex"] == p[2] for p in picked):
            continue
        picked.append((d, chip, chip["hex"]))
        if len(picked) == count:
            break
    return picked


def pretty(code):
    """Pantone writes "Cool Gray 1 C", the data says "COOL-GRAY-1 C"."""
    if re.fullmatch(r"[\d-]+ C", code):
        return code
    name = code[:-2] if code.endswith(" C") else code
    if not re.search(r"[A-Za-z]", name):
        return code
    return " ".join(w.capitalize() for w in name.replace("-", " ").split()) + " C"


def text(x, y, s, size=14, weight=400, fill=INK, anchor="start", opacity=None):
    extra = ' text-anchor="%s"' % anchor if anchor != "start" else ""
    if opacity is not None:
        extra += ' fill-opacity="%s"' % opacity
    return ('<text x="%g" y="%g" font-family="%s" font-size="%g" font-weight="%d" fill="%s"%s>%s</text>'
            % (x, y, FONT, size, weight, fill, extra, escape(s)))


def chip(x, y, size, hexv, radius=0):
    return ('<rect x="%g" y="%g" width="%g" height="%g" rx="%g" fill="%s" '
            'stroke="#131513" stroke-opacity=".17"/>' % (x, y, size, size, radius, hexv))


def build(pantone):
    coated, tcx = pantone["coated"], pantone["tcx"]
    rows = []
    blocks = []
    y = PAD
    blocks.append(text(PAD, y + 44, "Pantone reference", 40, 400))
    blocks.append(text(PAD, y + 78, "Nearest chip for every colour the four palettes declare, and two alternatives for each.",
                       16, 400, DIM))
    blocks.append(text(PAD, y + 102, "ΔE00 is CIEDE2000 distance from the screen colour: 0 is the same colour, under 3 is indistinguishable.",
                       13, 400, DIM))
    blocks.append(text(PAD, y + 122, "Hex values are published conversions of Pantone's own L*a*b* data - check a physical chip before printing.",
                       13, 400, DIM))
    y += 168

    for name, roles in palettes():
        blocks.append(text(PAD, y + 24, HEADINGS[name], 22, 500))
        y += 38
        blocks.append('<rect x="%g" y="%g" width="%g" height="1" fill="%s"/>' % (PAD, y, W - 2 * PAD, RULE))
        labels = [(COL_ROLE, "Colour"), (COL_MATCH, "Nearest Pantone"),
                  (COL_ALT1, "Alternative"), (COL_ALT2, "Alternative")]
        for cx, label in labels:
            blocks.append(text(cx, y + 20, label.upper(), 11, 500, DIM))
        y += 30
        for hexv, role_list in roles.items():
            top = y
            done = order_roles(role_list)
            blocks.append(chip(PAD, top + 8, 52, hexv, 2))
            blocks.append('<rect x="%g" y="%g" width="52" height="52" fill="none" stroke="%s"/>' % (PAD, top + 8, RULE))
            blocks.append(text(COL_ROLE, top + 26, done[0].replace("-", " "), 15, 500))
            blocks.append(text(COL_ROLE, top + 45, hexv.upper(), 13, 400, DIM))
            rest = [r for r in done[1:]]
            if rest:
                extra = "also " + ", ".join(r.replace("-", " ") for r in rest[:3])
                if len(rest) > 3:
                    extra += " +%d" % (len(rest) - 3)
                blocks.append(text(COL_ROLE, top + 64, extra, 11.5, 400, DIM))

            picks = nearest(coated, hexv, count=3)
            textile = nearest(tcx, hexv, count=1)[0]
            for i, (cx, (dist, c, _)) in enumerate(zip((COL_MATCH, COL_ALT1, COL_ALT2), picks)):
                big = i == 0
                blocks.append(chip(cx, top + (6 if big else 10), 26 if big else 20, c["hex"], 2))
                blocks.append(text(cx + (34 if big else 28), top + (26 if big else 24),
                                   pretty(c["code"]), 15 if big else 13, 500 if big else 400))
                blocks.append(text(cx + (34 if big else 28), top + (45 if big else 40),
                                   "%s   ΔE %.1f" % (c["hex"].upper(), dist),
                                   12.5 if big else 11.5, 400, DIM))
                if big:
                    blocks.append(text(cx + 34, top + 63,
                                       "%s · %s" % (textile[1]["code"], textile[1]["name"].replace("-", " ").title()),
                                       11.5, 400, DIM, opacity=".78"))
            blocks.append('<rect x="%g" y="%g" width="%g" height="1" fill="%s" fill-opacity=".55"/>'
                          % (PAD, y + ROW - 6, W - 2 * PAD, RULE))
            y += ROW
        y += 26

    height = y + PAD - 6
    head = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">'
            '<title>Pantone reference — green, moss, navy, ash</title>'
            '<desc>Each palette colour with its nearest Pantone Solid Coated chip, two alternatives, '
            'and the nearest textile TCX chip.</desc>' % (W, height, W, height))
    return head + '<rect width="%d" height="%d" fill="%s"/>' % (W, height, CREAM) + "".join(blocks) + "</svg>\n"


def main():
    check = "--check" in sys.argv[1:]
    data = json.loads(DATA.read_text())
    svg = build(data)
    if check:
        if not OUT.exists() or OUT.read_text() != svg:
            sys.exit("figma/%s is stale - run python3 tools/pantone_sheet.py" % OUT.name)
        print("figma/%s matches the palettes" % OUT.name)
        return
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(svg)
    print("figma/%s: %d KB, %d palette colours" % (
        OUT.name, len(svg) / 1024, len(re.findall(r"html\[data-palette", CSS.read_text())) * 0
        or sum(len(r) for _, r in palettes())))


if __name__ == "__main__":
    main()
