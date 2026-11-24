# BMO Editor

A local, browser-only research workbench for the BMO Open Studio effects shelf. The editor has a graphite shell, a paper 765 × 1000 document, desktop panels with a splitter, tablet drawers, a phone bottom dock and sheet, a five-action cover Quick Mode, and a half-fold layout driven by viewport segments.

This app is deliberately isolated in `editor/`. It does not touch the native engine, `handoff/`, the published `docs/` shelf, or any site files.

## Run

```bash
cd editor
npm install
npm run dev
```

Vite listens on port `3000`. To use a cloud preview, pass the exact hostname:

```bash
VITE_ALLOWED_HOST=<preview-host> npm run dev -- --host 0.0.0.0 --port 3000
```

## Checks

```bash
npm test          # Vitest + jsdom unit and smoke tests
npm run build     # TypeScript project build + production Vite build
npm run check:assets
npm run test:e2e       # Playwright regression specs; run npx playwright install chromium first
npm run test:e2e:perf  # Production-build drag budget, isolated to one worker
npm run perf:open      # The project-open frame case only, against dist/ (see below)
```

`npm run perf:open` is the loop for frame work. It rebuilds `dist/` only when a build input is newer, serves that bundle on a free port and runs just `twentyLayerProjectOpensWithinOneFrameAtFourTimesCpu`; it prints the p95, max and long-task count and leaves the JSON in `test-results/editor-perf.json`. Pass `--build` to force a rebuild or `--no-build` to refuse a stale one. The full `test:e2e:perf` command still builds from scratch and remains the check for every drag gate.

`scripts/check-assets.mjs` verifies that every runtime local asset has a source, licence and SHA-256 in `asset-manifest.json`; that the lockfile is pinned; that CSS and document references are local; and that the built HTML does not load remote code.

## The document

- The model is 765 × 1000 CSS pixels, origin at the artboard centre. X and Y in the inspector are document coordinates, not the old 338-unit stage coordinates.
- PNG export renders at 1530 × 2000 (2×) with a valid PNG signature, the paper ground, effects, grain and print metadata. It contains no selection, handles, tags or editor chrome, and does not add a history entry.
- Project files are `.add` JSON schema v2 (older `.twin` files still open), with separate `scaleX` and `scaleY`. Schema v1 files are migrated on open by the exact `765 / 338` factor for positions and layer text size; `scale` becomes both axes.
- Saving includes `selectedLayerIds`, so a round-trip keeps a still-valid selection. Empty documents are valid, saveable, printable and exportable; they show the empty-layers hint instead of invented layers.
- The layer cap is 64 layers, with at most 12 image layers. Add actions reject further layers with a toast and leave the document intact.
- Undo/redo is one document history capped at 30 edits. Selection, panel state and viewport are outside history. A still-valid selection survives undo/redo.

## Editing

- Move, hand and zoom tools: `V`, `H`, `Z`. Hold `Space` to pan temporarily with any tool.
- Select by clicking; `Shift` adds, `Cmd/Ctrl` toggles, and dragging empty canvas draws a marquee. Layers panel rows follow the same modifiers.
- Drag a selection to move it. Drag the eight handles to resize; `Shift` preserves aspect and `Alt` resizes from the centre. The rotation handle snaps to 15° with `Shift`.
- Arrow keys nudge 1 unit; `Shift` + arrows nudge 10. `Delete` or `Backspace` removes the selection. `Cmd/Ctrl+[` and `]` move a single selected layer down or up.
- `Cmd/Ctrl+A` selects all, `Cmd/Ctrl+Z` undoes, `Cmd/Ctrl+Shift+Z` or `Ctrl+Y` redoes, and `Cmd/Ctrl+K` opens the action palette. `Escape` closes the palette, then Preview, then a drawer.
- Snapping uses artboard and other-layer anchors at 6 screen pixels (10 on coarse pointers), divided by zoom. Hold `Cmd/Ctrl` while dragging to bypass it. Move and unrotated resize snap; rotated resize is precise but unsnapped.
- Inspector Transform has nine reference points for X, Y, W and H, plus rotation and an optional aspect lock. Changing a value preserves the chosen reference point.
- Text is edited in the inspector only. Double-clicking a text layer does not open an on-canvas editor.
- Multi-selection shows a dashed union box without handles. It moves, marquees, aligns and distributes; group resize and group rotate are not included.

## Viewport and devices

- Mouse wheel pans; `Cmd/Ctrl` + wheel zooms around the pointer. Two-finger touch pinch zooms around the midpoint. The page does not scroll, zoom stays clamped, and a second pointer cancels a layer gesture rather than committing it.
- Desktop (≥ 1024 px) uses fixed layers and inspector panels with a draggable 1 px splitter. The seam supports keyboard resizing, and either panel collapses to a 44 px rail.
- Tablet (600–1023 px) uses side drawers. Phone (< 600 px) uses a bottom dock and full-height bottom sheets; the focused field is kept above the visual keyboard. Landscape phones shorter than 500 px use the side tool rail.
- Cover Quick Mode is used at ≤ 380 × 380 px, including the 349 × 316 and 320 × 320 references. It has exactly five 44 px actions and no inspector, rulers or splitter. It intentionally has no numeric transform.
- Half-fold reads `window.viewport.segments` as two top/bottom rectangles. The canvas stays above the 24 px gutter and tools below it; no control sits on the crease. Posture alone does not invent a fold line: without segments the normal phone bottom sheet is used.
- Preview opens a read-only 411 or 840 device frame. It has no selection, handles or file actions; `Escape` restores the editor and focus.
- Native lens dims and badges the catalogue. It never removes or disables a plugin.
- The editor chrome follows the approved palette; red is reserved for active state, selection and guides. Decorative transitions respect `prefers-reduced-motion`.

## Files, print and boundaries

- **Save .add** downloads the portable project. **Open .add** (or an older `.twin`) validates format, colours, fonts, effects, transforms and embedded image data before replacing the document and resetting history.
- Imported PNGs are embedded as data URLs in the project file. Layer images live in memory for the session; there is no autosave or local storage.
- **Export PNG** is a flattened raster only; it does not write PSDs or editable layer files.
- **Print / Save PDF** calls the browser print dialog. A dedicated print root renders a 765 × 1000 artboard with a custom page size and no editor chrome or selection. A printer driver may still scale the custom page; PDF output is the exact path.
- Print, export and save read committed document state, so a live pointer transform cannot leak into them.
- PNG is the supported layer import. PSD parsing and SVG layer import are outside the new editor.
- There is no guides/grid, constraints, auto-layout, components, variants, prototyping, comments, multiplayer or cloud sync. There is no pen/vector path editing, boolean operations or masks.
- Raster effects can soften above 200% zoom because shader/texture canvases are CSS-scaled; text and vector shapes are re-rasterized by the browser. The 2× export is unaffected.
- A browser cannot run cover and main as two independent live surfaces. Cover is a separate Quick Mode, and Flex Mode uses the two segments of one browser viewport.
- Pinch and segment support are Chromium-first. Samsung Internet 30+ supplies viewport segments; Safari/iOS is not claimed.
- The app has not been checked on a physical Samsung Flip 7 in this environment. See `artifacts/editor/flip7-device-notes.md` for the device matrix that remains not checked.

## Verification

The current local release gate is recorded in `docs/superpowers/plans/2026-11-16-twin-workbench-v2.md` under Task 5. It covers the focused and full unit suites, the full Playwright suite, the production build, and the offline asset check. Device and visual evidence lives in `artifacts/editor/`.

## Third-party material

Ark UI supplies the accessible menus, tabs, number inputs, slider, switch, select and color picker. Paper Shaders, tsParticles, R3F/Drei, Motion, Rough.js, Feral Fur, Vanilla Tilt and Lucide are pinned runtime dependencies.

See [THIRD_PARTY.md](THIRD_PARTY.md) and [asset-manifest.json](asset-manifest.json) for sources, licences and hashes.
