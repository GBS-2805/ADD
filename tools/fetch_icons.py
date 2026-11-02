#!/usr/bin/env python3
"""Build docs/icons/index.html: a copy-to-clipboard media icon page.

Lineicons' Pro set is paid and its free tier is smaller than it looks, so
this uses Lucide instead - the same 24x24 stroke style, ISC licensed, so
every icon can be copied, edited and shipped without a plan.

Re-run to refresh the icon list:  python3 tools/fetch_icons.py
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
# jsDelivr keeps the published file names; the repo's raw paths have moved a
# few icons around, so it is only the fallback.
SOURCES = (
    "https://cdn.jsdelivr.net/npm/lucide-static@latest/icons/%s.svg",
    "https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/%s.svg",
)

NAMES = [
    "image", "images", "camera", "camera-off", "video", "video-off", "film",
    "clapperboard", "frame", "gallery-horizontal", "gallery-vertical",
    "picture-in-picture", "cast", "airplay", "maximize",
    "music", "music-2", "music-3", "music-4", "audio-lines", "audio-waveform",
    "list-music", "library", "disc", "disc-3", "cassette-tape",
    "mic", "mic-2", "mic-vocal", "headphones", "radio", "radio-tower",
    "podcast", "captions",
    "play", "pause", "circle-play", "circle-pause", "skip-back", "skip-forward",
    "rewind", "fast-forward", "volume", "volume-1", "volume-2", "volume-x",
    "tv", "tv-minimal", "monitor-play",
]


def fetch(name):
    """Returns the inner markup of one icon, or None when no mirror has it."""
    for source in SOURCES:
        for attempt in range(3):
            try:
                raw = urllib.request.urlopen(source % name, timeout=20).read().decode("utf-8")
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    break  # missing here, so try the next mirror
                time.sleep(1 + attempt)
                continue
            except Exception:
                time.sleep(1 + attempt)
                continue
            match = re.search(r"<svg[^>]*>(.*)</svg>", raw, re.S)
            if match:
                return re.sub(r"\s*\n\s*", "", match.group(1).strip())
            break
    return None


def main():
    items, missing = [], []
    for name in NAMES:
        body = fetch(name)
        if body is None:
            missing.append(name)
            continue
        items.append({"name": name, "body": body})

    page = TEMPLATE.read_text(encoding="utf-8")
    page = page.replace("__COUNT__", str(len(items)))
    page = page.replace("__ICONS__", json.dumps(items, ensure_ascii=False))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(page, encoding="utf-8")

    print("icons: %d" % len(items))
    print("missing: %s" % (", ".join(missing) if missing else "none"))
    print("wrote %s (%d bytes)" % (OUTPUT.relative_to(ROOT), OUTPUT.stat().st_size))


if __name__ == "__main__":
    main()
