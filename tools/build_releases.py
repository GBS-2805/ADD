#!/usr/bin/env python3
"""Write docs/releases/index.html out of the repository's own history.

    python3 tools/build_releases.py

For every app in tools/catalog.json it walks the commits that changed the
catalog and records each version it saw: the day that version first appeared,
the commit that shipped it, and the size of the APK blob at that commit, read
with `git cat-file -s` instead of from a page or a note. Release-note titles and
sentences come from tools/release_notes.json -- the only copy, which the tools page
links to rather than repeating -- and bump_version.py checks each one against the
commit that first added it.

The chrome -- band, pull-down and side tab -- is spliced from
docs/download/index.html so this page cannot drift from the rest of the shelf,
and the side tab's Releases row is marked current. Nothing here is timed: the
same history writes the same bytes, so a re-run on an unchanged tree is a no-op.
"""

import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from hash_assets import pin  # noqa: E402 - the tools directory is on the path above

CATALOG = ROOT / "tools/catalog.json"
NOTES = ROOT / "tools/release_notes.json"
DOWNLOAD = ROOT / "docs/download/index.html"
TEMPLATE = ROOT / "tools/releases_template.html"
OUT = ROOT / "docs/releases/index.html"

APK = re.compile(r"^docs/(Icons|Compress|ADD)-([0-9][0-9.]*)\.apk$")
MONTHS = ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")


def git(*args):
    done = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)
    return done.stdout if done.returncode == 0 else None


def day(iso):
    year, month, rest = iso.split("-")
    return "%d %s %s" % (int(rest), MONTHS[int(month) - 1], year)


def fmt(number):
    return "{:,}".format(number)


def human(number):
    """A size a reader can scan: whole MB, whole KB, then bytes. No decimals."""
    if number >= 1048576:
        return "%d MB" % round(number / 1048576)
    if number >= 1024:
        return "%d KB" % round(number / 1024)
    return "%d bytes" % number


def releases():
    """Every APK this repository shipped, with the commit and the size that shipped it."""
    history = git("log", "--reverse", "-M", "--name-status",
                  "--format=\x01%H\t%ad", "--date=short", "--", "docs")
    if not history:
        raise SystemExit("build_releases: no git history to read")
    found, order = {}, []
    for block in history.split("\x01"):
        block = block.strip("\n")
        if not block:
            continue
        head, _, listed = block.partition("\n")
        sha, date = head.split("\t")
        for line in listed.splitlines():
            columns = line.split("\t")
            if len(columns) < 2:
                continue
            # A rename lists old then new; the new path is the one that shipped.
            hit = APK.match(columns[-1])
            if not hit:
                continue
            app, version = hit.group(1).lower(), hit.group(2)
            size = git("cat-file", "-s", "%s:%s" % (sha, columns[-1]))
            if not size or not size.strip().isdigit():
                continue
            key = (app, version)
            record = found.get(key)
            if record is None:
                found[key] = {"id": app, "version": version, "date": date, "sha": sha[:7],
                              "bytes": int(size.strip()),
                              "apk": "/" + columns[-1].split("/")[-1]}
                order.append(key)
            else:
                # A rebuild replaces the file without moving the version; the
                # later size is the one that ended up shipping.
                record["bytes"] = int(size.strip())
    return [found[key] for key in order]


def notes():
    """The release notes, grouped by the app and version they shipped in."""
    grouped = {}
    for entry in json.loads(NOTES.read_text(encoding="utf-8"))["notes"]:
        grouped.setdefault((entry["id"], entry["version"]), []).append(
            (entry["title"], entry["note"]))
    return grouped


def rows(items, written, live):
    out = []
    for record in reversed(items):
        live_pill = (' <span class="pill live">shipping</span>'
                     if record["version"] == live.get(record["id"]) else "")
        entries = written.get((record["id"], record["version"]), [])
        body = "".join('\n          <p class="rel-note"><b>%s</b> %s</p>' % (title, sentence)
                       for title, sentence in entries)
        if not body:
            body = '\n          <p class="rel-none">No release note was written for this build.</p>'
        out.append("      <li>\n"
                   '        <div class="rel-top"><b>%s</b>%s'
                   '<span class="rel-meta"><span>%s</span><span>%s</span>'
                   '<code class="sha">%s</code></span></div>%s\n'
                   "      </li>"
                   % (record["version"], live_pill, day(record["date"]),
                      human(record["bytes"]), record["sha"], body))
    return "\n".join(out)


def swap(block, old, new, what):
    """Replace one piece of another page's copy, or stop.

    These are literal matches against a page this tool does not own, so a rename there
    would otherwise leave the releases band saying the download page's words, or
    describing the download page instead of the history. One match is the contract.
    """
    count = block.count(old)
    if count != 1:
        raise SystemExit("build_releases: expected one %s in the download page, found %d; "
                         "the releases band would ship the wrong copy" % (what, count))
    return block.replace(old, new, 1)


def chrome():
    """The band and both panels, taken from a page that already carries them."""
    text = DOWNLOAD.read_text(encoding="utf-8")
    block = text[text.index('<header class="bar">'):text.index("</aside>") + len("</aside>")]
    block = swap(block, '<a class="page-name" href="./">Download</a>',
                 '<a class="page-name" href="./">Releases</a>', "page name")
    block = swap(block, '<a href="../download/" aria-current="page">Download<span>one app</span></a>',
                 '<a href="../download/">Download<span>one app</span></a>', "menu row marker")
    block = swap(block, '<a href="../download/" aria-current="page">Download<span>1 app</span></a>',
                 '<a href="../download/">Download<span>1 app</span></a>', "drawer row marker")
    block = swap(block, '<a href="../releases/">Releases<span>history</span></a>',
                 '<a href="../releases/" aria-current="page">Releases<span>history</span></a>',
                 "Releases row")
    return swap(block, "ADD \u00b7 a design shelf: icons, type, photos and words, "
                       "in one quiet place.",
                "Every build this shelf has shipped, read back out of its own history.",
                "drawer note")


def kicker(items):
    if not items:
        return "Nothing yet"
    sizes = [record["bytes"] for record in items]
    return "%d releases, newest first \u00b7 %s to %s" % (
        len(items), human(min(sizes)), human(max(sizes)))


def main():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    live = {tool["id"]: tool.get("version") for tool in catalog["tools"]}
    everything = releases()
    written = notes()
    # One app, one stream. Icons and Compress are ids this history still carries, but
    # they are the same shelf, so the page lists every APK rather than splitting it.
    dates = sorted(record["date"] for record in everything)

    page = TEMPLATE.read_text(encoding="utf-8")
    meta = "\n".join("      <span>%s</span>" % line for line in (
        "%d releases" % len(everything),
        "%d APKs, Icons through ADD" % len(everything),
        "%s \u2013 %s" % (day(dates[0]), day(dates[-1])),
        "Read from git, not typed",
    ))
    fills = {
        "__CHROME__": chrome(),
        "__META_LINES__": meta,
        "__TOTAL__": str(len(everything)),
        "__APP_LIVE__": live.get(catalog["tools"][0]["id"]) or "unreleased",
        "__APP_KICKER__": kicker(everything),
        "__APP_ROWS__": rows(everything, written, live),
    }
    for key, value in fills.items():
        page = page.replace(key, value)
    left = re.search(r"__[A-Z_]+__", page)
    if left:
        raise SystemExit("build_releases: %s was never filled" % left.group(0))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(pin(page), encoding="utf-8")
    print("wrote %-32s %6d bytes" % (OUT.relative_to(ROOT), OUT.stat().st_size))
    noted = sum(1 for r in everything if written.get((r["id"], r["version"])))
    print("  %2d releases, %2d with a note, %s to %s" % (
        len(everything), noted,
        human(min(r["bytes"] for r in everything)), human(max(r["bytes"] for r in everything))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
