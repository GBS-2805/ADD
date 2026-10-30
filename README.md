# Compress — offline image compressor (Android)

A small, dependency-free Android app that shrinks photos entirely on-device. The
manifest deliberately declares **no `INTERNET` permission**, so the app physically
cannot upload anything.

This is the first module of the "app services offline" series: a local replacement
for web tools like `bgremovefree.com/compress`.

## Status

- APK: [`dist/Compress-1.3.apk`](dist/Compress-1.3.apk) (46 KB, signed, v1+v2, round adaptive icon)
- Download page: [`docs/index.html`](docs/index.html), deployed via Freebuff Sites
- Package: `app.offline.compress` · minSdk 26 · targetSdk 35 · versionCode 4
- Theme: light/dark toggle in the header, saved in `SharedPreferences`
  (`compress_settings` / `dark_mode`) and re-applied on every activity start, so
  the choice survives restarts. Dark palette lives in `res/values-night`.
- UI: the neobrutalist system from `bgremovefree.com/compress` — cream paper,
  white cards with a 4dp black border and hard offset shadow, black uppercase
  numbered steps, monospace captions, yellow chips. Source is ahead of the
  published build: 1.3 still has the previous dark theme.

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
  tile carries a × badge that drops that photo from the selection, and a
  **Clear all** chip under the strip empties it in one tap.
- Tapping a result opens `CompareActivity`: a draggable before/after slider
  (`ComparisonView`, custom `View`) with the size delta above it and a share
  action.
- **Preview runs before compression.** `Compressor.trial()` encodes the first
  selected photo into the cache without publishing it; `PreviewActivity` shows
  that against the original with the estimated size, and its primary button
  starts the batch. `compress()` is now `trial()` + MediaStore publish, so the
  preview and the saved file come from the same code path.

## Source layout

```
app/AndroidManifest.xml                    manifest (no INTERNET permission)
app/src/app/offline/compress/Compressor.java      decode → scale → rotate → encode → publish
app/src/app/offline/compress/MainActivity.java    picker, settings, preview strip, queue, results
app/src/app/offline/compress/CompareActivity.java before/after screen
app/src/app/offline/compress/ComparisonView.java  draggable comparison surface
app/res/…                                  layouts, drawables, vector adaptive icon
build.sh                                   SDK-tools build (aapt2 + javac + d8 + apksigner)
manifest.json, worker.js, docs/            download page deployment
```

## Build

No Gradle, no AndroidX, no downloads beyond the SDK itself.

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_SDK_ROOT=$HOME/android-sdk   # needs build-tools;35.0.0 + platforms;android-35
./build.sh
```

Output: `dist/Compress-1.3.apk`. Copy it to `docs/` before deploying the page.
Signing uses `$HOME/tools/compress-keystore.jks` (created on first build, password
`android`). Replace it with your own key for anything beyond personal sideloading.

## UI previews and screenshots

There is no emulator in this environment (the build VM has no KVM), so UI changes are
checked with a renderer rather than a device:

```bash
python3 tools/render_ui.py     # writes artifacts/ui-*.png + artifacts/ui-all.png
```

It reproduces each screen element by element from the same dp/sp values, colours,
border widths and shadow offsets as `app/res`, plus a synthetic photo for the
comparison slider. Treat it as a
design render, not a device capture: font metrics and shadows differ slightly. Keep
the numbers in `tools/render_ui.py` in sync when a layout changes, and attach the PNGs
whenever UI work is reported.

Fonts live in `tools/fonts` (Roboto regular/medium/bold, DejaVu fallback for glyphs the
Latin Roboto subset lacks). Re-download them if the worktree is restored without them.

## Not yet done

- Not exercised on a physical device or emulator (the build VM has no KVM, so
  software emulation was not viable). Verified so far: resource/link compilation,
  javac type checking, dex packaging, zipalign, v1+v2 signature verification,
  `resources.arsc` stored uncompressed for targetSdk 30+ installs, and a byte-exact
  download of the APK from the published page.
- HEIC input depends on the device decoder (`BitmapFactory`); output is JPEG/WebP.
- EXIF flip orientations (`FLIP_HORIZONTAL`/`FLIP_VERTICAL`) are treated as normal.
- The comparison slider and preview strip are compiled and reviewed but have not
  been exercised on a device either.
