# Checking the app's WebView bundle without a device

The app cannot be screencapped on this VM: `/dev/kvm` is unusable for this account, the
emulator falls back to TCG, `system_server` sits in a permanent ANR and every screencap
is a flat black frame at any `wm size`/`density`. The bundle underneath the app can
still be checked: the app is a WebView over files staged from `docs/`, so serving those
files and driving them in a browser proves what the app loads.

## 1. Stage the bundle

    ./build.sh stage

`stage_shelf_assets` rebuilds `app/assets/www` from scratch:

- `docs/icons/index.html` with its brand folders and `all/`, plus `docs/fonts/index.html`;
- `docs/assets/site.css`, `site.js`, `favicon.svg`, and `assets/fonts/*.woff2`;
- `docs/data/` whole: the names-first catalogs, their `data/<stem>/` glyph chunks,
  the `-sheet.json` subsets, and the page index the search box reads;
- `docs/search/index.html`, with that index trimmed to the pages the app carries;
- a redirect `index.html` pointing at `icons/`.

About 5.4 MB, generated on every build and never committed. `renders/` and `figma/`
are deliberately absent: they are the capture and artboard images themselves, 3.2 MB
of the APK, and a gallery without its pictures is a grid of broken cells. The app
answers those two addresses with `ShelfActivity`'s "That page lives on the site" page
instead, so the bundle harness does not look for them. Never hand-edit that tree: it
is rewritten on every build.

## 2. Serve it the way the WebView does

    python3 -m http.server 8870 --directory app/assets/www

The document root must be `www`, not its parent. Pages fetch `../../data/coolicons.json`,
which only resolves to `/data/coolicons.json` from that root.

## 3. Drive it

    export NODE_PATH=/tmp/pwshot/node_modules PLAYWRIGHT_BROWSERS_PATH=/tmp/pw
    export LD_LIBRARY_PATH=/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu
    export FONTCONFIG_FILE=/tmp/rootfs/fonts.conf
    node tools/webview-bundle-check.js http://127.0.0.1:8870

`tools/webview-bundle-check.js` opens `/icons/all/` at 390x844 and scrolls every
section into view, because each one fetches its sheet only as it nears the viewport
-- counting before the scroll leaves the last brand reading as twelve reserved cells
with no glyphs. It then requires twelve glyphs in each of the four sections and
checks every menu link, every brand page, every staged sheet, all four Streamline
catalogs and one chunk from each catalog. It then drives `/search/` the way a thumb
would: every section chip has to return the count it advertises, a word has to narrow
the list, ArrowDown has to highlight exactly one hit, Escape has to clear both the
word and the chip, and Enter has to open a page that exists in the bundle. A missing
file, an unloaded Poppins, a failed request or any console error fails the run.

Last it opens `/fonts/`, the bundle's second tool: every family in the staged catalog
has to render with one option per style in its Face pick, a Try button has to move the
preview and still be pressed after a reload, and every woff2 the catalog names has to
be staged. The `ttf` download links are counted rather than required, because the
bundle copies woff2 alone -- a ttf click inside the app is a dead link, and the run
reports those under `fontPage.ttfMissing` instead of hiding them.

A good run, from 2.19 (the shipped build):

    sections: 4 brands, 12 cells each (Lineicons, Coolicons, Phosphor, Streamline)
    poppins loaded: true
    /icons/all/ 200   /icons/lineicons/ 200   /icons/coolicons/ 200   /icons/phosphor/ 200
    /icons/streamline/ 200   /data/streamline-sheet.json 200   /data/streamline-flat.json 200
    /search/ 200   /data/pages.json 200   /data/icons/1.json 200   /data/coolicons/1.json 200
    coolicons All -> /icons/all/ 200
    search: chips [Icons:6], 6 hits, arrows true, escape true, Enter -> /icons/all/ 200
    console errors: none
    bundle: OK

Four sections of twelve cells each -- `fetch_icons.py` caps the sheet at `CAP = 12`
glyphs per brand, one from each category the sets share. The `-sheet.json` lines
matter most: a brand page and the all-brands sheet fetch those, not the full
catalogs, and before 2.6 the bundle carried only the catalogs, so that page
rendered empty sections inside the app while looking perfect on the site.

And check the artifact itself:

    python3 -c "import zipfile;n=zipfile.ZipFile('docs/ADD-3.0.apk').namelist();print([x for x in n if 'www/icons/all' in x or x.endswith('www/icons/index.html')])"

## 4. The unbundled-page fallback

A page the bundle does not carry is answered by `MainActivity.fallbackFor`, which
builds its HTML in Java -- so serving `www` returns a plain 404 and never shows it.
`tools/webview-fallback-check.js` reads the finished strings out of the shipped
`classes.dex`, joins them around a sample address exactly as the client does, and
loads that document in Chromium at 390x844:

    node tools/webview-fallback-check.js docs/ADD-3.0.apk artifacts/fallback-offline.png

`build.sh` runs it after signing ADD, so a release whose fallback went
stale fails instead of shipping. It prints the heading, both links, the background,
the font and any console error, saves the screenshot, and exits non-zero unless the
page says "every brand, their catalogs", carries no "both brands", renders both
links and does not scroll sideways. Without Playwright the build uses `--static`,
which checks the same shipped strings and links but cannot prove the layout -- it
still fails a stale copy.
A good run:

    "title": "That page lives on the site",
    "links": [ ".../download/", "/icons/index.html" ],
    "background": "rgb(63, 78, 150)", "font": "Poppins", "overflowX": false,
    "consoleErrors": [], "fallback: OK"

This proves the shipped copy renders and its two links resolve; which asset maps to
which site path inside the WebViewClient, and the clipboard/MediaStore bridge, still
need a device.

## 5. Release boundary cases

The WebView is deliberately a local bundle, not a browser. Add these to the device
smoke run when one is available:

- an external `https://` link opens the system browser, not the WebView;
- `file://` and `content://` navigation is refused;
- an off-origin subresource gets the blocked empty response and no network traffic;
- `window.IconsApp` is present only while the `icons.local` page is loaded;
- a release build has WebView debugging off, while a debug build may opt in.

The static checks can prove the settings and branches are present; they cannot prove
the native window, bridge or network behaviour without a device.

## What this proves, and what it does not

Proves: the exact HTML, CSS, JS and JSON the APK ships load and work offline — the tabs
build, twelve glyphs per brand with distinct categories, every menu link resolves inside
the bundle, Poppins loads, no console errors. The app declares no network permission, so
nothing can be masked by a remote fetch.

Does not prove: the native window, meaning the inset padding under the status bar and
camera cutout and the band's placement in the frame; the `JavascriptInterface` bridge
(clipboard writes, MediaStore saves of rasters and zips); system back and gesture
handling; install and upgrade behaviour. The origin also differs
(`http://127.0.0.1:PORT` versus `https://icons.local`), which matters only for anything
origin-keyed — nothing currently is.

For those, the honest options are a phone: install the APK, screenshot, and treat the
PNGs as device captures; or a machine with KVM and `adb screencap`.

Last run against the bundle: **Icons 2.19, versionCode 30, 1,841,488 bytes**, sha
`090e2f246d…`, on 2026-11-03. The bundle harness scrolled all four sections in and
found twelve glyphs each (Lineicons, Coolicons, Phosphor, Streamline), every menu
link and every staged catalog at 200, Poppins loaded, no failed request and no
console error; the fallback harness reported `fallback: OK` with both links. The
line records one build, not a standing claim about the current APK.
