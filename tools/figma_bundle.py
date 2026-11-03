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

# Written without a prefix: ElementTree otherwise emits <ns0:defs> for every
# element it re-serialises, which bloats the canvas and hides the font blocks.
ET.register_namespace("", "http://www.w3.org/2000/svg")

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
    # The blocks are matched by what they define, not by their wrapper, because
    # ElementTree re-serialises <defs> with its own namespace attribute.
    kept = [0]

    def first_faces(match):
        block = match.group(0)
        if "@font-face" not in block or "font/woff2" not in block:
            return block
        kept[0] += 1
        return block if kept[0] == 1 else ""

    out = re.sub(r"<style[^>]*>.*?</style>", first_faces, out, flags=re.S)

    # Each artboard embeds its own copy of the mark and of every icon sheet, so the
    # canvas carried the same artwork hundreds of times over. Keep one copy of each
    # in <defs>, at the origin and its natural size, and paint every instance with
    # <use> -- the reference carries no x/y of its own, so the instance's own offset
    # and scale are the only placement it gets.
    image = re.compile(r"<image\b([^>]*?)/>", re.S)
    attr = re.compile(r'([\w:-]+)="([^"]*)"')
    artwork = {}

    def intern(match):
        attrs = dict(attr.findall(match.group(1)))
        key = "xlink:href" if "xlink:href" in attrs else ("href" if "href" in attrs else None)
        if not key or not attrs[key].startswith("data:image"):
            return match.group(0)
        payload = attrs[key]
        if payload not in artwork:
            artwork[payload] = {
                "name": "artwork%d" % (len(artwork) + 1),
                "w": float(attrs.get("width", 0)) or 1.0,
                "h": float(attrs.get("height", 0)) or 1.0,
                "key": key,
            }
        art = artwork[payload]
        x, y = float(attrs.get("x", 0)), float(attrs.get("y", 0))
        sx = (float(attrs.get("width", art["w"])) or art["w"]) / art["w"]
        sy = (float(attrs.get("height", art["h"])) or art["h"]) / art["h"]
        move = "translate(%g %g)" % (x, y)
        if (sx, sy) != (1.0, 1.0):
            move += " scale(%g %g)" % (sx, sy)
        # Anything that paints the instance -- opacity, clip-path, id -- stays on the use.
        # The exporter writes the payload under both href and xlink:href; carrying
        # either onto the <use> would store the image all over again.
        extra = "".join(
            ' %s="%s"' % (k, v)
            for k, v in attrs.items()
            if k not in ("x", "y", "width", "height", "preserveAspectRatio")
            and not k.endswith("href")
        )
        return '<use %s="#%s"%s transform="%s"/>' % (key, art["name"], extra, move)

    out = image.sub(intern, out)

    if artwork:
        key = next(iter(artwork.values()))["key"]
        if key.startswith("xlink:") and "xmlns:xlink" not in out[:600]:
            out = out.replace(
                "<svg ", '<svg xmlns:xlink="http://www.w3.org/1999/xlink" ', 1)
        defs = "<defs>" + "".join(
            '<image id="%s" x="0" y="0" width="%g" height="%g" %s="%s"/>'
            % (art["name"], art["w"], art["h"], art["key"], payload)
            for payload, art in artwork.items()
        ) + "</defs>"
        close = out.index(">") + 1
        out = out[:close] + defs + out[close:]

    path = os.path.join(src, OUT)
    with open(path, "w") as fh:
        fh.write(out)
    return (
        f"{path}  {widest:g}x{tall}  {len(out) // 1024} KB  {len(parts)} artboards  "
        f"{len(artwork)} unique artwork payloads"
    )


if __name__ == "__main__":
    print(main(sys.argv[1] if len(sys.argv) > 1 else "figma"))
