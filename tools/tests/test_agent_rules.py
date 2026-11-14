import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class AgentRulesShapeTest(unittest.TestCase):
    def setUp(self):
        self.text = (ROOT / "AGENTS.md").read_text(encoding="utf-8")

    def test_contract_is_short_and_has_required_headings_in_order(self):
        self.assertLessEqual(len(self.text.splitlines()), 160)
        headings = re.findall(r"^##\s+(.+)$", self.text, re.M)
        wanted = ["Mission and current state", "Work loop", "Skills", "Scope",
                  "Delivery matrix", "Verification", "Publish", "Visual review",
                  "Handoff", "Never", "Where to look next"]
        self.assertEqual([h for h in headings if h in wanted], wanted)

    def test_contract_names_the_public_commands_and_hosts(self):
        for piece in ("python3 tools/check_all.py --visual",
                      "python3 tools/deploy_vercel.py",
                      "https://artbase-olive.vercel.app/",
                      "https://artbase-handoff.vercel.app/"):
            self.assertIn(piece, self.text)

    def test_skills_policy_requires_the_whole_skill_and_precedence(self):
        self.assertIn("## Skills", self.text)
        self.assertIn("whole matching skill", self.text)
        self.assertIn("AGENTS.md wins", self.text)

    def test_visual_review_names_the_phone(self):
        section = self.text.split("## Visual review", 1)[1].split("## Handoff", 1)[0]
        self.assertIn("phone", section.lower())

    def test_claude_points_at_agents(self):
        claude = (ROOT / "CLAUDE.md").read_text(encoding="utf-8")
        self.assertIn("AGENTS.md", claude)
        self.assertLess(len(claude.splitlines()), 8)

    def test_delivery_matrix_rows_name_a_check_and_a_link_or_none(self):
        rows = [line for line in self.text.splitlines()
                if line.startswith("| ") and not line.startswith("|---")]
        self.assertGreaterEqual(len(rows), 5)
        for row in rows[1:]:
            lower = row.lower()
            self.assertTrue(any(word in lower for word in
                                ("check", "build", "parity", "receipt")), row)
            self.assertTrue(any(word in lower for word in
                                ("none", "http", "site", "handoff", "apk")), row)

    def test_examples_cover_each_row_and_changelog_is_dated(self):
        examples = (ROOT / "docs" / "agent-rules-examples.md").read_text(encoding="utf-8")
        for word in ("Copy-only", "Visual page", "APK", "Deploy-only", "Skill-assisted"):
            self.assertIn(word, examples)
        changelog = (ROOT / "docs" / "agent-rules-changelog.md").read_text(encoding="utf-8")
        self.assertRegex(changelog, r"(?m)^- \d{4}-\d{2}-\d{2}:")


if __name__ == "__main__":
    unittest.main()
