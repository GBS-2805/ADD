import json
import shutil
import tempfile
import threading
import unittest
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from tools import check_live


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass


class LiveCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.docs = Path(self.tmp.name) / "docs"
        self.docs.mkdir()
        (self.docs / "index.html").write_text("<h1>hello</h1>\n", encoding="utf-8")
        (self.docs / "data").mkdir()
        (self.docs / "data/assets.json").write_text("{}\n", encoding="utf-8")
        (self.docs / "data/tools.json").write_text('{"tools": []}\n', encoding="utf-8")
        (self.docs / "renders").mkdir()
        (self.docs / "renders/page.webp").write_bytes(b"webp")
        self.receipt = {"schema": 1, "commit": "abc123", "deployedAt": "2026-11-12T00:00:00Z",
                        "project": "artbase"}
        (self.docs / "_deploy.json").write_text(json.dumps(self.receipt), encoding="utf-8")
        self.live = Path(self.tmp.name) / "live"
        shutil.copytree(self.docs, self.live)
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), lambda *a, **kw: QuietHandler(
            *a, directory=str(self.live), **kw))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.origin = "http://127.0.0.1:%d" % self.server.server_port

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.tmp.cleanup()

    def test_identical_tree_passes(self):
        self.assertEqual(check_live.check(self.origin, self.docs, "abc123"), [])

    def test_changed_html_fails_with_the_file_name(self):
        (self.docs / "index.html").write_text("<h1>changed</h1>\n", encoding="utf-8")
        problems = check_live.check(self.origin, self.docs, "abc123")
        self.assertTrue(any("index.html" in problem for problem in problems), problems)

    def test_missing_local_receipt_fails(self):
        (self.docs / "_deploy.json").unlink()
        with self.assertRaises(SystemExit):
            check_live.check(self.origin, self.docs, "abc123")

    def test_receipt_commit_mismatch_fails(self):
        problems = check_live.check(self.origin, self.docs, "different")
        self.assertTrue(any("receipt commit" in problem for problem in problems))

    def test_live_receipt_is_fetched_and_must_match_head(self):
        self.assertEqual(check_live.check(self.origin, self.docs, "abc123", "live"), [])

    def test_missing_live_receipt_fails(self):
        (self.live / "_deploy.json").unlink()
        with self.assertRaises(SystemExit):
            check_live.check(self.origin, self.docs, "abc123", "live")

    def test_receipt_is_not_hashed_as_a_page(self):
        paths = check_live.local_files(self.docs)
        self.assertNotIn(self.docs / "_deploy.json", paths)


if __name__ == "__main__":
    unittest.main()
