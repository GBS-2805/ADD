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
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "tools/site.json"
PROJECT = "artbase"          # created on first deploy if the account has no project by this name
CLI = ("npx", "-y", "vercel@latest")

sys.path.insert(0, str(ROOT / "tools"))
from build_site import routes as site_routes  # noqa: E402 - the tools directory is on the path above

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


def replace_origin(text, old, new):
    """Swap the origin wherever it stands alone, and count what moved.

    A plain substring replace would also rewrite a longer host that merely begins with
    it -- a subdomain, or a domain name that starts with the rented one -- and would
    leave the rest of the address pointing at nothing.
    """
    edge = re.compile(r"(?<![A-Za-z0-9.\-])" + re.escape(old) + r"(?![A-Za-z0-9.\-])")
    return edge.subn(new, text)


def rewrite(old, new):
    """Swap the origin inside each link file, keeping whatever trailing slash it had."""
    changed = []
    for path in link_files(old):
        text = path.read_text(encoding="utf-8")
        after, hits = replace_origin(text, old, new)
        if not hits:
            raise SystemExit("deploy: %s mentions %s only inside a longer address, so no "
                             "link there moved; fix that file before switching hosts"
                             % (path.relative_to(ROOT).as_posix(), old))
        before_lines = [line for line in text.splitlines() if old in line]
        after_lines = [line for line in after.splitlines() if new in line]
        path.write_text(after, encoding="utf-8")
        changed.append((path.relative_to(ROOT).as_posix(), before_lines, after_lines))
    return changed


def committed(page):
    """What HEAD holds for a file, or None when HEAD has no such file."""
    done = subprocess.run(["git", "show", "HEAD:%s" % page.relative_to(ROOT).as_posix()],
                          cwd=ROOT, capture_output=True)
    return done.stdout if done.returncode == 0 else None


def refresh_history():
    """Regenerate the history page from git, and stop if it is not committed.

    The page is written out of git, so a build reaches it only once committed -- which
    leaves the page on disk exactly one release behind after a bump. Regenerating here
    is what closes that gap, but uploading the result would publish a page no commit
    contains, so a regenerated page that differs from HEAD stops the deploy: the file is
    left corrected, and committing it is the only step left.
    """
    page = ROOT / "docs/releases/index.html"
    done = subprocess.run([sys.executable, str(ROOT / "tools/build_releases.py")],
                          cwd=ROOT, capture_output=True, text=True)
    if done.returncode != 0:
        sys.exit("the history page did not regenerate: " + (done.stderr or done.stdout).strip())
    print("  " + done.stdout.strip().splitlines()[0])
    on_disk = page.read_bytes() if page.exists() else b""
    if on_disk != (committed(page) or b""):
        sys.exit("docs/releases/index.html differs from HEAD once regenerated: commit it, "
                 "then deploy again (nothing was uploaded)")




def assets_current():
    """Stop when a page still names the previous CSS or JS."""
    done = subprocess.run([sys.executable, str(ROOT / "tools/hash_assets.py"), "--check"],
                          cwd=ROOT, capture_output=True, text=True)
    if done.returncode != 0:
        sys.exit("docs/ does not point at the current asset names; run "
                 "python3 tools/hash_assets.py and commit it first:\n"
                 + (done.stdout or done.stderr).strip())


def deploy():
    if not os.environ.get("VERCEL_TOKEN"):
        sys.exit("VERCEL_TOKEN is not set. Add it in this project under Settings -> "
                 "Secrets, then run again; --check works without it.")
    assets_current()
    refresh_history()
    cmd = [*CLI, "deploy", "docs", "--prod", "--yes",
           "--project", PROJECT, "--create-if-missing"]
    print("$ " + " ".join(cmd), flush=True)
    proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
    out = proc.stdout + proc.stderr
    print(out.rstrip())
    if proc.returncode != 0:
        sys.exit(f"deploy failed (exit {proc.returncode}); nothing was rewritten")
    # Prefer the production alias: the per-deployment host changes on every upload,
    # and the alias is the address the repo should hold.
    alias = re.findall(r"Aliased\s+(https://\S+)", out)
    urls = alias or re.findall(r"https://[a-z0-9-]+(?:\.[a-z0-9-]+)*\.vercel\.app", out)
    if not urls:
        sys.exit("deploy succeeded but printed no *.vercel.app URL; pass --links-only URL once "
                 "the production domain is known")
    return origin_of(urls[-1])


def probe(origin, route, wanted):
    """GET one route and read only enough of it to judge what came back.

    Reading 64 bytes and closing keeps the check cheap even when the shelf serves
    hundreds of captures and megabyte artboards: the bytes are not the point, the
    status and the content type are.
    """
    request = urllib.request.Request(origin + route, headers={"User-Agent": "deploy-check"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            ctype = response.headers.get("content-type", "")
            length = response.headers.get("content-length")
            head = response.read(64)
    except urllib.error.HTTPError as error:
        return route, f"HTTP {error.code}", 0
    except Exception as error:                          # noqa: BLE001 - report, do not mask
        return route, f"{type(error).__name__}: {error}", 0
    if wanted not in ctype:
        return route, f"served as {ctype!r}, expected {wanted}", 0
    if route.endswith(".apk") and not head.startswith(b"PK"):
        return route, "not a zip/APK", 0
    size = int(length) if length and length.isdigit() else len(head)
    return route, None, size


def verify(origin):
    """Check every file the deploy serves; a 200 with the wrong type is a failure.

    The routes come from the tree itself (tools/build_site.py), so a page or capture
    added to the shelf is checked without anyone adding it to a list here.
    """
    checks = site_routes()
    pages = {route for route, wanted in checks if wanted == "text/html"}
    if not checks:
        return ["no routes found under docs/ - is the folder there?"]

    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda item: probe(origin, *item), checks))

    problems = [f"{route}  {error}" for route, error, _ in results if error]
    assets = 0
    for route, error, size in results:
        if error:
            continue
        if route in pages:
            print(f"  ok  {route:<28} text/html  {size}b")
        else:
            assets += 1
    print(f"  ok  {assets} other files (css, js, json, images, fonts, APKs)")
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
        print(f"\nwould deploy docs/ as project {PROJECT!r}, then check {len(site_routes())} routes "
              f"and rewrite the {len(files)} files above")
        print("VERCEL_TOKEN is set" if os.environ.get("VERCEL_TOKEN") else
              "VERCEL_TOKEN is not set - deploy would stop here")
        return

    new = origin_of(args.links_only) if args.links_only else deploy()
    # A redeploy to the host the repo already names is the normal case, not an
    # error: the upload still has to be verified, it just changes no link.
    same = new == old

    print(f"\nverifying {new}")
    problems = verify(new)
    if problems:
        print("\nnot rewriting any link:")
        for problem in problems:
            print("  FAIL " + problem)
        sys.exit(1)
    if same:
        print("\nthe links already point at this host; nothing rewritten")
        return

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
