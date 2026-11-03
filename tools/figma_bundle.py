#!/usr/bin/env python3
"""Stack the exported artboards onto one SVG canvas.

    python3 tools/figma_bundle.py            # figma/*.svg -> figma/site-figma.svg

Eighteen files are awkward to look at; one canvas opens straight in a browser or
the desktop app's file panel (a zip cannot be previewed) and a single drag puts
the whole shelf into Figma. The marks are inlined rather than interned into <defs>
and painted with <use>: that saved half the file, but Figma does not paint <use>
instances, so a shared payload arrived as a gap in every artboard. Desktop artboards go down the left column, phone
artboards down the right, each labelled with its file name above it.
"""
import glob
import os
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from check_figma import check as figma_problems  # noqa: E402 - local tool

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
    keep = ""
    if len(kids) == 1 and tag(kids[0]) in ("g", "svg"):
        node = kids[0]
        body = (node.text or "") + "".join(ET.tostring(c, encoding="unicode") for c in node)
        # The artboard carries the text defaults it earned by hoisting them off every
        # run (font family, the majority size and weight). Unwrapping the group must
        # not drop them, or the canvas would paint its text in the default fill.
        keep = "".join(' %s="%s"' % (k, v) for k, v in node.attrib.items() if k != "id")
    else:
        body = "".join(ET.tostring(c, encoding="unicode") for c in kids)
    return float(root.get("width")), float(root.get("height")), body, keep


# Pages whose export can go stale. The listing and the gallery are written by this
# toolchain or by the capture run, not by the exporter, so they are not evidence.
EXCLUDED = ("figma", "renders")
SLACK = 2.0   # a page and the bake that read it can share a timestamp


def stale_export(src="figma"):
    """Stop when the export is older than the pages it is supposed to translate.

    A failed export leaves the previous artboards in place, and the bundler and the
    publisher then republish them as if they were current -- the run looks clean and
    the hand-off silently lags behind the site by a release.
    """
    pages = [p for p in glob.glob("docs/**/index.html", recursive=True)
             if not set(pathlib.PurePath(p).parts) & set(EXCLUDED)]
    boards = glob.glob(os.path.join(src, "[0-9][0-9]-*.svg"))
    if not pages or not boards:
        return
    page = max(pages, key=os.path.getmtime)
    board = max(boards, key=os.path.getmtime)
    if os.path.getmtime(page) > os.path.getmtime(board) + SLACK:
        sys.exit("the export is older than the page it comes from:\n  %s is newer than %s\n"
                 "run tools/figma_export.js before bundling, or the hand-off ships the "
                 "previous release" % (page, board))


def main(src="figma"):
    stale_export(src)
    files = sorted(
        f for f in glob.glob(os.path.join(src, "*.svg")) if os.path.basename(f) != OUT
    )
    if not files:
        sys.exit(f"no artboards in {src}/ - run tools/figma_export.js first")

    parts, widest, y_desk, y_phone = [], 0.0, TOP, TOP
    for f in files:
        w, h, body, keep = artboard(f)
        phone = w < 700
        x = PHONE_X if phone else 0
        if phone:
            y, y_phone = y_phone, y_phone + h + GAP
        else:
            y, y_desk = y_desk, y_desk + h + GAP
        name = os.path.basename(f)[: -len(".svg")]
        parts.append(
            f'<g transform="translate({x:g} {y:g})"{keep}>'
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

    issues = figma_problems(src)
    if issues:
        sys.exit("\n".join(["the canvas would not import:"] + issues))

    path = os.path.join(src, OUT)
    with open(path, "w") as fh:
        fh.write(out)
    return (
        f"{path}  {widest:g}x{tall}  {len(out) // 1024} KB  {len(parts)} artboards"
    )


if __name__ == "__main__":
    print(main(sys.argv[1] if len(sys.argv) > 1 else "figma"))
