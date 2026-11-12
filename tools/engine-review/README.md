# ADD engine review

A review-only APK that packages ADD's existing app and the native engine as one
application. The shipping build path (`./build.sh`) is unchanged; this project
exists only to put the engine on a phone for review.

## Build

```bash
bash tools/engine-review/build.sh
```

The APK lands at `artifacts/engine-review/ADD-3.9.1-field-preview.apk`. It is a debug
build, same applicationId (`app.offline.compress`) and version `3.9.1-field-preview`,
with review-only versionCode `56`, so it replaces an installed ADD copy. It targets
Android 13+ (`minSdk 33`). This build carries the artboard/layer colour field with its
recipe editor, on top of the Task 8 procedural texture library, Material Ink and the
recording-driven chrome fixes. It uses the existing ADD
signing key when `$KEYSTORE` (or the default `$HOME/tools/compress-keystore.jks`) is
present; without that key Gradle falls back to its debug key, which requires
uninstalling ADD first.

## Device audit

The app can capture its own live windows on a connected Android runtime. This is the
pre-signing gate for the parts unit tests cannot prove:

```bash
python3 tools/device_audit.py --serial emulator-5554 \
  --fixture artifacts/field-proof/device/document.json --reopen
```

It waits for `READY`, captures every window through `WindowManagerGlobal.mViews`,
verifies the saved artboard/layer field, then exports a PNG and requires 1080×1350. It writes `artifacts/field-proof/device/audit/audit.json` and exits non-zero
when any gate is missing. `--reopen` force-stops and relaunches the engine, then
requires the same field colours and field-painted layers. Use `--no-launch` to audit
the running activity and `--skip-export` for the shorter UI-only pass.

This x86 proof build is not a phone verdict: the harness exports the engine activity,
uses a 32/64 px field raster budget and a non-focusable first 40 s for software TCG.
Run the final gate on the arm64 candidate over a physical phone before shipping.

## Open the engine

Open ADD and tap **Tools → Editor**: this review opens the engine directly. The basic
editor is one tap away under **BASIC EDITOR** in the engine's info sheet, where QUICK,
STUDIO, PHOTO and EXPORT still live. Build 46 keeps the review entry and the settled add
flow, with the confirmation held until DONE/INSPECT; build 45 restored that routing;
build 44 made the editor door reach the engine; build 43 added the compositing authoring
pass; build 42 fixed the saved-document icon crash that stopped the engine on its first
draw. The release
`app.offline.compress` package has no engine activity and no such entry.

If the app stops, reopen ADD: a report with COPY appears when the process died of a
Java exception, and the engine's last startup stage when it died natively. Neither is
needed to keep working — the editor no longer opens the engine on its way in.

## What is real

The engine screen is the spike's own Compose UI, `EngineDocument`, vector network,
Skia artboard, Filament `SceneView` and export path. This pass adds:

- a settled add flow: every TYPE, VECTOR, STICKER, ICON, PHOTO and 3D add commits the
  layer, returns the tool to Select, and shows an **ADDED TO THE PAGE** notice with
  **INSPECT** and **DONE**. The top bar and bottom strip also carry **DONE** while any
  mode is armed, and system back leaves modes before it leaves the engine;
- a full-width **LAYERS** / **INSPECT** sheet built from two tabs, with the 3D viewport
  surfaces hidden while the sheet is open, so a model can no longer cover its own
  controls; the `SurfaceView` is also pinned behind the window;
- a labelled vertical tool rail and a visible **▾ TOOLS** handle, with larger type and
  touch targets throughout. The ADDED strip stays until DONE/INSPECT, sits above the
  zoom/status row, and new layers start clear of the rail. COMPOSITE is a disclosure,
  so the primary inspector ends clean instead of a cut wall of advanced controls;
- the Fabric Studio WebView ships GSAP 3.12.5 offline under `studio/vendor/`; its
  intro, selection feedback and overlay reveal run from the bundled files, respect
  reduced motion, and never use a CDN. The native engine has no DOM, so GSAP is not
  used inside Compose;
- JVM screenshot evidence: `EngineFrameCaptureTest` renders the real activity through
  Robolectric + Roborazzi at 411×891 dp, including a document with a visibly enabled
  3D layer while the sheet is open. It needs no emulator or Filament compositor;

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
- build 48 keeps that drag on a stationary pointer node while only the frame moves,
  caps the interactive preview at ~6 MP at high zoom, and caches effect-rendered
  layers so grain and halftone are not recomputed on every frame;
- cream icon thumbnails in the catalog and diagnostics, so glyphs are legible on
  the dark cards;
- a second 3D asset, `engine_ring.glb`, swappable with the reviewed gem;
- a review Editor entry that opens the engine directly, with the basic editor still
  reachable from the engine's info sheet;
- editable gradients with all four kinds, arbitrary stop colours and per-stop alpha,
  reorderable stops, all 19 blend modes, creatable/toggleable/removable paint masks,
  clipping, and independent grading, blur, grain, halftone and duotone switches that
  survive save and reload through `DocumentCodec`;
- both GLBs and the icon catalog in diagnostics.

ADD's existing Java and resources are compiled into the same APK, so the rest of the
app still works from the same package.

The APK is `arm64-v8a` only, because the review is for a phone and the x86_64
Filament copy would double the handoff download. This VM cannot install the APK or
draw its Compose/SceneView surface. The handoff must say that interaction, sticker
shapes, the 3D turntable, the icon touch targets and export remain phone checks. Do
not substitute a drawing for a capture.
