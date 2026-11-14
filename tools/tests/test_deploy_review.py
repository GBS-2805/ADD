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


if __name__ == "__main__":
    unittest.main()
