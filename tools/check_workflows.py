#!/usr/bin/env python3
"""Check GitHub Actions supply-chain and privilege rules without PyYAML.

The checks are intentionally line-based so the fast gate stays standard-library
only. They cover the policy the repository relies on: full-SHA action pins,
least-privilege top-level permissions, no `pull_request_target`, production
secrets kept out of pull-request jobs, publish/handoff restricted to a manual
dispatch on main, and checkout credentials not persisted where no push happens.

    python3 tools/check_workflows.py --check
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SHA = re.compile(r"^[0-9a-f]{40}$")
USES = re.compile(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)", re.MULTILINE)
PRODUCTION_SECRETS = ("VERCEL_ADMIN_TOKEN", "VERCEL_ARTBASE_TOKEN",
                      "VERCEL_HANDOFF_TOKEN")


def _jobs(text: str) -> dict[str, str]:
    lines = text.splitlines()
    try:
        start = next(i for i, line in enumerate(lines) if line.strip() == "jobs:")
    except StopIteration:
        return {}
    jobs = {}
    current = None
    body = []
    for line in lines[start + 1:]:
        match = re.match(r"^  ([A-Za-z0-9_-]+):\s*$", line)
        if match:
            if current:
                jobs[current] = "\n".join(body)
            current = match.group(1)
            body = []
        elif current:
            body.append(line)
    if current:
        jobs[current] = "\n".join(body)
    return jobs


def _checkout_steps(text: str) -> list[tuple[str, str]]:
    """Return (reference, step body) for every checkout use."""
    steps = []
    for match in re.finditer(r"(?ms)^\s*-\s+uses:\s+actions/checkout@([^\s#]+)(.*?)(?=^\s*-\s+|\Z)",
                             text):
        steps.append((match.group(1), match.group(2)))
    return steps


def problems(root: Path = ROOT) -> list[str]:
    root = Path(root)
    workflows = root / ".github" / "workflows"
    files = sorted(workflows.glob("*.yml")) if workflows.is_dir() else []
    if not files:
        return ["%s: no workflow files found" % workflows]

    found = []
    for path in files:
        try:
            text = path.read_text(encoding="utf-8")
        except OSError as error:
            found.append("%s: cannot read (%s)" % (path, error))
            continue
        label = path.relative_to(root).as_posix()

        if "pull_request_target" in text:
            found.append("%s: pull_request_target is forbidden" % label)

        top = re.search(r"(?m)^permissions:\s*\n((?:[ \t]+[^\n]*(?:\n|$))*)", text)
        if not top:
            found.append("%s: missing top-level permissions" % label)
        else:
            block = top.group(1)
            if "write-all" in block or re.search(r":\s*write\b", block):
                found.append("%s: top-level permissions grant write access" % label)

        for reference in USES.findall(text):
            if reference.startswith("./") or reference.startswith("docker://"):
                continue
            if "@" not in reference:
                found.append("%s: action %s has no ref" % (label, reference))
                continue
            _, ref = reference.rsplit("@", 1)
            if not SHA.fullmatch(ref):
                found.append("%s: action %s is not pinned to a 40-character SHA"
                             % (label, reference))

        for ref, body in _checkout_steps(text):
            if not re.search(r"(?m)^\s+persist-credentials:\s*false\s*$", body):
                found.append("%s: actions/checkout@%s does not set "
                             "persist-credentials: false" % (label, ref))

        jobs = _jobs(text)
        for name in ("publish", "handoff"):
            if name not in jobs:
                continue
            job = jobs[name]
            if ("github.event_name == 'workflow_dispatch'" not in job
                    or "refs/heads/main" not in job):
                found.append("%s: job %s must run only on manual dispatch to main"
                             % (label, name))
            if "github.event_name == 'push'" in job:
                found.append("%s: job %s must not run on push" % (label, name))

        for name, job in jobs.items():
            if "pull_request" not in job and name != "review":
                continue
            for secret in PRODUCTION_SECRETS:
                if "secrets.%s" % secret in job:
                    found.append("%s: job %s references production secret %s"
                                 % (label, name, secret))

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
        print("workflows: %d problem(s)" % len(found))
        return 1
    count = len(list((args.root / ".github" / "workflows").glob("*.yml")))
    print("workflows: OK - %d workflow(s), action pins and permissions are safe" % count)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
