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
The export is deterministic and takes about ten seconds, so "current" means what it
should -- the exporter would write this same file today.

The canvas and the colour sheet are left out: `site-figma.svg` comes from
`figma_bundle.py` and `pantone.svg` from `pantone_sheet.py`, not from the exporter.

Needs the render toolchain that `tools/render_env.py` builds, like the rendered half of
`tools/check_palette.py`.
"""

import os
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

# Chromium lives outside node's own lookup path on a bare box: the module and the
# browsers are installed under /tmp by tools/render_env.py, with their libs unpacked
# rootlessly beside them. A value already exported still wins.
BROWSER_ENV = {
    "NODE_PATH": os.environ.get("NODE_PATH", str(ROOT / "node_modules")),
    "PLAYWRIGHT_BROWSERS_PATH": os.environ.get("PLAYWRIGHT_BROWSERS_PATH", "/tmp/pw"),
    "LD_LIBRARY_PATH": os.environ.get(
        "LD_LIBRARY_PATH",
        "/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu"),
    "FONTCONFIG_FILE": os.environ.get("FONTCONFIG_FILE", "/tmp/rootfs/fonts.conf"),
}


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
    shutil.rmtree(scratch, ignore_errors=True)

    if stale or missing or unknown:
        print("artboards: FAILED - %d board(s) no longer match the pages"
              % (len(stale) + len(missing) + len(unknown)))
        for name in stale:
            print("  stale    figma/%s" % name)
        for name in missing:
            print("  missing  figma/%s (the exporter wrote no board by that name)" % name)
        for name in unknown:
            print("  unknown  %s (exported, not tracked in figma/)" % name)
        print("  fix: node tools/figma_export.js <base> figma, then figma_bundle.py, "
              "then figma_publish.py")
        return 1
    print("artboards: OK - %d boards match the pages" % len(kept))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
