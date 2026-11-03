# Figma file for the site

Twenty-four artboards: one per page per width, plus each pull-out open, as SVG
that Figma imports as editable layers. Nothing here is drawn by hand: `tools/figma_export.js` opens
each page in Chromium at the real viewport and translates what it measures.

| File | Page | Size |
| --- | --- | --- |
| `01-index-desktop.svg` | Index | 1300×2069 |
| `02-icons-desktop.svg` | Icons | 1300×2816 |
| `03-icons-all-desktop.svg` | Icons · All brands | 1300×1095 |
| `04-icons-lineicons-desktop.svg` | Icons · Lineicons | 1300×1417 |
| `05-icons-coolicons-desktop.svg` | Icons · Coolicons | 1300×1417 |
| `06-fonts-desktop.svg` | Fonts | 1300×2063 |
| `07-tools-desktop.svg` | Tools | 1300×4421 |
| `08-portfolio-desktop.svg` | Portfolio | 1300×2931 |
| `09-download-desktop.svg` | Download | 1300×2734 |
| `10-releases-desktop.svg` | Releases | 1300×5704 |
| `11-index-phone.svg` | Index | 390×3297 |
| `12-icons-phone.svg` | Icons | 390×3716 |
| `13-icons-all-phone.svg` | Icons · All brands | 390×1449 |
| `14-icons-lineicons-phone.svg` | Icons · Lineicons | 390×1625 |
| `15-icons-coolicons-phone.svg` | Icons · Coolicons | 390×1594 |
| `16-fonts-phone.svg` | Fonts | 390×2899 |
| `17-tools-phone.svg` | Tools | 390×6329 |
| `18-portfolio-phone.svg` | Portfolio | 390×4470 |
| `19-download-phone.svg` | Download | 390×3861 |
| `20-releases-phone.svg` | Releases | 390×7194 |
| `21-index-menu-desktop.svg` | Index · pull-down | 1300×2326 |
| `22-index-drawer-desktop.svg` | Index · side tab | 1300×2069 |
| `23-index-menu-phone.svg` | Index · pull-down | 390×3524 |
| `24-index-drawer-phone.svg` | Index · side tab | 390×3297 |

Heights are the full scroll height of the page at that width, so an artboard is
the whole page, not just the fold.

## The whole set on one canvas

`site-figma.svg` is all twenty-four artboards on one 1910×46015 canvas, desktop
down the left column and phone down the right, each labelled with its file name.
Open that file to look at the set — a browser or the desktop app's file panel
renders it, where a zip can only be downloaded. One drag puts everything into
Figma at once; it is stacked from the twenty-four files by
`tools/figma_bundle.py`, so it never needs editing by hand.

## Importing

1. In Figma: **New design file**. Turn on the Poppins family once
   (*Assets → Fonts*, or just apply it to any text and Figma offers to enable
   the Google font) — every text layer asks for Poppins 400/500/600.
2. Drag the SVG files onto the canvas, or `site-figma.svg` once to bring in the
   whole set. Each artboard becomes a group of layers at its real size; nothing
   is scaled.
3. Select a group and press **⌥⌘G** (Frame selection) to make it a frame with
   the page's dimensions and clipping.
4. Rename the frames from the `<title>` inside each file if the import drops it.

Colour and type come through as plain values, not styles: the field is
`#3F4E96`, the band `#D9D9D9`, hairlines are 1px rects at
`rgba(217,217,217,.26)` on the field and `rgba(0,0,0,.14)` on the band, dim
text is `#D9D9D9` at 64%. Poppins 400/500/600 is the whole type scale —
76/26/22/20/17/15/14/13/12px.

Those faces travel inside every artboard: Poppins 400, 500 and 600 are embedded as
base64 woff2, about 32 KB a file, because an SVG that only names the family renders
in whatever sans the viewer has. Open any of these in a browser — here, from
`/figma/` on the site, or from a phone's download — and the type is the site's.
That is what sets the hand-off's cost: with the faces in, `docs/figma/` measures
2.54 MB (2,661,344 bytes) and the whole site 7.05 MB. The Vercel host has no deploy
cap, so nothing here is near a limit today; the Sites fallback still refuses a folder
over 10 MiB, which is why `tools/figma_publish.py` keeps printing the headroom on
every run. Figma does not read `@font-face` from an SVG, so it still asks to enable the
Google font once on import.

## What is a layer and what is an image

- Text, boxes, hairlines, pills and card borders are vector layers: edit them
  directly.
- The 45px mark, the chrome glyphs and every icon in a sheet are embedded PNGs
  taken from the page's own `<svg>` elements at 3× — accurate, but not vector.
  The source glyphs are in `docs/data/icons.json` if you want them as vectors.
- The header band is exported closed on every page. Artboards 21–24 carry the two
  pull-outs open — the left accordion (`index-menu`) and the right side tab
  (`index-drawer`), at both widths — because they are the site's navigation.
  Every file in this folder is also served from `/figma/` on the site (see
  Regenerating), so none of it needs a desktop app to reach.

## Regenerating

The toolchain is headless Chromium, rebuilt by `tools/render_env.py` in a
fresh VM. Then:

    export PLAYWRIGHT_BROWSERS_PATH=/tmp/pw
    export LD_LIBRARY_PATH=/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu
    export FONTCONFIG_FILE=/tmp/rootfs/fonts.conf
    export NODE_PATH=/tmp/pwshot/node_modules
    python3 -m http.server 8321 --directory docs &
    node tools/figma_export.js http://127.0.0.1:8321/ figma
    python3 tools/figma_bundle.py
    python3 tools/figma_publish.py

`--pages=index` and `--widths=desktop` narrow a run; `--preview=01,10` also
writes clipped PNGs of the exported artboards to `artifacts/` so they can be
compared with the pages they came from.

## Downloading it

`python3 tools/figma_publish.py` mirrors this folder into `docs/figma/`, which the
site does serve, so the hand-off is at `/figma/` on the deployed host:
`/figma/01-index-desktop.svg` and so on for the twenty-four artboards, plus
`/figma/site-figma.svg` for the whole canvas. Page artboards keep their numbers;
on the site the four pull-outs are served under the plain name the links use
(`pull-down-desktop.svg`, `side-tab-phone.svg` and so on), so the numbered copies
stay in `figma/` alone — one name per artboard, because duplicates cost more of
the deploy budget than the hand-off can spare now that each file carries its fonts.
The script removes any
file it did not just write, so a retired artboard cannot linger on the site. Run
it after `figma_bundle.py`, or the site keeps serving the previous export. It also
writes `/figma/` itself — the listing page, every artboard with its dimensions and
weight — so the folder can be browsed from a phone without knowing a file name.
