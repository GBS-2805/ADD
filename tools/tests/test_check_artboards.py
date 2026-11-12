import tempfile
import unittest
from pathlib import Path

from tools import check_artboards


class PhoneIconDeterminismTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.first = Path(self.tmp.name) / "first"
        self.second = Path(self.tmp.name) / "second"
        self.first.mkdir()
        self.second.mkdir()
        for name in check_artboards.PHONE_ICONS:
            (self.first / name).write_bytes(b"<svg>stable</svg>\n")
            (self.second / name).write_bytes(b"<svg>stable</svg>\n")

    def tearDown(self):
        self.tmp.cleanup()

    def test_identical_exports_are_accepted(self):
        self.assertEqual(check_artboards.unstable_boards(self.first, self.second), [])

    def test_one_changed_phone_board_is_reported(self):
        name = check_artboards.PHONE_ICONS[1]
        (self.second / name).write_bytes(b"<svg>shifted 1.95px</svg>\n")
        self.assertEqual(check_artboards.unstable_boards(self.first, self.second), [name])

    def test_missing_second_board_is_reported(self):
        name = check_artboards.PHONE_ICONS[2]
        (self.second / name).unlink()
        self.assertEqual(check_artboards.unstable_boards(self.first, self.second), [name])


if __name__ == "__main__":
    unittest.main()
