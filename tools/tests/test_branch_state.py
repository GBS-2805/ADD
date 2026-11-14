import subprocess
import tempfile
import unittest
from pathlib import Path

from tools import check_branch_state as guard


def run(root, *args):
    subprocess.run(["git", *args], cwd=str(root), check=True,
                   capture_output=True, text=True)


class BranchStateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        run(self.root, "init", "-b", "main")
        run(self.root, "config", "user.email", "test@example.test")
        run(self.root, "config", "user.name", "Test")
        (self.root / "a.txt").write_text("one\n", encoding="utf-8")
        run(self.root, "add", "a.txt")
        run(self.root, "commit", "-m", "one")
        self.base = subprocess.check_output(["git", "rev-parse", "HEAD"],
                                            cwd=self.root, text=True).strip()
        run(self.root, "update-ref", "refs/remotes/origin/main", self.base)

    def tearDown(self):
        self.tmp.cleanup()

    def fetch(self, pulls):
        def fake(repo, owner, branch, token):
            return pulls
        return fake

    def test_merged_pr_with_head_in_base_passes(self):
        code, message = guard.check(self.root, "o/r", "feature", "main", "",
                                    self.fetch([{"number": 2, "merged_at": "yes"}]))
        self.assertEqual(code, 0)
        self.assertIn("already in", message)

    def test_merged_pr_one_commit_ahead_fails(self):
        (self.root / "b.txt").write_text("two\n", encoding="utf-8")
        run(self.root, "add", "b.txt")
        run(self.root, "commit", "-m", "two")
        code, message = guard.check(self.root, "o/r", "feature", "main", "",
                                    self.fetch([{"number": 2, "merged_at": "yes"}]))
        self.assertEqual(code, 1)
        self.assertIn("Create a new branch", message)
        self.assertIn("HEAD", message)

    def test_open_pr_passes(self):
        code, _ = guard.check(self.root, "o/r", "feature", "main", "",
                              self.fetch([{"number": 3, "merged_at": None}]))
        self.assertEqual(code, 0)

    def test_no_pr_passes(self):
        code, _ = guard.check(self.root, "o/r", "feature", "main", "", self.fetch([]))
        self.assertEqual(code, 0)

    def test_missing_token_on_private_repo_is_actionable(self):
        import urllib.error

        def denied(request, timeout=30):
            raise urllib.error.HTTPError(request.full_url, 404, "Not Found", {}, None)

        with self.assertRaises(guard.GuardError) as raised:
            guard.api_pulls("o/private", "o", "feature", "", denied)
        self.assertIn("GITHUB_TOKEN", str(raised.exception))

    def test_missing_base_ref_is_actionable(self):
        with self.assertRaises(guard.GuardError) as raised:
            guard.check(self.root, "o/r", "feature", "missing", "",
                        self.fetch([{"number": 2, "merged_at": "yes"}]))
        self.assertIn("fetch the base branch", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
