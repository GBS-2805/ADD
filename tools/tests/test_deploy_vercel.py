import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools import deploy_vercel


class HeaderGateTest(unittest.TestCase):
    def test_clean_headers_pass(self):
        with mock.patch.object(deploy_vercel, "live_header_problems", return_value=[]):
            self.assertIsNone(deploy_vercel.headers_current("https://example.test"))

    def test_missing_header_stops_and_names_the_problem(self):
        with mock.patch.object(deploy_vercel, "live_header_problems",
                               return_value=["site: missing x-frame-options"]):
            with self.assertRaises(SystemExit) as stopped:
                deploy_vercel.headers_current("https://example.test")
        self.assertIn("x-frame-options", str(stopped.exception))


class UploadGateTest(unittest.TestCase):
    def test_clean_upload_passes(self):
        with mock.patch.object(deploy_vercel, "upload_problems", return_value=[]):
            self.assertIsNone(deploy_vercel.upload_gate())

    def test_oversized_upload_stops_before_deploy(self):
        with mock.patch.object(deploy_vercel, "upload_problems",
                               return_value=["docs/ is over the limit"]):
            with self.assertRaises(SystemExit) as stopped:
                deploy_vercel.upload_gate()
        self.assertIn("docs/ is over the limit", str(stopped.exception))

    def test_receipt_then_gate_then_vercel(self):
        events = []

        def receipt(_sha):
            events.append("receipt")

        def gate():
            events.append("gate")

        def run(*_args, **_kwargs):
            events.append("vercel")
            return 0, "Aliased https://artbase-test.vercel.app\n"

        with mock.patch.object(deploy_vercel, "deploy_token", return_value="token"), \
             mock.patch.object(deploy_vercel, "commit", return_value="abc123"), \
             mock.patch.object(deploy_vercel, "storage_gate"), \
             mock.patch.object(deploy_vercel, "palette_current"), \
             mock.patch.object(deploy_vercel, "assets_current"), \
             mock.patch.object(deploy_vercel, "links_current"), \
             mock.patch.object(deploy_vercel, "refresh_history"), \
             mock.patch.object(deploy_vercel, "write_receipt", side_effect=receipt), \
             mock.patch.object(deploy_vercel, "upload_gate", side_effect=gate), \
             mock.patch.object(deploy_vercel, "run_deploy_cli", side_effect=run):
            deploy_vercel.deploy()
        self.assertEqual(events, ["receipt", "gate", "vercel"])

    def test_failed_gate_removes_the_receipt_and_skips_vercel(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs").mkdir()

            def receipt(_sha):
                (root / "docs/_deploy.json").write_text("receipt\n", encoding="utf-8")

            def gate():
                raise SystemExit("over the limit")

            with mock.patch.object(deploy_vercel, "ROOT", root), \
                 mock.patch.object(deploy_vercel, "commit", return_value="abc123"), \
                 mock.patch.object(deploy_vercel, "deploy_token", return_value="token"), \
                 mock.patch.object(deploy_vercel, "storage_gate"), \
                 mock.patch.object(deploy_vercel, "palette_current"), \
                 mock.patch.object(deploy_vercel, "assets_current"), \
                 mock.patch.object(deploy_vercel, "links_current"), \
                 mock.patch.object(deploy_vercel, "refresh_history"), \
                 mock.patch.object(deploy_vercel, "write_receipt", side_effect=receipt), \
                 mock.patch.object(deploy_vercel, "upload_gate", side_effect=gate), \
                 mock.patch.object(deploy_vercel, "run_deploy_cli") as run:
                with self.assertRaises(SystemExit):
                    deploy_vercel.deploy()
            self.assertFalse((root / "docs/_deploy.json").exists())
            run.assert_not_called()


class DeployCliTest(unittest.TestCase):
    def test_a_stalled_upload_stops_with_a_named_failure(self):
        with mock.patch.object(deploy_vercel, "run_cli",
                               side_effect=deploy_vercel.CliTimeout(600, "last line")):
            with self.assertRaises(SystemExit) as stopped:
                deploy_vercel.run_deploy_cli(["vercel"], {})
        self.assertIn("stopped responding", str(stopped.exception))
        self.assertIn("last line", str(stopped.exception))


if __name__ == "__main__":
    unittest.main()
