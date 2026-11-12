import unittest
from pathlib import Path

from tools import check_agent_rules as lint

ROOT = Path(__file__).resolve().parents[2]
AGENTS = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
REFERENCE = (ROOT / "docs" / "agent-reference.md").read_text(encoding="utf-8")
CLAUDE = (ROOT / "CLAUDE.md").read_text(encoding="utf-8")


class AgentRulesLintTest(unittest.TestCase):
    def test_current_rules_pass(self):
        self.assertEqual(lint.check(AGENTS, REFERENCE, CLAUDE), [])

    def test_missing_required_heading_fails(self):
        errors = lint.check(AGENTS.replace("## Skills\n", "## Methods\n"), REFERENCE, CLAUDE)
        self.assertTrue(any("missing headings" in error for error in errors))

    def test_old_artifacts_handoff_path_fails(self):
        errors = lint.check(AGENTS + "\n`artifacts/handoff/index.html`\n", REFERENCE, CLAUDE)
        self.assertTrue(any("old" not in error and "artifacts/handoff" in error
                            for error in errors))

    def test_old_dark_theme_phrase_fails(self):
        errors = lint.check(AGENTS + "\nRENDERS_THEME=light\n", REFERENCE, CLAUDE)
        self.assertTrue(any("RENDERS_THEME" in error for error in errors))

    def test_unknown_local_path_fails(self):
        errors = lint.check(AGENTS + "\n`tools/not-a-real-file.py`\n", REFERENCE, CLAUDE)
        self.assertTrue(any("not-a-real-file.py" in error for error in errors))

    def test_url_glob_and_flag_are_not_paths(self):
        text = AGENTS + ("\n`https://example.test/x` `tools/*.py` `--visual`"
                         " `docs/<page>/index.html`\n")
        self.assertTrue(all("example.test" not in token and "*" not in token and
                            "<" not in token for token in lint.local_paths(text)))

    def test_agents_over_260_lines_fails(self):
        errors = lint.check(AGENTS + ("\n\n" * 300), REFERENCE, CLAUDE)
        self.assertTrue(any("ceiling" in error for error in errors))

    def test_skills_rule_requires_whole_skill_and_precedence(self):
        text = AGENTS.replace("Read the whole matching skill before acting", "Read a skill")
        errors = lint.check(text, REFERENCE, CLAUDE)
        self.assertTrue(any("whole matching skill" in error for error in errors))

    def test_claude_must_point_at_agents(self):
        errors = lint.check(AGENTS, REFERENCE, "# empty\n")
        self.assertTrue(any("CLAUDE.md" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
