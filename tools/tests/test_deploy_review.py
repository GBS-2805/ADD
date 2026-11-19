import unittest
from unittest import mock

from tools import deploy_review


class HeaderGateTest(unittest.TestCase):
    def test_clean_handoff_headers_pass(self):
        with mock.patch.object(deploy_review, "header_problems", return_value=[]):
            self.assertEqual(deploy_review.headers_current("handoff"), 0)

    def test_missing_handoff_header_fails(self):
        with mock.patch.object(deploy_review, "header_problems",
                               return_value=["site: missing x-frame-options"]):
            self.assertEqual(deploy_review.headers_current("handoff"), 1)

    def test_other_targets_do_not_guess_an_origin(self):
        with mock.patch.object(deploy_review, "header_problems") as problems:
            self.assertEqual(deploy_review.headers_current("shots"), 0)
        problems.assert_not_called()


class StorageGateScopeTest(unittest.TestCase):
    """The strict storage gate must run only for projects the report covers.

    The `review` target deploys a throwaway project that is absent from the report,
    so a strict gate there could only ever fail. That bug turned every pull request
    red until it was found -- this test is what keeps it fixed.
    """

    def _run(self, target, gate=None):
        with mock.patch.object(deploy_review, "deploy_token", return_value="t"), \
             mock.patch.object(deploy_review, "storage_check") as storage, \
             mock.patch.object(deploy_review, "run_cli", return_value=(0, "")), \
             mock.patch.object(deploy_review, "header_problems", return_value=[]), \
             mock.patch("sys.argv", ["deploy_review.py", target]):
            try:
                deploy_review.main()
            except SystemExit as error:
                if error.code not in (0, None):
                    raise
        return storage

    def test_review_target_skips_the_strict_gate(self):
        storage = self._run("review")
        storage.assert_not_called()

    def test_handoff_target_still_runs_the_strict_gate(self):
        storage = self._run("handoff")
        storage.assert_called_once()
        self.assertIs(storage.call_args.kwargs.get("strict"), True)


if __name__ == "__main__":
    unittest.main()
