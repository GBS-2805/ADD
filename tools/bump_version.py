#!/usr/bin/env python3
"""Move the Icons release to a new version in one step.

    python3 tools/bump_version.py 2.4 --title "All brands in the app" \\
        --note "One sentence for the release list." --build --deploy

In order, it moves:

  * tools/catalog.json            version, versionCode, APK name
  * build.sh                      both the `icons` and `all` build cases
  * every live version label      the shelf pages, README, AGENTS, the capture list
  * tools/release_notes.json      one new note at the top, above the newest

With --build it then runs build.sh, copies the signed APK into docs/, deletes the
previous one, re-measures its size and SHA-256, writes those into the same pages and
rebuilds the catalogs.

Then, unless --skip-captures is given, it starts a static server over docs/ and runs
tools/render_pages.js against it, so the gallery is recaptured from the pages that
quote the new build rather than the ones before it. Playwright and the browser libs
come from tools/render_env.py; the environment is checked before anything is written,
so a machine without them stops the release instead of leaving it half done.

--deploy ends with the deployment. This machine has no deploy CLI, so the Sites tool
does the upload: the script prints the exact request to give it, with a fresh
requestId. Pass --deploy-cmd "<command>" to run a real CLI instead, once one exists.

Without --build nothing is compiled, no captures are taken and the size and checksum
keep their old values, which leaves the shelf failing `build_catalog.py --check` until
the build runs. --dry-run prints the planned edits and writes nothing.

The notes are never relabelled: a note keeps the version it was written for, and
every note is checked against the commit that first added it before anything moves.
"""

import argparse
import hashlib
import json
import os
import pathlib
import re
import shutil
import socket
import subprocess
import sys
import time
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools/catalog.json"
BUILD = ROOT / "build.sh"
NOTES = ROOT / "tools/release_notes.json"
SITE = ROOT / "tools/site.json"
LABEL_FILES = ["docs/index.html", "docs/download/index.html", "docs/icons/index.html",
               "docs/tools/index.html", "README.md", "AGENTS.md", "tools/render_pages.js"]

# A note carries its own version and must never be bumped, so the notes live in a
# file of their own instead of beside the labels a bump walks over.
VERSION = re.compile(r"^[0-9]+(\.[0-9]+)*$")


def notes():
    """Every release note, newest first, as the one file keeps them."""
    return json.loads(NOTES.read_text(encoding="utf-8"))["notes"]


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)


def shipped_with(title, app="icons"):
    """The app's version in the commit that first added a note, and its short sha."""
    # The notes lived on the tools page until they moved to the file, so both paths
    # are searched: the commit that first added a sentence is where it shipped.
    found = git("log", "--reverse", "--format=%H", "-S", title, "--",
                "docs/tools/index.html", "tools/release_notes.json")
    if found.returncode != 0:
        return None, "git could not read the history"
    if not found.stdout.strip():
        return None, "no commit adds it yet"
    sha = found.stdout.split()[0]
    catalog = git("show", "%s:tools/catalog.json" % sha)
    if catalog.returncode != 0:
        return None, "no catalog at " + sha[:8]
    try:
        version = next(tool["version"] for tool in json.loads(catalog.stdout)["tools"]
                       if tool["id"] == app)
    except Exception:
        return None, "catalog unreadable at " + sha[:8]
    return version, sha[:8]


def keyed(version):
    return tuple(int(part) for part in version.split("."))


def report_notes():
    """Check each note against its app's version at the commit that first added it.

    A note written after its build went out cannot be verified this way -- the
    Compress notes all arrived with the site itself, when that app was already at
    1.9 -- so it is reported as written later rather than as a mistake. A label
    newer than the catalog at that commit is a broken history and stops a bump.
    """
    if not (ROOT / ".git").exists():
        print("release notes: no git history to check against")
        return 0
    entries = notes()
    problems = late = 0
    for entry in entries:
        label, title = entry["version"], entry["title"]
        version, where = shipped_with(title, entry["id"])
        if version is None:
            print("  note %s %s \u00b7 %s: %s, unverified" % (entry["id"], label, title, where))
        elif version == label:
            continue
        elif keyed(version) > keyed(label):
            late += 1
            print("  note %s %s \u00b7 %s: added when %s was %s (%s), not checked"
                  % (entry["id"], label, title, entry["id"], version, where))
        else:
            problems += 1
            print("  note %s %s \u00b7 %s shipped in %s (%s)"
                  % (entry["id"], label, title, version, where))
    if problems:
        print("release notes: %d note(s) are labelled with a version the commit that "
              "added them does not ship" % problems)
    else:
        print("release notes: %d notes, %d checked against the build they shipped in, "
              "%d written after it" % (len(entries), len(entries) - late, late))
    return problems


def fmt(number):
    return "{:,}".format(number)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rewrite(path, pairs, dry):
    """Replace pairs line by line in a page; the notes file is never in this list."""
    path = ROOT / path
    text = path.read_text(encoding="utf-8")
    out, hits = [], 0
    for line in text.splitlines(keepends=True):
        for old, new in pairs:
            if old and old in line:
                hits += line.count(old)
                line = line.replace(old, new)
        out.append(line)
    if not hits:
        return 0
    if not dry:
        path.write_text("".join(out), encoding="utf-8")
    return hits


def label_pairs(old, new, old_code, new_code):
    return [(f"Icons-{old}", f"Icons-{new}"),
            (f"Icons {old}", f"Icons {new}"),
            (f"versionCode {old_code}", f"versionCode {new_code}")]


def bump_build_sh(old_apk, old, new_apk, new, old_code, new_code, dry):
    text = BUILD.read_text(encoding="utf-8")
    old_case = "%s %d %s" % (old_apk.lstrip("/"), old_code, old)
    new_case = "%s %d %s" % (new_apk.lstrip("/"), new_code, new)
    count = text.count(old_case)
    if count == 0:
        raise SystemExit("build.sh has no `%s`; refusing to guess" % old_case)
    if not dry:
        BUILD.write_text(text.replace(old_case, new_case), encoding="utf-8")
    return count


def add_note(new, title, note, dry):
    data = json.loads(NOTES.read_text(encoding="utf-8"))
    for entry in data["notes"]:
        if entry["id"] == "icons" and entry["version"] == new and entry["title"] == title:
            return "already there"
    data["notes"].insert(0, {"id": "icons", "version": new, "title": title, "note": note})
    if not dry:
        NOTES.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return "note %s above the newest" % ("inserted" if not dry else "would go")


def capture_env():
    """The headless-Chromium environment tools/render_env.py sets up."""
    env = os.environ.copy()
    env.setdefault("NODE_PATH", "/tmp/pwshot/node_modules")
    env.setdefault("PLAYWRIGHT_BROWSERS_PATH", "/tmp/pw")
    env.setdefault("LD_LIBRARY_PATH",
                   "/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu")
    env.setdefault("FONTCONFIG_FILE", "/tmp/rootfs/fonts.conf")
    return env


def capture_preflight():
    """Fail before any file moves if the capture toolchain is not here."""
    node = shutil.which("node")
    if not node:
        raise SystemExit("captures need node on PATH; run tools/render_env.py or pass --skip-captures")
    env = capture_env()
    probe = subprocess.run([node, "-e", "require('playwright')"], cwd=str(ROOT),
                           env=env, capture_output=True, text=True)
    if probe.returncode != 0:
        lines = (probe.stderr or probe.stdout).strip().splitlines()
        reason = next((line.strip() for line in lines
                       if "Cannot find module" in line or "MODULE_NOT_FOUND" in line),
                      "node cannot load it")
        raise SystemExit("captures need playwright (python3 tools/render_env.py), or pass "
                         "--skip-captures\n  " + reason)
    return node, env


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def run_captures(node, env):
    """Serve docs/ and let tools/render_pages.js recapture the gallery from it."""
    port = free_port()
    base = "http://127.0.0.1:%d/" % port
    server = subprocess.Popen(
        [sys.executable, "-m", "http.server", str(port), "--directory", str(ROOT / "docs")],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for _ in range(60):
            try:
                urllib.request.urlopen(base, timeout=1).read(1)
                break
            except Exception:
                time.sleep(0.2)
        else:
            raise SystemExit("the capture server never answered on %s" % base)
        done = subprocess.run([node, "tools/render_pages.js", base, "artifacts"],
                              cwd=str(ROOT), env=env, capture_output=True, text=True)
        if done.returncode != 0:
            print((done.stdout or "")[-1500:])
            print((done.stderr or "")[-1500:])
            raise SystemExit("captures failed: the release is moved, but the gallery still "
                             "shows the build before it")
        for line in done.stdout.splitlines():
            if line.startswith("gallery:") or line.startswith("console errors") \
                    or "removed orphan" in line:
                print("  " + line)
    finally:
        server.terminate()
        try:
            server.wait(timeout=5)
        except subprocess.TimeoutExpired:
            server.kill()


def deploy_request():
    site = {}
    if SITE.exists():
        site = json.loads(SITE.read_text(encoding="utf-8"))
    return {"siteId": site.get("siteId", ""),
            "environment": site.get("environment", "preview"),
            "requestId": "release-" + uuid.uuid4().hex[:12],
            "manifestPath": "manifest.json"}


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("version", nargs="?", help="the new version, e.g. 2.4")
    parser.add_argument("--title", help="release-note title, after the version")
    parser.add_argument("--note", help="release-note sentence")
    parser.add_argument("--build", action="store_true",
                        help="build and sign the APK, copy it into docs/ and re-measure it")
    parser.add_argument("--skip-captures", action="store_true",
                        help="leave docs/renders/ alone even when building")
    parser.add_argument("--deploy", action="store_true",
                        help="end with the deployment request, or run --deploy-cmd")
    parser.add_argument("--deploy-cmd",
                        help="a deploy CLI to run instead of printing the Sites request")
    parser.add_argument("--check-notes", action="store_true",
                        help="only verify the release notes against the history, change nothing")
    parser.add_argument("--dry-run", action="store_true", help="print the edits, write nothing")
    args = parser.parse_args()

    if args.check_notes:
        return 1 if report_notes() else 0
    if not args.version:
        parser.error("a version is required unless --check-notes is given")
    new = args.version.strip()
    if not VERSION.match(new):
        raise SystemExit("version must look like 2.4, not %r" % new)
    if bool(args.title) != bool(args.note):
        raise SystemExit("--title and --note go together")
    if (args.deploy or args.deploy_cmd) and not args.build:
        raise SystemExit("--deploy needs --build, so the shelf quotes the APK it ships")

    # A note whose label is not the version it shipped in is a broken history, and
    # bumping on top of it would only bury the mistake.
    if report_notes():
        raise SystemExit("refusing to bump: give those notes their real versions first")

    captures = args.build and not args.skip_captures
    toolchain = capture_preflight() if captures else None

    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = next(tool for tool in catalog["tools"] if tool["id"] == "icons")
    old, old_code = icons["version"], int(icons["version_code"])
    if new == old:
        raise SystemExit("the catalog is already at %s" % old)
    new_code = old_code + 1
    old_apk, new_apk = icons["apk"], "/Icons-%s.apk" % new
    old_file = ROOT / "docs" / old_apk.lstrip("/")
    if not old_file.exists():
        raise SystemExit("the shipped APK %s is missing" % old_file.relative_to(ROOT))
    old_size, old_sha = fmt(old_file.stat().st_size), digest(old_file)

    dry = args.dry_run
    print("Icons %s (versionCode %d) -> %s (versionCode %d), %s -> %s"
          % (old, old_code, new, new_code, old_apk, new_apk))

    icons.update({"version": new, "version_code": new_code, "apk": new_apk})
    if not dry:
        CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n",
                           encoding="utf-8")
    print("  catalog.json           version, versionCode and APK")

    cases = bump_build_sh(old_apk, old, new_apk, new, old_code, new_code, dry)
    print("  build.sh               %d build case(s)" % cases)

    pairs = label_pairs(old, new, old_code, new_code)
    for name in LABEL_FILES:
        hits = rewrite(name, pairs, dry)
        if hits:
            print("  %-30s %d label(s)" % (name, hits))

    if args.title:
        print("  %-30s %s" % ("tools/release_notes.json", add_note(new, args.title, args.note, dry)))

    if dry:
        print("dry run: nothing written; %s and %s would be replaced once the APK is built"
              % (old_size, old_sha[:10]))
        if captures:
            print("         and docs/renders/ would be recaptured from the new pages")
        return 0

    if not args.build:
        print("next: ./build.sh icons, then copy dist/Icons-%s.apk into docs/, delete\n"
              "      docs%s, and patch %s and %s into the pages\n"
              "      (or run this again with --build)" % (new, old_apk, old_size, old_sha[:10]))
        return 0

    print("building...")
    subprocess.run([str(BUILD), "icons"], cwd=ROOT, check=True)
    built = ROOT / "dist" / ("Icons-%s.apk" % new)
    if not built.exists():
        raise SystemExit("build.sh finished without %s" % built.relative_to(ROOT))

    shipped = ROOT / "docs" / built.name
    shutil.copyfile(built, shipped)
    if old_file.exists():
        old_file.unlink()
    new_size, new_sha = fmt(shipped.stat().st_size), digest(shipped)
    print("  docs/%s  %s bytes  %s.." % (built.name, new_size, new_sha[:10]))
    print("  removed docs/%s" % old_file.name)

    for name in LABEL_FILES:
        hits = rewrite(name, [(old_size, new_size), (old_sha, new_sha)], dry=False)
        if hits:
            print("  %-30s %d measurement(s)" % (name, hits))

    for extra in ([], ["--check"]):
        subprocess.run([sys.executable, str(ROOT / "tools/build_catalog.py"), *extra],
                       cwd=ROOT, check=True)

    if captures:
        print("capturing...")
        node, env = toolchain
        run_captures(node, env)
    else:
        print("captures skipped: docs/renders/ still shows %s" % old)

    if args.deploy:
        if args.deploy_cmd:
            print("deploying with: %s" % args.deploy_cmd)
            subprocess.run(args.deploy_cmd, shell=True, cwd=ROOT, check=True)
            print("  deploy command finished")
        else:
            print("deploy: this machine has no deploy CLI, so the preview goes out through the")
            print("        Sites tool. Give it this request:")
            print(json.dumps(deploy_request(), indent=2))

    print("Icons %s is in docs/ and the shelf agrees with it" % new)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
