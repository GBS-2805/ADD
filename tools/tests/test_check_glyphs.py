"""The glyph gate has to fail for the right reasons, not just pass on a good tree.

Each test builds a small stand-in tree so the four checks can be exercised
independently: a page painting an uncovered mark, a font file whose bytes moved,
a subset glyph outside unicode-range, and a shipped glyph no page uses.
"""

from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools import check_glyphs


class GlyphGate(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.docs = self.root / "docs"
        (self.docs / "assets" / "fonts").mkdir(parents=True)
        (self.root / "tools").mkdir()

        # One text face with basic Latin, and the subset with the arrow and burger.
        self.face("poppins-400.woff2", b"poppins")
        self.face("add-symbols.woff2", b"symbols")
        self.faces = {
            "poppins-400.woff2": ["0020-007E"],
            "add-symbols.woff2": ["2192", "2630"],
        }
        self.write_manifest()
        self.css(unicode_range="U+2192, U+2630")
        self.page("index.html", "<p>All \u2192 and &#9776;</p>")

        for name, value in (("ROOT", self.root), ("DOCS", self.docs),
                            ("FONTS", self.docs / "assets" / "fonts"),
                            ("SUBSET", self.docs / "assets" / "fonts" /
                             "add-symbols.woff2"),
                            ("SITE_CSS", self.docs / "assets" / "site.css"),
                            ("MANIFEST", self.root / "tools" / "glyphs.json")):
            patcher = mock.patch.object(check_glyphs, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def tearDown(self):
        self.tmp.cleanup()

    # -- fixtures -----------------------------------------------------------
    def face(self, name, payload):
        (self.docs / "assets" / "fonts" / name).write_bytes(payload)

    def write_manifest(self):
        digests = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                   for p in (self.docs / "assets" / "fonts").glob("*.woff2")}
        (self.root / "tools" / "glyphs.json").write_text(json.dumps({
            "subset": "add-symbols.woff2",
            "subset_sha256": digests["add-symbols.woff2"],
            "faces": self.faces,
            "sha256": digests,
        }), encoding="utf-8")

    def css(self, unicode_range="U+2192, U+2630"):
        (self.docs / "assets" / "site.css").write_text(
            '@font-face {\n  font-family: "ADD Symbols";\n'
            '  src: url("fonts/add-symbols.woff2") format("woff2");\n'
            "  unicode-range: %s;\n}\n" % unicode_range, encoding="utf-8")

    def page(self, name, body, folder=None):
        target = (self.docs / folder) if folder else self.docs
        target.mkdir(parents=True, exist_ok=True)
        (target / name).write_text("<html><body>%s</body></html>" % body,
                                   encoding="utf-8")

    def run_gate(self):
        from contextlib import redirect_stdout
        import io

        buf = io.StringIO()
        with redirect_stdout(buf):
            code = check_glyphs.main()
        return code, buf.getvalue()

    # -- the four checks ----------------------------------------------------
    def test_a_clean_tree_passes(self):
        code, out = self.run_gate()
        self.assertEqual(code, 0, out)
        self.assertIn("glyphs: OK", out)

    def test_an_uncovered_character_fails_and_is_named(self):
        self.page("index.html", "<p>All \u2192 and &#9776; then \u0394</p>")
        self.faces["add-symbols.woff2"] = ["2192", "2630"]  # delta not shipped
        self.write_manifest()
        code, out = self.run_gate()
        self.assertEqual(code, 1)
        self.assertIn("U+0394", out)
        self.assertIn("index.html", out)

    def test_an_entity_is_decoded_before_it_is_checked(self):
        # &#9776; is the burger. Read literally it is ASCII and would pass.
        self.page("index.html", '<button>&#9776;</button>')
        self.faces["add-symbols.woff2"] = ["2192"]
        self.write_manifest()
        self.css(unicode_range="U+2192")
        code, out = self.run_gate()
        self.assertEqual(code, 1)
        self.assertIn("U+2630", out)

    def test_a_changed_font_file_fails(self):
        self.face("poppins-400.woff2", b"poppins, edited")
        code, out = self.run_gate()
        self.assertEqual(code, 1)
        self.assertIn("no longer match", out)
        self.assertIn("poppins-400.woff2", out)

    def test_a_subset_glyph_outside_unicode_range_fails(self):
        self.css(unicode_range="U+2192")
        code, out = self.run_gate()
        self.assertEqual(code, 1)
        self.assertIn("never fetches", out)
        self.assertIn("U+2630", out)

    def test_a_shipped_glyph_no_page_uses_fails(self):
        self.faces["add-symbols.woff2"] = ["2192", "2630", "2190"]
        self.write_manifest()
        self.css(unicode_range="U+2190, U+2192, U+2630")
        code, out = self.run_gate()
        self.assertEqual(code, 1)
        self.assertIn("unused", out)
        self.assertIn("U+2190", out)

    def test_classic_pages_are_ignored(self):
        # A classic page may paint anything; only modern pages constrain the subset.
        self.page("index.html", "<p>\u03a9 \u00e5</p>", folder="classic/old")
        code, out = self.run_gate()
        self.assertEqual(code, 0, out)

    def test_a_missing_manifest_is_a_clear_failure(self):
        (self.root / "tools" / "glyphs.json").unlink()
        with self.assertRaises(SystemExit) as raised:
            self.run_gate()
        self.assertIn("build_symbols", str(raised.exception))



if __name__ == "__main__":
    unittest.main()
