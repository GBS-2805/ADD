#!/usr/bin/env python3
"""Rebuild the headless-Chromium render toolchain in /tmp, without root.

This VM ships no browser libs and no fonts, and /tmp is wiped between
sessions, so every render needs this first:

    python3 tools/render_env.py          # everything (~4 min, ~300 MB)
    python3 tools/render_env.py --libs   # just the missing .so + fonts

Afterwards export what it prints:

    PLAYWRIGHT_BROWSERS_PATH=/tmp/pw
    LD_LIBRARY_PATH=/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu
    FONTCONFIG_FILE=/tmp/rootfs/fonts.conf
    NODE_PATH=/tmp/pwshot/node_modules

apt-get cannot be used here (no root, stale indexes), so the runtime libs are
unpacked out of the Ubuntu archive with `dpkg -x`.
"""
from __future__ import annotations

import gzip
import os
import shutil
import subprocess
import sys
import urllib.request

ARCHIVE = "https://archive.ubuntu.com/ubuntu"
SUITES = ("noble", "noble-updates")
COMPONENTS = ("main", "universe")
ROOTFS = "/tmp/rootfs"
PKG_DIR = "/tmp/pkgs"
BROWSERS = "/tmp/pw"
PROJ = "/tmp/pwshot"

# Chromium's shared-object closure plus two text faces for the renders.
DEPS = """
libnspr4 libnss3 libasound2t64 libatk1.0-0t64 libatk-bridge2.0-0t64
libatspi2.0-0t64 libxkbcommon0 libxcomposite1 libxdamage1 libxfixes3
libxrandr2 libxrender1 libxcb-render0 libxcb-shm0 libpixman-1-0
libharfbuzz0b libgraphite2-3 libfreetype6 libfontconfig1 libpng16-16t64
libbrotli1 libx11-xcb1 libxi6 libxtst6 libegl1 libglx0
libwayland-client0 libwayland-server0 libwayland-egl1 libwayland-cursor0
fonts-dejavu-core fonts-liberation
""".split()


def log(msg: str) -> None:
    print(f"  {msg}", flush=True)


def fetch(url: str, dest: str) -> str:
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return dest
    tmp = dest + ".part"
    with urllib.request.urlopen(url, timeout=120) as r, open(tmp, "wb") as f:
        shutil.copyfileobj(r, f)
    os.replace(tmp, dest)
    return dest


def package_index() -> dict[str, str]:
    """Map pool-relative .deb path -> package name, for every suite/component."""
    index: dict[str, str] = {}
    for suite in SUITES:
        for comp in COMPONENTS:
            url = f"{ARCHIVE}/dists/{suite}/{comp}/binary-amd64/Packages.gz"
            dest = os.path.join(PKG_DIR, f"{suite}-{comp}.gz")
            try:
                fetch(url, dest)
            except Exception as exc:  # a missing suite is not fatal
                log(f"skip {suite}/{comp}: {exc}")
                continue
            with gzip.open(dest, "rt", errors="replace") as f:
                name = ""
                for line in f:
                    if line.startswith("Package: "):
                        name = line[9:].strip()
                    elif line.startswith("Filename: "):
                        index[line[10:].strip()] = name
    return index


def install_libs() -> None:
    os.makedirs(ROOTFS, exist_ok=True)
    os.makedirs(PKG_DIR, exist_ok=True)
    index = package_index()
    log(f"indexed {len(index)} packages")

    wanted = set(DEPS)
    picks: dict[str, str] = {}
    # Longest name wins so libatk1.0-0t64 does not shadow libatk1.0-0t64-dev.
    for path, name in sorted(index.items(), key=lambda kv: -len(kv[1])):
        if name in wanted and name not in picks:
            picks[name] = path

    missing = wanted - set(picks)
    if missing:
        log(f"MISSING from archive: {sorted(missing)}")

    for name, path in sorted(picks.items()):
        deb = os.path.join(PKG_DIR, os.path.basename(path))
        try:
            fetch(f"{ARCHIVE}/{path}", deb)
        except Exception as exc:
            log(f"download failed {name}: {exc}")
            continue
        subprocess.run(["dpkg", "-x", deb, ROOTFS], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        log(f"unpacked {name}")

    fonts = os.path.join(ROOTFS, "usr", "share", "fonts")
    with open(os.path.join(ROOTFS, "fonts.conf"), "w") as f:
        f.write(
            '<?xml version="1.0"?>\n'
            '<!DOCTYPE fontconfig SYSTEM "fonts.dtd">\n'
            "<fontconfig>\n"
            f"  <dir>{fonts}</dir>\n"
            "  <cachedir>/tmp/rootfs/fontcache</cachedir>\n"
            "</fontconfig>\n"
        )
    os.makedirs("/tmp/rootfs/fontcache", exist_ok=True)
    log(f"fonts.conf -> {fonts}")


def install_browser() -> None:
    os.makedirs(PROJ, exist_ok=True)
    if not os.path.isdir(os.path.join(PROJ, "node_modules", "playwright")):
        subprocess.run(["npm", "init", "-y"], cwd=PROJ, check=True,
                       stdout=subprocess.DEVNULL)
        log("npm install playwright")
        subprocess.run(["npm", "i", "--silent", "playwright"], cwd=PROJ, check=True)
    env = dict(os.environ, PLAYWRIGHT_BROWSERS_PATH=BROWSERS)
    log("playwright install chromium")
    subprocess.run(["npx", "playwright", "install", "chromium"], cwd=PROJ,
                   env=env, check=True)


def main() -> int:
    libs_only = "--libs" in sys.argv
    if not libs_only:
        install_browser()
    install_libs()
    print(
        "\nexport PLAYWRIGHT_BROWSERS_PATH=/tmp/pw\n"
        "export LD_LIBRARY_PATH=/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu\n"
        "export FONTCONFIG_FILE=/tmp/rootfs/fonts.conf\n"
        "export NODE_PATH=/tmp/pwshot/node_modules"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
