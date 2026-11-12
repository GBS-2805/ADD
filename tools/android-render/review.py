#!/usr/bin/env python3
"""Validate the newsprint renders, then rebuild the public review gallery.

The Maven test audits all ten native screen states in memory and writes only the
ten newsprint PNGs plus render-audit.json. This script rejects a missing, extra
or wrongly sized image, and refuses to publish unless that audit report says the
palette and layout gates passed.
"""

from __future__ import annotations

import html
import json
import os
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RENDERS = ROOT / "artifacts" / "android"
SHOTS = ROOT / "artifacts" / "shots"
STAGE = ROOT / "artifacts" / ".shots-next"
MANIFEST = Path(__file__).with_name("manifest.json")
AUDIT = "render-audit.json"
CONTACT = "contact-sheet.png"
WIDTH = 1080
HEIGHT = 2400

PAPER = (233, 224, 203)
INK = (21, 18, 14)
DIM = (98, 91, 79)
BAND = (221, 65, 36)
CARD = (218, 210, 190)
LABEL_FONT = ImageFont.truetype(str(ROOT / "app/res/font/libre_franklin.ttf"), 13)


def fail(message: str) -> None:
    raise SystemExit("review: " + message)


def load_matrix() -> tuple[list[dict[str, str]], str, list[str]]:
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    screens = data.get("screens", [])
    showcase = data.get("showcase")
    if len(screens) != 10:
        fail("manifest must declare ten screens")
    if len({s.get("slug") for s in screens}) != len(screens):
        fail("manifest repeats a screen")
    if not isinstance(showcase, str) or not showcase:
        fail("manifest must name the newsprint showcase")
    expected = [f"{screen['slug']}-{showcase}.png" for screen in screens]
    return screens, showcase, expected


def validate(expected: list[str], matrix: int) -> None:
    actual = {p.name for p in RENDERS.glob("*.png") if p.name != CONTACT}
    missing = sorted(set(expected) - actual)
    extra = sorted(actual - set(expected))
    if missing or extra:
        details = []
        if missing:
            details.append("missing: " + ", ".join(missing))
        if extra:
            details.append("extra: " + ", ".join(extra))
        fail("; ".join(details))

    audit_path = RENDERS / AUDIT
    if not audit_path.is_file():
        fail(f"missing {AUDIT}; the palette/layout gates did not finish")
    try:
        audit = json.loads(audit_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"cannot read {AUDIT}: {error}")
    if audit.get("palette") != "pass" or audit.get("layout") != "pass":
        fail(f"{AUDIT} says the palette or layout gate did not pass")
    if audit.get("themes") != 1:
        fail(f"{AUDIT} audited {audit.get('themes')} themes, expected one")
    if audit.get("matrix") != matrix:
        fail(f"{AUDIT} audited {audit.get('matrix')} combinations, expected {matrix}")
    if audit.get("layoutChecks") != matrix:
        fail(f"{AUDIT} ran {audit.get('layoutChecks')} layout checks, expected {matrix}")
    if audit.get("saved") != len(expected):
        fail(f"{AUDIT} saved {audit.get('saved')} renders, expected {len(expected)}")
    compress_height = audit.get("compressHeight")
    if not isinstance(compress_height, int) or compress_height <= HEIGHT:
        fail(f"{AUDIT} has no full-height Compress capture")

    for name in expected:
        path = RENDERS / name
        try:
            with Image.open(path) as image:
                if image.format != "PNG":
                    fail(f"{name} is {image.format}, expected PNG")
                if name.startswith("compress-"):
                    if image.width != WIDTH or image.height != compress_height:
                        fail(f"{name} is {image.size}, expected PNG {WIDTH}x{compress_height} (full form)")
                elif image.size != (WIDTH, HEIGHT):
                    fail(f"{name} is {image.format} {image.size}, expected PNG {WIDTH}x{HEIGHT}")
        except OSError as error:
            fail(f"cannot read {name}: {error}")


def write_contact(screens: list[dict[str, str]], showcase: str) -> None:
    columns = 3
    card_w, card_h = 360, 800
    rows = (len(screens) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * card_w, rows * card_h), PAPER)
    for index, screen in enumerate(screens):
        column = index % columns
        row = index // columns
        stem = f"{screen['slug']}-{showcase}"
        image = Image.open(RENDERS / (stem + ".png")).convert("RGB")
        image.thumbnail((336, 746), Image.Resampling.LANCZOS)
        card = Image.new("RGB", (card_w, card_h), CARD)
        card.paste(image, ((card_w - image.width) // 2, 34))
        card.paste(Image.new("RGB", (card_w, 4), BAND), (0, 20))
        ImageDraw.Draw(card).text(
            (10, 4), screen["title"], fill=INK, font=LABEL_FONT)
        sheet.paste(card, (column * card_w, row * card_h))
    sheet.save(STAGE / CONTACT, optimize=True)


def write_index(screens: list[dict[str, str]], showcase: str, matrix: int) -> None:
    cards = []
    for screen in screens:
        stem = f"{screen['slug']}-{showcase}"
        kind = " full" if screen.get("full") else ""
        cards.append(
            '<a class="shot{0}" href="{1}.png"><img src="{1}.png" alt="{2}" loading="lazy">'
            '<span>{2}</span></a>'.format(kind, html.escape(stem), html.escape(screen["title"]))
        )

    page = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ADD · native screens</title>
<style>
:root{color-scheme:light}
body{margin:0;background:#f0e7d4;color:#1B1212;font:16px/1.45 ui-sans-serif,system-ui,sans-serif}
main{max-width:1180px;margin:auto;padding:28px 18px 56px}
h1{font-size:clamp(28px,6vw,48px);margin:0 0 8px}
p.lede{max-width:840px;color:#5f5550;margin:0 0 22px}
h2{font-size:13px;letter-spacing:.12em;text-transform:uppercase;color:#5b2a4b;margin:30px 0 10px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(148px,1fr));gap:12px}
.shot{display:block;background:#e1d8c6;border:1px solid rgba(27,18,18,.24);border-radius:10px;padding:7px;color:#1B1212;text-decoration:none}
.shot img{display:block;width:100%;height:auto;border-radius:6px}
.shot span{display:block;font-size:12px;color:#5f5550;padding:7px 2px 2px}
a{color:#1B1212}code{background:rgba(27,18,18,.07);padding:2px 5px;border-radius:4px}
</style></head><body><main>
<h1>ADD · native screens</h1>
<p class="lede">__LEDE__</p>
<p><a href="contact-sheet.png">Contact sheet · all __COUNT__</a></p>
<div class="grid">__CARDS__</div>
<p style="margin-top:30px"><a href="https://artbase-handoff.vercel.app/">App handoff</a> · <a href="https://artbase-olive.vercel.app/">Site</a></p>
</main></body></html>"""
    lede = (
        f"Ten review-edition screenshots from {matrix} in-memory checks across ten native "
        "screen states. Nine are 1080x2400 phone frames; Compress is captured at the "
        "ScrollView's full height so the complete form and action row are visible. The "
        "layouts, the one review edition, drawables, type and the Editor/Compare paint "
        "code are the app's own. Palette, containment, text-fit, composition and "
        "cross-screen position gates run before these images are written. The default "
        "Studio editor is a Chromium WebView and is not captured; the five editor frames "
        "show the native Quick tab one tap away, not the default."
    )
    page = page.replace("__LEDE__", lede).replace("__CARDS__", "".join(cards))
    page = page.replace("__COUNT__", str(len(screens)))
    (STAGE / "index.html").write_text(page, encoding="utf-8")


def main() -> None:
    screens, showcase, expected = load_matrix()
    matrix = len(screens)
    validate(expected, matrix)

    if STAGE.exists():
        shutil.rmtree(STAGE)
    STAGE.mkdir(parents=True)
    for name in expected:
        shutil.copy2(RENDERS / name, STAGE / name)
    write_contact(screens, showcase)
    write_index(screens, showcase, matrix)

    if SHOTS.exists():
        shutil.rmtree(SHOTS)
    os.replace(STAGE, SHOTS)
    shutil.copy2(SHOTS / CONTACT, RENDERS / CONTACT)
    print(f"review: {len(expected)} review-edition PNGs from {matrix} checks in {SHOTS}")


if __name__ == "__main__":
    try:
        main()
    except BaseException:
        shutil.rmtree(STAGE, ignore_errors=True)
        raise
