"""The effects catalog gate must fail for the right reasons.

The catalog is what Twin will render from, so the cases that matter are the ones
that would look like a rendering bug rather than a data bug: a duplicate id, a
default with no control behind it, a `visibleWhen` naming a preset that was
renamed, and a native table that has quietly forked from the JSON.
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools import build_effects, check_effects_native


def catalog() -> dict:
    """The smallest catalog that satisfies every rule."""
    return {
        "version": 1,
        "kinds": list(build_effects.KINDS),
        "controls": {
            "shader": [
                {"id": "speed", "label": "Speed", "kind": "range",
                 "min": 0, "max": 1, "step": 0.05},
                {"id": "color", "label": "Colour", "kind": "swatch",
                 "options": ["#111111"]},
            ],
            "texture": [
                {"id": "opacity", "label": "Opacity", "kind": "range",
                 "min": 0, "max": 1, "step": 0.01},
            ],
            "pattern": [
                {"id": "ink", "label": "Ink", "kind": "swatch",
                 "options": ["#111111"]},
            ],
            "particles": [
                {"id": "speed", "label": "Speed", "kind": "range",
                 "min": 0.2, "max": 4, "step": 0.05},
            ],
            "field": [
                {"id": "spots", "label": "Colour spots", "kind": "stops"},
                {"id": "scale", "label": "Scale", "kind": "range",
                 "min": 0, "max": 1, "step": 0.01},
            ],
            "material": [
                {"id": "seed", "label": "Seed", "kind": "number",
                 "min": 0, "max": 99999999, "step": 1},
                {"id": "opacity", "label": "Opacity", "kind": "range",
                 "min": 0, "max": 1, "step": 0.01},
                {"id": "blend", "label": "Blend", "kind": "toggle"},
            ],
            "gradient": [
                {"id": "stops", "label": "Stops", "kind": "stops"},
            ],
            "pattern-stroke": [
                {"id": "scale", "label": "Scale", "kind": "range",
                 "min": 0.25, "max": 4, "step": 0.05},
            ],
        },
        "presets": [
            {"id": "shader-mesh", "name": "Mesh", "note": "n", "kind": "shader",
             "source": "twin", "render": "MeshGradient",
             "defaults": {"speed": 0.5, "color": "#111111"}},
            {"id": "texture-paper", "name": "Paper", "note": "n", "kind": "texture",
             "source": "twin", "render": "PaperTexture",
             "defaults": {"opacity": 1}},
            {"id": "pattern-circuit", "name": "Circuit", "note": "n",
             "kind": "pattern", "source": "twin", "render": "circuit",
             "defaults": {"ink": "#111111"}},
            {"id": "particles-dust", "name": "Dust", "note": "n",
             "kind": "particles", "source": "twin", "render": "dust",
             "defaults": {"speed": 1}},
            {"id": "field-mica-dawn", "name": "Mica dawn", "note": "n",
             "kind": "field", "source": "native", "render": "FlowField",
             "defaults": {"spots": [], "scale": 0.5}},
            {"id": "material-brick", "name": "Brick", "note": "n",
             "kind": "material", "source": "native", "render": "brick",
             "defaults": {"seed": 1, "opacity": 0.34, "blend": "MULTIPLY"}},
            {"id": "gradient-linear", "name": "Linear", "note": "n",
             "kind": "gradient", "source": "native", "render": "Linear",
             "defaults": {"stops": []}},
            {"id": "stroke-checks", "name": "Checks", "note": "n",
             "kind": "pattern-stroke", "source": "native", "render": "Checks",
             "defaults": {"scale": 1}},
        ],
    }


class CatalogShape(unittest.TestCase):
    def test_the_minimal_catalog_passes(self):
        self.assertEqual(build_effects.problems(catalog()), [])

    def test_a_duplicate_id_fails(self):
        data = catalog()
        data["presets"][1]["id"] = "shader-mesh"
        self.assertTrue(any("already used" in p for p in build_effects.problems(data)))

    def test_an_id_with_spaces_fails(self):
        data = catalog()
        data["presets"][0]["id"] = "Mesh gradient"
        self.assertTrue(any("bad id" in p for p in build_effects.problems(data)))

    def test_a_default_with_no_control_fails(self):
        data = catalog()
        data["presets"][0]["defaults"]["glitter"] = 1
        self.assertTrue(any("no control of that name" in p
                            for p in build_effects.problems(data)))

    def test_a_visible_when_naming_a_missing_preset_fails(self):
        data = catalog()
        data["controls"]["texture"][0]["visibleWhen"] = {"preset": "texture-fur"}
        found = build_effects.problems(data)
        self.assertTrue(any("visibleWhen names 'texture-fur'" in p for p in found))

    def test_a_kind_with_no_preset_fails(self):
        data = catalog()
        data["presets"] = [p for p in data["presets"] if p["kind"] != "particles"]
        self.assertTrue(any("no particles preset" in p
                            for p in build_effects.problems(data)))

    def test_a_range_with_min_above_max_fails(self):
        data = catalog()
        data["controls"]["shader"][0].update(min=1, max=0)
        self.assertTrue(any("is not below max" in p
                            for p in build_effects.problems(data)))

    def test_a_swatch_that_is_not_a_colour_fails(self):
        data = catalog()
        data["controls"]["shader"][1]["options"] = ["red"]
        self.assertTrue(any("not a #rrggbb" in p
                            for p in build_effects.problems(data)))

    def test_an_unknown_kind_fails(self):
        data = catalog()
        data["presets"][0]["kind"] = "hologram"
        self.assertTrue(any("not one of" in p for p in build_effects.problems(data)))


class CatalogCeiling(unittest.TestCase):
    """The catalog is fetched at runtime, so the page byte gate cannot see it."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.out = self.root / "effects.json"
        self.out.write_text(json.dumps(catalog()), encoding="utf-8")
        for name, value in (("OUT", self.out), ("ROOT", self.root)):
            patcher = mock.patch.object(build_effects, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def tearDown(self):
        self.tmp.cleanup()

    def run_gate(self, limit):
        from contextlib import redirect_stdout
        import io

        buf = io.StringIO()
        with mock.patch.object(build_effects, "MAX_BYTES", limit), \
                mock.patch.object(build_effects.sys, "argv", ["build_effects",
                                                             "--check"]), \
                redirect_stdout(buf):
            code = build_effects.main()
        return code, buf.getvalue()

    def test_a_catalog_over_the_ceiling_fails(self):
        code, out = self.run_gate(10)
        self.assertEqual(code, 1)
        self.assertIn("over the", out)
        self.assertIn("MAX_BYTES", out)

    def test_the_ceiling_is_comfortably_above_the_shipped_file(self):
        shipped = Path(build_effects.ROOT) / "docs/data/effects.json"
        if shipped.is_file():
            self.assertLess(shipped.stat().st_size, build_effects.MAX_BYTES)


class NativeAgreement(unittest.TestCase):
    """`check_effects_native` reads Kotlin as text; these pin the parse."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.engine = self.root / "engine"
        self.engine.mkdir()
        self.kotlin()
        self.data = catalog()
        self.data["presets"][4]["defaults"] = {
            "spots": [{"hex": "#F3E9DA", "x": 0.5, "y": 0.26, "band": 0.3}],
            "scale": 0.5,
        }
        self.write()

        for name, value in (
                ("FIELD_FILE", self.engine / "FieldPaint.kt"),
                ("TEXTURE_FILE", self.engine / "TexturePresets.kt"),
                ("CATALOG", self.root / "effects.json"),
                ("ROOT", self.root)):
            patcher = mock.patch.object(check_effects_native, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def tearDown(self):
        self.tmp.cleanup()

    def kotlin(self):
        (self.engine / "FieldPaint.kt").write_text(
            "data class FieldSpec(\n"
            "    val scale: Float = 0.50f,\n"
            ")\n"
            "object FieldPresets {\n"
            "    val all: List<Preset> = listOf(\n"
            '        preset("Mica dawn", 0xFFF3E9DA.toInt()),\n'
            "    )\n"
            "    fun ringPosition(index: Int, count: Int = 4): Pair<Float, Float> {\n"
            "        return (0.5f + cos(angle) * 0.27f) to (0.5f + sin(angle) * 0.24f)\n"
            "    }\n"
            "}\n", encoding="utf-8")
        (self.engine / "TexturePresets.kt").write_text(
            '    Brick("brick", "BRICK", "Masonry", 0.34f, BlendKind.Multiply),\n',
            encoding="utf-8")

    def write(self):
        (self.root / "effects.json").write_text(json.dumps(self.data),
                                                encoding="utf-8")

    def test_agreement_passes(self):
        self.assertEqual(check_effects_native.problems(), [])

    def test_a_colour_that_drifted_fails(self):
        self.data["presets"][4]["defaults"]["spots"][0]["hex"] = "#FFFFFF"
        self.write()
        found = check_effects_native.problems()
        self.assertTrue(any("#F3E9DA" in p and "#FFFFFF" in p for p in found))

    def test_a_material_opacity_that_drifted_fails(self):
        self.data["presets"][5]["defaults"] = {"seed": 1, "blend": "MULTIPLY"}
        self.write()
        found = check_effects_native.problems()
        self.assertTrue(any("opacity" in p for p in found))

    def test_a_moved_table_fails_rather_than_passing_on_nothing(self):
        (self.engine / "FieldPaint.kt").write_text("// rewritten\n",
                                                   encoding="utf-8")
        found = check_effects_native.problems()
        self.assertTrue(any("did the table move" in p for p in found))


if __name__ == "__main__":
    unittest.main()
