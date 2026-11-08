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

Open ADD, tap **Tools → Editor**, and the native editor opens as it does in every
build: QUICK tools, STUDIO on the Fabric tab, PHOTO, EXPORT. The engine is a third tab
beside QUICK and STUDIO, labelled **ENGINE**, and only this debuggable build has it;
the release `app.offline.compress` package has no engine activity and no such tab.

If the app stops, reopen ADD: a report with COPY appears when the process died of a
Java exception, and the engine's last startup stage when it died natively. Neither is
needed to keep working — the editor no longer opens the engine on its way in.

## What is real

The engine screen is the spike's own Compose UI, `EngineDocument`, vector network,
Skia artboard, Filament `SceneView` and export path. This pass adds:

- a searchable offline catalog of 4,373 icons from Lineicons, Coolicons, Phosphor
  and Streamline, parsed from the shipped JSON into scalable path layers;
- rotation and a centred uniform scale dial for every layer, including 3D, with 22 dp
  hit targets and inset handles, including text that scales its font with its box; X/Y
  movement stays on direct dragging and corners still scale, while sticker, vector and
  icon frames stay square in artboard pixels. A type layer at its 180 pt cap keeps
  growing its box, because the cap now belongs to the font, not to the gesture;
- a launched 3D viewport with a pinned camera: the **SCALE** dial resizes the layer's
  frame and the model with it, while **UNIT SCALE** resizes the model inside a frame
  that stays put, so neither control re-frames the other. The camera ignores gestures,
  so a drag can no longer orbit the model out of its frame;
- artboard zoom from 50% to 400%: two fingers pinch and pan, and a floating
  `− 100% +` pill steps the same range, with the percentage as the reset. One finger
  still belongs to the selected layer, and the second finger cancels that drag;
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
