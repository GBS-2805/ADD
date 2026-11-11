# Native engine chrome goldens

These eleven PNGs are the Roborazzi baselines rendered by
`app/src/test/java/app/add/engine/EngineFrameCaptureTest.kt` through Robolectric at
411×891 dp. They cover the real Compose activity chrome:

- `engine-studio-stage.png`
- `engine-studio-dock.png`
- `engine-studio-create.png`
- `engine-studio-landed.png`
- `engine-studio-style.png`
- `engine-studio-fx.png`
- `engine-studio-fx-grade.png`
- `engine-studio-layers.png`
- `engine-studio-layers-3d.png`
- `engine-studio-3d-style.png`
- `engine-studio-mask.png`

They deliberately **exclude the Filament viewport**. SceneView is a
`SurfaceView`; Robolectric has no Filament compositor, and the 3D states are
captured with the surface paused. Do not treat these as evidence of 3D rendering
or native touch behaviour.

The autosave label uses a fixed clock in the test, so a captured frame cannot
change just because the test ran at a different time.

Verify:

```bash
bash tools/engine-spike/verify-chrome.sh
```

Re-record intentionally after reviewing the Compose change:

```bash
bash tools/engine-spike/verify-chrome.sh --update
```

`ENGINE_CHROME_DIR` overrides the baseline directory for a temporary check.

`python3 tools/check_all.py --visual` runs this with the rest of the verdict, and
`.github/workflows/checks.yml` runs it on every push with a JDK 17 and the
android-35 platform. The chrome test declares this directory as an input, so a
changed or corrupted baseline re-runs the comparison rather than passing on a
cached UP-TO-DATE result.
