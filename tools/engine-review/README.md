# ADD engine review

A review-only APK that packages ADD's existing app and the native engine as one
application. The shipping build path (`./build.sh`) is unchanged; this project
exists only to put the engine on a phone for review.

## Build

```bash
bash tools/engine-review/build.sh
```

The APK lands at `artifacts/engine-review/ADD-3.7-engine-review.apk`. It is a debug
build, same package (`app.offline.compress`) and version (`3.7` / `39`) as ADD, so it
replaces an installed ADD copy. It uses the existing ADD signing key when
`$KEYSTORE` (or the default `$HOME/tools/compress-keystore.jks`) is present; without
that key Gradle falls back to its debug key, which requires uninstalling ADD first.

## Open the engine

Open ADD, tap **Tools → Editor**, and the engine opens directly in this review build.
The original native editor is still available: open the engine's info sheet (i) and
tap **BASIC EDITOR**. The drawer's Editor row uses the same entry. The engine entry
only exists in this debuggable build; the release `app.offline.compress` package has
no engine activity and keeps the Quick/Studio editor.

## What is real

The engine screen is the spike's own Compose UI, `EngineDocument`, vector network,
Skia artboard, Filament `SceneView` and export path. This pass adds:

- a searchable offline catalog of 4,373 icons from Lineicons, Coolicons, Phosphor
  and Streamline, parsed from the shipped JSON into scalable path layers;
- rotation and corner scaling for every layer, with 22 dp hit targets and inset
  handles, including text that scales its font with its box; X/Y/W/H sliders are
  gone, so vector and sticker frames stay square and are placed only by dragging
  and corner-scaling;
- a floating **EDIT 3D** chip that turns the model inside its viewport instead of
  rotating the SurfaceView, with corners that resize the frame and a direct drag
  that moves the layer on the artboard; the model orientation is baked into the
  export;
- cream icon thumbnails in the catalog and diagnostics, so glyphs are legible on
  the dark cards;
- a second 3D asset, `engine_ring.glb`, swappable with the reviewed gem;
- both GLBs and the icon catalog in diagnostics.

ADD's existing Java and resources are compiled into the same APK, so the rest of the
app still works from the same package.

The APK is `arm64-v8a` only, because the review is for a phone and the x86_64
Filament copy would double the handoff download. This VM cannot install the APK or
draw its Compose/SceneView surface. The handoff must say that interaction, sticker
shapes, the 3D turntable, the icon touch targets and export remain phone checks. Do
not substitute a drawing for a capture.
