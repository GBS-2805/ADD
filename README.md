# Compress — offline tool apps and the shelf they live on

Two small Android apps that work with no network at all, plus the static site that
acts as their database. The photo app's manifest deliberately declares **no
`INTERNET` permission**, so it physically cannot upload anything.

`docs/` is the shelf: tools, icons, fonts, portfolio and downloads, all served from
one palette and all published as JSON alongside the pages.

## Status

- **Compress 1.9** — APK [`dist/Compress-1.9.apk`](dist/Compress-1.9.apk)
  (272,592 bytes, signed v1+v2, round adaptive icon), published as
  `docs/Compress-1.9.apk`. Package `app.offline.compress` · minSdk 26 · targetSdk 35
  · versionCode 10 · versionName 1.9.
- **Icons** — in build. The web browser it wraps is live at `docs/icons/`: 606 free
  Lineicons glyphs, copy-on-tap, no account.
- Theme: **one palette**, taken from the Figma frame `6-27` — an indigo field
  (`#3F4E96`), a light band (`#D9D9D9`) that also fills every call to action, black
  ink on the band, light text on the field, and hairline rules where the old theme
  used border-plus-shadow cards. Surfaces are flat: `shadow` is fully transparent.
- Type: Poppins, self-hosted. The site serves woff2 from `docs/assets/fonts/`; the
  app bundles `res/font/poppins_{regular,medium,semibold}.ttf`.
- **No dark mode**, on the site or in the app. `res/values-night` is deleted and the
  header toggle is gone from the layout and the Java. `ThemePrefs` remains only to
  force night mode off in the system UI.

`dist/` keeps the older builds (1.3 through 1.9) as history. Only the current APK is
copied into `docs/`.

## The shelf

Seven pages, one stylesheet, no framework:

| Page | What it is |
| --- | --- |
| `docs/index.html` | Index: the four catalogs, the JSON list, the download |
| `docs/tools/` | Both apps, Compress in detail, install steps, release notes |
| `docs/icons/` | All 606 free Lineicons, filter and copy (generated) |
| `docs/fonts/` | Poppins 400/500/600, specimens, woff2 and TTF downloads |
| `docs/portfolio/` | Three projects, what I built in each, constraints |
| `docs/download/` | Standalone APKs, sizes, SHA-256, install steps |
| `docs/renders/` | Gallery of captures, nothing drawn by hand |

Pages share `docs/assets/site.css` — palette, band, tab strip and type — and carry
only their own small `<style>` for page-specific pieces. The tab strip appears on
every page with `aria-current` on the one you are reading.

### The JSON behind it

The site is also the database the apps read, so the same numbers go to both:

```
docs/data/index.json       the four catalogs, their pages, their counts, the downloads
docs/data/tools.json       both apps: version, package, APK path, bytes, SHA-256
docs/data/icons.json       all 606 glyphs with their markup (the Icons app's database)
docs/data/fonts.json       each Poppins face: weight, woff2 path, TTF path, sizes
docs/data/portfolio.json   the three projects
```

`tools/catalog.json` is the single handwritten source for apps, fonts and portfolio.
`tools/build_catalog.py` measures what actually ships — the APK's bytes and hash, each
font binary — copies every app TTF into `docs/assets/fonts/` so the download cannot
fall behind the app, writes the four JSON files, and then **fails** if a page still
quotes a stale version, size or checksum. Nothing on the shelf is typed twice.

```bash
python3 tools/fetch_icons.py       # icon page + data/icons.json (needs the network)
python3 tools/build_catalog.py     # measure, write the catalogs, check the pages
python3 tools/build_catalog.py --check   # check only
```

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
build.sh                                           SDK-tools build (aapt2 + javac + d8 + apksigner)
docs/                                              the seven-page static shelf + data/
tools/catalog.json                                 apps, fonts, portfolio (one source)
tools/build_catalog.py                             measures the shipped files, writes data/
tools/fetch_icons.py                               icon page + data/icons.json
tools/icons_template.html                          the only place to edit the icons page
tools/render_pages.js                              captures every page at desktop + phone
manifest.json, worker.js                           Sites deployment
```

## Build

No Gradle, no AndroidX, no downloads beyond the SDK itself.

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_SDK_ROOT=$HOME/android-sdk   # needs build-tools;35.0.0 + platforms;android-35
./build.sh
```

Output: `dist/Compress-1.9.apk`. Copy it to `docs/` and re-run
`python3 tools/build_catalog.py` so the download page, the JSON and the checksum move
together. Signing uses `$HOME/tools/compress-keystore.jks` (created on first build,
password `android`). Replace it with your own key for anything beyond personal
sideloading.

## Screenshots: captures only

The site carries exactly one kind of image: a screenshot of a page that actually ran
in a headless browser. The gallery at `docs/renders/index.html` lists those captures,
and the folder holds nothing else.

```bash
python3 tools/render_env.py                        # rebuild Chromium + libs in /tmp (~4 min)
python3 -m http.server 8765 --directory docs &
node tools/render_pages.js http://127.0.0.1:8765/ artifacts
```

`render_pages.js` shoots every page in one run — the index at its fold and full
height, then tools, icons, fonts, portfolio and download at 1300x900, then the index,
icons and download at 390x844 — and prints console errors and horizontal overflow.

**The Android app cannot be captured on this machine**, so the gallery shows no app
screens. `/dev/kvm` exists but the account here is not in the `kvm` group and `sudo`
is blocked by `no_new_privs`, so the emulator falls back to software emulation; there
it boots, installs and starts 1.9 fine, but Android's `system_server` sits in a
permanent "Application Not Responding" state and every screenshot comes back as a
flat black frame. Earlier versions of this README shipped screens drawn from the
layout's `dp` values; those were removed along with the renderer that produced them.

## Not yet done

- Compress has never been exercised on a physical device. Verified: resource/link
  compilation, javac type checking, dex packaging, zipalign, v1+v2 signature
  verification, `resources.arsc` stored uncompressed for targetSdk 30+ installs, a
  byte-exact download from the published page, and an install and launch on an
  Android emulator whose system UI was too broken to photograph.
- The Icons app does not exist yet. Its data does: `docs/data/icons.json`.
- The restyle itself is unconfirmed on hardware: colours, Poppins metrics and the
  hairline spacing have only been checked as resource values and layouts.
- HEIC input depends on the device decoder (`BitmapFactory`); output is JPEG/WebP.
- EXIF flip orientations (`FLIP_HORIZONTAL`/`FLIP_VERTICAL`) are treated as normal.
- The comparison slider and preview strip are compiled and reviewed but have not
  been exercised on a device.
