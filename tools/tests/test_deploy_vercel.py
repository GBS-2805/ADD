import unittest
from unittest import mock

from tools import deploy_vercel


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


if __name__ == "__main__":
    unittest.main()
