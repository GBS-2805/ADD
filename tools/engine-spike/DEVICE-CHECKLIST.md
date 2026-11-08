# ADD Engine Lab — device checklist

Use a real Android 8.0+ device. The APK is debug-signed and contains `arm64-v8a`
and `x86_64` libraries.

```bash
adb install -r artifacts/engine-spike/ADD-Engine-Spike-0.1.apk
adb shell am start -n app.add.engine/.MainActivity
```

Open **DIAG** in the status bar or **DIAGNOSTICS** in the info panel. The screen
reports SceneView attachment, model state, asset size, device/ABI, and lets you run
the same PixelCopy capture used by export without writing a file.

## The short checklist

1. The 3D gem is visible inside the artboard frame.
2. Dragging inside the 3D viewport orbits the gem.
3. Dragging a vector node edits the path and leaves the region closed.
4. **Add node**, then **Del node**, keeps the network closed.
5. **DUP** on the 3D layer adds a second gem viewport; both gems stay visible when you move the layer.
6. Open the **STICKER** tool, add one sticker and one icon, then **EXPORT**.
7. **EXPORT** writes a PNG to `Pictures/Engine Spike`.
8. Opening that PNG shows all visible gems and stickers, not the dark 3D placeholder.

## Pass / fail

- **TEST 3D** must report `PIXELCOPY OK` with lit samples above zero. `BLACK FRAME`
  means the surface has not rendered yet; wait for the gem and retry once.
- If export reports `3D CAPTURE UNAVAILABLE`, the PNG kept the placeholder. Treat the
  3D export as failed and collect the log.
- **Del node** must not leave an open or dangling region.
- The app is expected to work offline after install.

## If a check fails

```bash
adb logcat -s Filament:* SceneView:* AndroidRuntime:E
```

Include the device model, Android version, the DIAG screen values, and the failing
step. Emulator screenshots are not reliable in this VM, so a real-device observation
is the useful result.
