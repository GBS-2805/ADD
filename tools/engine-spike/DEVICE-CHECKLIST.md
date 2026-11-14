# ADD Engine Lab — device checklist

Use a real Android 13+ device. The BMO release candidate is
`artifacts/ADD-4.0-bmo.apk` (`4.0-bmo`, code 57), signed with the ADD release key
and containing `arm64-v8a`.

```bash
ADB="${ANDROID_HOME:-$HOME/android-sdk}/platform-tools/adb"
"$ADB" install -r artifacts/ADD-4.0-bmo.apk
"$ADB" shell am start -n app.offline.compress/app.add.engine.MainActivity
```

For the BMO shell, also check: canvas tap → Peek; page chip → Full; Hide UI →
Canvas; restore handle; dock scroll; selection rail; sheet drag; plugin shelf
run/hide/reopen/close; and that only one plugin panel can be active.

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

## Performance evidence

Run `bash tools/engine-spike/bench.sh` only with a real device attached. It writes
`artifacts/bmo/bench.json`; with no device it records `status: blocked` and exits 2.
Do not turn a JVM/Robolectric suite result into a device number. The device benchmark
uses 50 layers, three nested groups and one 2048 px photo, and records cold start,
median pan frame time, pinch latency, 1x/2x export time and peak bitmap allocation.

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
