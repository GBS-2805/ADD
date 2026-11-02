# Compress — offline image compressor (Android)

A small, dependency-free Android app that shrinks photos entirely on-device. The
manifest deliberately declares **no `INTERNET` permission**, so the app physically
cannot upload anything.

This is the first module of the "app services offline" series: a local replacement
for web tools like `bgremovefree.com/compress`.

## Status

- APK: [`dist/Compress-1.9.apk`](dist/Compress-1.9.apk) (272,592 bytes / 266 KB,
  signed v1+v2, round adaptive icon), published as `docs/Compress-1.9.apk`
- Download page: [`docs/index.html`](docs/index.html), deployed via Freebuff Sites
- Package: `app.offline.compress` · minSdk 26 · targetSdk 35 · versionCode 10 ·
  versionName 1.9
- Theme: **one palette, taken from the Figma frame `6-27`** — an indigo field
  (`#3F4E96`), a light band (`#D9D9D9`) that also fills every call to action,
  black ink on the band, light text on the field, and hairline rules (`#6772A7`)
  where the old theme used border-plus-shadow cards. Surfaces are flat: `shadow`
  is fully transparent. The only exception is `warn`, because a warning still has
  to read as a warning.
- Type: Poppins, shipped in the APK as `res/font/poppins_{regular,medium,semibold}.ttf`.
- **No dark mode.** `res/values-night` is deleted and the header toggle is gone
  from the layout and the Java. `ThemePrefs` remains only to force night mode off
  in the system UI, so the single palette is what you get whatever the device is
  set to; its `isDark`/`setDark` pair is now unused.
- Drawables are flattened to the new system — 14 of them, hairlines and filled
  bands rather than outlined cards with offset shadows.

`dist/` keeps the older builds (1.3 through 1.8) as history. Only the current APK
is copied into `docs/` for release.

## What it does

- Pick up to 40 photos at once through the system document picker (no storage
  permission needed on Android 10+).
- Quality slider (40–95, retried downward in steps of 12 until the encode is
  actually smaller than the source).
- Longest-edge cap: Original / 1280 / 1920 / 2560 / 3200 / 4096 px, with
  `inSampleSize` sub-sampling plus one bilinear scale pass.
- JPEG or WebP output (`WEBP_LOSSY` on API 30+, legacy `WEBP` below).
- EXIF orientation baked into the pixels so portrait shots stay upright; other
  metadata (GPS, device tags) is dropped by the re-encode.
- Results saved to `Pictures/Compress` via MediaStore (`IS_PENDING` flow), with
  per-file savings, a "no gain" badge when re-encoding does not help, and
  single/all sharing.
- Thumbnail strip of the picked photos renders before compressing (separate
  2-thread executor so previews never queue behind compression jobs), and each
  tile carries a × badge that drops that photo from the selection. A **Clear all**
  chip under the strip empties it in one tap, and turns into **Restore** so the drop
  can be undone.
- Tapping a result opens `CompareActivity`: a draggable before/after slider
  (`ComparisonView`, custom `View`) with the size delta above it and a share
  action.
- **Preview runs before compression.** `Compressor.trial()` encodes the first
  selected photo into the cache without publishing it; `MainActivity` shows that
  against the original with the estimated size, and the primary button on the same
  screen starts the batch. `compress()` is `trial()` + MediaStore publish, so the
  preview and the saved file come from the same code path.

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
manifest.json, worker.js, docs/                    download page and gallery deployment
```

## Build

No Gradle, no AndroidX, no downloads beyond the SDK itself.

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_SDK_ROOT=$HOME/android-sdk   # needs build-tools;35.0.0 + platforms;android-35
./build.sh
```

Output: `dist/Compress-1.9.apk`. Copy it to `docs/` before deploying the page.
Signing uses `$HOME/tools/compress-keystore.jks` (created on first build, password
`android`). Replace it with your own key for anything beyond personal sideloading.

## Screenshots: captures only

The site carries exactly one kind of image: a screenshot of a page that actually
ran in a headless browser. The gallery at `docs/renders/index.html` lists those
captures, and the folder holds nothing else.

```bash
python3 tools/render_env.py                        # rebuild Chromium + libs in /tmp (~4 min)
python3 -m http.server 8765 --directory docs &
node tools/render_pages.js http://127.0.0.1:8765/ artifacts
```

`render_pages.js` shoots the home page at 1300x900, its full height, the open
menu, and the phone widths at 390x844, and prints any console errors. Copy the
keepers into `docs/renders/` and name them in the gallery.

**The Android app cannot be captured on this machine**, so the gallery shows no
app screens at all. `/dev/kvm` exists but the account here is not in the `kvm`
group and `sudo` is blocked by `no_new_privs`, so the emulator falls back to
software emulation; there it boots (about three minutes), installs and starts 1.9
fine, but Android's `system_server` sits in a permanent "Application Not
Responding" state and every screenshot comes back as a flat black frame. Earlier
versions of this README shipped screens drawn element-by-element from the layout's
`dp` values. Those were removed along with the renderer that produced them: a
reconstruction reads as a screenshot without being one. If you capture the app on
a real phone, those files are the ones to add.

## Not yet done

- Never exercised on a physical device. Verified so far: resource/link
  compilation, javac type checking, dex packaging, zipalign, v1+v2 signature
  verification, `resources.arsc` stored uncompressed for targetSdk 30+ installs, a
  byte-exact download of the APK from the published page, and a successful install
  and launch on an Android 14 emulator — whose system UI was too broken to be
  worth photographing or clicking through.
- The restyle itself is unconfirmed on hardware: colours, Poppins metrics, and the
  hairline spacing have only been checked as resource values and layouts.
- HEIC input depends on the device decoder (`BitmapFactory`); output is JPEG/WebP.
- EXIF flip orientations (`FLIP_HORIZONTAL`/`FLIP_VERTICAL`) are treated as normal.
- The comparison slider and preview strip are compiled and reviewed but have not
  been exercised on a device either.
