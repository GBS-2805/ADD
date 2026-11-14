#!/usr/bin/env python3
"""Deploy a review surface only after the deployment-storage gate passes.

    python3 tools/deploy_review.py apk
    python3 tools/deploy_review.py handoff
    python3 tools/deploy_review.py shots
    python3 tools/deploy_review.py review
    python3 tools/deploy_review.py handoff --check

The gate is strict: an unavailable report is a failure, not a skip. The deploy token
is read from the target's project-scoped variable when set, otherwise VERCEL_TOKEN.
It reaches the CLI through the environment, never a command line.
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CLI = ("npx", "-y", "vercel@65.14.0")

sys.path.insert(0, str(ROOT / "tools"))
from check_headers import problems as header_problems  # noqa: E402 - the tools directory is on the path above
from deploy_storage import check as storage_check  # noqa: E402 - the tools directory is on the path above

TARGETS = {
    "apk": ("artifacts/handoff-apk", "artbase-handoff-apk", "VERCEL_APK_TOKEN"),
    "handoff": ("handoff", "artbase-handoff", "VERCEL_HANDOFF_TOKEN"),
    "review": ("docs", "artbase-review", "VERCEL_REVIEW_TOKEN"),
    "shots": ("artifacts/shots", "artbase-shots", "VERCEL_SHOTS_TOKEN"),
}


LIVE_ORIGINS = {
    "handoff": "https://artbase-handoff.vercel.app",
}


def deploy_token(env_name):
    return os.environ.get(env_name) or os.environ.get("VERCEL_TOKEN")


def headers_current(target):
    """Return 0 when a target's live headers pass; 1 when they do not."""
    origin = LIVE_ORIGINS.get(target)
    if not origin:
        return 0
    problems = header_problems(origin)
    if problems:
        for problem in problems:
            print("  " + problem)
        print("headers: %d problem(s) on %s" % (len(problems), origin))
        return 1
    print("headers: OK - %s carries CSP, HSTS, frame denial and cache policy" % origin)
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("target", choices=sorted(TARGETS), help="review surface to deploy")
    parser.add_argument("--check", action="store_true", help="print the plan, deploy nothing")
    args = parser.parse_args()

    source_name, project, token_env = TARGETS[args.target]
    source = ROOT / source_name
    if not source.is_dir():
        sys.exit("deploy_review: %s is missing; build or capture it first" % source_name)

    if args.check:
        token = deploy_token(token_env)
        print("source        %s" % source_name)
        print("project       %s" % project)
        print("deploy token  %s" % (token_env if os.environ.get(token_env) else
                                    "VERCEL_TOKEN fallback"))
        print("storage gate  strict - critical or unavailable stops the deploy")
        return 0

    token = deploy_token(token_env)
    if not token:
        sys.exit("%s (or VERCEL_TOKEN) is not set. Add it under Settings -> Secrets, "
                 "then run again; --check works without it." % token_env)

    storage_check(strict=True)

    cmd = [*CLI, "deploy", source.relative_to(ROOT).as_posix(), "--prod", "--yes",
           "--project", project, "--create-if-missing"]
    print("$ " + " ".join(cmd), flush=True)
    # The child gets only this target's deploy token, never the admin token.
    env = {key: value for key, value in os.environ.items()
           if key not in {"VERCEL_ADMIN_TOKEN", "VERCEL_ARTBASE_TOKEN",
                          "VERCEL_APK_TOKEN", "VERCEL_HANDOFF_TOKEN",
                          "VERCEL_REVIEW_TOKEN", "VERCEL_SHOTS_TOKEN"}}
    env["VERCEL_TOKEN"] = token
    proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, env=env)
    print((proc.stdout + proc.stderr).rstrip())
    if proc.returncode == 0:
        return headers_current(args.target)
    return proc.returncode


if __name__ == "__main__":
    raise SystemExit(main())
