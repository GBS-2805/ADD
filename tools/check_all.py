#!/usr/bin/env python3
"""Run every check on the shelf, and say whether this tree is shippable.

    python3 tools/check_all.py                                   # nothing but python
    python3 tools/check_all.py --browser http://127.0.0.1:8799/  # + palette and menu rendering
    python3 tools/check_all.py --artboards                       # + a re-export diff
    python3 tools/check_all.py --visual                          # + pixel baselines and engine tests
    python3 tools/check_all.py --live https://artbase-olive.vercel.app/
    python3 tools/check_all.py --perf                            # report-only browser timing

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
VERDICT = {"palette": "palette:", "menu": "menu:", "assets": "assets:",
           "upload": "upload:",
           "links": "links:", "rules": "rules:", "claims": "claims:",
           "secrets": "secrets:", "workflows": "workflows:",
           "dependencies": "dependencies:", "budget": "budget:",
           "images": "images:", "motion": "motion:", "csp": "csp:",
           "seo": "seo_meta:",
           "renders": "renders:",
           "site": "site:", "packs": "build_packs:",
           "figma": "figma check:",
           "notes": "notes regression:", "bundle": "bundle check:",
           "artboards": "artboards:", "engine": "engine:",
           "engine-claims": "claims:", "chrome": "chrome:",
           "headers": "headers:", "perf": "web-perf:",
           "glyphs": "glyphs:", "effects": "effects:",
           "effects-native": "effects-native:", "editor": "editor:"}

# Lines a gate prints on every run rather than only on failure, echoed after its row.
# The engine gate's frame budget is timing-dependent, so the visual log carries each
# family's warmup passes and samples and a failure can be read without re-running it.
DETAIL = {"engine": "engine-samples:"}


def gates(browser, artboards, visual, live=None, handoff=None, perf=False,
          editor=True):
    rows = [
        ["palette", [sys.executable, "tools/check_palette.py"]],
        ["assets", [sys.executable, "tools/hash_assets.py", "--check"]],
        ["upload", [sys.executable, "tools/check_upload.py"]],
        ["links", [sys.executable, "tools/check_links.py"]],
        ["rules", [sys.executable, "tools/check_agent_rules.py", "--check"]],
        ["claims", [sys.executable, "tools/check_claims.py", "--check"]],
        ["secrets", [sys.executable, "tools/check_secrets.py", "--check"]],
        ["workflows", [sys.executable, "tools/check_workflows.py", "--check"]],
        ["dependencies", [sys.executable, "tools/check_dependencies.py", "--check"]],
        ["budget", [sys.executable, "tools/check_web_budget.py", "--check"]],
        ["images", [sys.executable, "tools/check_images.py", "--check"]],
        ["motion", [sys.executable, "tools/check_motion.py", "--check"]],
        ["csp", [sys.executable, "tools/build_csp.py", "--check"]],
        ["seo", [sys.executable, "tools/seo_meta.py", "--check"]],
        ["renders", [sys.executable, "tools/check_renders.py", "--check"]],
        ["site", [sys.executable, "tools/build_site.py", "--check"]],
        ["classic", [sys.executable, "tools/archive_classic.py", "--check"]],
        ["catalog", [sys.executable, "tools/build_catalog.py", "--check"]],
        ["packs", [sys.executable, "tools/build_packs.py", "--check"]],
        ["figma", [sys.executable, "tools/check_figma.py"]],
        # Every face the pages ship covers every character they paint. Its own
        # failure mode is a silent system-font substitution that moves the
        # artboards, so it sits next to the other artboard inputs.
        ["glyphs", [sys.executable, "tools/check_glyphs.py"]],
        # The library's effects catalog: the file Twin renders from, its shape, and
        # the native tables it must agree with. No JDK: the Kotlin is read as text,
        # so the dependency points from the table towards the JSON, not backwards.
        ["effects", [sys.executable, "tools/build_effects.py", "--check"]],
        ["effects-native", [sys.executable, "tools/check_effects_native.py"]],
        # One Editor row, and the row and the shelf's Editor card reach the editor
        # each build stages: the browser canvas in research, Quick in the shipping APK.
        ["editor-rows", [sys.executable, "tools/check_editor_rows.py"]],
        # The two real bundles, not the routing test's flag: research ships the
        # page and its assets, release ships none. Skips without local APKs.
        ["editor-bundles", [sys.executable, "tools/check_editor_bundles.py"]],
        ["pantone", [sys.executable, "tools/pantone_sheet.py", "--check"]],
        ["notes", [sys.executable, "tools/notes_regression.py"]],
        ["bundle", ["./build.sh", "stage"]],
        # The editor's own suite. It builds the shelf's /editor/ page but is
        # outside every other gate, so before this row existed its 255 tests ran
        # only by hand. Last because it is the slowest and the least likely to be
        # the cause of a shelf failure; --no-editor skips it on a machine without
        # editor/node_modules.
        ["editor", [sys.executable, "tools/check_editor.py"]],
    ]
    if artboards:
        rows.insert(len(rows) - 1, ["artboards", [sys.executable, "tools/check_artboards.py"]])
    if browser:
        rows[0][1].extend(["--browser", browser])
        rows.insert(1, ["menu", ["node", "tools/check_menu.js", browser]])
    if visual:
        # Both compare against PNGs committed under tools/, so a drift in either is a
        # push failure rather than something someone has to remember to run.
        rows[-1:-1] = [
            ["engine", ["bash", "tools/engine-spike/verify-suite.sh"]],
            # The engine gate writes the XML this claim check reads; keep it here,
            # before the chrome gate replaces the result directory with its own run.
            ["engine-claims", [sys.executable, "tools/check_claims.py", "--check", "--engine"]],
            ["chrome", ["bash", "tools/engine-spike/verify-chrome.sh"]],
        ]
    if live:
        command = [sys.executable, "tools/check_headers.py", "--origin", live]
        if handoff:
            command.extend(["--handoff-origin", handoff])
        rows.append(["headers", command])
    if perf:
        url = browser or "http://127.0.0.1:8799/"
        rows.append(["perf", ["node", "tools/check_web_perf.js", "--url", url]])
    if not editor:
        rows = [row for row in rows if row[0] != "editor"]
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
    parser.add_argument("--visual", action="store_true",
                        help="also run the visual and engine gates: the Robolectric engine chrome "
                             "goldens and the whole engine test suite. Needs a JDK 17 "
                             "and the android-35 platform; "
                             ".github/workflows/checks.yml installs them")
    parser.add_argument("--live", metavar="ORIGIN",
                        help="also check live security headers at ORIGIN")
    parser.add_argument("--handoff-origin", metavar="ORIGIN",
                        help="handoff origin to include with --live")
    parser.add_argument("--perf", action="store_true",
                        help="also run the report-only browser timing measurement; "
                             "uses --browser as the base URL or the local port 8799")
    parser.add_argument("--no-editor", dest="no_editor",
                        action="store_true",
                        help="skip the editor gate (typecheck, unit tests, payload). "
                             "Needs node_modules installed in editor/; CI runs it in its "
                             "own job because the typecheck alone takes 45s")
    args = parser.parse_args()

    rows = gates(args.browser, args.artboards, args.visual, args.live,
                 args.handoff_origin, args.perf, editor=not args.no_editor)
    print("the checks")
    failures = []
    began = time.monotonic()
    for name, cmd in rows:
        started = time.monotonic()
        done = subprocess.run(cmd, cwd=str(ROOT), capture_output=True, text=True)
        took = time.monotonic() - started
        lines = [line.strip() for line in ((done.stdout or "") + (done.stderr or "")).splitlines()
                 if line.strip() and not line.strip().startswith("==")]
        # 2 means "could not run here", which is not a pass and not a failure: the
        # shelf job has no node toolchain, and the editor CI job runs this gate with
        # dependencies installed. Saying "ok" would be a lie; failing would make a
        # clean clone red for the wrong reason.
        if done.returncode == 0:
            mark = "ok"
        elif done.returncode == 2:
            mark = "skip"
        else:
            mark = "FAIL"
        print("  %-10s %-4s %5.1fs  %s" % (name, mark, took, summary(name, lines)[:104]))
        wanted_detail = DETAIL.get(name)
        if wanted_detail:
            for line in (line for line in lines if line.startswith(wanted_detail)):
                print("    " + line)
        if done.returncode and done.returncode != 2:
            failures.append((name, lines))
    total = time.monotonic() - began

    if failures:
        for name, lines in failures:
            print("\n%s said:" % name)
            # The verdict line carries the failure detail (the engine gate puts the
            # failing test and its samples there); the trailing lines are usually the
            # runner's build error, which does not name the assertion.
            wanted = VERDICT.get(name)
            detail = [line for line in lines if wanted and line.startswith(wanted)][:3]
            for line in detail:
                print("  " + line)
            for line in (lines or ["(no output)"])[-8:]:
                print("  " + line)
        print("\nchecks: %d of %d gates failed in %.1fs"
              % (len(failures), len(rows), total))
        return 1
    print("\nchecks: %d gates, all green in %.1fs" % (len(rows), total))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
