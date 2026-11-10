# ADD — compiled plan

10 November 2026. Engine plan plus the first implemented Studio shell slice; no site code, app theme, release, commit or production deploy is changed by this document.

## 1. Thesis

ADD is an archive shelf and a workshop. The archive holds the material — icons, fonts, paper, references, finished plates. The Studio is the main event.

The final goal is one editing powerhouse for a combined art experience: posters and general art, web design, UI and UX. One document, one artboard on the stage, two pages when a second surface is genuinely needed. Type, shapes, stickers, imagery, colour, composition, texture, grunge and general overlays, organic shapes and patterns, metallic finishes, brushes, grids, snapping, and GSAP-driven interface and prototype motion all belong to the same tool.

The basic bar is the Are.na type/lettering channel: heavy display type, stacked and outlined letterforms, distressed print, paper grounds, stamps and registration marks. The end goal is the Photoshop–Illustrator–Figma–Canvas combination, not a poster preset generator.

## 2. Decisions

These are the defaults for implementation. Any of them can be changed before Phase 0; nothing later should silently reverse them.

1. **Studio is the canonical engine.** The WebView Studio becomes the product surface. The native `tools/engine-spike` is frozen as the 3D and performance lab; it is not shipped as a second editor.
2. **Native shell, web engine.** Android remains the shell for launch, file I/O, photo picking, fonts, export, sharing, haptics and offline assets. The document, tools, canvas and interface live in the Studio WebView.
3. **Canvas2D plus WebGL.** Canvas2D keeps text and vectors crisp; WebGL handles raster, brushes, textures, metallic finishes, blending and compositing. Fabric is the starting interaction layer, not the final renderer.
4. **GSAP is for the interface first, artwork motion second.** The editor’s chrome and transitions use GSAP now. A per-layer timeline, prototype playback and animated export come after the static poster is excellent.
5. **Two pages, one artboard at a time.** The document model supports a `pages` array. The UI exposes two pages. v1 creates exactly one artboard per page; the stage shows one artboard, never an infinite canvas.
6. **Local-first and offline.** No account, no network, no cloud dependency. Project files and assets live on the device and can be exported.
7. **The archive is newsprint and ink.** One material system; no palette chips and no light/dark switch. Newsprint cream, black ink, one stamp red, halftone grey; graphite for the Studio. The full identity is in `BRAND.md`. This is a site track, parallel to the engine.
8. **Studio-first launch.** The app opens into the Studio, with the archive one tap away. QUICK remains the simple photo-and-type editor.
9. **No commit, deploy or release until a review verdict.** Every visual phase is shown as a working build with public captures and the handoff page before it ships.

## 3. Where the project stands

### Studio (the starting point)

- Fabric.js 5.3.0, GSAP 3.12.5 with Draggable, Observer and MotionPath, bundled offline.
- A fixed 540×675 artboard fitted into the stage; one document, no pages.
- Text and image objects only. Text has gradient, outline, shadow and blend; images import through the native bridge.
- Eight procedural overlays, opacity/blend/placement, motion path, groups, PNG export to `Pictures/Studio`.
- A graphite canvas-first shell now replaces the permanent header and the 46vh panel: floating chrome, a five-tool pill and a peek inspector. All existing Fabric tools remain.
- No shapes, pen, booleans, pages, artboards, grid controls, masks, paint brushes, adjustments, undo or document save yet.

### Studio 2 — first shell slice (implemented)

- Studio is the default editor destination; the native band and dock recede while the WebView is open.
- Graphite press shell, local Archivo Black / Plex Mono / Poppins, artboard margin, red selection and edition marks.
- Tool pill: Move, Type, Style, Texture, Motion. Every existing control is in the peek inspector; group, duplicate, delete, gradient, outline, shadow, blend, overlays, curves and export were not removed.
- Five press-material overlays — newsprint, wheatpaste, overexposed ink, halftone, rough canvas — now sit beside the eight atmosphere overlays.
- Still Fabric+; pages, document persistence, undo/autosave, shapes/pen/brush, grids, constraints and the two-page model remain the next phases.

### Native engine (the lab)

- Kotlin/Compose/Filament, roughly 12.7k lines, with a canvas-first chrome that already follows the right law.
- Image, text, vector, icon, sticker and 3D layers; grade, blur, grain, halftone and duotone effects; blend modes; gradients and patterns; snapping; diagnostics; document codec; export.
- No GSAP, no pages, no paint brush (only a mask brush), no web/UI frames, no component system.
- Its canvas-first rules, effects vocabulary, snapping model and diagnostics are specifications for the Studio. Its 3D layer can return later as a special native-rendered layer, not as a second editor.

### Site

- Sixteen pages with four palettes and a light/dark switch.
- The newsprint/ink direction and palette removal are specified in `BRAND.md`; they now follow the engine rather than define the product.

## 4. Architecture

```
┌─────────────────────────────────────────────────────┐
│ Native Android shell                                │
│ launch · files · photo picker · fonts · export ·    │
│ share · haptics · offline assets                    │
└──────────────────────────┬──────────────────────────┘
                           │ bridge
┌──────────────────────────▼──────────────────────────┐
│ Studio WebView                                      │
│ document · commands · tools · canvas · UI · GSAP    │
│                                                     │
│  ┌──────────────┐   ┌───────────────────────────┐   │
│  │ Canvas2D     │   │ WebGL compositor          │   │
│  │ text/vector  │◄──│ raster · brush · texture  │   │
│  └──────────────┘   │ metallic · blend · effect │   │
│                     └───────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

### 4.1 Renderer

- **Canvas2D:** text, vector paths, selection outlines, guides, grid. Resolution-independent and crisp at every zoom.
- **WebGL:** image layers, brush strokes, procedural textures, grain, halftone, duotone, paper, metallic and lighting, composited into one output.
- **Fabric:** object model, selection, transform gestures, text boxes, groups and event routing during the transition. Hot paths (brush, effects, many layers) bypass Fabric’s per-object rendering and draw into the composited surface.
- **Migration trigger:** if Fabric’s object count, filter cost or event latency blocks the performance budget, replace its renderer with the scene graph while keeping the document and tools. Do not migrate for tidiness.
- **Hit-testing:** the document owns bounds and paths; the renderer answers point queries. Selection never depends on Fabric’s internal object list once the custom scene graph arrives.

### 4.2 Native bridge

- `openProject()`, `saveProject()`, `exportPng()`, `pickPhoto()`, `listFonts()`, `loadFont()`, `share()`, `haptic()`.
- Project save/open uses a native zip writer/reader so large assets are not pushed through a single base64 string. The bridge streams asset chunks and reports progress.
- No network permission is added for the Studio. All icons, fonts and textures are bundled or in the document.

### 4.3 Performance budget

Measured on the target Android phone in the review build, not inferred:

| Metric | Target |
|---|---|
| Pan/zoom, 1080×1350 artboard | 60 fps |
| Brush input-to-paint | under 35 ms |
| 30 vector/text layers | 60 fps selection and drag |
| 4K photo preview | downscaled preview, no jank; original retained for export |
| GSAP interface transition | 60 fps, 120–240 ms |
| Memory with 30 layers and one 4K image | under 350 MB |
| Cold open of a two-page document | under 2 s |

Phase 0 gates the architecture on these numbers. If WebGL inside the WebView cannot hold them, the fallback is a native compositor behind the same document and UI — not a second editor.

## 5. Document model

### 5.1 Project

```json
{
  "schema": "add.studio",
  "schemaVersion": 2,
  "id": "doc_01H...",
  "name": "Untitled",
  "createdAt": "2026-11-10T00:00:00Z",
  "updatedAt": "2026-11-10T00:00:00Z",
  "settings": {
    "units": "px",
    "color": "sRGB",
    "snap": { "enabled": true, "grid": true, "guides": true, "objects": true,
              "artboard": true, "spacing": true, "baselines": true,
              "thresholdScreenPx": 8, "rotationStep": 15 }
  },
  "pages": [ ... ],
  "assets": { ... },
  "styles": { ... },
  "meta": { "app": "ADD", "engine": "studio-2", "preview": "preview.png" }
}
```

- **Units:** logical pixels. The artboard is resolution-independent; export scale is a setting.
- **Pages:** ordered array; the UI exposes two, the model does not cap it.
- **Assets:** content-addressed by SHA-256. One asset record can be referenced by many layers; deleting the last reference removes it on save.
- **Styles:** document-level colour, text and effect styles. A layer may override any property.
- **Meta:** app and engine version, preview path, optional author note. No user account, no sync ids.

### 5.2 Pages

```json
{
  "id": "pg_01H...",
  "name": "Poster",
  "artboards": [ ... ],
  "guides": [ { "id": "gd_1", "axis": "x", "at": 540, "locked": false } ],
  "lastArtboardId": "ab_01H..."
}
```

- A page is a workspace and a navigation unit. It does not render on the stage by itself.
- v1: exactly one artboard per page, enforced by the UI and validated on load.
- Two pages are the working limit: page 1 is the primary composition; page 2 is a variant, a UI kit, or a second surface. Switching pages preserves the viewport and selection per page.
- Guides live on the page so they can be shared by a future multi-artboard page; artboard-local guides are also allowed and win on conflict.
- Page navigation is explicit: tabs at the top-left, never a horizontal filmstrip that consumes the canvas.

### 5.3 Artboards

```json
{
  "id": "ab_01H...",
  "name": "Poster",
  "x": 0, "y": 0, "w": 1080, "h": 1350,
  "background": { "type": "paper", "stock": "newsprint", "color": "#E9E0CB" },
  "grid": { ... },
  "guides": [],
  "layers": [ ... ],
  "finish": [ { "type": "grain", "enabled": true, "params": { "amount": 0.18 } } ],
  "export": { "format": "png", "scale": 2 }
}
```

- The artboard is the export surface and the coordinate root. Its content box is inset by the grid margins; its bounds are the snap target for edges and centre.
- Presets: Poster 1080×1350, Square 1080×1080, Story 1080×1920, Phone UI 390×844, Custom. Presets set a grid and safe area; they do not lock the size.
- Background is a paint: solid, paper stock, or transparent. Paper stocks are procedural (fibre + tone), not a flat colour pretending to be paper.
- `finish` is the global, non-destructive pass: paper texture, overall grain, vignette, print marks, registration, stamps, deckle edge. It renders after all layers.
- Export settings belong to the artboard, so each page can export at its own size.

### 5.4 Layer base

Layers are stored bottom-first; the layers panel displays them top-first.

```json
{
  "id": "lyr_01H...",
  "type": "text | shape | image | brush | sticker | group | frame",
  "name": "HEADLINE",
  "visible": true,
  "locked": false,
  "opacity": 1,
  "blend": "normal",
  "transform": { "x": 72, "y": 96, "w": 936, "h": 220, "rotation": 0 },
  "mask": null,
  "effects": [],
  "constraints": null,
  "children": null
}
```

- `transform` is relative to the parent; the root parent is the artboard. Rotation is clockwise around the layer centre.
- Resizing a group scales its children; resizing a frame respects child constraints. This is the Figma-like distinction and must be tested.
- `mask` is either a raster mask asset (alpha) or a clip path. A mask is non-destructive and can be disabled.
- `effects` is an ordered stack; effects apply after the layer paints, before the mask and blend.
- `constraints` is meaningful for frame children; `null` means free positioning.
- No layer may reference itself or an ancestor. The tree is acyclic and validated on load.

### 5.5 Type layer

```json
{
  "type": "text",
  "text": "TAKE UP\nSPACE",
  "box": { "autoWidth": false, "autoHeight": true },
  "font": { "family": "Archivo Black", "style": "Regular", "weight": 400,
            "size": 118, "lineHeight": 0.92, "tracking": -0.03 },
  "align": "left",
  "verticalAlign": "top",
  "case": "upper",
  "fill": { "type": "solid", "color": "#15120E" },
  "stroke": null,
  "shadow": null,
  "warp": { "kind": "none", "amount": 0 },
  "path": null,
  "outline": { "enabled": false, "color": "#15120E", "width": 2 },
  "features": {}
}
```

- Point text and box text are the same type with `autoWidth`/`autoHeight` flags.
- Full controls: family, style, weight, size, leading, tracking, alignment, vertical alignment, case, colour or paint, stroke, shadow, outline, OpenType features.
- Lettering controls: `stack` (one word or line per row), `arc`, `arch`, `flag`, `rise`, `wave`, `bulge`; warp is non-destructive and can be reset.
- Text on path: a path reference, offset, side and alignment. Motion path is a different feature and must not be conflated with text on path.
- Text can be converted to outlines for export or vector editing; the source text remains until the conversion is accepted.
- Hyphenation and rich-text runs are deferred; v1 is one style per text layer.

### 5.6 Shape layer

```json
{
  "type": "shape",
  "geometry": {
    "kind": "path",
    "path": "M 0 0.5 C 0.1 0.1, 0.4 0, 0.5 0.5 ... Z",
    "radius": 0
  },
  "fill": { "type": "linear", "angle": 90, "stops": [
    { "at": 0, "color": "#15120E" }, { "at": 1, "color": "#DD4124" }
  ] },
  "stroke": { "color": "#15120E", "width": 4, "join": "miter", "cap": "butt", "dash": [] }
}
```

- Primitives: rectangle, ellipse, polygon, star, line, path, blob.
- Paths are stored in normalised layer coordinates (0–1), so resizing does not rewrite geometry.
- Pen tool edits anchors and control handles; boolean operations produce a path result while the source operands remain in history.
- Organic shapes: a seeded blob generator produces an editable path; it is not a fixed library of blobs.
- Fills: solid, linear and radial gradients, pattern (checks, dots, stripes, grid, halftone), and image fill later.
- Strokes: width, join, cap, dash, alignment (centre/inside/outside) where supported.
- Boolean groups (non-destructive) are Phase 4; v1 booleans are destructive with undo.

### 5.7 Image layer

```json
{
  "type": "image",
  "assetId": "sha256_...",
  "fit": "fill",
  "crop": { "x": 0.1, "y": 0.05, "w": 0.8, "h": 0.8 },
  "radius": 0,
  "adjustments": { "exposure": 0, "contrast": 0, "saturation": 0,
                   "temperature": 0, "tint": 0, "highlights": 0, "shadows": 0 },
  "filters": [],
  "mask": null
}
```

- Crop is normalised and non-destructive; the original asset is retained.
- Adjustments live on the layer and can be reset independently.
- Masks can be raster (brush) or vector (clip path).
- Fill crops to the frame; fit shows the whole image and leaves the background.
- Large images: preview is downscaled to a device-appropriate cap; export uses the original asset where memory allows, otherwise the highest safe resolution with a recorded warning.

### 5.8 Brush layer

```json
{
  "type": "brush",
  "strokes": [
    { "points": [ { "x": 0.12, "y": 0.4, "pressure": 0.7 } ],
      "brush": { "size": 0.04, "opacity": 0.9, "flow": 0.6,
                 "hardness": 0.5, "spacing": 0.08, "texture": null },
      "erase": false }
  ],
  "cacheAssetId": "sha256_...",
  "dirty": true
}
```

- Brush strokes are stored as input points plus brush settings; the raster cache is an optimisation, not the source of truth.
- Eraser is a stroke with `erase: true`, composited with destination-out.
- Brush texture, scatter, angle and pressure curves are Phase 3.
- A "flatten brush layer" command rasterises the strokes and drops the point history; it is explicit, never automatic.
- Stylus pressure uses PointerEvent pressure where the WebView exposes it; otherwise a native bridge pressure channel is the fallback. This is a Phase 0 measurement item.

### 5.9 Group and frame

- **Group:** transforms and opacity apply to children; no clipping. Resizing scales children.
- **Frame:** clips children, can carry a background, and is the UI/web layout primitive. Resizing respects child constraints.
- Frames are how a phone UI, a web section or a poster panel is composed inside the same artboard. A frame is not a separate page.
- Auto-layout, components and variants are Phase 4; v1 has frames with constraints only.

### 5.10 Effects and finish

Layer effects (non-destructive, ordered):

- `blur`, `shadow`, `glow`, `grain`, `halftone`, `duotone`, `emboss`, `metallic`, `roughen`, `outline`.

Artboard finish (global):

- `paper` stock, `grain`, `vignette`, `light`, `dust`, `cropMarks`, `registration`, `stamp`, `deckle`, `overprint`.

Rules:

- Effects are parameterised and disabled individually; none is baked until export.
- Metallic is a shader/texture combination, not a flat grey gradient: base tone, highlight, environment noise, roughness and a grain pass. It should read as a finish at 1× and 2×.
- Overprint and misregistration are deliberate print effects; they are not a substitute for blend modes.
- The renderer caches effect outputs per layer and invalidates only the changed layer.

### 5.11 Grids

```json
{
  "visible": true,
  "type": "columns",
  "columns": 12,
  "gutter": 24,
  "margin": 64,
  "rows": 0,
  "rowGutter": 24,
  "baseline": 8,
  "color": "rgba(23,19,14,0.12)",
  "snap": { "columns": true, "rows": false, "baseline": false }
}
```

- Types: columns, modular (columns + rows), baseline, isometric (later).
- The grid is relative to the artboard content box (inside margins), not the outer artboard.
- Presets: Poster (12 columns, 24 gutter, 64 margin), Editorial (6 columns, 32 gutter), UI (8-point baseline, 4 columns), Custom.
- Grids are display and snap targets only; they never export.
- Column snap targets are left edge, centre and right edge of each column, not every pixel of the gutter.
- Baseline snap targets are text baselines at the chosen interval.
- The grid has its own opacity and colour, and is hidden by default on a new artboard except when a preset turns it on.

### 5.12 Guides and rulers

- Guides: horizontal or vertical, stored per artboard (or page), with a colour and lock flag.
- Rulers are optional and hidden by default; they appear on intent, preserving canvas space.
- Dragging from a ruler creates a guide; dragging a guide off the artboard removes it.
- "Make guides from selection" creates guides at the selection edges and centre.
- Guides are snap targets and can be locked so they cannot be moved accidentally.
- Safe-area and bleed guides are presets of the artboard, not separate systems.

### 5.13 Snapping engine

Snapping is a pure function over the document, the selection and the pointer, so it can be unit-tested without a renderer.

**Inputs**

- Moving bounds: left, right, top, bottom, centre x, centre y.
- Static targets: artboard edges and centre; page guides; grid lines; sibling layer edges/centres; text baselines; frame edges; safe-area lines.
- Current zoom; snap settings; modifier state.

**Modes**

- `off`, `grid`, `guides`, `objects`, `artboard`, `all`; each axis is evaluated independently.
- Grid and baseline snapping have their own toggles; `all` respects those toggles.
- A touch-friendly "Snap" toggle sits in the tool pill; holding the toggle temporarily disables snapping without leaving the canvas gesture.

**Algorithm**

1. Convert the screen threshold (8 px) to artboard units: `threshold / zoom`.
2. Collect candidate targets near the pointer from an R-tree of static bounds; grid lines and guides come from their own ordered lists.
3. For each axis, compute the delta from each moving edge/centre to each candidate.
4. Keep candidates within the threshold; choose the smallest absolute delta.
5. Tie-break in this order: guide, object, artboard, grid. This keeps a deliberate guide stronger than a dense grid.
6. Apply a hysteresis margin of 2 screen px: once snapped, stay snapped until the pointer leaves the threshold plus the margin.
7. Emit `SnapResult` with the adjusted transform, the matched targets, the guide lines to draw, and distance badges.
8. Apply the transform once; the renderer draws the snap lines, not the document.

**Behaviours**

- Moving: snap x and y independently; never rotate the object to snap.
- Resizing: snap the active edge/corner; keep the opposite edge fixed; Shift preserves aspect; Alt resizes from centre where supported.
- Rotating: snap to 0/90/180/270 within 2°, and to 15° increments while Shift is held. Rotation does not use spatial guides.
- Multi-selection: snap the combined bounds; equal-spacing guides compare gaps between selected and nearby objects.
- Distribution: three or more objects show equal-gap badges; releasing applies the distribution only when the guide is active.
- Locked layers are valid targets; hidden layers are not. The active selection never targets itself.
- Grid snap can be on while object snap is off, and vice versa.
- Snapping is disabled while typing, while the hand tool is active, and during a two-finger canvas gesture.
- At any zoom, the threshold stays 8 screen pixels; it does not grow with the artboard.

**Acceptance cases**

- Centre to artboard centre, both axes.
- Edge to a locked guide, with the guide winning over a nearby grid line.
- Text baseline to baseline across two text layers.
- Equal gaps across three objects, with badges and a clean release.
- Zoom 25%, 100% and 400%: the same screen-space threshold.
- Snap bypass while the toggle is held, then snap again on release.
- Resize a corner to a grid intersection without moving the opposite corner.
- Rotate to 15° steps, then to 90° exactly.
- Snap to a frame edge while the frame is clipped.
- No snap when the target is hidden.

### 5.14 Constraints

```json
{
  "horizontal": "left | right | leftRight | center | scale",
  "vertical": "top | bottom | topBottom | center | scale"
}
```

- Constraints apply to children of a frame when the frame is resized.
- `leftRight` and `topBottom` stretch; `scale` scales the child and its distance from the pinned edges.
- Min/max width and height are optional and clamp the result.
- A frame resize is a single undoable command, even when many children move.
- Components and auto-layout build on this later; v1 does not ship them.

### 5.15 Styles and assets

- **Colour styles:** id, name, value, optional Pantone/ink note. Layers may link or override.
- **Text styles:** id, name, font, size, leading, tracking, case, fill.
- **Effect styles:** reusable effect stacks.
- **Document assets:** images, textures, SVGs, fonts. Content-addressed; one asset shared by many layers.
- **Archive assets:** icons, fonts and textures from the shelf are copied into the document on use, so a project opens without the archive and does not change when the shelf does.
- **Licences:** each archive asset carries its source and licence; the document keeps the attribution string.

### 5.16 History, autosave and recovery

- Command-based undo/redo, not full snapshots. Every mutation is a command with a label: `move layer`, `set type`, `add grid`, `delete page`.
- Continuous gestures coalesce into one command per gesture; text edits coalesce on a short debounce.
- History limit: 100 commands per session. The document snapshot is autosaved; history itself is not persisted.
- Autosave: 800 ms debounce, plus a flush when the app goes to the background. Stored in IndexedDB; assets in OPFS with an IndexedDB fallback.
- On launch, recover the last document if it is newer than the last explicit save; offer "Recovered" and "Discard".
- A crash while saving leaves the previous good snapshot; writes are atomic (write temp, then rename).
- Dirty state is visible in the title chip and in the save sheet.

### 5.17 File format

`.add` is a zip:

```
manifest.json      schema, app version, created/modified, thumbnail
document.json      the full project
assets/<sha>.<ext> images, textures, SVGs
preview.png        1600px longest edge
```

- The native bridge writes and reads the zip; the Studio never needs filesystem access.
- A project can also live unpacked in app storage for autosave. Export/import always uses `.add`.
- Schema migrations are a chain: `v1 → v2 → ...`; unknown fields are preserved so a newer file does not lose data when opened in an older build.
- Asset paths are hashes, never user-supplied names. SVG files are sanitised on import.
- Caps: 50 MB per project for v1, 25 MB per image asset, 8 fonts embedded. A project that exceeds the cap is refused with a clear message, not silently truncated.
- Fonts are referenced by family/style and resolved from the device/pack; a project that needs a missing font shows a substitution notice and offers the pack.

### 5.18 Example document

```json
{
  "schema": "add.studio", "schemaVersion": 2, "id": "doc_demo", "name": "Keep",
  "settings": { "units": "px", "color": "sRGB",
    "snap": { "enabled": true, "grid": true, "guides": true, "objects": true,
              "artboard": true, "spacing": true, "baselines": true,
              "thresholdScreenPx": 8, "rotationStep": 15 } },
  "pages": [{
    "id": "pg_poster", "name": "Poster", "lastArtboardId": "ab_poster",
    "guides": [{ "id": "gd_safe", "axis": "x", "at": 64, "locked": true }],
    "artboards": [{
      "id": "ab_poster", "name": "Poster", "x": 0, "y": 0, "w": 1080, "h": 1350,
      "background": { "type": "paper", "stock": "newsprint", "color": "#E9E0CB" },
      "grid": { "visible": true, "type": "columns", "columns": 12, "gutter": 24,
                "margin": 64, "baseline": 8, "snap": { "columns": true } },
      "finish": [{ "type": "grain", "enabled": true, "params": { "amount": 0.18 } }],
      "layers": [{
        "id": "lyr_head", "type": "text", "name": "HEADLINE",
        "transform": { "x": 64, "y": 160, "w": 952, "h": 520, "rotation": 0 },
        "text": "TAKE UP\nSPACE", "font": { "family": "Archivo Black", "size": 118,
          "lineHeight": 0.92, "tracking": -0.03 },
        "fill": { "type": "solid", "color": "#15120E" }, "effects": []
      }]
    }]
  }],
  "assets": {}, "styles": {}
}
```

### 5.19 Invariants

- Exactly one artboard per page in v1; two pages maximum in the UI.
- All ids stable and unique across pages, layers, assets and styles.
- Layer tree acyclic; child transforms are relative to the parent.
- Array order is paint order; the layers panel reverses it for display.
- Artboard bounds are finite and positive; no NaN/Infinity in transforms.
- Every asset reference resolves; orphan assets are removed on save.
- Undo restores the exact previous visible state, including selection.
- Autosave never blocks a gesture; a failed save is reported and keeps the dirty state.
- Export is deterministic for the same document and scale.

## 6. Studio interface

### 6.1 Canvas-first laws

1. No permanent header, status line or side panel.
2. The artboard owns the screen at rest; chrome appears on intent and dismisses on a canvas touch.
3. Tools float in a translucent pill over the canvas; the inspector is a peek that grows into a sheet.
4. Gestures carry the frequent actions; buttons carry the rest.
5. Status speaks only when there is something to say: saved, exporting, error.
6. Fit on launch; the artboard fills the available stage.
7. Nothing added later may reintroduce a fixed band.

### 6.2 Layout

- **Top-left:** page chip (`Poster` / `UI`) and artboard name; tap for pages, presets and export.
- **Top-right:** undo, redo, export. These fade when idle.
- **Bottom:** tool pill — Move, Type, Shape, Pen, Brush, Image, Assets. Snap toggle and hand tool live at the edge.
- **Peek:** selected layer name and type; drag up for Style, Effects, Layers, Page.
- **Command bar:** a search field summoned from the pill; actions, fonts, icons, colours, grids and export.
- **Layers filmstrip:** horizontal cards, thumbnails, visibility and lock; drag to reorder; never a permanent column.
- **Page tabs:** two explicit tabs, not a hidden gesture. Switching pages is a GSAP crossfade of the stage, not a document reload.
- **Rulers:** hidden by default; a toggle reveals them and the canvas shrinks only while they are visible.

### 6.3 GSAP motion

- Pill inflate/deflate, sheet spring, layer card settle, page tab underline, command result stagger, selection tag tick.
- 120–240 ms, `power2.out`/`power3.out`; no bounce by default.
- Motion is UI only; it must never intercept canvas pointer events or delay a paint stroke.
- `prefers-reduced-motion` and the Android animation scale both disable non-essential motion.
- The motion path editor keeps GSAP MotionPath for curved layer animation; the brush and drag loops never run through GSAP.

### 6.4 Touch and keyboard

- Pinch to zoom; two-finger drag to pan; two-finger tap undo; three-finger tap redo.
- Long-press empty canvas opens Add; double-tap text enters editing.
- Drag selection handles to resize; rotate handle outside the corner; snapping feedback follows the snap model.
- Hardware keyboard: Space+drag pan, Cmd/Ctrl+Z undo, Cmd/Ctrl+Shift+Z redo, arrow keys nudge, Shift+arrow 10× nudge.
- All touch targets at least 44 px; the pill is reachable with one thumb.

## 7. Capability roadmap

| Phase | Name | Scope | Acceptance gate |
|---|---|---|---|
| **0** | Proof | WebGL compositor, brush stamp, 4K image, 30 layers, GSAP shell; measure on the target phone | Budget in §4.3 met; fallback decision recorded |
| **1** | Shell and document | Canvas-first chrome; two pages; one artboard per page; grids, guides, snapping, constraints; undo; autosave; save/open | Create two pages, snap to grid/guide/object/baseline, reload exactly; undo restores |
| **2** | Poster parity | Type panel, shapes/pen, stickers, photo import/crop/adjust, overlays, layer effects; export | Three reference plates reproduced in-app; PNG matches the stage |
| **3** | Print and texture | Paper stocks, ink/duotone, halftone, overprint, grain, grunge, metallic, brushes, masks | A textured metallic poster at 1×/2×; brush latency within budget |
| **4** | Layout and UI | Frames, constraints, styles, UI kits, component system, responsive artboards | A phone UI and a web section composed in the same document; frame resize behaves |
| **5** | Motion | Layer timeline, GSAP prototype playback, GIF/WebM/HTML export | A short animated poster exports and plays; reduced motion respected |
| **6** | Archive | Shelf assets drag-in, save to Collection, paper/ink site, palette removal | Site and Studio share tokens; assets round-trip; palette checks rewritten |

### Phase 0 detail

The proof is deliberately small and must be measured, not inferred:

- One artboard, one 4K photo, one text layer, one vector shape, one brush stroke, one grain/halftone finish.
- Pan and zoom at 60 fps; brush latency under 35 ms; GSAP chrome at 60 fps; memory under 350 MB.
- Test on the target Android phone. The current emulator runs under TCG and is a smoke test, not a performance measurement.
- If the WebView cannot hold the budget, use the native compositor fallback: native renders the raster/brush/effect stack; the Studio keeps the document and UI. Record the decision and the numbers in this plan before Phase 1.

### Phase 1 detail

- Document store and command stack.
- Two-page UI and page navigation.
- Artboard preset and grid model.
- Guides, rulers and the snapping engine with the acceptance cases in §5.13.
- Frame constraints (basic) so the UI artboard has a reason to exist in Phase 1.
- Autosave/recovery and native save/open.
- Canvas-first shell with the floating pill, peek and command bar. No permanent panel.
- GSAP shell motion and reduced-motion behaviour.

### Phase 2 detail

- Type: full panel, fonts, leading/tracking, case, outline, shadow, warp, text on path.
- Shapes: primitives, pen, path edit, booleans (destructive), patterns.
- Stickers/icons from the archive, SVG import and tint.
- Photo: import, crop, fit/fill, adjustments, masks.
- Effects: grain, halftone, duotone, blur, shadow, paper, grunge, light.
- Acceptance: three plates from the reference channel, chosen before the phase starts. They are the visual test, not a mood board.

### Phase 3 detail

- Paper stocks and deckle edges.
- Ink sets, overprint, misregistration, registration marks.
- Metallic, foil, emboss and lighting.
- Brush engine with size/opacity/flow/hardness/texture; eraser.
- Print marks and edition stamps.
- Acceptance: a poster that reads as printed at 1× and 2×, not a flat digital mock.

### Phase 4 detail

- Frames with constraints; nested frames.
- Text, colour and effect styles.
- Components and variants; UI kits from the archive.
- Responsive/phone and web artboards; export presets.
- Acceptance: a UI screen and a poster in one document, both exporting correctly.

### Phase 5 detail

- Per-layer timeline: position, scale, rotation, opacity, path, easing.
- GSAP playback on the canvas and in a prototype preview.
- GIF/WebM export; HTML prototype export is a stretch goal.
- The editor UI motion remains separate from artwork motion.

### Phase 6 detail

- Archive assets into the Studio with licence metadata.
- Save finished plates to a local Collection; optional publish to the site.
- Site: paper/ink system, no palettes, no theme switch, wall home and collection pages.
- Remove palette CSS/JS/app themes, rewrite checks, regenerate Figma/Pantone.
- This track must not ship a paper site that advertises an editor the app cannot yet open.

## 8. Archive and site

The locked identity is `BRAND.md`; this section records how the archive applies it.

- **Surface:** newsprint `#E9E0CB`, ink `#15120E`, stamp red `#DD4124` for marks, halftone `#8C8476`; graphite `#131513` is Studio only.
- **Law:** clean structure, dirty ink. Grunge lives in display type, paper and images; never in body copy or controls.
- **Paper:** procedural newsprint fibre and grain; no downloaded texture; one substrate per page.
- **Type:** Archivo Black for display, Redaction for editorial, Plex Mono for labels, Poppins for body.
- **Marks:** registration, crop marks, accession numbers, plate captions, stamps, edition numbers.
- **Home:** one dominant Studio entry and a wall of collection plates; the Archive is one tap away. “The Keep” is retired as a brand name.
- **Sections:** archive plates with plate numbers and metadata; existing icon/font/tool functionality preserved.
- **Palette removal:** site CSS tokens, `site.js` palette/theme UI, app `Palette`/`ThemePrefs`/`PaletteRow`, eight themes, palette checks, Pantone sheet, Figma artboards, copy mentions. Release notes keep history.
- **Renders:** visual changes follow the repo render/review rules; plan-only changes do not.

## 9. What is frozen, kept and retired

### Frozen

- `tools/engine-spike`: no new feature work except 3D/performance experiments that inform the Studio. Its plan remains the lab plan.
- Native editor QUICK: stays simple; no canvas, pages, brushes or components.
- The dual-engine idea: no second editor in the shipped app.

### Kept

- Studio native bridge, bundled GSAP/Fabric, eight overlays, offline fonts and icons, archive catalogs.
- Native engine’s canvas-first laws, snapping model, effect vocabulary, diagnostics and 3D layer concept.
- Existing site pages, catalogs and release tooling.

### Retired or replaced

- Studio’s permanent header and 46vh panel — removed in the first Studio 2 shell slice.
- Studio’s single artboard, 540×675 fixed document — still the current artboard until the document phase.
- Fabric as the final renderer (kept as the starting interaction layer).
- Site palettes, theme switch, palette chips and four-palette art direction.
- App palette themes and palette row, once the site track lands.

## 10. Verification

### Unit

- Document serialization/deserialization; schema migrations; unknown-field preservation.
- Layer tree invariants; transform composition; group scaling vs frame constraints.
- Snapping: every acceptance case in §5.13, at 25/100/400% zoom.
- Grid geometry: columns, margins, gutters, baseline.
- Constraints: resize frame, check each horizontal/vertical mode.
- Undo/redo: command coalescing, selection restore.
- Autosave: recovery, atomic write, failed-save path.

### Integration

- Two-page round trip through `.add`: JSON, assets, preview, fonts.
- Native bridge save/open/export on a device.
- Offline: no network requests from the Studio; bundled assets resolve.
- Export determinism: same document, same PNG bytes at the same scale (or a documented tolerance).

### Visual

- Three reference plates reproduced in Phase 2; golden captures at 1× and 2×.
- A UI screen in Phase 4; frame resize sequence captured.
- All site changes follow the render rules; no visual change ships without a public review link.

### Performance

- Phase 0 numbers recorded before Phase 1. No speed claim without a measurement.
- Re-measure after Phase 3 (brushes/effects) and Phase 5 (motion).

## 11. Risks

| Risk | Mitigation |
|---|---|
| WebView/WebGL cannot hold brush latency or 4K images | Phase 0 gate; native compositor fallback behind the same document |
| Fabric’s ceiling blocks effects or object count | Custom scene graph hot paths; migrate only when measured |
| Scope expands faster than phases | Each phase has an acceptance gate; no phase starts before the prior one passes |
| Two pages become an infinite-canvas project | v1 enforces one artboard per page and two pages in the UI |
| GSAP motion interferes with drawing | GSAP only on chrome and motion-path preview; pointer/paint loop is independent |
| Metallic finishes look like grey gradients | Shader + environment noise + roughness + grain; acceptance at 1×/2× |
| Stylus pressure unavailable in WebView | Native pressure bridge; fall back to velocity-based width with a visible setting |
| Large project files on a phone | Asset caps, content-addressed compression, native zip streaming, clear refusal |
| Archive and Studio drift | Shared paper/ink tokens; archive assets copied into the document with licence metadata |
| Site advertises an unshipped editor | Studio entry is labelled preview until Phase 2/3 gate is approved |

## 12. Defaults chosen (change before Phase 0 if needed)

- Newsprint paper, no night mode; graphite Studio.
- Studio opens first; archive one tap away.
- Two pages in the UI; one artboard per page in v1; model supports more pages.
- GSAP interface motion now; artwork timeline in Phase 5.
- Native engine frozen as the lab; no 3D in the Studio v1.
- Reference acceptance: three plates from the Are.na type/lettering channel, selected at the start of Phase 2.
- Project format `.add` zip; local-first, no account.
- No commit, deploy or release until a phase verdict.

## 13. First move

On approval, Phase 0 only:

1. Build the WebGL proof in the Studio: one photo, one type layer, one shape, one brush stroke, one grain/halftone finish.
2. Measure pan/zoom, brush latency, 30-layer drag, 4K preview, memory and GSAP chrome on the target phone.
3. Record the numbers here. If they pass, start Phase 1. If they fail, decide the native compositor fallback before writing the shell.

No engine feature code, site restyle, app theme change, APK, commit or deploy happens before that decision.
