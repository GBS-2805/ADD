# Working rules for this repo

## Renders: only when the change is visual

A change to a page in `docs/` needs image renders **only if it changes what the page
looks like**: layout, spacing, colour, typography, iconography, or the presence and
position of an element.

**Skip renders entirely** -- and skip `docs/renders/`, the gallery page and the
deploy that would carry them -- for changes that cannot alter appearance:

- copy and wording edits, including headings, labels and alt text;
- link and URL changes, or swapping a `href`, `src` target or favicon path;
- attributes, metadata, comments, formatting and other non-visual markup;
- anything outside `docs/` at all.

When skipping, say so in one line in the reply (`No render: copy-only change`) so the
omission reads as a decision rather than an oversight. Do not capture a page to prove
that text changed.

**When a render is warranted**:

- Capture with headless Chromium via Playwright, served from a local static server
  over `docs/`, at 1300x900. `tools/render_pages.js` takes every page of the shelf
  in one run -- the index at its fold and at full height, then the left accordion
  and the right side tab open, then tools, both icon sets, fonts, portfolio and
  download at desktop
  width, then the index, its left menu and download at 390x844 -- in about ten
  seconds, and prints console errors and horizontal overflow.
- Save under `artifacts/`, one file per page the change touches. The names the
  gallery already uses are `page-home-light`, `page-home-full`, `page-menu`, `page-drawer`, `page-tools`,
  `page-icons`, `page-icons-brand`, `page-icons-panel`, `page-icons-variants`,
  `page-fonts`, `page-portfolio`, `page-download`, `phone-home-light`,
  `phone-home-full`, `phone-menu`, `phone-drawer`, `phone-icons`,
  `phone-icons-brand` and `logo-light`.. There is one palette, so there are no dark variants; the `-light`
  suffix on some of them is history, not a variant.
- Cover every page the change touches, plus mobile width when the change is
  responsive.
- Inspect each render before sending it. If the render shows a layout, colour or
  overflow problem, fix the page and re-render; never ship a screenshot of a bug.
- Report console errors seen while capturing. A render that only proves HTTP 200
  is not a render.
- Standalone artwork (SVG marks, icons) is additionally rendered with
  `@resvg/resvg-js` so the asset itself is verifiable apart from the page.
- Only then copy into `docs/renders/`, add it to `docs/renders/index.html`, and
  deploy -- the gallery only shows files listed there.

### Renders are captures, never drawings

Every image in `docs/renders/` is a screenshot of a page that actually ran. Do not
draw the Android app from its `dp`/`sp` values and do not recreate a screen for the
gallery: a reconstruction reads as a screenshot without being one, and this was
already shipped once and removed.

The app cannot be captured on this VM (see below), so the gallery carries no app
images. When an image stops being a capture, delete the file from `docs/renders/`
and its entry from the gallery in the same change; leave no orphan PNGs behind.

### How renders are sent (learned the hard way)

The person reading this works from a phone. Deliver renders like this, every time:

1. Lead with **one tappable markdown link to the live site**:
   `[Open the site](https://<site-host>/)`.
2. Follow immediately with **one tappable markdown link to the gallery**:
   `[Open the renders gallery](https://<site-host>/renders/)`.
3. Offer individual images as **short labelled markdown links**
   (`[Compress page — 1300×900](https://<site-host>/renders/x.png)`).
4. **Never** paste a bare `https://...` URL as the only way in: long URLs wrap
   mid-address and a phone will not linkify them.
5. **Never** rely on workspace-relative paths (`artifacts/x.png`) or inline
   `![](...)` embeds built from them — neither resolves outside the desktop app.
6. Verify before sending: the gallery returns 200, every listed image loads with a
   real `naturalWidth`, and no capture is missing from the page.

## Local render setup (no root, no system Chrome)

This VM has bare Chromium deps and zero fonts. `python3 tools/render_env.py` builds
the whole toolchain in one go (~4 min, ~300 MB) and prints the exports below; the
recipe underneath is what it automates, in case it breaks.

    npm i playwright && npx playwright install chromium   # PLAYWRIGHT_BROWSERS_PATH=/tmp/pw
    # unpack the missing runtime libs rootlessly from the Ubuntu archive:
    #   parse dists/noble/{main,universe}/binary-amd64/Packages.gz for the
    #   Filenames of libnspr4 libnss3 libasound2t64 libatk1.0-0t64
    #   libatk-bridge2.0-0t64 libatspi2.0-0t64 libxkbcommon0 libxcomposite1
    #   libxdamage1 libxfixes3 libxrandr2 libxrender1 libxcb-render0 libxcb-shm0
    #   libpixman-1-0 libharfbuzz0b libgraphite2-3 libfreetype6 libfontconfig1
    #   libpng16-16t64 libbrotli1 libx11-xcb1 libwayland-* fonts-dejavu-core
    #   fonts-liberation libxi6 libxtst6 libegl1 libglx0, then `dpkg -x` them
    #   into /tmp/rootfs. apt-get download fails here (stale indexes).
    export LD_LIBRARY_PATH=/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu
    export FONTCONFIG_FILE=/tmp/rootfs/fonts.conf   # <dir>/tmp/rootfs/usr/share/fonts</dir>

Screenshots are of a local server over `docs/` (`python3 -m http.server`), viewport
1300x900, and 390x844 for phone shots. `node tools/render_pages.js <baseUrl>
<outDir>` takes the home page, its full height, the open menu and both phone widths
in one run and prints console errors; it needs the toolchain from
`tools/render_env.py`.

The site has one palette -- an indigo field with a light band -- and no theme switch
on any page. Every page links one stylesheet, `docs/assets/site.css`: it carries the
`@font-face` block, the band, the pull-down menu and the shared type, and its
`url()` paths are relative to that file so pages at any depth can use it. The band
keeps frame 6-27's measured geometry -- 26px page name on the left inside a 40px
tap target, the 45px mark centred, two 17px lines and the burger on the right --
while the glyphs themselves are Coolicons line icons (MIT), which is why every
page loads `assets/site.js`. Page-specific rules live in a small `<style>` on the page. `docs/icons/index.html` is
generated on the same system out of the two icon templates.

### The app cannot be captured here

Do not spend another session on the emulator. `/dev/kvm` exists but this account is
not in the `kvm` group and `sudo` fails with the "no new privileges" flag, so the
emulator runs under TCG. That path does boot (about 195 s), installs and starts the
app, but `system_server` stays in a permanent "Application Not Responding" state
and every screencap is a flat black frame, at any `wm size`/`density`. Report app
changes as unverified on device rather than illustrating them.

## Project shape

- `docs/` is the deployed static site (Sites manifest `manifest.json`, assets from `docs`).
- The shelf is ten pages, each a folder with an `index.html`: the root index, then
  `tools/`, `icons/` and its two brand folders plus the all-brands sheet at
  `icons/all/`, `fonts/`, `portfolio/`, `download/` and `renders/`. They share
  `assets/site.css` and `assets/site.js`; no page has its own copy of the palette.
  The page name sits on the left of the band beside a chevron control that opens a
  pull-down accordion with four destinations (index, icons, fonts, tools); the
  burger on the right pulls out a side tab with three (download, portfolio,
  renders). `site.js` owns both toggles, and Escape or the scrim closes them.
- `docs/data/` is generated and public on purpose: the site doubles as the database
  behind the apps. `icons.json` and `coolicons.json` come from `tools/fetch_icons.py`; `index.json`,
  `tools.json`, `fonts.json` and `portfolio.json` come from `tools/build_catalog.py`.
- Releases go through `python3 tools/bump_version.py <version> --title "…" --note "…"
  --build`: it moves the catalog, both build cases, every live version label and the
  release-note list, then builds, copies the APK into `docs/`, re-measures size and
  checksum and rebuilds the catalogs. Then it serves `docs/` and reruns
  `tools/render_pages.js`, so the gallery is captured from the pages that quote the new
  build; `--skip-captures` leaves it alone. `--deploy` ends by running `--deploy-cmd`
  if given, otherwise printing the `sites_deploy` request with a fresh requestId from
  `tools/site.json` — there is no deploy CLI in this environment. It reads the history
  first and refuses to bump
  while any note's label is not the catalog version of the commit that added
  it; `--check-notes` runs that check alone. Never replace version strings by hand — a
  blanket find-and-replace once relabelled twelve releases as 2.2, and the notes only
  recovered their real versions by reading the commits again.
- `tools/catalog.json` is the single source of truth for the apps, the fonts and the
  portfolio. `tools/build_catalog.py` re-measures the APK and the font files, copies
  each TTF from `app/res/font/` into `docs/assets/fonts/`, writes the JSON and then
  fails if any page still quotes a stale version, size or checksum. Run it after any
  change to a version number or a shipped file.
- `docs/renders/` is the gallery page plus the captures it lists. The page shows
  only files named in `index.html`, so the folder must hold exactly those.
- `figma/` is the design hand-off: one SVG artboard per page per width, written by
  `tools/figma_export.js` from the pages as Chromium lays them out (text on real
  baselines, a rect per box fragment, ellipsised labels, the CSS-generated arrows
  and step numbers). Figma imports it as editable layers. `site-figma.svg` stacks
  all of them on one canvas (`tools/figma_bundle.py`) and is the file to hand over:
  the app's file panel cannot preview a zip. It is a translation of
  the page, never a hand-drawn one. `tools/figma_publish.py` mirrors the exports
  into `docs/figma/`, which the site does serve, so every artboard can be
  downloaded from a phone at `/figma/<file>`, and writes the listing at `/figma/`.
  Each artboard carries Poppins 400/500/600 as base64 woff2, because a file that
  only names the family renders in the viewer's fallback sans. `figma/` stays the
  source. Re-run the
  exporter after a visual change, then the bundle and the publish step; never edit
  the SVGs.
- `artifacts/` is scratch: captures in progress, Figma probes, old shots. Nothing
  there is deployed, and stale files in it are not evidence of anything.
- The icon pages are generated by `tools/fetch_icons.py` from
  `tools/icons_hub_template.html` (the Icons app landing at `docs/icons/`),
  `tools/icons_brand_template.html` (one page per brand) and
  `tools/icons_all_template.html` (the all-brands sheet at `docs/icons/all/`).
  Edit the templates and regenerate with `python3 tools/fetch_icons.py --pages-only`,
  never the output. Each page is ~27 KB: it loads its brand's catalog at run time
  instead of inlining glyphs, and the APK bundles those same files.
- The left pull-down of all three icon pages opens with All, linking to
  `/icons/all/`: twelve glyphs per brand, one from every category — the first name in
  the catalog for that category. Categories are matched across sets on a plain key
  (case, plural, punctuation), and the ones every set carries come first in one order —
  the average of each vendor's own position — so the sheets line up row for row when
  read down the page; a set short of twelve is topped up with its own categories from
  the start of its catalog. There are no tabs:
  every brand is a section stacked under the one before it, so the page is the list and
  a third set only needs another section. A phone gets the name, its note, the sheet
  four across and three down (about 200px tall) and the link to the whole set; from
  1000px the name and note move to the left of the sheet, which goes six across and two
  down, so a brand reads as one row. Do not widen the cells, raise the cap or bring
  back a one-brand-at-a-time control without rechecking that a section still fits with
  no sideways scroll.
- Every vendor glyph carries its own `stroke-width` attribute, and a presentation
  attribute beats a value inherited from the `<svg>`. The preview therefore writes
  the chosen weight onto each element (`tune()` in the template), not onto a parent.
  Setting it in CSS on the svg looks correct and does nothing.
- Both sets carry their vendor's own categories: `fetch_icons.py --categories` merges
  Coolicons' `icons/data/index.json` and Lineicons' public search index into the two
  catalogs as `cat`, keeps each vendor's order in `cat_order`, and the sheet's picker
  renders them. The four older Lineicons files the vendor has not categorised show
  under All icons only; never invent a bucket for them.
- A closed subpath no bigger than the stroke turns inside out: the renderer leaves a
  pinhole where the stroke meets itself (Coolicons' 1-unit head showed it at weight 2,
  and again at 2.5). `solidDots()` splits those subpaths out of the preview and fills
  them, so dots stay solid at every weight; the file itself is never touched.
- APK builds go through `build.sh` into `dist/`; released APKs are committed inside
  `docs/`. `dist/` keeps older builds as history, `docs/` ships only the current one.
  `./build.sh` builds both, `./build.sh icons` or `./build.sh compress` builds one.
  The Icons build stages its web bundle out of `docs/` into `icons-app/assets/`, which
  is generated and not committed: one copy of every glyph, not two.
- The Icons app is a WebView over that bundle, served from `https://icons.local` by a
  `WebViewClient` reading `assets/www`, with a `JavascriptInterface` bridge for copy
  and for saving a raster. It declares no permissions at all, so a page cannot reach
  the network even if one asked.
- The bundle can be checked without a device, which is the only way to verify an app
  change here: serve `icons-app/assets/www` and drive it in Playwright, as
  `tools/webview-check.md` describes. It proves the shipped files, not the window —
  insets, the clipboard and MediaStore bridge, and back handling stay unverified.
- The WebView sits in a frame padded by the window insets. Target 35 draws edge to
  edge, so without that the band hides under the status bar and, on a Flip 7, under
  the camera cutout.
- The preview exports SVG through the clipboard, or a PNG/WEBP/JPG at the chosen size
  (32 to 1024px) rendered on the chosen background: the site downloads it, the app
  writes it to Pictures/Icons through MediaStore (no permission on Android 10+).
- The Export button under those rows zips every glyph the sheet currently shows, in
  the chosen format and size, as a stored zip written byte by byte in the page; in the
  app the same bytes reach a second bridge method and land in Downloads/Icons.
- Double-tapping a glyph in the sheet collects it into the selection tab above the
  sheet, which shows the collected glyphs, Remove selected (the previewed one) and
  Remove all. Add all in the sheet's bar collects exactly what the filter and category
  show. While a selection exists Export packs it instead of the sheet's view; changing
  category clears a selection that Add all filled, so each category exports by itself.
- A line under the preview estimates the zip: an exact SVG total, or four glyphs
  sampled across the list and scaled for a raster, with the icon count and export size.
- Format and export size are both pull-downs rather than rows of segments: four
  formats and six sizes (32 to 1024px) inside listboxes, borrowing the category
  picker's menu. Only one list is open at a time. The chosen format
  and size are remembered per brand in `localStorage` (`icons:format:<slug>`,
  `icons:export:<slug>`, `icons:surface:<slug>`) the same way the category is.
- On the site a raster Copy downloads the file and puts a PNG on the clipboard too, so
  it pastes straight into a design tool; a WEBP or JPG copy is redrawn as PNG for the
  clipboard, which takes no other image type.
- A page the bundle does not carry shows the address of the same page on the site and
  an Open on the web button; the `WebViewClient` hands any http(s) link to the browser.
- Compress's palette has one source of truth: `app/res/values/colors.xml` (Icons has
  its own in `icons-app/res/values/styles.xml`). Names are kept from the old theme on
  purpose, so a rename touches every drawable, layout and Java file that references
  them.
