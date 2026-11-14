# Phasing plan audit — 2026-11-14

**Scope:** `docs/superpowers/plans/2026-11-14-phasing-plan.md`, all 583 lines, 24 tasks,
the A7.1 sub-task and the three source plans.

**Verdict:** the plan is structurally coherent and the implemented lanes are closed. A1,
A2, A4–A7 and A7.1 are implemented and verified within their hardware limits; A3's
enforcing CSP is live and verified on the site and handoff after a clean browser report.
C0–C4 are complete. B1–B7 and B9–B12 are implemented, including the Studio deletion and
the final signed APK. B8 and B11's install-over remain blocked on a real device. Device
numbers and the full A7 sign-off therefore remain open, and this audit does not claim
those.

## Structure audit

| Check | Result |
|---|---|
| Phasing plan length | 583 lines |
| Lanes | 3: cleanup, BMO, rules/gates |
| Tasks | 24 + the A7.1 sub-task |
| Batches | 9 inside one phase |
| A7.1 register row | Present in the Lane A register |
| A7.1 full section | Present under A7 |
| A1/A2/A5 register rows | Marked done with evidence in the phasing plan |
| Dependency order | Coherent for C/B/A; A7.1 was completed early because it is docs/test-only |

**Finding:** A7.1 was registered as depending on C2, but it does not touch build output
or deleted artifacts. It was implemented before C2 without changing the cleanup gate.
The plan register now records it as done with no cleanup dependency.

## Task audit

### Lane C — cleanup

| Task | Status | Audit |
|---|---|---|
| C0 | **Done** | Protected set, hashes and sizes recorded in ignored `artifacts/cleanup/README.md` |
| C1 | **Done** | Guarded planner implemented; 9 tests and all three dry-runs pass; no deletion |
| C2 | **Done** | Wave 1 removed 59 files / 356,419,245 bytes; protected APKs survive |
| C3 | **Done** | Root and engine build trees plus staged assets removed; workspace 602 MB |
| C4 | **Done** | Screen-review and brand archived with hashes; reference/scratch trees removed |

### Lane B — BMO

| Task | Status | Audit |
|---|---|---|
| B1 | **Done** | `EngineShellState` reducer and 9 tests implemented; targeted Gradle test passes |
| B2 | **Done** | `EngineScreen.kt` is 599 lines; stage, inspector, dialogs and model are separate files; 24/24 goldens |
| B3 | **Done** | Model plus selection, motion and content command files; 209 tests and goldens green |
| B4–B7 | **Done** | Shell chrome, command sheet, plugin host/shelf/Tone and responsive panel host; tests and reviewed captures |
| B8 | **Blocked** | `adb devices` lists no device; `artifacts/bmo/DEVICE-REPORT.md` records the block without invented numbers |
| B9 | **Done (device candidate blocked)** | Release Gradle config, engine route and 6 `EngineRoutingTest` assertions pass |
| B10 | **Done** | `LegacyLookMigration` + 5 tests, the `LEGACY LOOKS` library, and the routing tests prove `StudioPane`, `app/studio-assets` and the Studio web assets are absent |
| B11 | **APK built; install-over blocked** | Signed `artifacts/ADD-4.0-bmo.apk`; signature and manifest verified; no device install |
| B12 | **Done** | Thumbnails, JPEG, share chooser, weight/family controls and 3D resets; tests and 24 goldens |

**Toolchain evidence:**

- JDK path from `$HOME/tools/JDK_PATH`: present, JDK 17.
- Android platform 35: present at `$ANDROID_SDK_ROOT/platforms/android-35`.
- `adb`: present at `$ANDROID_SDK_ROOT/platform-tools/adb`; `adb devices` lists no device.
- Android emulator: present, but not a device gate substitute.
- `tools/apk-signing.json`: present with the public release-certificate SHA-256 allowlist.

### Lane A — rules/gates/automation

| Task | Status | Audit |
|---|---|---|
| A1 | **Done** | Claims gate implemented, tested and wired into the fast path and after the engine gate |
| A2 | **Done** | Secret, workflow and dependency gates implemented; actions SHA-pinned; Gradle wrapper checksummed |
| A3 | **Enforced and live** | `check_headers.py`, `build_csp.py` and configs implemented; clean Chromium report; site and handoff enforce CSP live; `style-src 'unsafe-inline'` is residual risk |
| A4 | **Static done** | APK identity/certificate checks and WebView hardening implemented; device smoke blocked on B8 (no device) |
| A5 | **Done** | Static budget, image and motion gates implemented with a measured baseline |
| A6 | **Harness done** | Web timing and benchmark harnesses implemented; device numbers blocked on B8/B11 |
| A7 | **Wired** | Fast/live/perf paths, CI headers and delivery matrix wired; full sign-off blocked on A4/A6/B11 |
| **A7.1** | **Done** | Compact rules implemented and verified |

## A7.1 implementation evidence

| Change | Result |
|---|---|
| `AGENTS.md` | 143 lines, 13 headings; search-first, targeted reads, untrusted content, protected paths, personal data and final-diff review added |
| `tools/engine-spike/AGENTS.md` | Added engine-local commands and traps; root policy remains canonical |
| `docs/agent-reference.md` | Points to the engine-local file |
| `docs/agent-rules-examples.md` | Added untrusted-content and final-diff examples |
| `tools/check_agent_rules.py` | Enforces the 160-line ceiling and the new required strings; whitespace-normalized matching |
| Tests | 28 rules tests pass |
| Fast gate | 23 gates green in 10.5s after A1–A7 wiring |

**A7.1 commands and observed results:**

```text
python3 tools/check_agent_rules.py --check
rules: OK - 143-line contract, 13 headings, no stale paths or phrases

python3 -m unittest tools.tests.test_check_agent_rules tools.tests.test_agent_rules -v
Ran 28 tests ... OK

python3 tools/check_all.py
checks: 23 gates, all green in 10.5s
```

**Engine evidence (not a device claim):**

```text
bash tools/engine-spike/verify-suite.sh
engine: 209 tests, 0 failures, 0 errors, 0 skipped

bash tools/engine-spike/verify-chrome.sh
chrome: goldens match
```

## Why the full phase cannot be claimed

1. **B8/B11 require a device.** The plan itself says a VM/Robolectric number is not a
   device pass. `adb devices` lists no device, so the device gate and install-over test
   cannot run; the emulator is not a legal substitute.
2. **Device evidence cannot be synthesized.** B8, the install-over and full A7 sign-off
   need a real device, not another code change.
3. **A1–A7 are implemented to their verified limits but do not make the phase complete.**
   A4 device smoke needs B8, A6 device numbers need B8/B11, and A7 sign-off inherits
   those plus B11. BMO and cleanup are still separate lanes.

## A1–A7 implementation evidence

| Task | Files | Verification |
|---|---|---|
| A1 | `tools/check_claims.py`, `tools/tests/test_check_claims.py` | `claims:` pass; 7 tests; stale claims fail |
| A2 | `tools/check_secrets.py`, `tools/check_workflows.py`, `tools/check_dependencies.py`, three test modules, workflow pins, Gradle checksum | three `OK` gates; 16 tests; fake token/workflow fixtures fail |
| A3 | `tools/check_headers.py`, `tools/build_csp.py`, their tests, both `vercel.json` files | 21 inline scripts / 18 hashes; 13 tests; clean browser report; live enforcing headers and browser pass |
| A4 | `tools/check_apk.py`, `tools/tests/test_check_apk.py`, `app/src/add/ShelfActivity.java`, `tools/apk-signing.json` | release APK OK; 12 tests; device smoke blocked |
| A5 | `tools/check_web_budget.py`, `tools/check_images.py`, `tools/check_motion.py`, three test modules, `tools/web_budget.json` | 16-page measured baseline; 33 images; motion <=500 ms; 16 tests |
| A6 | `tools/check_web_perf.js`, its smoke test, `tools/engine-spike/bench.sh` | three-run web report; `bench.sh` BLOCKED with no device |
| A7 | `tools/check_all.py`, CI headers, `AGENTS.md`, reference/examples, rules test | 23 fast / 26 visual / 25 perf gates; 28 rules tests |

**Full verdicts:** `python3 tools/check_all.py` -> 23 gates green in 9.1s;
`python3 tools/check_all.py --visual` (with the workspace Playwright environment) -> 26
gates green in 78.0s, including 209 engine tests, chrome goldens and engine claims;
`python3 -m unittest discover -s tools/tests -v` -> 171 tests OK. The live enforcing
header probe and browser pass are recorded in the phasing plan. The
signed review APK is recorded in `FINAL-TEST.md`; `check_apk --release` is not claimed
because it validates the shipping catalog identity, not the review candidate.

## Next safe work

1. Connect a real device to run the BMO shell/plugin checklist, `bench.sh`, and the
   release install-over; then complete B8/B11 device evidence.
2. C2, C3 and C4 have run; the workspace is 602 MB. C3 was removed after the last
   green engine suite and chrome goldens.
3. `check_apk --release` still targets the shipping `3.9` catalog identity, not this
   review candidate; do not repoint it or claim it as a pass for `4.0-bmo`.
