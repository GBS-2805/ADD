# BMO Twin

A local, browser-only research twin for the BMO Open Studio effects shelf. It is a printmaker’s workbench: an ADD-styled phone/tablet stage, a searchable tray of eight plugin families, direct PNG layers, and a Native lens that labels what is native-ready and what still needs native work.

This app is deliberately isolated in `twin/`. It does not touch the native engine, `handoff/`, the published `docs/` shelf, or any site files.

## Run

```bash
cd twin
npm install
npm run dev
```

The Vite dev server runs on port `3000`. For a cloud preview, pass the exact preview hostname:

```bash
VITE_ALLOWED_HOST=<preview-host> npm run dev -- --host 0.0.0.0 --port 3000
```

## Checks

```bash
npm test          # Vitest + jsdom unit and smoke tests
npm run build     # TypeScript project build + production Vite build
npm run check:assets
npm run test:e2e  # Playwright; run npx playwright install chromium first
```

`scripts/check-assets.mjs` verifies that every runtime local asset has a source, licence and SHA-256 in `asset-manifest.json`; that the lockfile is pinned; that CSS and document references are local; and that the built HTML does not load remote code.

## What is real

- Eight plugin panels, sixteen CSS blend modes, direct PNG layers, transforms, opacity, visibility, ordering and removal.
- An Ark UI Add menu creates text or star, orbit and block shapes, and routes PNG through the validated Overlay Lab importer. The side Inspector edits a selected layer's name, position, scale, rotation, opacity, visibility, blend, fill, text content, text size and font. X and Y have ±5 nudge buttons alongside the one-step controls.
- Drag any artboard layer directly with a mouse or touch. Layer effects render over the object at full surface size, so they remain visible on text, shapes and imported PNGs and stay attached while dragging.
- A **Present the stage** Actions command opens a read-only, focus-managed artboard view. The editor behind it is inert; `Escape` or the close control returns focus to the invoking command.
- **Save project file** downloads a portable `.twin` JSON document with all layers, effects, transforms and embedded PNG data. **Open project file** validates and replaces the current document; imported project IDs, colours, fonts, effects and image data are checked before they reach the stage. **Print / Save PDF** uses the browser print dialog and an artboard-only print stylesheet.
- **Export PNG** downloads a real flattened raster of the artboard. It renders the paper ground and effects, grain and print metadata, then visible layers in document order with their transforms, fill, opacity and blend mode. The canonical export is 1530 × 2000 (2× the 765 × 1000 artboard canvas, 0.765 ratio). Selection outlines, tags and editor chrome are not included. The command does not add a history entry; its result is exposed through the `png-export-result` status.
- Every document edit participates in one undo history: effects, layer add/remove, rename, text, font, fill, blend, opacity, transform, visibility and order. `Cmd/Ctrl+Z` undoes, `Cmd/Ctrl+Shift+Z` or `Ctrl+Y` redoes; the last 30 edits are kept. Selection, panel and view changes are not edits. A still-valid layer selection survives undo/redo.
- Paper Shaders, tsParticles, R3F/Drei, Motion, Rough.js, Feral Fur, Vanilla Tilt and Lucide are real pinned dependencies.
- Fonts are copied locally. The paper grain and pattern grounds are generated in CSS. No runtime request leaves the app for a shader, font, image, CDN or API.
- The artboard is a simulated web document. Tone and Prism mirror native labels but do not claim native pixels.

## Monochrome boundary

The twin chrome is ink-proof: neutral paper and ink tokens, hairline rules, grain and typography carry the hierarchy instead of hue. Generated shaders, patterns, particles, textures, 3D material and catalogue previews all stay inside that neutral range.

There are two deliberate colour exceptions: imported PNG layers on the document artboard, and the Native Parity tone/Prism reference swatches. Texture and Asset Shelf previews are grayscaled in CSS so the shelf reads as material rather than a colour picker. There is no grayscale filter on the Stage, on imported PNG layers, or on `.sample-image`.

## Boundaries

- PNG only for layers. PSDs must be exported to PNG first; this app has no PSD parser.
- Imported material is kept in memory for the session. It is not persisted to local storage or a server. Saving is an explicit `.twin` download; there is no autosave.
- Opening a project replaces the current document and starts a fresh edit history. A `.twin` file embeds PNG layer data, so it can be larger than the source PNGs.
- Print / Save PDF opens the browser print dialog; the app does not generate a PDF file itself.
- Export PNG is a flattened artboard raster at 1530 × 2000. It does not write PSD files or export editable layers separately.
- SVG imports are parsed, stripped of scripts, event handlers and `foreignObject`, and rejected if they point outside the file.
- Undo/redo is document history, capped at 30 edits. It covers canvas edits only; selection, panel state and viewport layout are outside history. Undoing a layer removal keeps the PNG object URL available for the session.
- Native lens dims and badges the catalogue; it never removes or disables a plugin.
- The app is not a shipping editor and has not been checked on a physical BMO device.

## Layout

- Compact: 411 × 891 device frame; the header, Stage and optional panel are grid rows. The dock is a floating overlay inside the Stage, not a layout row, so it never shortens the canvas.
- Compact panel: the Inspector, Actions and Plugin shelf share one full-width bottom sheet below the Stage. It is capped at 40dvh, scrolls internally, and the floating dock sits in the Stage's safe area above the sheet rather than over its controls.
- Short landscape phones fill the viewport and place the Stage and panel side by side. The panel still scrolls internally, and the floating dock stays inside the Stage column so it clears both the panel and the Stage footline.
- Expanded: 840 × 1000 device frame with a full-width Stage; the 344px Inspector/Actions/Plugin drawer overlays its right side instead of shrinking the canvas, and the floating dock shifts left of the drawer.
- The panel has a close button and leaves no layout gap when hidden.
- The compact bottom sheet has a drag handle for pointer dismissal; the close button remains the keyboard-accessible route. The handle is hidden in the short-landscape side panel.
- Portrait phone widths fill the viewport and hide the desktop viewport switch while keeping the compact bottom-sheet composition.
- Motion uses short transform/opacity transitions for panel arrival, dock press, selection and effect feedback, catalogue stagger, tab states, menus and toast exit. All decorative feedback and shader motion respect `prefers-reduced-motion`.
- Dock targets are at least 48 px.

## Verification

The current preview is https://site-b5c3eb4162ed46148d22881fe0e65300.freebuff.page/ and serves `assets/index-CS873E7_.js` with `assets/index-DjlKWoCi.css`, byte-identical to local `dist`. Final gates: `npm run build`, `npm run check:assets`, 49 unit tests, 43 Playwright tests (38 Twin + 5 project/print), live audits 98/98 at 390 × 844 and 101/101 at 844 × 390, and zero page, console or app-origin external requests. The live PNG/save/open/print/history probe passes 9/9; the 76 current-build visual-review frames are recorded in `artifacts/twin/visual-review/capture-manifest.json`. Details are in `artifacts/twin/spec-conformance/REPORT.md`, `spec-conformance.json` and `live-followups.json`.

## Third-party material

Ark UI 5.40.0 supplies the accessible menus, tabs, number inputs, slider, switch, select and color picker. It is pinned exactly and recorded as MIT.

See [THIRD_PARTY.md](THIRD_PARTY.md) and [asset-manifest.json](asset-manifest.json) for sources, licences and hashes.
