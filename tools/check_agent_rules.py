#!/usr/bin/env python3
"""Fail when the agent contract goes stale.

    python3 tools/check_agent_rules.py --check
    python3 tools/check_agent_rules.py --explain

Policy lives in `AGENTS.md`, mechanics in `docs/agent-reference.md`. This gate checks
the required headings and order, the commands and public links the contract promises,
the 260-line ceiling, stale legacy phrases and local paths written in backticks. It
never reads a network resource and never edits a file.
"""

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
AGENTS = ROOT / "AGENTS.md"
REFERENCE = ROOT / "docs" / "agent-reference.md"
CLAUDE = ROOT / "CLAUDE.md"
CHANGELOG = ROOT / "docs" / "agent-rules-changelog.md"
MAX_LINES = 260
HEADINGS = [
    "Mission and current state",
    "Work loop",
    "Skills",
    "Scope",
    "Delivery matrix",
    "Verification",
    "Publish",
    "Visual review",
    "Handoff",
    "Never",
    "Where to look next",
]
REQUIRED = [
    "python3 tools/check_all.py --visual",
    "python3 tools/deploy_vercel.py",
    "https://artbase-olive.vercel.app/",
    "https://artbase-handoff.vercel.app/",
    "whole matching skill",
    "AGENTS.md wins",
]
FORBIDDEN = [
    "four palettes",
    "all eight themes",
    "RENDERS_THEME=light",
    "the gallery follows the page's own mode, which is dark",
    "artifacts/handoff/index.html",
    "vercel@latest",
]
ROOTS = ("docs/", "tools/", "app/", "figma/", "handoff/", ".github/", "artifacts/")
BAD_TOKEN = re.compile(r"[\s<>*?$(){}]")
BACKTICK = re.compile(r"`([^`]+)`")
IGNORED = {
    "url": "http(s) URL",
    "command": "shell command or flag",
    "glob": "glob, placeholder or generated name",
    "identifier": "code identifier or filename",
    "outside": "path outside the checked roots",
}


def read(path):
    try:
        return path.read_text(encoding="utf-8")
    except OSError as exc:
        raise SystemExit("check_agent_rules: cannot read %s: %s" % (path, exc))


def classify(token):
    text = token.strip()
    if text.startswith(("http://", "https://")):
        return "url"
    if text.startswith(("-", "$", "python", "node", "bash", "npm", "npx", "git", "gh")):
        return "command"
    if BAD_TOKEN.search(text) or text.endswith("/"):
        return "glob"
    if text.startswith(ROOTS):
        return "path"
    if "/" in text or "." in text:
        return "identifier"
    return "outside"


def local_paths(text):
    found = []
    for match in BACKTICK.finditer(text):
        token = match.group(1).strip()
        if classify(token) == "path":
            found.append(token.rstrip(".,;:"))
    return found


def heading_positions(text):
    positions = {}
    offset = 0
    for line in text.splitlines(keepends=True):
        match = re.match(r"^##\s+(.+?)\s*$", line)
        if match:
            positions.setdefault(match.group(1), offset)
        offset += len(line)
    return positions


def check(text, reference_text, claude_text, root=ROOT):
    errors = []
    lines = text.count("\n") + (0 if text.endswith("\n") else 1)
    if lines > MAX_LINES:
        errors.append("AGENTS.md is %d lines; the ceiling is %d" % (lines, MAX_LINES))
    positions = heading_positions(text)
    missing = [name for name in HEADINGS if name not in positions]
    if missing:
        errors.append("missing headings: " + ", ".join(missing))
    ordered = [name for name in HEADINGS if name in positions]
    actual = sorted(ordered, key=lambda name: positions[name])
    if ordered != actual:
        errors.append("headings are out of order")
    for required in REQUIRED:
        if required not in text:
            errors.append("AGENTS.md is missing %r" % required)
    for phrase in FORBIDDEN:
        for label, body in (("AGENTS.md", text), ("docs/agent-reference.md", reference_text)):
            if phrase in body:
                errors.append("%s still contains stale phrase %r" % (label, phrase))
    for token in local_paths(text) + local_paths(reference_text):
        if not (root / token).exists():
            errors.append("referenced path does not exist: %s" % token)
    for path in (root / "handoff", root / "docs" / "agent-reference.md",
                 root / "docs" / "agent-rules-examples.md", root / "CLAUDE.md"):
        if not path.exists():
            errors.append("required rules file is missing: %s" % path.relative_to(root))
    if "whole matching skill" not in text or "AGENTS.md wins" not in text:
        errors.append("the skills policy must require the whole matching skill and AGENTS.md precedence")
    if "AGENTS.md" not in claude_text or claude_text.count("\n") >= 8:
        errors.append("CLAUDE.md must point at AGENTS.md and stay under 8 lines")
    if "## Changing these rules" not in text:
        errors.append("AGENTS.md has no change protocol")
    if not CHANGELOG.exists() or not re.search(r"^- \d{4}-\d{2}-\d{2}:",
                                               read(CHANGELOG), re.M):
        errors.append("docs/agent-rules-changelog.md needs a dated entry")
    return errors


def explain(text):
    counts = {}
    for match in BACKTICK.finditer(text):
        token = match.group(1).strip()
        kind = classify(token)
        counts[kind] = counts.get(kind, 0) + 1
        print("%-10s %s" % (kind, token))
    print("\nignored classes:")
    for kind, label in IGNORED.items():
        print("  %-10s %s (%d)" % (kind, label, counts.get(kind, 0)))


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="fail on a stale contract")
    parser.add_argument("--explain", action="store_true", help="print token classification")
    args = parser.parse_args()
    text = read(AGENTS)
    reference = read(REFERENCE)
    claude = read(CLAUDE)
    if args.explain:
        explain(text + "\n" + reference)
    if not args.check and not args.explain:
        parser.error("use --check or --explain")
    errors = check(text, reference, claude)
    if errors:
        for error in errors:
            print("  " + error)
        print("rules: %d problem(s)" % len(errors))
        return 1
    print("rules: OK - %d-line contract, %d headings, no stale paths or phrases"
          % (text.count("\n"), len(HEADINGS)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
