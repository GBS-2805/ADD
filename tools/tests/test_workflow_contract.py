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

    def test_token_is_scoped_to_the_deploy_step(self):
        self.assertIn("VERCEL_ARTBASE_TOKEN: ${{ secrets.VERCEL_ARTBASE_TOKEN }}", self.text)
        self.assertNotIn("pull_request_target", self.text)


if __name__ == "__main__":
    unittest.main()
