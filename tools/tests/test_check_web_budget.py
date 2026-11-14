from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools import check_web_budget


def make_tree(root: Path) -> None:
    docs = root / "docs"
    assets = docs / "assets"
    assets.mkdir(parents=True)
    (docs / "data").mkdir()
    (assets / "site.css").write_text(
        '@font-face { src: url("face.woff2"); }\n', encoding="utf-8")
    (assets / "face.woff2").write_bytes(b"font")
    (assets / "site.js").write_text("console.log(1);\n", encoding="utf-8")
    (assets / "photo.webp").write_bytes(b"image")
    (docs / "index.html").write_text(
        '<html><head><link rel="stylesheet" href="assets/site.css">'
        '<script src="assets/site.js"></script></head>'
        '<body><img src="assets/photo.webp" width="1" height="1"></body></html>\n',
        encoding="utf-8")
    (root / "tools").mkdir()


class BudgetTests(unittest.TestCase):
    def test_write_then_check_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            baseline = check_web_budget.write_baseline(root)
            self.assertEqual(check_web_budget.problems(root, baseline), [])

    def test_over_budget_page_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            baseline = check_web_budget.write_baseline(root)
            page = root / "docs" / "index.html"
            page.write_text(page.read_text() + ("x" * 5000), encoding="utf-8")
            found = check_web_budget.problems(root, baseline)
            self.assertTrue(any("html" in item and "budget" in item for item in found), found)

    def test_new_page_without_baseline_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            baseline = check_web_budget.write_baseline(root)
            new = root / "docs" / "new"
            new.mkdir()
            (new / "index.html").write_text("<html></html>\n", encoding="utf-8")
            found = check_web_budget.problems(root, baseline)
            self.assertTrue(any("new/index.html: new page" in item for item in found), found)

    def test_classic_ceiling_is_frozen(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_tree(root)
            classic = root / "docs" / "classic" / "old"
            classic.mkdir(parents=True)
            (classic / "index.html").write_text("<html>old</html>\n", encoding="utf-8")
            baseline = check_web_budget.write_baseline(root)
            (classic / "index.html").write_text("<html>" + ("x" * 5000) + "</html>\n",
                                                encoding="utf-8")
            found = check_web_budget.problems(root, baseline)
            self.assertTrue(any("classic: html" in item for item in found), found)


if __name__ == "__main__":
    unittest.main()
