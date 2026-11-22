from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools import check_workflows

SHA = "eef61447b9ff4aafe5dcd4e0bbf5d482be7e7871"


def workflow_text(*, ref: str = SHA, permissions: str = "contents: read",
                  persist: bool = True, publish_if: str = "github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main'",
                  review_secret: str = "") -> str:
    return f"""name: test

on:
  push:

permissions:
  {permissions}

jobs:
  shelf:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@{ref}
        with:
          fetch-depth: 0
          persist-credentials: {'false' if persist else 'true'}
  publish:
    if: {publish_if}
    runs-on: ubuntu-latest
    steps:
      - run: echo publish
  review:
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-latest
    steps:
      - run: echo {review_secret}
"""


class WorkflowTests(unittest.TestCase):
    def check(self, text: str) -> list[str]:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / ".github" / "workflows").mkdir(parents=True)
            (root / ".github" / "workflows" / "checks.yml").write_text(
                text, encoding="utf-8")
            return check_workflows.problems(root)

    def test_safe_workflow_passes(self):
        self.assertEqual(self.check(workflow_text()), [])

    def test_unpinned_action_fails(self):
        found = self.check(workflow_text(ref="v4"))
        self.assertTrue(any("not pinned" in item for item in found), found)

    def test_pull_request_target_fails(self):
        found = self.check(workflow_text() + "\n# pull_request_target\n")
        self.assertTrue(any("pull_request_target" in item for item in found), found)

    def test_top_level_write_permissions_fail(self):
        found = self.check(workflow_text(permissions="contents: write"))
        self.assertTrue(any("top-level permissions" in item for item in found), found)

    def test_persisted_checkout_credentials_fail(self):
        found = self.check(workflow_text(persist=False))
        self.assertTrue(any("persist-credentials" in item for item in found), found)

    def test_production_secret_in_review_fails(self):
        found = self.check(workflow_text(review_secret="${{ secrets.VERCEL_ADMIN_TOKEN }}"))
        self.assertTrue(any("production secret" in item for item in found), found)

    def test_publish_must_be_manual_dispatch_to_main(self):
        found = self.check(workflow_text(publish_if="github.event_name == 'pull_request'"))
        self.assertTrue(any("manual dispatch to main" in item for item in found), found)

    def test_publish_must_not_run_on_push(self):
        found = self.check(workflow_text(
            publish_if="github.event_name == 'push' && github.ref == 'refs/heads/main'"))
        self.assertTrue(any("must not run on push" in item for item in found), found)


if __name__ == "__main__":
    unittest.main()
