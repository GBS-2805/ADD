import tempfile
import unittest
from pathlib import Path

from tools import update_handoff


class HandoffProvenanceTest(unittest.TestCase):
    def app(self):
        return {"id": "add", "version": "3.9", "apk": "/ADD-3.9.apk",
                "bytes": 17234552,
                "sha256": "340810238ee240fdf897e0c1f5d17f29df98710d9fec18cf1a9bace821fc231b"}

    def test_block_names_commit_time_origin_apk_and_gates(self):
        body = update_handoff.block("abc1234", "2026-11-12T00:00:00Z",
                                    "https://example.test", "17/17", self.app())
        for piece in ("abc1234", "2026-11-12T00:00:00Z", "https://example.test",
                      "3.9", "17,234,552", self.app()["sha256"], "34081023…", "17/17"):
            self.assertIn(piece, body)

    def test_block_escapes_user_values(self):
        body = update_handoff.block("abc1234", "2026-11-12T00:00:00Z",
                                    "https://example.test", "<script>", self.app())
        self.assertNotIn("<script>", body)
        self.assertIn("&lt;script&gt;", body)

    def test_replacement_touches_only_the_marked_block(self):
        text = ("before\n<!-- provenance:start -->\nold\n<!-- provenance:end -->\nafter\n")
        out = update_handoff.replace_block(text, "<li>new</li>")
        self.assertIn("before\n<!-- provenance:start -->\n        <li>new</li>\n"
                      "        <!-- provenance:end -->\nafter\n", out)
        self.assertNotIn("old", out)

    def test_missing_marker_is_refused(self):
        with self.assertRaises(SystemExit):
            update_handoff.replace_block("no markers here", "<li>x</li>")

    def test_duplicate_marker_is_refused(self):
        text = ("<!-- provenance:start -->a<!-- provenance:end -->"
                "<!-- provenance:start -->b<!-- provenance:end -->")
        with self.assertRaises(SystemExit):
            update_handoff.replace_block(text, "<li>x</li>")

    def test_bad_commit_is_refused(self):
        self.assertIsNone(update_handoff.SHA_RE.match("NOT-A-SHA"))
        self.assertIsNone(update_handoff.SHA_RE.match("abc"))
        self.assertIsNotNone(update_handoff.SHA_RE.match("1b6b911"))

    def test_time_requires_a_timezone(self):
        with self.assertRaises(SystemExit):
            update_handoff.parse_time("2026-11-12T00:00:00")
        self.assertEqual(update_handoff.parse_time("2026-11-12T00:00:00Z"),
                         "2026-11-12T00:00:00Z")

    def test_pending_provenance_does_not_claim_a_deploy(self):
        self.assertIsNone(update_handoff.parse_time("pending"))
        body = update_handoff.block("abc1234", None, "https://example.test",
                                    "pending", self.app())
        self.assertIn("not yet deployed from this tree", body)
        self.assertNotIn("deployed <time", body)

    def test_origin_rejects_a_path(self):
        with self.assertRaises(SystemExit):
            update_handoff.origin_of("https://example.test/path")
        self.assertEqual(update_handoff.origin_of("https://example.test/"),
                         "https://example.test")


if __name__ == "__main__":
    unittest.main()
