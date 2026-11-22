# Native Android renders

Real screenshots of ADD's native screens, drawn by the app's own layouts and paint
code through Robolectric's `NATIVE` graphics mode. No emulator, no hand-drawn
reconstructions.

The method is the one recorded in the Android-renders handoff: compile the binary
resources with `aapt2`, host the Activity in Robolectric with the platform's
`libandroid_runtime`, and draw its content view into a bitmap.

## What is rendered

Ten native screen states, audited in one newsprint theme:

| Screen | How it is set up |
| --- | --- |
| **Compress** | `MainActivity` receives a sample photo through the same `ACTION_SEND` path as a gallery share. |
| **Side panel** | `MainActivity` opens the real side panel from the burger; the settled open state is drawn over the scrim. |
| **Editor · Quick** | `EditorActivity` is created with `editor_studio=false`, then the package-visible `EditorView` API loads a photo and sets two real type layers. |
| **Editor · Photo** | The real Photo `AlertDialog` is opened from `photoButton`, with a loaded photo and its transform actions. |
| **Editor · Presets** | The real Presets `AlertDialog` is opened from `effectButton` and its content panel is drawn over the dimmed Editor. |
| **Editor · Type** | The real Type `AlertDialog` is opened from `typeButton`, with the selected line's live controls. |
| **Editor · Layers** | The real Layers `AlertDialog` is opened from `layersButton`, with both type layers listed. |
| **Editor · Colour** | The real Text colour `AlertDialog` is opened from `colourButton`, with the wheel, palette, hex field and RGB sliders. |
| **Compare** | `CompareActivity` gets before/after files and the same extras the app sends. |
| **Font packs** | `PacksActivity` reads a cached copy of `docs/packs/packs.json`; no network request is made. |

The test audits every 1080×2400 screen in memory: the newsprint palette must be present in the pixels, and every visible view must stay inside its window, fit its text and keep the dock at the window edge. Only the ten newsprint captures are written to `artifacts/android/`; `render-audit.json` records the ten palette and layout checks. The Compress form is a scroll view, so after its 1080×2400 audit frame it is also written at its natural full height: the complete form, Preview and Compress all stay visible instead of ending mid-section 4. The other nine captures remain 1080×2400. `manifest.json` names the one showcase.

The five Editor sheets are composited from the app's actual dialog panel over the
actual Activity frame at the window's `dimAmount`; the sheet views are not redrawn.
This is the one place a render combines two real surfaces, because a dialog is a
separate window from its Activity.

## Keeping the set

`manifest.json` names the ten screens and the one newsprint `showcase`.
`review.py` requires exactly the ten showcase PNGs, a valid `render-audit.json`
(palette/layout pass, ten checks) and 1080×2400 dimensions for nine screens;
Compress must match the audited full height in `render-audit.json`. It then builds
the 3-column contact sheet and `artifacts/shots/` from scratch, so the public gallery
cannot silently keep an old screen.

`render.sh` writes into `artifacts/.android-next/` and replaces `artifacts/android/`
only after all ten screens pass. A failed run leaves the previous good set
untouched; the screenshots are a current set, never a growing archive.
`artifacts/android/` is
the canonical render set, while `artifacts/shots/` is the disposable public review
mirror. Neither is committed, and neither belongs in `docs/renders/`.

## Not rendered

The **Shelf** screen and the **Studio** tab are Chromium WebViews. Robolectric has
no Chromium compositor, so it can only draw a blank surface. Those surfaces are
left out rather than faked. The Editor's ENGINE tab starts the engine activity;
without that class the app falls back to Quick, and the test asserts the
fallback. The five Editor frames show the native Quick tab, which is one tap
away and deliberately labelled secondary in the gallery.

## Setup

The repo does not use Gradle. `setup.sh` builds the inputs instead:

```bash
bash tools/android-render/setup.sh
```

It runs:

1. `aapt2 compile` + `aapt2 link` over `app/res` and `app/AndroidManifest.xml`,
   producing `build/android-render/resources.apk` and the generated `R.java`.
2. `javac` over `app/src` plus the generated `R`, packaged as
   `build/android-render/lib/add-app.jar`.
3. Extracts `classes.jar` from the AndroidX `monitor` AAR. Maven's classpath
   cannot use an AAR, and Robolectric's POM asks for one.
4. Symlinks the cached `android-all-instrumented-15-robolectric-12650502-i7.jar`
   into `build/android-render/deps/` for `robolectric.offline=true`.

Requirements: JDK 17, Android SDK 35 build-tools and platform, Maven, the
Robolectric artifacts in `~/.m2`, and Python 3 with Pillow for the review page. The
app's own build is unchanged.

## Render

```bash
bash tools/android-render/render.sh
```

The Maven test is `AndroidRenderTest`; it audits all ten screens, writes the ten
showcase PNGs, and asserts dimensions, non-blank content and a minimum encoded size.
It records `render-audit.json` after the run. `render.sh` then runs `review.py` to
validate the manifest and audit report, and rebuild `artifacts/shots/`. To render one
screen while iterating:

```bash
JAVA_HOME="$(cat "$HOME/tools/JDK_PATH")" ANDROID_SDK_ROOT="$HOME/android-sdk" \
  "$HOME/tools/maven/bin/mvn" -f tools/android-render/pom.xml test \
  -Dtest=AndroidRenderTest#editorQuickScreen \
  -Dandroid.jar="$HOME/android-sdk/platforms/android-35/android.jar" \
  -Dadd.out="$PWD/artifacts/.android-iter" \
  -Drender.root="$PWD"
```

That scratch output is for inspection; it does not replace the validated set.

## Platform note

The test config uses the keys Robolectric documents for a non-Gradle project:
`android_resource_apk`, `android_merged_manifest` and
`android_custom_package`. `tools/android-render/src/test/resources/com/android/tools/test_config.properties`
is filtered by Maven so the absolute paths are current for this checkout.
