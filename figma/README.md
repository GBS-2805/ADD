# Figma file for the site

Eighteen artboards, one per page per width, as SVG that Figma imports as
editable layers. Nothing here is drawn by hand: `tools/figma_export.js` opens
each page in Chromium at the real viewport and translates what it measures.

| File | Page | Size |
| --- | --- | --- |
| `01-index-desktop.svg` | Index | 1300×1974 |
| `02-icons-desktop.svg` | Icons | 1300×2787 |
| `03-icons-all-desktop.svg` | Icons · All brands | 1300×1085 |
| `04-icons-lineicons-desktop.svg` | Icons · Lineicons | 1300×1417 |
| `05-icons-coolicons-desktop.svg` | Icons · Coolicons | 1300×1417 |
| `06-fonts-desktop.svg` | Fonts | 1300×2025 |
| `07-tools-desktop.svg` | Tools | 1300×5908 |
| `08-portfolio-desktop.svg` | Portfolio | 1300×2890 |
| `09-download-desktop.svg` | Download | 1300×2646 |
| `10-index-phone.svg` | Index | 390×3078 |
| `11-icons-phone.svg` | Icons | 390×3659 |
| `12-icons-all-phone.svg` | Icons · All brands | 390×1436 |
| `13-icons-lineicons-phone.svg` | Icons · Lineicons | 390×1625 |
| `14-icons-coolicons-phone.svg` | Icons · Coolicons | 390×1594 |
| `15-fonts-phone.svg` | Fonts | 390×2804 |
| `16-tools-phone.svg` | Tools | 390×9474 |
| `17-portfolio-phone.svg` | Portfolio | 390×4405 |
| `18-download-phone.svg` | Download | 390×3610 |

Heights are the full scroll height of the page at that width, so an artboard is
the whole page, not just the fold.

## Importing

1. In Figma: **New design file**. Turn on the Poppins family once
   (*Assets → Fonts*, or just apply it to any text and Figma offers to enable
   the Google font) — every text layer asks for Poppins 400/500/600.
2. Drag the SVG files onto the canvas. Each becomes a group of layers at its real
   size; nothing is scaled.
3. Select a group and press **⌥⌘G** (Frame selection) to make it a frame with
   the page's dimensions and clipping.
4. Rename the frames from the `<title>` inside each file if the import drops it.

Colour and type come through as plain values, not styles: the field is
`#3F4E96`, the band `#D9D9D9`, hairlines are 1px rects at
`rgba(217,217,217,.26)` on the field and `rgba(0,0,0,.14)` on the band, dim
text is `#D9D9D9` at 64%. Poppins 400/500/600 is the whole type scale —
76/26/22/20/17/15/14/13/12px.

## What is a layer and what is an image

- Text, boxes, hairlines, pills and card borders are vector layers: edit them
  directly.
- The 45px mark, the chrome glyphs and every icon in a sheet are embedded PNGs
  taken from the page's own `<svg>` elements at 3× — accurate, but not vector.
  The source glyphs are in `docs/data/icons.json` if you want them as vectors.
- The header band, the pull-down and the side tab are in their closed state;
  open states are not exported.

## Regenerating

The toolchain is headless Chromium, rebuilt by `tools/render_env.py` in a
fresh VM. Then:

    export PLAYWRIGHT_BROWSERS_PATH=/tmp/pw
    export LD_LIBRARY_PATH=/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu
    export FONTCONFIG_FILE=/tmp/rootfs/fonts.conf
    export NODE_PATH=/tmp/pwshot/node_modules
    python3 -m http.server 8321 --directory docs &
    node tools/figma_export.js http://127.0.0.1:8321/ figma

`--pages=index` and `--widths=desktop` narrow a run; `--preview=01,10` also
writes clipped PNGs of the exported artboards to `artifacts/` so they can be
compared with the pages they came from.

This folder is a design hand-off, not part of the deployed site: `manifest.json`
serves `docs/` only, so none of it is published.
