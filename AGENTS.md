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
- Standalone artwork (SVG marks, icons) was rendered apart from the page with
  `@resvg/resvg-js`, which is not part of the current toolchain. The one such
  capture, `logo-light`, is therefore kept rather than re-shot: the run fails if its
  published file is missing, because the gallery lists it, and re-render the mark by
  hand if `assets/logo.svg` changes.
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

## Artifacts are also kept in Drive

Every release refreshes two copies in Google Drive, under **ArtBase** — `figma/` and
`renders/`. The site keeps serving both; Drive is where they are opened from a phone
or handed to someone else. Folder ids live in `tools/site.json` under `drive`; read
them from there rather than searching Drive by name, which also finds unrelated
`renders` folders.

- **`figma/`** holds the 25 files (24 artboards plus `site-figma.svg`). Nothing is
  overwritten: clear `figma/old`, move last release's current files into it, then
  upload the new set. `old` is one generation deep, so an overwritten artboard is
  recoverable but two releases back is not.
- **`renders/`** is replaced in place. Upload the new captures, then delete every
  file that is not in the new set. There is no render history on purpose: a
  screenshot of a page that has since changed is worse than no screenshot.
- Upload from the published site (`/figma/<name>`, `/renders/<name>`) with
  `GOOGLEDRIVE_UPLOAD_FROM_URL`, so the bytes are the same ones the pages serve, and
  check the returned size against the file on disk.

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

- `docs/` is the deployed static site, live at **https://artbase-olive.vercel.app**
  (Vercel project `artbase`; the address is rented, not owned, so see **The address is
  rented, not owned** at the end of this file). Deploy with
  `python3 tools/deploy_vercel.py`: it uploads `docs/`, checks every route on the new
  host, and only then rewrites the origin in
  `tools/site.json` and the two app constants, so a failed deploy leaves the repo
  pointing at the host that still serves. The token is the `VERCEL_TOKEN` secret, read
  from the environment and never an argument. Vercel has no per-deploy size cap.
  Sites (`manifest.json`, `tools/site.json`) remains a fallback and **caps a deploy at
  10 MiB for the whole folder**, refusing an oversize one with `invalid_type`, an error
  that never mentions size -- so if a Sites deploy fails for no visible reason, check
  the budget first. `python3 tools/figma_publish.py` prints the headroom on every
  run. Captures ship as lossless WebP (`tools/to_webp.py`,
  wired into `tools/render_pages.js`): same pixels, a third of the bytes. The one canvas
  keeps each artwork payload once in `<defs>` and paints instances with `<use>`.
- The shelf is twelve pages, each a folder with an `index.html`: the root index, then
  `tools/`, `icons/` and its two brand folders plus the all-brands sheet at
  `icons/all/`, `fonts/`, `portfolio/`, `download/`, `renders/`, `figma/` (written by
  `figma_publish.py`) and `releases/`. They share
  `assets/site.css` and `assets/site.js`; no page has its own copy of the palette.
  The page name sits on the left of the band beside a chevron control that opens a
  pull-down accordion with four destinations (index, icons, fonts, tools); the
  burger on the right pulls out a side tab with four (download, portfolio,
  renders, releases). `site.js` owns both toggles, and Escape or the scrim closes them.
- `docs/data/` is generated and public on purpose: the site doubles as the database
  behind the apps. `icons.json` and `coolicons.json` come from `tools/fetch_icons.py`, which
  also writes `<brand>-sheet.json` for the all-brands page: a glyph per category plus the
  set's category list, so that page fetches ~90 KB instead of both full catalogs (1.67 MB);
  `index.json`,
  `tools.json`, `fonts.json` and `portfolio.json` come from `tools/build_catalog.py`.
- Releases go through `python3 tools/bump_version.py <version> --title "…" --note "…"
  --build`: it moves the catalog, both build cases and every live version label, adds
  the new note to `tools/release_notes.json`, then builds, copies the APK into `docs/`,
  re-measures size and checksum and rebuilds the catalogs. Then it serves `docs/` and reruns
  `tools/render_pages.js`, so the gallery is captured from the pages that quote the new
  build; `--skip-captures` leaves it alone. `--commit` makes the two commits a
  history row needs -- the release, then the page naming its sha -- and refuses to
  start on a worktree with changes already in it, so a release never carries work
  that is not part of it. `--deploy` ends by running `--deploy-cmd`
  if given — for a Vercel release, `--deploy-cmd "python3 tools/deploy_vercel.py"` —
  otherwise printing the `sites_deploy` request with a fresh requestId from
  `tools/site.json`. It reads the history
  first and refuses to bump
  while any note's label is not the catalog version of the commit that added
  it; `--check-notes` runs that check alone. `--dry-run` writes nothing and prints
  the plan instead: the labels, a diff of each file, the artboards and captures
  that go stale with those pages, and the deploy headroom. Never replace version
  strings by hand — a blanket find-and-replace once relabelled twelve releases as
  2.2, and the notes only recovered their real versions by reading the commits again.
- `tools/catalog.json` is the single source of truth for the apps, the fonts and the
  portfolio. `tools/build_catalog.py` re-measures the APK and the font files, copies
  each TTF from `app/res/font/` into `docs/assets/fonts/`, writes the JSON and then
  fails if any page still quotes a stale version, size or checksum. Run it after any
  change to a version number or a shipped file.
- `docs/renders/` is the gallery page plus the captures it lists. The page shows
  only files named in `index.html`, so the folder must hold exactly those.
- `docs/releases/` is the history page: every build the repository has shipped, with
  the day it went out, the size of the APK blob at that commit and its short sha, read
  out of git by `tools/build_releases.py` rather than typed into a table. The notes
  come from `tools/release_notes.json`, their only copy -- the tools page links here
  instead of keeping a second list. Regenerate with `python3 tools/build_releases.py`
  -- a version bump runs it before it finishes, and a deploy runs it before it
  uploads but refuses to upload a page that differs from HEAD, because a page read
  out of git can only carry a build once that build is committed, which is why a
  bump with `--commit` commits the release and then the page naming it. Never edit
  the output.
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
  the SVGs. The exporter fails quietly -- the previous files simply stay -- so both
  the bundler and the publisher stop when the newest page is newer than the newest
  artboard, which is the one signal that the export did not run.
- `artifacts/` is scratch: captures in progress, Figma probes, old shots. Nothing
  there is deployed, and stale files in it are not evidence of anything.
- The icon pages are generated by `tools/fetch_icons.py` from
  `tools/icons_hub_template.html` (the Icons app landing at `docs/icons/`),
  `tools/icons_brand_template.html` (one page per brand) and
  `tools/icons_all_template.html` (the all-brands sheet at `docs/icons/all/`).
  Edit the templates and regenerate with `python3 tools/fetch_icons.py --pages-only`,
  never the output. Each page is ~27 KB: it loads its brand's catalog at run time
  instead of inlining glyphs. The Icons bundle stages one catalog and one sheet per brand -- `icons.json`
  and `coolicons.json` for the brand pages, `lineicons-sheet.json` and
  `coolicons-sheet.json` for the all-brands sheet, which fetches the sheets  rather than the full catalogs. Miss one and that page renders empty sections inside the
  app while still perfect on the site, which is what the 2.5 build shipped and the next
    release fixed. That list is derived from the brand table (`--data-files`), so `build.sh` runs
    `tools/check_bundle.py` over the staged pages and fails the build if a
    reference any of them makes does not resolve inside the bundle.
- Brands are declared in one table in `tools/fetch_icons.py`: slug, display name,
  source, licence and where its catalog lives. Lineicons, Coolicons and Phosphor
  are fetched (Phosphor's regular weight, 1,512 outline icons, from its npm
  package); Ziia's two Geometric Shapes sets have no public source and are read
  from exported SVGs in `icon-src/geometric-shapes-1/` and `-2/`, each brand
  appearing as soon as its folder holds files. A brand with no catalog yet is
  skipped rather than failing the run, so the pages stay whole while one is still
  being exported.
- The left pull-down of all three icon pages opens with All, linking to
  `/icons/all/`: twelve glyphs per brand, one from every category — the first name in
  the catalog for that category. Categories are matched across sets on a plain key
  (case, plural, punctuation), and the ones every set carries come first in one order —
  the average of each vendor's own position — so the sheets line up row for row when
  read down the page; a set short of twelve is topped up with its own categories from
  the start of its catalog. There are no tabs:
  every brand is a section stacked under the one before it, so the page is the list and
  a new set only needs another section. A phone gets the name, its note, the sheet
  four across and three down (about 200px tall) and the link to the whole set; from
  1000px the name and note move to the left of the sheet, which goes six across and two
  down, so a brand reads as one row. Do not widen the cells, raise the cap or bring
  back a one-brand-at-a-time control without rechecking that a section still fits with
  no sideways scroll.
- Every vendor glyph carries its own `stroke-width` attribute, and a presentation
  attribute beats a value inherited from the `<svg>`. The preview therefore writes
  the chosen weight onto each element (`tune()` in the template), not onto a parent.
  Setting it in CSS on the svg looks correct and does nothing.
- All three sets carry their vendor's own categories: `fetch_icons.py --categories`
  merges Coolicons' `icons/data/index.json`, Lineicons' public search index and
  Phosphor's `IconCategory` (its own `phosphor-icons/core` repo: `src/types.ts` for the
  names and order, `src/icons.ts` per icon) into the catalogs as `cat`, keeps each
  vendor's order in `cat_order`, and the sheet's picker renders them. The four older
  Lineicons files the vendor has not categorised show under All icons only; never
  invent a bucket for them.
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

## The address is rented, not owned

`https://artbase-olive.vercel.app` is a subdomain of `vercel.app`, so the name is the
host's, not the project's. Nothing about it is paid for: hosting, the certificate and
every route cost nothing and renew themselves. Two consequences:

- **Do not rename the Vercel project.** The slug `artbase` is what sets the subdomain,
  and both shipped APKs carry the current host in `classes.dex`, so a rename points
  every installed copy at a host that no longer answers. Rename the display name
  instead, which changes nothing.
- **Any domain ever bought here is a yearly rental.** A registry takes the name back
  when it lapses; `.com` and `.dev` were both around $12-13/year when this was written,
  and subdomains of a domain already owned are free. Buying is optional: nothing above
  expires or degrades without one.

When a domain does exist, the move is already scripted and needs no new tooling:

1. Attach it -- `npx vercel domains add <domain> <project>`, or buy it in the Vercel
   dashboard, which wires its own DNS. At another registrar, an apex `A 76.76.21.21`
   plus `CNAME www -> cname.vercel-dns.com`. Vercel keeps the `*.vercel.app` address
   serving alongside it, so nothing breaks while DNS propagates.
2. Once it resolves, `python3 tools/deploy_vercel.py --links-only https://<domain>`.
   It verifies every route on the new host *before* rewriting `tools/site.json`, both
   app constants and this file, so a wrong name changes nothing.
3. The APKs still embed the `vercel.app` host. Repointing them at the new one needs a
   version bump (`tools/bump_version.py ... --build`) and a re-capture, because the
   version labels are visible ink.
