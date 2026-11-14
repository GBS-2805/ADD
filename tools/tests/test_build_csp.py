from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools import build_csp


def make_tree(root: Path) -> None:
    docs = root / "docs"
    docs.mkdir()
    (docs / "index.html").write_text(
        '<html><body><script>console.log("inline")</script>'
        '<script src="site.js"></script></body></html>\n', encoding="utf-8")
    (docs / "vercel.json").write_text('{"headers": []}\n', encoding="utf-8")
    handoff = root / "handoff"
    handoff.mkdir()
    (handoff / "vercel.json").write_text('{"headers": []}\n', encoding="utf-8")


class CspTests(unittest.TestCase):
    def test_inline_script_is_hashed_and_external_is_not(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            scripts = build_csp.inline_scripts(root)
            self.assertEqual(len(scripts), 1)
            self.assertIn("sha256-", build_csp.script_hash(scripts[0][1]))

    def test_write_then_check_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            build_csp.write_configs(root)
            self.assertEqual(build_csp.problems(root), [])
            data = json.loads((root / "docs" / "vercel.json").read_text())
            self.assertTrue(any(header["key"] == build_csp.CSP_HEADER
                                for header in data["headers"][0]["headers"]))

    def test_missing_hash_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            build_csp.write_configs(root)
            path = root / "docs" / "vercel.json"
            data = json.loads(path.read_text())
            data["headers"][0]["headers"][0]["value"] = "default-src 'self'"
            path.write_text(json.dumps(data), encoding="utf-8")
            found = build_csp.problems(root)
            self.assertTrue(any("missing inline-script hash" in item for item in found), found)

    def test_current_tree_passes(self):
        self.assertEqual(build_csp.problems(build_csp.ROOT), [])


if __name__ == "__main__":
    unittest.main()
