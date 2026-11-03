#!/usr/bin/env python3
"""Store the gallery's captures as lossless WebP instead of PNG.

    python3 tools/to_webp.py <pngDir> <galleryDir> <name> [<name> ...]

The screenshots are the heaviest thing the site serves and the whole site has to
fit in the host's 10 MiB deploy budget. Lossless WebP keeps exactly the same
pixels in roughly a third of the bytes -- it is a re-encoding, not a re-take, so
every image is still a capture of a page that ran. The PNG is removed once its
WebP exists, so the gallery folder never holds two copies of one capture.
"""
import pathlib
import sys

from PIL import Image

LOSSY = None  # lossless on purpose: these are evidence, not decoration.


def main(argv):
    if len(argv) < 4:
        sys.exit("usage: to_webp.py <pngDir> <galleryDir> <name> [<name> ...]")
    src, dst = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    dst.mkdir(parents=True, exist_ok=True)
    smaller = 0
    made, missing = [], []
    for name in argv[3:]:
        png = src / (name + ".png")
        if not png.exists():
            png = dst / (name + ".png")
        if not png.exists():
            # The gallery lists what was asked for, so a capture with no PNG becomes a
            # broken figure on the site. Counting it as converted is how that ships.
            missing.append(name)
            continue
        out = dst / (name + ".webp")
        Image.open(png).convert("RGB").save(out, "WEBP", lossless=True, method=6)
        smaller += png.stat().st_size - out.stat().st_size
        made.append(name)
        if png.parent == dst:
            png.unlink()
    print("webp: %d of %d captures, %d bytes smaller" % (len(made), len(argv) - 3, smaller))
    if missing:
        print("  no PNG for: %s" % ", ".join(missing))
        sys.exit("to_webp: %d capture(s) never reached the browser's output" % len(missing))


if __name__ == "__main__":
    main(sys.argv)
