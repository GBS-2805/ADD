#!/usr/bin/env python3
"""
Renders the app's screens to PNG without a device.

There is no emulator in this environment (no KVM), so this reproduces the
layouts element by element using the same dp/sp values, colours, borders and
shadow offsets as app/res and the Java drawables. It is a design render, not a
device capture: font metrics differ slightly from a real phone.

The visual system mirrors bgremovefree.com/compress: cream paper, white cards
with a 4dp black border and a hard offset shadow, black uppercase section
headers with a ruled underline, monospace captions, yellow highlight chips and
black segmented controls.

This file describes the Android UI only. No APK is built or touched here.

Values mirrored from app/res/layout/*.xml and app/res/values/colors.xml.
Keep them in sync when the UI changes.

    python3 tools/render_ui.py     # writes artifacts/ui-*.png
"""

import io
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "artifacts")
FONT_DIR = os.path.join(ROOT, "app", "res", "font")
GLYPH_FALLBACK = os.path.join(ROOT, "tools", "fonts", "DejaVuSans.ttf")
LOGO_MARK = os.path.join(ROOT, "tools", "logo-mark.png")

# Galaxy Z Flip 7 inner display: 1080 x 2520 px at ~2.625x -> 411 x 960 dp
DP_W, DP_H = 411, 960
S = 2.625

# Mirrors app/res/values/colors.xml: a light band on an indigo field.
# There is one palette now, so both names resolve to the same tokens.
LIGHT = {
    "bg": (0x3F, 0x4E, 0x96), "card": (0x47, 0x56, 0x9C), "card2": (0x4D, 0x5C, 0xA4),
    "ink": (0xD9, 0xD9, 0xD9), "muted": (0xA7, 0xAD, 0xC6),
    "stroke": (0x67, 0x72, 0xA7),
    "primary": (0xD9, 0xD9, 0xD9), "primary_ink": (0x00, 0x00, 0x00),
    "badge_ink": (0x00, 0x00, 0x00), "highlight": (0xD9, 0xD9, 0xD9),
    "dropzone": (0x47, 0x56, 0x9C), "warn_bg": (0x4D, 0x5C, 0xA4),
    "warn_ink": (0xFF, 0xC9, 0x8A), "track": (0x67, 0x72, 0xA7),
}
DARK = dict(LIGHT)

BG = CARD = CARD2 = INK = MUTED = STROKE = (0, 0, 0)
PRIMARY = PRIMARY_INK = BADGE_INK = (0, 0, 0)
HIGHLIGHT = DROPZONE = WARN_BG = WARN_INK = TRACK = (0, 0, 0)


def use_palette(dark):
    """Switches the module-wide palette to the app's light or dark tokens."""
    global BG, CARD, CARD2, INK, MUTED, STROKE, PRIMARY, PRIMARY_INK, BADGE_INK
    global HIGHLIGHT, DROPZONE, WARN_BG, WARN_INK, TRACK
    p = DARK if dark else LIGHT
    BG, CARD, CARD2 = p["bg"], p["card"], p["card2"]
    INK, MUTED, STROKE = p["ink"], p["muted"], p["stroke"]
    PRIMARY, PRIMARY_INK, BADGE_INK = p["primary"], p["primary_ink"], p["badge_ink"]
    HIGHLIGHT, DROPZONE = p["highlight"], p["dropzone"]
    WARN_BG, WARN_INK, TRACK = p["warn_bg"], p["warn_ink"], p["track"]

REG, MED, BOLD = "poppins_regular.ttf", "poppins_medium.ttf", "poppins_semibold.ttf"
FALLBACK = {}
MONO = "poppins_regular.ttf"


def px(dp):
    return dp * S


def font(kind, sp):
    """Loads a font; mono falls back to DejaVuSansMono, then DejaVuSans."""
    if kind == "mono":
        for name in (MONO, "RobotoMono-Regular.ttf", "DejaVuSans.ttf"):
            path = os.path.join(FONT_DIR, name)
            if os.path.exists(path):
                return ImageFont.truetype(path, int(round(sp * S)))
    path = os.path.join(FONT_DIR, kind)
    if not os.path.exists(path):
        path = os.path.join(FONT_DIR, FALLBACK.get(kind, "DejaVuSans.ttf"))
    return ImageFont.truetype(path, int(round(sp * S)))


def mono(sp):
    return font("mono", sp)


def logo_mark(size_px, fill):
    """The brand mark, rasterised by tools/render_logo_mark.js, tinted to `fill`."""
    if not os.path.exists(LOGO_MARK):
        return None
    src = Image.open(LOGO_MARK).convert("RGBA")
    w = max(1, int(size_px))
    h = max(1, int(round(w * src.height / src.width)))
    alpha = src.getchannel("A").resize((w, h), Image.LANCZOS)
    tinted = Image.new("RGBA", (w, h), tuple(fill) + (0,))
    tinted.putalpha(alpha)
    return tinted


def tracked(draw, xy, text, fnt, fill, tracking_dp=0.0):
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=fnt, fill=fill, anchor="la")
        x += fnt.getlength(ch) + tracking_dp * S
    return x


def text_w(text, fnt, tracking_dp=0.0):
    if not tracking_dp:
        return fnt.getlength(text)
    return sum(fnt.getlength(c) + tracking_dp * S for c in text)


FALLBACK_CHARS = {"\u2192", "\u2039", "\u00d7"}  # arrows / chevrons / times


def draw_mixed(draw, xy, text, fnt, fill, fallback=None):
    """Draws text left-aligned, swapping glyphs the Latin Roboto subset lacks."""
    x, y = xy
    for ch in text:
        f = fnt
        if ch in FALLBACK_CHARS:
            f = fallback or ImageFont.truetype(GLYPH_FALLBACK, fnt.size)
        draw.text((x, y), ch, font=f, fill=fill, anchor="la")
        x += f.getlength(ch)
    return x


def ellipsize(text, fnt, max_w):
    if fnt.getlength(text) <= max_w:
        return text
    lo, hi = 0, len(text)
    while lo < hi:
        mid = (lo + hi) // 2
        if fnt.getlength(text[:mid] + "\u2026") <= max_w:
            lo = mid + 1
        else:
            hi = mid
    return text[:max(0, lo - 1)] + "\u2026"


def wrap(text, fnt, max_w):
    words, lines, line = text.split(), [], ""
    for w in words:
        candidate = (line + " " + w).strip()
        if fnt.getlength(candidate) <= max_w or not line:
            line = candidate
        else:
            lines.append(line)
            line = w
    if line:
        lines.append(line)
    return lines


def card_box(draw, box, offset=0, border=1, fill=CARD):
    """Flat panel: no shadow, no border, square corners."""
    x0, y0, x1, y1 = box
    draw.rectangle([x0, y0, x1, y1], fill=fill)


def framed_box(draw, box, offset=0, border=1, fill=CARD):
    """Outlined block: a single hairline, used for ghost buttons and rows."""
    x0, y0, x1, y1 = box
    draw.rectangle([x0, y0, x1, y1], fill=fill,
                   outline=HIGHLIGHT if fill == BG else STROKE, width=max(1, int(px(1.5))))


def status_bar(draw, w):
    top = px(24)
    draw.text((px(20), top - px(15)), "9:41", font=font(MED, 15), fill=INK, anchor="lm")
    x = w - px(96)
    for i in range(4):
        h = px(3 + i * 2.2)
        draw.rectangle([x + i * px(4), top + px(4) - h,
                        x + i * px(4) + px(2.4), top + px(4)], fill=INK)
    x = w - px(66)
    for i in range(3):
        draw.arc([x + i * px(6), top - px(4) + i * px(5),
                  x + i * px(6) + px(16), top + px(12) + i * px(5)],
                 start=215, end=325, fill=INK, width=max(1, int(px(1.6))))
    bx, by = w - px(40), top - px(6)
    draw.rectangle([bx, by, bx + px(24), by + px(13)], outline=INK,
                   width=max(1, int(px(1.4))))
    draw.rectangle([bx + px(2), by + px(2), bx + px(17), by + px(11)], fill=INK)
    draw.rectangle([bx + px(25.5), by + px(4), bx + px(27.5), by + px(9)], fill=INK)
    pill_w, pill_h = px(108), px(4)
    draw.rectangle([(w - pill_w) / 2, px(DP_H) - px(10),
                    (w + pill_w) / 2, px(DP_H) - px(10) + pill_h], fill=INK)


def sample_photo(w, h, seed):
    rnd = random.Random(seed)
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    horizon = int(h * 0.62)
    top, mid = (24, 40, 70), (236, 150, 96)
    for y in range(horizon):
        t = y / horizon
        d.line([(0, y), (w, y)],
               fill=tuple(int(top[i] + (mid[i] - top[i]) * (t ** 0.85)) for i in range(3)))
    sx, sy = int(w * 0.68), int(horizon * 0.42)
    for r in range(int(w * 0.09), 0, -1):
        a = 1 - r / (w * 0.09)
        d.ellipse([sx - r, sy - r, sx + r, sy + r],
                  fill=(int(min(255, 255 * a + 40)), int(min(255, 210 * a + 20)),
                        int(min(255, 150 * a + 10))))
    for layer in range(4):
        base = horizon + int(layer * h * 0.075)
        pts, x = [(0, h)], 0
        while x <= w:
            pts.append((x, base - rnd.randint(0, int(h * 0.075))))
            x += int(w / 8)
        pts.append((w, h))
        shade = 52 - layer * 9
        d.polygon(pts, fill=(shade, shade + 14, shade + 22))
    for _ in range(1200):
        x, y = rnd.randrange(w), rnd.randrange(int(h * 0.55), h)
        v = rnd.randint(-16, 16)
        d.point((x, y), fill=(max(0, min(255, 30 + v)), max(0, min(255, 46 + v)),
                              max(0, min(255, 54 + v))))
    return img


def degraded(img, scale=0.5, quality=28):
    small = img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))),
                       Image.LANCZOS)
    buf = io.BytesIO()
    small.save(buf, format="JPEG", quality=quality)
    buf.seek(0)
    back = Image.open(buf).convert("RGB").resize(img.size, Image.BICUBIC)
    return back.filter(ImageFilter.GaussianBlur(0.7))


def square_crop(img, size):
    side = min(img.size)
    left = (img.width - side) // 2
    top = int((img.height - side) * 0.35)
    return img.crop((left, top, left + side, top + side)).resize((size, size), Image.LANCZOS)


def seekbar(draw, x, cy, w, fraction):
    track_h = px(4)
    draw.rectangle([x, cy - track_h / 2, x + w, cy + track_h / 2], fill=TRACK)
    draw.rectangle([x, cy - track_h / 2, x + w * fraction, cy + track_h / 2], fill=HIGHLIGHT)
    tx = x + w * fraction
    draw.ellipse([tx - px(9), cy - px(9), tx + px(9), cy + px(9)], fill=HIGHLIGHT)
    draw.ellipse([tx - px(4.5), cy - px(4.5), tx + px(4.5), cy + px(4.5)], fill=BG)


def segment(draw, x, y, w, h, labels, selected, f_on, f_off):
    card_box(draw, [x, y, x + w, y + h], offset=4, border=4, fill=CARD2)
    pad = px(4)
    seg_w = (w - pad * 2) / len(labels)
    for i, label in enumerate(labels):
        sx = x + pad + i * seg_w
        if i == selected:
            draw.rectangle([sx, y + pad, sx + seg_w, y + h - pad], fill=PRIMARY)
            draw.text((sx + seg_w / 2, y + h / 2), label, font=f_on, fill=PRIMARY_INK,
                      anchor="mm")
        else:
            draw.text((sx + seg_w / 2, y + h / 2), label, font=f_off, fill=INK, anchor="mm")


def primary_button(draw, box, label, fnt):
    x0, y0, x1, y1 = box
    card_box(draw, [x0, y0, x1, y1], offset=4, border=4, fill=PRIMARY)
    draw.text(((x0 + x1) / 2, (y0 + y1) / 2), label, font=fnt, fill=PRIMARY_INK,
              anchor="mm")


def ghost_button(draw, box, label, fnt):
    x0, y0, x1, y1 = box
    card_box(draw, [x0, y0, x1, y1], offset=4, border=4, fill=CARD)
    draw.text(((x0 + x1) / 2, (y0 + y1) / 2), label, font=fnt, fill=INK, anchor="mm")


def chip(draw, right, cy, label, fnt, fill=None, pad_w=6, h=18):
    # Resolved at call time: the palette is swapped per screen.
    fill = HIGHLIGHT if fill is None else fill
    w = fnt.getlength(label) + px(pad_w) * 2
    draw.rectangle([right - w, cy - px(h) / 2, right, cy + px(h) / 2], fill=fill)
    draw.text((right - w / 2, cy), label, font=fnt, fill=BADGE_INK, anchor="mm")
    return w


def badge(draw, right, cy, label, warn=False):
    f = font(BOLD, 11.5)
    h = px(22)
    w = f.getlength(label) + px(16)
    draw.rectangle([right - w, cy - h / 2, right, cy + h / 2],
                   fill=WARN_BG if warn else HIGHLIGHT)
    draw.text((right - w / 2, cy), label, font=f,
              fill=WARN_INK if warn else BADGE_INK, anchor="mm")
    return w


def share_glyph(draw, cx, cy, size_dp=16):
    r = px(size_dp * 0.13)
    pts = [(cx - px(size_dp * 0.30), cy), (cx + px(size_dp * 0.30), cy - px(size_dp * 0.24)),
           (cx + px(size_dp * 0.30), cy + px(size_dp * 0.24))]
    for a, b in ((pts[0], pts[1]), (pts[0], pts[2])):
        draw.line([a, b], fill=INK, width=max(1, int(px(1.6))))
    for p in pts:
        draw.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=INK)


def plus_circle(draw, cx, cy, d_dp=46):
    r = px(d_dp) / 2
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(0x5A, 0x69, 0xAE))
    draw.text((cx, cy - px(1)), "+", font=font(MED, 22), fill=INK, anchor="mm")


def section_header(od, x, y, w, title, size=21):
    od.text((x, y), title, font=font(BOLD, size), fill=INK, anchor="la")
    rule_y = y + px(size + 6)
    od.rectangle([x, rule_y, x + w, rule_y + px(1)], fill=STROKE)
    return rule_y + px(1) + px(12)


def render_main(selected, results=None, out="main.png", scroll_dp=None, dark=False):
    use_palette(dark)
    # A results screen is taller than the viewport, so it is drawn on a taller
    # canvas and cropped to one screen, scrolled down to the results block.
    tall = bool(results) and scroll_dp is None
    w = int(px(DP_W))
    # A negative scroll_dp means "scrolled to the bottom", which needs headroom
    # below the viewport for the content that is scrolled past.
    h = int(px(DP_H)) + (int(px(900)) if (tall or scroll_dp) else 0)
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    status_bar(d, w)

    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)

    pad = px(16)
    inner = pad + px(14)
    inner_w = (w - pad) - inner - px(14)

    y = px(24) + px(16)
    od.text((pad, y), "Compress", font=font(BOLD, 30), fill=INK, anchor="la")

    y += px(42)
    od.text((pad, y), "Batch compress \u00b7 100% offline \u00b7 no uploads",
            font=font(REG, 12.5), fill=MUTED, anchor="la")
    y += px(18)

    cards = []      # (top, bottom) of each white card
    photos = []     # (box, image) pasted before the overlay

    # ---- 1. ADD IMAGES -------------------------------------------------
    y += px(16)
    card_top = y
    y += px(14)
    y = section_header(od, inner, y, inner_w, "1. ADD IMAGES")

    dz_h = px(16 + 46 + 12 + 20 + 8 + 18 + 16)
    dz_box = [inner, y, w - pad - px(14), y + dz_h]
    cx = (dz_box[0] + dz_box[2]) / 2
    od.rectangle(dz_box, outline=HIGHLIGHT, width=int(px(1.5)), fill=DROPZONE)
    # dashed outline on top of the fill
    dash, gap = px(9), px(5)
    x0, y0, x1, y1 = dz_box
    xx = x0
    while xx < x1:
        od.line([(xx, y0), (min(xx + dash, x1), y0)], fill=HIGHLIGHT, width=int(px(1.5)))
        od.line([(xx, y1), (min(xx + dash, x1), y1)], fill=HIGHLIGHT, width=int(px(1.5)))
        xx += dash + gap
    yy = y0
    while yy < y1:
        od.line([(x0, yy), (x0, min(yy + dash, y1))], fill=HIGHLIGHT, width=int(px(1.5)))
        od.line([(x1, yy), (x1, min(yy + dash, y1))], fill=HIGHLIGHT, width=int(px(1.5)))
        yy += dash + gap

    plus_circle(od, cx, y + px(16) + px(23))
    title = "3 PHOTOS SELECTED" if selected else "TAP TO PICK PHOTOS"
    sub = "8.42 MB TOTAL \u00b7 TAP TO CHANGE" if selected else \
        "JPG \u2022 PNG \u2022 WEBP \u2014 UP TO 40 FILES"
    od.text((cx, y + px(16 + 46 + 12 + 9)), title, font=font(BOLD, 15), fill=INK,
            anchor="mm")
    chip(od, cx + (mono(10.5).getlength(sub) + px(12)) / 2, y + dz_h - px(16) - px(9),
         sub, mono(10.5))
    y += dz_h

    if selected:
        y += px(12)
        tile = px(62)
        for i, seed in enumerate((11, 22, 33)):
            tx = inner + i * (tile + px(8))
            thumb = square_crop(sample_photo(300, 300, seed), int(tile))
            photos.append(([tx, y, tx + tile, y + tile], thumb))
            photos.append((None, [tx, y, tx + tile, y + tile]))  # border marker
            bx, by = tx + tile - px(13), y + px(13)
            od.ellipse([bx - px(12), by - px(12), bx + px(12), by + px(12)], fill=HIGHLIGHT)
            od.text((bx, by - px(1)), "\u00d7", font=font(BOLD, 13), fill=BADGE_INK,
                    anchor="mm")
        y += tile

        y += px(10)
        f = font(BOLD, 11)
        label = "CLEAR ALL"
        cw = f.getlength(label) + px(20)
        framed_box(od, [w - pad - px(14) - cw, y, w - pad - px(14), y + px(28)],
                   offset=3, border=3, fill=CARD)
        od.text((w - pad - px(14) - cw / 2, y + px(14)), label, font=f, fill=INK,
                anchor="mm")
        y += px(28)

    y += px(18)
    cards.append((card_top, y))

    # ---- 2. OUTPUT FORMAT ----------------------------------------------
    y += px(13)
    card_top = y
    y += px(14)
    y = section_header(od, inner, y, inner_w, "2. OUTPUT FORMAT")
    seg_h = px(42)
    segment(od, inner, y, inner_w, seg_h, ["JPEG", "WebP"], 0,
            font(BOLD, 13), font(REG, 13))
    y += seg_h
    y += px(8)
    od.text((inner, y), "JPEG: widely compatible, great for photos",
            font=mono(10.5), fill=MUTED, anchor="la")
    y += px(16 + 18)
    cards.append((card_top, y))

    # ---- 3. COMPRESSION ------------------------------------------------
    y += px(13)
    card_top = y
    y += px(14)
    y = section_header(od, inner, y, inner_w, "3. COMPRESSION")
    od.text((inner, y + px(8)), "EXCELLENT QUALITY", font=mono(11), fill=MUTED,
            anchor="la")
    od.text((inner + inner_w, y - px(4)), "78%", font=font(BOLD, 23), fill=INK,
            anchor="ra")
    y += px(26)
    seekbar(od, inner, y, inner_w, 33 / 50)
    y += px(16)
    od.text((inner, y), "45% (smallest)", font=mono(10), fill=MUTED, anchor="la")
    od.text((inner + inner_w, y), "95% (best)", font=mono(10), fill=MUTED, anchor="ra")
    y += px(14 + 18)
    cards.append((card_top, y))

    # ---- 4. RESIZE -----------------------------------------------------
    y += px(13)
    card_top = y
    y += px(14)
    y = section_header(od, inner, y, inner_w, "4. RESIZE (OPTIONAL)")
    od.text((inner, y + px(8)), "LONGEST EDGE", font=mono(11), fill=MUTED, anchor="la")
    od.text((inner + inner_w, y - px(4)), "2560 px", font=font(BOLD, 23), fill=INK,
            anchor="ra")
    y += px(26)
    seekbar(od, inner, y, inner_w, 3 / 5)
    y += px(16)
    od.text((inner, y), "ORIGINAL", font=mono(10), fill=MUTED, anchor="la")
    od.text((inner + inner_w, y), "4096 PX", font=mono(10), fill=MUTED, anchor="ra")
    y += px(14 + 18)
    cards.append((card_top, y))

    # ---- actions -------------------------------------------------------
    y += px(16)
    ghost_button(d, [pad, y, w - pad, y + px(47)], "PREVIEW BEFORE COMPRESSING",
                 font(BOLD, 13.5))
    y += px(47 + 14)
    primary_button(d, [pad, y, w - pad, y + px(53)], "COMPRESS ALL", font(BOLD, 16))
    y += px(53)

    results_top = y
    if results:
        y += px(16)
        od.text((pad, y + px(2)), results["summary"], font=font(BOLD, 14), fill=INK,
                anchor="la")
        ghost_button(d, [w - pad - px(84), y - px(6), w - pad, y + px(26)],
                     "SHARE ALL", font(BOLD, 11))
        y += px(40)
        for row in results["rows"]:
            row_h = px(78)
            framed_box(d, [pad, y, w - pad, y + row_h], offset=6, border=4, fill=CARD)
            thumb = square_crop(sample_photo(420, 420, row["seed"]), int(px(54)))
            img.paste(thumb, (int(pad + px(12)), int(y + px(12))))
            d.rectangle([pad + px(12), y + px(12), pad + px(12) + px(54),
                         y + px(12) + px(54)], outline=STROKE, width=1)
            text_x = pad + px(12) + px(54) + px(12)
            share_cx = w - pad - px(10) - px(16)
            badge_right = share_cx - px(24)
            badge_w = font(BOLD, 11.5).getlength(row["badge"]) + px(16)
            avail = badge_right - badge_w - text_x - px(6)
            od.text((text_x, y + px(17)),
                    ellipsize(row["name"], font(BOLD, 13.5), avail),
                    font=font(BOLD, 13.5), fill=INK, anchor="la")
            draw_mixed(od, (text_x, y + px(40)),
                       ellipsize(row["sizes"], mono(10.5), avail),
                       mono(10.5), MUTED)
            badge(od, badge_right, y + row_h / 2, row["badge"])
            d.ellipse([share_cx - px(16), y + row_h / 2 - px(16),
                       share_cx + px(16), y + row_h / 2 + px(16)],
                      fill=CARD, outline=STROKE, width=1)
            share_glyph(od, share_cx, y + row_h / 2)
            y += row_h + px(14)
        y += px(4)

    y += px(10)
    note = ("Preview opens a slider on your first selected photo \u2014 nothing is saved "
            "until you compress. Copies land in Pictures/Compress; originals are never "
            "modified.")
    for line in wrap(note, mono(10.5), w - pad * 2):
        od.text((pad, y), line, font=mono(10.5), fill=MUTED, anchor="la")
        y += px(16)

    # ---- brand footer: mark + the download page for the other apps ------
    y += px(8)
    mark = logo_mark(px(26), INK)
    if mark is not None:
        overlay.paste(mark, (int(pad), int(y + px(10))), mark)
    foot_x = pad + px(26 + 10)
    od.text((foot_x, y + px(10)), "MORE APPS", font=mono(11), fill=INK, anchor="la")
    draw_mixed(od, (foot_x, y + px(25)), "TAP TO OPEN THE WEBSITE \u2192", mono(10), MUTED)
    y += px(46)

    # cards first (behind), then photos, then text
    for top, bottom in cards:
        card_box(d, [pad, top, w - pad, bottom], offset=6, border=4, fill=CARD)
    for box, payload in photos:
        if box is None:
            continue
        img.paste(payload, (int(box[0]), int(box[1])))
    for box, payload in photos:
        if box is not None:
            continue
        x0, y0, x1, y1 = payload
        d.rectangle([x0, y0, x1, y1], outline=STROKE, width=1)
    img.paste(overlay, (0, 0), overlay)

    if tall or scroll_dp:
        scroll = y - int(px(DP_H)) + px(24) if (tall or scroll_dp < 0) else px(scroll_dp)
        scroll = max(0, min(scroll, h - int(px(DP_H))))
        img = img.crop((0, int(scroll), w, int(scroll) + int(px(DP_H))))

    img.save(os.path.join(ART, out))
    return os.path.join(ART, out)


def render_compare(out="preview.png", preview=True, fraction=0.56, dark=False):
    use_palette(dark)
    w, h = int(px(DP_W)), int(px(DP_H))
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    status_bar(d, w)

    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)

    pad = px(14)
    y = px(24) + px(10)

    cy = y + px(20)
    d.ellipse([pad, cy - px(20), pad + px(40), cy + px(20)], fill=CARD, outline=INK,
              width=int(px(3)))
    draw_mixed(od, (pad + px(13), cy - px(13)), "\u2039", font(BOLD, 26), INK)
    od.text((pad + px(50), y + px(4)), "Preview \u00b7 JPEG \u00b7 q78 \u00b7 2560 px",
            font=font(BOLD, 16), fill=INK, anchor="la")
    draw_mixed(od, (pad + px(50), y + px(28)),
               "est. 3024 \u00d7 4032 \u00b7 11.84 MB \u2192 2.31 MB \u00b7 80% smaller",
               mono(10.5), MUTED)

    btn_h = px(50)
    frame_top = y + px(58)
    frame_bottom = h - px(18) - btn_h - px(12) - px(34)
    frame = [pad, frame_top, w - pad, frame_bottom]
    ix0, iy0 = pad + px(12), frame_top + px(12)
    ix1, iy1 = w - pad - px(12), frame_bottom - px(12)

    photo = sample_photo(1200, 1600, 7)
    soft_photo = degraded(photo)
    area_w, area_h = int(ix1 - ix0), int(iy1 - iy0)
    scale = min(area_w / photo.width, area_h / photo.height)
    fw, fh = int(photo.width * scale), int(photo.height * scale)
    sharp = photo.resize((fw, fh), Image.LANCZOS)
    soft = soft_photo.resize((fw, fh), Image.LANCZOS)
    split = int(fw * fraction)
    composite = sharp.copy()
    composite.paste(soft.crop((split, 0, fw, fh)), (split, 0))
    ox = int(ix0 + (area_w - fw) / 2)
    oy = int(iy0 + (area_h - fh) / 2)

    # frame first, then the photo inside it
    card_box(d, frame, offset=6, border=4, fill=CARD)
    img.paste(composite, (ox, oy))
    d.rectangle([ox, oy, ox + fw, oy + fh], outline=STROKE, width=1)

    sx = ox + split
    d.line([(sx, oy), (sx, oy + fh)], fill=BG, width=max(1, int(px(2))))
    d.line([(sx, oy), (sx, oy + fh)], fill=HIGHLIGHT, width=max(1, int(px(1))))
    hcy = oy + fh / 2
    d.ellipse([sx - px(17), hcy - px(17), sx + px(17), hcy + px(17)], fill=HIGHLIGHT)
    for direction in (-1, 1):
        ax = sx + direction * px(4)
        tip = sx + direction * px(9)
        d.line([(ax, hcy - px(4)), (tip, hcy)], fill=INK, width=max(1, int(px(2.2))))
        d.line([(tip, hcy), (ax, hcy + px(4))], fill=INK, width=max(1, int(px(2.2))))

    f = font(BOLD, 9.5)
    for label, left_side in (("BEFORE", True), ("AFTER", False)):
        tw = text_w(label, f, 0.4)
        cw, ch = tw + px(14), px(24)
        cxp = ox + px(10) if left_side else ox + fw - px(10) - cw
        d.rectangle([cxp, oy + px(10), cxp + cw, oy + px(10) + ch], fill=HIGHLIGHT)
        tracked(od, (cxp + px(7), oy + px(15)), label, f, BADGE_INK, 0.4)

    hint = ("THIS IS WHAT YOUR SETTINGS WOULD PRODUCE \u2014 NOTHING SAVED YET" if preview
            else "DRAG THE HANDLE TO COMPARE BEFORE AND AFTER")
    od.text((w / 2, frame_bottom + px(18)), hint, font=mono(10.5), fill=MUTED,
            anchor="mm")
    primary_button(d, [pad, frame_bottom + px(36), w - pad, frame_bottom + px(36) + btn_h],
                   "COMPRESS ALL PHOTOS" if preview else "SHARE COMPRESSED PHOTO",
                   font(BOLD, 14))
    img.paste(overlay, (0, 0), overlay)
    img.save(os.path.join(ART, out))
    return os.path.join(ART, out)


def contact_sheet(paths, out="ui-all.png", gap_dp=18):
    images = [Image.open(p) for p in paths]
    gap = int(px(gap_dp))
    w = sum(i.width for i in images) + gap * (len(images) + 1)
    h = max(i.height for i in images) + gap * 2
    sheet = Image.new("RGB", (w, h), (0xE9, 0xE5, 0xD8))
    x = gap
    for im in images:
        sheet.paste(im, (x, gap))
        x += im.width + gap
    sheet.save(os.path.join(ART, out))
    return os.path.join(ART, out)


def main():
    os.makedirs(ART, exist_ok=True)
    paths = [
        render_main(False, out="ui-1-main-empty.png"),
        render_main(True, out="ui-2-main-selected.png"),
        render_compare(out="ui-3-preview-slider.png", preview=True),
        render_main(False, out="ui-7-footer-light.png", scroll_dp=-1),
        render_main(True, out="ui-4-results.png", results={
            "summary": "Saved 14.2 MB \u00b7 76% smaller \u00b7 3 files",
            "rows": [
                {"seed": 11, "name": "IMG_2481_compressed.jpg",
                 "sizes": "3024 \u00d7 4032 \u00b7 11.84 MB \u2192 2.31 MB", "badge": "-80%"},
                {"seed": 22, "name": "IMG_2493_compressed.jpg",
                 "sizes": "2560 \u00d7 1920 \u00b7 4.12 MB \u2192 1.24 MB", "badge": "-70%"},
                {"seed": 33, "name": "Screenshot_20261027_compressed.webp",
                 "sizes": "1080 \u00d7 2400 \u00b7 2.10 MB \u2192 0.62 MB", "badge": "-71%"},
            ],
        }),
    ]
    for p in paths:
        print("wrote", os.path.relpath(p, ROOT))
    print("wrote", os.path.relpath(contact_sheet(paths), ROOT))


if __name__ == "__main__":
    main()
