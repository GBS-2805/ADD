import unittest
from pathlib import Path


class WorkflowContractTest(unittest.TestCase):
    def setUp(self):
        self.text = Path(".github/workflows/checks.yml").read_text(encoding="utf-8")

    def test_publish_waits_for_both_checks(self):
        self.assertIn("needs: [shelf, visual]", self.text)

    def test_publish_is_manual_dispatch_to_main_only(self):
        self.assertIn("workflow_dispatch:", self.text)
        jobs = self.text.split("\n  publish:", 1)[1].split("\n  handoff:", 1)[0]
        condition = jobs.split("runs-on:", 1)[0]
        self.assertIn("github.event_name == 'workflow_dispatch'", condition)
        self.assertIn("github.ref == 'refs/heads/main'", condition)
        self.assertNotIn("github.event_name == 'push'", condition)

    def test_publish_uses_production_environment(self):
        self.assertIn("environment: production", self.text)

    def test_publish_does_not_share_a_cancelling_lock(self):
        self.assertIn("group: publish-production", self.text)
        self.assertIn("cancel-in-progress: false", self.text)

    def test_merges_do_not_publish(self):
        """Landing checked work must never deploy it; publishing is a separate act."""
        jobs = self.text.split("\n  publish:", 1)[1].split("\n  handoff:", 1)[0]
        condition = jobs.split("runs-on:", 1)[0]
        self.assertNotIn("head_commit.message", condition)
        self.assertNotIn("[skip publish]", self.text)
        self.assertNotIn("github.event_name == 'push'", condition)

    def test_handoff_still_follows_publish(self):
        """The handoff must not be able to run without a publish."""
        handoff = self.text.split("\n  handoff:", 1)[1]
        condition = handoff.split("runs-on:", 1)[0]
        self.assertIn("needs: publish", condition)
        self.assertIn("github.event_name == 'workflow_dispatch'", condition)

    def test_token_is_scoped_to_the_deploy_step(self):
        self.assertIn("VERCEL_ARTBASE_TOKEN: ${{ secrets.VERCEL_ARTBASE_TOKEN }}", self.text)
        self.assertNotIn("pull_request_target", self.text)


if __name__ == "__main__":
    unittest.main()
