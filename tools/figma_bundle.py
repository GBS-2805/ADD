#!/usr/bin/env python3
"""Stack the exported artboards onto one SVG canvas.

    python3 tools/figma_bundle.py            # figma/*.svg -> figma/site-figma.svg

Eighteen files are awkward to look at; one canvas opens straight in a browser or
the desktop app's file panel (a zip cannot be previewed) and a single drag puts
the whole shelf into Figma. Desktop artboards go down the left column, phone
artboards down the right, each labelled with its file name above it.
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

GAP = 220       # vertical gutter between artboards
TOP = 170       # room for the first label
PHONE_X = 1520  # left edge of the phone column (1300 desktop width + gap)
OUT = "site-figma.svg"


def tag(el):
    return el.tag.split("}")[-1]


def artboard(path):
    """The artboard's size and its layers, without its own <svg> wrapper."""
    root = ET.parse(path).getroot()
    kids = [c for c in root if tag(c) != "title"]
    if len(kids) == 1 and tag(kids[0]) in ("g", "svg"):
        node = kids[0]
        body = (node.text or "") + "".join(ET.tostring(c, encoding="unicode") for c in node)
    else:
        body = "".join(ET.tostring(c, encoding="unicode") for c in kids)
    return float(root.get("width")), float(root.get("height")), body


def main(src="figma"):
    files = sorted(
        f for f in glob.glob(os.path.join(src, "*.svg")) if os.path.basename(f) != OUT
    )
    if not files:
        sys.exit(f"no artboards in {src}/ - run tools/figma_export.js first")

    parts, widest, y_desk, y_phone = [], 0.0, TOP, TOP
    for f in files:
        w, h, body = artboard(f)
        phone = w < 700
        x = PHONE_X if phone else 0
        if phone:
            y, y_phone = y_phone, y_phone + h + GAP
        else:
            y, y_desk = y_desk, y_desk + h + GAP
        name = os.path.basename(f)[: -len(".svg")]
        parts.append(
            f'<g transform="translate({x:g} {y:g})">'
            f'<text x="0" y="-46" font-family="Poppins" font-size="26" font-weight="500" '
            f'fill="#8f96a8">{name}</text>{body}</g>'
        )
        widest = max(widest, x + w)

    tall = int(max(y_desk, y_phone) - GAP + TOP)
    out = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{widest:g}" height="{tall}" '
        f'viewBox="0 0 {widest:g} {tall}">\n' + "\n".join(parts) + "\n</svg>\n"
    )
    # Every artboard carries the same Poppins faces; the canvas needs one copy.
    seen = set()

    def once(match):
        block = match.group(0)
        if block in seen:
            return ""
        seen.add(block)
        return block

    out = re.sub(r"<defs><style[^>]*><\\?/?[^>]*>?.*?</style></defs>", lambda m: m.group(0), out)
    out = re.sub(r"<defs[^>]*><style[^>]*>.*?</style></defs>", once, out, flags=re.S)

    path = os.path.join(src, OUT)
    with open(path, "w") as fh:
        fh.write(out)
    return f"{path}  {widest:g}x{tall}  {len(out) // 1024} KB  {len(parts)} artboards"


if __name__ == "__main__":
    print(main(sys.argv[1] if len(sys.argv) > 1 else "figma"))
