# Checking the Icons WebView bundle without a device

The app cannot be screencapped on this VM: `/dev/kvm` is unusable for this account, the
emulator falls back to TCG, `system_server` sits in a permanent ANR and every screencap
is a flat black frame at any `wm size`/`density`. The bundle underneath the app can
still be checked: the app is a WebView over files staged from `docs/`, so serving those
files and driving them in a browser proves what the app loads.

## 1. Stage the bundle

    ./build.sh icons

`stage_icons_assets` rebuilds `icons-app/assets/www` from scratch:

- `docs/icons/index.html`, and the folders `lineicons/`, `coolicons/`, `all/`;
- `docs/assets/site.css`, `site.js`, `favicon.svg`, and `assets/fonts/*.woff2`;
- `docs/data/icons.json` and `coolicons.json`;
- a redirect `index.html` pointing at `icons/`.

Fifteen files, tracked in this repository, so a release commit carries them. Never
hand-edit that tree: it is rewritten on every build.

## 2. Serve it the way the WebView does

    python3 -m http.server 8870 --directory icons-app/assets/www

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
checks every menu link, every brand page, every staged sheet and all four Streamline
catalogs. A missing file, an unloaded Poppins, a failed request or any console error
fails the run.

A good run, from 2.19 (the shipped build):

    sections: 4 brands, 12 cells each (Lineicons, Coolicons, Phosphor, Streamline)
    poppins loaded: true
    /icons/all/ 200   /icons/lineicons/ 200   /icons/coolicons/ 200   /icons/phosphor/ 200
    /icons/streamline/ 200   /data/streamline-sheet.json 200   /data/streamline-flat.json 200
    coolicons All -> /icons/all/ 200
    console errors: none
    bundle: OK

Four sections of twelve cells each -- `fetch_icons.py` caps the sheet at `CAP = 12`
glyphs per brand, one from each category the sets share. The `-sheet.json` lines
matter most: a brand page and the all-brands sheet fetch those, not the full
catalogs, and before 2.6 the bundle carried only the catalogs, so that page
rendered empty sections inside the app while looking perfect on the site.

And check the artifact itself:

    python3 -c "import zipfile;n=zipfile.ZipFile('docs/Icons-2.19.apk').namelist();print([x for x in n if 'www/icons/all' in x or x.endswith('www/icons/index.html')])"

## 4. The unbundled-page fallback

A page the bundle does not carry is answered by `MainActivity.fallbackFor`, which
builds its HTML in Java -- so serving `www` returns a plain 404 and never shows it.
`tools/webview-fallback-check.js` reads the finished strings out of the shipped
`classes.dex`, joins them around a sample address exactly as the client does, and
loads that document in Chromium at 390x844:

    node tools/webview-fallback-check.js docs/Icons-2.19.apk artifacts/fallback-offline.png

`build.sh` runs it after signing every Icons APK, so a release whose fallback went
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
