# ADD engine — consolidated plan

8 Nov 2026. Supersedes the phase list sent in chat. Nothing from the earlier
recommendations is dropped: deferred items are marked deferred, not removed.

## The goal

The spike stops being a spike: **one native engine inside ADD** that composes a
poster — photo, type, vector, icon, sticker and 3D — and exports it as a PNG, with no
WebView in the path, no account and no network. `tools/engine-spike` is the vehicle.
The engine plugs into ADD's nav beside the editor, not into it. The recorder is how every failure stays
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

## Canvas-first

The engine is a small-canvas powerhouse: the artboard owns the screen, and the tools
belong to the canvas rather than sitting beside it. This is the design law for every item
below, and it outranks convenience.

1. **No permanent bands.** The top bar, the status line and the layer panel all collapse
   to an edge. Nothing owns a fixed strip of the screen.
2. **Chrome comes on intent.** Touching the canvas dismisses it; tapping an edge, a handle
   or a selection summons it. The default state of the app is the artboard.
3. **Tools float.** The rail becomes a translucent pill over the canvas, and the inspector
   is a peek that grows into a sheet, never a docked 236 dp block.
4. **Gestures carry the frequent actions**, buttons the rest: two-finger tap undo,
   three-finger redo, long-press empty canvas to add.
5. **Status speaks only when there is something to say** — recording, saved, 3D error — as
   an overlay chip, not a permanent line.
6. **Fit on launch.** The page fills the screen at the size the screen can show.
7. **Nothing added later may reintroduce a fixed band.** An item that needs one is a
   design failure, not a shortcut.

Today the chrome takes 346 dp before the canvas gets a pixel — 56 dp top bar, 32 dp status,
22 dp strip, 236 dp panel — plus a permanent 54 dp rail. That is roughly two-fifths of a
phone, and it is the thing this plan fixes first. The cost of fixing it is discoverability,
paid with the gesture legend (C22) and a first-run tip, not with more chrome.

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
- **Canvas-first, 8 Nov 2026.** The plan is recut around the artboard owning the screen —
  see **Canvas-first** and **Build waves**. Every note from the review is an item,
  C11–C32 and E10–E16. The native editor stays the simple arranging and exporting page,
  with only E10 and E1–E4 planned.


## What happens to the editor

The native editor **stays, and stays simple**. It is the easy arranging and exporting
page: a photograph, type on it, the seven presets, saved looks, the colour mixer, font
packs, export to `Pictures/Editor`. That job is done, and it is not a stepping stone — the
engine does not inherit it, and nothing in this plan retires it.

Two things change in it, and only two:

- **E10, a real undo stack.** A deleted line or a turned photo is otherwise permanent. It
  folds the existing UNDO LOOK chip into itself rather than shipping two undos.
- **E1–E4, the recorder and report.** Invisible, and it is what makes an editor bug
  answerable at all.

Everything else on the editor list — E5–E9 and E11–E16 — is **deferred, not deleted**:
pinned swatches, preset management, export defaults, canvas presets, the default face,
before/after, the eyedropper, crop, straighten, thirds and font samples. They wait until
the editor asks for them, which by this plan's own logic it may never do.

The only surface on a retirement path is the **Studio tab inside that editor** — the
Fabric.js WebView. The engine replaces that tab, and only once it can hold a photo and
export the same page, in one release where both are present.

The two are different tools, not two versions of one: the engine is the powerhouse for
composition — vector, icons, stickers, 3D, photo — and the editor is the quick page for a
photograph and a line of type. They share a nav, a palette and a diagnostics language;
they do not share a screen.

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

- **C11 Reorder layers** (S). `layers` is only appended, so the last thing added sits on top
  for good. `moveLayer(id, to)` plus a grab handle on every row: long-press lifts it, drag
  moves it, the drop writes the order and autosaves.
- **C12 Exact placement fields** (S). X, Y, W, H and rotation as fields in the codec's own
  fractional units, with a 1 px / 10 px nudge pad. Writes go through
  `beginGesture`/`endGesture`, so one edit is one undo step.
- **C13 Save state** (S). `SAVED 06:52` / `SAVING…` / `SAVE FAILED` in the status line, and
  the same state in the report, so autosave stops being invisible.
- **C14 Type parity** (S). `TextLayer` carries `align` and `lineHeight`; the inspector
  controls neither, and align is printed in a label. Add both. Letter spacing is a model,
  codec and painter change (M) and follows.
- **C15 Layer thumbnails** (M). A 24 dp thumbnail per row, drawn by `ArtboardPainter`,
  cached and invalidated on `actionId`.
- **C16 Undo history** (M). Long-press ↶ for the last ten named actions, tap to jump.
- **C17 Hex colour field** (S). Beside the five swatches, writing the same fill for every
  layer kind.
- **C18 Export scale, format, SHARE** (S). 1×/2×, PNG or JPG, and a SHARE chip after a
  successful export.
- **C19 One door between the surfaces** (S). `BASIC EDITOR` moves out of the Info dialog to
  the top bar; the editor gains an ENGINE entry.
- **C20 Safe-area guides** (S). A 10 % inset rectangle, toggled with the grid.
- **C21 Document library** (L). Named documents with thumbnails: New, Open, Save as, Delete,
  and the last one reopened. Needs C2's visible half, which ships in the same wave.
- **C22 Gesture legend** (S). One sheet naming every gesture the stage accepts.
- **C23 Last-export thumbnail** (S). The last PNG, 40 dp, tappable to share — the other half
  of C18.
- **C24 Haptics** (S). A tick when a grip is taken and when a snap catches.
- **C25 Resizable bottom panel** (S, absorbed by C29). Drag the strip to any height, using the editor's own
  `SheetDrag` pattern.
- **C26 Icon recents** (S). The last twenty glyphs used, ahead of the catalog.
- **C27 ADD sheet** (M). Adding a sticker or icon no longer replaces the selected layer's
  inspector; ADD and the tool mode become separate things.
- **C28 Auto-hiding chrome** (M). Touching the canvas collapses the top bar, status line and
  panel to edges; tapping an edge or a handle brings them back. The default state of the app
  becomes the artboard.
- **C29 Peek panel** (M). The 236 dp block becomes a 22 dp strip — selected layer, three
  actions — that drags up into the full inspector. Absorbs C25.
- **C30 Floating tool pill** (M). The 54 dp rail becomes a translucent five-glyph pill over
  the canvas, auto-hiding.
- **C31 Status chip** (S). The 32 dp status line becomes an overlay chip that appears only
  for recording, saved or a 3D error.
- **C32 Fit on launch** (S). The artboard opens filling the screen; the dead 1080 / 1350
  labels at the rail foot go with the rail.


### E — Editor (main ADD app, `app/src/add/`)

The native editor has no instrumentation at all today.

Only **E10** and **E1–E4** are planned: an undo stack and a recorder. **E5–E9 and E11–E16
are deferred, not removed** — the editor stays the easy arranging and exporting page it is
today, and those wait until it asks for them.

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

- **E10 Undo stack** (M). Snapshot the document on every commit, keep fifteen, and fold the
  UNDO LOOK chip into it rather than shipping two undo systems.
- **E11 Before/after hold** (S). Hold the stage to see the ungraded photo; release to return.
- **E12 Eyedropper** (M). Sample a colour out of the photo into the selected line or canvas.
- **E13 Crop and aspect at export** (M). Square and 4:5, with the crop visible on the stage.
- **E14 Fine straighten** (S). Rotate by degrees, not 90° steps.
- **E15 Thirds overlay** (S). A composition grid over the photo.
- **E16 Font pack samples** (S). Each pack sets a sample line in its own face at 28 sp.


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

These record what each phase promised and how it was checked. **Build waves**, below, is
how the work actually ships: P0 and P1 are done or half done, and P2–P7 are folded into
the six batches below.

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

## Build waves

Canvas-first, in six engine batches and one editor track. A batch builds everything that
does not depend on a verdict, then ships one APK. Inside a batch items may depend on each
other — F3 before the settings sheet, C1 before C6 — but nothing waits for the batch's own
verdict to start.

**A — The shell (the feel test).** C28, C29, C30, C31, C32, C11, C12, C13, C22, C24.

Ten items, one question: does the app feel like the artboard? Auto-hiding chrome, a peek
that grows into the inspector, a floating tool pill, a status chip, fit on launch, layer
reordering, exact placement fields, a visible save, the gesture legend and haptics on the
grips. C25's resizable panel is absorbed here — once the panel is a peek, growing it is the
default posture rather than a nicety. This is the first engine APK.

Exit: no fixed band on screen, a layer moves by finger, a position can be typed, and the
save line says when.

**B — Parity inside the shell.** C7, C14, C15, C16, C17, C18, C19, C20, C23, C26.

Ten small items: lock and rename, type alignment and leading, thumbnails sized for the
peek, undo history, hex colour, export scale and share, the door to the editor, safe-area
guides, the last-export thumbnail, icon recents. Every one must land inside the shell's
rules — a thumbnail row is fine, a fixed panel is not.

Exit: every obvious control exists, and none of them bought back a band.

**C — Look.** C3, C4, C5, C27, F3.

Palette, page sizes, feel, the ADD sheet, and the `EngineScreen` split that makes room for
them. The ADD sheet is now the contextual add: tools summoned from the canvas, never a mode
that owns the inspector.

Exit: palette, page size and feel survive a restart, and adding something no longer
replaces what is selected.

**D — Photo.** C1.

The capability the goal is missing, and the reason the editor can stay simple instead of
being replaced. Import, fit, fill, crop, persistence, correct draw order, export.

Exit: your own photograph sits on the page and leaves in the PNG.

**E — Placement.** C6, D5, D6, D7.

Snapping and guides, plus the overlays that show what a finger is hitting and what the last
gesture did. Overlays are a toggle chip over the canvas, not a panel.

Exit: two layers snap edge to edge, and a dead tap explains itself.

**F — Library.** C2's visible half, C21.

New, Open, Save as, named documents, thumbnails.

Exit: a poster has a name and reopens.

**Editor track.** E10 and E1–E4, one APK — the only planned changes to the editor.

**Integration.** Behind the existing nav, the engine replaces the Studio WebView tab; both
stay for one release; then the tab is removed. The native editor keeps its own entry and is
not part of this.

**Why this order.** A is the feel, and the feel is the whole point — ten items that decide
whether the ambition works at all, small enough to judge in one sitting. Everything after
it lands on a shell that has already been approved, which is why B is allowed to be ten
small things instead of ten builds. D gives the engine a photograph, which is what lets the
editor stay simple. F is last because a library is only worth having once there is a
document worth keeping.

**If a batch is rejected**, each item is its own commit once the verdict is in, so the
piece that failed is dropped without unpicking the rest.

## Cadence

Every batch: unit tests, `assembleDebug`, APK, handoff page, commit, push, phone verdict.
A batch is built with every item it can hold, then ships one engine APK or one editor APK.
Each item is its own commit after the verdict, so a rejected piece is dropped without
unpicking the rest of the batch. Invisible work — the recorder, the spill, the schema —
commits on green. The engine is judged on the phone, always, because there is no render of
it; the editor is judged from Robolectric renders first, then on the phone for touch and
behaviour.

## Not building

Cloud sync, accounts or collaboration (no backend exists and the app is offline by
design). An undo tree. A rewrite of the Studio WebView. More emulator work. Animation
timelines and blend modes that export cannot honour. Arbitrary SVG import, which is a
licensing question first. Figma export.
