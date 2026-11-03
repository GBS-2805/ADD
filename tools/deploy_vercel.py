#!/usr/bin/env python3
"""Deploy docs/ to Vercel, verify it, then move the repo's links to the new host.

Order matters and is the whole point of the script: the new host is deployed and
every route is checked before a single link is rewritten. A deploy that fails or
serves a half-uploaded folder therefore leaves the repository pointing at the
host that still works.

    python3 tools/deploy_vercel.py --check          # what would happen, no token needed
    python3 tools/deploy_vercel.py                  # deploy, verify, rewrite links
    python3 tools/deploy_vercel.py --links-only URL # rewrite links to a known host

The token is read from VERCEL_TOKEN (Vercel's own variable) and never passed on a
command line, so it does not land in a process listing or a shell history.
"""

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "tools/site.json"
PROJECT = "artbase"          # created on first deploy if the account has no project by this name
CLI = ("npx", "-y", "vercel@latest")

# The shelf's routes, and the type each one must come back as. A host that serves
# the APK as text/html still returns 200, and that is the failure worth catching.
ROUTES = [
    ("/", "text/html"),
    ("/tools/", "text/html"),
    ("/icons/", "text/html"),
    ("/icons/all/", "text/html"),
    ("/icons/coolicons/", "text/html"),
    ("/icons/lineicons/", "text/html"),
    ("/fonts/", "text/html"),
    ("/portfolio/", "text/html"),
    ("/download/", "text/html"),
    ("/releases/", "text/html"),
    ("/renders/", "text/html"),
    ("/figma/", "text/html"),
    ("/assets/site.css", "text/css"),
    ("/assets/site.js", "javascript"),
    ("/data/icons.json", "application/json"),
    ("/renders/page-tools.webp", "image/webp"),
    ("/figma/site-figma.svg", "image/svg+xml"),
    ("/Compress-2.0.apk", "android.package-archive"),
]

# Text files that can hold the origin. The generated trees are skipped: they are
# rebuilt from templates or from git and never carry a link back to the host.
SKIP_DIRS = {".git", "artifacts", "dist", "build", "node_modules", ".vercel", "__pycache__"}
SKIP_PATHS = ("docs/releases", "docs/figma", "docs/renders", "docs/data")
TEXT_SUFFIXES = {".json", ".java", ".html", ".md", ".py", ".js", ".css", ".xml", ".sh", ".txt"}


def site():
    return json.loads(SITE.read_text(encoding="utf-8"))


def origin_of(url):
    return url.rstrip("/")


def link_files(origin):
    """Every text file in the repo that hard-codes the current origin."""
    hits = []
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or path.suffix not in TEXT_SUFFIXES:
            continue
        rel = path.relative_to(ROOT).as_posix()
        if set(path.relative_to(ROOT).parts) & SKIP_DIRS or rel.startswith(SKIP_PATHS):
            continue
        try:
            if origin in path.read_text(encoding="utf-8"):
                hits.append(path)
        except (UnicodeDecodeError, OSError):
            continue
    return hits


def rewrite(old, new):
    """Swap the origin inside each link file, keeping whatever trailing slash it had."""
    changed = []
    for path in link_files(old):
        text = path.read_text(encoding="utf-8")
        after = text.replace(old, new)
        if after == text:
            continue
        before_lines = [line for line in text.splitlines() if old in line]
        after_lines = [line for line in after.splitlines() if new in line]
        path.write_text(after, encoding="utf-8")
        changed.append((path.relative_to(ROOT).as_posix(), before_lines, after_lines))
    return changed


def deploy():
    if not os.environ.get("VERCEL_TOKEN"):
        sys.exit("VERCEL_TOKEN is not set. Add it in this project under Settings -> "
                 "Secrets, then run again; --check works without it.")
    cmd = [*CLI, "deploy", "docs", "--prod", "--yes",
           "--project", PROJECT, "--create-if-missing"]
    print("$ " + " ".join(cmd), flush=True)
    proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
    out = proc.stdout + proc.stderr
    print(out.rstrip())
    if proc.returncode != 0:
        sys.exit(f"deploy failed (exit {proc.returncode}); nothing was rewritten")
    urls = re.findall(r"https://[a-z0-9-]+(?:\.[a-z0-9-]+)*\.vercel\.app", out)
    if not urls:
        sys.exit("deploy succeeded but printed no *.vercel.app URL; pass --links-only URL once "
                 "the production domain is known")
    return "https://" + urls[-1].split("://", 1)[1]


def verify(origin):
    """GET every route; a 200 with the wrong type is still a failure."""
    problems = []
    for route, wanted in ROUTES:
        request = urllib.request.Request(origin + route, headers={"User-Agent": "deploy-check"})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                status, ctype, body = response.status, response.headers.get("content-type", ""), response.read()
        except urllib.error.HTTPError as error:
            problems.append(f"{route}  HTTP {error.code}")
            continue
        except Exception as error:                      # noqa: BLE001 - report, do not mask
            problems.append(f"{route}  {type(error).__name__}: {error}")
            continue
        if status != 200:
            problems.append(f"{route}  HTTP {status}")
        elif wanted not in ctype:
            problems.append(f"{route}  served as {ctype!r}, expected {wanted}")
        elif route.endswith(".apk") and not body.startswith(b"PK"):
            problems.append(f"{route}  not a zip/APK ({len(body)} bytes)")
        else:
            print(f"  ok  {route:<26} {status}  {ctype.split(';')[0]}  {len(body)}b")
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="print the plan, deploy nothing")
    parser.add_argument("--links-only", metavar="URL", help="skip the deploy, rewrite links to URL")
    args = parser.parse_args()

    old = origin_of(site()["url"])
    files = link_files(old)
    payload = sum(p.stat().st_size for p in (ROOT / "docs").rglob("*") if p.is_file())

    print(f"current host  {old}")
    print(f"payload       {payload / 1048576:.2f} MiB, "
          f"{sum(1 for p in (ROOT / 'docs').rglob('*') if p.is_file())} files")
    print(f"link files    {len(files)}")
    for path in files:
        for line in path.read_text(encoding="utf-8").splitlines():
            if old in line:
                print(f"  {path.relative_to(ROOT).as_posix()}: {line.strip()[:100]}")

    if args.check:
        print(f"\nwould deploy docs/ as project {PROJECT!r}, then check {len(ROUTES)} routes "
              f"and rewrite the {len(files)} files above")
        print("VERCEL_TOKEN is set" if os.environ.get("VERCEL_TOKEN") else
              "VERCEL_TOKEN is not set - deploy would stop here")
        return

    new = origin_of(args.links_only) if args.links_only else deploy()
    if new == old:
        sys.exit("the new host is the current host; nothing to do")

    print(f"\nverifying {new}")
    problems = verify(new)
    if problems:
        print("\nnot rewriting any link:")
        for problem in problems:
            print("  FAIL " + problem)
        sys.exit(1)

    changed = rewrite(old, new)
    apps = [name for name, _, _ in changed if name.endswith(".java")]
    print(f"\nrewrote {len(changed)} files")
    for name, before, after in changed:
        print(f"  {name}")
        for line in after:
            print(f"      {line.strip()[:100]}")

    if apps:
        print("\nThe APKs still embed the old origin, because it is compiled into them:")
        for name in apps:
            print(f"  {name}")
        print("Rebuild both (./build.sh) and bump the versions so the shipped APKs point at "
              "the new host; until then the apps keep using the old one, which still serves.")


if __name__ == "__main__":
    main()
