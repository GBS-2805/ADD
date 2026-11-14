#!/usr/bin/env python3
"""Reject floating dependency versions and an unverified Gradle wrapper.

    python3 tools/check_dependencies.py --check
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERCEL = "65.14.0"
PLAYWRIGHT = "1.65.0"
GRADLE_SHA = "d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"
GRADLE_FILES = ("*.gradle", "*.gradle.kts")


def _version_refs(text: str, tool: str) -> list[str]:
    return re.findall(r"\b%s@([^\s\"'\\]+)" % re.escape(tool), text)


def problems(root: Path = ROOT) -> list[str]:
    root = Path(root)
    found = []

    deploy_files = sorted((root / "tools").glob("deploy_*.py"))
    if not deploy_files:
        found.append("%s: no deploy scripts to check" % (root / "tools"))
    for path in deploy_files:
        try:
            text = path.read_text(encoding="utf-8")
        except OSError as error:
            found.append("%s: cannot read (%s)" % (path, error))
            continue
        refs = _version_refs(text, "vercel")
        for ref in refs:
            if ref != VERCEL:
                found.append("%s: vercel@%s, expected vercel@%s"
                             % (path.relative_to(root), ref, VERCEL))

    workflows = sorted((root / ".github" / "workflows").glob("*.yml"))
    workflow_text = ""
    for path in workflows:
        workflow_text += path.read_text(encoding="utf-8")
    refs = _version_refs(workflow_text, "playwright")
    if not refs:
        found.append("%s: no pinned playwright version" % (root / ".github/workflows"))
    for ref in refs:
        if ref != PLAYWRIGHT:
            found.append("%s: playwright@%s, expected playwright@%s"
                         % (root / ".github/workflows", ref, PLAYWRIGHT))

    wrapper = root / "tools" / "engine-spike" / "gradle" / "wrapper" / "gradle-wrapper.properties"
    try:
        wrapper_text = wrapper.read_text(encoding="utf-8")
    except OSError as error:
        found.append("%s: cannot read (%s)" % (wrapper, error))
    else:
        match = re.search(r"(?m)^distributionSha256Sum=([0-9a-f]{64})\s*$", wrapper_text)
        if not match:
            found.append("%s: missing distributionSha256Sum" % wrapper)
        elif match.group(1) != GRADLE_SHA:
            found.append("%s: distributionSha256Sum does not match Gradle 8.9"
                         % wrapper)

    for path in sorted((root / "tools" / "engine-spike").rglob("*.gradle*")):
        if not path.is_file():
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        for number, line in enumerate(text.splitlines(), 1):
            if ("latest.release" in line or "latest.integration" in line
                    or "SNAPSHOT" in line
                    or re.search(r"['\"][^'\"]+:[^'\"]+:[^'\"]*\+['\"]", line)):
                found.append("%s:%d: floating dependency version"
                             % (path.relative_to(root), number))
    return found


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="read-only check (default)")
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)

    found = problems(args.root)
    if found:
        for item in found:
            print("  " + item)
        print("dependencies: %d problem(s)" % len(found))
        return 1
    print("dependencies: OK - Vercel %s, Playwright %s, Gradle wrapper checksummed"
          % (VERCEL, PLAYWRIGHT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
