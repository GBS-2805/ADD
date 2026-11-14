import tempfile
import unittest
from pathlib import Path

from tools import check_artboards


HOVER_STYLE = ('font-family="Poppins" font-size="9.5" font-weight="400" '
               'letter-spacing="1.14" fill="#5f5550"')


def label(text, x, y):
    return '<text x="%s" y="%s" %s>%s</text>' % (x, y, HOVER_STYLE, text)


class SearchPlaceholderTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.boards = Path(self.tmp.name)
        for name, attrs in check_artboards.SEARCH_PLACEHOLDERS.items():
            self.write(name, attrs)

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, name, attrs):
        style = " ".join('%s="%s"' % item for item in attrs.items())
        (self.boards / name).write_text(
            '<svg><text %s>Search by name</text></svg>\n' % style, encoding="utf-8")

    def test_committed_icon_boards_keep_the_placeholder(self):
        self.assertEqual(check_artboards.placeholder_problems(check_artboards.BOARDS), [])

    def test_all_icon_boards_with_one_placeholder_are_accepted(self):
        self.assertEqual(check_artboards.placeholder_problems(self.boards), [])

    def test_board_without_the_placeholder_is_reported(self):
        name = check_artboards.SEARCH_BOARDS[2]
        (self.boards / name).write_text("<svg></svg>\n", encoding="utf-8")
        self.assertEqual(check_artboards.placeholder_problems(self.boards),
                         [(name, "0 copies")])

    def test_duplicated_placeholder_is_reported(self):
        name = check_artboards.SEARCH_BOARDS[-1]
        (self.boards / name).write_text(
            '<svg><text>Search by name</text><text>Search by name</text></svg>\n',
            encoding="utf-8")
        self.assertEqual(check_artboards.placeholder_problems(self.boards),
                         [(name, "2 copies")])

    def test_moved_placeholder_is_reported(self):
        name = check_artboards.SEARCH_BOARDS[0]
        attrs = dict(check_artboards.SEARCH_PLACEHOLDERS[name])
        attrs["x"] = "0"
        self.write(name, attrs)
        problems = check_artboards.placeholder_problems(self.boards)
        self.assertEqual(len(problems), 1)
        self.assertEqual(problems[0][0], name)
        self.assertIn("x='0' (want '15')", problems[0][1])

    def test_wrong_placeholder_font_is_reported(self):
        name = check_artboards.SEARCH_BOARDS[-1]
        attrs = dict(check_artboards.SEARCH_PLACEHOLDERS[name])
        attrs["font-family"] = "Poppins"
        self.write(name, attrs)
        problems = check_artboards.placeholder_problems(self.boards)
        self.assertEqual(len(problems), 1)
        self.assertEqual(problems[0][0], name)
        self.assertIn("font-family='Poppins' (want 'Spectral')", problems[0][1])


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


class BoardMatchTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def board(self, name, x, text="ICONS", rect="<rect x=\"1\" y=\"2\" width=\"3\" height=\"4\"/>"):
        path = self.dir / name
        path.write_text(
            '<svg>%s<text x="%s" y="10" font-family="Poppins" font-size="12" '
            'font-weight="400">%s</text></svg>\n' % (rect, x, text), encoding="utf-8")
        return path

    def test_identical_board_matches(self):
        self.assertTrue(check_artboards.boards_match(self.board("a", "10"), self.board("b", "10")))

    def test_subpixel_text_shift_is_accepted(self):
        self.assertTrue(check_artboards.boards_match(self.board("a", "10.22"), self.board("b", "10")))

    def test_two_pixel_text_shift_is_accepted(self):
        self.assertTrue(check_artboards.boards_match(self.board("a", "12.11"), self.board("b", "10")))

    def test_text_shift_beyond_tolerance_is_reported(self):
        self.assertFalse(check_artboards.boards_match(self.board("a", "12.6"), self.board("b", "10")))

    def test_changed_text_is_reported(self):
        self.assertFalse(check_artboards.boards_match(
            self.board("a", "10"), self.board("b", "10", text="TYPE")))

    def test_changed_non_text_layout_is_reported(self):
        self.assertFalse(check_artboards.boards_match(
            self.board("a", "10"),
            self.board("b", "10", rect="<rect x=\"2\" y=\"2\" width=\"3\" height=\"4\"/>")))


if __name__ == "__main__":
    unittest.main()
