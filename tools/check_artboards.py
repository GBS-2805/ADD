#!/usr/bin/env python3
"""Fail when an artboard no longer matches the page it was traced from.

    python3 tools/check_artboards.py

`figma/*.svg` is a transcription of the pages as Chromium lays them out, and a board
records nothing about where it came from: once written it is only an SVG. A page that
moved, was reworded or changed colour leaves its board quoting the old text, and
nothing notices. `check_figma.py` reads the boards for what Figma needs -- own type per
text run, no `<use>` -- and the bundler only compares the newest page's timestamp with
the newest board's, which one late re-export is enough to make look fresh.

So this gate re-exports instead of guessing: it serves docs/, runs
`tools/figma_export.js` into a scratch folder and compares the bytes with `figma/`.
It then exports a second time and requires the three long phone icon boards to be
byte-identical between the two runs. Those boards once drifted by a fraction of a
pixel because offscreen cards in a clipped scroll box were being measured; one
export matching the commit is not enough to prove the next one will.

The export is deterministic and takes about ten seconds, so "current" means what it
should -- the exporter would write this same file today.

The canvas and the colour sheet are left out: `site-figma.svg` comes from
`figma_bundle.py` and `pantone.svg` from `pantone_sheet.py`, not from the exporter.

Needs the render toolchain that `tools/render_env.py` builds, like the rendered half of
`tools/check_palette.py`.
"""

import html
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BOARDS = ROOT / "figma"
DERIVED = ("site-figma.svg", "pantone.svg")
PHONE_ICONS = ("16-icons-lineicons-phone.svg", "17-icons-coolicons-phone.svg",
               "18-icons-streamline-phone.svg")
SEARCH_PLACEHOLDER = "Search by name"
SEARCH_PLACEHOLDERS = {
    "04-icons-lineicons-desktop.svg": {"x": "15", "y": "1046.58", "font-family": "Spectral",
                                      "font-size": "16", "font-weight": "400"},
    "05-icons-coolicons-desktop.svg": {"x": "15", "y": "1046.58", "font-family": "Spectral",
                                      "font-size": "16", "font-weight": "400"},
    "06-icons-streamline-desktop.svg": {"x": "15", "y": "1046.58", "font-family": "Spectral",
                                       "font-size": "16", "font-weight": "400"},
    "16-icons-lineicons-phone.svg": {"x": "35", "y": "1170.78", "font-family": "Spectral",
                                     "font-size": "15", "font-weight": "400"},
    "17-icons-coolicons-phone.svg": {"x": "35", "y": "1170.78", "font-family": "Spectral",
                                     "font-size": "15", "font-weight": "400"},
    "18-icons-streamline-phone.svg": {"x": "35", "y": "1222.92", "font-family": "Spectral",
                                      "font-size": "15", "font-weight": "400"},
}
SEARCH_BOARDS = tuple(SEARCH_PLACEHOLDERS)
HOVER_BOARDS = ("25-index-menu-desktop.svg", "26-index-drawer-desktop.svg",
                "27-index-menu-phone.svg", "28-index-drawer-phone.svg")
TEXT_RE = re.compile(r"<text\b([^>]*)>(.*?)</text>")
ATTR_RE = re.compile(r'([\w:-]+)="([^"]*)"')

# Chromium lives outside node's own lookup path on a bare box: the module and the
# browsers are installed under /tmp by tools/render_env.py, with their libs unpacked
# rootlessly beside them. A value already exported still wins. On a CI runner the
# browser is installed in Playwright's normal cache instead, so only add a fallback
# when that path actually exists; forcing a missing /tmp here makes the export stop
# before it opens a page.
def chromium_installed(path):
    return Path(path).is_dir() and any(Path(path).glob("chromium-*"))


def browser_env():
    env = {}
    node_path = os.environ.get("NODE_PATH") or str(ROOT / "node_modules")
    if Path(node_path).is_dir():
        env["NODE_PATH"] = node_path
    for name, fallback in (
        ("PLAYWRIGHT_BROWSERS_PATH", "/tmp/pw"),
        ("LD_LIBRARY_PATH",
         "/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu"),
        ("FONTCONFIG_FILE", "/tmp/rootfs/fonts.conf"),
    ):
        value = os.environ.get(name)
        if value:
            env[name] = value
        elif name == "PLAYWRIGHT_BROWSERS_PATH":
            if chromium_installed(fallback):
                env[name] = fallback
        elif Path(fallback).exists():
            env[name] = fallback
    return env


BROWSER_ENV = browser_env()


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def wait_for(base):
    for _ in range(60):
        try:
            urllib.request.urlopen(base, timeout=1).read(1)
            return
        except Exception:
            time.sleep(0.2)
    raise SystemExit("the scratch server never answered on %s" % base)


def export(base, target):
    done = subprocess.run(["node", "tools/figma_export.js", base, str(target)],
                          cwd=str(ROOT), env=dict(os.environ, **BROWSER_ENV),
                          capture_output=True, text=True)
    if done.returncode != 0:
        tail = ((done.stderr or "") + (done.stdout or "")).strip().splitlines()[-6:]
        raise SystemExit("the exporter did not run; this gate needs the render toolchain "
                         "(python3 tools/render_env.py)\n  " + "\n  ".join(tail))


def unstable_boards(first, second, names=PHONE_ICONS):
    """Phone icon boards whose bytes differ between two exports."""
    return [name for name in names
            if not (second / name).is_file()
            or (first / name).read_bytes() != (second / name).read_bytes()]


def placeholder_problems(directory, expected=SEARCH_PLACEHOLDERS):
    """Icon boards whose search placeholder is absent, moved or wrong-typed."""
    found = []
    for name, want in expected.items():
        path = directory / name
        if not path.is_file():
            found.append((name, "board missing"))
            continue
        runs = [dict(ATTR_RE.findall(match.group(1)))
                for match in TEXT_RE.finditer(path.read_text(encoding="utf-8"))
                if html.unescape(match.group(2)).strip() == SEARCH_PLACEHOLDER]
        if len(runs) != 1:
            found.append((name, "%d copies" % len(runs)))
            continue
        wrong = ["%s=%r (want %r)" % (key, runs[0].get(key), value)
                 for key, value in want.items() if runs[0].get(key) != value]
        if wrong:
            found.append((name, "; ".join(wrong)))
    return found


def hover_twins(svg):
    """Repeated labels one line apart, the signature of an emitted hover twin.

    `.roll-a` and `.roll-b` share text, x and type; the hidden twin sits 110% of a
    1.25 line box lower and is clipped by `.roll`. If the exporter stops rejecting
    clipped nodes, both copies land in the menu/drawer boards as text runs at the
    same x separated by 13.06-13.07 px.
    """
    lines = {}
    for match in TEXT_RE.finditer(svg):
        attrs = dict(ATTR_RE.findall(match.group(1)))
        try:
            y = float(attrs["y"])
        except (KeyError, ValueError):
            continue
        key = (attrs.get("x"), html.unescape(match.group(2)),
               attrs.get("font-family"), attrs.get("font-size"),
               attrs.get("font-weight"), attrs.get("letter-spacing"),
               attrs.get("fill"))
        lines.setdefault(key, []).append(y)

    found = []
    for key, ys in lines.items():
        ys = sorted(ys)
        for first, second in zip(ys, ys[1:]):
            if 12 <= second - first <= 14:
                found.append((key[1], round(first, 2), round(second, 2)))
    return found


def text_runs(path):
    return [(html.unescape(match.group(2)).strip(),)
            + tuple(dict(ATTR_RE.findall(match.group(1))).get(key)
                    for key in ("x", "y", "font-family", "font-size", "font-weight"))
            for match in TEXT_RE.finditer(path.read_text(encoding="utf-8"))]


def first_difference(expected, actual):
    """The first text run that moved or changed, with its page and export values."""
    want, got = text_runs(expected), text_runs(actual)
    for index, (a, b) in enumerate(zip(want, got)):
        if a != b:
            return "text %d expected %r got %r" % (index, a, b)
    if len(want) != len(got):
        return "%d text runs expected, %d exported" % (len(want), len(got))
    return "no text run differs; layout only"


def main():
    scratch = Path(tempfile.mkdtemp(prefix="artboards-"))
    port = free_port()
    base = "http://127.0.0.1:%d/" % port
    server = subprocess.Popen(
        [sys.executable, "-m", "http.server", str(port), "--directory", str(ROOT / "docs")],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        wait_for(base)
        export(base, scratch)
        export(base, scratch / "rerun")
    finally:
        server.terminate()
        try:
            server.wait(timeout=5)
        except subprocess.TimeoutExpired:
            server.kill()

    written = {path.name: path for path in scratch.glob("*.svg")}
    kept = {path.name: path for path in BOARDS.glob("*.svg") if path.name not in DERIVED}
    stale = [name for name in sorted(kept)
             if name in written and written[name].read_bytes() != kept[name].read_bytes()]
    missing = [name for name in sorted(kept) if name not in written]
    unknown = [name for name in sorted(written) if name not in kept]
    unstable = unstable_boards(scratch, scratch / "rerun")
    hidden = [(name, hover_twins((scratch / name).read_text(encoding="utf-8")))
              for name in HOVER_BOARDS if (scratch / name).is_file()]
    hidden = [(name, found) for name, found in hidden if found]
    placeholders = placeholder_problems(scratch)
    differences = [(name, first_difference(kept[name], written[name]))
                   for name in stale]
    shutil.rmtree(scratch, ignore_errors=True)

    if stale or missing or unknown or unstable or hidden or placeholders:
        print("artboards: FAILED - %d board(s) no longer match the pages, "
              "%d phone icon board(s) not byte-identical between exports, "
              "%d menu/drawer board(s) still emit clipped hover twins, "
              "%d icon board(s) without one search placeholder"
              % (len(stale) + len(missing) + len(unknown), len(unstable), len(hidden),
                 len(placeholders)))
        for name in stale:
            print("  stale    figma/%s" % name)
        for name, problem in differences[:4]:
            print("  diff     figma/%s: %s" % (name, problem[:220]))
        for name in missing:
            print("  missing  figma/%s (the exporter wrote no board by that name)" % name)
        for name in unknown:
            print("  unknown  %s (exported, not tracked in figma/)" % name)
        for name in unstable:
            print("  unstable figma/%s (two exports disagree)" % name)
        for name, found in hidden:
            labels = ", ".join("%s %.2f/%.2f" % hit for hit in found)
            print("  hidden   figma/%s (repeated one line down: %s)" % (name, labels))
        for name, problem in placeholders:
            print("  placeholder figma/%s (%s; expected one %r text run)"
                  % (name, problem, SEARCH_PLACEHOLDER))
        print("  fix: node tools/figma_export.js <base> figma, then figma_bundle.py, "
              "then figma_publish.py")
        return 1
    print("artboards: OK - %d boards match the pages; %d phone icon boards "
          "byte-identical across two exports; no clipped hover twins in %d "
          "menu/drawer boards; search placeholder in %d icon boards"
          % (len(kept), len(PHONE_ICONS), len(HOVER_BOARDS), len(SEARCH_BOARDS)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
