import struct
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools import seo_meta

PAGE = """<!doctype html><html><head>
<title>Test &amp; page</title>
<meta name="description" content="A description &amp; a half." />
</head><body>copy</body></html>
"""


class SeoMetaTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.docs = Path(self.tmp.name)
        (self.docs / "icons").mkdir()
        self.page = self.docs / "icons" / "index.html"
        self.page.write_text(PAGE, encoding="utf-8")
        self.patch = mock.patch.object(seo_meta, "DOCS", self.docs)
        self.patch.start()

    def tearDown(self):
        self.patch.stop()
        self.tmp.cleanup()

    def test_check_rejects_a_missing_canonical(self):
        self.assertEqual(seo_meta.check_page(self.page, "https://example.test"),
                         "missing marked metadata block")

    def test_write_inserts_the_marked_block(self):
        text = PAGE
        updated = seo_meta.insert(text, seo_meta.block(self.page, "https://example.test", text))
        self.assertIn(seo_meta.START, updated)
        self.assertIn('<link rel="canonical" href="https://example.test/icons/" />', updated)
        self.assertTrue(updated.rstrip().endswith("</html>"))

    def test_check_rejects_a_wrong_origin(self):
        text = PAGE
        self.page.write_text(
            seo_meta.insert(text, seo_meta.block(self.page, "https://example.test", text)),
            encoding="utf-8")
        self.assertIn("differs", seo_meta.check_page(self.page, "https://other.test"))

    def test_modern_pages_are_in_scope_and_classic_is_not(self):
        classic = self.docs / "classic" / "index.html"
        classic.parent.mkdir()
        classic.write_text(PAGE, encoding="utf-8")
        self.assertEqual(seo_meta.modern_pages(), [self.page])

    def test_og_and_twitter_fields_match_the_page(self):
        text = PAGE
        rendered = seo_meta.block(self.page, "https://example.test", text)
        self.assertIn('property="og:title" content="Test &amp; page"', rendered)
        self.assertIn('name="twitter:description" content="A description &amp; a half."',
                      rendered)
        self.assertIn('property="og:url" content="https://example.test/icons/"', rendered)

    def test_root_page_url_is_a_slash(self):
        root = self.docs / "index.html"
        root.write_text(PAGE, encoding="utf-8")
        self.assertEqual(seo_meta.page_url(root), "/")

    def test_homepage_carries_one_website_search_action(self):
        import json
        root = self.docs / "index.html"
        root.write_text(PAGE, encoding="utf-8")
        rendered = seo_meta.block(root, "https://example.test")
        marker = '<script type="application/ld+json">'
        blocks = rendered.split(marker)[1:]
        self.assertEqual(len(blocks), 1)
        schema = json.loads(blocks[0].split("</script>")[0])
        self.assertEqual(schema["@type"], "WebSite")
        self.assertEqual(schema["url"], "https://example.test/")
        self.assertEqual(schema["potentialAction"]["target"],
                         "https://example.test/search/?q={search_term_string}")

    def test_og_card_is_png_and_1200_by_630(self):
        data = Path("docs/assets/og.png").read_bytes()
        self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
        width, height = struct.unpack(">II", data[16:24])
        self.assertEqual((width, height), (1200, 630))


if __name__ == "__main__":
    unittest.main()
