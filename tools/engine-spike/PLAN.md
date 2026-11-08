# ADD engine — consolidated plan

8 Nov 2026. Supersedes the phase list sent in chat. Nothing from the earlier
recommendations is dropped: deferred items are marked deferred, not removed.

## The goal

The spike stops being a spike: **one native engine inside ADD** that composes a
poster — photo, type, vector, icon, sticker and 3D — and exports it as a PNG, with no
WebView in the path, no account and no network. `tools/engine-spike` is the vehicle.
The editor track is where it plugs in. The recorder is how every failure stays
diagnosable from a phone.

Two consequences the earlier list missed:

- **The engine has no photo layer.** `Layer` is Vector, Text, Icon and Model only, so
  it cannot yet do the job the Fabric Studio does. A photo primitive is on the critical
  path to the goal, not a nice-to-have.
- **The engine and the editor are two build paths.** P0-P4 and P7 ship the engine-review
  APK (`tools/engine-review/build.sh`). P5-P6 ship the official ADD build (`./build.sh`).
  Integration is where they meet.

Done means all five hold:

1. A poster with a photo, three type lines, a vector shape, an icon, a sticker and one
   3D model exports a PNG that matches the stage at 1080x1350.
2. The document survives process death and reopens exactly, including that photo.
3. Any failure in that session is diagnosable from one pasted report.
4. The reader can change palette, artboard, feel and snapping without a build.
5. No WebView, no account, no network.

## Progress

- **P0 — done.** D1, D2, D3, D4, D9, D10. The recorder spills to disk and carries the
  previous session's tail into the next report, an export reports its lit percentage
  and any black capture, a dead 3D layer says whether the asset is missing or the
  loader refused it, every control sets an action label, the header carries the undo
  depth, and the dialog has a LAST row.
- **P1 — half done.** F1, the invisible half of C2, and D8's encoder: the document is
  versioned JSON on disk, saved after each edit and restored at launch. New / Open /
  Save as visible controls are still to do, because a top-bar row is a visual change
  and waits for a verdict.
- Everything else: not started.

## What happens to the editor

The native editor is **kept and improved**, not retired. It stays the photo-plus-type
surface: decode at 2048 px with EXIF rotation, type lines, the seven presets and the
reader's saved looks, the colour mixer, the TYPE / PHOTO / LAYERS sheets, font packs,
and export to `Pictures/Editor`. P5 and P6 add instrumentation and controls to it;
nothing removes it.

The only thing on a retirement path is the **Studio tab inside that editor** — the
Fabric.js WebView, which is a spike, keeps its own state, is not wired to saved looks
and is unverified on a device. The engine replaces that tab only after P3 and P4, only
once it can hold a photo and export the same page, and only with one release where both
are present.

The engine is a third surface, not a rewrite of the editor: a *composition* page for
vector, icons, stickers and 3D, where the editor is a *photograph* with type on it. They
share a nav, a palette and a diagnostics language; they do not share a screen.

## Workstreams

### D — Debugging, engine

- **D1 Spill the recorder to disk** (S). A Filament or Skia native fault bypasses
  `Thread.setDefaultUncaughtExceptionHandler` and takes the session with it. Write each
  line to `filesDir/diagnostics-log.txt` from `Diagnostics.append`, close the stream in
  `stop`; the tail survives a native death and is shown next launch.
- **D2 Lit samples per 3D capture** (S). `Exporter.litSamples()` already counts lit
  pixels and throws the number away during export. Carry it: `3d 1/1 · 62% lit`, and
  call a 0% capture *black*, not *missing*. `captures` must keep the attempt message and
  the ratio per model, not just the Bitmap.
- **D3 Name why a 3D layer died** (S). Three causes, three lines: asset file absent
  from the APK (`ModelAsset.path`), loader or material error (`handle.error`), surface
  down (`SurfaceHolder.Callback`). "Offline" covers all three today.
- **D4 Action labels on every mutation** (M). Give `EngineDocument.commit` a reason and
  keep the last one, so the sampler prints `edit DUP · gem`, `edit FRONT · star`,
  `edit fill #0FBF92 · star`, `edit weight 500 · kicker`. One field, ~30 call sites,
  covers every button whose result the sampler can see but whose cause it cannot name.
- **D5 Hit-target overlay** (M). Toggle: paint each layer's hit rect, each grip circle
  at its real capped radius, and the vector node radius. Answers "I tapped it and
  nothing happened" without reading a log.
- **D6 Gesture trace overlay** (M). Draw the last gesture's path for two seconds with
  the handle it took. Shares the D5 coordinate work.
- **D7 Frame sparkline** (S). The last 120 frame deltas as a strip in the diagnostics
  dialog; `frames 59/s · worst 41 ms` is a number, a stall at the pinch is a shape.
- **D8 Scene JSON** (S). Copy the document as JSON from the dialog. Reproducible bug
  reports, and the fixture for P1's round-trip tests.
- **D9 Undo depth in the header** (S). `undo 3`, so an undo that does nothing is
  distinguishable from one that was never armed.
- **D10 One "why" line** (S). Last action, last gesture, last dial in one sentence at
  the top of the dialog. A photograph of that line is the bug report.

### C — Customization, engine

- **C1 Photo layer** (L). `ImageLayer` with a source copied into `filesDir/media/`, fit,
  fill and crop, drawn in `ArtboardPainter` above the background and below the vectors.
  Import through the system photo picker, decode capped like the editor at 2048 px.
  This is what turns the engine from a vector toy into the Studio's replacement.
- **C2 Persistence: New / Open / Save** (L). Nothing survives a process death today —
  no preferences, no file, seed layers only. Autosave on gesture end, restore on
  launch, NEW resets to seed, a corrupt file seeds and says so.
- **C3 Palettes** (M). `EngineTheme` is one hard-coded dark set. An `EnginePalette` data
  class behind a `LocalPalette` composition local, green/Moss/Navy x dark/light, the
  same six the shipped app uses.
- **C4 Artboard presets, canvas colour, grid** (M). `ArtboardPainter.W/H` are constants:
  make size, background and an optional grid/rulers document state, with 1080x1350,
  1:1, 9:16 and A4 presets. Scale layers proportionally on change or refuse and say why.
- **C5 A FEEL sheet** (M). Grip radius multiplier, pinch deadband, rotation snap step,
  long-press duration — persisted. The 0.22 icon and 0.25 grip cap become defaults, not
  laws: this is the class of thing that differs by hand and cannot be settled here.
- **C6 Snapping and guides** (L). Edges and centres to the artboard and to other
  layers, thirds, 15-degree rotation snapping, visible guides, a SNAP toggle. Largest
  perceived-quality jump in the plan, and it makes placement debuggable because a snap
  is drawn.
- **C7 Layer lock and rename** (M). `visible` exists, `locked` does not. A locked layer
  cannot be hit or gripped — this week's entire bug class, prevented.
- **C8 Multi-select, align, distribute** (L). `selectedId` is one string, so this is
  architectural. Worth it only when the reader asks for layout work; align and
  distribute are trivial after it and impossible before.
- **C9 Toolbar order and hiding** (S, deferred). Six tools do not need it yet.
- **C10 Custom sticker and icon import** (L, deferred). Licensing before code.

### E — Editor (main ADD app, `app/src/app/offline/compress/`)

The native editor has no instrumentation at all today.

- **E1 An editor recorder** (M). Same shape as `Diagnostics`, smaller: sheet opened,
  preset applied, look saved, look undone, line added or deleted, colour set, photo
  turned, export result.
- **E2 An editor report** (S). Selected line, line count, canvas rotation and flip,
  photo pixels and EXIF orientation, each sheet's remembered height, current face and
  pack. COPY, then paste.
- **E3 Decode and export diagnostics** (S). Decode time and size, export byte count,
  MediaStore outcome. A large photo is the likeliest field failure.
- **E4 Sheet drag trace** (S). Height transitions and cancelled drags, so "the sheet is
  stuck" is a line rather than a guess.
- **E5 Pinned swatches** (M). Keep the reader's own eight beside the 36 fixed ones.
- **E6 Preset management** (M). Rename, reorder, delete, and export or import a saved
  look as JSON.
- **E7 Export defaults** (S). Remember size, format and destination per module.
- **E8 Studio canvas presets** (M). 1080x1350 is fixed today; add post, story, square.
- **E9 Default type face** (S). Choose the face a new type line starts in.

### F — Foundations

- **F1 Versioned document schema** (M). `JSONObject`, `schema: 1`, artboard, layers of
  all five kinds, selection, palette, zoom. Prerequisite for C1, C2, C4, C6.
- **F2 Tests per phase** (S each). The 27 existing tests are geometry and zoom; there is
  no test for a commit, a serialization round-trip, a snap target or a grip cap at
  rotation. Every phase adds its own.
- **F3 Split `EngineScreen` before it grows** (M). 1,459 lines and the choke point for
  every phase: extract the inspector and the new settings sheet into their own files as
  part of P2, not as a refactor for its own sake.

## Phases

### P0 — Close the diagnostics loop (S + M)
Items: D1, D2, D3, D4, D9, D10.
Exit: 27+ tests, APK, handoff rebuilt; a report shows the action, the dial, the gesture,
frames, crashes and an export's lit percentage.
Phone: do three different things (a dead tap, a dial drag, an export) and paste the
report.

### P1 — The document survives (L)
Items: F1, C2, D8.
Exit: kill the app mid-session and reopen it; every layer kind round-trips; a corrupt
file seeds rather than crashes.
Phone: build something, force-close from recents, reopen.

### P2 — The reader's look (M)
Items: C3, C4, C5, F3.
Exit: palette, artboard and feel survive restart. Visual, so it ships an APK and waits
for a verdict before the commit.
Phone: switch palette, set 9:16, shrink the grip.

### P3 — The photo primitive (L)
Items: C1.
Exit: import from the gallery, fit, fill and crop, export composites it in the right
order, persistence keeps the copy.
Phone: build a poster with your own photo and export it.

### P4 — Placement (L)
Items: C6, D5, D6, D7.
Exit: snapping on and off, guides visible at edges, centres and thirds; overlay toggles;
unit tests for every snap target.
Phone: place two layers edge to edge.

### P5 — The editor speaks (M) — parallelizable after P1
Items: E1, E2, E3, E4. Ships the official ADD build.

### P6 — The editor's controls (M)
Items: E5, E6, E7, E8, E9.

### P7 — Layout at scale (L)
Items: C7, C8. C9 and C10 stay deferred until a reason arrives.

### Integration — the Studio tab, not the editor
After P3 and P4 the engine has what the Studio has: a photo, type, vector, icon and
sticker on one page, exported. Wire it into ADD as the replacement for the **Studio
WebView tab** behind the existing nav, keep that tab for one release, then remove it.
No phase in this plan replaces the native editor. P5 and P6 add to it; C1's photo layer
is what would let a future plan consider folding it in, and that decision is not made
here.

## Cadence

Every phase: unit tests, `assembleDebug`, APK, handoff page, commit, push, phone verdict.
Visual phases (P2, P3, P4, P6) wait for the verdict before the commit; the rest commit on
green. Parallel tracks: debugging (P0, P5) can run while a visual verdict is pending;
persistence (P1) touches nothing visible and is the safest work to do while waiting.

## Not building

Cloud sync, accounts or collaboration (no backend exists and the app is offline by
design). An undo tree. A rewrite of the Studio WebView. More emulator work. Animation
timelines and blend modes that export cannot honour. Arbitrary SVG import, which is a
licensing question first. Figma export.
