#!/usr/bin/env python3
"""Refuse new commits on a branch whose pull request has already merged.

    python3 tools/check_branch_state.py --repo OWNER/REPO --branch NAME --base main

The guard asks GitHub for the branch's pull requests, finds one with `merged_at`, and
then checks whether `HEAD` is already reachable from `origin/main`. A merged PR whose
HEAD is contained in the base is healthy; a merged PR with a commit that never landed
is not, because the new commits cannot be merged by that PR again. The script only
reads: it never pushes, closes, merges or deletes anything.
"""

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


class GuardError(Exception):
    pass


def api_pulls(repo, owner, branch, token, opener=urllib.request.urlopen):
    query = urllib.parse.urlencode({"head": "%s:%s" % (owner, branch),
                                    "state": "all", "per_page": 100})
    url = "https://api.github.com/repos/%s/pulls?%s" % (repo, query)
    headers = {"Accept": "application/vnd.github+json",
               "User-Agent": "add-branch-guard"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, headers=headers)
    try:
        with opener(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        if exc.code in (401, 403, 404) and not token:
            raise GuardError("GitHub returned %d for %s; set GITHUB_TOKEN for a private "
                             "repository" % (exc.code, repo))
        raise GuardError("GitHub returned %d while reading pull requests for %s"
                         % (exc.code, repo))
    except (urllib.error.URLError, OSError, ValueError) as exc:
        raise GuardError("cannot read pull requests for %s: %s" % (repo, exc))


def merged_pull(pulls):
    for pull in pulls:
        if pull.get("merged_at"):
            return pull
    return None


def git(root, *args):
    done = subprocess.run(["git", *args], cwd=str(root), capture_output=True, text=True)
    return done.returncode, (done.stdout or "").strip(), (done.stderr or "").strip()


def head(root):
    code, out, err = git(root, "rev-parse", "HEAD")
    if code:
        raise GuardError("cannot read HEAD: %s" % (err or out))
    return out


def base_ref(root, base):
    for candidate in ("origin/%s" % base, base):
        code, _, _ = git(root, "rev-parse", "--verify", candidate + "^{commit}")
        if not code:
            return candidate
    raise GuardError("no %s or origin/%s in this checkout; fetch the base branch first"
                     % (base, base))


def check(root, repo, branch, base, token, fetch=api_pulls):
    owner = repo.split("/", 1)[0]
    pull = merged_pull(fetch(repo, owner, branch, token))
    if not pull:
        return 0, "branch %s has no merged pull request; nothing to guard" % branch
    sha = head(root)
    ref = base_ref(root, base)
    code, _, _ = git(root, "merge-base", "--is-ancestor", "HEAD", ref)
    if code == 0:
        return 0, ("merged pull request #%s; HEAD %s is already in %s"
                   % (pull.get("number", "?"), sha[:7], ref))
    if code != 1:
        raise GuardError("git merge-base could not compare HEAD with %s" % ref)
    return 1, ("merged pull request #%s, but HEAD %s is not reachable from %s. "
               "Create a new branch from %s before adding commits."
               % (pull.get("number", "?"), sha[:7], ref, base))


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--repo", required=True, help="OWNER/REPO")
    parser.add_argument("--branch", required=True, help="branch name")
    parser.add_argument("--base", default="main", help="base branch (default: main)")
    parser.add_argument("--token-env", default="GITHUB_TOKEN",
                        help="environment variable holding the GitHub token")
    parser.add_argument("--root", default=str(ROOT), help="git checkout to inspect")
    args = parser.parse_args()
    token = os.environ.get(args.token_env, "").strip()
    try:
        code, message = check(Path(args.root), args.repo, args.branch, args.base, token)
    except GuardError as exc:
        print("branch guard: %s" % exc)
        return 1
    print("branch guard: %s" % message)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
