#!/usr/bin/env python3
"""Mirror the Figma hand-off into the site, so it can be downloaded anywhere.

`figma/` stays the source: `tools/figma_export.js` writes it and
`tools/figma_bundle.py` stacks it. This copies the result into `docs/figma/`,
which `manifest.json` does serve, so a phone can open the same files without a
desktop app. The four pull-out artboards also get the names the links use; every
page artboard keeps its numbered file name. Files no longer exported are removed,
so the folder never carries an artboard that has gone.

    python3 tools/figma_publish.py
"""
import pathlib
import shutil
import sys

SRC = pathlib.Path("figma")
DST = pathlib.Path("docs/figma")

# The navigation states are referred to by what they are, not by their number.
RENAME = {
    "19-index-menu-desktop.svg": "pull-down-desktop.svg",
    "20-index-drawer-desktop.svg": "side-tab-desktop.svg",
    "21-index-menu-phone.svg": "pull-down-phone.svg",
    "22-index-drawer-phone.svg": "side-tab-phone.svg",
}
BUNDLE = "site-figma.svg"


def main():
    exports = sorted(SRC.glob("[0-9][0-9]-*.svg"))
    if not exports:
        sys.exit("no artboards in figma/ - run tools/figma_export.js first")
    bundle = SRC / BUNDLE
    if not bundle.exists():
        sys.exit("figma/%s is missing - run tools/figma_bundle.py" % BUNDLE)

    DST.mkdir(parents=True, exist_ok=True)
    wanted = {}
    for artboard in exports:
        wanted[RENAME.get(artboard.name, artboard.name)] = artboard
    wanted[BUNDLE] = bundle

    dropped = sorted(p.name for p in DST.glob("*.svg") if p.name not in wanted)
    for name in dropped:
        (DST / name).unlink()
    for name, artboard in wanted.items():
        shutil.copyfile(artboard, DST / name)

    total = sum(p.stat().st_size for p in DST.glob("*.svg"))
    print("docs/figma: %d files, %.1f MB%s" % (len(wanted), total / 1e6,
          (", dropped " + ", ".join(dropped)) if dropped else ""))


if __name__ == "__main__":
    main()
