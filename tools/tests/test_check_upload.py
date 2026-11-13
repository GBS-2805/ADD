import tempfile
import unittest
from pathlib import Path

from tools import check_upload


class UploadLimitTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.docs = Path(self.tmp.name) / "docs"
        self.docs.mkdir()
        (self.docs / "page.html").write_bytes(b"1234567890")

    def tearDown(self):
        self.tmp.cleanup()

    def test_measure_counts_bytes_and_files(self):
        (self.docs / "assets").mkdir()
        (self.docs / "assets" / "site.css").write_bytes(b"12345")
        self.assertEqual(check_upload.measure(self.docs), (15, 2))

    def test_exact_byte_limit_is_allowed(self):
        self.assertEqual(check_upload.problems(self.docs, max_bytes=10, max_files=1), [])

    def test_one_byte_over_limit_fails(self):
        problems = check_upload.problems(self.docs, max_bytes=9, max_files=1)
        self.assertTrue(any("over the" in problem and "upload limit" in problem
                            for problem in problems))

    def test_exact_file_limit_is_allowed(self):
        self.assertEqual(check_upload.problems(self.docs, max_bytes=10, max_files=1), [])

    def test_one_file_over_limit_fails(self):
        (self.docs / "extra.txt").write_bytes(b"")
        problems = check_upload.problems(self.docs, max_bytes=10, max_files=1)
        self.assertTrue(any("over the 1-file upload limit" in problem for problem in problems))

    def test_missing_docs_is_reported(self):
        self.assertEqual(check_upload.problems(self.docs / "missing"),
                         ["docs/ is not a directory"])


if __name__ == "__main__":
    unittest.main()
