# Workspace Cleanup Waves Implementation Plan

> **Execution control:** `docs/superpowers/plans/2026-11-14-phasing-plan.md` is the one-phase master plan; this document keeps the detailed cleanup steps.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover roughly 2 GB of ignored scratch space in three approved waves without deleting tracked source, current release artifacts, deploy inputs, or the BMO plan.

**Architecture:** Treat `artifacts/`, `dist/`, `build/` and the two engine `app/build` trees as disposable generated output. Use one guarded Python tool with an explicit allowlist and protect list; it plans first, prints every path and byte count, and deletes only with `--yes`. Run the waves around BMO tasks so no build or device gate is running while its outputs are removed.

**Tech Stack:** Python 3.12 standard library, `du`, `git`, `sha256sum`, existing `tools/check_all.py` and `tools/check_apk.py`.

**Spec:** `docs/superpowers/plans/2026-11-14-bmo-plan.md`, `AGENTS.md`, `docs/agent-reference.md`, and the 14 November 2026 workspace audit. This plan is a proposal; nothing below is approved for execution.

## Global Constraints

- Do not delete tracked files. If `git ls-files` lists it, this plan does not remove it.
- Do not delete `docs/`, `handoff/`, `figma/`, `app/src/`, app resources, tools source, or `docs/superpowers/plans/2026-11-14-bmo-plan.md`.
- Do not delete `docs/ADD-3.9.apk`, `dist/ADD-3.9.apk`, `docs/classic/`, or `artifacts/handoff-apk/ADD-3.9.1-field-preview.apk`.
- Do not delete `artifacts/bmo/` or a newer BMO candidate/report. Those are BMO gate evidence.
- Do not delete `app/studio-assets/` in this plan. BMO Task 10 owns that removal after migration.
- Do not run Vercel deployment deletion. `DEPLOYMENT_STORAGE.md` owns that separate approval path.
- A cleanup must never run while a Gradle, build, test, render, or deploy process is writing the same tree.
- A cleanup does not change a gate. If a protected path is missing, stop and report it; do not “repair” it by deleting more.
- Every wave ends with `python3 tools/check_all.py` and `git status --short`; both results are recorded.

## Review Focus

1. **The protected path is the current artifact:** a broad glob could remove `ADD-3.9.1-field-preview.apk` or the BMO report. Tests must assert protected files survive.
2. **The path escapes the workspace:** a symlink or `..` segment could turn a scratch cleanup into a source deletion. The tool must resolve paths and reject anything outside the expected roots.
3. **A build is running:** deleting `app/build` or `app/assets/www` during Gradle or `build.sh` produces a half-written tree. The plan has explicit scheduling and a process check.
4. **The cleanup becomes the only copy:** `reference-src`, `screen-review` and `brand` are evidence or research material. They are archived or approved individually before deletion, not treated as generic cache.
5. **A green check hides a missing artifact:** `check_all` can pass while `docs/ADD-3.9.apk` was removed because the site still links it. Verify APK hashes and the download page after Wave 1.

---

## Baseline snapshot

Measured 14 November 2026. Sizes are rounded; the audited workspace is 3.0 GB excluding `.git` directories.

| Area | Size | Decision |
|---|---:|---|
| `artifacts/` | 2.3 GB | Scratch; wave targets below |
| `tools/` | 459 MB | ~20 MB source + ~436 MB engine builds |
| `dist/` | 120 MB | 53 old APKs; keep only `ADD-3.9.apk` |
| `docs/` | 75 MB | **Keep** |
| `build/` | 62 MB | Generated; delete after BMO build work |
| `app/` | 38 MB | Source; `app/assets/www` is staged and rebuildable |
| `artifacts/reference-src/` | 1.1 GB | Reference clones; delete only after approval |
| `artifacts/paparazzi/` | 380 MB | Screenshot harness; delete only if unused |
| `artifacts/renderer/` | 188 MB | Test libraries; delete only if unused |
| `artifacts/screen-review/` | 152 MB | 139 MB screen recording; archive or approve deletion |
| APKs in `artifacts/`, `dist/`, `docs/` | 812 MiB / 81 files | Keep current 3.9 and 3.9.1; prune old |

## File structure

| File | Responsibility |
|---|---|
| `tools/cleanup_waves.py` | Allowlisted planner and remover for the three waves. |
| `tools/tests/test_cleanup_waves.py` | Temp-tree tests for protection, dry-run and path containment. |
| `docs/superpowers/plans/2026-11-14-cleanup-waves.md` | This plan and the recorded evidence. |

The tool does not replace shell review. Its only job is to make the allowlist and the byte report explicit.

### Task 0: Freeze the protected set

**Files:**
- Read: `README.md`, `docs/agent-reference.md`, `docs/superpowers/plans/2026-11-14-bmo-plan.md`
- Create: `artifacts/cleanup/README.md` (protected-list record; ignored)

**Interfaces:**
- Produces: the exact keep list every later task consumes.

- [ ] **Step 1: Record the keep list.**

  Include at minimum:
  ```text
  docs/
  handoff/
  figma/
  app/src/
  app/res/
  app/studio-assets/
  tools/**
  docs/ADD-3.9.apk
  dist/ADD-3.9.apk
  docs/classic/
  artifacts/handoff-apk/ADD-3.9.1-field-preview.apk
  artifacts/bmo/
  docs/superpowers/plans/2026-11-14-bmo-plan.md
  ```

- [ ] **Step 2: Record the current hashes and sizes.**

  Run:
  ```bash
  sha256sum docs/ADD-3.9.apk dist/ADD-3.9.apk \
    artifacts/handoff-apk/ADD-3.9.1-field-preview.apk
  du -sh artifacts dist build tools/engine-spike/app/build \
    tools/engine-review/app/build app/assets/www
  ```
  Expected: `docs/ADD-3.9.apk` and `dist/ADD-3.9.apk` share
  `1120bd53af07239861e555ed5f756dd791bcdbf119e92ac5ad178e03485065b1`.

- [ ] **Step 3: Confirm the BMO plan is untouched.**

  Run:
  ```bash
  stat -c '%s bytes, %y' docs/superpowers/plans/2026-11-14-bmo-plan.md
  git status --short
  ```
  Expected: 51,083 bytes and only the known plan/status entries.

### Task 1: Build the guarded cleanup tool

**Files:**
- Create: `tools/cleanup_waves.py`
- Test: `tools/tests/test_cleanup_waves.py`

**Interfaces:**
- Consumes: `Path`, `shutil`, `argparse`.
- Produces:
  ```python
  ROOT: Path
  WAVES: dict[int, tuple[str, ...]]
  KEEP: frozenset[Path]
  def protected(path: Path, root: Path = ROOT) -> bool: ...
  def wave_entries(wave: int, root: Path = ROOT) -> list[Path]: ...
  def apply(entries: list[Path], root: Path = ROOT) -> tuple[int, int]: ...
  ```
  `apply` returns `(removed_count, removed_bytes)` and removes files first, then empty
  directories. It never follows a symlink.

**Wave definitions:**

| Wave | Entries |
|---|---|
| 1 | Old `dist/*.apk` except `ADD-3.9.apk`; old `artifacts/handoff-apk/*` except `ADD-3.9.1-field-preview.apk`; all of `artifacts/engine-review`, `artifacts/emu`, `artifacts/handoff-archive`, `artifacts/engine-spike`, `artifacts/apk-test`, `artifacts/apk-check` |
| 2 | `tools/engine-spike/app/build`; `tools/engine-review/app/build`; `build/`; `app/assets/www` |
| 3 | `artifacts/reference-src`; `artifacts/paparazzi`; `artifacts/renderer`; `artifacts/screen-review`; `artifacts/brand` after an archive check |

- [ ] **Step 1: Write the failing tests.**

  Cover:
  ```python
  def test_protected_paths_are_never_planned()
  def test_wave_one_keeps_the_current_apks()
  def test_wave_two_targets_only_build_trees()
  def test_dry_run_changes_nothing()
  def test_apply_removes_only_planned_entries()
  def test_path_outside_root_is_rejected()
  def test_symlink_is_not_followed()
  def test_unknown_wave_is_an_error()
  ```

- [ ] **Step 2: Run the test and verify it fails.**

  Run:
  ```bash
  python3 -m unittest tools.tests.test_cleanup_waves -v
  ```
  Expected: import failure because `tools.cleanup_waves` does not exist.

- [ ] **Step 3: Implement the planner and remover.**

  Require `--wave {1,2,3}` and `--dry-run` or `--yes`. Print one line per entry:
  `remove <bytes> <path>` or `keep <path>`. Refuse an entry that is tracked, protected,
  outside the three allowed roots, or a symlink. `--dry-run` is the default when
  `--yes` is absent.

- [ ] **Step 4: Run the tests and the dry-run.**

  Run:
  ```bash
  python3 -m unittest tools.tests.test_cleanup_waves -v
  python3 tools/cleanup_waves.py --wave 1 --dry-run
  ```
  Expected: all tests pass; the plan lists only Wave 1 paths and the protected APKs are
  absent from the removal list.

- [ ] **Step 5: Commit.**

  ```bash
  git add tools/cleanup_waves.py tools/tests/test_cleanup_waves.py
  git commit -m "chore: add guarded workspace cleanup waves"
  ```

### Task 2: Execute Wave 1 — old release copies

**Files:**
- Modify: ignored `artifacts/`, `dist/`
- Verify: `README.md`, `docs/download/index.html`, `docs/data/tools.json`

**Interfaces:**
- Consumes: Task 1's `--wave 1`.
- Produces: ~700 MB nominal recovery and no change to the current APK.

- [ ] **Step 1: Stop all build and deploy processes.**

  Confirm no `gradlew`, `build.sh`, `deploy_vercel.py`, `deploy_review.py` or
  `render_pages.js` is writing the tree. Do not use a name-pattern kill; inspect and
  stop only the known process.

- [ ] **Step 2: Review the dry-run.**

  Run:
  ```bash
  python3 tools/cleanup_waves.py --wave 1 --dry-run
  ```
  Expected: old APKs and old engine/emulator folders only; both current APKs and
  `artifacts/bmo/` absent from the remove list.

- [ ] **Step 3: Apply Wave 1 after approval.**

  Run:
  ```bash
  python3 tools/cleanup_waves.py --wave 1 --yes
  ```
  Expected: the tool prints the removed count and bytes, and leaves the two current APKs.

- [ ] **Step 4: Verify release integrity.**

  Run:
  ```bash
  sha256sum docs/ADD-3.9.apk dist/ADD-3.9.apk
  python3 tools/check_apk.py --apk dist/ADD-3.9.apk \
    --catalog tools/catalog.json --published docs/data/tools.json
  python3 tools/check_all.py
  git status --short
  ```
  Expected: identical hashes, `check_apk: OK`, 15 gates green, and no tracked deletion.

### Task 3: Execute Wave 2 — build trees

**Files:**
- Modify: `tools/engine-spike/app/build`, `tools/engine-review/app/build`, `build/`, `app/assets/www`

**Interfaces:**
- Consumes: BMO Task 8 complete, or an explicit decision to rebuild from scratch.
- Produces: ~535 MB nominal recovery; the next build is slower.

- [ ] **Step 1: Schedule around BMO.**

  Run Wave 2 only after the BMO device candidate is captured and the engine baseline is
  recorded, or after the final BMO APK. Do not run it between Task 8 and the next build
  without accepting a cold rebuild.

- [ ] **Step 2: Review and apply.**

  Run:
  ```bash
  python3 tools/cleanup_waves.py --wave 2 --dry-run
  python3 tools/cleanup_waves.py --wave 2 --yes
  ```
  Expected: only the two `app/build` trees, root `build/` and staged `app/assets/www`.

- [ ] **Step 3: Prove the source still builds.**

  Run:
  ```bash
  bash tools/engine-spike/verify-suite.sh
  python3 tools/check_all.py
  git status --short
  ```
  Expected: 175 tests green; 23 gates green; no tracked change. A cold rebuild is the
  cost of this wave, not a failure.

### Task 4: Execute Wave 3 — research and evidence scratch

**Files:**
- Modify: `artifacts/reference-src`, `artifacts/paparazzi`, `artifacts/renderer`, `artifacts/screen-review`, `artifacts/brand`

**Interfaces:**
- Consumes: BMO Task 2 merged, `NATIVE_EDITOR_V2_REFERENCES.md` reviewed, and one
  explicit approval per evidence folder.
- Produces: up to ~1.9 GB nominal recovery.

- [ ] **Step 1: Archive before deleting evidence.**

  Copy `artifacts/screen-review/Screen_Recording_20261111_104837.mp4` and the selected
  `artifacts/brand/` sheets to the owner’s Drive or another durable store. Verify the
  copies before removing the local originals. Do not archive the whole folder blindly.

- [ ] **Step 2: Confirm references are no longer needed locally.**

  Check that `NATIVE_EDITOR_V2_REFERENCES.md` contains the pinned commits and decisions,
  and that BMO Task 2 has merged the group/matrix work. If either is incomplete, stop;
  keep `reference-src` until it is done.

- [ ] **Step 3: Review and apply the reduced set.**

  Remove `reference-src`, `paparazzi`, `renderer`, `screen-review` and the archived
  `brand` outputs only after the dry-run shows those exact paths:
  ```bash
  python3 tools/cleanup_waves.py --wave 3 --dry-run
  python3 tools/cleanup_waves.py --wave 3 --yes
  ```

- [ ] **Step 4: Verify the workspace and the plans.**

  Run:
  ```bash
  du -sh .
  python3 tools/check_all.py
  git status --short
  stat -c '%s bytes, %y' docs/superpowers/plans/2026-11-14-bmo-plan.md
  ```
  Expected: a smaller workspace; 15 gates green; only the planned untracked plan remains.

## C0/C1 result 2026-11-14

C0 recorded the keep list, hashes and sizes in ignored `artifacts/cleanup/README.md`. The
current APK copies share `1120bd53...065b1`; the field-preview APK is
`a395349b...656468`; the BMO plan is 51,083 bytes.

C1 added `tools/cleanup_waves.py` and nine temp-tree tests covering protection,
containment, dry-run, symlinks and apply. All three dry-runs pass:

```text
wave 1: old dist/handoff APKs and old engine/emulator scratch
wave 2: app/assets/www, build, tools/engine-{spike,review}/app/build
wave 3: artifacts/{brand,paparazzi,reference-src,renderer,screen-review}
```

No `--yes` command was run and no file was deleted. C2–C4 remain blocked on explicit
owner approval; Wave 3 additionally needs the recording and brand sheets archived first.

## Execution order

| Order | Action | Depends on |
|---:|---|---|
| 0 | Review and approve all three plans | owner review |
| 1 | Wave 1 | Task 1 tool tests pass |
| 2 | BMO Tasks 1–8 | Wave 1 complete |
| 3 | Wave 2 | BMO device candidate captured |
| 4 | BMO Tasks 9–12 | Wave 2 may run after Task 11 instead |
| 5 | Wave 3 | BMO Task 2 merged and evidence archived |

## Acceptance

- `du -sh` shows the expected reduction after each wave.
- `git status --short` has no tracked deletion.
- `python3 tools/check_all.py` is green after Waves 1 and 2.
- `python3 tools/check_apk.py` accepts the current APK after Wave 1.
- The BMO plan, current APKs, `docs/`, `handoff/`, `figma/` and `artifacts/bmo/` survive.
- No Vercel deployment was deleted by this plan.

## Rollback and limits

Ignored files have no Git history. The only rollback is a rebuild or a re-download.
`reference-src` can be re-cloned from the pinned commits, `paparazzi` and `renderer` can
be recreated by their test setup, and `app/assets/www` / `app/build` can be rebuilt. The
screen recording and brand sheets cannot be recreated from the repository; archive them
first or keep them.

## Self-review

- **Coverage:** current APK protection, BMO evidence, build trees, research clones,
  evidence archive, dry-run, path containment and post-wave checks each have a task.
- **Deliberate exclusion:** no source cleanup, no Studio deletion, no Vercel deletion and
  no automatic cleanup of tracked files.
- **No fake savings:** the report gives nominal wave sizes and measured `du` after each
  wave; it does not add overlapping folder sizes into a fabricated total.
