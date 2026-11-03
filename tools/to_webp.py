#!/usr/bin/env python3
"""Store the gallery's captures as WebP instead of PNG.

    python3 tools/to_webp.py <pngDir> <galleryDir> <name> [<name> ...]

The screenshots are the heaviest thing the site serves. Each capture is encoded
twice -- losslessly and at LOSSY_QUALITY -- and the smaller file wins: flat panels
and sharp text compress better losslessly (the landing page is 92 KB lossless
against 217 KB at q82), while a 16,000px phone page is a third of the size lossy
(1.9 MB -> 799 KB at 35.5 dB PSNR). Picking per image means the folder never grows
to save bytes, and every image is still a capture of a page that ran: a
re-encoding, not a re-take. The PNG is removed once its WebP exists.
"""
import pathlib
import sys

WEBP_LIMIT = 16000

from PIL import Image

# Tried against lossless on every capture; whichever file is smaller is kept.
LOSSY_QUALITY = 82


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
        image = Image.open(png).convert("RGB")
        # WebP refuses anything longer than 16,383 px, and a full-height capture at 2x
        # device scale can pass that. Shrink the outliers to fit: a slightly smaller
        # screenshot beats a capture run that aborts and leaves the gallery behind.
        longest = max(image.size)
        if longest > WEBP_LIMIT:
            scale = WEBP_LIMIT / longest
            image = image.resize((max(1, round(image.width * scale)),
                                  max(1, round(image.height * scale))), Image.LANCZOS)
            print("  shrunk %s to %dx%d" % (png.name, image.width, image.height))
        import io
        plain = io.BytesIO()
        image.save(plain, "WEBP", lossless=True, method=6)
        lossy = io.BytesIO()
        image.save(lossy, "WEBP", quality=LOSSY_QUALITY, method=6)
        if len(lossy.getvalue()) < len(plain.getvalue()):
            out.write_bytes(lossy.getvalue())
            print("  lossy q%d for %s: %.1f KB vs %.1f KB lossless"
                  % (LOSSY_QUALITY, name, len(lossy.getvalue()) / 1024, len(plain.getvalue()) / 1024))
        else:
            out.write_bytes(plain.getvalue())
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
