#!/usr/bin/env python3
"""Scan tracked and non-ignored files for high-confidence credentials.

The scan is deliberately narrow: it looks for token shapes that are almost
never documentation placeholders, because a noisy secret gate gets switched off.
It prints `path:line: kind` and never the value that matched.

    python3 tools/check_secrets.py --check
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKIP_DIRS = {".git", ".venv", "node_modules", "build", "dist", "artifacts",
             "__pycache__", ".gradle", ".idea"}
PATTERNS = [
    ("github-token", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,}\b")),
    ("github-fine-grained", re.compile(r"\bgithub_pat_[A-Za-z0-9_]{22,}\b")),
    ("google-api-key", re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b")),
    ("vercel-token", re.compile(r"\bvercel_[A-Za-z0-9]{24,}\b")),
    ("aws-access-key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("stripe-secret", re.compile(r"\bsk_(?:live|test)_[0-9A-Za-z]{16,}\b")),
    ("slack-token", re.compile(r"\bxox[baprs]-[0-9A-Za-z-]{10,}\b")),
    ("private-key", re.compile(
        r"-----BEGIN (?:RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----")),
]


def _paths(root: Path) -> list[Path]:
    """Tracked files plus non-ignored untracked files, never ignored scratch."""
    if (root / ".git").exists():
        try:
            tracked = subprocess.run(
                ["git", "ls-files", "-z"], cwd=root, check=True,
                capture_output=True, text=True).stdout.split("\0")
            untracked = subprocess.run(
                ["git", "ls-files", "--others", "--exclude-standard", "-z"],
                cwd=root, check=True, capture_output=True, text=True).stdout.split("\0")
            return [root / name for name in tracked + untracked if name]
        except (OSError, subprocess.SubprocessError):
            pass
    return [path for path in root.rglob("*")
            if path.is_file() and not any(part in SKIP_DIRS for part in path.parts)]


def scan_file(path: Path) -> list[tuple[int, str]]:
    try:
        data = path.read_bytes()
    except OSError:
        return []
    if b"\0" in data[:2048]:
        return []
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        return []
    found = []
    for number, line in enumerate(text.splitlines(), 1):
        for kind, pattern in PATTERNS:
            if pattern.search(line):
                found.append((number, kind))
    return found


def scan(root: Path = ROOT) -> tuple[list[str], int]:
    root = Path(root)
    problems = []
    files = 0
    for path in _paths(root):
        if not path.is_file() or any(part in SKIP_DIRS for part in path.parts):
            continue
        files += 1
        for line, kind in scan_file(path):
            try:
                shown = path.relative_to(root).as_posix()
            except ValueError:
                shown = str(path)
            problems.append("%s:%d: %s" % (shown, line, kind))
    return problems, files


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="read-only check (default)")
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)

    found, files = scan(args.root)
    if found:
        for item in found:
            print("  " + item)
        print("secrets: %d problem(s) in %d files" % (len(found), files))
        return 1
    print("secrets: OK - %d files, no credential-shaped values" % files)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
