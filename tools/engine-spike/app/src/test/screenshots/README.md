# Native engine chrome goldens

These six PNGs are the Roborazzi baselines rendered by
`app/src/test/java/app/add/engine/EngineFrameCaptureTest.kt` through Robolectric at
411×891 dp. They cover the real Compose activity chrome:

- `engine-rail.png`
- `engine-added.png`
- `engine-inspector.png`
- `engine-layers.png`
- `engine-layers-3d-visible.png`
- `engine-inspector-3d-visible.png`

They deliberately **exclude the Filament viewport**. SceneView is a
`SurfaceView`; Robolectric has no Filament compositor, and the 3D states are
captured with the surface paused. Do not treat these as evidence of 3D rendering
or native touch behaviour.

Verify:

```bash
bash tools/engine-spike/verify-chrome.sh
```

Re-record intentionally after reviewing the Compose change:

```bash
bash tools/engine-spike/verify-chrome.sh --update
```

`ENGINE_CHROME_DIR` overrides the baseline directory for a temporary check.
