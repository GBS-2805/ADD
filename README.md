# Compress — two offline Android tools and the design shelf they sit on

Two small Android apps that work with no network at all, plus the static site that
is both their documentation and their database. The photo app's manifest and the
icon app's manifest both declare **no `INTERNET` permission**, so they physically
cannot upload anything or phone home.

The site is a general design shelf: `docs/` carries the icons, the fonts, the work
and the downloads, all served from one palette and all published as JSON alongside
the pages.

## Status

- **Icons 2.20** — APK [`dist/Icons-2.20.apk`](dist/Icons-2.20.apk)
  (2,064,041 bytes, signed v1+v2), published as `docs/Icons-2.20.apk`. Package
  `app.offline.icons` · minSdk 26 · targetSdk 35 · versionCode 31. The whole browser
  — all four brands, 4,373 glyphs, the stylesheet and Poppins — is bundled in the APK
  and served to a WebView from a private origin.
- **Compress 2.1** — APK [`dist/Compress-2.1.apk`](dist/Compress-2.1.apk)
  (272,592 bytes, signed v1+v2, round adaptive icon), published as
  `docs/Compress-2.1.apk`. Package `app.offline.compress` · versionCode 12.
- **Icons browser** — live at `docs/icons/`: four free brands — 606 Lineicons,
  1,258 Coolicons, 1,512 Phosphor and 997 Streamline — each on its own page with a preview, variant controls and the
  set folded into a scrolling sheet. Copy-on-tap, no account.
- Theme: **one palette**, taken from the Figma frame `6-27` — an indigo field
  (`#3F4E96`), a light band (`#D9D9D9`) that also fills every call to action, black
  ink on the band, light text on the field, and hairline rules where the old theme
  used border-plus-shadow cards. Surfaces are flat: `shadow` is fully transparent.
- Type: Poppins, self-hosted. The site serves woff2 from `docs/assets/fonts/`; both
  apps bundle Poppins (Compress as `res/font/`, Icons inside the web bundle).
- **No dark mode**, on the site or in either app. `res/values-night` is deleted and
  the header toggle is gone from the layout and the Java. `ThemePrefs` remains only
  to force night mode off in the system UI.

`dist/` keeps every older build as history; only the current APKs are copied into
`docs/`.

## The shelf

Twelve pages, one stylesheet, no framework:

| Page | What it is |
| --- | --- |
| `docs/index.html` | Index: the material, both apps, the palette, the JSON list |
| `docs/tools/` | Both apps in detail, install steps, a link to the release notes |
| `docs/icons/` | Icons app landing: download, the four brands, what the app does |
| `docs/icons/lineicons/` | Lineicons: preview tab, variants, the set (generated) |
| `docs/icons/coolicons/` | Coolicons: preview tab, variants, the set (generated) |
| `docs/icons/all/` | All brands: one section per brand, twelve glyphs each (generated) |
| `docs/fonts/` | Poppins 400/500/600, specimens, woff2 and TTF downloads |
| `docs/portfolio/` | Three projects, what I built in each, constraints |
| `docs/download/` | Both APKs, sizes, SHA-256, install steps |
| `docs/renders/` | Gallery of captures, nothing drawn by hand |
| `docs/releases/` | Every build, with the day, the APK size and the note written for it |
| `docs/figma/` | The design hand-off, listed so every artboard downloads from a phone |

Pages share `docs/assets/site.css` — palette, band, menus and type — plus
`docs/assets/site.js` for both menus. The band is frame 6-27's geometry with the page
name on the left inside a 40px tap target, the mark centred, and two lines plus the
burger on the right. The chevron opens a four-row pull-down (index, icons, fonts,
tools); the burger pulls out a right-hand side tab (download, portfolio, renders,
releases) On the icon pages that left pull-down lists the **brands** instead, and
each brand row is a link to its page. The glyphs in that chrome are Coolicons line
icons. Pages carry only their own small `<style>` for page-specific pieces.

### The brand pages

Each brand page is built around one idea: pick a glyph, then tune how you want to see
it. The preview sits in its own tab, with a small chip of the same glyph beside the
controls; weight, size and background repaint it live without touching the file. The
whole set lives behind one toggle and scrolls inside its own sheet, so the page is a
preview rather than a wall of 1,258 cells. Copy hands over the untouched markup: a
24×24 `viewBox`, `currentColor`, no baked weight.

### The JSON behind it

The site is also the database the apps read, so the same numbers go to both:

```
docs/data/index.json       the four catalogs, their pages, their counts, the downloads
docs/data/tools.json       both apps: version, package, APK path, bytes, SHA-256
docs/data/icons.json       all 606 Lineicons glyphs with their markup
docs/data/coolicons.json   all 1,258 Coolicons glyphs (the Icons app's database)
docs/data/fonts.json       each Poppins face: weight, woff2 path, TTF path, sizes
docs/data/portfolio.json   the three projects
docs/data/pages.json       every page: path, title, description, section
```

`tools/catalog.json` is the single handwritten source for apps, fonts and portfolio.
`tools/build_catalog.py` measures what actually ships — each APK's bytes and hash, each
font binary — copies every app TTF into `docs/assets/fonts/` so the download cannot
fall behind the app, writes the four JSON files, and then **fails** if a page still
quotes a stale version, size or checksum. Nothing on the shelf is typed twice.

```bash
python3 tools/fetch_icons.py                # fetch every set, write data + pages
python3 tools/fetch_icons.py --pages-only   # rewrite the pages from the catalogs
python3 tools/fetch_icons.py --rechunk      # rewrite the catalogs in chunks, no network
python3 tools/build_catalog.py              # measure, write the catalogs, check the pages
python3 tools/build_catalog.py --check      # check only
python3 tools/build_site.py                 # pages.json, sitemap.xml, robots.txt, search page
python3 tools/build_site.py --check         # fail when any of them is stale
python3 tools/hash_assets.py                # after editing site.css or site.js
python3 tools/hash_assets.py --check        # fail when a page names an old asset
```

## What Icons does

- **Four brands, each whole**: 606 Lineicons, 1,258 Coolicons, 1,512 Phosphor and 500
    Plump Solid, generated from the
  vendors' repositories and recoloured to `currentColor`.
- **A live preview**: the selected glyph in its own tab, with weight (1 → 2.5), size
  (32 → 96) and surface (field or band) applied in the preview only.
- **Categories**: the sets whose vendor publishes one filter by its own taxonomy, beside the
  name filter — Coolicons' 30 groups (System, Arrow, Navigation, Edit, File, ...)
  and Lineicons' 51, merged from the vendors' own data. The four older
  Lineicons files the vendor has not categorised appear under All icons only.
- **Remembers**: each brand keeps the category you last picked and restores it,
  per brand; choosing All icons forgets it.
- **The set folds away**: a brand's glyphs scroll inside a sheet with its own filter
  and count, and the all-brands page stacks every brand as a section — twelve glyphs
  each, drawn from the categories the sets share — so all 4,373 stay reachable
  without a wall of cells.
- **Copy the file**: the exact SVG, with a textarea fallback, and inside the APK a
  Java bridge that puts it on the clipboard.
- **Offline**: the app has no `INTERNET` permission; the bundle is served from
  `https://icons.local` by a `WebViewClient` that reads `assets/www`.

## What Compress does

- Pick up to 40 photos through the system document picker (no storage permission
  needed on Android 10+).
- Quality slider (40–95, retried downward in steps of 12 until the encode is actually
  smaller than the source).
- Longest-edge cap: Original / 1280 / 1920 / 2560 / 3200 / 4096 px, with
  `inSampleSize` sub-sampling plus one bilinear scale pass.
- JPEG or WebP output (`WEBP_LOSSY` on API 30+, legacy `WEBP` below).
- EXIF orientation baked into the pixels; other metadata (GPS, device tags) dropped.
- Results saved to `Pictures/Compress` via MediaStore (`IS_PENDING` flow), with
  per-file savings, a "no gain" badge and single/all sharing.
- **Preview runs before compression.** `Compressor.trial()` encodes the first photo
  into the cache without publishing; `compress()` is `trial()` + MediaStore publish,
  so preview and saved file come from the same path.

## Source layout

```
app/AndroidManifest.xml                            manifest (no INTERNET permission)
app/src/app/offline/compress/Compressor.java       decode → scale → rotate → encode → publish
app/src/app/offline/compress/MainActivity.java     picker, settings, trial preview, queue, results
app/src/app/offline/compress/CompareActivity.java  before/after screen
app/src/app/offline/compress/ComparisonView.java   draggable comparison surface
app/src/app/offline/compress/SelectionStore.java   keeps the picked set across activities
app/src/app/offline/compress/ThemePrefs.java       forces night mode off (one palette)
app/res/                                           27 resources: 3 layouts, 14 drawables,
                                                   Poppins, one values/ (no values-night)
icons-app/AndroidManifest.xml                      Icons manifest (no permissions at all)
icons-app/src/app/offline/icons/MainActivity.java  WebView, asset origin, clipboard bridge
icons-app/res/                                      one theme, one vector launcher icon
icons-app/assets/                                   staged at build time from docs/ (not committed)
build.sh                                           SDK-tools build: ./build.sh [all|stage|compress|icons]
docs/                                              the eleven-page static shelf + data/
tools/catalog.json                                 apps, fonts, portfolio (one source)
tools/build_catalog.py                             measures the shipped files, writes data/
tools/build_releases.py                            writes the releases page from git history
tools/release_notes.json                           the release notes: one copy, newest first
tools/fetch_icons.py                               the icon pages + the chunked catalogs
tools/icons_hub_template.html                      the Icons app landing page
tools/icons_brand_template.html                    the per-brand preview page
tools/render_pages.js                              captures every page and writes the gallery
tools/build_site.py                                page manifest, sitemap, robots, search page
tools/search_template.html                         the search page: section chips, arrow keys
tools/hash_assets.py                               hashed copies of the CSS and JS every page names
tools/deploy_vercel.py                             deploys docs/ to Vercel, verifies it, moves the links
manifest.json, worker.js                           Sites deployment (fallback host)
```

## Build

No Gradle, no AndroidX, no downloads beyond the SDK itself.

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_SDK_ROOT=$HOME/android-sdk   # needs build-tools;35.0.0 + platforms;android-35
./build.sh            # both APKs
./build.sh stage      # the browser bundle only: no JDK or SDK needed
./build.sh icons      # Icons only: stages docs/ into icons-app/assets, then builds
./build.sh compress   # Compress only
```

Output: `dist/Compress-2.1.apk` and `dist/Icons-2.20.apk`. Copy them to `docs/` and
re-run `python3 tools/build_catalog.py` so the pages, the JSON and the checksums move
together. Signing uses `$HOME/tools/compress-keystore.jks` (created on first build,
password `android`); both apps share it, so they update side by side. Replace it with
your own key for anything beyond personal sideloading.

## Screenshots: captures only

The site carries exactly one kind of image: a screenshot of a page that actually ran
in a headless browser. The gallery at `docs/renders/index.html` lists those captures,
the folder holds nothing else, and `render_pages.js` writes the gallery from its own
shot list — so a listed file cannot go missing and an orphan cannot survive a run.

```bash
python3 tools/render_env.py                        # rebuild Chromium + libs in /tmp (~4 min)
python3 -m http.server 8765 --directory docs &
node tools/render_pages.js http://127.0.0.1:8765/ artifacts docs/renders
```

`render_pages.js` shoots every page in one run — the index at its fold and full
height, the left pull-down and the right side tab, tools, the Icons app page, the
Coolicons preview, the open set and a variant, fonts, portfolio and download at
1300×900, then the index, its two menus and both icon pages at 390×844 — and prints
console errors and horizontal overflow.

**Neither Android app can be captured on this machine**, so the gallery shows no app
screens. `/dev/kvm` exists but the account here is not in the `kvm` group and `sudo`
is blocked by `no_new_privs`, so the emulator falls back to software emulation; there
it boots, installs and starts the apps fine, but Android's `system_server` sits in a
permanent "Application Not Responding" state and every screenshot comes back as a
flat black frame. Earlier versions of this README shipped screens drawn from the
layout's `dp` values; those were removed along with the renderer that produced them.

## The address is rented, not owned

The site is live at `https://artbase-olive.vercel.app`, a subdomain of `vercel.app`.
That name is the host's, not this project's: free, auto-renewing, served over a
certificate Vercel manages, and therefore not something that can be sold, lost to a
missed renewal or transferred. The trade-off is that it is not yours either — renaming
the Vercel project changes the subdomain, and both apps have the current one compiled
in, so the slug stays as it is.

A domain of this project's own would cost roughly $12–13 a year and would be a rental,
not a purchase: the registry reclaims the name if a renewal is missed. Nothing on the
site needs one — hosting, HTTPS and any subdomain of a domain already owned are free.
If one is ever bought, moving is one command:

```bash
python3 tools/deploy_vercel.py --links-only https://your-domain.example
```

It verifies every route on the new host before rewriting `tools/site.json`, both app
constants and the docs, so a name that does not answer changes nothing. The APKs then
need a version bump to carry the new host. `AGENTS.md` has the full sequence.

## Not yet done

- **Icons has not been run on a device.** The bundle, the manifest and the signing
  are verified; the WebView surface, the clipboard bridge and the private origin are
  reviewed code, not observed behaviour. Compress has never been exercised on a
  physical device either: verified resource/link compilation, javac type checking,
  dex packaging, zipalign, v1+v2 signature verification, `resources.arsc` stored
  uncompressed for targetSdk 30+ installs, and a byte-exact download from the
  published page.
- The restyle itself is unconfirmed on hardware: colours, Poppins metrics and the
  hairline spacing have only been checked as resource values and layouts.
- HEIC input depends on the device decoder (`BitmapFactory`); output is JPEG/WebP.
- EXIF flip orientations (`FLIP_HORIZONTAL`/`FLIP_VERTICAL`) are treated as normal.
- The comparison slider and preview strip are compiled and reviewed but have not
  been exercised on a device.
