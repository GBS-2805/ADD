import tempfile
import unittest
from pathlib import Path

from tools import check_artboards


HOVER_STYLE = ('font-family="Poppins" font-size="9.5" font-weight="400" '
               'letter-spacing="1.14" fill="#5f5550"')


def label(text, x, y):
    return '<text x="%s" y="%s" %s>%s</text>' % (x, y, HOVER_STYLE, text)


class HiddenHoverTwinTest(unittest.TestCase):
    def test_old_clipped_twin_pattern_is_reported(self):
        svg = "\n".join([label("ICONS", 0, 142.55), label("ICONS", 0, 155.61)])
        self.assertEqual(check_artboards.hover_twins(svg),
                         [("ICONS", 142.55, 155.61)])

    def test_single_visible_label_is_accepted(self):
        svg = "\n".join([label("ICONS", 0, 142.55), label("FONTS", 0, 191.05)])
        self.assertEqual(check_artboards.hover_twins(svg), [])

    def test_same_label_at_another_x_is_accepted(self):
        svg = "\n".join([label("ICONS", 0, 142.55), label("ICONS", 60, 155.61)])
        self.assertEqual(check_artboards.hover_twins(svg), [])

    def test_same_label_far_below_is_accepted(self):
        svg = "\n".join([label("ICONS", 0, 142.55), label("ICONS", 0, 191.05)])
        self.assertEqual(check_artboards.hover_twins(svg), [])


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
