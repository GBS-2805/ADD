from __future__ import annotations

import shutil
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class WebPerfSmokeTest(unittest.TestCase):
    def test_script_parses(self):
        if not shutil.which("node"):
            self.skipTest("node is not installed")
        done = subprocess.run(["node", "--check", "tools/check_web_perf.js"],
                              cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(done.returncode, 0, done.stderr)


if __name__ == "__main__":
    unittest.main()
