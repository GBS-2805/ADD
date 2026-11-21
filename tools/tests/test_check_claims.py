from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools import check_claims


def make_tree(root: Path, *, bytes_count: int = 17_387_029,
              current_tests: int = 231) -> None:
    docs = root / "docs"
    (docs / "data").mkdir(parents=True)
    # MODERN_PAGES counts this fixture's pages: the root index, "compress", and the
    # numbered ones. Keep the total equal to the constant so the count and the words
    # are tested against one number rather than against each other.
    numbered = check_claims.MODERN_PAGES - 2
    for name in ["compress", *["page-%02d" % i for i in range(1, numbered + 1)]]:
        (docs / name).mkdir(parents=True)
        (docs / name / "index.html").write_text("<html></html>\n", encoding="utf-8")
    (docs / "index.html").write_text("<html></html>\n", encoding="utf-8")
    for i in range(16):
        (docs / "classic" / ("old-%02d" % i)).mkdir(parents=True)
        (docs / "classic" / ("old-%02d" % i) / "index.html").write_text(
            "<html></html>\n", encoding="utf-8")

    tool = {"id": "add", "version": "3.9", "bytes": bytes_count}
    (docs / "data" / "tools.json").write_text(
        json.dumps({"tools": [tool]}), encoding="utf-8")
    (root / "tools").mkdir()
    (root / "tools" / "catalog.json").write_text(
        json.dumps({"tools": [tool]}), encoding="utf-8")
    (root / "README.md").write_text(
        "# Shelf\n\nSeventeen pages, one stylesheet.\n\n"
        "- **ADD 3.9** - APK (17 MB, signed), published from `docs/compress/`.\n",
        encoding="utf-8")
    (docs / "agent-reference.md").write_text(
        "The shelf is seventeen pages.\n", encoding="utf-8")

    (root / "NATIVE_EDITOR_V2.md").write_text(
        "All tests stay green. Current baseline: **%d tests, 0 failures, 0 errors**.\n"
        % current_tests, encoding="utf-8")
    engine = root / "tools" / "engine-spike"
    engine.mkdir()
    (engine / "README.md").write_text(
        "Current baseline: **%d tests, 0 failures, 0 errors**.\n" % current_tests,
        encoding="utf-8")
    results = engine / "app" / "build" / "test-results" / "testDebugUnitTest"
    results.mkdir(parents=True)
    (results / "TEST-engine.xml").write_text(
        '<testsuite tests="%d" failures="0" errors="0" skipped="0" />\n' % current_tests,
        encoding="utf-8")


class ClaimsTests(unittest.TestCase):
    def test_current_tree_passes(self):
        self.assertEqual(check_claims.problems(check_claims.ROOT), [])

    def test_wrong_apk_size_fails_at_readme(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root, bytes_count=12_000_000)
            found = check_claims.problems(root)
            self.assertTrue(any("README.md" in item and "APK size" in item
                                for item in found), found)

    def test_wrong_page_count_fails_at_docs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            (root / "docs" / "page-01" / "index.html").unlink()
            found = check_claims.problems(root)
            expected = check_claims.MODERN_PAGES - 1
            self.assertTrue(any("docs: %d modern pages" % expected in item
                                for item in found), found)

    def test_missing_compress_row_fails_at_readme(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            (root / "README.md").write_text(
                "# Shelf\n\nSeventeen pages.\n- **ADD 3.9** - APK (17 MB).\n",
                encoding="utf-8")
            found = check_claims.problems(root)
            self.assertTrue(any("docs/compress/" in item for item in found), found)

    def test_old_engine_baseline_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root, current_tests=160)
            (root / "tools" / "engine-spike" / "app" / "build" / "test-results"
             / "testDebugUnitTest" / "TEST-engine.xml").write_text(
                '<testsuite tests="166" failures="0" errors="0" skipped="0" />\n',
                encoding="utf-8")
            found = check_claims.problems(root, engine=True)
            self.assertTrue(any("NATIVE_EDITOR_V2.md" in item and "160" in item
                                for item in found), found)

    def test_historical_160_mention_is_allowed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root, current_tests=231)
            native = root / "NATIVE_EDITOR_V2.md"
            native.write_text(
                "Historically this suite had 160 tests. They were expanded.\n"
                "Current baseline: **231 tests, 0 failures, 0 errors**.\n",
                encoding="utf-8")
            self.assertEqual(check_claims.problems(root, engine=True), [])

    def test_engine_mode_needs_test_xml(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            for path in (root / "tools" / "engine-spike" / "app" / "build"
                         / "test-results").rglob("*.xml"):
                path.unlink()
            found = check_claims.problems(root, engine=True)
            self.assertTrue(any("no engine test XML" in item for item in found), found)


if __name__ == "__main__":
    unittest.main()
