import os
import tempfile
import time
import unittest
import zipfile
from pathlib import Path

from tools import check_editor_bundles

PAGE = """<!doctype html>
<link rel="stylesheet" href="./assets/index.css">
<script src="./assets/index.js"></script>
"""


def apk(path, entries):
    with zipfile.ZipFile(path, "w") as bundle:
        for name, data in entries.items():
            bundle.writestr(name, data)


class EditorBundlesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.research = self.dir / "research.apk"
        self.release = self.dir / "release.apk"

    def tearDown(self):
        self.tmp.cleanup()

    def research_entries(self):
        return {
            "assets/www/editor/index.html": PAGE,
            "assets/www/editor/assets/index.js": "console.log(1);",
            "assets/www/editor/assets/index.css": "body{}",
        }

    def test_research_page_and_its_assets_pass(self):
        apk(self.research, self.research_entries())
        apk(self.release, {"assets/www/index.html": "shelf"})
        self.assertEqual(check_editor_bundles.problems(self.research, self.release), [])

    def test_missing_page_fails(self):
        entries = self.research_entries()
        entries.pop("assets/www/editor/index.html")
        apk(self.research, entries)
        apk(self.release, {})
        found = check_editor_bundles.problems(self.research, self.release)
        self.assertTrue(any("no assets/www/editor/index.html" in problem for problem in found))

    def test_page_with_missing_asset_fails(self):
        entries = self.research_entries()
        entries.pop("assets/www/editor/assets/index.js")
        apk(self.research, entries)
        apk(self.release, {})
        found = check_editor_bundles.problems(self.research, self.release)
        self.assertTrue(any("loads missing" in problem and "index.js" in problem
                            for problem in found))

    def test_release_carrying_editor_assets_fails(self):
        apk(self.research, self.research_entries())
        apk(self.release, {"assets/www/editor/index.html": "should not be here"})
        found = check_editor_bundles.problems(self.research, self.release)
        self.assertTrue(any("release APK still ships" in problem for problem in found))

    def test_twin_lane_is_not_the_release_bundle(self):
        release = self.dir / "ADD-3.9.apk"
        release.write_bytes(b"")
        twin = self.dir / "ADD-4.0-twin.apk"
        twin.write_bytes(b"")
        os.utime(twin, (time.time() + 60, time.time() + 60))
        picked = check_editor_bundles.newest(
            "ADD-*.apk", "*-editor.apk", "*-twin.apk", directory=self.dir)
        self.assertEqual(picked.name, "ADD-3.9.apk")


if __name__ == "__main__":
    unittest.main()
