#!/usr/bin/env python3
"""Move the Icons release to a new version in one step.

    python3 tools/bump_version.py 2.4 --title "All brands in the app" \\
        --note "One sentence for the release list." --build

In order, it moves:

  * tools/catalog.json            version, versionCode, APK name
  * build.sh                      both the `icons` and `all` build cases
  * every live version label      the shelf pages, README, AGENTS, the capture list
  * the release notes             one new entry at the top, above the newest

With --build it then runs build.sh, copies the signed APK into docs/, deletes the
previous one, re-measures its size and SHA-256, writes those into the same pages and
rebuilds the catalogs, so the shelf agrees with the file it ships.

Without --build nothing is compiled and the size and checksum keep their old values,
which leaves the shelf failing `build_catalog.py --check` until the build runs.
--dry-run prints the planned edits and writes nothing.

The release-note list is never rewritten: a note keeps the version it was written
for, which is what a blanket find-and-replace destroyed the first time.
"""

import argparse
import hashlib
import json
import pathlib
import re
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools/catalog.json"
BUILD = ROOT / "build.sh"
NOTES = ROOT / "docs/tools/index.html"
LABEL_FILES = ["docs/index.html", "docs/download/index.html", "docs/icons/index.html",
               "docs/tools/index.html", "README.md", "AGENTS.md", "tools/render_pages.js"]

# A release-note line carries its own version and must never be bumped.
NOTE_LINE = re.compile(r"<li><b>Icons [0-9.]+ \u00b7 ")
VERSION = re.compile(r"^[0-9]+(\.[0-9]+)*$")

NOTE_RE = re.compile(r"<li><b>Icons ([0-9.]+) \u00b7 ([^<]+)</b>")


def notes_on_page():
    return NOTE_RE.findall(NOTES.read_text(encoding="utf-8"))


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)


def shipped_with(title):
    """The Icons version in the commit that first added a note, and its short sha."""
    found = git("log", "--reverse", "--format=%H", "-S", title, "--", "docs/tools/index.html")
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
                       if tool["id"] == "icons")
    except Exception:
        return None, "catalog unreadable at " + sha[:8]
    return version, sha[:8]


def report_notes():
    """Print every note whose label is not the version it shipped in; return how many."""
    if not (ROOT / ".git").exists():
        print("release notes: no git history to check against")
        return 0
    notes = notes_on_page()
    problems = 0
    for label, title in notes:
        version, where = shipped_with(title)
        if version is None:
            print("  note %s \u00b7 %s: %s, unverified" % (label, title, where))
        elif version != label:
            problems += 1
            print("  note %s \u00b7 %s shipped in %s (%s)" % (label, title, version, where))
    if problems:
        print("release notes: %d note(s) disagree with the version they shipped in" % problems)
    else:
        print("release notes: %d notes, each label matches the version it shipped in" % len(notes))
    return problems


def fmt(number):
    return "{:,}".format(number)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rewrite(path, pairs, dry):
    """Replace pairs line by line, skipping release-note lines."""
    path = ROOT / path
    text = path.read_text(encoding="utf-8")
    out, hits = [], 0
    for line in text.splitlines(keepends=True):
        if NOTE_LINE.search(line):
            out.append(line)
            continue
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
    text = NOTES.read_text(encoding="utf-8")
    if "<li><b>Icons %s \u00b7 %s</b>" % (new, title) in text:
        return "already there"
    marker = NOTE_LINE.search(text)
    if not marker:
        raise SystemExit("no release-note list found in docs/tools/index.html")
    line_start = text.rfind("\n", 0, marker.start()) + 1
    indent = text[line_start:marker.start()]
    entry = "%s<li><b>Icons %s \u00b7 %s</b><span>%s</span></li>\n" % (indent, new, title, note)
    if not dry:
        NOTES.write_text(text[:line_start] + entry + text[line_start:], encoding="utf-8")
    return "note %s above the newest" % ("inserted" if not dry else "would go")


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("version", nargs="?", help="the new version, e.g. 2.4")
    parser.add_argument("--check-notes", action="store_true",
                        help="only verify the release notes against the history, change nothing")
    parser.add_argument("--title", help="release-note title, after the version")
    parser.add_argument("--note", help="release-note sentence")
    parser.add_argument("--build", action="store_true",
                        help="build and sign the APK, copy it into docs/ and re-measure it")
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

    # A note whose label is not the version it shipped in is a broken history, and
    # bumping on top of it would only bury the mistake.
    if report_notes():
        raise SystemExit("refusing to bump: give those notes their real versions first")

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
        print("  %-30s %s" % ("docs/tools/index.html", add_note(new, args.title, args.note, dry)))

    if dry:
        print("dry run: nothing written; %s and %s would be replaced once the APK is built"
              % (old_size, old_sha[:10]))
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

    for script, extra in (("tools/build_catalog.py", []), ("tools/build_catalog.py", ["--check"])):
        subprocess.run([sys.executable, str(ROOT / script), *extra], cwd=ROOT, check=True)
    print("Icons %s is in docs/ and the shelf agrees with it" % new)
    print("next: node tools/render_pages.js <baseUrl> artifacts  (the captures still show %s)" % old)
    print("      deploy the preview, then commit")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
