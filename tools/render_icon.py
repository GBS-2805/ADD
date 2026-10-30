#!/usr/bin/env python3
"""
Renders the adaptive launcher icon to a PNG from the real vector.

There is no device here, so this reads app/res/drawable/ic_launcher_foreground.xml
and the background colour from app/res/values/colors.xml, then draws the same
geometry with Pillow. Only straight M/L path segments are supported, which is all
the icon uses. It is a render of those files, not a device capture.

    python3 tools/render_icon.py     # writes artifacts/icon-*.png
"""

import os
import re

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "artifacts")
VECTOR = os.path.join(ROOT, "app", "res", "drawable", "ic_launcher_foreground.xml")
COLORS = os.path.join(ROOT, "app", "res", "values", "colors.xml")

SIZE = 512
VIEWPORT = 108.0


def color(name):
    xml = open(COLORS, encoding="utf-8").read()
    m = re.search(r'<color name="%s">(#\w+)</color>' % name, xml)
    if not m:
        raise SystemExit("missing colour: %s" % name)
    return hex_rgb(m.group(1))


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def vector_paths(path):
    xml = open(path, encoding="utf-8").read()
    paths = []
    for tag in re.findall(r"<path\b[^>]*>", xml, re.S):
        data = re.search(r'android:pathData="([^"]+)"', tag)
        stroke = re.search(r'android:strokeColor="([^"]+)"', tag)
        width = re.search(r'android:strokeWidth="([\d.]+)"', tag)
        if data and stroke:
            paths.append({
                "data": data.group(1),
                "stroke": hex_rgb(stroke.group(1)),
                "width": float(width.group(1)) if width else 1.0,
            })
    return paths


def points(data, scale):
    """Parses absolute M/L commands into a flat point list."""
    pts = []
    for cmd, args in re.findall(r"([MLZ])\s*([-\d.,\s]*)", data):
        if cmd == "Z":
            continue
        numbers = [float(n) for n in re.findall(r"-?\d+\.?\d*", args)]
        for i in range(0, len(numbers) - 1, 2):
            pts.append((numbers[i] * scale, numbers[i + 1] * scale))
    return pts


def reference_rect(scale):
    """Bounds of the invisible (transparent, unstroked) reference rectangle."""
    xml = open(VECTOR, encoding="utf-8").read()
    for tag in re.findall(r"<path\b[^>]*>", xml, re.S):
        fill = re.search(r'android:fillColor="([^"]+)"', tag)
        data = re.search(r'android:pathData="([^"]+)"', tag)
        if not fill or not data or fill.group(1).lower() != "#00000000":
            continue
        if re.search(r'android:strokeColor="([^"]+)"', tag):
            continue
        pts = points(data.group(1), scale)
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        return (min(xs), min(ys), max(xs), max(ys))
    return None


def painted_box(scale):
    """Bounds actually painted by the stroked arrows, half-widths included."""
    boxes = []
    for path in vector_paths(VECTOR):
        pts = points(path["data"], scale)
        half = path["width"] * scale / 2.0
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        boxes.append((min(xs) - half, min(ys) - half, max(xs) + half, max(ys) + half))
    if not boxes:
        return None
    return (min(b[0] for b in boxes), min(b[1] for b in boxes),
            max(b[2] for b in boxes), max(b[3] for b in boxes))


def render():
    os.makedirs(ART, exist_ok=True)
    scale = SIZE / VIEWPORT
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    d.ellipse([0, 0, SIZE - 1, SIZE - 1], fill=color("iconBg"))

    for path in vector_paths(VECTOR):
        pts = points(path["data"], scale)
        if len(pts) < 2:
            continue
        w = max(1, int(round(path["width"] * scale)))
        d.line(pts, fill=path["stroke"], width=w, joint="curve")
        for x, y in pts:                      # round caps / joins
            r = w / 2.0
            d.ellipse([x - r, y - r, x + r, y + r], fill=path["stroke"])

    round_path = os.path.join(ART, "icon-round.png")
    img.save(round_path)

    # centring check: the reference rectangle vs what the arrows actually paint
    rect = reference_rect(scale)
    painted = painted_box(scale)
    centre = SIZE / 2.0
    if rect and painted:
        rx, ry = (rect[0] + rect[2]) / 2.0, (rect[1] + rect[3]) / 2.0
        ax, ay = (painted[0] + painted[2]) / 2.0, (painted[1] + painted[3]) / 2.0
        print("disc centre dp:     54.00, 54.00")
        print("reference centre dp: %.2f, %.2f" % (rx / scale, ry / scale))
        print("painted centre dp:   %.2f, %.2f" % (ax / scale, ay / scale))
        print("rect dp:    %.2f x %.2f" % ((rect[2] - rect[0]) / scale,
                                           (rect[3] - rect[1]) / scale))
        print("painted dp: %.2f x %.2f" % ((painted[2] - painted[0]) / scale,
                                           (painted[3] - painted[1]) / scale))
        print("rect-vs-painted offset: %.2f, %.2f dp" %
              ((rx - ax) / scale, (ry - ay) / scale))

    diag = img.copy()
    dd = ImageDraw.Draw(diag)
    if rect:
        dd.rectangle(rect, outline=(220, 30, 30, 255), width=max(1, int(scale * 1.2)))
    dd.line([centre, 0, centre, SIZE], fill=(220, 30, 30, 140), width=2)
    dd.line([0, centre, SIZE, centre], fill=(220, 30, 30, 140), width=2)
    diag_path = os.path.join(ART, "icon-centering.png")
    diag.save(diag_path)

    # small preview on the app's paper, at launcher size
    paper = color("bg")
    small = img.resize((96, 96), Image.LANCZOS)
    sheet = Image.new("RGB", (336, 160), paper)
    sd = ImageDraw.Draw(sheet)
    for i, (size, label) in enumerate(((96, "96"), (64, "64"), (48, "48"))):
        tile = img.resize((size, size), Image.LANCZOS)
        x = 24 + i * 104
        sheet.paste(tile, (x, 32), tile)
        sd.rectangle([x, 24, x + size - 1, 24 + size - 1], outline=(0, 0, 0), width=1)
    small_path = os.path.join(ART, "icon-sizes.png")
    sheet.save(small_path)
    return round_path, diag_path, small_path


if __name__ == "__main__":
    for p in render():
        print("wrote", os.path.relpath(p, ROOT))
