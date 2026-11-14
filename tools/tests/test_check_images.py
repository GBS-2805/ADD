from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools import check_images


def write_page(root: Path, body: str, name: str = "index.html") -> Path:
    docs = root / "docs"
    page = docs / name
    page.parent.mkdir(parents=True, exist_ok=True)
    page.write_text("<html><body>%s</body></html>\n" % body, encoding="utf-8")
    return docs


class ImageTests(unittest.TestCase):
    def test_safe_images_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_page(root, (
                '<img src="hero.webp" width="1200" height="800" '
                'fetchpriority="high" decoding="async">'
                '<img src="other.webp" width="600" height="400" loading="lazy" '
                'decoding="async">'))
            self.assertEqual(check_images.problems(root), [])

    def test_missing_dimensions_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_page(root, '<img src="x.webp" loading="lazy" decoding="async">')
            found = check_images.problems(root)
            self.assertTrue(any("missing or non-numeric width" in item for item in found), found)

    def test_lazy_loading_is_required_below_the_hero(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_page(root, (
                '<img src="hero.webp" width="1" height="1" fetchpriority="high" decoding="async">'
                '<img src="x.webp" width="1" height="1" decoding="async">'))
            found = check_images.problems(root)
            self.assertTrue(any("loading=lazy" in item for item in found), found)

    def test_duplicate_high_priority_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_page(root, (
                '<img src="a.webp" width="1" height="1" fetchpriority="high" decoding="async">'
                '<img src="b.webp" width="1" height="1" fetchpriority="high" loading="lazy" decoding="async">'))
            found = check_images.problems(root)
            self.assertTrue(any("fetchpriority=high images" in item for item in found), found)

    def test_png_gallery_capture_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_page(root, '<img src="capture.png" width="1" height="1" loading="lazy" decoding="async">',
                       name="renders/index.html")
            found = check_images.problems(root)
            self.assertTrue(any("expected WebP or AVIF" in item for item in found), found)

    def test_dynamic_image_without_src_is_ignored(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_page(root, '<img id="preview" alt="chosen photo">')
            self.assertEqual(check_images.problems(root), [])

    def test_current_tree_passes(self):
        self.assertEqual(check_images.problems(check_images.ROOT), [])


if __name__ == "__main__":
    unittest.main()
