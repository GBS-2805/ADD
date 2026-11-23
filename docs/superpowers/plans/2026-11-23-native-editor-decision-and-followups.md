# Native Editor Decision and Seven Follow-ups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Settle whether native v2 is ADD's editor, then make the seven follow-ups independently testable and mergeable without weakening an existing gate or creating a second editor inside the app.

**Architecture:** One decision record and two tracks. The native track verifies the already-committed native v2 editor on a real phone and rebuilds the 4.0 candidate at the current commit; it is the only app-shipping work here. The web track hardens the separate browser workbench under `editor/`: save-size invariant, CPU-throttled frame gate, autosave flush, lazy plugin panels with a first-editable budget, parser fuzzing, and CI sharding. The browser workbench remains a web surface and a validation harness; it does not become an in-app second editor.

**Tech Stack:** Kotlin/Compose/Skia/Filament for the native app, React 19.2 + Vite 7.3 + TypeScript 5.9 + Playwright 1.57 for the browser workbench, Python repository gates, GitHub Actions. No new runtime dependency.

**Spec:** `PLAN.md` (one-editor rule and native v2 shipping decision), `FINAL-TEST.md` (4.0 candidate, Studio retirement, blocked device gate), `NATIVE_EDITOR_V2.md` (native task list; its checkboxes are stale and are not evidence), `docs/superpowers/plans/2026-11-16-twin-workbench-v2.md` (browser baseline and known limitations), `editor/README.md` (browser workbench state), `AGENTS.md` (verification and evidence rules).

**Status:** Native accepted. Task 2 (save-size invariant) is implemented, green and committed (`9456452`); Task 3 (CPU-throttled frame gate) is committed (`ac3eb76`) with its 20-layer phone gate green and its 64-layer ceiling promoted from six clean baselines. Task 1 remains **BLOCKED** at the real-device step. Tasks 4–7 are not started. There is no commit, deploy or publish authorization in this plan.

---

## Decision: native v2 is the app editor

**Recommendation: yes. Keep native v2 as ADD's only editor inside the app. Do not replace it with the browser workbench, and do not ship both editors in the app.**

This is not a new direction imposed on the repository. It is already the recorded direction:

- `PLAN.md` says Native Engine v2 is the shipping editor and Studio is frozen; there is no second WebView editor.
- `FINAL-TEST.md` records the `ADD-4.0-bmo.apk` release candidate `4.0-bmo (57)`, with `app.add.engine.MainActivity` present on the `:engine` process.
- `FINAL-TEST.md` records Studio retirement: `StudioPane.java`, `app/studio-assets/**` and `assets/www/studio/**` are deleted, and `EngineRoutingTest` asserts both asset trees stay absent.
- The native editor owns the app's file, export, 3D, gesture, offline and Android-back behaviour. The browser workbench is a DOM/Vite application with a WebView save bridge; it is not a drop-in replacement for those native surfaces.

The browser workbench is still real and still worth maintaining: `editor/README.md` describes the page it builds, and the repository ships that page. It is a **separate web surface**, not a second editor in the APK. That distinction is what satisfies the one-editor rule.

### What would change the decision

A deliberate reversal is justified only if the native release gate produces a core failure that cannot be fixed in a bounded effort, for example:

- 3D capture is broken on the target phone and the export cannot be made truthful.
- Direct manipulation, plugin shelf or sheet behaviour is unusable on the target phone.
- The native editor cannot meet the agreed performance floor after a measured optimisation attempt.
- Native parity is materially further away than the current candidate suggests.

If that happens, the answer is a re-scope of ADD 4.0, not two editors in one app. Re-enabling the retired WebView editor would be a separate decision with its own plan.

### What this means for the seven follow-ups

All seven are in this plan. Follow-up 4 (the device gate) is the only native-release blocker and is Task 1. Follow-ups 1, 2, 3, 5, 6 and 7 are browser workbench work; they improve the web surface and its evidence, but they do not block the native APK and must not delay it.

### Follow-up map

| Plan task | Original follow-up | Track | Blocks native release? |
|---|---|---|---|
| 1 | Native device gate | Native | **Yes** |
| 2 | Save-size invariant | Browser | No |
| 3 | Frame-based, CPU-throttled drag gate | Browser | No |
| 4 | Autosave flush on `pagehide` / real IndexedDB reload | Browser | No |
| 5 | Lazy plugin panels / first-editable budget | Browser | No |
| 6 | `.add` parser and embedded-asset fuzzing | Browser | No |
| 7 | Shard the editor CI job | Browser | No |

### Owner decision gate

- [ ] **Accept:** native v2 remains the app editor; the browser workbench remains a separate web surface. Execute Task 1 first; Tasks 2–7 are the web backlog.
- [ ] **Reject:** name the surface that should replace native and why. Do not execute Task 1; rewrite the decision and ordering before any implementation.

If this gate is not signed, do not start implementation. A plan is not an authorization to reverse the product direction.

---

## Global constraints

- Work only in the paths named by each task. Do not modify unrelated app, site, handoff, docs or tool source.
- **No second editor inside ADD.** Do not add a WebView editor entry, do not restore `StudioPane`, and do not present the browser workbench as the app editor.
- **No invented device evidence.** A device task is complete only with a real Android 13+ device attached. With no device, record `blocked` and stop; do not convert Robolectric, JVM, emulator or browser numbers into a device result.
- **No invented performance numbers.** Thresholds below are explicit gates, not claims that the current build already meets them. If a gate fails, report the failure and fix the cause; do not raise the ceiling silently.
- **No dependency changes** for these follow-ups. The browser work uses the installed React, Vite, Playwright, Vitest and Ark stack. The native work uses the existing Gradle/Compose/Filament tree.
- **Preserve the project format.** `PROJECT_FORMAT = "bmo-twin-project"`, `PROJECT_VERSION = 2`, v1 migration and unknown-effect preservation stay intact. Any new optional field must be additive and must round-trip.
- **Preserve existing gates.** Do not skip, weaken or delete `check_editor.py`, the 64 MB parser cap, the 12-image/64-layer caps, the pointerup performance check or the engine suite.
- **No commit, push, merge, deploy or publish** unless the user separately authorizes it. This plan records no commit steps.
- **The browser workbench is not the native app.** A green browser e2e run is not a phone gate, and a green native JVM suite is not a device gate.
- **Keep the lockfile.** Do not update `editor/package-lock.json` to get a new tool for this plan.

## Review focus

These are the failure modes a reviewer should actively look for. Each one has a task and a test below.

1. **A save that succeeds but cannot reopen.** The parser has a 64 MB cap; the serializer has no output check. A 12 × 8 MB image document can base64-inflate past the cap. The invariant is: `saveProject` must never hand the browser or host a file that `parseProjectFile` rejects for size.
2. **A drag gate that passes while frames are janky.** The current 20-layer gate measures one long task and pointerup, not frame intervals, and it does not throttle CPU. A fast desktop run is not a phone-class result.
3. **A “Saved” line that lost the last edit.** Autosave is a 500 ms debounce; a tab close or app background inside that window can lose the last change. The fix must flush the pending write and prove a real IndexedDB reload, not a memory fake only.
4. **A native promotion without a device.** The 4.0 candidate's report is `BLOCKED`; the 209-test report is older than the current tree. A stale APK or a JVM-only run must not be promoted as a phone-verified release.
5. **Cold start hidden by an initial-byte budget.** Moving plugin code behind `lazy()` can lower the initial payload while leaving the first editable frame just as slow. The plan measures first-editable time under CPU throttle, not only bundle bytes.
6. **A hostile project file that hangs or crashes.** The parser runs on user files with embedded base64 and SVG. Fuzzing must prove bounded failure (`ProjectFileError`) rather than `TypeError`, stack overflow, infinite work or a network fetch.
7. **CI sharding that loses coverage.** Two green shards can still miss a spec if the matrix is wrong. The plan keeps the full suite count, uploads per-shard reports and leaves the production perf job isolated.

---

## Execution order

1. **Decision gate.** Owner accepts or rejects native. If accepted, continue.
2. **Task 1 — native device gate.** Rebuild the 4.0 candidate at the current commit, run the automation gates, then run the real-device checklist and benchmark. If no device is attached, record `blocked`; the browser tasks may still proceed, but the native release does not.
3. **Tasks 2–7 — web workbench.** Each is independently mergeable. Do them in the order written unless the owner prioritises differently; Tasks 2 and 3 protect data and interaction first, Task 7 changes CI last.
4. **Post-plan check.** Run `python3 tools/check_all.py` after the browser tasks and the workflow change; run the task-specific commands named below. Do not claim the device gate from any of those.

If the owner rejects native, stop after the decision record. Tasks 2–7 are still valid browser work, but Task 1 must not be executed and the native release question is unresolved.

---

## Task 1: Rebuild the 4.0 candidate and run the real-device gate

**Follow-up:** #4 — unblock one real device and run the Flip 7/WebView/native frame gates.  
**Track:** Native. **Blocks the native release:** yes.

**Goal:** Prove the current native tree, not the 2026-11-14 candidate, against a real Android 13+ phone. Rebuild the release APK, verify its manifest and signature, run the JVM/chrome/routing gates, then run the device checklist, smoke test, performance benchmark and export checks. If no device is attached, write a truthful `BLOCKED` report and stop.

**Files:**

- `tools/engine-review/build.sh` — build command; change only if the command or output path is actually broken.
- `tools/engine-spike/verify-suite.sh` — JVM suite.
- `tools/engine-spike/verify-chrome.sh` — chrome goldens.
- `tools/engine-spike/DEVICE-CHECKLIST.md` — human checklist for shell, plugins, 3D, export and performance.
- `tools/engine-spike/bench.sh` — device benchmark; writes `artifacts/bmo/bench.json`.
- `tools/device_smoke.py` — install/launch/crash smoke; exits 2 when no device is attached.
- `FINAL-TEST.md` — candidate table, green automation, blocked device section.
- `artifacts/bmo/DEVICE-REPORT.md` — device evidence or `BLOCKED` report.
- `artifacts/ADD-4.0-bmo.apk` — rebuilt candidate. Do not delete the existing artifact until the new one is verified.
- `NATIVE_EDITOR_V2.md` — reconcile checkbox state with `FINAL-TEST.md` after the run; do not mark device steps complete without a device.

**Interfaces / commands:**

```bash
bash tools/engine-spike/verify-suite.sh
bash tools/engine-spike/verify-chrome.sh
(cd tools/engine-review && ../engine-spike/gradlew :app:testReleaseUnitTest --tests add.EngineRoutingTest)
bash tools/engine-review/build.sh release
python3 tools/device_smoke.py --apk artifacts/ADD-4.0-bmo.apk --json
bash tools/engine-spike/bench.sh
```

The release build writes `artifacts/ADD-4.0-bmo.apk` and prints `apksigner verify --print-certs` output. `device_smoke.py` and `bench.sh` are expected to exit **2** with `blocked` when `adb devices` is empty; that is not a pass.

**Steps:**

- [ ] Record the starting commit and tree state: `git rev-parse HEAD`, `git status --short`. Stop if the tree is dirty; a release candidate must identify exactly one commit.
- [ ] Run the JVM suite. Record the printed test/failure/error/skip counts. The `FINAL-TEST.md` figure of **209 tests** is from the 2026-11-14 candidate and is not assumed for the current tree; the report must use this run's count.
- [ ] Run the chrome goldens. Record the printed match count; the existing candidate reported **24/24**, but the current run is the evidence.
- [ ] Run `EngineRoutingTest` and record the route/absence results.
- [ ] Build `bash tools/engine-review/build.sh release`. Record the output path, byte size and SHA-256 with `sha256sum artifacts/ADD-4.0-bmo.apk`.
- [ ] Verify the APK directly, because `tools/check_apk.py --release` intentionally compares the 3.9 shipping identity and is **not applicable** to `4.0-bmo`:
  ```bash
  SDK="${ANDROID_HOME:-$HOME/android-sdk}"
  "$SDK/build-tools/35.0.0/aapt2" dump badging artifacts/ADD-4.0-bmo.apk
  "$SDK/build-tools/35.0.0/apksigner" verify --print-certs artifacts/ADD-4.0-bmo.apk
  ```
  Confirm package `app.offline.compress`, version `4.0-bmo (57)`, no `application-debuggable`, `app.add.engine.MainActivity` present, and the ADD release certificate. Record the certificate SHA-256.
- [ ] Run `"$SDK/platform-tools/adb" devices`. If no device is listed, write the `BLOCKED` report now and stop. Do not run emulator substitutes, do not invent numbers, and do not promote the candidate.
- [ ] Install over the shipped app and launch the resolved activity:
  ```bash
  "$SDK/platform-tools/adb" install -r artifacts/ADD-4.0-bmo.apk
  "$SDK/platform-tools/adb" shell am start -n app.offline.compress/app.add.engine.MainActivity
  ```
- [ ] Run `python3 tools/device_smoke.py --apk artifacts/ADD-4.0-bmo.apk --json` and require exit 0, a live process and no crash/ANR in logcat.
- [ ] Walk every item in `tools/engine-spike/DEVICE-CHECKLIST.md`: canvas tap → Peek, page chip → Full, Hide UI → Canvas, restore handle, dock scroll, selection rail, sheet drag, plugin shelf run/hide/reopen/close, one active plugin only; 3D visibility and orbit; vector add/delete closed; duplicate 3D; sticker/icon export; PNG in `Pictures/Engine Spike`; exported PNG shows visible gems/stickers; `TEST 3D` reports `PIXELCOPY OK` with lit samples; tap-to-inspect/hit testing; back behaviour; 50-layer drag; pinch latency; export.
- [ ] Run `bash tools/engine-spike/bench.sh` with the device attached. Record cold start, median pan frame time, pinch latency, 1×/2× export time and peak bitmap allocation from `artifacts/bmo/bench.json`. If the device is absent, `status: blocked` and exit 2 are the only valid result.
- [ ] Update `artifacts/bmo/DEVICE-REPORT.md` with device model, Android version, DIAG values, each checklist result, benchmark JSON values and any failure logs. Update `FINAL-TEST.md` to the current candidate's counts and hashes, and keep the known limitations that remain. Reconcile `NATIVE_EDITOR_V2.md` checkboxes only to what the evidence supports.

**Verification:**

- JVM suite: 0 failures, 0 errors, 0 skipped, with the current count recorded.
- Chrome goldens: all printed goldens match.
- Routing: all route/absence tests pass.
- APK: correct package/version, no debuggable flag, engine activity present, ADD release certificate.
- Device smoke: exit 0 and no crash/ANR.
- Device checklist: every line is pass, fail or blocked with a named reason. A blocked line is not a pass.
- Benchmark: `status: ok` with real values, or `status: blocked` with exit 2.

**Promotion rule:** Do not call the APK phone-verified, release-ready or shippable unless the smoke, checklist and benchmark all pass on the attached device. If a core item fails, return to the decision gate before doing native polish. The browser tasks below can continue independently.

**Known trap:** Do not “fix” `tools/check_apk.py --release` by repointing it at the 4.0 review candidate. `FINAL-TEST.md` explicitly says the shipping identity check is not applicable to this candidate; signature and manifest are verified directly instead.

---

## Task 2: Refuse to write a `.add` the parser cannot reopen

**Follow-up:** #1 — make browser `.add` saves unable to exceed the 64 MB parser cap.  
**Track:** Browser. **Blocks native release:** no.

**Goal:** Enforce one invariant: if `saveProject` writes a `.add`, `parseProjectFile` can read it. Do not change the format id, version, parser cap or image workflow.

**Fresh-eyes ruling:** The 40 MB add-time image budget and the `bytes` bookkeeping were a second product rule, not the save invariant. They required an optional schema field, parser derivation, and autosave changes for a case the save guard already handles. Removed. Cost if wrong: a user can build an oversized document in memory and learns at save instead of at add; the save toast tells them to remove a layer, and no unopenable file is written.

**Files:**

- `editor/src/editor-core/store.tsx` — check serialized length before `downloadBlob`.
- `editor/src/editor-core/store.test.tsx` — one over-cap save test with a mocked serializer.

**Interface:**

```ts
const source = await serializeProject(doc);
if (source.length > MAX_PROJECT_BYTES) {
  const limit = Math.round(MAX_PROJECT_BYTES / (1024 * 1024));
  const actual = Math.ceil(source.length / (1024 * 1024));
  showToast(
    `This project is ${actual} MB; the editor cannot reopen files over ${limit} MB. Remove a layer or use a smaller PNG.`,
  );
  return;
}
downloadBlob(new Blob([source], { type: "application/json" }), projectFilename(doc));
```

Use `source.length` deliberately: it mirrors `parseProjectFile`'s own check. Do not use only `Blob.size`; the parser's rejection is based on the text length, so the preflight must match it. The guard must run before `downloadBlob`/`IconsApp.saveFile`.

**Steps:**

- [x] Add the serialized-size preflight to `saveProject` before `downloadBlob`.
- [x] Add a test that a serialized source just over `MAX_PROJECT_BYTES` shows the error and never creates a download URL.
- [x] Keep the test honest without a 64 MB allocation: mock `serializeProject` to return an object whose `length` is one over the cap, and assert the real guard stops the real `downloadBlob` path.
- [x] Run the focused store test, then the full editor gate.

**Implementation record (2026-11-23):**

- The over-cap test first failed with `URL.createObjectURL` called; the guard then made it pass.
- `cd editor && npx vitest run src/editor-core/store.test.tsx` — **10/10 passed**.
- `python3 tools/check_editor.py` — **typecheck ok, assets ok, tests ok, payload ok** (623118 bytes, ceiling 623118); `editor: OK` in 21s.

**Verification:**

```bash
cd editor && npx vitest run src/editor-core/store.test.tsx
python3 tools/check_editor.py
```

**Reviewer check:** The rejection must happen before `downloadBlob`/`IconsApp.saveFile`, not after. A toast alone is not a fix if the file still downloads. Do not reintroduce a `bytes` field or an add-time budget without a separate requirement and its own tests.

---

## Task 3: Gate drag on frames under phone-class CPU

**Follow-up:** #2 — make the drag gate frame-based and CPU-throttled at phone-class speed.  
**Track:** Browser. **Blocks native release:** no.

**Goal:** Replace the “one long task” result with a repeatable frame-interval gate at 4× CPU throttling, for both the 20-layer production case and the 64-layer stress case, while keeping the existing pointerup and long-task checks.

**Files:**

- `editor/e2e/editor-task5-performance.spec.ts` — existing 20-layer drag and 64-layer record.
- `editor/e2e/support/frameProbe.ts` — new helper for CPU throttling and rAF sampling.
- `editor/playwright.performance.config.ts` — production preview, one worker; keep it.
- `.github/workflows/checks.yml` — keep the production perf step; add the metrics artifact.
- `tools/editor_budget.json` — only if the repo decides to store frame ceilings there; otherwise keep thresholds in the spec and record the JSON as an artifact.

**Interfaces:**

```ts
export async function throttleCpu(page: Page, rate = 4): Promise<void>;
export async function startFrameProbe(page: Page): Promise<void>;
export async function stopFrameProbe(page: Page): Promise<FrameStats>;
export interface FrameStats { count: number; p95Ms: number; maxMs: number; }
```

Implementation notes:

- Use a Chromium CDP session: `await page.context().newCDPSession(page)` then `Emulation.setCPUThrottlingRate` with `{ rate: 4 }`. Restore the rate to `1` in `finally` so later tests are not contaminated.
- Install the rAF probe before pointer-down. Discard the first 3 intervals as warm-up. Stop after pointer-up plus 2 frames so the last commit is included.
- The probe records `performance.now()` deltas; it must not call React state or force a re-render.
- `p95Ms` is the nearest-rank 95th percentile; `maxMs` is the worst interval. Do not average away a long frame.
- Keep the existing long-task gate and the pointerup assertion. A frame gate does not replace them.

**Thresholds:**

| Case | p95 | max | Status |
|---|---|---|---|
| 20 layers at 4× CPU | ≤ 33 ms | ≤ 50 ms | Hard product gate; 30 fps floor. |
| 64 layers at 4× CPU | report-only | report-only | No hard ceiling until three clean 4× baselines exist. |

The 64-layer case starts as **report-only**. Record three clean production runs at 4× CPU, then set the committed p95/max ceilings to the worst observed value plus 10%; only then may the test assert them. Until that promotion, the test must fail only if the metrics are non-finite or missing, not on an unmeasured ceiling. Do not invent a 64-layer number from the earlier 59 ms unthrottled peak; that is a different measurement. The 20-layer gate is the product smoothness gate.

**Measured 2026-11-23, before the fix (local production preview, Chromium 1.57, 4× CPU):**

- 20 layers at 360×840: frame p95 **16.8 ms**, frame max **116.6 ms**, max long task **127 ms**. Repeated phone runs reached 77, 87, 90, 114 and 127 ms.
- 64 layers at 360×840: frame p95 **16.8 ms**, frame max **166.6 ms**, max long task **162 ms**. Report-only; one run, not a baseline.

**Root cause (V8 profile and CDP trace of the pointerup task):** the earlier “paint/layout” reading was wrong. The task was **151–180 ms** at 4×, of which about **134 ms was one React render** and **26 ms the commit**; Blink style/layout/paint contributed about 5 ms. A single `EditorProvider` context exposed the document, the UI state and the actions, so a one-layer transform commit re-rendered every consumer: the Ark UI number inputs, selects and menus, lucide icons, the layers panel and both phone drawers even while closed. The release work was broad React work, not a JavaScript hot spot and not paint.

**Fix (smallest change that removes the work):**

- The document and its history now live in their own subscription (`DocumentContext`, `useEditorDocument()`, `useEditorHistoryState()`). The store's UI/actions context no longer changes on an edit, so chrome with no document to show does not re-render.
- `EditorProvider` writes history synchronously through one `applyHistory` path instead of React state, so there is no second render pass and the autosave effect subscribes to the document store.
- The undo/redo buttons are their own subscriber, so the document menu is not re-rendered when undo becomes available.
- Closed `BottomSheet` drawers no longer render their document panels. On a phone the layers panel and inspector were re-rendering behind the canvas on every edit for no visible result.
- The store no longer exposes the document or the history flags at all: `state.doc`, `canUndo`, `canRedo` and the `getDocument()` escape hatch are gone. A read that returns fresh data without subscribing is a silent staleness bug waiting to be written, so `useEditorDocument()` and `useEditorHistoryState()` are the only way to read either. `buildCommands` takes the snapshot as a parameter, and the hotkeys call `moveSelectedLayer`, `deleteSelection` and `nudgeSelection`, which resolve the selection inside the provider's authoritative history and report whether they acted.

**Measured 2026-11-23, after the fix (same preview, Chromium 1.57, 4× CPU):**

- 20 layers at 360×840: frame p95 **16.8 ms**, frame max **16.8 ms**, max long task **0 ms**, 700 frames sampled. The hard gate passes with the 50 ms budget unchanged.
- 64 layers at 360×840: frame p95 **16.7 ms**, frame max **16.8 ms**, 708 frames. Report-only at that point; promoted separately below after three more runs.
- Desktop 20 layers at 1440×900: **not measured**. This VM's headless shell crashes the renderer on any viewport at or above 1024 px, and the same crash reproduces on the Task 2 HEAD build, so it predates this change and is not evidence about the editor. The desktop assertion stays unverified here and must be checked in CI or on a machine whose browser survives that layout.

**Steps:**

- [x] Add `frameProbe.ts` with the CDP throttle and frame sampling. Keep it test-only; no app code imports it.
- [x] Change the 20-layer test to throttle to 4×, start the probe before the drag, stop after pointerup + 2 frames, and assert p95/max. Keep the existing long-task and pointerup checks.
- [x] Add a 64-layer test using the same probe and the 64-layer setup already recorded in the file. Keep it report-only: assert finite, non-negative `p95Ms`/`maxMs` and the sample count, and log the values. Do not assert an unmeasured ceiling.
- [x] Persist one JSON result per case under `editor/test-results/editor-perf.json` with `{ case, throttle, p95Ms, maxMs, count, longTasks, maxLongTaskMs, attribution, longTaskStarts, elapsedMs, dragStartedAtMs, renderSpans }`; attach it to the Playwright result so a failed run carries the numbers. The file is read-merged because Playwright restarts the worker after a failed test.
- [x] Upload that JSON with the existing editor artifacts. Do not replace the JUnit artifact.
- [x] Run the production perf config locally with a production build; record the actual p95/max. **Done:** the 20-layer phone gate is green after the render-scope fix (p95 16.8 ms, max 16.8 ms, max long task 0 ms). The 50 ms budget was not changed. The desktop case could not run on this VM because its browser crashes at desktop widths on the unmodified HEAD build as well; it is reported as not measured, not as passing.
- [x] Collect three clean 64-layer 4× runs. Only then set the committed ceilings to the worst observed p95/max plus 10%, make the assertions hard and record the three runs in the PR. Never promote a ceiling from one run or from an unthrottled number. **Done:** six clean runs collected; p95 16.7–16.8 ms in all six, max 16.8 / 33.4 / 33.4 / 50.0 / 16.8 / 16.8 ms; committed ceilings 18.5 ms p95 and 55 ms max with hard assertions.

**Measured 2026-11-23, 64-layer promotion (six clean local production-preview runs, Chromium 1.57, 4× CPU, 360×840):**

| Run | Frames | p95 | Max | Max long task |
|---|---|---|---|---|
| 1 | 665 | 16.8 ms | 33.4 ms | 0 ms |
| 2 | 677 | 16.8 ms | 50.0 ms | 51 ms |
| 3 | 674 | 16.8 ms | 33.4 ms | 0 ms |
| 4 | 685 | 16.7 ms | 16.8 ms | 0 ms |
| 5 | 692 | 16.7 ms | 16.8 ms | 0 ms |
| 6 | 687 | 16.7 ms | 16.8 ms | 0 ms |

All six runs hold p95 at one frame interval, so the p95 ceiling is solid. Max is an extreme-value statistic and does not agree as tightly: three later runs dropped no frame at all, while the worst run dropped three (50.0 ms). The ceilings are still the worst observed value plus 10%: p95 16.8 ms → 18.48 ms, rounded up to **18.5 ms**; max 50.0 ms → **55 ms**. Because 55 − 50 = 5 ms is less than one 60 Hz frame (16.7 ms), the max ceiling tolerates no additional dropped frame; that ratchet is deliberate for a stress case, and a future run that needs widening must bring three new baselines rather than loosening this one on a flaky result. Both assertions are hard; a p95-only or max-only gate was rejected because each hides a different failure. Run 2's 50.0 ms frame came with a 51 ms long task; the 64-layer case asserts frames only, and no long-task ceiling is claimed for it.

**Verification:**

```bash
cd editor && npm run test:e2e:perf
python3 tools/check_editor.py
```

The perf command must show both cases and the JSON artifact must contain finite `p95Ms`/`maxMs`. A skip or a missing artifact is not a pass. If the local environment cannot throttle (non-Chromium or no CDP), report the blocker; do not substitute an unthrottled number.

**Reviewer check:** Once the 64-layer ceilings are promoted, a gate that only checks `max` can pass a run with many 45 ms frames and a gate that only checks p95 can hide one 200 ms stall; both assertions are then required. Before promotion, verify the 64-layer test cannot fail on an invented ceiling and that its JSON still records real metrics.

---

## Task 4: Flush autosave on `pagehide` and prove a real IndexedDB reload

**Follow-up:** #3 — flush autosave on `pagehide`/`visibilitychange` and test real IndexedDB reload.  
**Track:** Browser. **Blocks native release:** no.

**Goal:** Close the 500 ms last-write window. When the page is hidden or unloaded, write the pending document immediately; when the page reloads, restore that exact document from IndexedDB. Keep the existing “storage off” degradation.

**Files:**

- `editor/src/editor-core/store.tsx` — pending timer, flush function, `pagehide`/`visibilitychange` listeners.
- `editor/src/editor-core/autosave.ts` — only if the backend needs a flush/close hook; the IndexedDB transaction already commits writes.
- `editor/src/editor-core/autosaveStore.test.tsx` — unit coverage with the memory backend and fake timers.
- `editor/e2e/autosave.spec.ts` — new real-browser IndexedDB test.
- `editor/README.md` — remove the now-false “no autosave” statement; keep the Flip 7 limitation.

**Interfaces:**

```ts
type FlushAutosave = () => Promise<void>;
// internal to EditorProvider:
const flushAutosave = useCallback(async () => { ... }, [writeAutosave]);
```

Flush rules:

- If no timer is pending, do nothing (do not write on every visibility change).
- If a timer is pending, clear it, null the ref, and await `writeAutosave()` once.
- The `pagehide` and hidden `visibilitychange` handlers call the same function; a second event in the same hide must be a no-op.
- On unmount, flush once if a timer is pending. Do not leave a timer that fires after unmount.
- If the backend is `null` or quota-blocked, keep the existing `status: "off"` behaviour; never throw from the event handler.

The current effect that only clears the timer on unmount is replaced, not duplicated. The autosave timer/refs remain the single source of truth.

**Steps:**

- [ ] Add `flushAutosave` after `writeAutosave` and before the restore effect; it must read the latest `stateRef.current.present`, not a stale closure.
- [ ] Add a `useEffect` that registers `window.addEventListener("pagehide", flush)` and `document.addEventListener("visibilitychange", onVisibility)`, with a cleanup that removes both and flushes a pending timer once.
- [ ] Keep `scheduleAutosave`’s 500 ms debounce and its restore suppression unchanged.
- [ ] Unit-test: edit, advance less than `AUTOSAVE_DELAY_MS`, dispatch `pagehide`, await the write, assert the backend received the edited document and the status says saved.
- [ ] Unit-test: edit, dispatch `visibilitychange` with `document.visibilityState === "hidden"`, assert one write; dispatch it again and assert no duplicate.
- [ ] Unit-test: with the backend `null`, dispatch `pagehide` and assert no throw and `status` becomes `off` (or stays `off`).
- [ ] Add `editor/e2e/autosave.spec.ts`:
  - Load the editor, edit a text layer through the real UI, wait for “Saved”, reload, and assert the edited text is present.
  - Edit, immediately dispatch `pagehide` before the debounce, reload, and assert the edit is present. This is the last-write-loss regression.
  - Use a new browser context per test so one test’s IndexedDB does not seed another.
- [ ] Update `editor/README.md`: autosave exists; the remaining limitation is that the Flip 7/device path has not been checked.

**Verification:**

```bash
cd editor && npx vitest run src/editor-core/autosaveStore.test.tsx
cd editor && npm run test:e2e -- autosave.spec.ts
python3 tools/check_editor.py
```

The e2e test must use a real IndexedDB backend, not the memory test backend. A unit test with `MemoryBackend` is necessary but not sufficient. If the browser’s IndexedDB is unavailable in CI, the test must report that specific blocker, not silently skip.

**Reviewer check:** A `beforeunload` dialog is not a flush. The write must happen on the hide event; a warning that the user may lose data is not the fix. Do not add a synchronous localStorage copy as a workaround.

---

## Task 5: Cut first-editable time by lazy-loading plugin panels

**Follow-up:** #5 — cut cold start by lazy-loading plugin-only modules and budget first-editable time.  
**Track:** Browser. **Blocks native release:** no.

**Goal:** Keep the editor shell and canvas in the initial bundle, move the eight plugin panels and their heavy dependencies behind dynamic imports, and measure the time to the first editable canvas under 4× CPU. Lower the committed initial-gzip ceiling only after the split is measured.

**Files:**

- `editor/src/editor-core/registry.tsx` — replace eight static panel imports with `lazy()` loaders; add `preloadPlugin`.
- `editor/src/editor-core/types.ts` — allow a lazy panel component in `EditorPlugin`.
- `editor/src/editor-core/PluginHost.tsx` — wrap the active panel in `Suspense` with a stable loading fallback.
- `editor/src/editor/panels/EffectsPanel.tsx` — keep the catalogue/plugin switch; no static plugin import may leak back in.
- `editor/src/shell/PluginCatalogue.tsx` — preload on pointer enter/focus, and on click before state opens the panel.
- `editor/src/editor/EditorShell.tsx` — add `performance.mark("editor:first-editable")` and a ready attribute after the first canvas layout.
- `editor/e2e/editor-task5-performance.spec.ts` — add the first-editable test to the production preview config.
- `tools/editor_budget.json` — lower `initial_gzip` to the measured post-split value with a small headroom.
- `editor/README.md` — record the plugin-lazy state only if the README describes startup or plugin loading.

**Interfaces:**

```ts
// types.ts
import type { ComponentType, LazyExoticComponent } from "react";
type PluginPanel = ComponentType<PluginPanelProps>;
panel: PluginPanel | LazyExoticComponent<PluginPanel>;

// registry.tsx
export function preloadPlugin(id: string): Promise<unknown> | undefined;
```

Use the existing default exports. Each loader is the same function passed to `lazy`, so a preload and a later render share the module promise:

```ts
const loaders = {
  "shader-lab": () => import("../plugins/ShaderLabPlugin"),
  "texture-lab": () => import("../plugins/TextureLabPlugin"),
  // ... all eight
} as const;
```

`preloadPlugin` returns `loaders[id]?.()` and never throws; a failed preload is allowed to surface when the panel actually renders. Do not preload all eight on mount — that defeats the split. Preload the hovered/focused catalogue item and the clicked item only.

`EditorShell` marks first-editable after the canvas has a non-zero layout and the initial restore has resolved or timed out. The mark must be one-shot:

```ts
performance.mark("editor:first-editable");
document.documentElement.dataset.editorReady = "true";
```

The production startup test waits for `html[data-editor-ready="true"]` and reads `performance.getEntriesByName("editor:first-editable")[0].startTime`. It starts **report-only**: record three clean production runs at 4× CPU, then set the committed `STARTUP_BUDGET_MS` to the worst observed value plus 10% and make the assertion hard. Until that promotion, the test fails only if the mark is missing, non-finite or non-positive; it must not assert an unmeasured 3,000 ms ceiling. Do not describe a report-only measurement as a budget.

**Steps:**

- [ ] Convert `registry.tsx` to lazy loaders. Remove all eight static imports; `lucide-react` and the metadata stay static.
- [ ] Update the `EditorPlugin.panel` type to accept the lazy component; keep `PluginHost`’s props unchanged.
- [ ] Wrap the active panel in `Suspense` with a lightweight fallback that has `data-testid="plugin-loading"` and does not import plugin code.
- [ ] Add preload on catalogue pointer enter/focus and on `openPlugin`; do not preload all panels on app mount.
- [ ] Add the one-shot first-editable mark and ready attribute in `EditorShell` after the canvas size is non-zero and the restore effect has settled.
- [ ] Add the production startup test to `editor-task5-performance.spec.ts`; throttle 4×, navigate, wait for ready, read the mark, assert it is finite and positive, and write the measurement to `editor/test-results/editor-perf.json`. Keep it report-only until three clean baselines are collected.
- [ ] After three clean runs, set `STARTUP_BUDGET_MS` to the worst observed first-editable time plus 10%, make the assertion hard and record the three measurements in the PR. Do not promote from one run.
- [ ] Run `npm run build`; record the emitted initial chunk sizes. Set `tools/editor_budget.json` `initial_gzip` to the measured initial gzip with at most 2% headroom. The gate must fail if a static plugin import returns.
- [ ] Add a unit/regression test that `getPlugin` still resolves every id and that `preloadPlugin` resolves the same module for a known id. Avoid asserting Vite chunk internals in jsdom.
- [ ] Run the editor gate and the e2e startup test.

**Verification:**

```bash
cd editor && npm run build
python3 tools/check_editor.py
cd editor && npm run test:e2e:perf
```

`check_editor.py` must pass with the lowered initial-gzip ceiling. The build output must show separate plugin chunks and no plugin panel in the entry chunk. The startup test must show a finite, positive first-editable time at 4× CPU. Until three clean baselines exist, the number is report-only; once promoted, the hard `STARTUP_BUDGET_MS` is the worst baseline plus 10%.

**Reviewer check:** A lower initial bundle with the same first-editable time is not a cold-start win. The startup mark must be after the canvas is actually usable, not at `DOMContentLoaded` or at the first React render. Do not preload all eight panels on mount.

---

## Task 6: Fuzz the `.add` parser and embedded asset/SVG boundary

**Follow-up:** #6 — fuzz the `.add` parser and embedded asset/SVG boundary.  
**Track:** Browser. **Blocks native release:** no.

**Goal:** Prove that hostile or malformed project files fail in a bounded, typed way and never hang, crash the renderer, or fetch a network URL. Keep valid unknown effects and legacy v1 files working.

**Files:**

- `editor/src/editor-core/project.fuzz.test.ts` — new deterministic parser fuzz.
- `editor/src/editor-core/imports.fuzz.test.ts` — new SVG/embedded-source fuzz.
- `editor/src/editor-core/project.ts` — wrap sanitizer errors as `ProjectFileError` where the parser currently leaks a generic `Error`.
- `editor/src/editor-core/imports.ts` — only if the sanitizer needs a bounded input or a typed error; do not change the public import UI behaviour.
- `editor/src/editor-core/project.test.ts` — keep the existing boundary cases; do not duplicate them in the fuzz file.

**Interfaces:**

```ts
// test-only
function mulberry32(seed: number): () => number;
const SEEDS = [0x5eed, 0xc0ffee, 0x12345678];

// existing app interfaces under test
parseProjectFile(source: string): EditorDocument;
sanitizeSvgMarkup(markup: string): string;
```

The fuzz suite is deterministic: fixed seeds, a fixed corpus and a fixed number of mutations. Do not use `Math.random()`; a failure must be reproducible from the test log.

**Required cases:**

- [ ] Malformed JSON: truncations, trailing commas, `null`, arrays, primitives, deeply nested arrays and huge strings. The only acceptable outcomes are a valid `EditorDocument` or `ProjectFileError`; no `TypeError`, `RangeError`, `SyntaxError`, stack overflow or hang.
- [ ] Header mutations: wrong `format`, `version` 0/1/3/999, missing `document`, `document` wrong type. v1 still migrates; unknown versions reject.
- [ ] Layer mutations: 0, 64 and 65 layers; 12 and 13 image layers; duplicate ids; empty ids; unsupported kinds; invalid blend/opacity/transform; NaN/Infinity; extreme but finite transforms.
- [ ] Embedded source mutations: valid PNG data URL; JPEG/WebP where allowed; non-data URL; malformed base64; wrong MIME; a data URL just over the 24,000,000-character source limit; a base64 payload that decodes to invalid bytes.
- [ ] SVG boundary: `<script>`, `<foreignObject>`, `on*` attributes, external `href`/`xlink:href`, `javascript:` URLs, malformed XML, and an SVG whose sanitized output is larger than its input. The parser must not fetch any URL; run with a fetch spy that fails the test if called.
- [ ] Effect boundary: >32 controls, overlong keys, non-finite numbers, invalid hex colours, unknown effect kinds. Unknown effects must round-trip as `raw` without nesting a new `unsupported` wrapper on each save.
- [ ] Length boundary: a source at the parser cap and one character over it. The over-cap case must reject before `JSON.parse`; do not allocate a second copy.
- [ ] Valid corpus: seed valid projects through the same mutation harness and require `parseProjectFile` to return a document; this catches a fuzzer that only proves rejection.

**Steps:**

- [ ] Add the seeded PRNG and corpus. Keep the run at 300 mutations per seed (900 total) so CI stays bounded; record the count in the test name/description.
- [ ] Add a `try/catch` assertion helper that accepts `ProjectFileError` only and reports the seed/mutation index on failure. Never catch and ignore.
- [ ] Fix the parser leak: `sanitizeImageSource` catches errors from `sanitizeSvgMarkup` and throws `ProjectFileError` with the layer label. The UI import path may keep its existing user-facing `Error`; the file parser must be typed.
- [ ] Add the SVG-specific fuzz with a `fetch`/`XMLHttpRequest` spy. The sanitizer must remove active content and reject external references before any load.
- [ ] Add the valid-corpus round-trip assertion, including an unknown effect, using `serializeProject` → `parseProjectFile`.
- [ ] Run the fuzz tests repeatedly with the same seeds to confirm determinism; run the full editor gate.

**Verification:**

```bash
cd editor && npx vitest run src/editor-core/project.fuzz.test.ts src/editor-core/imports.fuzz.test.ts
python3 tools/check_editor.py
```

The fuzz run must complete within the normal test timeout and report the mutation count. A test that catches all errors and asserts nothing is not acceptable. A parser crash that escapes as a non-`ProjectFileError` is a failure.

**Reviewer check:** Fuzzing that only generates random bytes rarely reaches the interesting boundary. Keep the structured mutations (layers, controls, data URLs, SVG) as first-class cases, not just random strings.

---

## Task 7: Shard the editor CI job without losing coverage

**Follow-up:** #7 — shard the roughly 10-minute editor CI job.  
**Track:** Browser. **Blocks native release:** no.

**Goal:** Split the editor job into unit/static checks, two Playwright e2e shards and an isolated production perf job. Keep the full spec count, the production preview, the one-worker perf isolation and the existing artifacts.

**Files:**

- `.github/workflows/checks.yml` — split the `editor` job; add `editor-e2e` matrix and `editor-perf`; update `review.needs`.
- `editor/playwright.config.ts` — no change expected; `TWIN_E2E_CI=1` and the two-worker setting stay.
- `editor/package.json` — no new script required; `npm run test:e2e:ci -- --shard=N/2` passes the shard argument through.
- `tools/tests/test_workflow_contract.py` — run; update only if it asserts the old job shape (it currently checks publish conditions, not editor job names).
- `tools/tests/test_check_workflows.py` — run; no change expected.
- `tools/check_workflows.py` — run; no change expected.

**Job shape:**

```yaml
editor:
  # checkout, setup-node, npm ci, python3 tools/check_editor.py
  # no browser install, no Playwright

editor-e2e:
  strategy:
    fail-fast: false
    matrix:
      shard: [1, 2]
  # checkout, setup-node, npm ci, install chromium
  # npm run test:e2e:ci -- --shard=${{ matrix.shard }}/2
  # upload editor-junit-${{ matrix.shard }}

editor-perf:
  # checkout, setup-node, npm ci, install chromium
  # npm run test:e2e:perf
  # upload editor-perf
```

`review` becomes `needs: [shelf, visual, editor, editor-e2e, editor-perf]`. `publish` keeps its current `needs: [shelf, visual]`; do not add editor jobs to the production publish gate as part of this task.

**Steps:**

- [ ] Keep the existing `editor` job name for the unit/static job so a branch-protection check named `editor` still resolves.
- [ ] Remove the “Install the pinned browser”, “The canvas specs” and “The production drag budget” steps from `editor`.
- [ ] Add `editor-e2e` with the matrix above. Install only Node and Chromium; run `npm run test:e2e:ci -- --shard=${{ matrix.shard }}/2`.
- [ ] Add `editor-perf` as a separate job with one worker via the existing performance config. Keep the production build and pointerup check; do not run it inside a shard.
- [ ] Upload `editor/test-results/` under `editor-junit-${{ matrix.shard }}` in e2e and `editor-perf` in perf. Keep `if: always()`.
- [ ] Update `review.needs`. Do not change `publish` or `handoff` conditions.
- [ ] Run the workflow checker and the local shard count check.

**Verification:**

```bash
python3 -m unittest discover -s tools/tests -v
python3 tools/check_workflows.py
cd editor && npx playwright test --list --shard=1/2
cd editor && npx playwright test --list --shard=2/2
```

The two shard lists must together equal the unsharded `npx playwright test --list` set. Then run the actual shards once the browser is installed:

```bash
cd editor && npm run test:e2e:ci -- --shard=1/2
cd editor && npm run test:e2e:ci -- --shard=2/2
```

Both must pass, and the production perf job must still pass on its own. If the two-shard wall time is not better than the current job, record the measurement and do not merge the split just for the label.

**Reviewer check:** Sharding is not a coverage reduction. The perf job must not share a runner with e2e; that was the reason for its one-worker isolation. The existing JUnit artifact name may be consumed by tooling; keep the original artifact for shard 1 or add a compatibility artifact if needed.

---

## Self-review

**Spec coverage:** All seven follow-ups are present and mapped in the table: save size (#1, Task 2), frame gate (#2, Task 3), autosave (#3, Task 4), device gate (#4, Task 1), cold start (#5, Task 5), fuzz (#6, Task 6), CI sharding (#7, Task 7). The native decision is recorded separately and does not replace any follow-up.

**Placeholder scan:** No `TBD`, no “add error handling”, no “write tests” without a named file or command. The 64-layer and first-editable ceilings are deliberately report-only until three clean 4× baselines exist; both have an explicit promotion rule and no invented number. The device task has an explicit blocked path.

**Type consistency:** `preloadPlugin`, `FrameStats`, `throttleCpu`, `startFrameProbe` and `stopFrameProbe` are used consistently across the task interfaces. `EditorPlugin.panel` is the only public type widened for lazy loading. Task 2 adds no public type or schema field.

**Failure modes covered:** save-write/unopenable, CPU-throttled frame pacing, last-write loss on hide, stale/blocked native candidate, bundle-only cold-start false win, hostile project file, shard coverage loss.

**Honesty check:** The plan does not claim a device pass, a startup number, a current bundle reduction or a CI time. Frame numbers appear only in the dated local measurement block, with their failing/report-only status and run-to-run variance. The 64-layer and first-editable gates are report-only until measured; the 209-test figure is explicitly marked as the old candidate's count, not the current tree's.

**Scope check:** No deploy, publish, commit, dependency or second-editor work is included. The browser work stays in `editor/`, `tools/` and `.github/`; the native work stays in the existing engine/build paths.

---

## Handoff

Native v2 is accepted as the app editor; the browser workbench remains a separate web surface. Task 1 stays **BLOCKED** until a real Android 13+ device is attached. Task 2 is **complete** and green (`9456452`). Task 3 is implemented and its 20-layer 4× phone gate is **green** without changing the budget: p95 16.8 ms, frame max 16.8 ms, max long task 0 ms over 700 frames, after moving the document out of the single store context, isolating the undo buttons and stopping closed drawers from rendering their panels. The 64-layer case is **promoted** from six clean runs (p95 16.7–16.8 ms throughout, max 16.8 / 33.4 / 33.4 / 50.0 / 16.8 / 16.8 ms) to hard ceilings of 18.5 ms p95 and 55 ms max, the worst observed value plus 10%; the 55 ms max is a deliberate ratchet 5 ms above the worst run, less than one frame. The desktop 20-layer assertion is **not measured** on this VM: the headless shell crashes at desktop widths on the Task 2 HEAD build too, so the crash is environmental and predates this change. CI on `1f8fe99` measured it on GitHub's ubuntu-24.04 runner in two consecutive attempts: frame p95 16.8 ms and max 50.0 ms both times, but the editor job failed the desktop long-task gate (`maxLongTaskMs` ≤ 50 ms) each time. The artifact now carries `longTasks`, `maxLongTaskMs` and `attribution` so the next failure records the actual value; the 50 ms gate is unchanged. Static gates are 29/29 green and the editor unit suite is 282/282. Do not raise the 64-layer ceilings without three new clean baselines, and do not call the desktop case green without evidence.

Execution method: subagent-driven task boundaries, with one review after Task 2 before the next task starts. This host exposed no coding subagent, so Task 2 was executed inline under the same task boundary and TDD gate; no dispatch is fabricated.
