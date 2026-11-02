#!/usr/bin/env python3
"""Mirror the Figma hand-off into the site, with a page that lists it.

`figma/` stays the source: `tools/figma_export.js` writes it and
`tools/figma_bundle.py` stacks it. This copies the result into `docs/figma/`,
which `manifest.json` does serve, writes the listing at `docs/figma/index.html`,
and removes any artboard that is no longer exported. The four pull-out artboards
also get the names the links use; every page artboard keeps its number.

    python3 tools/figma_publish.py
"""
import html
import pathlib
import re
import shutil
import sys

SRC = pathlib.Path("figma")
DST = pathlib.Path("docs/figma")

# The navigation states are referred to by what they are, not by their number.
RENAME = {
    "19-index-menu-desktop.svg": "pull-down-desktop.svg",
    "20-index-drawer-desktop.svg": "side-tab-desktop.svg",
    "21-index-menu-phone.svg": "pull-down-phone.svg",
    "22-index-drawer-phone.svg": "side-tab-phone.svg",
}
STATE_NUMBERS = {"19", "20", "21", "22"}
BUNDLE = "site-figma.svg"


def describe(path):
    head = path.read_text(errors="replace")[:2500]
    title = re.search(r"<title>(.*?)</title>", head)
    size = re.search(r'width="([\d.]+)" height="([\d.]+)"', head)
    dims = "×".join("%g" % float(size.group(i)) for i in (1, 2)) if size else ""
    return (title.group(1) if title else path.stem), dims


def human(n):
    return "%.0f KB" % (n / 1024) if n < 1048576 else "%.2f MB" % (n / 1048576)


def listing(names):
    facts = {n: describe(DST / n) + (human((DST / n).stat().st_size),) for n in names}

    def item(name):
        title, dims, size = facts[name]
        return '<li><a href="%s">%s</a><span>%s · %s</span></li>' % (
            html.escape(name), html.escape(title), html.escape(dims), size)

    def group(prefix, names_):
        if not names_:
            return ""
        return "<h2>%s</h2>\n  <ul>\n    %s\n  </ul>\n  " % (prefix, "\n    ".join(item(n) for n in names_))

    pages = sorted(n for n in names if re.match(r"\d\d-", n) and n.split("-")[0] not in STATE_NUMBERS)
    states = sorted(n for n in names if n.startswith(("pull-down-", "side-tab-")))
    desktop = [n for n in pages if n.endswith("-desktop.svg")]
    phone = [n for n in pages if n.endswith("-phone.svg")]
    body = (
        group("Pages, desktop", desktop)
        + group("Pages, phone", phone)
        + group("Navigation, open", states)
        + group("The whole set on one canvas", [BUNDLE] if BUNDLE in names else [])
    )
    return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<meta name="theme-color" content="#3f4e96" />
<title>Figma hand-off — every artboard of this site</title>
<link rel="icon" href="../assets/favicon.svg" type="image/svg+xml" />
<link rel="stylesheet" href="../assets/site.css" />
<style>
  main { padding: 52px 40px 64px; max-width: 1040px; }
  h1 { margin: 0 0 10px; font-size: 40px; font-weight: 400; line-height: 1.15; letter-spacing: -.02em; }
  .lede { margin: 0 0 10px; }
  .note { margin: 0 0 34px; max-width: 70ch; font-size: 15px; color: var(--on-dim); }
  h2 { margin: 38px 0 12px; font-size: 20px; font-weight: 500; }
  ul { list-style: none; margin: 0; padding: 0; border-top: 1px solid var(--rule); }
  li { display: flex; align-items: baseline; justify-content: space-between; gap: 18px; padding: 14px 0; border-bottom: 1px solid var(--rule); }
  li a { font-size: 17px; text-decoration: none; }
  li a:hover { text-decoration: underline; }
  li span { flex: none; font-size: 13px; color: var(--on-dim); white-space: nowrap; }
  .back { margin: 40px 0 0; font-size: 15px; }
  @media (max-width: 700px) {
    main { padding: 32px 20px 48px; }
    h1 { font-size: 30px; }
    li { flex-wrap: wrap; gap: 2px 14px; }
  }
</style>
</head>
<body>
<main>
  <h1>Figma hand-off</h1>
  <p class="lede">Twenty-two artboards of this site, and the whole set on one canvas.</p>
  <p class="note">Each file is the page as Chromium laid it out — text on real baselines, a rect per box, PNG copies of the marks. Poppins 400/500/600 travels inside every file, so it looks the same here as in Figma. Drag one into a Figma document to get editable layers; enable the Poppins family once there and run Frame selection to lock the artboard's size.</p>
  %s<p class="back"><a href="../">← Back to the site</a></p>
</main>
</body>
</html>
""" % body


def main():
    exports = sorted(SRC.glob("[0-9][0-9]-*.svg"))
    if not exports:
        sys.exit("no artboards in figma/ - run tools/figma_export.js first")
    bundle = SRC / BUNDLE
    if not bundle.exists():
        sys.exit("figma/%s is missing - run tools/figma_bundle.py" % BUNDLE)

    DST.mkdir(parents=True, exist_ok=True)
    wanted = {}
    for artboard in exports:
        wanted[RENAME.get(artboard.name, artboard.name)] = artboard
    wanted[BUNDLE] = bundle

    dropped = sorted(p.name for p in DST.glob("*.svg") if p.name not in wanted)
    for name in dropped:
        (DST / name).unlink()
    for name, artboard in wanted.items():
        shutil.copyfile(artboard, DST / name)

    (DST / "index.html").write_text(listing(list(wanted)))
    total = sum(p.stat().st_size for p in DST.glob("*.svg"))
    print("docs/figma: %d files, %.1f MB, listing written%s" % (
        len(wanted), total / 1e6, (", dropped " + ", ".join(dropped)) if dropped else ""))


if __name__ == "__main__":
    main()
