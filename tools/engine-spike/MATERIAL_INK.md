# Material Ink

**Shipped in the native engine path, 11 Nov 2026.**

Material Ink is a seeded procedural-material brush. The reader arms it from the
Texture inspector, chooses one of the ten local families, and draws directly on the
artboard. Each gesture becomes a new **document stroke**, not a bitmap layer.

## Why this belongs here

The engine already has three ingredients that a generic texture editor does not:
the seeded procedural families, a Skia artboard shared by preview and export, and a
document that stores recipes rather than pixels. Material Ink joins them at the
gesture boundary:

- the brush is **authored by drawing**, not by importing a texture;
- the mark is **resolution-independent**, so the same document opens at preview or
  2× export;
- the stroke is **editable history**, not a destructive flatten;
- the family and seed are part of the document, so the same recipe rebuilds the
  same material without a downloaded asset.

## Data model

`TextureInk` is a top-level document array:

```json
{
  "id": "ink-…",
  "width": 0.075,
  "recipe": {
    "family": "marble",
    "seed": 120587,
    "opacity": 0.72,
    "scale": 1.0,
    "blend": "Multiply"
  },
  "points": [{ "x": 0.12, "y": 0.31 }, …]
}
```

`points` are normalized artboard coordinates. `width` is a fraction of the artboard
short side. Schema **v5** adds `ink`; v1–v4 documents open with an empty stroke list.
A malformed stroke is dropped without taking the page down.

## Rendering

`TextureInkRenderer` asks `TextureRenderer` for a cached 256 px field, repeats it as
a `BitmapShader` along a stroked `Path`, and draws it after the artboard texture and
before the safe-area/footer layer. The tile is shared by all strokes with the same
recipe; no per-frame full-field generation is added.

The renderer is deterministic because the family, seed, scale, width and normalized
points fully describe the mark. No timestamps, device dimensions or random values
enter the render.

## Interaction and UI

- The **Paint Ink** button lives in the Texture sheet.
- Arming it closes the sheet and opens a compact **Material Ink HUD** showing the
  material, stroke count, Undo, Remove last, Clear and brush width.
- With the brush armed, a drag on the stage writes a stroke; layer transforms and
  mask painting are suspended. Back/Done settles back to Select.
- The object bar now exposes **INK** directly and uses compact glyph buttons so the
  selected layer name is no longer reduced to `GRAD…`.
- The Style sheet puts layer-specific controls (Type, Vector, Photo, 3D) before the
  mask callout, so opening a text layer no longer hides the Type controls below a
  generic mask banner.

## Verification

```bash
bash tools/engine-spike/verify-suite.sh
bash tools/engine-spike/verify-chrome.sh
```

`TextureInkTest` covers save/load round-trip, undo/redo, deterministic pixels,
different-seed divergence and the measured median ink-render time. The review
recording also exposed the object-bar truncation, missing mode HUD and the
mask-before-Type ordering; those are fixed in the same change.
