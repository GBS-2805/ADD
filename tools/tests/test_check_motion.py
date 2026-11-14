from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools import check_motion


def page(root: Path, style: str) -> None:
    docs = root / "docs"
    docs.mkdir()
    (docs / "index.html").write_text(
        "<html><head><style>%s</style></head><body></body></html>\n" % style,
        encoding="utf-8")


class MotionTests(unittest.TestCase):
    def test_safe_motion_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            page(root, ".card { transition: transform .2s ease; } "
                      "@media (prefers-reduced-motion: reduce) { .card { transition: none; } }")
            self.assertEqual(check_motion.problems(root), [])

    def test_missing_reduced_motion_block_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            page(root, ".card { transition: transform .2s ease; }")
            found = check_motion.problems(root)
            self.assertTrue(any("no prefers-reduced-motion block" in item for item in found), found)

    def test_over_500ms_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            page(root, ".card { animation: rise .62s ease both; } "
                      "@media (prefers-reduced-motion: reduce) { .card { animation: none; } }")
            found = check_motion.problems(root)
            self.assertTrue(any("620ms exceeds 500ms" in item for item in found), found)

    def test_no_motion_needs_no_block(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            page(root, ".card { color: red; }")
            self.assertEqual(check_motion.problems(root), [])

    def test_current_tree_passes(self):
        self.assertEqual(check_motion.problems(check_motion.ROOT), [])


if __name__ == "__main__":
    unittest.main()
