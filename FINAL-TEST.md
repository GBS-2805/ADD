# Final BMO gate — 2026-11-24

**Scope:** the BMO lane, Tasks 1–12, plus C2–C4 cleanup; Studio retirement is included.
This candidate was rebuilt from `a3606a8` on `perf/followups-2`, not from `main`, so it
is a review artifact and not a promoted release. The deploy and live-header records
below are from the 2026-11-14 run and were not re-run for this candidate.

## Candidate

| Field | Value |
|---|---|
| Artifact | `artifacts/ADD-4.0-bmo.apk` |
| Package | `app.offline.compress` |
| Version | `4.0-bmo (57)` |
| Size | 44,795,413 bytes |
| SHA-256 | `5bddae032c5152d60cbdc5f24ed673ed9f8b863b505342d6a1e77fd6aab06d2c` |
| Certificate SHA-256 | `960b76985fb74b50ebe7fb3f368a586aa51f31ea59ab0d94be215e9441dd7fd6` |
| Build | `bash tools/engine-review/build.sh release` |
| Commit | `a3606a8` on `perf/followups-2` |

`aapt2 dump badging` reports no `application-debuggable` flag, the release
certificate is the ADD key, and `app.add.engine.MainActivity` is present on the
`:engine` process. The APK was rebuilt after the final B2/B3/B10/B12 source changes.

## Green automation

| Command | Result |
|---|---|
| `bash tools/engine-spike/verify-suite.sh` | **234 tests, 0 failures, 0 errors, 0 skipped** (rerun; the first run at this commit had 2 host-timing `TexturePresetTest` failures that did not reproduce) |
| `bash tools/engine-spike/verify-chrome.sh` | **23/23 captures match**, exit 0 |
| `cd tools/engine-review && ../engine-spike/gradlew :app:testReleaseUnitTest --tests add.EngineRoutingTest` | **6/6 route/absence tests pass** |
| `python3 tools/check_all.py` | **29 gates green in 41.5s** |
| `python3 tools/check_all.py --visual` | not run for this candidate |
| `python3 -m unittest discover -s tools/tests` | not run separately; `check_all` and `editor` are green |

The first JVM run at `a3606a8` reported 234 tests with two failures in
`TexturePresetTest`: `Concrete` at 16.44 ms against the 16.00 ms host budget, and a
138 ms ten-family warm. The rerun on the same commit was 234/0/0/0 with `Concrete` at
6.33 ms and no 169 ms `Marble` outlier. Both runs are recorded; the failure was host
contention, not a tree change.

## Blocked or not applicable

### Device checks — **BLOCKED**

`$ANDROID_HOME/platform-tools/adb devices` lists no device. Therefore there are no
real cold-start, frame-time, pinch-latency, export, 3D-orbit, install-over or
process-death results. See `artifacts/bmo/DEVICE-REPORT.md`. No device number in this
report is estimated or inferred from Robolectric.

### Live headers — **PASS (recorded 2026-11-14, not re-run)**

```text
python3 tools/check_headers.py --origin https://artbase-olive.vercel.app \
  --handoff-origin https://artbase-handoff.vercel.app
headers: OK - site and handoff carry enforcing CSP, HSTS, frame denial and cache policy
```

The CSP change deployed at `1e45f85`; the plan record was redeployed at `51a0221`. `docs/vercel.json` and `handoff/vercel.json`
carry the enforcing CSP, HSTS, CORP, X-Permitted-Cross-Domain-Policies and the frame,
referrer, COOP and permissions headers. All 21 inline scripts, including the ten
executable classic-archive scripts, are hashed; the handoff's `img-src` allows the site
origin for its screenshots. A Chromium report against the report-only policy was clean
across 32 docs pages and the handoff, and the live enforcing browser pass was clean too.
`style-src 'unsafe-inline'` remains the recorded residual risk. The refreshed deployment
token authenticates as `ghost-0`; `tools/deploy_vercel.py` and
`tools/deploy_review.py handoff` fail the deploy if a required live header disappears.

### Shipping `check_apk.py` — **NOT APPLICABLE**

```text
python3 tools/check_apk.py --apk artifacts/ADD-4.0-bmo.apk ... --release
check_apk: 8 problem(s)
```

The checker compares the shipped catalog identity (`3.9`, code 41, published digest)
with the review candidate. Those differences are expected for `4.0-bmo (57)`, so this
gate is not claimed as a pass. Signature and manifest were verified directly with
`apksigner` and `aapt2`; the release-install-over test remains blocked on a device.

### Studio retirement — **DONE**

`LegacyLookMigration` and the `LEGACY LOOKS` library section are implemented and
tested. `StudioPane.java`, `app/studio-assets/**` and `assets/www/studio/**` are
deleted; the ENGINE tab opens the native engine activity and falls back to Quick only
when the engine class is unavailable. `EngineRoutingTest` asserts the class and both
asset trees stay absent. The 44,795,413-byte release candidate no longer packages the
WebView editor.

## Saved-look fields

The migration preserves every valid look in `filesDir/legacy-looks.json` and leaves
the original `StyleStore` preference untouched. It maps exposure, contrast,
saturation and fade to `LayerEffects`; **warmth and type shadow have no engine
equivalent** and are reported as warnings rather than silently dropped. Type
treatments map to text layers by document order.

## Known limitations

- No physical-device evidence; Tasks 8/11 device steps remain open.
- `style-src 'unsafe-inline'` remains a residual CSP risk; the policy is not claimed strict.
- 3D transparency remains a renderer limitation while the preview uses a
  `SurfaceView`.
- The migration lives in `app.add.engine.LegacyLookMigration` (the engine module)
  rather than the plan's `add` package so the standalone engine suite can compile and
  test it without the review-only source set.
- The release candidate uses the review manifest's `ShelfActivity`; the shipping
  `check_apk` identity gate is intentionally not repointed at it.
