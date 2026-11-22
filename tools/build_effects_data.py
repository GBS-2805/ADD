#!/usr/bin/env python3
"""Write `docs/data/effects.json` from the preset tables below.

    python3 tools/build_effects_data.py

The tables here are the *authored* copy: a preset is a name, a note and a
recipe, and nothing in it is a bitmap or a shader string. `tools/build_effects.py
--check` then holds the file to its shape, and a fixture compares the `native`
entries against the Kotlin tables so the two cannot fork.

Ids are `<kind>-<slug>` because a document stores the id: "paper" is both a
shader ground and a texture stock, and one document may hold either. The display
`name` is free to change; the id is not.
"""

from __future__ import annotations

import json
import math
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/data/effects.json"



def control(id, label, kind, **kw):
    out = {"id": id, "label": label, "kind": kind}
    out.update(kw)
    return out

def feature(id, name, note, **kw):
    out = {"id": id, "name": name, "note": note}
    out.update(kw)
    return out

SHADERS = [
    ("mesh", "Mesh gradient", "Soft ink field in motion", "MeshGradient"),
    ("grain", "Grain gradient", "Inky bands with paper grain", "GrainGradient"),
    ("warp", "Warp", "Striped ink under pressure", "Warp"),
    ("orbit", "Dot orbit", "A field of small satellites", "DotOrbit"),
    ("panels", "Ink panels", "A fan of printed sheets", "ColorPanels"),
    ("metal", "Liquid metal", "Chrome ink in a circular well", "LiquidMetal"),
    ("smoke", "Gem smoke", "A slow bloom of ink", "GemSmoke"),
    ("paper", "Paper texture", "The workbench itself", "PaperTexture"),
]
TEXTURES = [
    ("paper", "Paper 006", "Neutral stock", "PaperTexture"),
    ("creased", "Fluted glass", "Ribbed transparency", "FlutedGlass"),
    ("dither", "Image dither", "A printed photograph", "ImageDithering"),
    ("halftone", "Halftone dots", "Newsprint at close range", "HalftoneDots"),
    ("fur", "Fur", "A pettable silhouette", "Fur"),
]
PATTERNS = [
    ("jigsaw", "Jigsaw", "Interlocking arcs"),
    ("topography", "Topography", "Contour lines"),
    ("circuit", "Circuit", "A board trace"),
    ("diagonal", "Diagonal", "Cut paper stripes"),
    ("plus", "Plus", "A quiet cross grid"),
    ("rain", "Rain", "Short vertical marks"),
    ("bubbles", "Bubbles", "A loose foam"),
    ("aztec", "Aztec", "A stepped border"),
]
PARTICLES = [
    ("dust", "Dust", "Slow motes in the light"),
    ("sparks", "Sparks", "A quick graphite shower"),
    ("orbit", "Orbit", "A loose ring of ink"),
    ("confetti", "Confetti", "Cut-paper celebration"),
]
# Native FieldPresets: name -> four colours in ring order (light to dark).
FIELDS = [
    ("mica-dawn", "Mica dawn", ["#F3E9DA", "#C7A88B", "#6B5A4E", "#2C2723"]),
    ("sea-glass", "Sea glass", ["#E7F4EF", "#9CD3C4", "#3A8A7A", "#123F3A"]),
    ("plum-smoke", "Plum smoke", ["#F2E6EE", "#C89DBB", "#7B4C6D", "#2B1B2A"]),
    ("citrus-haze", "Citrus haze", ["#FFF6D9", "#FFD37A", "#F08A4B", "#7A2E1F"]),
    ("cobalt-rain", "Cobalt rain", ["#E8F1FA", "#9CBCE8", "#3767B1", "#14274D"]),
    ("olive-dusk", "Olive dusk", ["#F0EEDC", "#C7C58F", "#6C7A4A", "#28301F"]),
    ("rose-quartz", "Rose quartz", ["#FBEDEE", "#E7B7BC", "#B86B78", "#4A2430"]),
    ("ink-tide", "Ink tide", ["#E4EEF0", "#8FB6C2", "#3C6C7D", "#0E2630"]),
]
GRADIENTS = [
    ("linear", "Linear", "A straight run between stops", "Linear"),
    ("radial", "Radial", "From the centre out", "Radial"),
    ("sweep", "Sweep", "Around the wheel", "Sweep"),
    ("diamond", "Diamond", "A four-corner spread", "Diamond"),
]
# The native material families: masonry, stone, print screens. These are NOT the
# same thing as Twin's five texture grounds (paper, fluted glass, dither, halftone,
# fur) -- a family is a seeded field an engraver or press can use, a ground is a
# shader. They get their own kind so the catalog can hold both without one
# pretending to be the other.
MATERIALS = [
    ("brick", "Brick", "Masonry courses and mortar", 0.34, "MULTIPLY"),
    ("wood-grain", "Wood", "Laser-cut grain and rings", 0.38, "MULTIPLY"),
    ("marble", "Marble", "Veined stone etching", 0.30, "MULTIPLY"),
    ("concrete", "Concrete", "Cast stone and plaster tooth", 0.34, "MULTIPLY"),
    ("fabric", "Fabric", "Woven cloth and knit lines", 0.32, "MULTIPLY"),
    ("halftone", "Halftone", "Print dot screens", 0.40, "MULTIPLY"),
    ("gradient-mesh", "Mesh", "Smooth tonal meshes", 0.26, "OVERLAY"),
    ("grain", "Grain", "Film and ink tooth", 0.30, "OVERLAY"),
    ("checker", "Checker", "Registration and tile grid", 0.28, "MULTIPLY"),
    ("scanline", "Scanline", "CRT and line-screen print", 0.30, "MULTIPLY"),
]

STROKES = [
    ("checks", "Checks", "Alternating squares", "Checks"),
    ("dots", "Dots", "A dot screen", "Dots"),
    ("stripes", "Stripes", "Parallel bands", "Stripes"),
    ("grid", "Grid", "Two crossing sets of lines", "Grid"),
]

INK_SWATCHES = ["#111111", "#5a5a56", "#8a8983", "#d8d7d1", "#fafaf7"]
PAPER_SWATCHES = ["#fafaf7", "#e7e6e1", "#d8d7d1"]
CONTROL_COLOURS = INK_SWATCHES
MIX = ["#F5ECD8", "#8A3B00", "#5B2A4B", "#1B1212"]

def ring(i, count=4):
    import math
    a = (i / max(count, 1)) * math.pi * 2 - math.pi / 2
    return round(0.5 + math.cos(a) * 0.27, 4), round(0.5 + math.sin(a) * 0.24, 4)

presets = []

for pid, name, note, shader in SHADERS:
    presets.append(feature("shader-" + pid, name, note, kind="shader", source="editor",
        render=shader,
        defaults={ "speed": 0.5, "distortion": 0.35,
                  "swirl": 0.2, "grain": 0.2, "color": INK_SWATCHES[0]}))

for pid, name, note, shader in TEXTURES:
    presets.append(feature("texture-" + pid, name, note, kind="texture", source="editor",
        render=shader,
        defaults={ "paper": PAPER_SWATCHES[0], "roughness": 0.4,
                  "fluff": 1, "scale": 1, "opacity": 1}))

for pid, name, note in PATTERNS:
    presets.append(feature("pattern-" + pid, name, note, kind="pattern", source="editor",
        render=pid,
        defaults={ "opacity": 0.78, "ink": INK_SWATCHES[0],
                  "scale": 1, "rough": 0}))

for pid, name, note in PARTICLES:
    presets.append(feature("particles-" + pid, name, note, kind="particles", source="editor",
        render=pid,
        defaults={ "density": 1, "speed": 1,
                  "color": INK_SWATCHES[0], "trail": 0}))

for pid, name, colours in FIELDS:
    spots = []
    for i, hexv in enumerate(colours):
        x, y = ring(i, len(colours))
        spots.append({"hex": hexv, "x": x, "y": y, "band": 0.30})
    presets.append(feature("field-" + pid, name, "Colour spots in a warped field",
        kind="field", source="native", render="FlowField",
        defaults={"spots": spots, "scale": 0.50, "distortion": 0.60,
                  "swirl": 0.10, "speed": 0.30, "soften": 0.0, "noise": 0.06}))

for index, (pid, name, note, opacity, blend) in enumerate(MATERIALS):
    presets.append(feature("material-" + pid, name, note, kind="material",
        source="native", render=pid,
        defaults={"seed": index * 7919 + 104729, "opacity": opacity,
                  "scale": 1, "blend": blend}))

for pid, name, note, kind in GRADIENTS:
    presets.append(feature("gradient-" + pid, name, note, kind="gradient", source="native",
        render=kind,
        defaults={
                  "stops": [{"at": 0, "hex": "#1B1212"},
                            {"at": 0.5, "hex": "#5B2A4B"},
                            {"at": 1, "hex": "#F5ECD8"}]}))

for pid, name, note, kind in STROKES:
    presets.append(feature("stroke-" + pid, name, note, kind="pattern-stroke", source="native",
        render=kind,
        defaults={ "foreground": "#1B1212",
                  "background": "#F0E7D4", "scale": 1}))

controls = {
  "shader": [
    control("speed", "Speed", "range", min=0, max=1, step=0.05),
    control("distortion", "Distortion", "range", min=0, max=1, step=0.05),
    control("swirl", "Swirl", "range", min=0, max=1, step=0.05),
    control("grain", "Grain", "range", min=0, max=1, step=0.05),
    control("color", "Colour", "swatch", options=CONTROL_COLOURS),
  ],
  "texture": [
    control("paper", "Paper", "swatch", options=PAPER_SWATCHES),
    control("roughness", "Roughness", "range", min=0, max=1, step=0.05),
    control("fluff", "Fluff", "range", min=1, max=3, step=0.1, visibleWhen={"preset": "texture-fur"}),
    control("scale", "Scale", "range", min=0.5, max=2, step=0.05),
    control("opacity", "Opacity", "range", min=0, max=1, step=0.01),
  ],
  "pattern": [
    control("ink", "Ink", "swatch", options=INK_SWATCHES[:4]),
    control("opacity", "Opacity", "range", min=0, max=1, step=0.01),
    control("scale", "Scale", "range", min=0.5, max=3, step=0.05),
    control("rough", "Rough edges", "toggle"),
  ],
  "particles": [
    control("density", "Density", "range", min=0.2, max=3, step=0.05),
    control("speed", "Speed", "range", min=0.2, max=4, step=0.05),
    control("color", "Colour", "swatch", options=INK_SWATCHES[:4]),
    control("trail", "Trail", "toggle"),
  ],
  "field": [
    # Spots are the field's colours placed on a normalized ring; `stops`-kind is
    # the "a list of things" control, and the renderer reads hex/x/y/band from it.
    control("spots", "Colour spots", "stops"),
    control("scale", "Scale", "range", min=0, max=1, step=0.01),
    control("distortion", "Distortion", "range", min=0, max=1, step=0.01),
    control("swirl", "Swirl", "range", min=0, max=1, step=0.01),
    control("speed", "Speed", "range", min=0, max=1, step=0.01),
    control("soften", "Soften", "range", min=0, max=1, step=0.01),
    control("noise", "Grain", "range", min=0, max=1, step=0.01),
  ],
  "gradient": [
    control("stops", "Stops", "stops"),
  ],
  "material": [
    # The seed is the whole recipe for a material: same family, seed, size and
    # scale give the same pixels, so it is a real control, not a detail.
    control("seed", "Seed", "number", min=0, max=99999999, step=1),
    control("opacity", "Opacity", "range", min=0, max=1, step=0.01),
    control("scale", "Scale", "range", min=0.25, max=4, step=0.05),
    control("blend", "Blend", "toggle"),
  ],
  "pattern-stroke": [
    control("foreground", "Foreground", "swatch", options=MIX),
    control("background", "Background", "swatch", options=MIX),
    control("scale", "Scale", "range", min=0.25, max=4, step=0.05),
  ],
}

doc = {
  "version": 1,
  "note": "The library's effects. Twin renders these; it does not own them. "
          "This file is the source of truth: Twin fetches it, and the native "
          "tables are checked against it rather than the other way round.",
  "kinds": ["shader", "texture", "pattern", "particles", "field", "material",
            "gradient", "pattern-stroke"],
  "controls": controls,
  "presets": presets,
}

out = OUT
out.write_text(json.dumps(doc, indent=1, sort_keys=False) + "\n", encoding="utf-8")
print("wrote", out, len(presets), "presets,", out.stat().st_size, "bytes")
