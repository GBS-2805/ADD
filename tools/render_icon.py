#!/usr/bin/env python3
"""
Renders the adaptive launcher icon from the real vector.

There is no device here, so this reads app/res/drawable/ic_launcher_foreground.xml
and the background colour from app/res/values/colors.xml, turns them back into an
SVG and rasterizes it with headless Chromium. It is a render of those files, not a
device capture.

The vector is filled artwork with cubic curves now (the band mark), which is why
this no longer draws with Pillow: hand-flattening the curves would put a drawing
next to the file it claims to show. Chromium paints the same pathData Android
paints.

    python3 tools/render_icon.py              # artifacts/icon-round.png, icon-sizes.png
    python3 tools/render_icon.py --name icon-green --disc 0fbf92 --ink f2eee3

--disc and --ink override the disc and the glyph for a side-by-side look at a
variant; nothing on disk changes. Needs the Chromium from tools/render_env.py
(PLAYWRIGHT_BROWSERS_PATH, LD_LIBRARY_PATH, FONTCONFIG_FILE exported).
"""

import argparse
import glob
import os
import re
import subprocess
import sys

from PIL import Image, ImageChops, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "artifacts")
VECTOR = os.path.join(ROOT, "app", "res", "drawable", "ic_launcher_foreground.xml")
COLORS = os.path.join(ROOT, "app", "res", "values", "colors.xml")

SIZE = 512
SAFE_CIRCLE = 66.0          # dp the adaptive mask guarantees to keep


def color(name):
    xml = open(COLORS, encoding="utf-8").read()
    m = re.search(r'<color name="%s">#(\w+)</color>' % name, xml)
    if not m:
        raise SystemExit("missing colour: %s" % name)
    return m.group(1).lower()


def drawable():
    """Reads the viewport, the group transform and every filled path."""
    xml = open(VECTOR, encoding="utf-8").read()
    vw = float(re.search(r'android:viewportWidth="([\d.]+)"', xml).group(1))
    vh = float(re.search(r'android:viewportHeight="([\d.]+)"', xml).group(1))
    group = re.search(r"<group\b([^>]*)>", xml, re.S)
    scale = translate = (1.0, 1.0)
    if group:
        attrs = group.group(1)

        def get(name, default):
            m = re.search(r'android:%s="([-\d.]+)"' % name, attrs)
            return float(m.group(1)) if m else default

        scale = (get("scaleX", 1.0), get("scaleY", 1.0))
        translate = (get("translateX", 0.0), get("translateY", 0.0))
    paths = []
    for tag in re.findall(r"<path\b[^>]*>", xml, re.S):
        data = re.search(r'android:pathData="([^"]+)"', tag)
        fill = re.search(r'android:fillColor="([^"]+)"', tag)
        if data and fill and fill.group(1).lower() not in ("#00000000", "@android:color/transparent"):
            paths.append((data.group(1), fill.group(1)[-6:].lower()))
    if not paths:
        raise SystemExit("no filled paths in %s" % os.path.relpath(VECTOR, ROOT))
    return {"vw": vw, "vh": vh, "scale": scale, "translate": translate, "paths": paths}


def svg(doc, disc, ink, px):
    tx, ty = doc["translate"]
    sx, sy = doc["scale"]
    body = []
    for data, fill in doc["paths"]:
        body.append('<path d="%s" fill="#%s"/>' % (data, ink or fill))
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
        'viewBox="0 0 %g %g">'
        '<rect width="%g" height="%g" fill="#%s"/>'
        '<g transform="translate(%g %g) scale(%g %g)">%s</g>'
        "</svg>"
    ) % (
        px, px, doc["vw"], doc["vh"],
        doc["vw"], doc["vh"], disc,
        tx, ty, sx, sy, "".join(body),
    )


def chromium():
    if os.environ.get("CHROME"):
        return os.environ["CHROME"]
    patterns = [
        # the headless shell first: it carries far fewer library deps than full
        # chrome, which is why it is the one that runs on a bare VM
        "/tmp/pw/chromium_headless_shell-*/chrome-headless-shell-linux64/chrome-headless-shell",
        os.path.expanduser("~/.cache/ms-playwright/chromium_headless_shell-*/chrome-headless-shell-linux64/chrome-headless-shell"),
        "/tmp/pw/chromium-*/chrome-linux64/chrome",
        "/tmp/pw/chromium-*/chrome-linux/chrome",
    ]
    for pattern in patterns:
        found = sorted(glob.glob(pattern))
        if found:
            return found[-1]
    raise SystemExit("no Chromium found - run python3 tools/render_env.py first")


def shoot(svg_path, out, px):
    binary = chromium()
    flags = [binary, "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
             "--force-device-scale-factor=1", "--window-size=%d,%d" % (px, px),
             "--screenshot=%s" % out, "file://" + svg_path]
    if "headless-shell" not in os.path.basename(binary):
        flags.insert(1, "--headless=new")
    subprocess.run(
        flags,
        check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--name", default="icon", help="output prefix under artifacts/")
    ap.add_argument("--disc", help="disc colour, no # (default: iconBg)")
    ap.add_argument("--ink", help="glyph colour, no # (default: the drawable's own)")
    args = ap.parse_args()

    os.makedirs(ART, exist_ok=True)
    doc = drawable()
    disc = (args.disc or color("iconBg")).lower().lstrip("#")
    ink = (args.ink or "").lower().lstrip("#") or None

    work = os.path.join(ART, "%s.svg" % args.name)
    open(work, "w", encoding="utf-8").write(svg(doc, disc, ink, SIZE))

    round_path = os.path.join(ART, "%s-round.png" % args.name)
    shoot(work, round_path, SIZE)
    # the canvas is a full square: an adaptive background is painted edge to edge and
    # the launcher applies the mask, so a circle here would fake a shape it does not own
    icon = Image.open(round_path).convert("RGB")
    disc_rgb = tuple(int(disc[i:i + 2], 16) for i in (0, 2, 4))
    diff = ImageChops.difference(icon, Image.new("RGB", icon.size, disc_rgb))
    box = diff.convert("L").point(lambda v: 255 if v else 0).getbbox()
    scale_px = SIZE / doc["vw"]
    mark = None
    if box:
        mark = (
            (box[2] - box[0]) / scale_px,
            (box[3] - box[1]) / scale_px,
            (box[0] + box[2]) / 2.0 / scale_px,
            (box[1] + box[3]) / 2.0 / scale_px,
        )

    # launcher sizes on the app's own paper, the way it meets the eye on a home screen
    paper = tuple(int(color("green_page")[i:i + 2], 16) for i in (0, 2, 4))
    sheet = Image.new("RGB", (356, 168), paper)
    sd = ImageDraw.Draw(sheet)
    for i, size in enumerate((96, 64, 48)):
        tile = icon.resize((size, size), Image.LANCZOS)
        x = 22 + i * 108
        sheet.paste(tile, (x, 36))
        sd.rectangle([x - 1, 35, x + size, 36 + size], outline=(0, 0, 0))
        sd.text((x, 140), "%d px" % size, fill=(90, 90, 90))
    sizes_path = os.path.join(ART, "%s-sizes.png" % args.name)
    sheet.save(sizes_path)

    sx, sy = doc["scale"]
    print("disc:      #%s" % disc)
    print("glyph:     %s" % ("#%s (override)" % ink if ink else "as drawn"))
    if mark:
        w, h, cx, cy = mark
        print("mark box:  %.2f x %.2f dp, centre %.2f, %.2f (canvas centre %.1f, %.1f)"
              % (w, h, cx, cy, doc["vw"] / 2.0, doc["vh"] / 2.0))
        print("safe:      %.1f dp circle, mark %s inside"
              % (SAFE_CIRCLE, "fits" if max(w, h) <= SAFE_CIRCLE else "DOES NOT FIT"))
    else:
        print("mark box:  nothing painted")
    print("scale:     %.6f" % sx)
    for p in (round_path, sizes_path):
        print("wrote", os.path.relpath(p, ROOT))


if __name__ == "__main__":
    main()
