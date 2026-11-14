import json
import tempfile
import unittest
from pathlib import Path

from tools import check_renders


class RenderManifestTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.docs = Path(self.tmp.name)
        (self.docs / "data").mkdir()
        (self.docs / "assets").mkdir()
        (self.docs / "renders").mkdir()
        (self.docs / "data/assets.json").write_text(
            '{"files": {"site.css": "site.css", "site.js": "site.js"}}\n', encoding="utf-8")
        (self.docs / "assets/site.css").write_text("body{}\n", encoding="utf-8")
        (self.docs / "assets/site.js").write_text("//\n", encoding="utf-8")
        (self.docs / "index.html").write_text("<h1>hi</h1>\n", encoding="utf-8")
        (self.docs / "renders/page.webp").write_bytes(b"webp")
        self.assets = {"site.css": "site.css", "site.js": "site.js"}
        self.write_manifest()

    def tearDown(self):
        self.tmp.cleanup()

    def write_manifest(self):
        source = check_renders.source_hash(self.docs, "", self.assets)
        manifest = {"schema": 1, "shots": {"page": {
            "page": "", "width": 1300, "full": False, "source": source,
            "image": "page.webp", "imageSha": check_renders.sha256(b"webp"),
        }}}
        (self.docs / "renders/manifest.json").write_text(json.dumps(manifest), encoding="utf-8")

    def test_current_manifest_passes(self):
        self.assertEqual(check_renders.check(self.docs), [])

    def test_changed_page_fails(self):
        (self.docs / "index.html").write_text("<h1>changed</h1>\n", encoding="utf-8")
        problems = check_renders.check(self.docs)
        self.assertTrue(any("page source changed" in problem for problem in problems))

    def test_changed_assets_map_fails(self):
        (self.docs / "assets/other.js").write_text("// other\n", encoding="utf-8")
        (self.docs / "data/assets.json").write_text(
            '{"files": {"site.css": "site.css", "site.js": "other.js"}}\n', encoding="utf-8")
        problems = check_renders.check(self.docs)
        self.assertTrue(any("page source changed" in problem for problem in problems))

    def test_missing_webp_fails(self):
        (self.docs / "renders/page.webp").unlink()
        problems = check_renders.check(self.docs)
        self.assertTrue(any("missing page.webp" in problem for problem in problems))

    def test_orphan_webp_fails(self):
        (self.docs / "renders/orphan.webp").write_bytes(b"orphan")
        problems = check_renders.check(self.docs)
        self.assertTrue(any("orphan" in problem for problem in problems))

    def test_check_does_not_write(self):
        before = (self.docs / "renders/manifest.json").read_bytes()
        check_renders.check(self.docs)
        self.assertEqual((self.docs / "renders/manifest.json").read_bytes(), before)


if __name__ == "__main__":
    unittest.main()
