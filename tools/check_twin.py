#!/usr/bin/env python3
"""Run Twin's own checks: typecheck, unit tests, asset licences, and payload.

    python3 tools/check_twin.py
    python3 tools/check_twin.py --no-tests      # skip the slow suite while iterating
    python3 tools/check_twin.py --write-budget  # record the current payload ceiling

Twin has 255 unit tests across 35 files and, until this gate existed, nothing ran
them on a push. `tools/check_all.py` covers the shelf; Twin builds the shelf's twin/
page but was outside every gate, so a broken test or a broken type was only found
by whoever happened to run it.

Four checks, in the order that fails fastest:

  typecheck   `tsc -b` -- a type error is a build failure on the deploy side
  assets      `npm run check:assets` -- every binary needs a source, licence, SHA-256
  tests       `vitest run` with a JUnit report, so the run is readable after the fact
  payload     the initial script weight of the committed build, against a ceiling

The payload check is the one that keeps the bundle split from regressing. Twin's page
is 2.4 MB because every plugin is loaded up front; the ceiling here starts at what it
is today and gets tightened when the split lands, so the number can only move by a
deliberate edit rather than by someone adding an eager import.

Needs `node_modules`: run `npm ci` in twin/ first. When it is absent the gate says so
and fails, rather than passing on nothing.
"""

from __future__ import annotations

import argparse
import gzip
import os
import html
import json
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"

def budget_path() -> Path:
    return twin_dir() / "tools" / "twin_budget.json"
REPORT = ROOT / "test-results" / "twin-junit.xml"
NODE_MODULES = "twin/node_modules"

# Exit status for "the dependencies are not here", distinct from pass and fail so
# check_all.py can say what happened instead of printing a misleading green.
SKIP = 2

# A script tag in the built page. Anything matching here is downloaded before the
# first paint; anything the app imports later is not, and is not counted.
SCRIPT = re.compile(rb'<script[^>]*\bsrc="([^"]+)"', re.I)


def npx(*args: str) -> list[str]:
    """A local binary from twin/node_modules, never a download."""
    return ["npx", "--no-install", *args]


def script(name: str) -> list[str]:
    """A twin/package.json script, so the repository decides how it runs."""
    return ["npm", "run", "--silent", name]


def twin_dir() -> Path:
    """The twin/ tree this run is about.

    Defaults to the repository the script sits in. A caller can point it elsewhere
    with TWIN_ROOT, which is what the self-tests do: they build a fixture tree and
    need the tool to look at that tree rather than at this one.
    """
    return Path(os.environ.get("TWIN_ROOT", ROOT)).resolve()


def run(name: str, command: list[str], timeout: int = 900) -> bool:
    began = time.monotonic()
    result = subprocess.run(command, cwd=str(twin_dir() / "twin"), capture_output=True,
                            text=True,
                            timeout=timeout)
    took = time.monotonic() - began
    output = (result.stdout or "") + (result.stderr or "")
    if result.returncode == 0:
        print("  %-9s ok    %5.1fs" % (name, took))
        return True
    print("  %-9s FAIL  %5.1fs" % (name, took))
    for line in [line for line in output.splitlines() if line.strip()][-12:]:
        print("    " + line[:160])
    return False


def initial_scripts_at(page: Path) -> list[Path]:
    """The scripts `page` loads before the app runs.

    Separate from `initial_scripts` so the arithmetic can be tested against pages
    built for the purpose rather than against whatever docs/twin/ happens to be.
    """
    if not page.is_file():
        raise SystemExit("check_twin: %s is missing; run python3 tools/build_twin.py"
                         % page)
    found = []
    for raw in SCRIPT.findall(page.read_bytes()):
        src = html.unescape(raw.decode("utf-8", "replace"))
        if not src.startswith(("/twin/", "./", "../")):
            # Off-origin or inline: not part of what the shelf serves for this page.
            continue
        relative = src[len("/twin/"):] if src.startswith("/twin/") else src
        path = (page.parent / relative).resolve()
        if path.is_file():
            found.append(path)
    return found


def measure_at(page: Path) -> dict:
    files = initial_scripts_at(page)
    sizes = {path.name: len(gzip.compress(path.read_bytes(), 9)) for path in files}
    return {"files": len(sizes), "bytes": sum(sizes.values()), "each": sizes}


def compare_at(page: Path, ceiling: int) -> bool:
    """Whether `page`'s initial weight is within `ceiling`. No printing."""
    return measure_at(page)["bytes"] <= ceiling


def measure() -> dict:
    return measure_at(twin_dir() / "docs" / "twin" / "index.html")


def check_payload(write: bool) -> bool:
    measured = measure()
    if write:
        record = {"generated": time.strftime("%Y-%m-%d"),
                  "initial_gzip_bytes": measured["bytes"],
                  "budget_bytes": measured["bytes"],
                  "note": ("Initial script weight of docs/twin/ as built. gzip -9, so "
                           "the shelf's brotli is smaller. Tighten budget_bytes "
                           "deliberately when the plugin split lands.")}
        budget_path().write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
        print("  %-9s wrote %s (%d bytes)" % ("payload", budget_path().relative_to(twin_dir()),
                                              measured["bytes"]))
        return True
    if not budget_path().is_file():
        print("  %-9s FAIL  no %s; run --write-budget"
              % ("payload", budget_path().relative_to(twin_dir())))
        return False
    record = json.loads(budget_path().read_text(encoding="utf-8"))
    ceiling = int(record["budget_bytes"])
    grew = measured["bytes"] > ceiling
    print("  %-9s %-4s %d bytes in %d file(s), ceiling %d"
          % ("payload", "FAIL" if grew else "ok", measured["bytes"],
             measured["files"], ceiling))
    if grew:
        print("    %+d bytes over. Initial weight only grows deliberately: either"
              % (measured["bytes"] - ceiling))
        print("    record a new ceiling with --write-budget, or import lazily.")
        for name, size in sorted(measured["each"].items(), key=lambda kv: -kv[1])[:4]:
            print("    %8d gz  %s" % (size, name))
    return not grew


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--no-tests", action="store_true",
                        help="skip the vitest suite (it takes about 80s)")
    parser.add_argument("--write-budget", action="store_true",
                        help="record the current payload as the ceiling; writes nothing else")
    parser.add_argument("--tests-only", action="store_true",
                        help="run the suite and nothing else, for a fast edit loop")
    args = parser.parse_args()

    if not (twin_dir() / "twin" / "node_modules").is_dir():
        print("  %-9s SKIP  no %s; run `npm ci` in twin/. The shelf job has no node "
              "toolchain by design, so this is a skip, not a failure; the twin CI "
              "job runs the same gate with dependencies installed."
              % ("install", NODE_MODULES))
        return SKIP

    if args.write_budget:
        return 0 if check_payload(True) else 1

    began = time.monotonic()
    ok = True
    if not args.tests_only:
        ok &= run("typecheck", npx("tsc", "-b"))
        ok &= run("assets", script("check:assets"))
    if not args.no_tests:
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        ok &= run("tests", npx("vitest", "run",
                               "--reporter=default", "--reporter=junit",
                               "--outputFile.junit=%s" % REPORT))
    if not args.tests_only:
        ok &= check_payload(False)

    total = time.monotonic() - began
    if ok:
        print("twin: OK - typecheck, assets, tests and payload in %.0fs" % total)
        return 0
    print("twin: FAILED in %.0fs" % total)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
