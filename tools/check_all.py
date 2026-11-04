#!/usr/bin/env python3
"""Run every check on the shelf, and say whether this tree is shippable.

    python3 tools/check_all.py                                   # nothing but python
    python3 tools/check_all.py --browser http://127.0.0.1:8799/  # + the rendered half
    python3 tools/check_all.py --artboards                       # + a re-export diff

Every gate runs even after one fails, so a single pass shows the whole picture rather
than the first thing to break, and the exit status is 1 when anything failed. The one
step it cannot own is the APK build, which needs a JDK, the android-35 platform and the
keystore; `./build.sh` is that step. Nothing here talks to a remote host.
"""

import argparse
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# The summary line each gate ends with, so a pass prints its verdict and not its
# working. A gate whose last line is the interesting one has no entry here.
VERDICT = {"palette": "palette:", "assets": "assets:", "site": "site:",
           "figma": "figma check:", "notes": "notes regression:",
           "bundle": "bundle check:", "artboards": "artboards:"}


def gates(browser, artboards):
    rows = [
        ["palette", [sys.executable, "tools/check_palette.py"]],
        ["assets", [sys.executable, "tools/hash_assets.py", "--check"]],
        ["site", [sys.executable, "tools/build_site.py", "--check"]],
        ["catalog", [sys.executable, "tools/build_catalog.py", "--check"]],
        ["figma", [sys.executable, "tools/check_figma.py"]],
        ["pantone", [sys.executable, "tools/pantone_sheet.py", "--check"]],
        ["notes", [sys.executable, "tools/notes_regression.py"]],
        ["bundle", ["./build.sh", "stage"]],
    ]
    if artboards:
        rows.insert(len(rows) - 1, ["artboards", [sys.executable, "tools/check_artboards.py"]])
    if browser:
        rows[0][1].extend(["--browser", browser])
    return rows


def summary(name, lines):
    """What the gate concluded, rather than the last thing it happened to print."""
    wanted = VERDICT.get(name)
    if wanted:
        for line in reversed(lines):
            if line.startswith(wanted):
                return line
    return lines[-1] if lines else "(no output)"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--browser", metavar="BASE",
                        help="also drive the pages over a server, as "
                             "tools/check_palette.py --browser does")
    parser.add_argument("--artboards", action="store_true",
                        help="also re-export the artboards and diff them against figma/")
    args = parser.parse_args()

    rows = gates(args.browser, args.artboards)
    print("the checks")
    failures = []
    began = time.monotonic()
    for name, cmd in rows:
        started = time.monotonic()
        done = subprocess.run(cmd, cwd=str(ROOT), capture_output=True, text=True)
        took = time.monotonic() - started
        lines = [line.strip() for line in ((done.stdout or "") + (done.stderr or "")).splitlines()
                 if line.strip() and not line.strip().startswith("==")]
        mark = "ok" if done.returncode == 0 else "FAIL"
        print("  %-10s %-4s %5.1fs  %s" % (name, mark, took, summary(name, lines)[:104]))
        if done.returncode:
            failures.append((name, lines))
    total = time.monotonic() - began

    if failures:
        for name, lines in failures:
            print("\n%s said:" % name)
            for line in (lines or ["(no output)"])[-8:]:
                print("  " + line)
        print("\nchecks: %d of %d gates failed in %.1fs"
              % (len(failures), len(rows), total))
        return 1
    print("\nchecks: %d gates, all green in %.1fs" % (len(rows), total))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
