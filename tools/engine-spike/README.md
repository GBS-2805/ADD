# ADD native engine spike

A standalone lab APK for the next ADD editor: a Skia-backed artboard with editable
vector networks, native type, and a live Filament glTF layer inside the artboard.

It is deliberately **not** wired into `build.sh` or the ADD release. ADD's raw
`aapt2`/`javac`/`d8` build has no Gradle or Kotlin, while SceneView 2.3 requires
Kotlin 2.0, Compose and Filament's native libraries. Keeping the spike here proves
the engine without putting a 28 MB debug dependency set into the shipping APK.

## Run

Requirements: JDK 17, Android SDK 35, Node 20+ (only for the asset pipeline).

```bash
cd tools/engine-spike
printf 'sdk.dir=%s\n' "$HOME/android-sdk" > local.properties
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

`JAVA_HOME` must point at a JDK 17 install; the project does not assume `java` is on
`PATH`.

`bash verify-suite.sh` runs every test through that same JDK/SDK setup and prints the
totals; `python3 tools/check_all.py --visual` runs it with the rest of the shelf's
verdict, and `.github/workflows/checks.yml` runs it on every push.

## Device verification

Open **DIAG** in the status bar (or **DIAGNOSTICS** in the info dialog) for live
SceneView/model/device state and a non-saving PixelCopy test. The screen also carries
the six manual checks. The same procedure is in
[`DEVICE-CHECKLIST.md`](DEVICE-CHECKLIST.md).

The capture test uses the export path, but it cannot prove orbit gestures or the
saved PNG by itself; those remain manual checks.

## What is implemented

| Feature | Path |
| --- | --- |
| Artboard, grid, type and selection rasterizer | `ArtboardPainter.kt` — Android Canvas, which is Skia |
| Editable vector network | `VectorNetwork.kt` — points, segments, ordered regions, split/bridge edits |
| Layer document and history | `EngineDocument.kt` — vector, text, sticker and 3D layers; gesture-grouped undo/redo |
| Sticker and icon library | `Stickers.kt` — eight local vector shapes, editable with the vector nodes |
| Live 3D layers | `SceneLayer.kt` — one SceneView / Filament 1.56 viewport per visible glTF layer, orbit camera |
| PNG composite | `Exporter.kt` — artboard draw plus `PixelCopy` of the SceneView surface |
| Native screen | `EngineScreen.kt` — tool rail, stage, layer stack, inspector, export |
| Device diagnostics | `DiagnosticsScreen.kt` — live SceneView/model state, PixelCopy test, manual checklist |
| Asset pipeline | `scripts/make_gem.py`, `scripts/prepare-assets.mjs` |

The vector stage supports selecting a node, dragging it, splitting a segment with
**Add node**, bridging neighbours with **Del node**, reordering layers, changing
fill/stroke, duplicating and deleting. The type layer edits live text; Android's
Minikin/HarfBuzz stack shapes it. The **Sticker** tool adds one of eight local
vector stickers/icons; once placed it is a normal editable vector layer, so the
same node, fill, stroke, duplicate and delete controls apply.

The 3D layer is a real SceneView with a real glTF asset, not a drawn placeholder.
Every visible 3D layer gets its own viewport and PixelCopy capture, so **DUP** on a
3D layer produces a second gem that is visible on the artboard and in the export,
not just a second row in the layer stack.

### Dependency choices

- **SceneView 2.3.0** is pinned because it is published against Filament 1.56,
  Compose 1.7.8 and Kotlin 2.0.21. This is the last stable line before the Compose
  1.8/Kotlin 2.1 alignment that would force a broader toolchain migration.
- **Filament** comes transitively from SceneView. The APK is limited to
  `arm64-v8a` and `x86_64` while this is a lab.
- **glTF-Transform 4.1.1** and **meshoptimizer 0.22** run at build time. The source
  icosphere is 5,120 triangles; `optimize` welds/dedupes/prunes it and `simplify`
  uses meshoptimizer to produce a 39,752-byte GLB. Compression is disabled so
  Filament's gltfio can load the GLB without a runtime meshopt decoder.
- **OpenColorIO** is deferred. The export is explicitly sRGB; pulling a large C++
  colour-management stack into this spike would not prove a useful editor path.
- **3D layer opacity and true compositing** are deferred. The live 3D layer is a
  SceneView `SurfaceView`, so the exporter captures it with `PixelCopy` instead of
  drawing it as a translucent layer. Opacity controls currently apply to vector and
  type layers; a production renderer would draw Filament into an offscreen target.
- **HarfBuzz** is present through Android's text stack (Minikin uses HarfBuzz). A
  second direct NDK integration would duplicate the platform shaper.

## Asset pipeline

```bash
npm install
npm run assets
```

This writes `app/src/main/assets/models/engine_gem.glb`. The generated `.gltf`,
`.bin` and intermediate `.glb` files under `assets-src/` are build scratch; the
script is the source of truth. The shipped GLB is committed so a clean Gradle build
does not require Node.

## Verification

- `:app:assembleDebug` compiles the Kotlin/Compose/SceneView app and packages the
  GLB and Filament native libraries.
- `:app:testDebugUnitTest` exercises the network topology and undo/redo without a
  device. The DIAG PixelCopy path compiles into the same APK, but has not been run on
  a device here.
- The lab cannot be visually verified in this VM. SceneView is a `SurfaceView` and
  Robolectric has no Filament compositor; `/dev/kvm` is unavailable to this account,
  so the existing emulator path is not usable. The APK must be installed on a real
  Android device to judge the 3D viewport and touch behavior.

## Licences

See `THIRD_PARTY.md`. Blender and Krita were UX references only; no GPL code is
copied. Graphite and OpenPencil were read as architecture references; no code from
them is vendored here.
