# Native Editor v2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote the native Kotlin/Filament engine into one safe, offline editor with correct grouping, motion paths that follow their layers, undo/autosave, real type controls, a layer tree, photo layers, and a restrained Material 3/Pajamas-informed shell.

**Architecture:** Keep the existing `EngineDocument` + Skia/Filament renderer and make the document model richer: `GroupLayer`, multi-selection, and artboard-space `MotionPath` data in schema v4. Split the 2,879-line `EngineScreen.kt` into stage, chrome, sheets, and layer-panel files before adding features. Use M3 behaviour and Pajamas rules selectively; do not switch the app to Google's default visual language.

**Tech Stack:** Kotlin 2.0.21, Compose BOM `2025.02.00`, Compose Material 2 currently, SceneView 2.3.0 / Filament 1.56, Android `org.json`, Robolectric 4.14.1 and Roborazzi 1.44.0.

**Spec:** `PLAN.md` (§1–7, §9–12), `tools/engine-spike/PLAN.md`, `tools/engine-spike/README.md`, and this document.

## Decision: native v2 committed

On 2026-11-11 the project owner chose the native v2 path. On 2026-11-12 the engine and field-gradient preview were made the committed track; `PLAN.md` now records the same decision.

- **Target:** native v2 becomes the only editor; Studio is frozen.
- **Current preview:** the Android 13+ review APK carries the field background and layer fill. It does not replace 3.9.
- **No dual engine:** never ship Studio and native v2 as two editors in one release. That is the failure mode `PLAN.md` §9 already rejects.
- **Implementation order:** engine foundation → field gradient quick win → signed resource packs.
- **Fallback:** if the reference audit or device gate finds a blocker, stop and report it; do not quietly fall back to Studio.

## Field gradient quick win

The field is a document recipe: up to eight colour spots with Gaussian reach, scale, distortion, swirl, speed, soften and noise. It applies to the artboard background or a type/vector fill, and stores in schema 6. The engine renderer is a bounded software Skia bitmap, cached by recipe and phase; the reference WebGL shader is a visual target, not a runtime dependency. Presets use original names and palettes. No MP4, brush engine, 3D change or resource pack is part of this first field milestone.

## Global Constraints

- Offline only: no account, no sync, no runtime network dependency.
- One editor at a time; no dual-engine release.
- Keep the native package separate (`app.add.engine`) until the integration decision; do not put Filament and the 28 MB debug dependency set into `app.offline.compress` before parity.
- Integration is a task, not an assumption: Task 10 merges the engine into `app.offline.compress` and deletes Studio; Task 11 produces the signed final testing APK, which is the only release artifact.
- Performance is a gate, not polish: 50 layers, three nested groups and one 2048 px photo must hold a 16 ms median frame time at 1080×1350 on the target phone. Record the number; do not estimate it.
- Keep Kotlin 2.0.21, Compose BOM `2025.02.00`, SceneView 2.3.0 and Filament 1.56 unless a time-boxed compatibility spike proves an upgrade.
- All existing engine tests must stay green. Current baseline: **166 tests, 0 failures, 0 errors**.
- No visual claim without a capture; no device claim without a device check.
- No third-party texture is bundled without a per-file source, licence and SHA-256 entry.
- No Material dynamic colour; ADD keeps graphite/cream/newsprint/stamp red.
- No Material or Pajamas component is adopted merely because it exists; it must solve a named editor problem.
- Reference code is read-only until licence review; GPL/MPL code is not copied into the app.

## Review Focus

1. **Group transforms:** grouping two layers, moving/rotating/scaling the group (including non-uniform scale and resulting skew), ungrouping, nested groups, hit-testing, undo and save/restore must all preserve absolute positions.
2. **Motion-path anchoring:** editing text, changing font size, dragging, rotating, changing zoom, switching selection, replaying and interrupting must not make the layer jump to a stale path start.
3. **Autosave/undo atomicity:** a crash during write, an old schema, a corrupt file and a failed save must never destroy the last good document.
4. **Touch arbitration:** one finger selects/drags, two fingers pinch/pan, the inspector sheet and native text fields keep focus, and the 3D `SurfaceView` does not eat the gesture.
5. **M3 upgrade risk:** adding Material 3/Expressive must not break SceneView, Robolectric captures or the existing Compose runtime; if it does, keep the current toolchain and implement M3-shaped custom components.

## Milestones and decision gates

| Milestone | Tasks | Gate |
|---|---|---|
| M0 Foundation | 0, 0b, 0c | Baseline green; audit reviewed; the M3/Compose/SceneView spike has a recorded pass/fail. |
| M1 Document | 1, 2, 3, 4 | `SelectionTest`, `GroupTest`, `MotionPathTest`, `DocumentRecoveryTest` and three geometry goldens pass. If group math is not green, stop and re-scope; do not start type or crop. |
| M2 Authoring | 5, 6 | Type and crop round-trip; export matches the stage at 1× and 2×. |
| M3 Shell | 7, 8 | Chrome goldens and accessibility tests pass; texture determinism passes. The M3 shell is the first scope to cut if time runs out. |
| M4 Ship | 9, 10, 11 | Device gate on the merged build; one editor; a signed final testing APK with a recorded SHA-256. |

**Delivery rule:** a task is done only when `verify-suite.sh` and `verify-chrome.sh` pass and, for anything that renders, a capture exists. The final testing APK is the deliverable, not a review build with a go/no-go note.

**Foundation already landed:** `LayerAffine.kt` (matrix composition, skew-aware decomposition, `grouped`/`flatten`, inverse hit-testing) and `MotionPath.kt` (cubic sampling, rigid clamping, group-local mapping) are in the engine with 14 JVM tests. The suite is 166 tests, 0 failures. Tasks 2 and 3 consume these helpers; they must not grow a second matrix or path implementation.

---

## What exists today

| Area | Shipped Studio (3.9) | Native engine lab (3.7 review) |
|---|---|---|
| Canvas | Fixed 540×675 Fabric canvas; pan; wheel zoom 0.8–1.4; no pinch | Skia artboard; 50–400% pinch/zoom and zoom pill |
| Type | Add/edit, gradient, outline, shadow, four blends; one default face; no size/leading/tracking/font picker | Text, size, leading, weight, alignment; no tracking, case or font picker |
| Photo | One background image, cover crop; not a layer | Real `ImageLayer` with fill/fit; no crop or per-layer adjustments |
| Layers | No layer list; GROUP groups every text layer; UNGROUP loses position | Layer panel, reorder, visibility and lock; flat list, no groups |
| Motion | One cubic path per text; playback; drag carries path; text edit does not | None |
| Safety | No undo, save, autosave or recovery | Undo/redo, JSON document save/reopen, saved-state label |
| Paint/effects | 13 procedural overlays; four blends; text gradient | 19 blends, gradients, patterns, masks, clipping, grade/blur/grain/halftone/duotone |
| 3D | None | Two GLB models, Filament viewport, PixelCopy export; no true transparency |
| UI | Custom graphite press shell; Material 2 not used | Compose Material 2 controls with custom graphite chrome |

The native path already starts ahead on compositing, photos, safety and touch zoom. It starts behind on grouping, motion and type range. That is why this plan is not a port of the Studio code.

## Source review: what is worth using

### Material 3 / M3 Expressive

Sources: [Material 3](https://m3.material.io/), [Toolbars](https://m3.material.io/components/toolbars/overview), [Bottom sheets](https://m3.material.io/components/bottom-sheets/overview), [Motion physics](https://m3.material.io/styles/motion/overview).

**Worth adopting**

- **Floating or docked toolbar** for the tool rail. The new toolbar variants are explicitly designed for many actions and can pair with a primary action; this fits a canvas tool strip better than a permanent Material bottom bar.
- **Bottom sheet** for the inspector/details surface. M3's compact-width guidance, optional drag handle and 48dp handle target match the existing peek/sheet model.
- **Split button** for EXPORT: the main action exports at the current scale; the attached menu chooses 1×/2× or format.
- **Button groups / segmented buttons** for alignment and small mutually exclusive choices.
- **Motion physics tokens** for chrome only: sheets, toolbars, selection tags and button state. Use spatial springs for movement and effects springs for opacity/colour; respect the system animation scale and reduced motion.
- **48dp targets, visible labels and predictable focus order** as accessibility requirements, not decoration.
- **Compose-first direction.** M3's own 2026 guidance says Android is Compose-first; that supports the native path rather than the WebView path.

**Not worth adopting**

- **Default M3 colour and dynamic colour.** ADD's identity is graphite/cream/newsprint/stamp red. Dynamic colour would erase it.
- **Wholesale M3 component skin.** A default Material app would look like every other Android app and would fight the existing press-sheet language.
- **Expressive shape morphs everywhere.** Use shape motion for chrome/press feedback, not artwork geometry. A drawing tool must not animate a layer's shape while the user is editing it.
- **M3 as an automatic dependency upgrade.** The current project uses `androidx.compose.material:material` (Material 2), Compose BOM `2025.02.00` and SceneView 2.3.0, which is pinned to Compose 1.7.8/Kotlin 2.0.21. The M3 Expressive toolbar and motion APIs may require a newer Material 3/Compose line. Treat that as a spike with a hard go/no-go gate; do not silently upgrade the stack and break 3D.

**Honest verdict:** M3 is useful for interaction patterns and motion language, not for the visual identity. Adopt the toolbar/sheet/split-button/segmented-control behaviour and spring tokens; build them with ADD's tokens.

### GitLab Pajamas

Sources: [Pajamas](https://design.gitlab.com/), [design-system philosophy](https://design.gitlab.com/about/design-system-philosophy), [component collection](https://design.gitlab.com/components/overview), [segmented control](https://design.gitlab.com/components/segmented-control), [tree](https://design.gitlab.com/components/tree).

**Worth adopting**

- **Core vs extended layer model.** Keep a small, stable core of editor primitives; allow experimental canvas patterns in an extended layer instead of promoting every idea immediately.
- **Component documentation discipline.** Every new control needs anatomy, when to use, when not to use, content rules, accessibility notes and a test.
- **Segmented-control rules:** 2–5 options, equal width and prominence, exactly one active, visible or accessible label, disabled options stay visible. This is a good fit for ALIGN and small tool modes.
- **Tree rules:** expansion/collapse, indentation, parent/child semantics and clear selection. This is the right model for grouped layers.
- **Shared responsibility for UI quality.** The design system supplies building blocks; the product team owns domain fit and research. That is exactly how the editor should use Pajamas.
- **Toast, drawer, popover, tooltip, skeleton and spinner patterns** are useful references for export status, secondary panels, property help and image loading.

**Not worth adopting**

- **GitLab's visual language.** Dense DevOps blue/grey is wrong for a print-inspired creative tool.
- **Vue/`@gitlab/ui` code.** This is an Android Compose app; copy the rules, not the implementation.
- **GitLab-specific navigation and permission patterns.** The editor has no projects, issues, roles or network state.

**Honest verdict:** Pajamas is the strongest source for governance, accessibility and component documentation. It is not a visual theme and not a code dependency.

### `tttamannaanand/stash`

Source: [github.com/tttamannaanand/stash](https://github.com/tttamannaanand/stash).

**What it actually is:** a README-only curated list of 398 free design resources. The repository page shows only `README.md` and `CONTRIBUTING.md`, four commits and no `LICENSE` file. It is a directory, not an editor, library or codebase.

**Worth using**

- The **licence badge model** is genuinely useful: green = commercial use/no credit, yellow = credit required, blue = free tool/plan, purple = mixed, check each item. This is a good policy shape for the editor's asset manifest.
- It points at useful categories and sources: M3, design-system galleries, icon sets, Poly Haven/ambientCG, patterns, textures, fonts and accessibility tools.
- It is useful as a **discovery index** for future asset packs and for finding the original licence page.

**Not worth using**

- It is not a dependency and should not be vendored, scraped or presented as a library.
- Its licence badges are a convenience, not a legal review; the README itself says licences can change. Every asset still needs the original source, licence text, attribution and hash.
- The list has link rot and “free” claims that can change. Do not build an offline product on live links.
- It does not solve grouping, motion, type or undo. It will not make native v2 smaller.

**Honest verdict:** useful as a shortlist and as a model for a licence manifest, not as implementation.

### Speckyboy texture roundup

Source: [40+ High-Resolution Packs of Free Textures for Designers in 2026](https://speckyboy.com/free-high-resolution-texture-packs/).

**What it actually is:** an editorial roundup of more than 40 texture packs, updated in 2026. It links to Pixel Buddha, Indieground, GraphicsFuel, Envato Elements, Gumroad, Behance, Dribbble and others. The page has an affiliate disclosure and does **not** provide a licence column for each pack.

**Worth using**

- The **categories** are a good checklist for the editor's texture library: paper, grit, vintage/retro, grunge, grain/noise, abstract/fractal, stone/concrete, metal, paint/graffiti, wood, fabric, black-and-white and miscellaneous.
- The **quality bar** is useful: high resolution, real material character, and textures that can be toned down rather than dominating the work.
- The application advice is correct: opacity, blend mode and context matter; texture should support hierarchy, not replace it.

**Not worth using**

- **Do not bulk-download or bundle these packs.** Many are “free” under site-specific terms, require attribution, are personal-use only, or sit beside paid items. The roundup grants no licence.
- **Do not scrape the preview images.** They are the publisher's assets, not the pack.
- **Do not assume “free” means CC0.** Check each source's current licence and keep a signed manifest.
- **Do not make an external texture pack a v2 dependency.** The app is offline and already has 13 procedural overlays; a seed-based procedural library is safer and smaller.

**Honest verdict:** a useful taxonomy and quality reference, not an asset source. If external textures are added, use only CC0/CC-BY sources with a per-file licence manifest; otherwise stay procedural.

## Reference projects to analyze

These are references, not dependencies. Licence is a gate: Apache-2.0 and MIT code can be adapted with the required notices; MPL-2.0 is file-level copyleft; GPL-3.0+ is read-only by default unless the app's distribution is GPL-compatible. No reference code enters the app until Task 0b records the exact commit, licence and files. The point is to avoid inventing group transforms, crop and motion-path semantics badly, not to copy another editor.

| Project | Licence | What to study | Verdict |
|---|---|---|---|
| [CanHub/Android-Image-Cropper](https://github.com/CanHub/Android-Image-Cropper) | Apache-2.0 | Kotlin `CropImageView`, `CropOverlayView`, EXIF/rotation, sampling and output | **Mandatory — prototype in Task 6.** Use its crop UI or matrix logic; keep the crop rect in the document and do not let the library own the image layer. |
| [SceneView](https://github.com/SceneView/sceneview-android) | Apache-2.0 | Filament lifecycle, `Scene`, `ModelNode`, `CameraNode`, Compose/View host | **Mandatory — keep.** Audited at the pinned 2.3.0 (`v2.3.0` tag). 2.3.4 is the last 2.x and bounds the main-thread `destroy()` wait, so treat that bump as its own spike; 4.67.0 is a different API line. Do not fork it. |
| [PhotoEditor](https://github.com/burhanrashid52/PhotoEditor) | MIT | Kotlin View stack, `UndoRedo`, text/sticker/save | **Optional — read-only.** v3.0.2 is useful for simple undo and view manipulation; it has no groups, motion, 3D or Compose document model. |
| [Compose Rich Editor](https://github.com/MohamedRejeb/Compose-Rich-Editor) | Apache-2.0 | `RichTextState`, spans, selection, built-in history | **Optional — do not integrate in v2.** Current 1.1.0 targets Compose 1.11/Kotlin 2.4; the 1.0.0-rc10 line is the only one near this project's Compose 1.7.x stack, and per-character rich text is not in scope. |
| [Fabric.js](https://github.com/fabricjs/fabric.js) | MIT | `Group`, object matrices, bounds, nested transforms, JSON | **Mandatory — best math reference for Task 2.** Port the reasoning, not the JavaScript. |
| [Excalidraw](https://github.com/excalidraw/excalidraw) | MIT | Group selection, transform handles, history, touch and tests | **Mandatory — interaction reference.** Use it to pressure-test selection and undo semantics; no Android code. |
| [Penpot](https://github.com/penpot/penpot) | MPL-2.0 | Document tree, groups, undo architecture, design tokens | **Optional — architecture reference only.** MPL is file-level copyleft; do not copy files without a licence review. |
| [Graphite](https://github.com/GraphiteEditor/Graphite) | MIT OR Apache-2.0 | `document/`, `node-graph/`, layer transforms, history | **Optional — future architecture.** Its node graph is more than v2 needs; read the document and history split. |
| [Glaxnimate](https://invent.kde.org/graphics/glaxnimate) | GPL-3.0+ | Motion along path, keyframes, auto-orient groups, undo | **Mandatory — motion reference only.** Read before Task 3; do not copy code into a non-GPL app. |

**Mandatory audit (all five, before Task 1):** Fabric.js group/matrix → Excalidraw group/history → Glaxnimate motion path → Android-Image-Cropper crop → SceneView lifecycle. These are the references that change a Task 2, 3, 6 or 3D decision; Task 0b is not complete until all five are pinned and reviewed. All five are now pinned in `NATIVE_EDITOR_V2_REFERENCES.md`.

**Optional:** Penpot and Graphite are second-pass architecture reads; PhotoEditor and Compose Rich Editor are evidence for rejecting a dependency, not integration targets.

**Not shortlisted:** Krita and Inkscape are GPL and enormous; they explain layer stacks but not Android implementation. Lottie is a player, not an editor. tldraw is a strong canvas reference, but its licence needs review before any code use; it is not a v2 dependency. None of these beats the shortlist for this plan.

## What to implement, and what not to

### Adopt

- M3 floating/docked toolbar, bottom sheet, split button, button group and motion springs, using ADD tokens.
- Pajamas segmented-control rules, tree semantics, component-documentation discipline and accessibility expectations.
- Stash's licence-badge idea as an internal `AssetLicence` manifest, not its list.
- Speckyboy's texture categories and quality bar as a procedural preset taxonomy.
- Native engine's existing Skia compositor, layer document, undo, document save, pinch zoom, photos and 3D.

### Reject

- Default M3 colours, dynamic colour, universal rounded cards and M3's default typography.
- Pajamas visual style and Vue components.
- Stash as a dependency or a source of code.
- Speckyboy packs as bulk assets, or preview images as textures.
- Any “upgrade Compose to get Expressive components” step without a SceneView/Robolectric spike.
- A second editor in the shipped APK.

## Target scope for v2

**In:** multi-selection; groups and a layer tree; motion paths with correct anchoring; undo/redo and atomic autosave; font/size/leading/tracking/alignment/case; photo crop; the existing 19 blends/masks/effects; pinch zoom; M3/Pajamas-informed chrome; procedural texture presets; device verification; a signed final testing APK of the single editor.

**Out:** brushes/pen, pages/artboards, components/variants, web/UI frames, 3D import, video/GIF export, cloud sync, collaboration, texture-store downloads.

## File structure

| File | Responsibility |
|---|---|
| `NATIVE_EDITOR_V2_BASELINE.md` (new) | Task 0 baseline evidence and decision record. |
| `NATIVE_EDITOR_V2_REFERENCES.md` (new) | Task 0b pinned commits, licences, files read and adopt/adapt/reject decisions. |
| `tools/engine-spike/app/src/main/java/app/add/engine/EngineDocument.kt` | Document state, selection, commands, undo snapshots. Keep under control; extract selection/group/motion helpers. |
| `.../EngineSelection.kt` (new) | `selectedIds`, `editingGroupId`, additive and group selection, group entry/exit and selection restoration. |
| `.../LayerAffine.kt` (new) | Affine matrix with rotation, skew and non-uniform scale; compose, invert, decompose, skew-aware bounds and inverse hit-testing. No Android types. |
| `.../LayerGroup.kt` (new) | `GroupLayer`, its place in the sealed hierarchy, group/ungroup commands and recursive traversal. Uses `LayerAffine` rather than duplicating the math. |
| `.../MotionPath.kt` (new) | Cubic path data, bounds, translation, sampling and serialisation primitives. |
| `.../EngineMotion.kt` (new) | Playback clock, interruption and path/layer anchoring. |
| `.../EngineScreen.kt` | Shell composition only; target under ~500 lines. |
| `.../EngineStage.kt` (new) | Artboard, pointer gestures, selection frame, path handles, 3D layer host. |
| `.../EngineChrome.kt` (new) | Top bar, tool pill/toolbar, status, zoom. |
| `.../EngineSheets.kt` (new) | Inspector, layers, export, documents, add sheet, history, legend. |
| `.../EngineLayerPanel.kt` (new) | Recursive layer tree, visibility, lock, selection, reorder. |
| `.../EngineTheme.kt`, `.../EngineTokens.kt` | ADD colour/space/shape/motion tokens; no dynamic colour. |
| `.../DocumentCodec.kt` | Schema v4: groups with scale/skew, motion, type fields, photo crop, migrations from 3. |
| `.../ArtboardPainter.kt`, `.../LayerCompositor.kt` | Recursive group rendering and photo crop; preserve current flat fast path. |
| `src/test/java/app/add/engine/SelectionTest.kt` | Additive and group selection, group entry/exit and restoration. |
| `src/test/java/app/add/engine/GroupTest.kt` | Group/ungroup/nested transforms. |
| `src/test/java/app/add/engine/MotionPathTest.kt` | Sampling, translation, clamping, playback. |
| `src/test/java/app/add/engine/DocumentRecoveryTest.kt` | Schema, autosave, corruption, atomic write. |
| `src/test/java/app/add/engine/TextStyleTest.kt` | Font, size, leading, tracking, case, fallback. |
| `src/test/java/app/add/engine/ImageCropTest.kt` | Crop/fill/fit and export pixels. |
| `src/test/java/app/add/engine/EngineChromeTest.kt` | 48dp targets, labels, semantics, motion-scale behaviour. |

No new top-level editor. No second `MainActivity`. No new app package until Task 10, which owns the merge.

`EngineStudioChrome.kt` (952 lines) and `EngineStudioInspector.kt` (928 lines) already hold chrome and sheets. Task 1 merges them into `EngineChrome.kt` and `EngineSheets.kt` and deletes the old pair. Leaving both pairs alive does not count as a split.

## Tasks

### Task 0: Baseline the approved native path

**Files:**
- Create: `NATIVE_EDITOR_V2_BASELINE.md`
- Read: `PLAN.md`, `tools/engine-spike/README.md`, `tools/engine-spike/PLAN.md`
- Test: existing suite and chrome guard

**Interfaces:**
- Produces: a recorded baseline commit, test totals, capture paths, and the approved native v2 decision.

- [ ] **Step 1: Record the current commit and working tree.**
  Run: `git rev-parse HEAD && git status --short`
  Expected: a SHA and no unrelated modified files.

- [ ] **Step 2: Run the native suite.**
  Run:
  ```bash
  export JAVA_HOME="$(cat "$HOME/tools/JDK_PATH")"
  export ANDROID_SDK_ROOT="$HOME/android-sdk" ANDROID_HOME="$HOME/android-sdk"
  bash tools/engine-spike/verify-suite.sh
  ```
  Expected: `engine: 60 tests, 0 failures, 0 errors, 0 skipped` (or a larger number after new tests).

- [ ] **Step 3: Run the chrome goldens.**
  Run: `bash tools/engine-spike/verify-chrome.sh`
  Expected: the Roborazzi comparison passes; no missing baseline.

- [ ] **Step 4: Write the baseline file.**
  Record: commit, date, 60-test result, chrome result, which captures exist, and the known unverified device checks. Do not claim a device pass.

- [ ] **Step 5: Record the approved decision.**
  Native v2 is approved for planning. Studio stays shipped until parity. Do not start Task 1 until the reference audit in Task 0b is reviewed.

- [ ] **Step 6: Commit the baseline.**
  ```bash
  git add NATIVE_EDITOR_V2_BASELINE.md
  git commit -m "chore: baseline the native editor before v2"
  ```

### Task 0b: Audit the five mandatory references (planning only)

**Files:**
- Read: `NATIVE_EDITOR_V2.md` reference table; selected source files from the five mandatory projects: Fabric.js, Excalidraw, Glaxnimate, Android-Image-Cropper and SceneView. PhotoEditor, Penpot and Graphite are optional.
- Create: `NATIVE_EDITOR_V2_REFERENCES.md` — pinned commit, SPDX licence, files read, adopt/adapt/reject decision.
- Test: none; no production code.

**Interfaces:**
- Consumes: Task 0 baseline.
- Produces: one decision per mandatory reference (`reuse`, `adapt`, or `read-only`) and a short optional-notes section; no code copied yet.

- [ ] **Step 1: Pin each reference to a commit and licence.**
  Record the URL, commit SHA, SPDX licence and whether the licence permits code reuse. Do not treat “free” or “open source” as a licence.

- [ ] **Step 2: Read the group implementations.**
  Read Fabric.js `Group`/matrix code and Excalidraw group/selection/history. Confirm the order of bounds union, inverse transforms and nested rotation before Task 2.

- [ ] **Step 3: Read the motion implementation.**
  Read Glaxnimate's motion-along-path and auto-orient behaviour. Confirm whether the plan's rigid-translation rule matches real editor behaviour before Task 3.

- [ ] **Step 4: Read the Android candidates.**
  Read Android-Image-Cropper's crop/EXIF/rotation path and SceneView's lifecycle and camera nodes. Decide dependency versus read-only reference for each. PhotoEditor's undo stack is optional evidence, not a mandatory read.

- [ ] **Step 5: Write the audit and stop.**
  Commit `NATIVE_EDITOR_V2_REFERENCES.md` only. All five mandatory entries must be present. Do not copy GPL/MPL code. Do not start Task 1 until the audit is reviewed.

### Task 0c: M3 / Compose / SceneView compatibility spike (time-boxed)

**Files:**
- Modify: `tools/engine-spike/app/build.gradle.kts` on a disposable branch only
- Test: `EngineFrameCaptureTest`, the GLB/3D smoke test

**Interfaces:**
- Produces: a recorded pass/fail, the exact versions tried, and the decision Task 7 consumes.

This is the cheapest decision in the plan and it constrains the shell that Task 1 splits. Running it seventh risks rebuilding that shell for nothing.

- [ ] **Step 1: Branch and add Material 3.**
  Add the M3 artifact at a version compatible with Compose BOM `2025.02.00`. Do not upgrade Compose, Kotlin or SceneView in this spike.

- [ ] **Step 2: Exercise the real components.**
  Put `HorizontalFloatingToolbar`, `ModalBottomSheet`, `SplitButton`, `SegmentedButton` and `MotionScheme` on one disposable screen with the ADD tokens, not on the editor.

- [ ] **Step 3: Run the guards.**
  Run: `bash tools/engine-spike/verify-chrome.sh` and the GLB/3D smoke test.
  Expected: goldens identical; a Filament surface still attaches; no Robolectric native-graphics failure.

- [ ] **Step 4: Decide and delete the branch.**
  Adopt M3 if the guards pass; otherwise keep the current toolchain and build `EngineFloatingToolbar`, `EngineSheet`, `EngineSplitButton` and `EngineSegmentedControl` with M3 behaviour. A crash in the 3D host is a fail, not something to work around.

- [ ] **Step 5: Record the result.**
  Add the versions, the pass/fail and the reason to `NATIVE_EDITOR_V2_REFERENCES.md` and commit only the note.

### Task 1: Split the screen and add additive selection

**Files:**
- Modify: `EngineScreen.kt` — keep only shell composition (target under ~500 lines)
- Create: `EngineStage.kt`, `EngineChrome.kt`, `EngineSheets.kt`
- Create: `EngineSelection.kt`
- Delete: `EngineStudioChrome.kt`, `EngineStudioInspector.kt` once their content has moved
- Test: `SelectionTest.kt`, `EngineChromeTest.kt`

**Interfaces:**
- Consumes: current `EngineDocument.select(id)` and `selectedId`.
- Produces:
  ```kotlin
  val selectedIds: Set<String>
  val editingGroupId: String?        // the group entered for child editing; null = artboard level
  val selectedGroupId: String?       // derived: the group the stage frames, when the whole selection shares one
  fun select(id: String, additive: Boolean = false): Boolean
  fun clearSelection()
  fun selectedLayers(): List<Layer>
  fun enterGroup(groupId: String)
  fun exitGroup()
  ```
  `selectedId` remains the primary layer for the inspector; `selectedIds` is the set used by grouping and the layer tree. `editingGroupId` is the Excalidraw-style entered-group state: at artboard level a tap on a grouped layer selects its top-level group, a double-tap (or the tree chevron) enters that group and selects the tapped child, and Back/Escape leaves one level and selects the group it left. `selectedGroupId` is derived from the selection and the editing level, never stored separately, and is non-null only when every selected layer sits in the same group. Entering or leaving a group changes selection state only; it is not a document edit and must not create an undo entry.

- [ ] **Step 1: Write failing selection tests.**
  Assert: tapping a second layer with additive selection selects both; primary stays the last tapped; clearing empties the set; selecting a locked layer is refused; selection survives undo. Group entry and group selection are named cases in `SelectionTest`:
  - `tappingAChildOfAGroupSelectsTheGroup`
  - `doubleTapEntersTheGroupAndSelectsTheChild`
  - `backLeavesOneGroupAndSelectsIt`
  - `enteringOrLeavingAGroupIsNotADocumentEdit`
  - `selectedGroupIdNeedsTheWholeSelectionInOneGroup`

- [ ] **Step 2: Run the test and confirm it fails for the missing API.**
  Run: `cd tools/engine-spike && ./gradlew :app:testDebugUnitTest --tests app.add.engine.SelectionTest`
  Expected: compile failure or assertion failure naming `selectedIds`.

- [ ] **Step 3: Implement the selection state and split files.**
  Move stage, chrome and sheets without changing behaviour. Keep `EngineScreen.kt` as the composition root and route the existing tests through it. Merge `EngineStudioChrome.kt` and `EngineStudioInspector.kt` into the new files in the same commit; the old pair is deleted, so there is never a second chrome path. Add `editingGroupId`, the derived `selectedGroupId`, and the enter/exit commands, with the stage drawing one group frame rather than per-child handles while a group is selected. Route a tap through the editing level, not through the flat layer list: inverse-map the pointer through the editing group's matrix and test the children, never their axis-aligned bounds.

- [ ] **Step 4: Run selection and existing tests.**
  Run: `bash tools/engine-spike/verify-suite.sh`
  Expected: all previous tests plus the new selection tests pass.

- [ ] **Step 5: Commit.**
  ```bash
  git add tools/engine-spike/app/src/main/java/app/add/engine/EngineScreen.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineStage.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineChrome.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineSheets.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineSelection.kt \
    tools/engine-spike/app/src/test/java/app/add/engine/SelectionTest.kt
  git commit -m "refactor: split the native editor and add multi-select"
  ```

### Task 2: Add groups and a real layer tree

**Files:**
- Create: `LayerGroup.kt`
- Modify: `EngineDocument.kt`, `EngineStage.kt`, `EngineLayerPanel.kt`, `ArtboardPainter.kt`, `LayerCompositor.kt`, `DocumentCodec.kt`
- Test: `GroupTest.kt`, `DocumentCodecTest.kt`

**Interfaces:**
- Consumes: `selectedIds`, `select(id, additive)`.
- Produces:
  ```kotlin
  data class GroupLayer(
      override val id: String,
      override val name: String,
      override val visible: Boolean = true,
      override val x: Float,
      override val y: Float,
      override val w: Float,
      override val h: Float,
      override val opacity: Float = 1f,
      override val rotation: Float = 0f,
      override val skewX: Float = 0f,
      val scaleX: Float = 1f,
      val scaleY: Float = 1f,
      val children: List<Layer>,
      override val style: LayerStyle = LayerStyle(),
  ) : Layer

  fun group(ids: Set<String>, name: String = "GROUP"): String?
  fun ungroup(groupId: String): Boolean
  ```
  All layer types gain `skewX: Float = 0f`. `GroupLayer` adds `scaleX`/`scaleY`; its transform is `T(center) * R(rotation) * SkewX(skewX) * S(scaleX, scaleY)` around its centre. The same matrix is used by the painter, compositor, hit-testing and bounds. Grouping requires at least two top-level selected layers. Grouping inserts the new `GroupLayer` at the index of the highest selected layer in paint order, removes the children from that level, and selects the group; ungrouping reinserts the children at the group's index in their existing order and leaves them selected at the parent level. The group's local bounds are the union of the children's bounds; world bounds apply the group matrix. Children store positions relative to the group origin. Grouping converts each child's world transform into group-local space; ungrouping applies `G` to the child, decomposes to `x/y/w/h/rotation/skewX`, and reinserts at the group's index. Non-uniform group scaling is supported; the child `skewX` is what preserves a rotated child. If Filament cannot represent the shear for a 3D layer, apply the group matrix to the node or block that combination with a clear message; do not silently drop it.

Hit-testing inverse-maps the pointer into group-local space and tests the children there, so the empty corners of a skewed group's bounding box are not tappable. Group opacity is applied as one isolated layer over the artboard backdrop (`saveLayer` semantics), not per child: two children with different blend modes must not blend into each other through a translucent group. The same isolation applies in export. Motion paths owned by children are stored in group-local space, so a group transform carries them without rewriting any point.

- [ ] **Step 1: Write failing group tests.**
  Assert: grouping two text layers preserves each layer's absolute bounding box; moving/rotating/uniformly and non-uniformly scaling the group moves both children without drift; a rotated child inside a non-uniformly scaled group keeps its absolute geometry after ungroup; nested group flattening preserves world positions; undo restores the pre-group tree; save/load round-trips the tree and scale/skew. Z-order and group selection are named cases in `GroupTest`:
  - `groupingInsertsTheGroupAtTheHighestSelectedIndex`
  - `ungroupingReinsertsChildrenAtTheGroupIndex`
  - `ungroupingKeepsTheChildrensRelativeOrder`
  - `groupingSelectsTheGroupAndUngroupingLeavesIt`
  - `groupOpacityIsolatesAgainstTheArtboardNotTheChildBehind`
  - `aSkewedGroupsEmptyCornerIsNotTappable`
  - `movingAGroupCarriesItsChildrensMotionPaths`

- [ ] **Step 2: Run the tests and confirm the expected failure.**
  Run: `cd tools/engine-spike && ./gradlew :app:testDebugUnitTest --tests app.add.engine.GroupTest`
  Expected: missing `GroupLayer`/`group` or wrong bounds.

- [ ] **Step 3: Implement `GroupLayer` and commands.**
  Update `when` branches, recursive traversal, hit-testing, duplicate/delete, painter and compositor. Use the Task 0b Fabric.js/Excalidraw notes for transform order. Use the already-tested `LayerAffine` for `world`/`local` conversion (`grouped`, `flatten`, `decompose`); it supports `scaleX != scaleY`, writes the resulting `skewX`, and inverse-maps hit tests. Do not add a second matrix helper. Do not make a group a flat visual convenience; it must carry a real transform.

- [ ] **Step 4: Add recursive codec support.**
  `kind: "group"` with `children`, `scaleX` and `scaleY`; every layer kind gets `skewX`; bump `DocumentCodec.VERSION` to 4; decode v1–v3 with scale `1` and skew `0`. Unknown fields stay ignored as today.

- [ ] **Step 5: Replace the flat layer panel with the recursive tree.**
  Use the Pajamas tree rules: chevron, indentation, parent/child selection, visible/lock controls, reorder within the parent. Keep a flat fallback when the group has no children.

- [ ] **Step 6: Run group, codec and full suite.**
  Run: `bash tools/engine-spike/verify-suite.sh`
  Expected: all green, including the old document tests.

- [ ] **Step 7: Commit.**
  ```bash
  git add tools/engine-spike/app/src/main/java/app/add/engine/LayerGroup.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineDocument.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineStage.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineLayerPanel.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/ArtboardPainter.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/LayerCompositor.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/DocumentCodec.kt \
    tools/engine-spike/app/src/test/java/app/add/engine/GroupTest.kt
  git commit -m "feat: add real layer groups and a tree"
  ```

### Task 3: Add motion paths that cannot go stale

**Files:**
- Create: `MotionPath.kt`, `EngineMotion.kt`
- Modify: `EngineDocument.kt`, `EngineStage.kt`, `EngineSheets.kt`, `DocumentCodec.kt`
- Test: `MotionPathTest.kt`

**Interfaces:**
- Consumes: selected layer's centre, `Layer` transform, `EngineClock`.
- Produces:
  ```kotlin
  data class MotionPoint(val x: Float, val y: Float)
  data class MotionPath(
      val start: MotionPoint,
      val c1: MotionPoint,
      val c2: MotionPoint,
      val end: MotionPoint,
      val durationMs: Int = 1400,
  )
  fun setMotionPath(layerId: String, path: MotionPath)
  fun resetMotionPath(layerId: String)
  fun playMotion(layerId: String)
  fun translateMotionPath(layerId: String, dx: Float, dy: Float)
  ```
  Path points are stored in the coordinate space of the layer's parent: artboard coordinates at the top level, group-local coordinates inside a group. A path belongs to one layer ID. The path start is the layer centre when created. Any layer-centre change in its own space translates the whole path by the same delta. A group move/scale/rotate must not rewrite child paths at all: the group matrix carries them at paint and hit-test time. If translation would take the path outside the artboard, translate the whole path back by one rigid offset; never clamp individual points. This is the rule that stops group transforms from producing the stale-path bug Task 3 exists to remove.

- [ ] **Step 1: Write failing path tests.**
  Assert: a path starts at the layer centre; dragging the layer translates all four points by the same delta; editing text from one line to five lines re-anchors the path to the new centre; rotating a non-square layer keeps the path valid; dragging mid-play stops playback and starts the next gesture at the actual centre; reduced-motion playback jumps to the path end.

- [ ] **Step 2: Confirm failure.**
  Run: `cd tools/engine-spike && ./gradlew :app:testDebugUnitTest --tests app.add.engine.MotionPathTest`
  Expected: no `MotionPath` type.

- [ ] **Step 3: Implement the path model and pure sampling.**
  Read Glaxnimate's motion-along-path model first (Task 0b); use the already-tested `MotionPath`/`MotionAnchoring` for sampling, rigid clamping and group-local mapping instead of writing a second sampler. No GSAP, no WebView. Keep the path UI independent of the renderer. Glaxnimate derives auto-orient from the path tangent and its Follow Path command bakes keyframes; v2 does neither. The path stays live artboard-space data sampled per frame, and the layer keeps rigid translation only — rotation-along-path is out of scope and must never be written into `rotation`.

- [ ] **Step 4: Wire anchoring into every centre-changing command.**
  Call the same `translateMotionPath` from `moveSelected`, `nudge`, `setText`, `setFontSize`, `setBounds`, `setRotation`/centre-preserving rotation, and undo restore. Translate in the layer's parent space. A group transform calls nothing here; its matrix already carries the children's paths. This is the bug from the Studio audit; it must not be fixed only in the drag handler. Named cases: `movingAGroupCarriesItsChildrensPaths` and `editingTextInsideAGroupKeepsItsPathAnchored`.

- [ ] **Step 5: Add the stage editor and playback.**
  Four handles, a visible curve when selected, `PLAY PATH`, `RESET PATH`, clear/close. Interrupt on any new gesture. Respect `ANIMATOR_DURATION_SCALE`; at 0, jump to the end without a tween.

- [ ] **Step 6: Serialise and migrate.**
  Add `motion` to schema v4; old documents have no motion. A path for a missing layer is dropped on decode and reported once in diagnostics.

- [ ] **Step 7: Run path and full suite.**
  Run: `bash tools/engine-spike/verify-suite.sh`
  Expected: all green, including the old 60.

- [ ] **Step 8: Commit.**
  ```bash
  git add tools/engine-spike/app/src/main/java/app/add/engine/MotionPath.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineMotion.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineDocument.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineStage.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineSheets.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/DocumentCodec.kt \
    tools/engine-spike/app/src/test/java/app/add/engine/MotionPathTest.kt
  git commit -m "feat: add motion paths that follow their layers"
  ```

### Task 4: Make undo and autosave cover the new document

**Files:**
- Modify: `EngineDocument.kt`, `DocumentCodec.kt`, `EngineScreen.kt`
- Test: `DocumentRecoveryTest.kt`, `DocumentCodecTest.kt`

**Interfaces:**
- Consumes: layers, groups, motion paths, `selectedIds`, zoom, locked IDs.
- Produces:
  ```kotlin
  data class EditorSnapshot(
      val layers: List<Layer>,
      val motion: Map<String, MotionPath>,
      val selectedIds: Set<String>,
      val editingGroupId: String? = null,
      val zoom: Float,
      val locked: Set<String>,
  )
  ```
  Undo/redo restores the full snapshot, including group structure, motion paths, selection and the entered group. This extends the snapshot stacks that already exist in `EngineDocument` (`ArrayDeque<List<Layer>>`, pushed by `commit()`); it is not a new history system. Autosave writes a temp file, fsyncs, then renames; a failed write leaves the previous file untouched. Selection is recorded but is not an edit: a commit whose document payload (layers and motion) equals the current snapshot pushes no history entry and leaves the redo stack intact, so tagging a different layer, grouping already-grouped layers or ungrouping an empty selection can never be undone as though it changed the document. `ensureSelection()` currently falls back to the first layer; with multi-select and group entry it must clear the selection instead of stealing focus. Schema v4 persists `selectedIds` and `editingGroupId` so a reopen matches the last snapshot.

- [ ] **Step 1: Write failing recovery tests.**
  Assert: undo after group/ungroup restores the tree and selection; undo after path edit restores the path; autosave survives a simulated crash between temp write and rename; a corrupt file falls back to the last good document; a v3 file opens; a v4 file with an unknown group kind is refused, not half-decoded. The history guard has its own named cases in `DocumentRecoveryTest`:
  - `changingSelectionAddsNoUndoEntry`
  - `changingSelectionKeepsTheRedoStack`
  - `groupingAlreadyGroupedLayersIsANoOp`
  - `ungroupingWithNothingSelectedIsANoOp`
  - `undoRestoresTheEditingGroupAndSelection`

- [ ] **Step 2: Confirm failure.**
  Run: `cd tools/engine-spike && ./gradlew :app:testDebugUnitTest --tests app.add.engine.DocumentRecoveryTest`
  Expected: missing `EditorSnapshot` or failed recovery assertions.

- [ ] **Step 3: Implement snapshot-based history.**
  Extend the existing snapshot stacks rather than replacing them. Coalesce a gesture into one command; keep text edits on a short debounce; keep the existing 100-command limit if it exists, otherwise define 100 and test it. Record a snapshot only when its document payload differs from the current one, and never clear redo for a selection-only change; restoring a snapshot restores `selectedIds` and `editingGroupId` with it.

- [ ] **Step 4: Implement atomic save/restore and debounce.**
  Write `document.json.tmp`, close, rename to `document.json`; keep `document.json.bak` as the last good file; debounce 300 ms and flush on pause. Save a visible `SAVING…`/`SAVED hh:mm`/`SAVE FAILED` state.

- [ ] **Step 5: Run recovery and full suite.**
  Run: `bash tools/engine-spike/verify-suite.sh`
  Expected: all green.

- [ ] **Step 6: Commit.**
  ```bash
  git add tools/engine-spike/app/src/main/java/app/add/engine/EngineDocument.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/DocumentCodec.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineScreen.kt \
    tools/engine-spike/app/src/test/java/app/add/engine/DocumentRecoveryTest.kt
  git commit -m "feat: make undo and autosave cover the whole document"
  ```

### Task 5: Add real type controls and fonts

**Files:**
- Modify: `EngineDocument.kt`, `DocumentCodec.kt`, `ArtboardPainter.kt`, `EngineSheets.kt`
- Create: `TypefaceRegistry.kt`
- Test: `TextStyleTest.kt`

**Interfaces:**
- Consumes: bundled `Poppins` resources and installed font packs.
- Produces:
  ```kotlin
  data class TextLayer(
      ...,
      val fontFamily: String = "Poppins",
      val tracking: Float = 0f,
      val textCase: TextCase = TextCase.None,
  )
  enum class TextCase { None, Upper, Lower }
  ```
  `TypefaceRegistry.resolve(family, weight)` returns a bundled face, a pack face, or the Poppins fallback with a visible "substituted" notice.

- [ ] **Step 1: Write failing style tests.**
  Assert: tracking changes measured width; case conversion is display-only and source text survives; missing font falls back and reports substitution; v3 documents decode with Poppins/tracking 0; v4 round-trips font/tracking/case.

- [ ] **Step 2: Confirm failure.**
  Run: `cd tools/engine-spike && ./gradlew :app:testDebugUnitTest --tests app.add.engine.TextStyleTest`
  Expected: missing properties.

- [ ] **Step 3: Add the fields and painter support.**
  Use `TextPaint.letterSpacing` and an explicit typeface; do not fake tracking by inserting spaces. Do not bake case into the stored text.

- [ ] **Step 4: Add the inspector controls.**
  Font picker, size, leading, tracking, alignment, case, weight. Use a compact segmented control for alignment (2–3 visible options; Pajamas says never more than 5) and a select/sheet for fonts. Keep all labels short and visible.

- [ ] **Step 5: Save, restore and run.**
  Run: `bash tools/engine-spike/verify-suite.sh`
  Expected: all green.

- [ ] **Step 6: Commit.**
  ```bash
  git add tools/engine-spike/app/src/main/java/app/add/engine/TypefaceRegistry.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineDocument.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/DocumentCodec.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/ArtboardPainter.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineSheets.kt \
    tools/engine-spike/app/src/test/java/app/add/engine/TextStyleTest.kt
  git commit -m "feat: add tracking, case and real font selection"
  ```

### Task 6: Add photo crop and a safer image path

**Files:**
- Modify: `EngineDocument.kt`, `DocumentCodec.kt`, `ArtboardPainter.kt`, `LayerCompositor.kt`, `EngineSheets.kt`
- Test: `ImageCropTest.kt`

**Interfaces:**
- Consumes: existing `ImageLayer.path` and `fill`.
- Produces:
  ```kotlin
  data class ImageCrop(
      val x: Float = 0f,
      val y: Float = 0f,
      val w: Float = 1f,
      val h: Float = 1f,
  )
  data class ImageLayer(
      ...,
      val crop: ImageCrop = ImageCrop(),
  )
  fun setImageCrop(layerId: String, crop: ImageCrop)
  ```
  Crop is normalised to the source image and non-destructive. Fill/fit operates on the cropped source.

- [ ] **Step 1: Write failing crop tests.**
  Assert: crop changes export pixels; original source is not rewritten; fit shows the whole cropped area; missing file leaves a visible placeholder and does not crash; a 4K image is capped for preview but the export path uses the safe source.

- [ ] **Step 2: Confirm failure.**
  Run: `cd tools/engine-spike && ./gradlew :app:testDebugUnitTest --tests app.add.engine.ImageCropTest`

- [ ] **Step 3: Implement crop in the painter and compositor.**
  Use a source rect and destination rect; do not copy the bitmap per frame. Cache the decoded image by path and mtime.

- [ ] **Step 4: Add crop handles in the inspector, not on the artboard.**
  Keep v2 scope tight: X/Y/W/H sliders and a reset. Read `Android-Image-Cropper` first (Task 0b); use its crop matrix if it fits, but store the crop rect, not a flattened bitmap. Store the rect in the decoded, EXIF-normalised source space the app already produces, and keep it axis-aligned: the reference's four crop points are reduced to their bounding box, which loses area on a rotated crop. A rotated crop needs a stored quad and is out of v2. On-canvas crop handles are a follow-up.

- [ ] **Step 5: Save/restore and run.**
  Run: `bash tools/engine-spike/verify-suite.sh`
  Expected: all green.

- [ ] **Step 6: Commit.**
  ```bash
  git add tools/engine-spike/app/src/main/java/app/add/engine/EngineDocument.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/DocumentCodec.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/ArtboardPainter.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/LayerCompositor.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineSheets.kt \
    tools/engine-spike/app/src/test/java/app/add/engine/ImageCropTest.kt
  git commit -m "feat: add non-destructive photo crop"
  ```

### Task 7: M3/Pajamas shell, without breaking the stack

**Files:**
- Modify: `app/build.gradle.kts` only in the spike branch
- Modify: `EngineTheme.kt`, `EngineChrome.kt`, `EngineSheets.kt`, `EngineLayerPanel.kt`
- Test: `EngineChromeTest.kt`, existing chrome goldens

**Interfaces:**
- Consumes: ADD tokens (graphite, cream, newsprint, stamp red), current `EngineTheme`, system motion scale.
- Produces: one `EngineTokens` object; no default Material colour scheme in the editor.

- [ ] **Step 1: Run the compatibility spike.**
  Reuse the Task 0c result; do not repeat the spike. If Task 0c was skipped, run it now before touching the shell.
  Expected: the recorded pass/fail decides whether Step 2 uses M3 components or custom M3-shaped ones.

- [ ] **Step 2: Decide.**
  If the spike passes, use M3 components. If it fails, implement `EngineFloatingToolbar`, `EngineSheet`, `EngineSplitButton` and `EngineSegmentedControl` with the same behaviour and ADD tokens. Do not upgrade Compose “just in case”.

- [ ] **Step 3: Implement the shell with M3 behaviour.**
  Floating/docked toolbar for tools; bottom sheet for the inspector; split export action; segmented alignment; tree layer panel. Use M3 spatial springs only for chrome and effects springs for opacity/colour. No dynamic colour, no default M3 shapes, no permanent bottom bar.

- [ ] **Step 4: Apply Pajamas rules.**
  Segmented controls: 2–5 options, equal width, one active, visible/accessible label, disabled options visible. Tree: chevron, indent, selection, expand/collapse, accessibility semantics. Toast for export result. 48dp targets.

- [ ] **Step 5: Add accessibility tests.**
  Assert: every icon button has a label; every sheet has a title and dismissal; alignment options expose selected state; focus order follows the stage → toolbar → sheet; 48dp minimum; reduced motion removes non-essential animation.

- [ ] **Step 6: Run chrome and full suite.**
  Run: `bash tools/engine-spike/verify-chrome.sh && bash tools/engine-spike/verify-suite.sh`
  Expected: goldens pass; if a visual change was intentional, update the golden only after a review capture.

- [ ] **Step 7: Commit.**
  ```bash
  git add tools/engine-spike/app/src/main/java/app/add/engine/EngineTokens.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineTheme.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineChrome.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineSheets.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineLayerPanel.kt \
    tools/engine-spike/app/src/test/java/app/add/engine/EngineChromeTest.kt
  git commit -m "feat: apply M3 behaviour and Pajamas rules to the editor chrome"
  ```

### Task 8: Add a procedural texture library, not a texture dump

**Files:**
- Create: `TexturePresets.kt`
- Modify: `LayerCompositor.kt`, `LayerStyle.kt`, `EngineSheets.kt`
- Test: `TexturePresetTest.kt`

**Interfaces:**
- Consumes: existing procedural grain/halftone/duotone/paper passes.
- Produces:
  ```kotlin
  data class TexturePreset(
      val id: String,
      val name: String,
      val seed: Int,
      val kind: TextureKind,
      val defaultOpacity: Float,
      val defaultBlend: BlendKind,
  )
  ```
  Presets are generated from a seed and the artboard size; no bitmap is stored in the APK.

- [ ] **Step 1: Write failing determinism tests.**
  Assert: same preset + seed + size produces identical pixels; different seeds differ; no network call; presets survive save/load by id; a missing preset id falls back to none with a diagnostic.

- [ ] **Step 2: Implement the preset taxonomy.**
  Start with the categories that matter to ADD: newsprint, wheatpaste, overexposed ink, halftone, rough canvas, film grain, dust, paper fibre, vignette, light leak. These are the existing Studio family; do not add 40 stock textures.

- [ ] **Step 3: Add an asset-licence gate.**
  If a bitmap texture is ever added, require:
  ```kotlin
  data class AssetLicence(
      val sourceUrl: String,
      val licence: String,
      val attribution: String?,
      val sha256: String,
  )
  ```
  Build fails when an asset lacks a licence entry. This is the useful part of Stash's badge model and the necessary answer to Speckyboy's unlicensed roundup.

- [ ] **Step 4: Run tests and goldens.**
  Run: `bash tools/engine-spike/verify-suite.sh && bash tools/engine-spike/verify-chrome.sh`
  Expected: all green.

- [ ] **Step 5: Commit.**
  ```bash
  git add tools/engine-spike/app/src/main/java/app/add/engine/TexturePresets.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/LayerCompositor.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/LayerStyle.kt \
    tools/engine-spike/app/src/main/java/app/add/engine/EngineSheets.kt \
    tools/engine-spike/app/src/test/java/app/add/engine/TexturePresetTest.kt
  git commit -m "feat: add deterministic procedural texture presets"
  ```

**Task 8 status (11 Nov 2026): done and verified in the working tree.** The ten
families are algorithmic-only and stored by recipe; determinism, size resampling,
document save/load and the unknown/invalid/corrupt fallback are covered by
`TexturePresetTest` and `TextureDocumentTest`. The real inspector exposes the
family/seed/opacity/scale/remove controls, and `TextureRendererTest` proves the
bounded cache reaches `ArtboardPainter`. `verify-suite.sh` is **154 tests, 0
failures, 0 errors, 0 skipped**; `verify-chrome.sh` reports **11 goldens match**
after a reviewed re-record for the fourth `TEXTURE` segment. No bitmap or network
asset was added. The review build was carried through as **3.8.4 (54)** and is live
at `https://artbase-handoff-apk.vercel.app/ADD-3.8.4-engine-review.apk`; the public
handoff is `https://artbase-handoff.vercel.app/`. Task 9–11 remain untouched, and
this work is uncommitted by request.

### Task 9: Device gate on the candidate build

**Files:**
- Modify: `tools/engine-spike/DEVICE-CHECKLIST.md`, `tools/engine-review/README.md`
- Capture: `artifacts/engine-render/` and `artifacts/emu/` only when a frame actually lands
- Test: full suite, chrome, a signed candidate build

**Interfaces:**
- Consumes: Tasks 1–8.
- Produces: a pass/fail device report with measured timings, and the defect list Task 10 must clear.

- [ ] **Step 1: Run the whole JVM verdict.**
  Run: `bash tools/engine-spike/verify-suite.sh && bash tools/engine-spike/verify-chrome.sh`
  Expected: all tests and goldens pass.

- [ ] **Step 2: Build the candidate.**
  Run: `bash tools/engine-review/build.sh` for the engine-only candidate, and the Task 11 script once it exists. A candidate is signed with the ADD key and installs over the shipped build.

- [ ] **Step 3: Run the device checklist, with numbers.**
  Install on the target phone. Verify: launch, pinch zoom, one-finger drag, group/ungroup, non-uniform group scale with rotated text/photo, 3D layer under a scaled group, text edit + motion path, undo/redo, autosave/reopen, photo crop, 3D orbit, export, share/save, back handling, system animation scale 0. Record: cold start to first frame, 50-layer pan median frame time, pinch latency, 1× and 2× export time.

- [ ] **Step 3a: Run the live device audit.**
  On a connected runtime, run `python3 tools/device_audit.py --fixture artifacts/field-proof/device/document.json --reopen`. It must reach `READY`, capture the live windows, verify the saved field, reopen it, and export 1080×1350; any missing check fails. The x86 harness is a pre-signing smoke proof, not the phone verdict.

- [ ] **Step 4: Capture only what actually rendered.**
  If the emulator produces a frame, attach the date and attempt count. The engine review can draw its live windows through `WindowManagerGlobal.mViews`; attach `device-ui-roots.txt` so a dialog is visibly a separate root. If it aborts, record that. Do not draw a Compose/Filament screen and call it a capture.

- [ ] **Step 5: File every defect.**
  A device defect blocks Task 11. It may only become a known limitation with a written reason in `FINAL-TEST.md`; an unrecorded failure is not allowed to disappear into the next build.

- [ ] **Step 6: Commit the checklist and report.**
  ```bash
  git add tools/engine-spike/DEVICE-CHECKLIST.md tools/engine-review/README.md
  git commit -m "docs: record the native editor v2 device gate"
  ```

### Task 10: Integrate the engine and retire Studio

**Files:**
- Modify: `tools/engine-review/app/build.gradle.kts`, `tools/engine-review/build.sh`, `app/AndroidManifest.xml`, the shipping `build.sh`
- Delete: Studio WebView assets, GSAP vendor copy, Studio CSS/JS and the Fabric bootstrap
- Test: `EngineRoutingTest`, `verify-suite.sh`, `verify-chrome.sh`

**Interfaces:**
- Consumes: engine sources, Studio routing, `StyleStore`.
- Produces: one editor inside `app.offline.compress`; the STUDIO tab resolves to the engine; no Studio asset is reachable.

This task exists because the earlier plan ended at a review build and the success criterion "only one editor ships" had no step that made it true.

- [ ] **Step 1: Measure the cost before writing UI code.**
  Record the shipped APK size, dex count and cold start. Build the merged source set and record the same numbers. If the APK grows past the agreed budget, decide what is cut (x86_64, debug symbols, unused GLB) before merging.

- [ ] **Step 2: Move the engine into the shipping source set.**
  Keep the `app.add.engine` package and activity name. Keep the separate `:engine` process unless an equivalent crash isolation replaces it; a Filament fault must not take the shelf down.

- [ ] **Step 3: Route and delete.**
  The STUDIO tab opens the engine activity. Remove the debug-only door, the Studio HTML/JS, the GSAP vendor copy and the Fabric bootstrap, then search every remaining reference including `manifest.json`, the service-worker precache list and `build.sh`. A stale precache entry is a broken offline launch.

- [ ] **Step 4: Migrate data.**
  Existing Studio documents and saved looks either load in the engine or appear in `FINAL-TEST.md` as intentionally dropped, with the reason. Never silently discard a saved look.

- [ ] **Step 5: Write the one-editor test.**
  `EngineRoutingTest` asserts the Studio route resolves to the engine activity, that no Studio HTML/JS asset exists in the merged source set, and that saved looks are reachable. This is the test that makes "one editor" enforceable instead of aspirational.

- [ ] **Step 6: Re-run the gate on the merged build.**
  Run suite, chrome and the Task 9 checklist; fix every defect the gate recorded.

- [ ] **Step 7: Commit.**
  ```bash
  git add tools/engine-review app tools/engine-final
  git commit -m "feat: ship the native editor as the only editor"
  ```

### Task 11: Build, sign and verify the final testing APK

**Files:**
- Create: `tools/engine-final/build.sh`, `FINAL-TEST.md`
- Modify: `tools/engine-review/app/build.gradle.kts` (versionCode, versionName, release signing)
- Test: suite, chrome, device checklist, `apksigner verify`

**Interfaces:**
- Consumes: everything above.
- Produces: `artifacts/final-test/ADD-4.0-final-test.apk`, its SHA-256 and size, and `FINAL-TEST.md`.

This replaces the old "review APK + go/no-go" ending. The deliverable is a signed, installable, upgrade-tested APK of the single editor.

- [ ] **Step 1: Freeze scope.**
  After this point any change needs the whole gate re-run. No "small" UI edits in the release candidate.

- [ ] **Step 2: Make it a release build.**
  `isDebuggable = false`, release signing with the ADD key, `versionCode` one above the shipped build, `versionName` 4.0, no review-only routes and no crash-report screen. The engine must still be reachable, because it is now the editor.

- [ ] **Step 3: Run the gate and build.**
  Run `verify-suite.sh`, `verify-chrome.sh`, then `bash tools/engine-final/build.sh`, then `apksigner verify --print-certs` and `zipalign -c -v 4`.

- [ ] **Step 4: Install over the shipped build.**
  Upgrade-install, not a clean install. Run the full checklist including saved-document reopen, share/save, back handling and animation scale 0.

- [ ] **Step 5: Re-measure on this exact artifact.**
  Repeat the timings from Task 9 on the release APK. A build that is fast in debug and slow in release is not tested.

- [ ] **Step 6: Write `FINAL-TEST.md`.**
  Record version, versionCode, SHA-256, size, device, Android version, every checklist result, measured timings, known limitations with reasons, and the exact command that produced the APK. No device claim without this file.

- [ ] **Step 7: Publish and commit.**
  ```bash
  mkdir -p artifacts/final-test
  sha256sum artifacts/final-test/ADD-4.0-final-test.apk
  git add tools/engine-final FINAL-TEST.md
  git commit -m "release: build the final testing APK"
  ```
  The product is finished only when every checklist line is passed or listed as a known limitation with a reason.

## Success criteria

- Group/ungroup never changes absolute layer positions, including rotated children under non-uniform group scale; skew survives ungroup and save/restore; nested groups preserve the tree.
- Dragging or editing text cannot leave a motion path at the old centre; playback starts at the layer's actual centre; edge clamping moves the path rigidly.
- Undo covers groups, motion and type; autosave survives a crash and a corrupt file; v3 documents still open.
- Type offers font, size, leading, tracking, alignment and case; missing fonts fall back visibly.
- Photos are real cropped layers; export matches the stage at 1×/2×.
- Pinch zoom remains 50–400%; 3D and export do not regress.
- Performance meets the budget: 50 layers, three nested groups and one 2048 px photo hold a 16 ms median frame time at 1080×1350 on the target phone, measured on the release APK.
- Chrome meets 48dp, labels, focus order and reduced-motion rules; no default M3 colour leaks in.
- All existing tests plus the new tests pass; the device checklist is honest about what was and was not run.
- Only one editor exists: the STUDIO tab resolves to the engine, Studio's HTML/JS/GSAP assets are deleted, and `EngineRoutingTest` fails if either comes back.
- The final testing APK is built, signed, upgrade-installed, verified with `apksigner` and `zipalign`, and recorded in `FINAL-TEST.md` with its SHA-256, size and measured timings.

## Estimated shape

This is a five-to-seven week native project for one focused developer, not a polish pass. The hard parts are group transforms, path anchoring across every mutation, and the M3/SceneView compatibility spike; the texture and website research is the smallest part. M1 is the real decision point: if group math and the three geometry goldens are not green, the rest of the plan is not worth starting. The final two tasks (integration and the signed APK) are deliberately not optional: without them the plan produces a review build, which is what the previous version of this document did.

## Source links

- [Material 3](https://m3.material.io/)
- [M3 Toolbars](https://m3.material.io/components/toolbars/overview)
- [M3 Bottom sheets](https://m3.material.io/components/bottom-sheets/overview)
- [M3 Motion physics](https://m3.material.io/styles/motion/overview)
- [Pajamas Design System](https://design.gitlab.com/)
- [Pajamas design-system philosophy](https://design.gitlab.com/about/design-system-philosophy)
- [Pajamas component collection](https://design.gitlab.com/components/overview)
- [Pajamas segmented control](https://design.gitlab.com/components/segmented-control)
- [Pajamas tree](https://design.gitlab.com/components/tree)
- [tttamannaanand/stash](https://github.com/tttamannaanand/stash)
- [Speckyboy texture roundup](https://speckyboy.com/free-high-resolution-texture-packs/)
- [CanHub/Android-Image-Cropper](https://github.com/CanHub/Android-Image-Cropper)
- [burhanrashid52/PhotoEditor](https://github.com/burhanrashid52/PhotoEditor)
- [SceneView](https://github.com/SceneView/sceneview-android)
- [Compose Rich Editor](https://github.com/MohamedRejeb/Compose-Rich-Editor)
- [Fabric.js](https://github.com/fabricjs/fabric.js)
- [Excalidraw](https://github.com/excalidraw/excalidraw)
- [Penpot](https://github.com/penpot/penpot)
- [Graphite](https://github.com/GraphiteEditor/Graphite)
- [Glaxnimate (KDE GitLab)](https://invent.kde.org/graphics/glaxnimate)
