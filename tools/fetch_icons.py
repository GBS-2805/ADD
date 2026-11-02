#!/usr/bin/env python3
"""Build docs/icons/index.html from the free Lineicons SVG set.

Lineicons publishes 600+ icons under MIT in the LineiconsHQ/Lineicons repo.
The Pro set on lineicons.com is a paid product, so only the free SVGs from
that repository are used here. Each glyph is recoloured to currentColor so
it inherits the surrounding text colour, and keeps its own viewBox.

Re-run to refresh:  python3 tools/fetch_icons.py
"""

import json
import pathlib
import re
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEMPLATE = ROOT / "tools/icons_template.html"
OUTPUT = ROOT / "docs/icons/index.html"
SOURCE = ("https://cdn.jsdelivr.net/gh/LineiconsHQ/Lineicons@main"
          "/assets/svgs/regular/%s.svg")

NAMES = [
    # pictures and camera
    "photos", "gallery", "camera-1", "camera-movie-1", "crop-2",
    # playback
    "play", "pause", "next-step-2", "previous-step-2", "shuffle",
    # sound
    "music", "beat", "watch-beat-1", "microphone-1", "hand-mic",
    "headphone-1", "volume-off", "volume-mute", "volume-low", "volume-1",
    "volume-high", "tower-broadcast-1", "rss-right",
    # screens
    "monitor", "monitor-mac", "chromecast", "facetime",
    # streaming marks
    "spotify", "apple-music", "youtube-music", "soundcloud", "youtube",
    "netflix", "twitch", "vimeo", "tiktok",
]

COLOR = re.compile(r'(fill|stroke)="#[0-9a-fA-F]{3,8}"')


def fetch(name):
    """The raw SVG text, or None when the icon is not in the free set."""
    for attempt in range(3):
        try:
            return urllib.request.urlopen(SOURCE % name, timeout=20).read().decode("utf-8")
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            time.sleep(1 + attempt)
        except Exception:
            time.sleep(1 + attempt)
    return None


def prepare(raw):
    """Returns (root attributes, body) with every hard-coded colour removed."""
    head = re.search(r"<svg([^>]*)>", raw, re.S)
    inner = re.search(r"<svg[^>]*>(.*)</svg>", raw, re.S)
    if not head or not inner:
        return None

    def attr(key, default=""):
        found = re.search(r'%s="([^"]*)"' % key, head.group(1))
        return found.group(1) if found else default

    body = COLOR.sub(r'\1="currentColor"', inner.group(1))
    body = re.sub(r"\s*\n\s*", "", body.strip())

    # Lineicons draws with filled paths, so the root keeps fill="none" and the
    # colour arrives on the children. A stroke-drawn file keeps its stroke.
    parts = ['width="%s"' % attr("width", "24"),
             'height="%s"' % attr("height", "24"),
             'viewBox="%s"' % attr("viewBox", "0 0 24 24")]
    if "currentColor" not in body:
        parts.append('fill="currentColor"')
    else:
        parts.append('fill="none"')
    if 'stroke="' in head.group(1) or 'stroke="currentColor"' in body:
        parts.append('stroke="currentColor"')
    return " ".join(parts), body


def main():
    icons, missing = [], []
    for name in NAMES:
        raw = fetch(name)
        if raw is None:
            missing.append(name)
            continue
        prepared = prepare(raw)
        if prepared is None:
            missing.append(name)
            continue
        attrs, body = prepared
        icons.append({"name": name, "attrs": attrs, "body": body})

    page = TEMPLATE.read_text(encoding="utf-8")
    page = page.replace("__COUNT__", str(len(icons)))
    page = page.replace("__ICONS__", json.dumps(icons, ensure_ascii=False))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(page, encoding="utf-8")

    print("icons: %d" % len(icons))
    print("missing: %s" % (", ".join(missing) if missing else "none"))
    print("wrote %s (%d bytes)" % (OUTPUT.relative_to(ROOT), OUTPUT.stat().st_size))


if __name__ == "__main__":
    main()
