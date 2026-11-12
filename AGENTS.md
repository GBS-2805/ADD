# Working rules for this repo

## Delivery rule: public links, every time

A visual review is not delivered until the reader can tap it. Every reply that shows
a UI change must include:

1. a clickable public link to the working page;
2. a clickable public link to the capture index;
3. labelled public links to each relevant capture, including phone width.

A Browser-panel reference, a workspace path, an `artifacts/...` path or merely saying
"the preview is open" is not a link. Use `python3 tools/deploy_review.py review` and
`python3 tools/deploy_review.py shots`, verify each URL returns 200 (and `image/png`
for images) before sending, then lead the reply with those links. This is a standing
rule, not a per-request preference.

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
- Save under `artifacts/`, one file per page the change touches. The `shots` array in
  `tools/render_pages.js` is the authority on names: the gallery is generated from it,
  so a capture exists only if it is listed there. The gallery follows the page's own
  mode, which is dark, so there are no light/dark twins; the `-light` suffix on some of
  them is history, not a variant, and `RENDERS_THEME=light` shoots the other mode. One capture per page per width: a full-height shot already contains
  its fold, and a phone shot of a 10,000 px page is taken at the fold rather than
  stitched, because the stitched one cost a third of the gallery's weight.
- Cover every page the change touches, plus mobile width when the change is
  responsive.
- Inspect each render before sending it. If the render shows a layout, colour or
  overflow problem, fix the page and re-render; never ship a screenshot of a bug.
- Report console errors seen while capturing. A render that only proves HTTP 200
  is not a render.
- Nothing is rendered apart from a page any more. The mark had its own capture,
  `logo-light`, shot once with `@resvg/resvg-js`, which is not in the toolchain; it is
  retired, because the mark already sits in the band of every capture.
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

The app's native screens are captures too: `tools/android-render/` hosts the real
activities in Robolectric and draws them through the app's own resources, views and
paint code. Those review renders live in `artifacts/android/`; `docs/renders/` stays
the gallery of site pages. The Shelf and Studio are Chromium WebViews and cannot be
captured this way -- do not draw them. When an image stops being a capture, delete
the file from `docs/renders/` and its entry from the gallery in the same change;
leave no orphan PNGs behind.

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
    python3 tools/deploy_review.py shots

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

    python3 tools/deploy_review.py review

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

### When a copy change is still a visual one

The exemption above is about changes a reader cannot see. Copy that sits *inside* an
existing capture is different: a button label, a figure caption, a kicker, a release
row is ink in the WebP, so changing it makes the shipped capture wrong even when
nothing about the layout moved. Re-shoot the set in that case, and re-export the
artboards with it -- both are transcriptions of the page's own text, which is why a
merge that only reworded pages still left every board and every shot stale.

Two traps in that run:

- the base URL must end in a slash. `render_pages.js` builds `base + page`, so a bare
  base produces `http://127.0.0.1:8792tools/` and the navigation fails.
- `to_webp.py` writes the `.webp` files straight into `docs/renders/`. Do not copy the
  PNGs out of `artifacts/renders/` into that folder: it leaves orphans the gallery
  never lists, and the gallery is the authority on what ships.

## Artifacts are also kept in Drive

Every release refreshes two copies in Google Drive, under **ArtBase** — `figma/` and
`renders/`. The site keeps serving both; Drive is where they are opened from a phone
or handed to someone else. Folder ids live in `tools/site.json` under `drive`; read
them from there rather than searching Drive by name, which also finds unrelated
`renders` folders.

- **`figma/`** holds the current 30 files (28 artboards plus `site-figma.svg` and `pantone.svg`) and only the
  current generation. Upload the new set, then delete every file in the folder that
  is not in it -- old artboards go to Drive's trash, not into an `old/` folder.
  Nested `old/` folders are how three generations once piled up there.
- **`renders/`** is replaced in place. Upload the new captures, then delete every
  file that is not in the new set. There is no render history on purpose: a
  screenshot of a page that has since changed is worse than no screenshot.
- Upload from the published site (`/figma/<name>`, `/renders/<name>`) with
  `GOOGLEDRIVE_UPLOAD_FROM_URL`, so the bytes are the same ones the pages serve, and
  check the returned size against the file on disk.
- There is no way to replace a file in place with the tools available here:
  `GOOGLEDRIVE_UPLOAD_UPDATE_FILE` takes staged bytes (`s3key`), and nothing in this
  environment stages a local file, while `UPLOAD_FROM_URL` only creates -- so a
  same-named upload lands beside the old file as a duplicate. The sequence that works
  is: list the folder with `GOOGLEDRIVE_FIND_FILE` (`folder_id` from
  `tools/site.json`), upload the current set from the published URLs, then
  `GOOGLEDRIVE_TRASH_FILE` every id that was there before. Trash, not delete: it is
  recoverable. That is 54 calls for the two folders at eight per batch, so run it as
  one sweep after the verdict rather than half-way.

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

The site is one printed edition now: review paper (`#f0e7d4`), ink (`#1B1212`) and
plum (`#5b2a4b`), set in Instrument Serif, Spectral and Libre Franklin. There are no
palette chips and no light/dark switch. The final `html[data-theme], html[data-palette]`
block in `site.css` is canonical; `site.js` no longer reads `site:theme` or
`site:palette` and no longer builds the look controls. `tools/sync_edition.py` reads
that block and generates `app/res/values/colors.xml`,
`app/studio-assets/edition.css` and the engine's `EngineEdition.kt`; `build.sh` runs
it before the palette gate, and `check_palette.py` fails when any generated copy is
stale. Never hand-edit those generated files.

Every page links one stylesheet, `docs/assets/site.css`: it carries the `@font-face`
block, the masthead, the pull-down menu and the shared type, and its `url()` paths are
relative to that file so pages at any depth can use it. `site.js` injects the quiet
`ADD` wordmark on pages that do not carry it, adds the Search row, and keeps the
navigation. The old ring `.mark` is hidden on the site; the favicon and app icon keep
it. The header is paper with a red top rule and a black baseline, not a palette band.
Page-specific rules live in a small `<style>` on the page. `docs/icons/index.html` is
generated on the same system out of the three icon templates.

### The app: native screens render; the emulator is a smoke test

The emulator is a smoke test and, at best, an opportunistic capture -- never a renderer
of record. `/dev/kvm` exists but this account is not in the `kvm` group and `sudo`
fails with the "no new privileges" flag, so it runs under TCG. It does boot (124 s
measured on 9 November 2026), installs the APK and starts the app, and it does draw:
the engine has been captured there at 1080x2340 with its own page `#0c1210` and cream
`#f3ebdd` on screen. What makes it unusable as a pipeline is that the outcome varies by
boot. Often the activity aborts before it paints -- `files/engine-boot.txt` stops at
"document, typefaces and photos ready" and the window goes launcher to flat white --
and when it does paint, Filament's `fxaa` shader fails to link or compile and
`FEngine::loop` raises SIGABRT about five seconds after "first frame drawn". Installs
also fail with a `PackageManagerInternal.freeStorage` null pointer until the guest has
settled, `sys.boot_completed` never flips, and `mCurrentFocus` can stay null with an
activity drawn. With the seed 3D gem hidden, the Filament `fxaa` abort does not start;
a saved visible 3D layer can still bring it back.

The engine review now captures its own live windows rather than relying on
SurfaceFlinger: `WindowManagerGlobal.mViews` is drawn with `View.draw` into
`files/device-ui-*.png`, including Compose dialogs as their own windows. That is
runtime proof, not a page render, and it does not replace a physical-phone verdict.
`tools/device_audit.py` packages the READY, live-window, saved-field and export checks
so a candidate can be audited before signing. It never drives the editor UI beyond the
export action.

`am start .../app.add.engine.MainActivity` answers `Activity class ... does not exist`
because that activity is declared `android:exported="false"` and runs in its own
`:engine` process: the shell uid cannot resolve a non-exported component, so the message
means concealment, not a missing class -- the class is there in `classes3.dex` and the
manifest labels it "Engine Lab". Launch it as the app's own uid (`run-as`) or export it
in the review build; never read that message as "the screen is gone".

`tools/emulate.sh` gates on capability rather than a boot flag: `cmd package list
packages` must return packages and `settings` must answer before it launches anything,
and it skips the reinstall when the installed `base.apk` already matches the local
build. Its `engine` path grabs in short back-to-back batches, because the painted window
is only a few seconds wide, and it judges a frame by pixel count, never by the
breadcrumb alone. Its `grab` stages inside a private directory -- the console's
screenshot command takes a directory, and a `*.png.tmp` name used to be read as one and
stall -- checks the PNG signature, and renames into place atomically; a failed grab
prints the console output and returns nonzero. `tools/emulate-grab-test.sh` drives that
function with a fake adb, so its staging, fallback and failure paths are tested without
a guest.

The four-way split this settles on:

- **Site pages -> Chromium.** `tools/render_pages.js` over `docs/`, 1300x900 and
  390x844, about ten seconds for the shelf, with console errors and horizontal
  overflow reported. Deterministic, and the only path that feeds `docs/renders/`.
- **Native screens -> Robolectric.** `tools/android-render/` draws the real activities
  through the app's own resources and audits 72 combinations before it writes
  anything.
- **Emulator -> smoke test** as above, plus an opportunistic engine frame.
- **Engine screen -> one honest capture with its provenance attached**, or a real
  device when one exists. It is the one screen neither of the first two paths can
  reach: Filament needs a compositor Robolectric does not have.

The native screens do render without an emulator. `tools/android-render/` compiles
`app/res` with `aapt2`, hosts the activities in Robolectric with `NATIVE` graphics,
and draws their real content views at 1080x2400. It audits every one of the 72
screen/theme combinations in memory and writes only the nine green-dark
representatives into `artifacts/android/`. It covers Compress, Editor Quick, its
Photo/Presets/Type/Layers/Colour sheets, Compare and Font packs, in all eight themes.
The method is the Android-renders handoff: binary resources,
`android-all-instrumented`, and Skia through the platform runtime. See
`tools/android-render/README.md`:

```bash
bash tools/android-render/setup.sh
bash tools/android-render/render.sh
```

`render.sh` stages the run and replaces `artifacts/android/` only after all 72
combinations pass; a failure keeps the previous set. `manifest.json` is the expected
matrix and names the showcase pair; `review.py` requires exactly the nine showcase
PNGs, a passing `render-audit.json` with 72 checks, and 1080×2400 dimensions before
it rebuilds `artifacts/shots/` from scratch for the public review. The screenshots
are a current set, not an archive, and are never committed.

This verifies drawing, not touch behaviour, and not a WebView. The Shelf and the
Studio tab are Chromium surfaces; Robolectric has no Chromium compositor, so report
those as uncaptured rather than faking a screenshot. On-device interaction remains
unverified.

## Project shape

There is one app. `tools/catalog.json` holds a single tool entry, `build.sh` has a
single `add` case, `bump_version.py` has a single version stream (`APPS = {"add":
"ADD"}`), and the site says "app", not "apps", everywhere outside `docs/releases/`,
where the notes keep the history they shipped with.Icons, Fonts, Compress and the Editor are tools *inside* that app, not apps of their own.

- `docs/` is the deployed static site, live at **https://artbase-olive.vercel.app**
  (Vercel project `artbase`; the address is rented, not owned, so see **The address is
  rented, not owned** at the end of this file). Deploy with
  `python3 tools/deploy_vercel.py`: it uploads `docs/`, checks every route on the new
  host -- the list comes from the tree itself (`tools/build_site.py`), so a page or
  capture added to the shelf is checked without being added here -- and only then
  rewrites the origin in
  `tools/site.json` and the two app constants, so a failed deploy leaves the repo
  pointing at the host that still serves. It runs `tools/check_palette.py` before the
  upload and refuses a palette that drifted, and `tools/check_links.py`, which fails
  when any internal `href` or `src` names a file the shelf does not carry, beside its
  stale-hash and uncommitted-history stops. The deploy token is `VERCEL_ARTBASE_TOKEN` when set,
  otherwise the `VERCEL_TOKEN` secret; the storage report uses `VERCEL_ADMIN_TOKEN`
  or `VERCEL_TOKEN`. Tokens are read from the environment and never an argument.
  Vercel has no per-deploy size cap.
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
- `python3 tools/deploy_storage.py` reports the retained deployment count, target
  split and retention policy for `artbase`, `artbase-handoff`, `artbase-handoff-apk`
  and `artbase-shots`; `deploy_vercel.py` runs it before uploading, so a release that
  would add another full copy shows the result first. It warns above 40 retained
  deployments, above 450 MiB of estimated output as an early warning, above 512 MiB,
  or any retention window over 7 days. A critical count or estimate exits 2, and an unavailable report also stops
  `deploy_vercel.py` (strict gate). `deploy_review.py apk|handoff|shots|review`
  applies the same gate; `deploy_audit.py` exports deletion and retention events.
  `artbase-handoff-apk` serves the review APK from `artifacts/handoff-apk`, so a
  handoff deployment does not carry a 29 MB copy. The size is an estimate from the
  current folder times the retained count; exact Deployment Storage bytes are
  dashboard-only on Hobby. This is an operational guardrail, not an audit trail. See
  `DEPLOYMENT_STORAGE.md` for the token, approval and evidence runbook.
- The shelf is sixteen pages, each a folder with an `index.html`: the root index,
  then `tools/`, `compress/`, `icons/` and its four brand folders (`icons/lineicons/`,
  `icons/coolicons/`, `icons/phosphor/`, `icons/streamline/`) plus the all-brands
  sheet at `icons/all/`, `fonts/`, `portfolio/`, `download/`, `renders/`,
  `figma/` (written by `figma_publish.py`),
  `releases/` and the generated `search/`. They share
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
  --build`: it first runs the push verdict (`tools/check_all.py --visual` — both pixel
  baselines, the whole engine suite and the shelf's gates) and refuses to move anything
  while one is red, `--dry-run` included. Then it moves the catalog's one entry, the
  `add` build case and every live
  version label, adds
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
  it; `--check-notes` runs that check alone. The palette check runs before any
  label moves, so a colour that stopped following the palette fails the release
  itself, not just the build. Once the catalog has moved it refreshes the generated
  icon pages (`fetch_icons.py --pages-only`), which print the version and the APK's
  file name from tokens and would otherwise stop the sweep. `--dry-run` writes nothing and prints
  the plan instead: the labels, a diff of each file, the artboards and captures
  that go stale with those pages, and the deploy headroom. Never replace version
  strings by hand — a blanket find-and-replace once relabelled twelve releases as
  whatever was newest at the time, and the notes only recovered their real versions by reading the commits again.
- `build_catalog.py` also rewrites the numbers the pages state: every "N icons",
  "N-icon" and "in four brands" / "four free sets" phrase in the shelf pages is
  regenerated from the catalogs it counted, meta descriptions included. `docs/releases/`
  is left out on purpose -- its notes are history and keep the numbers they shipped
  with. Never hand-edit a total or a brand count; add the brand and run the generator.
  The two icon pages are not in `COUNT_PAGES`: `fetch_icons.py` writes them from
  tokens and states each brand's own count in prose, so the rewrite would replace
  those numbers with the shelf total.
- A size a reader sees is rounded, never exact: whole MB above a mebibyte, whole KB
  above a kilobyte, plain bytes below that, and no decimals (`2 MB`, `278 KB`,
  `512 bytes`). The rule lives once per language: `human_bytes()` in
  `build_catalog.py`, whose check then looks for that same string in the pages;
  `human()` in `build_releases.py`; whole-number MB in `bump_version.py` and
  `figma_publish.py`; a rounding `bytes()` in the fonts page and `fmtBytes()` in the
  icon templates. The exact count stays in `tools/catalog.json` and `docs/data/`,
  which are the app's data and the checksum's input, not labels. Never hand-type a
  size into a page -- the catalog check fails when the rounded string is missing.
- `tools/catalog.json` is the single source of truth for the apps, the fonts and the
  portfolio. `tools/build_catalog.py` re-measures the APK and the font files, copies
  each TTF from `app/res/font/` into `docs/assets/fonts/`, writes the JSON and then
  fails if any page still quotes a stale version, size or checksum. Run it after any
  change to a version number or a shipped file.
- `tools/fetch_fonts.py` is the only thing that talks to Google Fonts: it resolves
  Poppins (100-900 with italics) and all seven Redaction grades (regular, italic,
  bold each) to a latin woff2 and a plain TTF, writes them under `docs/assets/fonts/`,
  and rewrites the `fonts` block of `tools/catalog.json` as families of styles. The
  three Poppins styles the apps bundle keep an `app_res` path, and `build_catalog.py`
  still copies those from `app/res/font/`, so the download cannot drift from the APK.
  The fonts page fetches `/data/fonts.json` and draws one sheet per family -- name,
  meta, face pull-down -- keeping a family's grades inside that sheet, opened like an
  accordion with the first grade showing while it is closed, and a floating button
  that returns to the opened family's head once that head scrolls away. The typeable
  box in the hero beside the title sets that line in every sheet; the pull-down lists
  the family's styles in their own faces and sets that sheet and the box; every
  sheet's foot carries its own Copy CSS, which copies that sheet's face at the current
  size; `fonts:preview` remembers the text, the size, the face and which families were
  left open. Font sizes are generated into the catalog rather than quoted in HTML, so
  `build_catalog.py` checks that page for its catalog link and picker instead.
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
  the desktop artboards on one canvas (`tools/figma_bundle.py`) and is the file to hand over:
  the app's file panel cannot preview a zip. It is a translation of
  the page, never a hand-drawn one. `tools/figma_publish.py` mirrors the exports
  into `docs/figma/`, which the site does serve, so every artboard can be
  downloaded from a phone at `/figma/<file>`, and writes the listing at `/figma/`.
  Each artboard carries Poppins 400/500/600 as base64 woff2, because a file that
  only names the family renders in the viewer's fallback sans. `figma/` stays the source. `tools/pantone_sheet.py` writes `figma/pantone.svg`, the colour reference: the nearest Solid Coated chip for every solid colour the four palettes declare, two alternatives for each, and the nearest textile TCX, matched against the vendored `tools/pantone-data.json` by CIEDE2000. It reads `site.css`, so a palette change makes `--check` fail until it is re-run; never hand-edit that sheet. Re-run the
  exporter after a visual change, then the bundle and the publish step; never edit
  the SVGs. `tools/check_figma.py` gates all three: it fails when a text run lacks its
  own family, size or fill, or when a file paints a mark with `<use>`, because Figma
  imports neither and the artboard arrives as bare blocks. The exporter fails quietly -- the previous files simply stay -- so both
  the bundler and the publisher stop when the newest page is newer than the newest
  artboard, which is the one signal that the export did not run.
- `artifacts/` is scratch: captures in progress, Figma probes, old shots, and the
  handoff page source under `artifacts/handoff/`. Nothing there is part of the site
  deploy; the handoff is published separately. Stale files in it are not evidence of
  anything.
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
  `./build.sh` builds ADD, and `./build.sh stage` only stages its web bundle -- which
  is how the bundle can be checked here without a device. `stage_shelf_assets()`
  writes it into `app/assets/www` (generated, not committed) and runs
  `tools/check_bundle.py` over it, so a build fails when a staged page names a file
  the bundle does not carry. There is no second app tree to keep in step: the
  pre-merge WebView-only source is gone, and ADD's `app/` is the only Android source.
- ADD is one package: the native compressor (`MainActivity`, `CompareActivity`) and a
  WebView (`ShelfActivity`) over that same bundle, served from `https://icons.local`
  by a `WebViewClient` reading `assets/www`, with the `IconsApp` bridge for copy and
  for saving a raster. The shelf pages are local, but the manifest does request
  `INTERNET`: the native packs screen is the one caller, and the WebView is not
  sandboxed from it.
- The bundle can be checked without a device, which is the only way to verify an app
  change here: serve `app/assets/www` and drive it in Playwright, as
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
  unless the copy and both links render. `build.sh` runs it after signing ADD, and
  falls back to its `--static` mode when Playwright is absent. Neither mode
  needs the capture environment exported by hand: `build.sh` hands the check
  `NODE_PATH`, `PLAYWRIGHT_BROWSERS_PATH`, `LD_LIBRARY_PATH` and `FONTCONFIG_FILE`
  itself, and a value already exported still wins.
- Compress wears the one Review edition, plus the band and the side tab. The site and
  app share the paper, ink and plum tokens. The Android copy is generated from the
  site's final token block into `app/res/values/colors.xml`; nothing in a layout or a
  drawable names a colour resource directly: every one asks for a theme attribute
  declared in `app/res/values/attrs.xml`, and `app/res/values/styles.xml` maps those
  attributes in the single `AppTheme`. The band is `res/layout/band.xml` and the side
  panel `res/layout/drawer.xml`, both included by `activity_main.xml`; `CompareActivity`
  inherits the theme and wears the same colours.
- `app/studio-assets/edition.css` is generated from the same block, and `studio.css`
  maps its old shell variable names onto those tokens. The Studio is a paper proof
  sheet, not a graphite instrument panel.
- `tools/engine-spike/app/src/main/java/app/add/engine/EngineEdition.kt` is generated
  from the same block. The engine is still a separate review APK, but its Compose
  chrome is on the one edition: paper page, ink type, plum selection and actions, and
  the app's functional warning ink for destructive states.
- `python3 tools/check_palette.py` guards all of the above, site, app and engine together. It
  compares the final Review block in `site.css` with `colors.xml` and the generated
  Studio and engine tokens, requires the one theme to set every attribute, holds the band and page
  in that edition, insists the mark on the band wears the band's ink (it is tinted
  `?attr/ink`, so a themed `ink` is what keeps it visible), refuses a literal colour in
  a layout or a `bg_` drawable and a glyph drawn without a tint, and measures contrast
  — 4.5:1 for words, 3:1 for dim words and the side-panel labels. `--browser
  http://127.0.0.1:8799/` adds `tools/check_palette.js`, the half a stylesheet cannot
  prove: it fails when a pull-down paints anything but the page colour, a selected
  control leaves the band, a disabled control looks enabled, the status-bar meta or the
  printed colour codes disagree with the edition. `build.sh` runs the sync and the
  static half before staging, so a drifted edition fails a build without a browser.
- `docs/packs/` is generated by `python3 tools/build_packs.py` from
  `docs/data/fonts.json` and the TTFs under `docs/assets/fonts/`: one flat
  `<slug>-<version>.addpack` zip per family (manifest plus TTFs, every file with its
  byte count and SHA-256) and a `packs.json` listing. There is no page of its own:
  the cards are generated into `docs/download/index.html` between
  `<!-- packs:start -->` and `<!-- packs:end -->`, so the download page is the one
  place that hands out a file. The version is
  derived from the file hashes, so a changed TTF renames the pack, and the zip is
  written with a fixed timestamp so `--check` can prove the folder is current. Each
  zip holds `manifest.json`, a DER ECDSA P-256 `manifest.sig` over those exact bytes,
  and the TTFs; `PacksActivity` fetches the listing and one pack on request, while the
  folder itself is excluded from the APK bundle beside `renders/` and `figma/`. The
  private key stays outside the repo; `tools/pack-signing-public.pem` and the copy in
  `PackKey.java` are committed, so a new key needs an app update.
- The editor is native (`EditorActivity` and `EditorView`): a photo decoded at up to
  2048 px with its EXIF rotation applied, any number of type lines, seven built-in
  presets and the reader's own saved looks (`StyleStore`, in the same preferences
  file), every one of them thumbnailed from the current canvas; a saved look also
  carries every line's colour, tracking, leading, opacity and shadow in order, so a
  whole poster returns in one tap, with an UNDO LOOK chip that stays on the stage
  until the next edit; five live adjustments, and an export to
  `Pictures/Editor` through MediaStore. The colour sheet is a real
  mixer -- a hue/saturation wheel with a brightness bar, 36 swatches, a hex field and
  RGB sliders -- and paints either the selected
  line or the canvas behind the photo; TYPE adds alignment, letter spacing, line
  height, opacity and shadow; PHOTO turns, flips, resets and colours the canvas; the
  layer panel reorders by drag or arrows, hides, duplicates, centres and deletes.
  Every sheet goes through `showDialog()`, which anchors it to the bottom of the
  screen, slides it up with `R.style.SheetAnimation`, and lays it on `bg_sheet` -- the
  same rounded surface as the dock, never the platform's grey box. Its handle and
  heading are the grab zone: pull down to close the sheet, and on Presets and Layers
  pull up to grow the list until the card meets the top of the screen (or tap the
  header to open and close that list). A sheet reopens at the height it was left,
  remembered per sheet in `compress_settings`. `SheetDrag` moves the window, so the
  card slides rather than clips. The colour sheet
  lays its action row under the palette and sizes to its controls, with the wheel and
  its brightness bar beside the hex field and RGB sliders, so every control is on
  screen rather than behind a scroll pane. The dock is that surface behind a `bg_handle`. In the native editor, layer grouping, shared version history and any
  real-time editing are deliberately absent: the app is offline with no accounts or
  sync backend, so they would need a service, not a control. Type is set in the three bundled
  Poppins faces or any face from a downloaded pack. `Font packs` is `PacksActivity`: it
  lists the cached `packs.json`, downloads only on tap, and hands the file to
  `PackStore`, which checks the listing's outer SHA-256, then the manifest signature
  against `PackKey`, then a SHA-256 per entry before installing atomically into
  `filesDir/font-packs/<slug>/<version>/`. The manual `.addpack` picker remains as a
  secondary link for a file already on the phone. The editor's STUDIO tab now opens
  first; it keeps the Studio engine in the same activity: a WebView over
  `assets/www/studio` running vendored Fabric.js 5.3.0 (MIT) with a custom `AddText`
  class (gradient, outline, shadow and blend), fast-drag mode, grouping, photo import
  and a 1080x1350 PNG export to `Pictures/Studio`. The paper edition shell is
  canvas-first: a flat paper masthead, a Move/Type/Style/Texture/Motion bar and a peek
  inspector, all wearing the generated site tokens. `overlays.js` draws the five press materials — newsprint, wheatpaste,
  overexposed ink, halftone and rough canvas — beside the eight atmosphere textures
  (light leak, grain, bokeh, paper, dust, vignette, window shadows and lens flare), and
  the page exposes opacity, blend, shuffle, remove, on-canvas placement handles and
  X/Y/scale/rotation controls; nothing is fetched and no stock asset is bundled. It is
  the first Studio 2 slice, not wired to saved looks, and it is unverified on a device.
- `python3 tools/check_all.py` is the single verdict: palette, asset hashes, internal
  links, site tree, catalog, Figma structure, Pantone sheet, note dating and the staged
  bundle, one line each, all of them run so one pass shows everything. `--browser <url>` adds the
  rendered palette and the menu-focus/ARIA regression; `--artboards` adds the
  re-export diff. Both need Chromium, and `.github/workflows/checks.yml` runs the
  browserless set on every push. The APK build stays local: it needs the JDK, the android-35 platform and the keystore.
- Artboards go stale silently -- a board records nothing about the page it came from,
  `check_figma.py` only proves it imports, and the bundler's timestamp comparison is
  satisfied by one late re-export. `python3 tools/check_artboards.py` therefore
  re-exports the boards into a scratch folder and fails when any file differs from
  `figma/`; it reads `site-figma.svg` and `pantone.svg` as derived and skips them.

## The address is rented, not owned

`https://artbase-olive.vercel.app` is a subdomain of `vercel.app`, so the name is the
host's, not the project's. Nothing about it is paid for: hosting, the certificate and
every route cost nothing and renew themselves. Two consequences:

- **Do not rename the Vercel project.** The slug `artbase` is what sets the subdomain,
  and the shipped APK carries the current host in `classes.dex`, so a rename points
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

## Completion notifications

When a task is finished and verified, send one short ntfy notification to
`https://ntfy.sh/Freebuff-ADD-2805` before the final reply. Say what is ready, the
pass/fail state, and the one public link to open. Do not notify for progress,
intermediate milestones, or while blocked; one notification per finished task.

## Handoff standard

Every review or release handoff is one public page, scannable on a phone, not a pile
of links. The live two-build handoff is **https://artbase-handoff.vercel.app/**
(source: `artifacts/handoff/index.html`), with both the official and editor-review
APKs. Use it as the reference shape.

**Always include that public handoff link in every reply that mentions a build or
review.** Rebuild the page and deploy it before sending if either APK changes:

    python3 tools/deploy_review.py handoff

1. **Status and To do first.** Build, one review link, one APK, what is live, and
   whether anything was committed or deployed. Then say exactly what the reader
   should install, try, and answer, plus what to keep in mind. Say plainly when a
   build is review-only.
2. **Latest and open, side by side.** Three dated changes; three things still
   unverified, waiting, or blocked.
3. **A compact gallery.** Small previews in a grid; full captures open only on tap.
   Every full image stays public and returns `200 image/png`.
4. **Rules and gates together.** Short rules with a reason; every gate named with its
   result. Put long gate notes behind `<details>`. Never present a failed or skipped
   gate as green.
5. **Words, not a footnote.** A visible glossary explains the working vocabulary in
   plain language and says why each term matters here. Assume the reader is new to
   the project, not that they are careless.
6. **Downloadable files by area.** Group installs, source, packs, tools and review
   proof; every listed file links to a working download, with a short note on what it
   is and what it does. Never link a private key or another secret.
7. **Links and inventory, collapsed.** Keep the complete set available, but do not
   print it down the page.

Keep the visible page under about five phone screens, including the To do and Words
sections. No full-height screenshots inline, no repeated prose, and no stale handoff.
A stale handoff is worse than none, and a gallery that only proves HTTP 200 is not a
reviewed screen.
