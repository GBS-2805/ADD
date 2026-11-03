#!/usr/bin/env python3
"""Move the Icons release to a new version in one step.

    python3 tools/bump_version.py 2.4 --title "All brands in the app" \\
        --note "One sentence for the release list." --build --deploy

--app chooses the tool, `icons` (the default) or `compress`, and everything below
follows from it: the catalog entry, the build case, the labels and the note's app.

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

  With --commit it then commits twice: the release, and the history page that names the
  sha of that commit. It refuses to start on a worktree that already has changes, because
  the commit stages everything and a release must not carry work that is not part of it.

--deploy ends with the deployment. Without --deploy-cmd the Sites tool does the
upload: the script prints the exact request to give it, with a fresh requestId. Pass
--deploy-cmd "<command>" to run a real CLI instead; the site lives on Vercel, so a
release deploys with --deploy-cmd "python3 tools/deploy_vercel.py", which also checks
the new host and moves the old origin out of the repo.

Without --build nothing is compiled, no captures are taken and the size and checksum
keep their old values, which leaves the shelf failing `build_catalog.py --check` until
the build runs.

--dry-run writes nothing and prints the plan instead: every label it would move, a diff
of each file it would touch, the artboards and captures that go stale with those pages,
when the history page learns about the build, and the deploy headroom afterwards.

The notes are never relabelled: a note keeps the version it was written for, and
every note is checked against the commit that first added it before anything moves.
"""

import argparse
import difflib
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
               "docs/tools/index.html", "docs/portfolio/index.html", "README.md",
               "AGENTS.md", "tools/render_pages.js"]

# A note carries its own version and must never be bumped, so the notes live in a
# file of their own instead of beside the labels a bump walks over.
VERSION = re.compile(r"^[0-9]+(\.[0-9]+)*$")


def notes():
    """Every release note, newest first, as the one file keeps them."""
    return json.loads(NOTES.read_text(encoding="utf-8"))["notes"]


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)


def unrelated_changes():
    """Paths already changed in the worktree, sorted -- work a release must not carry."""
    done = subprocess.run(["git", "status", "--porcelain", "-z", "--untracked-files=all"],
                          cwd=ROOT, capture_output=True, text=True)
    if done.returncode != 0:
        raise SystemExit("git status failed: " + (done.stderr or done.stdout).strip())
    paths, renamed = set(), False
    for entry in done.stdout.split("\0"):
        if not entry:
            continue
        if renamed:
            renamed = False          # the source path of a rename follows as its own entry
            continue
        paths.add(entry[3:])
        if entry[0] in "RC":
            renamed = True
    return sorted(paths)


def regenerate_history():
    """Rebuild docs/releases from git, and return the line the tool printed."""
    done = subprocess.run([sys.executable, str(ROOT / "tools/build_releases.py")],
                          cwd=ROOT, capture_output=True, text=True)
    if done.returncode != 0:
        raise SystemExit("the history page did not regenerate: "
                         + (done.stderr or done.stdout).strip())
    return (done.stdout.strip().splitlines() or ["releases page"])[0]


def commit_all(message):
    """Stage the whole tree and commit it, returning the short sha of the commit."""
    def run(*args):
        return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)
    staged = run("add", "-A")
    if staged.returncode != 0:
        raise SystemExit("git add failed: " + (staged.stderr or staged.stdout).strip())
    made = run("commit", "-m", message)
    if made.returncode != 0:
        raise SystemExit("git commit failed: " + (made.stderr or made.stdout).strip())
    return run("rev-parse", "--short", "HEAD").stdout.strip()


def shipped_with(title, app="icons", label=None):
    """The app's version at the commit that added a note, and that commit short.

    Every commit that ever changed how often the sentence appears is a candidate: the
    notes file, and the tools page that carried them before the file existed. The
    first candidate whose catalog already ships the note's own version is the commit
    that added it, which is what lets a sentence that also runs as page copy -- the
    band's "Art Design Database", say -- skip the commit that merely printed the copy.
    """
    found = git("log", "--reverse", "--format=%H", "-S", title, "--",
                "docs/tools/index.html", "tools/release_notes.json")
    if found.returncode != 0:
        return None, "git could not read the history"
    shas = found.stdout.split()
    if not shas:
        return None, "no commit adds it yet"
    first = None
    for sha in shas:
        catalog = git("show", "%s:tools/catalog.json" % sha)
        if catalog.returncode != 0:
            continue
        try:
            version = next(tool["version"] for tool in json.loads(catalog.stdout)["tools"]
                           if tool["id"] == app)
        except Exception:
            continue
        if first is None:
            first = (version, sha[:8])
        if label is None or version == label:
            return version, sha[:8]
    if first is None:
        return None, "no catalog at " + shas[0][:8]
    return first


def keyed(version):
    return tuple(int(part) for part in version.split("."))


# The dating rule broke twice in ways that each looked like a fix: reading the notes
# file and the tools page together sent a title that also runs as page copy to the commit
# that printed the copy, and reading the notes file alone dropped verified notes from 26
# to 11. Pinned notes must still date to the build they shipped in, and the count of
# verified notes may not fall below the floor.
NOTES_VERIFIED_FLOOR = 27
NOTES_PINS = (("Art Design Database", "icons", "2.7"),
              ("The all-brands sheet works in the app", "icons", "2.6"))


def regress_notes():
    """Failures that mean the dating got worse, and the number of notes it verifies."""
    fails = []
    for title, app, label in NOTES_PINS:
        version, where = shipped_with(title, app, label)
        if version != label:
            fails.append("pinned note %s %s dated to %s (%s)" % (app, label, version, where))
    verified = sum(1 for entry in notes()
                   if shipped_with(entry["title"], entry["id"], entry["version"])[0]
                   == entry["version"])
    if verified < NOTES_VERIFIED_FLOOR:
        fails.append("%d notes verify, the floor is %d" % (verified, NOTES_VERIFIED_FLOOR))
    return fails, verified


def report_notes(extra=None):
    """Check each note against its app's version at the commit that first added it.

    A note written after its build went out cannot be verified this way -- the
    Compress notes all arrived with the site itself, when that app was already at
    1.9 -- so it is reported as written later rather than as a mistake. A label
    newer than the catalog at that commit is a broken history and stops a bump.
    """
    if not (ROOT / ".git").exists():
        print("release notes: no git history to check against")
        return 0
    entries = ([extra] if extra else []) + notes()
    problems = late = unverified = 0
    for entry in entries:
        label, title = entry["version"], entry["title"]
        version, where = shipped_with(title, entry["id"], label)
        if version is None:
            unverified += 1
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
              "%d written after it%s"
              % (len(entries), len(entries) - late - unverified, late,
                 "" if not unverified else ", %d not in the history yet" % unverified))
    fails, verified = regress_notes()
    for line in fails:
        print("  " + line)
    print("  dating: %d verified, floor %d, %d pinned, %s"
          % (verified, NOTES_VERIFIED_FLOOR, len(NOTES_PINS),
             "REGRESSED" if fails else "unchanged"))
    return problems + len(fails)


def fmt(number):
    return "{:,}".format(number)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def planned(path, pairs):
    """The text a page would carry after the labels move, and how many moved.

    A pair may carry a third item: a word its line has to contain, so a bump to the
    versionCode cannot walk into the other app's line in the README.
    """
    text = (ROOT / path).read_text(encoding="utf-8")
    out, hits = [], 0
    for line in text.splitlines(keepends=True):
        for pair in pairs:
            old, new = pair[0], pair[1]
            if len(pair) > 2 and pair[2] not in line:
                continue
            if old and old in line:
                hits += line.count(old)
                line = line.replace(old, new)
        out.append(line)
    return text, "".join(out), hits


def rewrite(path, pairs, dry):
    """Replace pairs line by line in a page; the notes file is never in this list."""
    before, after, hits = planned(path, pairs)
    if not hits:
        return 0
    if not dry:
        (ROOT / path).write_text(after, encoding="utf-8")
    return hits


def label_pairs(app, old, new, old_code, new_code, clash=False):
    # The versionCode travels with the app's own name: the two apps keep separate
    # codes, and in the README their lines carry the numbers one above the other.
    # Only when both apps happen to share a code does the line have to say which.
    name = APPS[app]
    if clash:
        codes = [(f"version code {old_code}", f"version code {new_code}", name.lower()),
                 (f"Version code {old_code}", f"Version code {new_code}", name.lower())]
    else:
        codes = [(f"version code {old_code}", f"version code {new_code}"),
                 (f"Version code {old_code}", f"Version code {new_code}")]
    return [(f"{name}-{old}", f"{name}-{new}"),
            (f"{name} {old}", f"{name} {new}"),
            # The tools page prints a bare version in a pill, and the download page
            # spells the code with a space; neither carries the app's name.
            (f'class="pill live">{old}<', f'class="pill live">{new}<'),
            (f'class="pill live">app {old}<', f'class="pill live">app {new}<'),
            (f"{old} \u00b7 app.offline.{app} \u00b7", f"{new} \u00b7 app.offline.{app} \u00b7"),
            (f"Android app \u00b7 {old} \u00b7", f"Android app \u00b7 {new} \u00b7"),
            (f"versionCode {old_code}", f"versionCode {new_code}", name.lower())] + codes


# A page and the artboards made from it. The exporter writes one board per page per
# width by position, so a page that moves takes its boards with it; the two pull-outs
# are states of the index and carry its band, so they go stale with it too.
PAGE_OF = {"docs/index.html": "/", "docs/icons/index.html": "/icons/",
           "docs/tools/index.html": "/tools/", "docs/download/index.html": "/download/"}
SLUG_OF = {"/": "index", "/icons/": "icons", "/tools/": "tools", "/download/": "download"}
STATES = ("pull-down-desktop", "pull-down-phone", "side-tab-desktop", "side-tab-phone")
BUDGET = 10485760
APPS = {"icons": "Icons", "compress": "Compress"}


def human(number):
    return "%d KB" % round(number / 1024) if number < 1048576 else "%.2f MB" % (number / 1048576)


# Pages whose text is generated elsewhere, or is a list of every version the shelf has
# ever run: neither holds a live label the bump has to move.
SWEEP_SKIP = ("docs/releases", "docs/figma", "docs/renders", "docs/data")


# 2.5 is both a version of this app and the heaviest stroke the icon preview offers,
# so a sweep must not read every 2.5 as a label. These are the shapes the weight
# takes; a version label matches none of them, and the pill above is left alone.
WEIGHT_NOISE = (
    re.compile(r"\bweight\s*:\s*\"[^\"]*\""),        # render_pages.js capture config
    re.compile(r"weight\s*\([^)\n]*\)"),               # "weight (1 -> 2.5)" in prose
    re.compile(r"again at [\d.]+"),                      # prose about the stroke closing
    re.compile(r"at weight [\d.]+"),
    re.compile(r"FILL_WEIGHT[^\n]*"),                    # the ladder in the page script
    re.compile(r"<button[^>]*data-weight[^>]*>[^<]*</button>"),
)


def visible(text, path):
    """What a reader sees: markup carries coordinates that look like versions."""
    text = re.sub(r'\sd="[^"]*"', " ", text)          # path data, in HTML and in JS
    for noise in WEIGHT_NOISE:
        text = noise.sub(" ", text)
    return re.sub(r"<[^>]*>", " ", text) if path.endswith(".html") else text


def quoted_elsewhere(old, old_code, plans, codes=None):
    """Every page that still prints the old version once the known labels have moved.

    A bump that knows only some of the shapes a version takes leaves the rest behind,
    which is how the tools page kept a 2.3 pill while every other label said 2.4.
    """
    planned = {page: after for page, _, after, _ in plans}
    # A letter or digit against the number means it is part of a word rather than a
    # label: libatk-bridge2.0-0t64 is a package, not a version of this app.
    token = re.compile(r"(?<![0-9A-Za-z.])%s(?![0-9A-Za-z])" % re.escape(old))
    # The code is a live label too, and it is spelled several ways, so it is matched
    # as a phrase rather than as a number that could be anything.
    code = re.compile(r"version\s*code\s*%d\b" % old_code, re.IGNORECASE)
    pages = dict.fromkeys(LABEL_FILES + sorted(str(path.relative_to(ROOT))
                                                for path in (ROOT / "docs").rglob("*.html")))
    found = []
    for page in pages:
        if page.startswith(SWEEP_SKIP):
            continue
        if page in planned:
            text = planned[page]
        elif (ROOT / page).exists():
            text = (ROOT / page).read_text(encoding="utf-8")
        else:
            continue
        for number, line in enumerate(visible(text, page).splitlines(), 1):
            stated = [int(value) for value in
                      re.findall(r"version\s*code\s*(\d+)", line, re.IGNORECASE)]
            # A code no app ships, or one printed beside an app that ships another,
            # is wrong even when it is not the code this bump is moving.
            wrong = codes is not None and (
                any(value not in codes.values() for value in stated)
                or any(value != codes[app_id] for app_id, shipped in codes.items()
                       if APPS[app_id].lower() in line.lower() for value in stated))
            if token.search(line) or code.search(line) or wrong:
                found.append("%s:%d  %s" % (page, number, " ".join(line.split())[:90]))
    return found


def show_diffs(plans, keep=14):
    """The lines each file would lose and gain, so the plan can be read, not trusted."""
    print("\nthe edits")
    for name, before, after, hits in plans:
        if not hits:
            continue
        diff = difflib.unified_diff(before.splitlines(keepends=True),
                                    after.splitlines(keepends=True),
                                    fromfile="a/" + name, tofile="b/" + name, n=0)
        lines = [line.rstrip("\n") for line in diff]
        for line in lines[:keep + 2]:
            print("  " + line)
        if len(lines) > keep + 2:
            print("  ... %d more line(s) in this diff" % (len(lines) - keep - 2))


def stale_boards(slugs):
    """The artboards made from a page that is changing, and the states of the index."""
    found = []
    for board in sorted((ROOT / "figma").glob("[0-9][0-9]-*.svg")):
        named = re.match(r"[0-9]{2}-(.+)-(desktop|phone)\.svg$", board.name)
        if named and named.group(1) in slugs:
            found.append("figma/" + board.name)
    if "index" in slugs:
        for state in STATES:
            if (ROOT / "figma" / (state + ".svg")).exists():
                found.append("figma/%s.svg" % state)
    return found


def canvas_note():
    """The stacked canvas as it stands, for comparing a re-bundle against it."""
    path = ROOT / "figma/site-figma.svg"
    if not path.exists():
        return ""
    with path.open(encoding="utf-8") as handle:
        head = handle.read(400)
    width = re.search(r'width="([0-9.]+)"', head)
    height = re.search(r'height="([0-9.]+)"', head)
    if not (width and height):
        return ""
    return ", site-figma.svg %s\u00d7%s, %s" % (width.group(1), height.group(1),
                                                human(path.stat().st_size))


def capture_names(pages):
    """The gallery captures of those pages, matched out of the capture list."""
    text = (ROOT / "tools/render_pages.js").read_text(encoding="utf-8")
    shots = re.findall(r'name: "([^"]+)", page: "([^"]*)"', text)
    return [name for name, page in shots if page in pages]


def docs_size():
    return sum(path.stat().st_size for path in (ROOT / "docs").rglob("*") if path.is_file())


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


def add_note(app, new, title, note, dry):
    data = json.loads(NOTES.read_text(encoding="utf-8"))
    for entry in data["notes"]:
        if entry["id"] == app and entry["version"] == new and entry["title"] == title:
            return "already there"
    data["notes"].insert(0, {"id": app, "version": new, "title": title, "note": note})
    if not dry:
        NOTES.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return "note %s \u00b7 %s %s" % (new, title,
                                    "inserted above the newest" if not dry
                                    else "would go above the newest")


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
    parser.add_argument("--commit", action="store_true",
                        help="commit the release, then commit the history page "
                             "that names it, so a deploy has nothing stale")
    parser.add_argument("--deploy", action="store_true",
                        help="end with the deployment request, or run --deploy-cmd")
    parser.add_argument("--deploy-cmd",
                        help="a deploy CLI to run instead of printing the Sites request")
    parser.add_argument("--check-notes", action="store_true",
                        help="only verify the release notes against the history, change nothing")
    parser.add_argument("--app", choices=sorted(APPS), default="icons",
                        help="which tool to move (default: icons)")
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

    # A release commit stages the whole tree, so anything already changed would ride
    # along with it. Say so before the bump writes a single file, not after.
    if args.commit:
        dirty = unrelated_changes()
        if dirty:
            listed = ", ".join(dirty[:4]) + (
                " and %d more" % (len(dirty) - 4) if len(dirty) > 4 else "")
            problem = ("%s %s already changed, so a release commit would carry work that "
                       "is not part of the release; commit or stash %s and run the bump again"
                       % (listed, "was" if len(dirty) == 1 else "were",
                          "it" if len(dirty) == 1 else "them"))
            if args.dry_run:
                print("  --commit would refuse: " + problem)
            else:
                raise SystemExit("refusing to commit: " + problem)

    # A note whose label is not the version it shipped in is a broken history, and
    # bumping on top of it would only bury the mistake.
    if report_notes():
        raise SystemExit("refusing to bump: give those notes their real versions first")

    captures = args.build and not args.skip_captures
    toolchain = capture_preflight() if captures else None

    app, app_name = args.app, APPS[args.app]
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    tool = next(item for item in catalog["tools"] if item["id"] == app)
    old, old_code = tool["version"], int(tool["version_code"])
    if new == old:
        raise SystemExit("the catalog is already at %s" % old)
    new_code = old_code + 1
    old_apk, new_apk = tool["apk"], "/%s-%s.apk" % (app_name, new)
    old_file = ROOT / "docs" / old_apk.lstrip("/")
    if not old_file.exists():
        raise SystemExit("the shipped APK %s is missing" % old_file.relative_to(ROOT))
    old_size, old_sha = fmt(old_file.stat().st_size), digest(old_file)

    dry = args.dry_run
    print("%s %s (versionCode %d) -> %s (versionCode %d), %s -> %s"
          % (app_name, old, old_code, new, new_code, old_apk, new_apk))

    tool.update({"version": new, "version_code": new_code, "apk": new_apk})
    if not dry:
        CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n",
                           encoding="utf-8")
    print("  catalog.json           version, versionCode and APK")

    cases = bump_build_sh(old_apk, old, new_apk, new, old_code, new_code, dry)
    print("  build.sh               %d build case(s)" % cases)

    clash = any(int(other["version_code"]) == old_code
                for other in catalog["tools"] if other["id"] != app)
    pairs = label_pairs(app, old, new, old_code, new_code, clash)
    plans = [(page, *planned(page, pairs)) for page in LABEL_FILES]
    # The labels above are the shapes this tool knows. Anything else that still
    # prints the old version is a label nobody will move, so the bump stops here.
    codes = {item["id"]: int(item["version_code"]) for item in catalog["tools"]}
    leftovers = quoted_elsewhere(old, old_code, plans, codes)
    if leftovers:
        print("refusing to bump: these still quote %s %s once the labels move"
              % (app_name, old))
        for line in leftovers[:12]:
            print("  " + line)
        raise SystemExit("teach label_pairs the form, or fix the page, then run again")
    for page, before, after, hits in plans:
        if not dry and hits:
            (ROOT / page).write_text(after, encoding="utf-8")
        if hits:
            print("  %-30s %d label(s)" % (page, hits))

    if args.title:
        print("  %-30s %s"
              % ("tools/release_notes.json", add_note(app, new, args.title, args.note, dry)))

    if dry:
        print("\nthe sweep")
        print("  nothing else prints %s %s in a form the labels miss" % (app_name, old))
        pages = {PAGE_OF[plan[0]] for plan in plans if plan[0] in PAGE_OF and plan[3]}
        slugs = {SLUG_OF[page] for page in pages}
        show_diffs(plans)

        print("\nthe hand-off")
        stale = stale_boards(slugs)
        if stale:
            print("  these quote %s %s and go stale with the pages:" % (app_name, old))
            for board in stale:
                print("    " + board)
        else:
            print("  no artboard quotes %s %s, so none of them change" % (app_name, old))
        boards = len(list((ROOT / "figma").glob("[0-9][0-9]-*.svg")))
        print("  one export run rewrites all %d boards%s" % (boards, canvas_note()))
        print("  then figma_bundle.py, then figma_publish.py, in that order")

        print("\nthe history page")
        history = (ROOT / "docs/releases/index.html").read_text(encoding="utf-8")
        print("  %d releases now; %s %s gains its row once the APK is committed,"
              % (history.count('<div class="rel-top">'), app_name, new))
        print("  because build_releases.py reads the day, size and sha out of git")
        releases = sorted(board.name for board in (ROOT / "figma").glob("*-releases-*.svg"))
        if releases:
            print("  the releases boards gain that row with it: " + ", ".join(releases))

        print("\nthe captures")
        names = capture_names({page.lstrip("/") for page in pages})
        print("  the run is retaken in full; these show a page that changes:")
        for name in names:
            print("    docs/renders/%s.webp" % name)

        if args.title:
            print("\nthe notes, with this one added")
            report_notes({"id": app, "version": new, "title": args.title, "note": args.note})

        print("\nthe deploy")
        now = docs_size()
        print("  docs: %s of 10.0 MiB, %s left - %s"
              % (human(now), human(abs(BUDGET - now)),
                 "OVER BUDGET - the host will refuse this deploy" if now > BUDGET else "ok"))
        print("  docs%s (%s today) is replaced once the APK is built; its bytes are"
              % (new_apk, old_size))
        print("  unknown until then, and so is the re-exported artboard weight")

        print("\ndry run: nothing written")
        return 0

    if not args.build:
        print("next: ./build.sh %s, then copy dist/%s-%s.apk into docs/, delete\n"
              "      docs%s, and patch %s and %s into the pages\n"
              "      (or run this again with --build)"
              % (app, app_name, new, old_apk, old_size, old_sha[:10]))
        return 0

    print("building...")
    subprocess.run([str(BUILD), app], cwd=ROOT, check=True)
    built = ROOT / "dist" / ("%s-%s.apk" % (app_name, new))
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

    # The history page is written from git, so it gains the row for this build only
    # once the build is committed.
    print("  " + regenerate_history())

    if args.commit:
        # A row records the commit that shipped the APK, so that commit has to exist
        # before the row can: one commit for the release, a second for the page that
        # now names it. Asking for both here is what keeps a deploy from stopping.
        release = commit_all("%s %s%s" % (app_name, new,
                                          (": " + args.title) if args.title else ""))
        print("  committed release                  %s" % release)
        page = ROOT / "docs/releases/index.html"
        before = page.read_bytes()
        print("  " + regenerate_history())
        if page.read_bytes() != before:
            print("  committed history page             %s"
                  % commit_all("History: %s %s" % (app_name, new)))
        else:
            print("  the history page needed no commit of its own")

    if args.deploy:
        if args.deploy_cmd:
            print("deploying with: %s" % args.deploy_cmd)
            subprocess.run(args.deploy_cmd, shell=True, cwd=ROOT, check=True)
            print("  deploy command finished")
        else:
            print("deploy: this machine has no deploy CLI, so the preview goes out through the")
            print("        Sites tool. Give it this request:")
            print(json.dumps(deploy_request(), indent=2))

    print("%s %s is in docs/ and the shelf agrees with it" % (app_name, new))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
