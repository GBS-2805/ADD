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
  `page-fonts`, `page-portfolio`, `page-download`, `phone-fonts`, `phone-home-light`,
  `phone-home-full`, `phone-menu`, `phone-drawer`, `phone-icons`,
  `phone-icons-brand` and `logo-light`.. The gallery holds the light mode only, so there are no
  dark variants; the `-light` suffix on some of them is history, not a variant.
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
- To look at captures without publishing anything, set `RENDERS_NO_GALLERY=1` on
  `tools/render_pages.js`: the shots stay in `artifacts/`, `docs/renders/` is left
  alone, and no deploy or release is involved.

### Renders are captures, never drawings

Every image in `docs/renders/` is a screenshot of a page that actually ran. Do not
draw the Android app from its `dp`/`sp` values and do not recreate a screen for the
gallery: a reconstruction reads as a screenshot without being one, and this was
already shipped once and removed.

The app cannot be captured on this VM (see below), so the gallery carries no app
images. When an image stops being a capture, delete the file from `docs/renders/`
and its entry from the gallery in the same change; leave no orphan PNGs behind.

### How renders are sent (learned the hard way)

**A UI change is shown before it ships, and waits for a verdict.** Anything that
alters layout, spacing, colour, type, an icon, or the presence and position of an
element is built in its working state, captured, and put in front of the reader with
something they can open -- the dev-server preview in the Browser panel, or a capture
with a real URL -- *before* the commit, the deploy and the Drive refresh. Then stop and
wait for a thumbs up or down. Do not ship a UI change and show it afterwards: by then
the only useful answer left is "revert it".

A shot sitting under `artifacts/` is not that picture, and neither is a
workspace-relative markdown link to one. It does not resolve on a phone, so it never
counts as showing anything. This has already failed twice: the reader was sent
`artifacts/incoming/*.png` links for a palette change and could see none of them.

So a review ships its pictures from a public host, the same way a release does:

    # captures for the verdict go in artifacts/shots/, with a plain index.html beside them
    npx -y vercel@latest deploy artifacts/shots --prod --yes --project artbase-shots \
        --create-if-missing --token "$VERCEL_TOKEN"

That serves them at `https://artbase-shots.vercel.app/<file>.png`, with an index at `/`,
openable on a phone with no sign-in. `artifacts/shots/` is rebuilt per review, so it never
accumulates. Send, in this order and never as bare URLs:

1. `[Open the site](https://artbase-review.vercel.app/)` -- the working build;
2. `[See the screens](https://artbase-shots.vercel.app/)` -- the shot index;
3. one labelled link per screen, the phone width among them
   (`[Phone side tab, 390 px](https://artbase-shots.vercel.app/phone-drawer-moss.png)`).

Check each shot URL with curl for 200 and an `image/png` content type before sending;
a link that was never fetched is how the artifact paths slipped through.

Put the review build somewhere the reader can open without a sign-in, and send that
link. A throwaway project's *production* URL is public, while its previews are not:

    npx -y vercel@latest deploy docs --prod --yes --project artbase-review \
        --create-if-missing --token "$VERCEL_TOKEN"

That serves the working copy of `docs/` at `https://artbase-review.vercel.app`, leaves
the real project untouched, and answers 200 to a stranger. Point it at the shipped
build -- or delete the project -- once the verdict is in, so the review URL never
lags the site. The two in-app previews are **not** review links: the Freebuff dev
server answers 403 to anyone not signed in, and a preview deployment of the real
project redirects 302 to Vercel SSO. The Browser panel preview stays for poking at a
change interactively. Nothing under `artifacts/` is a delivery at all, since a phone
cannot resolve it.

Once the verdict is yes, the usual chain runs -- captures into `docs/renders/`, the
artboards, the deploy, the Drive refresh -- and the reply that delivers it carries the
site link, the gallery link and labelled links to the captures of every page touched.

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
   `![](...)` embeds built from them — neither resolves outside the desktop app. This
   holds for review shots as much as for shipped renders.
6. Verify before sending: the gallery returns 200, every listed image loads with a
   real `naturalWidth`, and no capture is missing from the page.

## Artifacts are also kept in Drive

Every release refreshes two copies in Google Drive, under **ArtBase** — `figma/` and
`renders/`. The site keeps serving both; Drive is where they are opened from a phone
or handed to someone else. Folder ids live in `tools/site.json` under `drive`; read
them from there rather than searching Drive by name, which also finds unrelated
`renders` folders.

- **`figma/`** holds the current 27 files (26 artboards plus `site-figma.svg`) and only the
  current generation. Upload the new set, then delete every file in the folder that
  is not in it -- old artboards go to Drive's trash, not into an `old/` folder.
  Nested `old/` folders are how three generations once piled up there.
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

The site has two modes over one palette: a green band (`#0fbf92`) in both, an off-black
page (`#131513`) with cream type (`#f2eee3`) in dark, and cream with off-black in light.
A new visitor follows `prefers-color-scheme`; `site.js` swaps `data-theme` on `<html>`
for the toggle it injects into the side tab and remembers the choice under `site:theme`,
so generated pages inherit both. Every page links one stylesheet, `docs/assets/site.css`: it carries the
`@font-face` block, the band, the pull-down menu and the shared type, and its
`url()` paths are relative to that file so pages at any depth can use it. The band
keeps frame 6-27's measured geometry -- 26px page name on the left inside a 40px
tap target, the 45px mark centred, two 17px lines and the burger on the right, and the theme control living in the side
tab rather than the band; a phone drops the descriptor for `ADD` over
`Art • Design • Database` --
while the glyphs themselves are Coolicons line icons (MIT), which is why every
page loads `assets/site.js`. Page-specific rules live in a small `<style>` on the page. `docs/icons/index.html` is
generated on the same system out of the two icon templates.

Three palettes ride over those two modes: green (the original band, `#0fbf92`), and the
two sets from the hand-off — Moss (`#005043` band, `#0e2a24` page, `#e7ddce` words,
`#d6e381` accent) and Navy (`#242d55` band, `#1c1d2f` page, `#a1b0c3` words, `#72b6b4`
accent). `site.js` swaps `data-palette` on `<html>` and remembers the choice under
`site:palette`; the three chips sit at the foot of the side tab, under the note and beside
the theme toggle. The band holds in both themes and only the page turns over, so a palette
is its block of values in `site.css` plus one entry in the `PALETTES` list in `site.js`.

The fourth colour of each set is that palette's accent, and it does two jobs: it fills the
count chips and the live pills (`--accent`, with `--accent-ink` on top, because the accent
is light and the band ink on it measures 1.03:1), and it colours the two small labels on
the band (`MORE` and `Palette`, `--label`). Green has no accent of its own -- its band is
the accent -- so its labels keep the near-black band ink while Moss and Navy get their
lime and teal. Never spend the accent on body copy or on link words: it sits at 1.03:1
against the palette's band words and would read as a second, competing ink.

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
  host -- the list comes from the tree itself (`tools/build_site.py`), so a page or
  capture added to the shelf is checked without being added here -- and only then
  rewrites the origin in
  `tools/site.json` and the two app constants, so a failed deploy leaves the repo
  pointing at the host that still serves. The token is the `VERCEL_TOKEN` secret, read
  from the environment and never an argument. Vercel has no per-deploy size cap.
  Sites (`manifest.json`, `tools/site.json`) remains a fallback and **caps a deploy at
  10 MiB for the whole folder**, refusing an oversize one with `invalid_type`, an error
  that never mentions size -- so if a Sites deploy fails for no visible reason, check
  the budget first. `python3 tools/figma_publish.py` prints the headroom on every
  run. Captures ship as WebP (`tools/to_webp.py`, wired into
  `tools/render_pages.js`): each capture is encoded losslessly and at q82 and the
  smaller file wins, so flat UI stays pixel-exact (lossless is smaller there) and a
  16,000px phone page takes the lossy one. The one canvas
  inlines every mark instead of painting it through `<use>`, which Figma does not
  import: a shared payload came back as a gap in every artboard.
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
  `pages.json` (every page's path, title, description, heading and section), plus
  `docs/sitemap.xml` and `docs/robots.txt`, come from `tools/build_site.py`, which
  scans the pages on disk; its `--check` fails when any of the three is stale, and
  nothing else writes them.
- `docs/vercel.json` is the deployed cache policy: the font files are immutable for a
  year, everything else revalidates. It is not generated and not served as a page.
- `docs/assets/site.<hash>.css` and `.js` are the names the pages actually load,
  written by `tools/hash_assets.py` from `site.css` and `site.js`. Run it after editing
  either source and before the generators -- `fetch_icons.py`, `figma_publish.py`,
  `build_releases.py` and `render_pages.js` pin the hashed name through the same map
  (`docs/data/assets.json`), so a regeneration cannot silently point back at the
  source. `--check` fails on a stale reference and `deploy_vercel.py` refuses to
  upload one, which is what a generator run without this step looks like.
- `docs/search/` is generated by `tools/build_site.py` alongside the index it reads:
  one box and a section filter over `data/pages.json`, arrow keys through the
  results, linked from the side tab on every page by `site.js`. The Icons bundle
  stages it (with `data/pages.json`), so the app keeps the link.
- A brand catalog is a names-first index (`data/icons.json`) plus glyph chunks under
  `data/icons/`. The index carries the count, the category counts and one glyph, so
  the page paints before the markup arrives; `load_catalog()` in `fetch_icons.py`
  merges the chunks back for anything that needs the whole set (the sheets), and
  `check_bundle.py` follows the `chunks` list inside the staged JSON.
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
- `build_catalog.py` also rewrites the numbers the pages state: every "N icons",
  "N-icon" and "in four brands" / "four free sets" phrase in the shelf pages is
  regenerated from the catalogs it counted, meta descriptions included. `docs/releases/`
  is left out on purpose -- its notes are history and keep the numbers they shipped
  with. Never hand-edit a total or a brand count; add the brand and run the generator.
  The two icon pages are not in `COUNT_PAGES`: `fetch_icons.py` writes them from
  tokens and states each brand's own count in prose, so the rewrite would replace
  those numbers with the shelf total.
- `tools/catalog.json` is the single source of truth for the apps, the fonts and the
  portfolio. `tools/build_catalog.py` re-measures the APK and the font files, copies
  each TTF from `app/res/font/` into `docs/assets/fonts/`, writes the JSON and then
  fails if any page still quotes a stale version, size or checksum. Run it after any
  change to a version number or a shipped file.
- `tools/fetch_fonts.py` is the only thing that talks to Google Fonts: it resolves
  Poppins (100-900 with italics) and Redaction 50 (regular, italic, bold) to a latin
  woff2 and a plain TTF, writes them under `docs/assets/fonts/`, and rewrites the
  `fonts` block of `tools/catalog.json` as families of styles. The three Poppins
  styles the apps bundle keep an `app_res` path, and `build_catalog.py` still copies
  those from `app/res/font/`, so the download cannot drift from the APK. The fonts
  page fetches `/data/fonts.json` and shows one family sheet at a time behind a
  family's own face pull-down; a sheet's Try button sets the preview and
  `fonts:preview` remembers
  the choice. Font sizes are generated into the catalog rather than quoted in HTML,
  so `build_catalog.py` checks that page for its catalog link and picker instead.
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
  and step numbers). Figma imports it as editable layers, and every text run keeps its own
  `font-family`, `font-size`, `font-weight` and `fill` -- a run that only inherits
  them from the wrapper group imported as an empty layer, leaving just the blocks. `site-figma.svg` stacks
  all of them on one canvas (`tools/figma_bundle.py`) and is the file to hand over:
  the app's file panel cannot preview a zip. It is a translation of
  the page, never a hand-drawn one. `tools/figma_publish.py` mirrors the exports
  into `docs/figma/`, which the site does serve, so every artboard can be
  downloaded from a phone at `/figma/<file>`, and writes the listing at `/figma/`.
  Each artboard carries Poppins 400/500/600 as base64 woff2, because a file that
  only names the family renders in the viewer's fallback sans. `figma/` stays the
  source. Re-run the
  exporter after a visual change, then the bundle and the publish step; never edit
  the SVGs. `tools/check_figma.py` gates all three: it fails when a text run lacks its
  own family, size or fill, or when a file paints a mark with `<use>`, because Figma
  imports neither and the artboard arrives as bare blocks. The exporter fails quietly -- the previous files simply stay -- so both
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
  The Icons bundle stages every catalog and every sheet the pages can ask for --
  `--data-files` is that list -- because a brand page fetches its catalog (one per
  style for Streamline) and the all-brands sheet fetches the sheets rather than the
  full catalogs. Miss one and that page renders empty sections inside the app while
  still perfect on the site, which is what the 2.5 build shipped and the next release
  fixed. `build.sh` runs `tools/check_bundle.py` over the staged pages and fails the
  build if a reference any of them makes does not resolve inside the bundle.
- Brands are declared in one table in `tools/fetch_icons.py`: slug, display name,
  source, licence and where its catalog lives. Lineicons, Coolicons, Phosphor and
  Streamline are all fetched: Phosphor's regular weight from its npm package, and
  Streamline's free Core set from `webalys-hq/streamline-vectors`, whose tarball
  carries `core/line`, `core/duo`, `core/solid` and `core/flat` -- 997 drawings each,
  CC BY 4.0, with the vendor's own category folders. The paid Pro packs are not
  taken. A brand whose catalog is missing is skipped rather than failing the run, and
  a brand with no repository can instead be read from exported SVGs in
  `icon-src/<slug>/`.
- A style is not a brand. Streamline publishes one catalog per style, fetched on
  demand by the page's Style toggle and kept for the visit. `brand` and `default` in
  those catalogs let `build_catalog.icon_sets()` count the set once (997, not 3,988),
  and `--data-files` stages all four for the app. Line and Solid are recoloured to
  `currentColor`; Duo and Flat keep Streamline's palette, because that palette is
  what makes them different styles. Palette glyphs and Solid carry `tune: false`,
  render with `data-mode="plain"` so no CSS stroke lands on them, and hide the weight
  row. Switching style keeps the previewed name and the collected icons by name,
  drops the ones that style does not have, says how many went, and names the zip
  after the style.
- The Colour row under Background sets the ink: Auto (a circle with an A), white, black,
  indigo, green and red, and Manual (a circle with an M that opens the page's
  own colour dialog: the same swatches as circles, the current choice ringed, and
  three panes behind Custom / CMYK / Wheel -- hex, four CMYK fields, and a
  hue-saturation wheel with a lightness slider -- all ending as one hex string, with
  Cancel/Set) -- seven 26px circles in all, remembered per
  brand (`icons:ink:<slug>`). Never hand the choice to the browser's colour dialog:
  it arrives square, with a "chosen colour" well and a Cancel that cannot be styled. Ink styles take it as
  `currentColor` through `--glyph`, so the preview, the thumbnails, the chips and the
  raster export follow. Duo and Flat are tinted rather than flattened: the darkest tone
  becomes the ink, each lighter tone the same ink at an opacity that keeps their order,
  so the style survives the choice. Auto restores the vendor palette. Never collapse a
  palette glyph to a single colour -- that turns Duo into Solid. Corners come from
  one scale (`--r: 8px`, `--r-sm: 5px` for rows inside a menu); glyph cells and the
  segments inside one row stay square, because they share their rules with the box
  that clips them.
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
- `/icons/all/` carries each sheet's category list inline (`__SETS__`), so the shared
  rows are known before any sheet arrives and every section can load its own sheet
  when it comes within 400px of the viewport (`IntersectionObserver`, one sheet per
  brand). Each fetched sheet is kept in `sessionStorage` under
  `icons:sheet:<rev>:<slug>`, where the revision hashes the sheet files, so a repeat
  visit in the same tab fetches none of them and a release misses the cache once.
  A section reserves its twelve cells while waiting, so scrolling never jumps. Do not
  reintroduce `cache: "no-store"` or a `Promise.all` over every sheet.
- Every vendor glyph carries its own `stroke-width` attribute, and a presentation
  attribute beats a value inherited from the `<svg>`. The preview therefore writes
  the chosen weight onto each element (`tune()` in the template), not onto a parent.
  Setting it in CSS on the svg looks correct and does nothing.
  The weights are chosen on that 24-unit grid, so `tune()` scales the stroke to the
  glyph's own viewBox first: Phosphor draws on 256 and Streamline's Core set on 14,
  where one number cannot describe both and the weight control would look dead. Scale in that one function,
  which the preview, the thumbnails and the raster export all go through.
- Every set but the two Figma exports carries its vendor's own categories now --
  Streamline's are its folder names: `fetch_icons.py --categories`
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
- The preview pane is the black stage itself: the glyph is centred large and one line
  against the bottom-right corner reads the name and its style (`add-1 · Duo`) at
  caption size -- no file label, no hint, no headline. Markup is a button beside Copy SVG
  that opens the snippet in a modal pop-up -- Copy takes it and dismisses, and the
  close button, Escape or a click on the backdrop closes it -- so the icon never
  leaves the screen; there is no Preview tab, because the stage is the preview. The
  pane carries the surface choice, so White repaints the whole stage rather than a
  tile on it.
- The preview exports SVG through the clipboard, or a PNG/WEBP/JPG at the chosen size
  (32 to 1024px) rendered on the chosen background: the site downloads it, the app
  writes it to Pictures/Icons through MediaStore (no permission on Android 10+).
- The Export button zips exactly what the selection holds -- nothing on load, so it
  reads "Nothing to export" until something is collected with a double-tap or Add
  all -- in the chosen format and size, as a stored zip written byte by byte in the
  page; in the app the same bytes reach a second bridge method and land in
  Downloads/Icons. Remove all empties the export with it, because nothing falls
  back to the sheet's view; do not reintroduce such a fallback.
- Double-tapping a glyph in the sheet collects it into the selection tab above the
  sheet, which shows the collected glyphs, Remove selected (the previewed one) and
  Remove all. Add all in the sheet's bar collects exactly what the filter and category
  show. Remove all clears the export with it; changing
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
  The fallback is built in Java, so it is not in the staged bundle: drive it with
  `tools/webview-fallback-check.js`, which reassembles it from `classes.dex` and fails
  unless the copy and both links render. `build.sh` runs it after signing every Icons
  APK, and falls back to its `--static` mode when Playwright is absent.
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
