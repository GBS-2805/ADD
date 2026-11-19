import unittest
from pathlib import Path


class WorkflowContractTest(unittest.TestCase):
    def setUp(self):
        self.text = Path(".github/workflows/checks.yml").read_text(encoding="utf-8")

    def test_publish_waits_for_both_checks(self):
        self.assertIn("needs: [shelf, visual]", self.text)

    def test_publish_is_main_push_only(self):
        self.assertIn("github.event_name == 'push'", self.text)
        self.assertIn("github.ref == 'refs/heads/main'", self.text)

    def test_publish_uses_production_environment(self):
        self.assertIn("environment: production", self.text)

    def test_publish_does_not_share_a_cancelling_lock(self):
        self.assertIn("group: publish-production", self.text)
        self.assertIn("cancel-in-progress: false", self.text)

    def test_publish_can_be_skipped_without_blocking_the_merge(self):
        """A merge must be able to land without releasing the site.

        Without this, checking work on main and shipping it are the same action, so
        nobody can merge a small fix without deploying everything behind it.
        """
        self.assertIn("[skip publish]", self.text)
        # The marker must gate publish itself, not a step inside it.
        jobs = self.text.split("\n  publish:", 1)[1].split("\n  handoff:", 1)[0]
        condition = jobs.split("runs-on:", 1)[0]
        self.assertIn("head_commit.message", condition)
        self.assertIn("contains(", condition)

    def test_handoff_still_follows_publish(self):
        """The handoff must not be able to run without a publish."""
        handoff = self.text.split("\n  handoff:", 1)[1]
        self.assertIn("needs: publish", handoff.split("runs-on:", 1)[0])

    def test_token_is_scoped_to_the_deploy_step(self):
        self.assertIn("VERCEL_ARTBASE_TOKEN: ${{ secrets.VERCEL_ARTBASE_TOKEN }}", self.text)
        self.assertNotIn("pull_request_target", self.text)


if __name__ == "__main__":
    unittest.main()
