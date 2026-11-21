"""The editor gate must fail on the two things it exists to catch.

The payload check is the interesting one: it is the only thing standing between the
bundle split and a silent regression, so its arithmetic is tested against files built
for the purpose rather than trusted.
"""
from __future__ import annotations

import gzip
import json
import tempfile
import unittest
from pathlib import Path

from tools import check_editor


class InitialScripts(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.page = self.root / "docs/editor/index.html"
        (self.page.parent / "assets").mkdir(parents=True)

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def write(self, name: str, data: bytes) -> None:
        (self.page.parent / "assets" / name).write_bytes(data)

    def test_finds_only_local_shelf_scripts(self) -> None:
        self.write("a.js", b"a")
        self.write("b.js", b"b")
        self.page.write_text(
            '<script src="/editor/assets/a.js"></script>'
            '<script src="./assets/b.js"></script>'
            '<script src="https://example.test/remote.js"></script>'
            "<script>inline()</script>",
            encoding="utf-8")
        found = [path.name for path in check_editor.initial_scripts_at(self.page)]
        self.assertEqual(sorted(found), ["a.js", "b.js"])

    def test_ignores_a_script_named_but_not_there(self) -> None:
        self.page.write_text('<script src="/editor/assets/missing.js"></script>',
                             encoding="utf-8")
        self.assertEqual(check_editor.initial_scripts_at(self.page), [])

    def test_measures_gzip_not_raw(self) -> None:
        # Highly repetitive on purpose: raw is much larger than gzip, so a measure
        # that forgot to compress would be visibly wrong rather than subtly wrong.
        self.write("big.js", b"x" * 200_000)
        self.page.write_text('<script src="/editor/assets/big.js"></script>',
                             encoding="utf-8")
        measured = check_editor.measure_at(self.page)
        self.assertEqual(measured["files"], 1)
        self.assertLess(measured["bytes"], 5_000)
        self.assertEqual(measured["bytes"],
                         len(gzip.compress(b"x" * 200_000, 9)))

    def test_over_ceiling_fails_and_under_passes(self) -> None:
        self.write("a.js", b"y" * 50_000)
        self.page.write_text('<script src="/editor/assets/a.js"></script>',
                             encoding="utf-8")
        size = check_editor.measure_at(self.page)["bytes"]
        self.assertTrue(check_editor.compare_at(self.page, size))
        self.assertFalse(check_editor.compare_at(self.page, size - 1))

    def test_missing_page_is_an_error_not_a_zero(self) -> None:
        with self.assertRaises(SystemExit):
            check_editor.initial_scripts_at(self.root / "docs/editor/nope.html")


class Budget(unittest.TestCase):
    def test_record_is_valid_json_with_a_note(self) -> None:
        # The committed file has to parse and say what it means; a bare number in a
        # file nobody documented is how a ceiling gets raised without anyone noticing.
        path = Path(__file__).resolve().parents[2] / "tools/editor_budget.json"
        record = json.loads(path.read_text(encoding="utf-8"))
        self.assertIn("budget_bytes", record)
        self.assertIsInstance(record["budget_bytes"], int)
        self.assertIn("Tighten", record["note"])


if __name__ == "__main__":
    unittest.main()
