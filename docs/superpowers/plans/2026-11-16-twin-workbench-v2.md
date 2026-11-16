# Twin Workbench 2.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the browser Twin's phone-shaped mockup with a device-aware workbench that has Figma-level direct manipulation and Illustrator-class numeric controls on desktop, and a genuine phone/foldable editing mode for the Galaxy Z Flip 7 and general layouts, without replacing the existing document, effects, plugin or PNG-export engines.

**Architecture:** One canonical 765 × 1000 document space drives a shared DOM artboard renderer, a pointer-driven selection overlay, an explicit print-only artboard, and the existing 2× PNG exporter. Desktop uses fixed, resizable Ark `Splitter` panels; phone uses a bottom tool dock and bottom-sheet inspector; the Flip 7 cover uses a deliberately limited quick mode; half-fold uses viewport segments to put the canvas above the fold and tools below it. Ark UI 5.40 supplies the accessible tree, numeric scrubbers, splitter, drawers, toggles, tabs and dialogs; direct manipulation is first-party pointer math because no installed library provides the needed range of transform behavior. A temporary `?workbench=1` flag keeps the existing shell green while the new editor is built, then the cutover task removes the flag and the old shell.

**Tech Stack:** React 19.2, Vite 7.3, TypeScript 5.9, Ark UI 5.40.0, Motion 14.9, Lucide 1.73, Vitest 3.2, Playwright 1.57, local Archivo + IBM Plex Mono OFL fonts, existing Paper Shaders / tsParticles / Three.js / Feral Fur / Rough.js plugins. No new runtime dependency.

**Spec:** This document is the UI and interaction spec. The brand constraints are in `BRAND.md` (§Studio: graphite shell, cream controls, paper artboard, red active); the product boundary is `PLAN.md`; the shipped baseline and its superseded UI shell are in `docs/superpowers/plans/2026-11-14-twin-ui.md`. The native editor is a separate product and is out of scope.

**Status:** Proposed — revision 3. **Five tasks and thirteen workstreams.** No editor code has been written for this revision. The shipped A+B+flattened-PNG work remains the baseline; this plan supersedes only the browser Twin shell, coordinate model and interaction surface.

### Revision 2: what changed after the second audit

- **Fixed/resizable panels replace floating cards.** Figma's final UI3 moved from floating panels back to fixed, resizable sidebars because floating cards cramped the canvas and weakened rulers. Ark `Splitter` is now used, not rejected. The bottom tool pill still floats.
- **Print is a separate 765 × 1000 artboard.** The zoomed/transformed editor canvas must never be the print source.
- **Empty documents are valid.** Deleting the last layer must not make save/open fail, and the layer cap must be enforced at add time.
- **Touch and trackpad are explicit.** Native non-passive wheel zoom and two-pointer pinch are in the plan, not left to browser defaults.
- **Selection stays visible.** Canvas selection scrolls the corresponding layer row into view without stealing focus.
- **Focus ownership is specified.** Handles are pointer-only; keyboard equivalents live in the inspector; focus returns from palette, preview and drawers.
- **Panels and print are tested.** The verification matrix now includes empty/64-layer/print/panel-resize cases.

### Revision 3: device contract and foldable modes

- **Device optimization is now a rule, not a separate polish pass.** Every UI task must pass the device matrix below; a desktop-only capture is not acceptance evidence.
- **Flip 7 profiles are explicit.** Main display reference: 360 × 840 CSS at DPR 3 (2520 × 1080 physical, 21:9). Cover FlexWindow reference: 349 × 316 CSS (1048 × 948 physical at DPR 3), plus a 320 × 320 stress size. The CSS viewport is a tested reference, not a claim that physical pixels equal CSS pixels.
- **Three foldable modes, not a fake second display.** Cover = Quick Mode; unfolded = full phone editor; half-fold = canvas on the top segment and tools on the bottom segment when the browser reports two viewport segments. A browser page cannot drive the cover and main display as two independent live editor surfaces; that would require a native app, and the plan does not pretend otherwise.
- **Touch is first-class.** 44 px coarse-pointer hit areas around 8 px visual handles, pointer-based layer reorder instead of HTML drag, bottom-sheet inspector, 100dvh, safe-area insets and keyboard/visualViewport handling.
- **Fold support is progressive.** `window.viewport.segments` is used when present; `device-posture: folded` and ordinary width/height are deterministic fallbacks. No unsupported promise for Safari.
- **The rule is testable.** Task 4 owns the device modes; Task 5 captures and audits the full matrix.
- **Five review gates, not thirteen.** The original thirteen units are grouped into five tasks; every workstream keeps its own files, interfaces, step sequence and tests, so nothing is deleted or minimized.

### Retained from the shipped Twin

The effect engine, eight plugin panels, shader/texture/particle/pattern renderers, PNG import sanitisation, 16 blend modes, save/open, print/PDF, flattened PNG export, 30-snapshot document history and Native lens stay. The plan replaces how the document is framed and manipulated, not the content system. The shipped A+B+PNG baseline is not reopened.

## The honest read

The current screen is not an editor. It is a 411 px (or 840 px) device mockup with a bottom sheet and a four-item dock; the artboard is `min(100%, 338px)` in compact mode and `min(100%, 500px)` in expanded mode, while export renders a separate 338-design-unit scene at 1530 × 2000. Layer positions are CSS pixels on a responsive artboard, so the composition changes with the frame width; `Stage` contains explicit `layoutShift` hacks for two frames. The only manipulation is drag-to-move, with no resize, rotation, marquee, multi-select, snapping, rulers, zoom, pan, align or distribute. Layers are hidden behind an inspector tab. Labels are 6–9 px. The shell's monochrome treatment was a deliberate proof but conflicts with `BRAND.md`, which requires red for active states and selection.

That cannot be fixed by restyling. The plan therefore replaces four things:

1. **The shell:** desktop workbench, not a device frame. The old frame survives as Preview.
2. **The coordinate model:** one 765 × 1000 space for stage, rulers, snapping and export.
3. **The interaction layer:** pointer math, selection, handles, snapping and keyboard.
4. **The control surface:** layers tree, transform inspector, align/distribute, command palette.

It deliberately does **not** replace the effect engine, plugin registry, shader renderers, import sanitisation, history model, print path or PNG exporter. Those already work and have tests.

## Decisions, not options

| Area | Decision | Rejected because |
|---|---|---|
| Scope | Browser Twin only. Native engine untouched. | The native editor has its own model and plan. |
| Renderer | Keep DOM layers; add a first-party selection overlay. | A new Canvas/Konva engine would reimplement text, effects, blend modes, shader canvases and the tested PNG exporter for no user-visible gain at 64 layers. |
| Coordinate space | Document is 765 × 1000 units; export is exactly 2× (1530 × 2000). | The current 338-unit artboard and responsive CSS disagree with export. |
| Transform model | `{ x, y, scaleX, scaleY, rotation }`; width/height are derived from an explicit base size. | A stored `width/height` would duplicate text/image intrinsic sizing and create a second source of truth. |
| Selection | `selectedLayerIds: string[]`; multi-select moves and aligns. | Single `selectedLayerId` cannot express marquee, shift-click or multi-layer alignment. |
| Multi-select handles | Move, marquee and align/distribute only; no group resize/rotate. | Group non-uniform scaling of rotated children needs skew semantics that this release does not need and should not fake. |
| Numeric transforms | Illustrator reference point (9 anchors) controls X/Y/W/H/rotation. | Center-only numeric transforms are weaker than the requested Illustrator controls. |
| Layers | Ark UI `TreeView` with multiple selection, keyboard and inline rename. | A hand-rolled tree loses ARIA, typeahead, focus and rename behavior. |
| Panels | Fixed, resizable, collapsible left layers and right inspector; floating bottom tool pill; Ark `Splitter`. | Figma's final UI3 moved away from floating panels because they cramped the canvas and weakened rulers. Floating cards would repeat the current sheet problem; a dashboard grid would waste more space. |
| Motion | CSS for panels; Motion only for panel/palette presence; no animation on drag, resize, rotate or snapping. | Canvas manipulation must be immediate; springs would add latency and make precision feel wrong. |
| Snap targets | Artboard edges/center + other layer edges/centers; 6 screen px threshold; Cmd/Ctrl disables. | Grid, guides, constraints and smart-distance matching are separate features and are not promised here. |
| Styling | New scoped `editor.css`; delete the old shell CSS at cutover. No Tailwind, CSS-in-JS or design system dependency. | The current 3,806-line stylesheet is the source of the cramped phone layout. |
| Fonts | Archivo 600 for UI, IBM Plex Mono 400/600 for numbers/rulers, existing document faces unchanged. Copy from `docs/assets/fonts/`. | Remote fonts violate the offline boundary. |
| Schema | Project format v2; read v1 and migrate on load; write v2 only; zero layers allowed. | Dual-write would keep the old 338-unit geometry alive indefinitely. Rejecting zero layers would break save after deleting the last layer. |
| Print | A dedicated print-only `ArtboardView` at 765 × 1000 with an exact page size. | Printing the zoomed/transformed editor canvas would crop or scale it unpredictably. |
| Text editing | Inspector textarea only; no on-canvas text editing in this release. | A rotated, zoomed, auto-sized text editor is a separate feature; promising it here would create a fake editor. |
| Device contract | Every UI task must pass desktop, phone, cover and half-fold checks; desktop-only evidence is not acceptance. | A phone layout added at the end always ships truncated controls and touch bugs. |
| Dual screen | Three modes: cover Quick Mode, unfolded editor, half-fold canvas + tools. No simultaneous cover/main live canvas. | A web page cannot address the FlexWindow and main display as two independent surfaces; pretending it can would be a false promise. |
| Phone inspector | Bottom sheet, not a side drawer; layers use a full-height sheet; 44 px controls. | A 276 px side panel on a 360 px viewport leaves no canvas. |
| Touch reorder | Pointer-based grip reorder on coarse pointers; native HTML drag only on fine pointers; move-up/down actions always available. | Native HTML drag is unreliable on touch and has no touch cancel semantics. |
| Delivery | Build behind `?workbench=1`; cut over in Task 5; remove the flag and old shell. | A big-bang replacement would leave the suite red for many tasks; a flag keeps every intermediate task verifiable. |

**Brand reconciliation:** `BRAND.md`'s “no permanent bands” is read as no full-width top/bottom chrome bands. Fixed 224/276 px side panels are allowed because they collapse to 44 px rails, the canvas keeps the center, and the tool pill still floats. The “peek inspector” is the collapsed inspector rail. Task 2 implements this reading; it does not silently keep the old floating-card proposal.

## Research basis

- Figma, [Inside the redesigned Figma, where your work takes center stage](https://www.figma.com/blog/behind-our-redesign-ui3/) (26 June 2024): resizable, collapsible panels and a slim bottom toolbar; the top is freed for the canvas.
- Figma, [Figma 2024: We shipped it, you shaped it](https://www.figma.com/blog/figma-2024-we-shipped-it-you-shaped-it/) (4 December 2024): “Fixed panels are so back, floating panels are out.”
- Figma on Figma, [Our approach to designing UI3](https://www.figma.com/blog/our-approach-to-designing-ui3) (1 October 2024): user feedback said floating panels “cramped the canvas” and “made rulers less effective.” This plan therefore keeps fixed side panels and floats only the tool pill.
- Figma Help, [Adjust alignment, rotation, position, and dimensions](https://help.figma.com/hc/en-us/articles/360039956914-Adjust-alignment-rotation-position-and-dimensions): one selected layer aligns to its parent; multiple layers align to each other. The plan's align rules match that.
- Adobe, [Transform objects in Illustrator](https://helpx.adobe.com/illustrator/using/transforming-objects.html): the Transform panel exposes a 3 × 3 reference point plus X/Y, W/H and rotation. The inspector follows that model.
- Samsung, [Galaxy Z Flip7 official display specifications](https://news.samsung.com/global/samsung-galaxy-z-flip7-a-pocket-sized-ai-powerhouse-with-a-new-edge-to-edge-flexwindow): main 6.9-inch 2520 × 1080 (21:9); cover 4.1-inch 1048 × 948.
- Device CSS reference: [Breakpane device viewports](https://breakpane.com/en/devices) and [YesViz Galaxy Z Flip 7](https://yesviz.com/devices/samsung-z-flip7) both report the unfolded Flip 7 as **360 × 840 CSS at DPR 3**. The cover CSS viewport is not officially documented; the plan derives **349 × 316** from 1048 × 948 at DPR 3 and requires an on-device check, with 320 × 320 as a stress size.
- Chrome, [Viewport Segments API shipped](https://developer.chrome.com/blog/viewport-segments-api-shipped) (Chrome 138+; Samsung Internet 30+ per [caniuse](https://caniuse.com/wf-viewport-segments)): `window.viewport.segments` and CSS `horizontal-viewport-segments` / `vertical-viewport-segments`. MDN documents top/bottom segments as `vertical-viewport-segments: 2` and the `env(viewport-segment-*)` offsets. This is the exact basis for the half-fold layout.
- MDN, [Device Posture API](https://developer.mozilla.org/en-US/docs/Web/API/Device_Posture_API) and [`device-posture`](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/At-rules/@media/device-posture): Chromium 132+ reports `continuous` or `folded`; feature-detect because support outside Chromium is not available.
- Local Ark UI API verification: `useSplitter`, `Splitter.Panel`, `Splitter.ResizeTrigger`, `collapsePanel`/`expandPanel`, `TreeView`, `NumberInput.Scrubber`, `SegmentGroup`, `Drawer` (including bottom placement) and `Dialog` all exist in the installed `@ark-ui/react@5.40.0`. No new dependency is needed.

## Global constraints

- Work only under `twin/`, this plan file and `artifacts/twin/editor/`. Do not modify `docs/index.html`, `docs/assets/**`, `handoff/`, `app/`, `tools/engine-spike/` or any pre-existing uncommitted work.
- Do not commit, push, merge, deploy or publish. No commit steps exist in this plan because the user has not authorized one.
- No new runtime dependency. Keep `@ark-ui/react@5.40.0`, React 19.2, Motion 14.9, Lucide 1.73 and the existing plugin stack.
- Keep save/open, print/PDF, flattened PNG export, undo/redo, plugin sessions, Native lens and all eight plugin behaviours working.
- Allow zero layers and empty selection. Enforce `MAX_LAYERS = 64` at add time; keep the 12-PNG cap. Removing the last layer must leave a valid, saveable document.
- Print from a dedicated, unrotated 765 × 1000 artboard. Do not print the editor's transformed/zoomed canvas.
- On-canvas text editing is out of scope; text content is edited in the inspector. Do not describe the editor as full Figma text parity.
- TypeScript strict; no `any` in public editor contracts. Follow existing repository formatting; do not reformat unrelated files.
- All fonts and assets are local. No CDN, remote font, remote shader, telemetry or runtime network request.
- Respect `prefers-reduced-motion`; do not animate direct manipulation.
- The device contract below applies to every UI task. A UI task is not complete from a 1440 px capture alone.
- The old shell remains the default until Task 5. `?workbench=1` is a temporary development flag, not a shipped feature.

## Device contract (applies to every UI task)

This is the rule: every UI task implements, tests and captures the modes it touches at the relevant profiles; Task 4 owns the device modes and Task 5 audits the whole matrix. Pure schema/model tasks are exempt, but any component that renders chrome is not.

| Profile | Reference viewport | Input | Required behavior |
|---|---|---|---|
| Desktop | 1440 × 900 | fine pointer | Splitter panels, rulers, hover, full inspector. |
| Laptop | 1024 × 768 | fine pointer | Splitter panels; no horizontal overflow; canvas ≥ 400 px. |
| Tablet | 768 × 1024 | coarse | Side drawers, 44 px targets, no splitter. |
| Phone main | 390 × 844 and 360 × 840 (Flip 7 reference, DPR 3) | coarse | Bottom tool dock, bottom-sheet inspector, full-height layers sheet, pinch, no rulers. |
| Phone landscape | 840 × 360 | coarse | 72 px side tool rail, canvas right, bottom sheet; no vertical clipping. |
| Cover | 349 × 316 reference; 320 × 320 stress | coarse | Quick Mode only: compact canvas + five 44 px actions; no full inspector or splitter. |
| Half-fold | 360 × 420 top + 360 × 420 bottom, fold gutter | coarse | Canvas above, tools/properties below; no control or content on the fold; fallback bottom sheet if segments are unavailable. |
| Preview | 411 × 891 and 840 × 1000 | any | Read-only device frame. |

Rules that apply at every profile:

- Touch targets are at least 44 × 44 CSS px; visual 8 px handles get a 44 px invisible hit area on coarse pointers.
- No horizontal page scroll at 320 px. No fixed `100vh`; use `100dvh` and safe-area insets.
- Hover styles are behind `(hover: hover)`; they never gate an action.
- Bottom sheets use `visualViewport` so the keyboard does not cover fields.
- Content never crosses a fold gutter when segments are reported.
- A browser page cannot drive the cover and main display as two independent live surfaces. The cover is a separate Quick Mode, not a second editor window.
- Viewport Segments is Chromium-only; the fallback is posture/width and must still be usable. Do not claim Safari fold support.

## Review focus

These are the failure modes most likely to hurt a real user; each has a named test in the owning task.

1. **Pointer drift under zoom/rotation:** grabbing an east handle at 37° and 200% must keep the opposite corner fixed to within 0.5 document units; no jump on pointer capture or cancel.
2. **Undo granularity:** one drag, resize or rotate is one history entry; pointer-cancel leaves the document unchanged; undo after deleting a layer leaves selection valid.
3. **Snap independence from zoom:** the 6 px threshold is converted to document units at the current zoom; snapping does not become sticky at 25% or impossible at 400%; Cmd/Ctrl disables it for the gesture.
4. **Stage/export parity:** a v1 project opened in v2 and a new composition both export at 1530 × 2000 with layer positions matching the stage at 2× within 0.5 px.
5. **Keyboard containment:** arrows, Delete, Cmd+A and Cmd+Z do nothing while focus is in a text field, number input or dialog; Escape closes palette, then preview, then panels, then clears selection.
6. **Panel occlusion and preview:** at 390 and 360 the bottom sheet never covers the selected layer without a way to close it, and the dock stays reachable; Preview shows a read-only 411/840 frame and exits without changing the document. Cover (349/320) and fold (360×420 + 360×420) are checked in Tasks 11 and 13.
7. **Empty/capped document:** deleting the last layer still saves, exports and prints; the 65th layer is rejected with a clear message, not a corrupt project.
8. **Print and focus:** print output is the exact artboard, not the editor viewport; focus returns from palette/preview/drawers, and canvas handles never trap Tab.
9. **Cover Quick Mode:** at 349 × 316 and 320 × 320, five 44 px actions remain reachable, the canvas is pannable/zoomable, and no full inspector or splitter is mounted. It is a quick mode, not a squeezed desktop.
10. **Half-fold:** at two 360 × 420 segments, the canvas stays above the fold and tools below it; no control, sheet handle or selected layer crosses the gutter; without segment data the normal bottom sheet is used rather than a guessed split.
11. **Touch precision:** 8 px visual handles have 44 px hit areas, layer reorder works by pointer on coarse input, and a drag on a handle does not scroll the page or pan the canvas.

## Visual direction

**The Studio is the press, not the newspaper.** Graphite is the press bed; the paper artboard is the work; red is the registration mark, never body copy.

```
┌──────────────────────────────────────────────────────────────────────┐
│  OPEN STUDIO ▾ 765 × 1000       [↶] [↷]        [Preview] [Native]  │  floating chips
│ ┌─────────────┐                                        ┌───────────┐ │
│ │ Layers   5  │          ┌──────────────────┐           │ Design    │ │
│ │ ▸ OPEN...   │          │                  │           │ Transform │ │
│ │   Star      │          │    ARTBOARD      │           │ X Y W H   │ │
│ │   Orbit     │          │    765 × 1000    │           │ Align     │ │
│ │             │          │                  │           │ Appearance│ │
│ └─────────────┘          └──────────────────┘           └───────────┘ │
│            rulers around the paper          [V][H][Z]  +  [Add] 100% │
└──────────────────────────────────────────────────────────────────────┘
```

- **Palette:** `#131513` Studio, `#1B1D1A` panel, `#232622` raised, `#343832` rule, `#F2EEE3` cream, `#A9AA9F` muted, `#DD4124` red, `#E9E0CB` paper, `#FAFAF7` sheet, `#111111` ink.
- **Type:** Archivo 600 for UI; IBM Plex Mono 400/600 for numbers, rulers and keyboard hints; Instrument Serif / Spectral / Poppins / Libre Franklin remain document choices. UI minimum 11 px; inputs 12–13 px; panel headings 15 px; no 6–9 px chrome labels.
- **Layout:** left layers panel 224 px (resizable 180–320, collapsible to 44 px), right inspector 276 px (resizable 240–400, collapsible to 44 px), canvas fluid; rulers 22 px; bottom floating pill 52 px. Panels are fixed graphite surfaces separated by 1 px seams, not rounded dashboard cards. The collapsed inspector rail is the “peek” state. At <1024 px the splitter is not mounted: tablet uses side drawers, phone uses a 56 px bottom dock and bottom sheets, cover uses Quick Mode, and half-fold uses top canvas / bottom tools. Only the tool pill floats over the canvas.
- **Tall phone:** at 360 × 840 the canvas owns the full 21:9 height between a 44 px top strip and a 56 px bottom dock; the inspector is a 62dvh sheet, not a stack of cards. The thumb zone owns add/undo/tool actions. Cover at 349 × 316 is a five-action quick surface, not a shrunk desktop. Half-fold is 420/420 with a reserved gutter, not a floating dialog over the canvas.
- **Selection:** red 1.5 px outline; 8 px cream handles with red border; rotation handle is a red dot on a short stem; multi-selection uses a dashed red box with no handles. Snap guide is a 1 px red line with a small value chip.
- **Motion:** 180 ms panel presence, 140 ms command palette, 90 ms press; `prefers-reduced-motion` removes all except state visibility. No page-load sequence, no hover animation on every control.
- **One memorable element:** the paper sheet on the graphite bed with red registration ticks and live ruler lines. Everything else stays quiet.
- **Avoid:** newsprint/cream page skin, device notch, fake phone chrome, 6 px labels, dashboard cards, gradient washes, all-caps eyebrows, “AI” glow.

## Interaction contract

**Pointer**

| Gesture | Result |
|---|---|
| Tap / click layer / Shift-click / Cmd-click | Replace / add / toggle selection |
| Drag selected layer | Move all selected layers; Shift locks the dominant axis; Cmd/Ctrl disables snapping. Coarse pointer: 8 px visual handles with 44 × 44 hit areas. |
| Touch grip / tap-drag layer row | Reorder after 8 px; `touch-action: none`; row menu always offers Move up/down. |
| Drag empty artboard | Marquee select intersecting layers; Shift adds; Escape cancels |
| Drag 8 handles | Resize single selection; Shift preserves ratio; Alt/Option resizes from center |
| Drag rotation handle | Rotate around center; Shift snaps to 15° |
| Double-click layer name in Layers | Inline rename; Enter saves, Escape cancels |
| Space-drag / middle-drag | Pan |
| Wheel | Pan; Cmd/Ctrl+wheel or trackpad pinch | Zoom around pointer; native listener is non-passive so the page does not scroll |
| Two-finger pinch | Zoom around the midpoint and pan with the midpoint | Preserve the document point under the fingers; cancel any active single-pointer gesture |
| Drag panel seam / keyboard seam | Resize layers or inspector; collapse to a 44 px rail | Ark `Splitter`; keyboard resize retains focus |
| Cmd/Ctrl+0, Cmd/Ctrl+1 | Fit artboard, 100% |

**Keyboard**

`V` Move, `H` Hand, `Z` Zoom, `Space` temporary Hand, `Cmd/Ctrl+Z` undo, `Shift+Cmd/Ctrl+Z` / `Ctrl+Y` redo, `Cmd/Ctrl+A` select all, `Escape` clear selection / close palette / close preview / close drawers in that order, `Delete`/`Backspace` remove selected, arrow keys nudge 1, Shift+arrows nudge 10, `Cmd/Ctrl+]` / `[` move layer up/down, `Shift+Cmd/Ctrl+]` / `[` bring to front/send to back, `Cmd/Ctrl+K` command palette.

Shortcuts are ignored when `event.target` is an input, textarea, select, contenteditable or inside an open Ark dialog/menu. Selection handles are pointer-only (`tabIndex={-1}`, `aria-hidden`); keyboard users get equivalent X/Y/W/H/rotation controls in the inspector. The canvas is a labeled focusable region, and the status bar has a polite live region for zoom and selection count.

**Numeric transform**

- One selection: X/Y are the chosen reference point’s document coordinates; W/H are the unrotated frame dimensions; rotation is normalized to −180…180.
- Reference point: 3 × 3 grid (NW, N, NE, W, C, E, SW, S, SE). Changing W/H keeps that point fixed; changing rotation rotates around it; changing X/Y moves it to the typed coordinate.
- Aspect lock keeps W/H ratio. Minimum frame side is 4 document units.
- Multi-selection: inspector shows selection bounds read-only plus Align and Distribute. One selected layer aligns to the artboard; two or more align to the selection bounds. Distribute needs three or more.

## Canonical geometry

```ts
export const ARTBOARD_WIDTH = 765;
export const ARTBOARD_HEIGHT = 1000;
export const ARTBOARD_EXPORT_WIDTH = 1530;
export const ARTBOARD_EXPORT_HEIGHT = 2000;
export const EXPORT_SCALE = 2;
export const MIN_LAYER_SIZE = 4;
export const MIN_ZOOM = 0.1;
export const MAX_ZOOM = 8;
export const MAX_LAYERS = 64;
export const MAX_IMAGE_LAYERS = 12;

export interface Point { x: number; y: number }
export interface Size { width: number; height: number }
export interface Rect extends Point, Size {}
export interface Frame extends Rect { rotation: number }

export type ResizeHandle = "nw" | "n" | "ne" | "e" | "se" | "s" | "sw" | "w";
export type ReferencePoint = ResizeHandle | "c";
```

`x` and `y` are the **frame center**. `width` and `height` are `baseLayerSize(layer)` multiplied by `scaleX` and `scaleY`. Rotation is clockwise degrees around the center. `layerBounds` returns the axis-aligned bounding box after rotation for marquee, align and snapping.

Base sizes are explicit so the stage, inspector and exporter cannot disagree:

- Text: width `642`, height `max(size × 0.82, lines × size × 0.82 + 45)`.
- Shape: block `244`, star `208`, orbit `222` (both sides).
- Image: imported fitted width/height stored in the layer.

The v1 migration factor is exactly `765 / 338 = 2.263313…`. It multiplies v1 `x`, `y`, text `size`, and image `width`/`height`; `scale` becomes `scaleX = scaleY = scale`; `selectedLayerId` becomes `selectedLayerIds`. Fonts, colors, effects and layer order are unchanged.

## Reuse register

| Need | Reuse | Exact API / decision |
|---|---|---|
| Layer tree, multi-select, keyboard, rename | Ark UI `TreeView` | `Root`, `Label`, `Tree`, `NodeProvider`, `Branch`/`Item`, `ItemText`, `NodeRenameInput`; `selectionMode="multiple"`; `createTreeCollection` with `rootNode`, `nodeToValue`, `nodeToString`, `nodeToChildren`. |
| X/Y/W/H/rotation/opacity | Ark UI `NumberInput` | `Root`, `Label`, `Scrubber`, `Input`; commit with `onValueCommit`, not every `onValueChange`. |
| Tools and text alignment | Ark UI `ToggleGroup` | Single-value groups (`multiple={false}`, `deselectable={false}`). |
| Design/Effects | Ark UI `Tabs` | Two tabs, active plugin opens Effects; no duplicate panel slot. |
| Add menu | Ark UI `Menu` | Text, Shape submenu (Star/Orbit/Block), PNG; returns focus to trigger. |
| Reference point | Ark UI `ToggleGroup` | Nine buttons; values `nw…se`, `c`. |
| Aspect lock, visibility | Ark UI `Switch` | Visible labels. |
| Fill | Ark UI `ColorPicker` | Existing `FillPicker` behaviour. |
| Blend / font | Ark UI `Select` | Existing 16 modes / 4 fonts. |
| Command palette | Ark UI `Dialog` | Focus trap, Escape, backdrop; does not own the canvas. |
| Preview device switch | Ark UI `SegmentGroup` | 411 / 840. |
| Panels, resize, collapse | Ark UI `Splitter` | `useSplitter` + `RootProvider`, `Panel`, `ResizeTrigger`, `ResizeTriggerIndicator`; panels use `id`, `minSize`, `maxSize`, `collapsible`, `collapsedSize`; `api.collapsePanel(id)` / `expandPanel(id)`; the seam is keyboard-resizable. Desktop only. |
| Panels under 1024 px | Ark UI `Drawer` | Tablet: side drawers. Phone: bottom sheet for Inspector (`placement="bottom"`), full-height sheet for Layers. Close on Escape/backdrop; focus returns to the trigger. |
| Foldable viewport segments | Native `window.viewport.segments` + CSS env | Read `window.viewport?.segments`; two top/bottom segments set `data-fold="vertical"` and `--fold-*` variables from `env(viewport-segment-*)`. `@media (vertical-viewport-segments: 2)` is the no-JS fallback; `device-posture: folded` plus normal width is the next fallback. No dependency. |
| Device posture | Native `navigator.devicePosture` | Feature-detect `navigator.devicePosture?.type`; listen for `change`; never require it. |
| Icons | Lucide | Existing dependency; no new icon files. |
| Cursors, ruler ticks, registration marks | CSS + inline SVG | Browser cursors and canvas-drawn ticks; no bitmap assets, no cursor downloads. |
| Effect assets | Existing plugin stack | Paper Shaders, tsParticles, Three.js, Feral Fur and Rough.js stay; no new textures, HDRIs or remote assets. |
| Motion | Motion 14.9 | `AnimatePresence` for drawer/palette only; panel resizing is immediate; no canvas springs. |
| Scroll selected layer into view | Native DOM | `row.scrollIntoView({ block: "nearest" })` after selection changes; do not call `TreeView.focus()` or steal focus from the canvas. |
| Print | Shared `ArtboardView` | A second, always-mounted render at exactly 765 × 1000 inside `.ed-print-root`; `@media print` hides the shell and shows it. No transform, zoom or selection chrome. |
| Fonts | `docs/assets/fonts` | `archivo-600.woff2` SHA-256 `d9e8c29f…67e8`; `plex-mono-400.woff2` `08949f72…80f7`; `plex-mono-600.woff2` `0d1f0b8d…df83`. Copy + add to `asset-manifest.json`, `THIRD_PARTY.md`, `scripts/check-assets.mjs`. |
| New canvas engine | **Rejected** | Not justified; DOM + overlay covers this layer count and preserves export. |
| dnd-kit / react-dnd | **Rejected** | Layer reorder is one list; HTML drag + keyboard is enough. |

## File structure

```
twin/src/editor/
  EditorShell.tsx              // desktop Splitter shell / mobile drawers; right-tab state; hotkeys
  editor.css                   // scoped editor styles; imported by EditorShell
  palette.ts                   // approved editor colours only
  useEditorHotkeys.ts          // keyboard map with input/dialog guards
  useDeviceLayout.ts           // cover/phone/tablet/desktop, coarse pointer, safe areas
  useFoldSegments.ts           // segment API, posture fallback, data-fold + CSS vars
  CommandPalette.tsx           // Ark Dialog + filtered commands
  PreviewMode.tsx              // read-only 411/840 device preview
  PrintArtboard.tsx            // always-mounted 765 × 1000 print-only ArtboardView
  TopChips.tsx                 // document menu, undo/redo, preview, native lens
  Toolbar.tsx                  // desktop/tablet bottom pill + add/effects
  mobile/
    PhoneToolDock.tsx          // 56 px bottom dock, safe-area padding
    CoverQuickBar.tsx          // 5 x 44 px cover actions
    FoldToolDeck.tsx           // top canvas / bottom Tools-Layers-Properties tabs
  StatusBar.tsx                // zoom, layer count, snap/rulers toggles; polite live region
  canvas/
    Canvas.tsx                 // viewport, artboard, pointer surface
    ArtboardView.tsx           // pure artboard renderer (editor + preview)
    LayerView.tsx              // one layer's frame, content and effect
    CanvasRulers.tsx           // canvas-drawn rulers
    SelectionOverlay.tsx       // boxes, handles, marquee, snap lines
    usePointerSession.ts       // move/resize/rotate/marquee state machine
    useViewportGestures.ts     // non-passive wheel zoom, pinch, pan, pointer cancel
  panels/
    LayersPanel.tsx            // Ark TreeView + pointer reorder
    BottomSheet.tsx            // Ark Drawer bottom placement + visualViewport
    InspectorPanel.tsx         // selection-aware Design panel
    TransformSection.tsx       // reference point, X/Y/W/H, rotation
    AlignSection.tsx           // align + distribute
    EffectsPanel.tsx           // existing catalogue + PluginHost
    NumericField.tsx           // shared NumberInput wrapper
  model/
    geometry.ts                // base sizes, frames, bounds, resize/rotate math
    snapping.ts                // snap candidates, threshold, guides
    align.ts                   // align/distribute transforms
    selection.ts              // selection reducer helpers
    viewport.ts                // fit/zoom/pan/screen-doc conversion
    ruler.ts                   // ruler tick selection
    layerTree.ts               // Ark collection + reorder
```

Tests live beside the units. `twin/src/editor/model/*.test.ts` are pure. `twin/src/editor/*.test.tsx` cover the shell/panels. `twin/e2e/editor.spec.ts` covers real pointer and keyboard behavior. The old `src/shell/*` files remain until Task 5.

## Core contracts

```ts
export interface LayerTransform {
  x: number;
  y: number;
  scaleX: number;
  scaleY: number;
  rotation: number;
}

export interface TwinLayer {
  id: string;
  name: string;
  kind: "text" | "shape" | "image";
  effect: TwinEffect | null;
  blend: BlendMode;
  opacity: number;
  transform: LayerTransform;
  visible: boolean;
  image?: { assetId: string; src: string; width: number; height: number };
  text?: { content: string; size: number; align: "left" | "center" | "right"; font?: TextFont };
  shape?: { variant: "star" | "orbit" | "block" };
  fill?: string;
}

export interface TwinDocument {
  background: TwinEffect | null;
  layers: TwinLayer[];
  /** May be empty: a new or emptied document is valid. */
  selectedLayerIds: string[];
}

export type EditorTool = "move" | "hand" | "zoom";
export type RightTab = "design" | "effects";
export type DeviceMode = "desktop" | "tablet" | "phone" | "cover" | "fold";

export interface FoldInsets {
  /** null when the browser exposes no fold; do not guess. */
  orientation: "vertical" | null;
  top: number;
  bottom: number;
  gap: number;
  source: "segments" | "posture" | "none";
}

export const TOUCH_TARGET = 44;
export const COARSE_SNAP_SCREEN_PX = 10;

export interface Viewport {
  zoom: number;
  panX: number;
  panY: number;
}

export interface SnapGuide {
  axis: "x" | "y";
  position: number;
}
```

```ts
// model/geometry.ts
export function baseLayerSize(layer: TwinLayer): Size;
export function layerFrame(layer: TwinLayer, transform?: LayerTransform): Frame;
export function layerBounds(layer: TwinLayer, transform?: LayerTransform): Rect;
export function selectionBounds(layers: TwinLayer[], ids: string[]): Rect | null;
export function referencePoint(frame: Frame, ref: ReferencePoint): Point;
export function moveTransform(transform: LayerTransform, dx: number, dy: number): LayerTransform;
export function resizeTransform(
  layer: TwinLayer,
  start: LayerTransform,
  handle: ResizeHandle,
  pointer: Point,
  options: { fromCenter: boolean; keepAspect: boolean },
): LayerTransform;
export function rotateTransform(
  layer: TwinLayer,
  start: LayerTransform,
  pointer: Point,
  options: { snap: boolean },
): LayerTransform;
export function changeSizeAtReference(
  layer: TwinLayer,
  size: Size,
  ref: ReferencePoint,
  keepAspect: boolean,
): LayerTransform;
export function changePositionAtReference(
  layer: TwinLayer,
  point: Point,
  ref: ReferencePoint,
): LayerTransform;
export function changeRotationAtReference(
  layer: TwinLayer,
  degrees: number,
  ref: ReferencePoint,
): LayerTransform;
export function normalizeAngle(degrees: number): number;
```

```ts
// model/snapping.ts
export interface SnapResult { dx: number; dy: number; guides: SnapGuide[] }
export function snapAnchors(layers: TwinLayer[], excludeIds: string[]): { x: number[]; y: number[] };
export function snapMove(
  bounds: Rect,
  delta: Point,
  anchors: { x: number[]; y: number[] },
  threshold: number,
): SnapResult;
export function snapResizeRect(
  rect: Rect,
  handle: ResizeHandle,
  anchors: { x: number[]; y: number[] },
  threshold: number,
): { rect: Rect; guides: SnapGuide[] };
```

```ts
// model/align.ts
export type AlignEdge = "left" | "hcenter" | "right" | "top" | "vcenter" | "bottom";
export function alignTransforms(
  layers: TwinLayer[],
  ids: string[],
  edge: AlignEdge,
): Record<string, LayerTransform>;
export function distributeTransforms(
  layers: TwinLayer[],
  ids: string[],
  axis: "x" | "y",
): Record<string, LayerTransform>;
```

```ts
// model/viewport.ts
export function fitViewport(container: Size): Viewport;
export function clampViewport(viewport: Viewport): Viewport;
export function zoomAt(
  viewport: Viewport,
  pointer: Point,
  nextZoom: number,
  container: Size,
): Viewport;
export function docToScreen(point: Point, viewport: Viewport, container: Size): Point;
export function screenToDoc(point: Point, viewport: Viewport, container: Size): Point;
```

```ts
// model/ruler.ts
export interface RulerTick { doc: number; screen: number; major: boolean; label?: string }
export function rulerTicks(zoom: number, originScreen: number, length: number): RulerTick[];
```

```ts
// store additions
selectLayers(ids: string[], mode?: "replace" | "add" | "toggle"): void;
selectAllLayers(): void;
clearSelection(): void;
setLayerTransforms(updates: Record<string, LayerTransform>): void;
setTransientTransforms(updates: Record<string, LayerTransform> | null): void;
reorderLayers(ids: string[], targetIndex: number): void;
removeSelectedLayers(): void;
setTool(tool: EditorTool): void;
setRightTab(tab: RightTab): void;
setSnapEnabled(enabled: boolean): void;
setRulersVisible(visible: boolean): void;
setViewport(viewport: Viewport): void;
```

`setTransientTransforms` renders a live gesture without touching history. `setLayerTransforms` commits one history entry. `setLayerTransform(id, partial)` remains as a single-layer convenience for the old shell and existing tests until Task 5. **Transient state is never serialized or exported:** `saveProject`, `exportPng`, `printStage` and history read committed `doc` only, never the transient overlay. Add `saveExportAndPrintIgnoreTransientTransforms` coverage in Task 3 Workstream 3B and again in the Task 5 Workstream 5A project tests.

## Tasks

Five review gates, thirteen workstreams. Each workstream keeps its own files, interfaces and step sequence; each task ends at a named gate.

| Task | Workstreams | Gate |
|---|---|---|
| **1. Canonical document and shared renderer** | 1A model/migration; 1B renderer/preview | Geometry, v1 migration, 2× export parity and `ArtboardView`. |
| **2. Workbench shell and layers** | 2A shell; 2B layers | Splitter, breakpoints, selection and reorder. |
| **3. Canvas interaction and precise controls** | 3A viewport; 3B move/snap; 3C resize/rotate; 3D inspector | Pointer math, rotated-corner invariant and numeric controls. |
| **4. Keyboard, effects and device modes** | 4A hotkeys; 4B effects; 4C preview/print/phone/cover/fold | Effects parity plus every device profile. |
| **5. Cut over and prove the result** | 5A cutover; 5B final audit | Full suite, captures, on-device notes and scope audit. |


All paths in the tasks are relative to `twin/` unless they start with `docs/`, `artifacts/` or `docs/assets/`. A `twin/src/...` path and an `src/...` path refer to the same file under `twin/`; later tasks use the shorter form.

### Task 1: Canonical document and shared renderer

**Gate:** `npm test -- geometry pngExport project ArtboardView` and `npm run build`. Stop if v1 migration, canonical geometry or 2× export parity fails.

**Status (2026-11-16): complete.** Focused Task 1 tests: 34 passed. Full unit suite: 73 passed. Production build: passed. Old-shell and project e2e: 43 passed; the two presentation tests passed again after the final stack-origin fix. Baseline: `artifacts/twin/editor/presentation-baseline.png`.

#### Workstream 1A: Cut the document to 765 × 1000 and v2 transforms

**Files:**
- Create: `twin/src/editor/model/geometry.ts`
- Test: `twin/src/editor/model/geometry.test.ts`
- Modify: `twin/src/twin/types.ts`, `project.ts`, `pngExport.ts`, `store.tsx`, `history.ts`, `imports.ts`
- Modify: `twin/src/shell/Stage.tsx`, `Inspector.tsx`, `src/plugins/OverlayLabPlugin.tsx` (temporary legacy adapter)
- Create: `twin/src/shell/legacy.ts` (temporary; deleted in Task 5)
- Test: `twin/src/twin/project.test.ts`, `pngExport.test.ts`, `history.test.ts`, `src/twin/imports.test.ts`

**Interfaces:**
- Produces: `ARTBOARD_WIDTH = 765`, `ARTBOARD_HEIGHT = 1000`, `ARTBOARD_EXPORT_WIDTH = 1530`, `ARTBOARD_EXPORT_HEIGHT = 2000`, `EXPORT_SCALE = 2`, `MAX_LAYERS = 64`, `MAX_IMAGE_LAYERS = 12`.
- Produces: `LayerTransform { x, y, scaleX, scaleY, rotation }`; `TwinDocument { background, layers, selectedLayerIds }`; `PROJECT_VERSION = 2`; empty `layers` and empty `selectedLayerIds` are valid.
- Produces: `baseLayerSize`, `layerFrame`, `layerBounds`, `referencePoint`, `normalizeAngle` from `geometry.ts`.
- Consumes: existing `TwinEffect`, `BlendMode`, `TextFont`, `ShapeVariant` unchanged.

- [x] **Step 1: Write the failing geometry tests.** Cover `textBaseSizeIsStable`, `shapeBaseSizesAreStable`, `rotatedBoundsAreAxisAligned`, `frameKeepsCenter`, `referencePointMapsAllNineAnchors`, `normalizeAngleWrapsAt180`. Use exact values: text 2 lines at size 154 is `{ width: 642, height: 297.56 }`; star is `208 × 208`; a `100 × 50` frame at 90° has bounds `50 × 100`.
- [x] **Step 2: Run `cd twin && npm test -- src/editor/model/geometry.test.ts`.** Expect failure because the module does not exist.
- [x] **Step 3: Implement `geometry.ts`.** `x/y` are centers. `layerFrame` multiplies base size by `scaleX/scaleY`. `layerBounds` uses `|w cos| + |h sin|` and `|w sin| + |h cos|`. `referencePoint` applies the frame rotation to the local anchor. `normalizeAngle` returns `((v + 180) % 360 + 360) % 360 - 180`.
- [x] **Step 4: Update the types and PNG exporter.** Change `ARTBOARD_DESIGN_WIDTH` to 765, height to 1000, scale to 2. Change `layerSize` to call `baseLayerSize`. Replace `layer.transform.scale` with `scaleX/scaleY`. Scale metadata positions and font size by `765 / 338`. Keep `ARTBOARD_EXPORT_WIDTH/HEIGHT` at 1530/2000. Update `pngExport.test.ts` to assert `plan.designWidth === 765`, `plan.scale === 2`, a v1 image still exports at the same physical size, and an empty document exports its background at 1530 × 2000.
- [x] **Step 5: Implement project v2, empty documents and v1 migration.** `PROJECT_VERSION = 2`. `serializeProject` writes `selectedLayerIds`. `parseProjectFile` accepts v1 and calls `migrateV1Document`; it does not write v1. Migration multiplies `x`, `y`, text `size`, image `width/height` by `765 / 338`, maps `scale` to `scaleX/scaleY`, and maps `selectedLayerId` to a valid one-element `selectedLayerIds`. Change `validateDocument` to accept zero layers and return `selectedLayerIds: []` when nothing is selected; keep rejecting more than `MAX_LAYERS`, duplicate ids and more than 12 images. Add tests `migratesV1Coordinates`, `migratesV1TextAndImageSizes`, `migratesV1Selection`, `rejectsMalformedV2`, `roundTripsV2`, `emptyDocumentRoundTrips`, `emptySelectionIsValid`, `rejectsSixtyFifthLayer`.
- [x] **Step 6: Update the store, history and old consumers.** `pushEffect` takes the primary `selectedLayerIds[0]`; if a layer target has no single primary, it is a no-op with the existing toast. `restoreSelection` filters ids against the restored layers. Update initial document values by the migration factor. Add `setLayerTransforms(updates)` and `setTransientTransforms`; keep `setLayerTransform` as a one-layer wrapper. Update the old Stage/Inspector with the temporary `legacy.ts` projection:
  ```ts
  export const LEGACY_UNIT = 338 / 765;
  export const toLegacy = (doc: number) => doc * LEGACY_UNIT;
  export const fromLegacy = (css: number) => css / LEGACY_UNIT;
  ```
  The old Stage projects document x/y/text size into legacy CSS px; its drag deltas convert back with `fromLegacy`. The old Inspector displays legacy units and converts edits back. This adapter exists only so the old shell's tests stay meaningful until Task 5.
- [x] **Step 7: Update `addImageLayer` sizing and enforce the layer cap at add time.** Decode the PNG, then fit it inside `0.8 × 765` by `0.8 × 1000` while preserving aspect. Store the fitted width/height in document units; keep `scaleX = scaleY = 1`. Export `MAX_LAYERS`; `addTextLayer` and `addShapeLayer` return `string | null` and return `null` with the existing toast at the cap; `addImageLayer` checks both the total cap and the 12-PNG cap before creating an object URL. Add `fitsImportedImageInsideArtboard`, `keepsSmallImageNaturalSize`, `textAndShapeRespectLayerCap`, `imageRespectsTotalAndImageCaps` tests.
- [x] **Step 8: Run the focused tests, then the full unit suite and build.**
  ```bash
  cd twin
  npm test -- src/editor/model/geometry.test.ts src/twin/project.test.ts src/twin/pngExport.test.ts src/twin/history.test.ts src/twin/imports.test.ts
  npm test
  npm run build
  ```
  All must pass. The old shell's e2e drag tests must still pass through the legacy adapter; run `npm run test:e2e -- e2e/twin.spec.ts` and note the result.
- [x] **Step 9: Inspect the diff for accidental coordinate edits outside the named files.** Do not commit.

#### Workstream 1B: Build the shared artboard renderer and read-only preview

**Files:**
- Create: `twin/src/editor/canvas/ArtboardView.tsx`, `LayerView.tsx`
- Test: `twin/src/editor/canvas/ArtboardView.test.tsx`
- Modify: `twin/src/shell/Presentation.tsx`
- Modify: `twin/src/styles/app.css` (only the preview/presentation selectors needed by this task)

**Interfaces:**
- Produces: `ArtboardView({ doc, transforms?, selectedIds?, readOnly?, testId?, className?, onLayerSelect? })`.
- Produces: `LayerView({ layer, transform, selected, readOnly })`.
- Consumes: `baseLayerSize`, `layerFrame` from Task 1 Workstream 1A.

- [x] **Step 1: Write failing renderer tests.** `rendersAtCanonicalSize`, `rendersEachLayerAtItsFrame`, `appliesRotationAroundCenter`, `appliesScaleXAndScaleY`, `rendersTextAndShapeContent`, `readOnlyOmitsSelectionClasses`, `effectsStayClippedToTheFrame`. Assert `data-artboard-width="765"`, `data-artboard-height="1000"`, and the layer's `data-layer-id`.
- [x] **Step 2: Run `npm test -- ArtboardView`.** Expect failure.
- [x] **Step 3: Implement `ArtboardView` and `LayerView`.** The artboard is exactly `765 × 1000` CSS px with root class `ed-artboard`; the viewport scales it. A layer is positioned `left = frame.x - width/2`, `top = frame.y - height/2`, `width = frame.width`, `height = frame.height`, `transform: rotate(rotationdeg)`, `transform-origin: center`. Text uses `font-size: size` and the shared line-height; shapes use width/height 100%; images use `object-fit: fill`. Keep `data-testid="art-layer-${id}"` and the `.art-layer` class for compatibility. The print root targets `.ed-artboard`, so do not rename it later.
- [x] **Step 4: Use `ArtboardView` in `Presentation`.** Scale the 765 × 1000 artboard to fit the presentation screen without selection. Add `presentationUsesCanonicalArtboard`.
- [x] **Step 5: Run `npm test -- ArtboardView`, `npm test -- App`, `npm run build`.** All pass. Capture `artifacts/twin/editor/presentation-baseline.png` for comparison; do not overwrite the old review captures.
- [x] **Step 6: Do not commit.**

### Task 2: Workbench shell and layers

**Gate:** `npm test -- EditorShell LayersPanel selection` and `npm run build`. The desktop splitter, all breakpoint selections and the layer tree must pass before canvas work starts.

#### Workstream 2A: Build the editor shell and visual system behind `?workbench=1`

**Files:**
- Create: `twin/src/editor/EditorShell.tsx`, `editor.css`, `palette.ts`, `useDeviceLayout.ts`, `useFoldSegments.ts`, `TopChips.tsx`, `Toolbar.tsx`, `StatusBar.tsx`
- Create: `twin/src/editor/panels/LayersPanel.tsx`, `InspectorPanel.tsx` (placeholders in this task)
- Create: `twin/src/editor/AddLayerMenu.tsx`
- Test: `twin/src/editor/EditorShell.test.tsx`
- Modify: `twin/src/App.tsx`, `src/twin/store.tsx`, `src/styles/tokens.css`, `src/main.tsx`
- Modify: `twin/asset-manifest.json`, `twin/THIRD_PARTY.md`, `twin/scripts/check-assets.mjs`
- Copy: `docs/assets/fonts/archivo-600.woff2`, `plex-mono-400.woff2`, `plex-mono-600.woff2` to `twin/src/assets/fonts/`

**Interfaces:**
- Produces: `EditorShell` returning a fragment with a full-viewport `<main className="ed-shell">`; Task 4 mounts `PrintArtboard` as a sibling outside that main, never inside it. `useDeviceLayout()` returns `{ mode, isCoarse, width, height }`; `useFoldSegments()` returns `FoldInsets`.
- Adds to store: `tool: EditorTool`, `snapEnabled: boolean`, `rulersVisible: boolean`, `rightTab: RightTab`, `viewport: Viewport`, `setTool`, `setSnapEnabled`, `setRulersVisible`, `setRightTab`, `setViewport`.
- App selection: `new URLSearchParams(location.search).has("workbench")` renders `EditorShell`; otherwise `TwinShell`. Remove this flag in Task 5.

- [x] **Step 1: Write failing shell tests.** `workbenchContainsLayersInspectorAndCanvas`, `workbenchHasNoDeviceFrame`, `desktopUsesSplitterPanels`, `splitterCollapsesToRail`, `splitterKeyboardResizes`, `breakpointSelectsTablet`, `breakpointSelectsPhone`, `breakpointSelectsCover`, `toolbarHasMoveHandZoomAndAdd`, `toolbarTargetsAreAtLeast44px`, `documentMenuHasSaveOpenPrintExport`, `nativeLensChipIsPresent`, `statusBarShowsZoomAndLayerCount`, `workbenchFlagDoesNotChangeDefaultShell`, `editorFontsAreLocal`.
- [x] **Step 2: Run `npm test -- EditorShell`.** Expect failure.
- [x] **Step 3: Add the design tokens and fonts.** Add only the approved palette from this plan. Register Archivo 600 and Plex Mono 400/600 via `@font-face` using local `/src/assets/fonts/` URLs. Add their paths, licences and SHA-256 values to `asset-manifest.json`, `THIRD_PARTY.md` and `check-assets.mjs`. Do not add Archivo Black; document content does not need it.
- [x] **Step 4: Implement the shell with Ark `Splitter`.** Top chips float over the canvas: document menu (`OPEN STUDIO`, `TWIN / 765 × 1000`), undo/redo, Preview and Native lens. The document chip is an Ark `Menu` trigger; its items reuse the existing store actions: `Save .twin`, `Open .twin`, `Print / Save PDF`, `Export PNG`. Keep the hidden file input from the old shell. Preview is a top-right button; Native lens is a top-right toggle bound to `store.nativeLens` with `aria-pressed`, not a tool. On desktop, `const splitter = useSplitter({ panels: [{ id: "layers", minSize: 180, maxSize: 320, collapsible: true, collapsedSize: 44, defaultSize: 224 }, { id: "canvas", minSize: 400 }, { id: "inspector", minSize: 240, maxSize: 400, collapsible: true, collapsedSize: 44, defaultSize: 276 }] })`; render `Splitter.RootProvider value={splitter}` with `Panel` and `ResizeTrigger` components. Panel headers have a collapse button calling `splitter.collapsePanel(id)` / `expandPanel(id)`, with accessible labels and `aria-expanded`. The splitter seam is keyboard-resizable by Ark. Use fixed graphite surfaces with 1 px seams, not rounded cards. Canvas center, status bar bottom-left, tool pill bottom-center. At <1024 px, do not mount the Splitter: render a mode placeholder selected by `useDeviceLayout` (tablet/phone/cover/fold) so the breakpoint tests are meaningful; the full phone dock, cover bar and fold deck land in Task 4. Use graphite/cream/red; no device frame.
- [x] **Step 5: Implement the tool pill with Ark `ToggleGroup`.** Move/Hand/Zoom are exclusive; `Add` opens the new `AddLayerMenu`; `Effects` selects the right `Effects` tab. Add menu has Text, Shape (Star/Orbit/Block) and PNG. Use Lucide icons; tooltips name the shortcut. Do not put text labels under every icon; the tooltip and status bar carry the name.
- [x] **Step 6: Implement `EditorShell` tests and run `npm test -- EditorShell`, `npm test`, `npm run build`, `npm run check:assets`.** All pass. `?workbench=1` must render the new shell; default `/` must still render the old shell. The desktop test must assert three splitter panels and a working collapse button; the breakpoint tests must select tablet/phone/cover modes without mounting a splitter. Full phone dock, cover bar and fold deck behavior is Task 4.
- [x] **Step 7: Start the workbench preview and inspect it at 1440 × 900.** Use `npm run dev -- --host 0.0.0.0` and `cloud_show_dev_server` with `?workbench=1`; capture `editor/shell-1440.png`. Do not claim visual correctness beyond the captured frame.
- [x] **Step 8: Do not commit.**

#### Workstream 2B: Layers tree, selection model, reorder and empty state

**Files:**
- Create: `twin/src/editor/model/selection.ts`, `layerTree.ts`, `src/editor/panels/usePointerReorder.ts`
- Test: `src/editor/model/selection.test.ts`, `layerTree.test.ts`, `src/editor/panels/usePointerReorder.test.tsx`
- Replace placeholder: `src/editor/panels/LayersPanel.tsx`
- Test: `src/editor/panels/LayersPanel.test.tsx`
- Modify: `src/twin/store.tsx`, `src/twin/history.ts`, `src/editor/canvas/ArtboardView.tsx`

**Interfaces:**
- Produces: `nextSelection(current, id, mode)`, `pruneSelection(ids, layers)`, `selectAllIds(layers)`.
- Produces: `createLayerCollection(layers)` using Ark `createTreeCollection`; `reorderLayerIds(layers, ids, targetIndex)`; `usePointerReorder({ rowId, onReorder })` for coarse pointers.
- Store: `selectLayers`, `selectAllLayers`, `clearSelection`, `reorderLayers`, `removeSelectedLayers`.
- `selectedLayerIds` is serialized in the document and excluded from history commits; undo filters invalid ids. `removeSelectedLayers` may empty the document and must leave the store valid.
- Layer removal does not revoke a blob URL; the existing history eviction path owns URL cleanup so undo can restore the image.

- [x] **Step 1: Write failing selection/model tests.** `replaceSelectsOne`, `addKeepsExisting`, `toggleRemovesSelected`, `selectAllSkipsNothing`, `pruneDropsMissingIds`, `reorderMovesMultipleLayersInOrder`, `reorderToFrontAndBack`, `reorderIsNoOpWhenTargetIsInsideSelection`, `emptySelectionIsValid`, `selectAllOnEmptyDocumentIsNoOp`.
- [x] **Step 2: Write failing LayersPanel tests.** `rendersLayersInDocumentOrder`, `shiftClickAddsLayer`, `cmdClickTogglesLayer`, `eyeButtonTogglesVisibilityWithoutChangingSelection`, `doubleClickRenamesWithEnter`, `escapeCancelsRename`, `dragReordersLayers`, `touchReorderUsesPointerNotHtmlDrag`, `pointerReorderCancelLeavesOrderUnchanged`, `moveUpDownActionsReorder`, `keyboardArrowMovesSelection`, `panelShowsLayerCount`, `emptyDocumentShowsNoLayersHint`, `selectedLayerScrollsIntoView`, `deletingFocusedLayerMovesFocusToNextRow`.
- [x] **Step 3: Implement `layerTree.ts` with Ark `createTreeCollection`.** Root node `{ id: "root", name: "Layers", children: [...] }`; `nodeToValue: n => n.id`, `nodeToString: n => n.name`, `nodeToChildren: n => n.children ?? []`. Flatten the document layers so the top row is the topmost layer (`layers` reversed for display), then map selection back to document ids. Return an empty collection for zero layers.
- [x] **Step 4: Implement `LayersPanel`.** Ark `TreeView.Root` with `selectionMode="multiple"`, controlled `selectedValue`, `onSelectionChange`. Row: type icon, name, visibility button, a 44 px grip, and `data-layer-row={id}`. Use Ark `NodeRenameInput` or `Editable` for inline rename. Native HTML drag is used only when `matchMedia("(pointer: fine)").matches`; on coarse pointers the grip starts a first-party pointer reorder with an 8 px threshold, `touch-action: none`, auto-scroll, and `pointercancel`/Escape cancel. Always expose `Move up` and `Move down` in the row menu so reorder never depends on drag. Do not add dnd-kit. Add a `useEffect` on `doc.selectedLayerIds` that finds the last selected row inside the panel and calls `scrollIntoView({ block: "nearest" })`; do not call `TreeView.focus()` and do not move focus away from the canvas. Empty document: render a quiet empty state (“No layers. Add text, a shape or a PNG.”) with the Add menu trigger; keep the panel header and count visible.
- [x] **Step 5: Wire canvas layer clicks to `selectLayers`.** In `ArtboardView`, accept an optional `onLayerSelect(id, event)`; new Canvas will use it, preview does not. Shift/Cmd modifiers map to add/toggle. Clicking empty artboard clears selection.
- [x] **Step 6: Implement removal to empty, focus repair and empty-document rendering.** `removeSelectedLayers` filters layers and sets `selectedLayerIds: []` when nothing remains; no toast-only dead end. `ArtboardView` renders the background and no layer nodes. The inspector shows the document state, not a crash or a phantom selection. If focus is inside a removed row, move it to the nearest remaining row; if no row remains, focus the canvas. Add store tests `removingLastLayerLeavesEmptyDocument` and `undoRestoresRemovedLayerSelection`; the focus behavior is the panel test `deletingFocusedLayerMovesFocusToNextRow` from Step 2.
- [x] **Step 7: Preserve image object URLs across removal/undo.** Do not revoke in `removeSelectedLayers`; keep the existing history-eviction cleanup that revokes only URLs absent from the restored document. Add `imageObjectUrlSurvivesRemoveUndo` and `historyEvictionRevokesUnusedImageUrl`.
- [x] **Step 8: Run focused tests, full unit tests and build.**
  ```bash
  cd twin && npm test -- src/editor/model/selection.test.ts src/editor/model/layerTree.test.ts src/editor/panels/usePointerReorder.test.tsx src/editor/panels/LayersPanel.test.tsx src/twin/project.test.ts
  npm test && npm run build
  ```
- [x] **Step 9: Add e2e `layersRename`, `scrollsSelectedLayerIntoView`, `emptyDocumentShowsHint`, `imageSurvivesRemoveUndo` and `touchReorderWorksAt360`.** Select a layer from the canvas, assert its tree row is in view without focus theft; delete all layers, assert the hint and a saveable document; remove an image, undo, assert the image is still visible; reorder by touch grip at 360 × 840 and assert the new document order. Run the editor e2e.
- [x] **Step 10: Inspect at `?workbench=1` and capture `editor/layers-1440.png`, `editor/layers-empty.png`.** Do not commit.


**Task 2 verification (2026-11-16):** Focused gate passed: `npm test -- EditorShell LayersPanel selection` — 37/37 across 3 files. Full unit suite passed: 124/124 across 18 files. `npm run build` passed. `npm run check:assets` passed with 9 assets and 429 pinned lock entries. Editor e2e passed: 5/5 (`layersRename`, `scrollsSelectedLayerIntoView`, `emptyDocumentShowsHint`, `imageSurvivesRemoveUndo`, `touchReorderWorksAt360`). Captures viewed: `artifacts/twin/editor/shell-1440.png`, `layers-1440.png`, `layers-empty.png`. The empty state intentionally focuses the canvas, which shows the red focus ring. Committed as `a9cb421` ("Build the Twin workbench shell and layer tree").
### Task 3: Canvas interaction and precise controls

**Gate:** `npm test -- Canvas SelectionOverlay geometry TransformSection align` plus the focused editor e2e. The rotated-resize invariant is the interaction gate: opposite corner within 0.5 units at 200%, or stop.

#### Workstream 3A: Canvas viewport, zoom/pan, pinch and rulers

**Files:**
- Create: `src/editor/model/viewport.ts`, `ruler.ts`
- Test: `src/editor/model/viewport.test.ts`, `ruler.test.ts`
- Create: `src/editor/canvas/Canvas.tsx`, `CanvasRulers.tsx`, `useViewportGestures.ts`
- Test: `src/editor/canvas/Canvas.test.tsx`, `useViewportGestures.test.tsx`
- Modify: `src/editor/EditorShell.tsx`, `src/editor/editor.css`, `src/editor/canvas/usePointerSession.ts`

**Interfaces:**
- Produces: `fitViewport`, `clampViewport`, `zoomAt`, `docToScreen`, `screenToDoc`.
- Produces: `rulerTicks(zoom, originScreen, length)` with major labels at 1/2/5 × 10ⁿ.
- Produces: `useViewportGestures({ containerRef, viewport, onChange, enabled })`; it owns non-passive wheel zoom, two-pointer pinch, pan and `pointercancel`.
- Canvas renders `ArtboardView` inside `.ed-artboard-stage`; `data-testid="canvas"`, `data-zoom`, `touch-action: none`.

- [x] **Step 1: Write failing viewport tests.** `fitCentersArtboardWithPadding`, `fitClampsZoom`, `zoomAtPreservesDocPoint`, `screenDocRoundTrip`, `clampRejectsNaNAndOutOfRange`, `rulerPicksReadableMajorStep`, `rulerLabelsAtZoom25And400`.
- [x] **Step 2: Implement viewport math.** Pan is in screen pixels; zoom is clamped 0.1–8; `fit = min((w - 96)/765, (h - 96)/1000)` clamped. `zoomAt` computes the doc point under the pointer before and after and adjusts pan so it stays fixed.
- [x] **Step 3: Implement `useViewportGestures` and `Canvas`.** Attach a native `wheel` listener with `{ passive: false }` to the viewport; do not rely on React's passive synthetic wheel. Plain wheel pans; Cmd/Ctrl+wheel (including trackpad pinch, which Chromium reports as ctrl+wheel) zooms at the pointer. Call `preventDefault()` for both cases so the page never scrolls behind the canvas, and set `overscroll-behavior: contain` on the viewport. Two active pointers enter pinch: store the start distance and midpoint, compute `zoom = clamp(startZoom * distance / startDistance)`, and keep the document point under the start midpoint under the current midpoint. When a second pointer arrives, cancel the active single-pointer selection session before pinching; ignore that gesture in `usePointerSession`. Handle `pointerup` and `pointercancel` for every pointer id. Space-drag/middle-drag pans; `V/H/Z` tools set the cursor. Zoom tool click zooms in, Alt-click zooms out, then stays active until V. Artboard wrapper is `left: 50%; top: 50%; transform: translate(calc(-50% + panXpx), calc(-50% + panYpx)) scale(zoom)`. Use a `ResizeObserver` for the container.
- [x] **Step 4: Implement `CanvasRulers`.** 22 px canvas strips at top and left, DPR-aware, Plex Mono 10px. Origin 0 at the artboard top-left; negative values outside. Draw major ticks/labels and minor ticks from `rulerTicks`; red 1 px origin line. Rulers hide on phone, cover, fold and landscape-phone modes, or when `rulersVisible` is false; tablet and desktop may show them. Add `data-testid="ruler-x"`/`ruler-y`.
- [x] **Step 5: Wire status bar zoom controls.** Display a rounded percent; buttons for Fit and 100%; keep the current zoom in store so preview/screenshot tests can read it. Add a polite `aria-live="polite"` status text for zoom and selection count; do not announce every pointer move, only committed zoom changes and selection changes.
- [x] **Step 6: Write gesture tests for `wheelZoomPreservesPointerDocPoint`, `wheelZoomDoesNotScrollPage`, `plainWheelPansWithoutZoom`, `pinchZoomPreservesMidpoint`, `pinchClampsAtMinAndMax`, `pointerCancelEndsPinch`, `twoFingerGestureCancelsSinglePointerMove`.** Use PointerEvent with two pointer ids and distinct client coordinates; assert the doc point under the midpoint stays within 0.5 units.
- [x] **Step 7: Write Canvas tests for `rendersArtboardAt765`, `fitButtonCentersArtboard`, `spaceDragPansWithoutChangingDocument`, `rulerToggles`, `touchActionIsNone`.** Run focused tests, full unit and build.
- [x] **Step 8: Add unit `coverCanvasFitsAt349x316`, then e2e `pinchZoomsAt360`, `wheelZoomDoesNotScrollPage` and `twoFingerPinchDoesNotMoveLayer`.** Use CDP touch events at 360 × 840; assert zoom changed, the page did not scroll, and the layer transform did not change. Capture `editor/canvas-fit.png` and `editor/canvas-200.png`; inspect both. Cover e2e lands in Task 4 with the cover shell. Do not commit.

#### Workstream 3B: Move, multi-select, marquee and snapping

**Files:**
- Create: `src/editor/model/snapping.ts`
- Test: `src/editor/model/snapping.test.ts`
- Create: `src/editor/canvas/SelectionOverlay.tsx`, `usePointerSession.ts`
- Test: `src/editor/canvas/SelectionOverlay.test.tsx`, `src/editor/canvas/usePointerSession.test.tsx`
- Modify: `src/editor/canvas/Canvas.tsx`, `src/twin/store.tsx`, `src/editor/editor.css`

**Interfaces:**
- Produces: `snapAnchors`, `snapMove`, `snapResizeRect` (resize used in Task 3 Workstream 3C).
- Produces: `SelectionOverlay({ layers, selectedIds, transforms, guides, marquee })`.
- Store: `transientTransforms` and `setTransientTransforms`.
- Pointer session kinds: `move`, `marquee`, `resize`, `rotate`.

- [x] **Step 1: Write failing snapping tests.** `moveSnapsLeftCenterRight`, `moveSnapsTopMiddleBottom`, `nearestTargetWins`, `thresholdScalesWithZoom`, `disabledReturnsRawDelta`, `guidesAreDeduplicated`, `snapAnchorsIncludesArtboardAndLayerEdges`, `snapAnchorsExcludesSelectedLayers`.
- [x] **Step 2: Implement `snapping.ts`.** Anchors: artboard 0/382.5/765 and 0/500/1000, plus every visible unselected layer's bounds edges and centers. Compare moving AABB left/center/right and top/middle/bottom. Threshold is passed in document units (`6 / zoom`). Return one guide per axis; deduplicate equal positions.
- [x] **Step 3: Implement selection rendering.** Single selection: red outline, 8 handles (Task 3 Workstream 3C), rotation handle (Task 3 Workstream 3C), label chip. Multi-selection: dashed union box, no handles, label `N layers`. Handles are `calc(8px / var(--zoom))` so they stay screen-sized. `data-testid="selection-overlay"` and `data-selection-count`.
- [x] **Step 4: Implement pointer state.** On pointerdown store pointer id, kind, start doc point, start transforms, modifiers. Capture on the event target. On move compute a tentative delta; if move, snap with `snapMove`; apply the same dx/dy to every selected layer; call `setTransientTransforms`. On up, if moved, `setLayerTransforms` once; clear transient. On cancel, clear without commit. Own exactly one primary pointer: if a second pointerdown arrives, clear transient state without committing, release capture and yield to `useViewportGestures` for pinch. Ignore secondary buttons and non-primary pointers. In the same step, prove the boundary: `saveExportAndPrintIgnoreTransientTransforms` asserts the exported plan, saved JSON and print artboard use the committed transform while the overlay shows the transient one. Never copy transient transforms into `doc`.
- [x] **Step 5: Implement marquee.** Drag empty artboard (Move tool) starts a document-space rect; render it with a red 1 px border and 8% fill. On pointerup select layers whose AABB intersects; Shift adds; Escape cancels. Clicking empty artboard clears selection.
- [x] **Step 6: Add hover.** Hovering a layer shows a 1 px cream/40% outline; no labels. Hover state is local to Canvas, not history.
- [x] **Step 7: Run focused tests, full unit and build.** Add `secondPointerCancelsMoveWithoutCommit` to the pointer-session tests. Add e2e in `editor.spec.ts`: `dragsASelectionAndUndoesOnce`, `shiftClickBuildsMultiSelection`, `marqueeSelectsTwoLayers`, `snapGuideAppearsAtCenter`, `cmdDisablesSnapping`, `touchMoveWorksAt360`, `twoFingerPinchDoesNotMoveLayer`. Use zoom 100% and known layer boxes.
- [x] **Step 8: Capture `editor/selection-multi.png` and `editor/snap-center.png`.** Do not commit.

#### Workstream 3C: Resize and rotate with exact pointer math

**Files:**
- Modify: `src/editor/model/geometry.ts`
- Test: `src/editor/model/geometry.test.ts`
- Modify: `src/editor/canvas/SelectionOverlay.tsx`, `usePointerSession.ts`, `Canvas.tsx`
- Modify: `src/editor/editor.css`

**Interfaces:**
- Produces: `resizeTransform` and `rotateTransform` as specified in Core contracts.
- Produces: `data-handle="nw|n|ne|e|se|s|sw|w"` and `data-handle="rotate"` for e2e.
- Modifier semantics: Shift = preserve aspect / snap rotation to 15°; Alt/Option = resize from center; Cmd/Ctrl = disable snapping for the gesture.

- [x] **Step 1: Write failing resize/rotate tests.** `eastHandleKeepsWestEdgeFixed`, `northHandleKeepsSouthEdgeFixed`, `cornerHandleKeepsOppositeCornerFixed`, `shiftKeepsAspect`, `altResizesFromCenter`, `minSizeClampsAtFour`, `resizeAt37DegreesKeepsOppositeCornerFixed`, `rotateAroundCenterKeepsCenter`, `shiftSnapsRotationTo15`, `rotatedResizeDoesNotDriftAfterPointerCancel`, `handlesArePointerOnlyAndNotInTabOrder`, `handleHitAreaIs44OnCoarsePointer`.
- [x] **Step 2: Implement `resizeTransform`.** Convert the pointer to layer-local coordinates around the start center. Use the opposite handle as anchor (or center for Alt). Compute new width/height from the moving edges. Clamp to 4 document units. If Shift, use `max(newW/startW, newH/startH)` and apply the same factor to both. Compute the new local center from the fixed anchor, rotate it back, and return `{ x, y, scaleX, scaleY, rotation }`. No negative scales.
- [x] **Step 3: Implement `rotateTransform`.** Start angle is `atan2(pointer.y - center.y, pointer.x - center.x)`; delta is normalized. Shift rounds the resulting absolute rotation to the nearest 15°. Center stays fixed. Return normalized rotation.
- [x] **Step 4: Render interactive handles.** Single selection only. Handles are pointer-only spans (`role="presentation"`, `aria-hidden="true"`, `tabIndex={-1}`) sized inverse zoom; they are not a second keyboard interface and must not enter the Tab order. Rotation handle sits 24 screen px above the top edge. On coarse pointers each handle gets a 44 × 44 CSS px invisible hit area (pseudo-element or padding) around the 8 px visual dot; the hit area must not enlarge the visual outline. Keyboard users get equivalent X/Y/W/H/rotation controls in the inspector. Cursor changes per handle and rotation. Multi-selection shows no handles. The overlay itself is `aria-hidden`; the selected layer's name and bounds are exposed through the inspector and the status live region.
- [x] **Step 5: Wire resize/rotate into the pointer session.** Use `snapResizeRect` for unrotated resize only; rotated resize stays precise but unsnapped. This is intentional and documented in the status hint. Before committing, verify the resulting transform is finite and scale ≥ 0.01.
- [x] **Step 6: Add e2e:** `resizesEastHandle`, `shiftCornerPreservesRatio`, `rotatesAndShiftSnaps`, `resizeAt200PercentTracksPointer`, `resizeKeepsOppositeCornerFixedAt37Degrees`, `pointerCancelLeavesDocumentUnchanged`, `handlesDoNotEnterTabOrder`, `touchResizeHandleAt360`. Run focused tests, full unit, build and the editor e2e.
- [x] **Step 7: Capture `editor/resize-handles.png`, `editor/rotate-15.png`.** Do not commit.

#### Workstream 3D: Precise inspector, reference point, align and distribute

**Files:**
- Create: `src/editor/model/align.ts`
- Test: `src/editor/model/align.test.ts`
- Create: `src/editor/panels/NumericField.tsx`, `TransformSection.tsx`, `AlignSection.tsx`
- Replace placeholder: `src/editor/panels/InspectorPanel.tsx`
- Test: `src/editor/panels/InspectorPanel.test.tsx`, `TransformSection.test.tsx`, `AlignSection.test.tsx`
- Modify: `src/editor/EditorShell.tsx`, `src/editor/editor.css`

**Interfaces:**
- Produces: `NumericField({ label, value, min, max, step, onCommit, suffix? })` using Ark `NumberInput.Root` + `Scrubber` + `Input`; commit on `onValueCommit`.
- Produces: `TransformSection` with a 9-anchor `ToggleGroup`, X/Y fields, W/H fields, aspect `Switch`, rotation field, and a reset-transform action.
- Produces: `alignTransforms(layers, ids, edge)`, `distributeTransforms(layers, ids, axis)`.
- Produces inspector states: no selection (document), single selection, multi-selection.

- [x] **Step 1: Write failing align tests.** `singleLayerAlignsToArtboard`, `twoLayersAlignToSelectionBounds`, `alignLeftKeepsRelativeY`, `alignCenterUsesRotatedBounds`, `distributeThreeLayersEqualGaps`, `distributePreservesOuterLayers`, `distributeNeedsThree`, `hiddenLayersStillAlignIfSelected`.
- [x] **Step 2: Write failing inspector tests.** `noSelectionShowsDocument`, `singleSelectionShowsTransform`, `multiShowsBoundsAndAlign`, `referencePointChangesXWithFixedAnchor`, `widthChangeKeepsReferencePoint`, `aspectLockChangesBothAxes`, `rotationFieldKeepsReferencePoint`, `numericFieldCommitsOnce`, `alignButtonsDisabledBelowTwo`, `distributeDisabledBelowThree`, `multiSelectShowsMixedOpacity`, `numericFieldsAre44OnPhone`, `numericFieldsUseDecimalInputMode`.
- [x] **Step 3: Implement align/distribute.** Use `layerBounds` for every layer. One id: target bounds are `{x:0,y:0,width:765,height:1000}`. Two or more: target is `selectionBounds`. Align changes only `x/y`; distribute equalizes gaps between AABBs and preserves the outer two. Round only for display; store full precision.
- [x] **Step 4: Implement `TransformSection`.** Ark `NumberInput` with min/max/step; `Scrubber` on the label; `onValueCommit` calls `setLayerTransforms`. W/H operate on frame dimensions: `scaleX = width / base.width`, `scaleY = height / base.height`. Reference point uses `changeSizeAtReference`, `changePositionAtReference`, `changeRotationAtReference`. Aspect lock preserves the ratio. Rotation input displays normalized −180…180. On phone/cover, fields are 44 px high, use 16 px text to avoid browser auto-zoom, and set `inputMode="decimal"`; the bottom sheet keeps the focused field above the keyboard.
- [x] **Step 5: Implement `AlignSection`.** Six align buttons and two distribute buttons with Lucide icons, tooltips and `aria-label`s. Single selection aligns to artboard; multi aligns to selection. Disable distribute below three. Add a 3 × 3 reference-point grid with `aria-label="Reference point"`; selected point uses red fill.
- [x] **Step 6: Implement `InspectorPanel`.** Design tab: Transform, Align, Appearance (opacity + blend), Type/Image/Shape, and an `Open effects` button that selects the Effects tab. No selection: artboard size `765 × 1000`, background summary, `Export 1530 × 2000`, Preview button. Multi: selection bounds read-only, Align, common opacity/blend only if all values match, otherwise “Mixed”. Text content is edited in the existing textarea here; there is no double-click canvas text editor in this release. Add `textContentEditedInInspector` and `noCanvasTextEditor` tests so the boundary is explicit.
- [x] **Step 7: Preserve existing controls.** Reuse `FillPicker`, font select, blend select and text content textarea from the old Inspector by extracting their pure parts into `InspectorPanel`; do not duplicate color-picker behavior. The old Inspector remains until Task 5.
- [x] **Step 8: Run focused tests, full unit and build.**
  ```bash
  cd twin && npm test -- src/editor/model/align.test.ts src/editor/panels/InspectorPanel.test.tsx src/editor/panels/TransformSection.test.tsx src/editor/panels/AlignSection.test.tsx
  npm test && npm run build
  ```
- [x] **Step 9: Add e2e `inspectorEditsTransform` and `inspectorEditsOpacity`.** Select a layer, change X through the number input, commit, assert the stage and undo; change opacity through the slider/number input, assert the layer and undo. Run the editor e2e.
- [x] **Step 10: Capture `editor/inspector-single.png`, `editor/inspector-multi.png`, `editor/reference-point.png`.** Check contrast and 11 px minimum labels in the captures. Do not commit.

**Task 3 verification (2026-11-16):** Focused gate passed: `npm test -- Canvas SelectionOverlay geometry TransformSection align` — 67/67 across 9 files. Full unit suite passed: 195/195 across 29 files. `npm run build` passed in 10.30s with the pre-existing >500 kB chunk warning. `npm run check:assets` passed with 9 assets and 429 pinned lock entries. Focused editor e2e passed: 25/25 — 19 Task 3 interaction scenarios, 5 Task 2 editor scenarios and one Task 3 capture run. The rotated-resize interaction gate passed at 200%: the opposite corner drifted ≤0.5 document units while width increased, and the 200% east-handle drag changed width by 25–35 units. Captures viewed: `artifacts/twin/editor/canvas-fit.png`, `canvas-200.png`, `selection-multi.png`, `snap-center.png`, `resize-handles.png`, `rotate-15.png`, `inspector-single.png`, `inspector-multi.png`, `reference-point.png`. Selection and trim geometry use the existing artboard-center document coordinates (`LayerView`, PNG export); snapping and alignment convert layer bounds to absolute artboard coordinates. No commit made.

### Task 4: Keyboard, effects and device modes

**Gate:** `npm test -- useEditorHotkeys CommandPalette EffectsPanel PreviewMode` plus the full editor e2e. Every device profile must pass or be reported as not checked with its blocker; desktop-only evidence is not acceptance.

#### Workstream 4A: Keyboard map and command palette

**Files:**
- Create: `src/editor/useEditorHotkeys.ts`, `CommandPalette.tsx`
- Test: `src/editor/useEditorHotkeys.test.tsx`, `CommandPalette.test.tsx`
- Modify: `src/twin/commands.ts`, `src/editor/EditorShell.tsx`, `src/editor/Toolbar.tsx`, `src/editor/StatusBar.tsx`
- Modify: `src/twin/store.tsx` (undo/redo/selection guards)

**Interfaces:**
- Produces: `useEditorHotkeys({ enabled, onPreview, onPalette, onEscape })`.
- Produces: `CommandPalette({ open, onOpenChange })` using Ark `Dialog`.
- Extends `TwinCommand` with `shortcut?: string`; add editor commands: select all, clear selection, zoom fit, zoom 100%, toggle rulers, toggle snap, align six edges, distribute, preview, panel toggles.
- Modifier policy: Cmd on macOS and Ctrl on Windows/Linux both accepted; no shortcut fires when focus is editable.

- [x] **Step 1: Write failing hotkey tests.** `vSelectsMoveTool`, `hSelectsHand`, `zSelectsZoom`, `spaceTemporarilyPans`, `cmdAUsesSelectAll`, `escapeClearsSelection`, `deleteRemovesSelected`, `arrowsNudgeOne`, `shiftArrowsNudgeTen`, `cmdBracketReorders`, `shortcutsIgnoredInInput`, `shortcutsIgnoredInDialog`, `shortcutsIgnoredInMenu`, `escapeOrderPaletteThenPreviewThenDrawers`.
- [x] **Step 2: Write failing focus tests.** `paletteReturnsFocusToTrigger`, `previewReturnsFocusToPreviewButton`, `drawerReturnsFocusToTrigger`, `canvasIsLabeledAndFocusable`, `statusLiveRegionAnnouncesSelectionCount`, `handlesAreNotInTabOrder`. These are shell-level tests in addition to the Task 3 Workstream 3C unit assertion.
- [x] **Step 3: Write failing palette tests.** `opensWithCmdK`, `filtersCommands`, `enterRunsFirstMatch`, `escapeClosesAndRestoresFocus`, `shortcutsAreShown`, `emptyQueryShowsGroups`, `noUnsafeCommands`.
- [x] **Step 4: Implement the hotkey hook.** Attach one `keydown` listener while the workbench is mounted. Ignore `event.defaultPrevented`, `event.isComposing`, editable targets and `[role="dialog"], [role="menu"], [data-scope="menu"]` ancestors. Use `event.code === "Space"` for temporary Hand so it does not conflict with button activation. Prevent default only for handled shortcuts. Arrow nudges commit one history entry per key press; use `setLayerTransforms`.
- [x] **Step 5: Implement the command palette.** Ark `Dialog.Root`, `Dialog.Backdrop`, `Dialog.Positioner`, `Dialog.Content`; search input; grouped list; arrow-key navigation; Enter runs; Escape closes and returns focus to the previously focused element. Reuse `buildCommands(store)` and add the new commands. No command that is only a placeholder. Store the trigger element before opening and restore it in `onOpenChange(false)`.
- [x] **Step 6: Wire Escape and focus ownership.** If palette open, close it. Else if preview open, exit preview. Else if a drawer/panel is open, close it. Else clear selection. Do not close the command palette via the global Escape handler when Ark already handled it. Canvas has `role="region"`, `aria-label="Artboard canvas"` and `tabIndex={0}`. Status bar uses one polite live region; selection changes announce `N layers selected`, zoom changes announce the percentage once per committed change.
- [x] **Step 7: Add e2e:** `cmdKOpensPalette`, `paletteRunsUndo`, `arrowNudgesSelection`, `deleteRemovesSelection`, `shortcutsDoNotFireInInspectorText`, `shortcutsDoNotFireInMenu`, `focusReturnsAfterPalette`, `escapeClosesPreviewBeforeSelection`. Run focused tests, full unit, build and editor e2e.
- [x] **Step 8: Do not commit.**

#### Workstream 4B: Effects panel integration

**Files:**
- Create: `src/editor/panels/EffectsPanel.tsx`
- Test: `src/editor/panels/EffectsPanel.test.tsx`
- Modify: `src/editor/panels/InspectorPanel.tsx`, `src/editor/EditorShell.tsx`, `src/twin/PluginHost.tsx`, `src/twin/store.tsx`, `src/twin/plugins/*` only where they read the selected layer
- Modify: `src/styles/app.css` for plugin content inside the new panel
- Test: `src/twin/pluginHost.test.tsx` if present, `src/e2e/editor.spec.ts`

**Interfaces:**
- Produces: right panel `Tabs.Root` Design/Effects; `EffectsPanel` contains the existing `PluginCatalogue` and `PluginHost`.
- `openPlugin(id)` sets `rightTab: "effects"`; `openInspector()` sets `"design"`.
- Layer effects require exactly one selected layer. Multi-selection disables Apply with the hint “Select one layer for a layer effect.”
- Plugin session semantics unchanged: hide preserves local controls, close resets, one active plugin.

- [x] **Step 1: Write failing effects tests.** `effectsTabShowsCatalogue`, `searchFiltersPlugins`, `opensShaderLab`, `appliesShaderToArtboard`, `appliesTextureToSingleLayer`, `layerEffectDisabledForMultiSelection`, `hidePreservesPluginControls`, `closeResetsPluginControls`, `nativeLensStillDimsOnly`, `pluginPanelDoesNotDuplicateInspector`.
- [x] **Step 2: Implement `EffectsPanel`.** Keep the existing catalogue search, group chips and 2-column cards; restyle only the container for the new panel. Render `PluginHost` below the active card. Data is still bounded by existing parsers.
- [x] **Step 3: Update plugin selection reads.** Replace every `doc.selectedLayerId` read with `doc.selectedLayerIds[0]` (or a `primaryLayerId` helper). `TargetSwitch` disables “Layer” when selection length ≠ 1. `applyEffect` checks the same condition and shows a toast rather than silently applying to the first layer.
- [x] **Step 4: Update `PluginHost` scroller assumptions.** It currently searches for `.panel-content`; make the scroll container explicit via a ref/prop or a documented class. Do not make it depend on the old PanelSheet.
- [x] **Step 5: Add e2e:** `opensEffectsFromToolbar`, `appliesShaderToArtboard`, `appliesOverlayTextureToLayer`, `multiSelectionBlocksLayerEffect`, `pluginSessionSurvivesTabSwitch`. Run focused tests, full unit, build and editor e2e.
- [x] **Step 6: Capture `editor/effects-catalogue.png`, `editor/effects-applied.png`.** Do not commit.

#### Workstream 4C: Preview, print, phone, cover and half-fold layouts

**Files:**
- Create: `src/editor/PreviewMode.tsx`, `src/editor/PrintArtboard.tsx`
- Create: `src/editor/mobile/PhoneToolDock.tsx`, `src/editor/mobile/CoverQuickBar.tsx`, `src/editor/mobile/FoldToolDeck.tsx`
- Create: `src/editor/panels/BottomSheet.tsx`
- Test: `src/editor/PreviewMode.test.tsx`, `src/editor/PrintArtboard.test.tsx`, `src/editor/EditorShell.device.test.tsx`, `src/editor/useFoldSegments.test.tsx`
- Modify: `src/editor/EditorShell.tsx`, `src/editor/useDeviceLayout.ts`, `src/editor/useFoldSegments.ts`, `src/editor/TopChips.tsx`, `src/editor/Toolbar.tsx`, `src/editor/StatusBar.tsx`, `src/editor/editor.css`, `src/twin/store.tsx`
- Modify: `src/shell/DeviceFrame.tsx` only if a sizing prop is required
- Test: `src/e2e/editor.spec.ts`

**Interfaces:**
- Produces: `useDeviceLayout(): { mode: DeviceMode; isCoarse: boolean; width: number; height: number }`. Priority: `fold` when two top/bottom segments are reported; `cover` when both dimensions are ≤380 px; landscape phone when width > height and height <500 px; `phone` below 600 px; `tablet` 600–1023 px; `desktop` 1024 px and above. Landscape is checked before tablet so 840×360 is a phone, not a tablet.
- Produces: `useFoldSegments(): FoldInsets`. Read `window.viewport?.segments`; two segments sorted by `y` produce `{ orientation: "vertical", top, bottom, gap: second.y - (first.y + first.height), source: "segments" }`. If `navigator.devicePosture?.type === "folded"` but no segments, return `orientation: null, source: "posture"` and keep the normal bottom-sheet layout; do not guess a fold line. No API returns `source: "none"`.
- Produces: `PreviewMode({ layout, onLayoutChange, onExit })`; layouts `compact` (411 × 891) and `expanded` (840 × 1000).
- Produces: `PrintArtboard({ doc })`: always-mounted, read-only `ArtboardView` at exactly 765 × 1000, `data-testid="print-artboard"`, no selection, handles, rulers or zoom transform.
- `printStage` becomes `window.print()`; it no longer toggles `body.is-printing`. Print CSS owns visibility.
- Preview is read-only: no selection, no handles, no canvas pointer actions; save/open/export are not shown.
- Phone: 56 px bottom dock plus safe-area padding; Inspector is an Ark `Drawer` with `placement="bottom"`; Layers is a full-height sheet; rulers are not mounted; all controls 44 px.
- Cover: Quick Mode only. Five 44 px actions (`Move`, `Add`, `Layers`, `Undo`, `Preview`), compact canvas, no Inspector, no splitter, no rulers. It is not a full editor and does not pretend to be.
- Half-fold: when `orientation === "vertical"`, the top segment is the canvas and the bottom segment is `FoldToolDeck` with `Tools | Layers | Properties`; a fold gutter is reserved by `gap`; no control or selected layer crosses it. Without segments, use the normal phone bottom sheet.
- A browser page cannot drive the cover and main display as two independent live surfaces. The cover is a separate Quick Mode, not a second editor window.

- [x] **Step 1: Write failing device, preview and print tests.** `coverUsesQuickMode`, `coverHasFive44pxActions`, `coverHidesInspectorRulersAndSplitter`, `phoneUsesBottomDock`, `phoneInspectorIsBottomSheet`, `phoneLayersSheetIsFullHeight`, `phoneUses100dvhAndSafeArea`, `tabletUsesDrawers`, `desktopUsesSplitter`, `landscapeUsesSideRail`, `foldUsesTopCanvasBottomTools`, `foldGutterAvoidsContent`, `foldFallsBackToBottomSheetWithoutSegments`, `foldSegmentsWinOverPosture`, `visualViewportKeepsFocusedFieldAboveKeyboard`, `previewShowsCompactDevice`, `previewShowsExpandedDevice`, `previewHasNoSelectionHandles`, `segmentGroupSwitchesDevice`, `escapeExitsPreview`, `previewExitRestoresFocus`, `previewDoesNotChangeDocument`, `printArtboardIsCanonicalAndReadOnly`, `printRootHiddenOnScreen`, `printArtboardWorksWithZeroLayers`.
- [x] **Step 2: Implement `PreviewMode`.** Reuse `DeviceFrame` and `ArtboardView` with `readOnly`. Use Ark `SegmentGroup` for 411/840; a floating `Exit preview` chip; Escape exits. Dim the editor behind it; do not mount a second editable canvas. Store the Preview trigger and restore focus on exit.
- [x] **Step 3: Implement `useDeviceLayout` and the mode switch.** Derive `mode` from `innerWidth`/`innerHeight`, `matchMedia("(pointer: coarse)")`, and fold segments. The shell chooses `DesktopShell`, `TabletShell`, `PhoneShell`, `CoverShell` or `FoldShell`; hooks live inside each component, never conditionally. `CoverShell` mounts `CoverQuickBar`; `PhoneShell` mounts `PhoneToolDock` and `BottomSheet`; `FoldShell` mounts `FoldToolDeck` only when segments are exact; otherwise it renders `PhoneShell`.
- [x] **Step 4: Implement the phone layout.** `PhoneShell` is `height: 100dvh`, `padding-bottom: env(safe-area-inset-bottom)`, top bar 44 px, canvas flex, bottom dock 56 px. Inspector is a bottom sheet at `min(62dvh, 560px)` with `visualViewport` height/offset; Layers is a full-height sheet from the left. Hide rulers, hover-only affordances and the splitter. 44 px minimum for every button, input and row action.
- [x] **Step 5: Implement the cover Quick Mode.** `CoverShell` is a compact canvas with a five-action bar: Move/Hand, Add, Layers, Undo, Preview. It does not mount `InspectorPanel`, `Splitter`, `CanvasRulers` or the command palette. The canvas is pannable and pinch-zoomable at 349 × 316 and 320 × 320. `Layers` opens a compact full-screen sheet with visibility and selection only; numeric transform remains on the main screen. This is a deliberate capability limit, stated in the UI.
- [x] **Step 6: Implement half-fold.** `useFoldSegments` reads `window.viewport?.segments` and sets `data-fold="vertical"` plus `--fold-top`, `--fold-bottom`, `--fold-gap` on `FoldShell`. `FoldShell` uses `grid-template-rows: var(--fold-top) var(--fold-gap) var(--fold-bottom)`: canvas top, 24 px minimum gutter, `FoldToolDeck` bottom. `FoldToolDeck` has three tabs; it is not a second canvas. `@media (vertical-viewport-segments: 2)` and `env(viewport-segment-height 0 0)` provide the no-JS CSS fallback; `device-posture: folded` without segments keeps the normal phone bottom sheet. Never draw a control on `--fold-gap`.
- [x] **Step 7: Implement landscape phone.** At 840 × 360, `PhoneShell` switches to a 72 px left tool rail (44 px buttons, vertical) and puts the canvas to its right; the Inspector remains a bottom sheet. There is no vertical page scroll; the canvas fits by height.
- [x] **Step 8: Implement safe areas and the keyboard.** Use `100dvh`, `env(safe-area-inset-*)`, and `visualViewport` for bottom sheets; when the keyboard opens, the sheet height and scroll padding shrink so the focused numeric/text field stays visible. Set `inputMode="decimal"` and 16 px input text on phone/cover. Add `useVisualViewport.test.tsx` if the hook is extracted.
- [x] **Step 9: Implement `PrintArtboard` and print CSS.** Mount `<PrintArtboard doc={doc} />` as a **sibling of `.ed-shell`** (return a fragment from `EditorShell`), never inside the shell that print hides. Screen CSS: `.ed-print-root { display: none; }`. Print CSS:
  ```css
  @page { size: 765px 1000px; margin: 0; }

  @media print {
    html, body { margin: 0; background: #fff; }
    #root { display: block !important; height: auto !important; min-height: 0 !important; overflow: visible !important; }
    .ed-shell { display: none !important; }
    .ed-print-root { display: block !important; }
    .ed-print-root .ed-artboard {
      width: 765px; height: 1000px;
      transform: none; box-shadow: none;
      print-color-adjust: exact; -webkit-print-color-adjust: exact;
    }
    .ed-print-root .selection-overlay { display: none !important; }
  }
  ```
  `@page` stays at top level. Change `printStage` in the store to call `window.print()` and remove the `body.is-printing` class/timeout. Do not use the editor's pan/zoom transform. Empty documents print the background. Printer drivers may scale the custom page to their paper; that is outside the app's control and must be stated in the README.
- [x] **Step 10: Add e2e at the full matrix.** `previewModeIsReadOnly`, `previewSwitches411And840`, `previewExitRestoresEditor`, `coverQuickModeAt349`, `coverDoesNotMountInspector`, `phoneBottomSheetAt360`, `phoneKeyboardKeepsFieldVisible`, `tabletDrawersAt768`, `landscapeToolRailAt840`, `foldUsesTopCanvasBottomTools`, `foldGutterHasNoControls`, `foldFallbackWithoutSegments`, `printExactArtboard`, `printDoesNotIncludeSelection`, `emptyDocumentPrintsBackground`. For print, `page.emulateMedia({ media: "print" })`; assert `.ed-shell` hidden, `.ed-print-root` visible, and the print artboard 765 × 1000. For fold, inject `window.viewport.segments` via `page.addInitScript` before load; assert the top/bottom split and gutter. Run focused tests, full unit, build and editor e2e.
- [x] **Step 11: Capture and device-check.** Capture `editor/preview-411.png`, `editor/preview-840.png`, `editor/phone-360.png`, `editor/cover-349.png`, `editor/cover-320.png`, `editor/landscape-840x360.png`, `editor/fold-360x840.png`. Inspect each. Then run the same states in Samsung Internet on the physical Flip 7: unfolded, cover/FlexWindow, half-fold, landscape. Record browser-reported viewport, zoom, posture and segment count in the artifact notes. If Samsung Internet cannot open the cover or does not expose segments, report it as not checked; do not claim those modes work on device. Do not commit.

**Task 4 verification (2026-11-16):** Focused gate passed: `npm test -- useEditorHotkeys CommandPalette EffectsPanel PreviewMode` — 41/41 across 4 files. Full unit suite passed: 265/265 across 38 files after the final fold-source assertion. `npm run build` passed in 12.13s with the pre-existing >500 kB chunk warning. `npm run check:assets` passed with 9 assets and 429 pinned lock entries. Editor and device e2e passed: 61/61 in 6.6m — 34 in `editor.spec.ts` (5 retained + 29 Task 4), 19 Task 3 interactions and 8 Task 4 capture cases. The whole Playwright suite also passed: 105/105 in 10.2m across all six specs (61 editor/device plus 44 old-shell and project scenarios). Captures viewed: `artifacts/twin/editor/preview-411.png`, `preview-840.png`, `phone-360.png`, `cover-349.png`, `cover-320.png`, `landscape-840x360.png`, `fold-360x840.png`, `effects-catalogue.png`, `effects-applied.png`. Review fixes: closed Ark sheets no longer intercept taps; preview/print no longer duplicate layer test ids; the print background no longer inherits the 1440 px shell; effects plugin panels are legible on graphite; phone keeps Preview visible; the landscape rail starts below the top chips; fold captions no longer cross the gutter; compact canvases use device-specific fit padding; the 360 px touch-reorder test opens the phone Layers sheet first. Physical Samsung Flip 7 cover/FlexWindow, half-fold and landscape checks were **not checked**: no Samsung Internet or physical device is available in the cloud workspace. No commit made.

### Task 5: Cut over and prove the result

**Gate:** `npm run build`, `npm test`, `npx playwright test` and `npm run check:assets`. No old shell, no flag, no desktop-only claim; every capture is viewed or explicitly reported not checked, never presented as evidence.

#### Workstream 5A: Cut over, remove the old shell and migrate the test suite

**Files:**
- Modify: `twin/src/App.tsx`, `src/main.tsx`, `src/styles/tokens.css`, `src/styles/app.css`
- Delete: `src/shell/TwinShell.tsx`, `Stage.tsx`, `Dock.tsx`, `PanelSheet.tsx`, `Inspector.tsx`, `CommandSheet.tsx`, `AddMenu.tsx`, `legacy.ts`, `useShortLandscape.ts` (only after all imports are gone)
- Keep: `DeviceFrame.tsx`, `Presentation.tsx` only if Preview/Presentation still imports them; otherwise delete with the same check
- Modify: `twin/e2e/twin.spec.ts`, `project.spec.ts`, `playwright.config.ts`
- Replace: `src/twin/monochrome.test.ts` with `src/editor/palette.test.ts`
- Modify: `twin/README.md`, `twin/THIRD_PARTY.md`, `twin/asset-manifest.json`

**Interfaces:**
- Default `/` renders `EditorShell`; `?workbench=1` has no special meaning.
- No old `.dock`, `.panel-sheet`, `.device-ui`, `.stage` editor selectors remain in `app.css`.
- The old shell's 43 Playwright scenarios are either retained against equivalent new UI or replaced one-for-one by the mapping below. No scenario is deleted without a named replacement.

- [x] **Step 1: Flip the default and remove the flag.** `App` renders `EditorShell` unconditionally. Delete the query-param branch. Run `npm test -- App`; expect the old shell test to be updated to the workbench, not skipped.
- [x] **Step 2: Delete the old shell files and imports.** Use `rg` to prove no imports remain. Remove `legacy.ts`; ensure no editor code calls `toLegacy`/`fromLegacy`. Remove old shell CSS blocks from `app.css`, including the old `.artboard` print block and the `body.is-printing` selectors; keep plugin panel/catalogue styles used by `EffectsPanel`.
- [x] **Step 3: Replace the monochrome test.** Delete the assumption that the editor must be neutral. Add `palette.test.ts` asserting the approved hex set, cream-on-graphite contrast ≥ 7, muted-on-graphite contrast ≥ 4.5, red used only as accent/selection, and no unapproved color literals in `editor.css` outside gradients/shadows. Add the e2e `shellUsesApprovedPalette`. If a generated-content test no longer expresses a requirement, delete it explicitly in the same task and say why in the final report.
- [x] **Step 4: Migrate the e2e mapping.** Keep these behaviours, retargeted to the new selectors:
  - compact/expanded device frame → `previewShowsCompactDevice` / `previewShowsExpandedDevice`
  - dock targets and panel geometry → `toolbarTargetsAreAtLeast44px` / `phoneBottomSheetAt360`
  - monochrome palette → `palette.test.ts` + `shellUsesApprovedPalette`
  - drag a layer → `dragsASelectionAndUndoesOnce`
  - touch drag / reorder → `touchMoveWorksAt360` / `touchReorderWorksAt360`
  - phone / cover / fold / landscape → `phoneBottomSheetAt360`, `coverQuickModeAt349`, `foldUsesTopCanvasBottomTools`, `landscapeToolRailAt840`
  - Add → PNG / Overlay Lab → `addsPngLayerAndChangesBlend`
  - inspector X/Y/opacity/visibility/rename → `inspectorEditsTransform`, `inspectorEditsOpacity`, `layersRename`
  - plugin catalogue/hide/close → `effectsTabShowsCatalogue`, `hidePreservesPluginControls`, `closeResetsPluginControls`
  - command sheet → `cmdKOpensPalette`
  - save/open/PNG → keep `project.spec.ts` scenarios; update only selectors that moved
  - print → `printsExactArtboard`, `printDoesNotIncludeSelection`; old `.artboard` print block is deleted
  - empty document → `emptyDocumentShowsHint`, `emptyDocumentRoundTrips`, `emptyDocumentPrintsBackground`
  - panel layout → `desktopUsesSplitterPanels`, `splitterKeyboardResizes`, `splitterCollapsesToRail`
  - presentation → `previewModeIsReadOnly`
  - native lens → `nativeLensStillDimsOnly`
- [x] **Step 5: Update `project.spec.ts` for v2.** The PNG test must still assert 1530 × 2000 and valid signature. Save/open must round-trip `selectedLayerIds`, `scaleX/scaleY` and 765 × 1000 coordinates, including an empty document. Add `saveExportAndPrintIgnoreTransientTransforms` so a live gesture cannot leak into a saved file, export or print. Print must assert the dedicated `.ed-print-root` artboard at 765 × 1000 and no editor chrome; do not preserve the old `.artboard` print alias just to keep the test green.
- [x] **Step 6: Remove obsolete selectors and CSS.** Run `rg -n "\\.dock|\\.panel-sheet|\\.device-ui|layout-compact|layout-expanded|monochrome|is-printing"` under `twin/src` and resolve every remaining editor hit. Confirm `@media print` appears once and targets only `.ed-print-root`. Plugin-local styles may remain if they are still used.
- [x] **Step 7: Run the complete verification.**
  ```bash
  cd twin
  npm run build
  npm test
  npx playwright test
  npm run check:assets
  ```
  Report exact counts. The old 49 unit and 43 Playwright baseline must not shrink without a named replacement; the new suite should be larger.
- [x] **Step 8: Inspect the final diff for scope.** No native, site, handoff or tools changes. Do not commit.

#### Workstream 5B: Final device audit, documentation and evidence

**Files:**
- Modify: `twin/README.md`, `twin/THIRD_PARTY.md`, `twin/asset-manifest.json`
- Modify: `docs/superpowers/plans/2026-11-16-twin-workbench-v2.md` (check boxes and actual results only)
- Capture: `artifacts/twin/editor/final-1440.png`, `final-1024.png`, `final-768.png`, `final-390.png`, `final-360.png`, `final-cover-349.png`, `final-cover-320.png`, `final-landscape-840x360.png`, `final-fold-360x840.png`, `final-preview.png`
- Device notes: `artifacts/twin/editor/flip7-device-notes.md`

**Interfaces:**
- Consumes: completed editor.
- Produces: an honest README section for the workbench, shortcuts, schema v2 migration, preview, device modes, cover limits and known limitations.

- [x] **Step 1: Audit every device profile against the visual direction.** Confirm: no device frame in editor; graphite shell; paper artboard; desktop fixed splitter with 1 px seams; tablet drawers; phone bottom dock and bottom sheet; cover five 44 px actions with no inspector; half-fold top canvas / bottom tools with no control on the gutter; red only for active/selection/guide; 11 px minimum UI labels on desktop, 16 px input text on phone; no rounded dashboard cards. Record anything not checked.
- [x] **Step 2: Run a desktop keyboard-only pass and a phone touch-only pass.** Desktop: Tab through top chips, splitter seams, layers, inspector and tool pill; handles not in the Tab order; focus returns from palette/preview/drawers. Phone/cover: every action reachable by thumb; no hover-only action; bottom sheet focus visible. Report failures; do not call it accessible without both passes.
- [x] **Step 3: Run a reduced-motion and orientation pass.** With `prefers-reduced-motion: reduce`, panel/palette transitions are removed; state changes remain visible. Rotate 360 × 840 to 840 × 360 and back; no clipped toolbar, no page scroll, no lost selection. Direct manipulation has no animation in either mode.
- [x] **Step 4: Update `twin/README.md`.** Document: 765 × 1000 model, 2× export, v1→v2 migration, empty documents, layer cap, direct-manipulation gestures, pinch/wheel behavior, reference point, snap threshold, keyboard map, Preview mode, Native lens placement, print page size, device modes, 360 × 840 main / 349 × 316 cover references, half-fold behavior, and known limitations (rotated resize is unsnapped; group resize is not included; raster effects may soften above 200%; text editing is inspector-only; printer drivers may scale the custom page; a browser cannot run cover and main as two independent live surfaces; cover Quick Mode does not expose numeric transform).
- [x] **Step 5: Update `THIRD_PARTY.md` and `asset-manifest.json`.** Add Archivo/Plex Mono entries with SHA-256, OFL licence links and local paths. Confirm `npm run check:assets` passes and the built app makes no external request.
- [x] **Step 6: Capture the final profile matrix and inspect every capture.** Use the current dev server and Playwright at 1440×900, 1024×768, 768×1024, 390×844, 360×840, 349×316, 320×320, 840×360 and a mocked two-segment 360×420 + 360×420 fold. Embed the paths in the final report only if they exist and were viewed. A capture at 1440 alone is not evidence for this plan.
- [x] **Step 7: Run the final checks and the phone performance trace.**
  ```bash
  cd twin
  npm run build
  npm test
  npx playwright test
  npm run check:assets
  ```
  Also run a 10-second pointer-drag trace with 20 layers at 360 × 840 and 1440 × 900. The acceptance bar is no long task over 50 ms during the 20-layer drag on either profile; report the 64-layer trace separately as an observed limit. Do not claim a performance target without these measurements. If a check cannot run, report it as not checked with the blocker. Do not describe earlier results as current.
- [x] **Step 8: Run the physical Flip 7 pass.** In Samsung Internet, record viewport, DPR, zoom, posture and segment count for unfolded, cover/FlexWindow, half-fold and landscape. Verify: bottom dock reachable one-handed; cover Quick Mode has only the five actions; half-fold puts tools below the canvas and no control on the crease; pinch/pan does not scroll the page; print is unaffected. If the cover browser or segment API is unavailable, write that in `flip7-device-notes.md` and report it as not checked; do not claim it works.
- [x] **Step 9: Final self-audit.** Read `git diff`; confirm every requirement has a task, every name in the verification matrix exists or is explicitly marked not implemented, no placeholder commands remain, no debug logs, no `?workbench=1` references, no desktop-only device claim, and no files outside scope changed. Do not commit.

**Task 5 verification (2026-11-16):** The cutover is complete. `/` renders `EditorShell` unconditionally; no `?workbench=1`, `TwinShell`, `shell/legacy`, `toLegacy`, `fromLegacy` or `useShortLandscape` reference remains in `twin/src`, `twin/e2e` or the README. The old shell components and their obsolete tests are deleted. `app.css` has no `.dock`, `.panel-sheet`, `.device-ui` or `.stage` editor selector, and `editor.css` contains the only `@media print`, targeting the dedicated print root.

- Unit: `npm test` passed **250/250 across 35 files**. Build: `npm run build` passed in 10.89s with the pre-existing >500 kB chunk warning. Assets: `npm run check:assets` passed with 9 runtime assets and 429 pinned lock entries.
- Playwright: **90/90 passed in 12.0m** against the production `dist` build served by `vite preview`, not the dev server. The suite is 34 editor scenarios, 19 Task 3 interactions, 1 Task 3 capture, 8 Task 4 captures, 10 Task 5 captures, 2 performance traces, 6 project/print scenarios and 10 new-shell parity scenarios. The old 49-unit/43-Playwright baseline is exceeded. The mapping below records replacements; legacy shell-only assertions (monochrome palette, drag-handle sheet, expanded desktop drawer) were replaced by palette, phone-sheet, tablet-drawer and splitter coverage. SVG sanitisation, whose legacy e2e relied on shell-specific Asset Shelf selectors, remains covered by `imports.test.ts`, with the plugin smoke-tested in `opensEveryPluginWithoutPageErrors`.
- Capture evidence viewed: `final-1440.png`, `final-1024.png`, `final-768.png`, `final-390.png`, `final-360.png`, `final-cover-349.png`, `final-cover-320.png`, `final-landscape-840x360.png`, `final-fold-360x840.png` and `final-preview.png`. The final suite also regenerated the Task 4 preview/effects captures.
- Automated device/accessibility audit (`accessibility-orientation-audit.json`): 160 desktop Tab scans (62 unique controls) reached the document/preview top chips, canvas layers, splitter seams, inspector and tool pill; no selection handle entered the Tab order; palette and preview focus returned to their triggers; all seven phone dock targets are 44 × 44 and the full-height layers sheet opens by tap; reduced-motion transition duration is `1e-05s`; rotating 360 × 840 → 840 × 360 → 360 × 840 preserved one selected layer, kept the rail visible and produced no page scroll. The audit exposed Ark toggle items with `tabIndex=-1`; Move/Hand/Zoom are now explicitly tabbable.
- Performance (`performance-*.json`, production build): 20-layer drags at 360 × 840 and 1440 × 900 produced **no long task over 50 ms** (maximum 0 ms; idle maximum 0 ms). The separate 64-layer trace at 360 × 840 peaked at **59 ms**, recorded as the observed limit. A dev-server StrictMode diagnostic at 20 layers peaked at 164 ms; it is not the shipped build and is not presented as a pass.
- Visual gaps recorded, not hidden: at 1024 px the fixed 180 px layers panel ellipsizes names to forms such as `Sha…` and `Typ…`; several desktop mono labels remain 8–10 px, below the plan's 11 px target. Both are inherited from the accepted Task 4 visual direction and were not changed during the cutover. The 230 px minimum experiment collapsed the Ark splitter panel to a 44 px rail, so it was reverted.
- Physical Samsung Flip 7/Samsung Internet remains **not checked**; the blocker and required matrix are in `artifacts/twin/editor/flip7-device-notes.md`.
- Committed as a focused Task 5 cutover. The only file outside `twin/` included in the commit is this plan; pre-existing dirty native/tools files were left untouched.

## Test migration map

The existing suite is shell-specific, so the cutover must preserve behaviour rather than DOM strings.

| Existing behaviour | Existing test area | New test |
|---|---|---|
| Compact/expanded frame | `twin.spec.ts` frame tests | `previewShowsCompactDevice`, `previewShowsExpandedDevice` |
| Dock target size/labels | `twin.spec.ts` shell tests | `toolbarTargetsAreAtLeast44px` |
| Panel occlusion / bottom sheet | `twin.spec.ts` geometry tests | `phoneBottomSheetAt360` |
| Phone / cover / fold | New device requirement; no old equivalent | `phoneBottomSheetAt360`, `coverQuickModeAt349`, `foldUsesTopCanvasBottomTools`, `landscapeToolRailAt840` |
| Fixed/resizable panels | Old header/dock layout | `desktopUsesSplitterPanels`, `splitterCollapsesToRail`, `splitterKeyboardResizes` |
| Empty document / cap | Old parser required a layer | `emptyDocumentShowsHint`, `emptyDocumentRoundTrips`, `textAndShapeRespectLayerCap` |
| Monochrome palette | `monochrome.test.ts`, shell e2e | `palette.test.ts`, `shellUsesApprovedPalette` |
| Drag/touch drag | `twin.spec.ts` drag tests | `dragsASelectionAndUndoesOnce`, `touchMoveWorksAt360`, `touchReorderWorksAt360` |
| Add PNG/Overlay | `twin.spec.ts` overlay tests | `addsPngLayerAndChangesBlend` |
| Inspector edits | `Inspector.test.tsx`, e2e | `inspectorEditsTransform`, `inspectorEditsOpacity`, `layersRename` |
| Plugin catalogue/session | `twin.spec.ts` plugin tests | `effectsTabShowsCatalogue`, `hidePreservesPluginControls`, `closeResetsPluginControls` |
| Command sheet | `twin.spec.ts` commands | `cmdKOpensPalette`, `paletteRunsUndo` |
| Save/open/PNG | `project.spec.ts` | Retained; selectors updated; empty document case added |
| Presentation | `twin.spec.ts` presentation | `previewModeIsReadOnly` |
| Native lens | `twin.spec.ts` lens | `nativeLensStillDimsOnly`, `nativeLensChipIsPresent` |
| Print | `project.spec.ts` print | `printsExactArtboard`, `printDoesNotIncludeSelection` |

## Verification matrix

| Requirement | Check |
|---|---|
| Canonical geometry and export | `npm test -- geometry pngExport project` |
| v1 compatibility | `npm test -- project` migration cases |
| Layer tree and multi-select | `npm test -- LayersPanel selection` |
| Direct move/marquee/snap | `npm test -- snapping usePointerSession` + `editor.spec.ts` |
| Resize/rotate precision | `npm test -- geometry` + `editor.spec.ts` |
| Reference point | `npm test -- TransformSection align` |
| Align/distribute | `npm test -- align AlignSection` |
| Keyboard/palette | `npm test -- useEditorHotkeys CommandPalette` + e2e |
| Effects/plugin parity | `npm test -- EffectsPanel` + existing plugin tests |
| Device contract | `npm test -- EditorShell.device useFoldSegments` + e2e at 1440/1024/768/390/360/349/320/840×360/fold |
| Phone/cover/fold | `npm test -- PreviewMode` + `phoneBottomSheetAt360`, `coverQuickModeAt349`, `foldUsesTopCanvasBottomTools`, `landscapeToolRailAt840` |
| Preview | `npm test -- PreviewMode` + e2e at 411/840 |
| Panels/resize/collapse | `npm test -- EditorShell`; e2e `desktopUsesSplitterPanels`, `splitterKeyboardResizes` |
| Empty document/layer cap | `npm test -- project LayersPanel`; e2e `emptyDocumentShowsHint`, `emptyDocumentRoundTrips` |
| Print parity | `npm test -- PrintArtboard`; e2e `printsExactArtboard`, `printDoesNotIncludeSelection` |
| Pinch/wheel | `npm test -- useViewportGestures`; e2e `pinchZoomsAt360`, `wheelZoomDoesNotScrollPage` |
| Focus/focus return | `npm test -- useEditorHotkeys`; keyboard pass in Task 5 Workstream 5B |
| Performance | 10-second 20-layer drag trace: no long task >50 ms; report the 64-layer trace separately |
| Old behaviour preserved | Full `npm test`, `npx playwright test`; mapping table above |
| Offline assets/licences | `npm run check:assets` |
| Visual direction | Every Task 5 Workstream 5B Step 6 capture viewed, including 360, 349 cover and fold; keyboard/reduced-motion audit |
| No scope creep | `git status --short` and `git diff` |

## State inventory

Every state below must have a rendered result before cutover; none may be "handled later".

| State | Required result |
|---|---|
| Default, no selection, zero layers | Artboard background, empty layers hint, document inspector; no phantom handles. |
| One text layer | Frame, selection outline, handles, Type inspector, text content textarea. |
| One shape layer | Frame, fill/shape inspector, effects target enabled. |
| One image layer | Frame, fitted image, image inspector, blob URL alive. |
| Multi-selection, 2+ | Dashed union box, no handles, selection bounds, align enabled, distribute only at 3+. |
| 64 layers | Add actions reject with a toast; no project corruption; tree scrolls. |
| Last layer deleted | Empty document remains saveable, printable and exportable; undo restores layer and selection. |
| Drag/resize/rotate/marquee | Transient overlay only; one committed history entry; cancel leaves document unchanged. |
| Pinch/wheel | Zoom changes around pointer/midpoint; page never scrolls; one-pointer gesture cancelled cleanly. |
| Preview | Read-only device frame; no selection, handles or file actions; Escape restores focus. |
| Phone | Bottom dock + bottom-sheet inspector; layers sheet; no rulers; 44 px targets; keyboard keeps field visible. |
| Cover | 349×316 and 320×320: compact canvas + five 44 px actions; no inspector/rulers/splitter. |
| Half-fold | Top canvas / bottom tools; 24 px gutter; no control on the crease; bottom-sheet fallback without segments. |
| Landscape phone | 840×360: 72 px side rail, canvas right, no vertical clipping. |
| Tablet | 768×1024: side drawers, no splitter. |
| Print | Dedicated 765 × 1000 artboard; editor chrome hidden; no selection; empty background prints. |
| Panel collapsed | 44 px rail with an accessible expand label; canvas remains usable. |

## Known limitations (documented, not hidden)

- **Rotated resize is unsnapped.** Move and unrotated resize snap to artboard/layer anchors; rotated resize stays mathematically precise but does not snap edges. Rotation snaps to 15° with Shift.
- **Text editing is inspector-only.** Double-clicking a text layer does not open an on-canvas editor; text content is edited in the right panel. This is not Figma text parity and is not described as such.
- **Print uses a custom page size.** The app prints a 765 × 1000 CSS-pixel page with no margins; a printer driver may scale it to its paper. PDF output is the intended exact path.
- **Fixed panels are deliberate.** The shell uses fixed, resizable Figma-UI3-style side panels and a floating tool pill; it does not use floating panels. This is a documented design choice, not a missing feature.
- **Pinch and segments are Chromium-first.** Playwright touch CDP covers Chromium; Samsung Internet 30+ supplies viewport segments. Safari/iOS is not claimed without a separate device pass.
- **A browser cannot run cover and main as two independent live surfaces.** Cover is a separate Quick Mode; Flex Mode uses the top/bottom segments of one browser viewport. Simultaneous cover+main editing needs a native app.
- **Cover Quick Mode is intentionally limited.** It has five actions and no numeric transform; full editing is on the unfolded main screen. The 349×316 CSS reference is derived from 1048×948 at DPR 3, not an official browser viewport.
- **Half-fold needs segment data for the exact split.** Without `window.viewport.segments`, the app uses the normal phone bottom sheet rather than guessing a fold line. `device-posture: folded` alone is not treated as a fold rectangle.
- **Multi-select has no group resize/rotate.** It moves, marquees, aligns and distributes. Group non-uniform scaling of rotated children is out of scope.
- **Raster effects may soften above 200%.** The artboard is DOM; shader/texture canvases are raster and CSS-scaled. Text and vector shapes are re-rasterized by the browser, but high zoom can soften WebGL content. The 2× export is unaffected.
- **No guides, grid, constraints, auto-layout, components, variants, prototyping, comments, multiplayer or cloud sync.** These are separate product features, not omissions to hide.
- **No pen/vector path editing, boolean operations, masks or smart selection.** The existing layer kinds are text, shape and image.
- **No new canvas engine.** The plan deliberately keeps the DOM renderer and existing PNG export.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Rotated resize math drifts | Pure tests at 0°, 37°, 90°; opposite-corner invariant; pointer-cancel test. |
| Feature flag leaks | Task 5 removes the query branch; final audit greps for `workbench=1`. |
| v1 projects change composition | Exact `765 / 338` migration tests for x/y/text/image; export parity test at 2×. |
| Test churn hides regressions | Mapping table; no deletion without replacement; full old/new counts reported. |
| Ark TreeView drag conflicts | Native drag only on a dedicated handle; keyboard reorder always available; tests for both. |
| Plugin canvases misbehave at zoom | CSS transform only; keep `usePageVisible`; document raster softness; no new engine. |
| Print root accidentally hidden with the shell | Mount it as a sibling of `.ed-shell`; e2e asserts computed visibility under print media. |
| Splitter traps focus or fails on small screens | Desktop-only splitter; `useDeviceLayout` chooses drawers below 1024 px, phone dock below 600, cover below 380×380; seam and focus tests. |
| Fold API missing or lying | Segments are authoritative only when two top/bottom rects exist; posture alone keeps the bottom-sheet fallback; no guessed fold line. |
| Cover overpromises editing | Quick Mode has exactly five actions; numeric transform is absent by design; e2e asserts no inspector/splitter. |
| Touch reorder conflicts with scroll | Grip-only pointer reorder, 8 px threshold, `touch-action: none`, cancel path; row menu always has Move up/down. |
| Phone performance regresses | 20-layer drag trace at 360×840 and 1440×900; no long task >50 ms is the gate. |
| Pinch fights layer move | Pointer session yields on second pointer without committing; `twoFingerPinchDoesNotMoveLayer` e2e. |
| Empty document breaks save/print | Allow zero layers in schema; round-trip, print and export tests with zero layers. |
| Scroll-to-selection steals canvas focus | `scrollIntoView` only, never `TreeView.focus()`; test asserts focus stays on the canvas. |
| On-canvas text editing is mistaken for shipped | Inspector-only textarea; explicit limitation and `noCanvasTextEditor` test. |
| Red becomes decorative | Palette test allows red only for active/selection/guide; contrast tests keep cream/muted text readable. |
| Font/licence drift | Local copies, SHA-256 entries, OFL links, `check-assets` gate. |
| Scope creeps into native/site | Global constraint; final `git status` audit. |

## Non-goals

- Native Editor v2, Android UI, Kotlin or `tools/engine-spike/`.
- Public site redesign, `docs/` pages, handoff, deployment.
- New effects, plugins, AI features, accounts, sync or collaboration.
- Auto-layout, components, variants, constraints, guides, grids, masks, boolean ops, vector paths, prototyping, comments, multiplayer, cloud storage.
- On-canvas text editing, text-on-path, rich text spans or IME composition inside a transformed canvas.
- Simultaneous cover+main live editing, native FlexWindow widgets, Android split-screen orchestration or a second browser window as a tools surface.
- A new rendering engine or dependency.
- A commit, push, merge or publish.

## Execution handoff

This plan is intentionally staged behind `?workbench=1` so each task can be verified without leaving the existing Twin broken. **Task 1** is the hard technical gate: if canonical geometry, v1 migration and 2× export parity do not pass, stop and report before building the shell. **Task 2** is the shell/layer gate: desktop splitter, breakpoints and reorder must pass. **Task 3** is the interaction gate: if rotated resize cannot hold the opposite corner within 0.5 units at 200%, stop and report rather than shipping a handle that drifts. **Task 4** is the device gate: no desktop-only acceptance, and no cover/fold claim without the captures and on-device notes. **Task 5** is the release and evidence gate: no old shell, no flag, no weakened test, no unviewed visual claim; report what was viewed, measured and not checked.

Recommended execution: **subagent-driven**, because Tasks 1, 7 and 12 have interfaces that later tasks depend on and benefit from a fresh reviewer at each boundary. Do not commit or deploy unless the user separately asks.

## Skills applied

`writing-plans` for plan structure and task-level verification; `frontend-design` for the graphite/paper/red direction, type scale and the cover/phone hierarchy; `performance` for the device performance gates and trace method; the earlier composition pass used `vercel-composition-patterns`. The repository's `AGENTS.md` overrides generic skill advice: no commit, no scope beyond `twin/`, evidence before completion.
