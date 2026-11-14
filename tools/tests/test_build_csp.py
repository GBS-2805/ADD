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
    classic = docs / "classic"
    classic.mkdir()
    (classic / "index.html").write_text(
        '<html><body><script>console.log("classic")</script></body></html>\n',
        encoding="utf-8")
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
            self.assertEqual(len(scripts), 2)
            self.assertIn("sha256-", build_csp.script_hash(scripts[0][1]))
            self.assertEqual({path.parts[0] for path, _ in scripts}, {"classic", "index.html"})

    def test_write_then_check_passes_and_removes_report_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            path = root / "docs" / "vercel.json"
            data = json.loads(path.read_text())
            data["headers"] = [{"source": "/(.*)", "headers": [
                {"key": build_csp.REPORT_ONLY_HEADER, "value": "default-src 'self'"}]}]
            path.write_text(json.dumps(data), encoding="utf-8")
            build_csp.write_configs(root)
            self.assertEqual(build_csp.problems(root), [])
            data = json.loads(path.read_text())
            keys = [header["key"] for header in data["headers"][0]["headers"]]
            self.assertIn(build_csp.CSP_HEADER, keys)
            self.assertNotIn(build_csp.REPORT_ONLY_HEADER, keys)

    def test_handoff_allows_site_images_but_site_does_not(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            site_policy = build_csp.policy(root)
            handoff_policy = build_csp.policy(root, handoff=True)
            self.assertNotIn(build_csp.SITE_ORIGIN, site_policy)
            self.assertIn(build_csp.SITE_ORIGIN, handoff_policy)

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
