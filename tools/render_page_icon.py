#!/usr/bin/env python3
"""Verify and render the icon markup used on the download page.

Reads the inline favicon SVG from docs/index.html, checks the chevron
geometry (painted centre, widths, gaps) against the app's launcher icon,
then rasterises it at real favicon / home-screen sizes.

Rendering uses cairosvg when it is installed, otherwise a small Pillow
polyline fallback that only needs the numbers from the SVG.
"""

import os
import re
import urllib.parse

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ART = os.path.join(ROOT, "artifacts")
PAGE = os.path.join(ROOT, "docs", "index.html")
AMBER = (255, 178, 15, 255)
INK = (43, 41, 38, 255)          # #2B2926
PAPER = (11, 15, 20, 255)        # the page background


def load_svg():
    html = open(PAGE, encoding="utf-8").read()
    href = re.search(r'<link rel="icon" href="([^"]+)"', html).group(1)
    return urllib.parse.unquote(href.split(",", 1)[1]), html


def parse(svg):
    disc = re.search(r"<circle cx='([\d.]+)' cy='([\d.]+)' r='([\d.]+)'", svg)
    arrows = [(d, float(w)) for d, w in
              re.findall(r"<path d='([^']+)' stroke-width='([\d.]+)'", svg)]
    return disc, arrows


def points(d):
    return [(float(a), float(b)) for a, b in re.findall(r"([\d.]+)\s+([\d.]+)", d)]


def check(disc, arrows):
    """Centre / size / gap audit of the painted mark."""
    boxes = []
    for d, w in arrows:
        pts = points(d)
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        boxes.append((min(xs) - w / 2.0, min(ys) - w / 2.0,
                      max(xs) + w / 2.0, max(ys) + w / 2.0))
    left = min(b[0] for b in boxes)
    top = min(b[1] for b in boxes)
    right = max(b[2] for b in boxes)
    bottom = max(b[3] for b in boxes)
    cx, cy = float(disc.group(1)), float(disc.group(2))
    print("disc:      centre %.2f, %.2f  r %s" % (cx, cy, disc.group(3)))
    print("mark:      centre %.2f, %.2f  (%.2f x %.2f dp)"
          % ((left + right) / 2.0, (top + bottom) / 2.0, right - left, bottom - top))
    print("offset:    %.2f, %.2f dp from disc centre"
          % ((left + right) / 2.0 - cx, (top + bottom) / 2.0 - cy))
    print("widths:    " + ", ".join("%.2f" % (b[2] - b[0]) for b in boxes))
    print("gaps:      " + ", ".join("%.2f" % (boxes[i + 1][1] - boxes[i][3])
                                    for i in range(len(boxes) - 1)))


def fallback(size, disc, arrows):
    s = size / 108.0
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy, r = float(disc.group(1)), float(disc.group(2)), float(disc.group(3))
    d.ellipse([(cx - r) * s, (cy - r) * s, (cx + r) * s, (cy + r) * s], fill=AMBER)
    for path, w in arrows:
        pts = [(x * s, y * s) for x, y in points(path)]
        d.line(pts, fill=INK, width=max(1, round(w * s)), joint="curve")
        rad = w * s / 2.0
        for x, y in (pts[0], pts[-1]):
            d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=INK)
    return img


def render(size, disc, arrows, svg):
    try:
        import cairosvg
    except ImportError:
        return fallback(size, disc, arrows), "pillow"
    import io
    png = cairosvg.svg2png(bytestring=svg.encode("utf-8"),
                           output_width=size, output_height=size)
    return Image.open(io.BytesIO(png)).convert("RGBA"), "cairosvg"


def main():
    os.makedirs(ART, exist_ok=True)
    svg, html = load_svg()
    disc, arrows = parse(svg)
    print("favicon: %d chevrons" % len(arrows))
    check(disc, arrows)
    print("green leftovers in page:", "3BE08D" in html)

    sizes = [180, 72, 32]
    tiles = []
    engine = None
    for size in sizes:
        img, engine = render(size, disc, arrows, svg)
        tiles.append((size, img))

    pad, gap, label_h = 24, 26, 20
    w = pad * 2 + sum(t[1].width for t in tiles) + gap * (len(tiles) - 1)
    h = pad * 2 + max(t[1].height for t in tiles) + label_h
    sheet = Image.new("RGBA", (w, h), PAPER)
    d = ImageDraw.Draw(sheet)
    x = pad
    for size, img in tiles:
        y = pad + (max(t[1].height for t in tiles) - img.height) // 2
        sheet.alpha_composite(img, (x, y))
        d.text((x, h - pad - label_h + 2), "%d px" % size, fill=(147, 162, 178, 255))
        x += img.width + gap
    out = os.path.join(ART, "page-icon.png")
    sheet.save(out)
    print("engine:", engine)
    print("wrote", out)


if __name__ == "__main__":
    main()
