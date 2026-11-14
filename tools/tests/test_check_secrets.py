from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools import check_secrets


class SecretsTests(unittest.TestCase):
    def test_token_is_reported_without_its_value(self):
        fake = "ghp_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8"
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "settings.py").write_text('TOKEN = "%s"\n' % fake, encoding="utf-8")
            found, files = check_secrets.scan(root)
            self.assertEqual(files, 1)
            self.assertEqual(found, ["settings.py:1: github-token"])
            self.assertNotIn(fake, "\n".join(found))

    def test_private_key_header_is_flagged(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            header = "-----BEGIN " + "PRIVATE KEY-----"
            (root / "key.pem").write_text(
                header + "\nnot-a-real-key\n", encoding="utf-8")
            found, _ = check_secrets.scan(root)
            self.assertEqual(found, ["key.pem:1: private-key"])

    def test_binary_and_safe_files_are_skipped(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "blob.bin").write_bytes(b"\0" + b"ghp_" + b"A" * 36)
            (root / "README.md").write_text("No credentials here.\n", encoding="utf-8")
            found, _ = check_secrets.scan(root)
            self.assertEqual(found, [])

    def test_current_tree_is_clean(self):
        found, _ = check_secrets.scan(check_secrets.ROOT)
        self.assertEqual(found, [])


if __name__ == "__main__":
    unittest.main()
