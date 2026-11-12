#!/usr/bin/env python3
"""Mirror the Figma hand-off into the site, with a page that lists it.

`figma/` stays the source: `tools/figma_export.js` writes it and
`tools/figma_bundle.py` stacks it. This copies the result into `docs/figma/`,
which `manifest.json` does serve, writes the listing at `docs/figma/index.html`,
and removes any artboard that is no longer exported. Every page artboard keeps its
number; the four pull-outs are served under the plain name the links use, because
serving both names duplicated 181 KB the deploy budget does not have.

    python3 tools/figma_publish.py
"""
import html
import json
import pathlib
import re
import shutil
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from hash_assets import pin  # noqa: E402 - the tools directory is on the path above
from check_figma import check as figma_problems  # noqa: E402 - same directory
from pantone_sheet import DATA, nearest, order_roles, palettes, pretty  # noqa: E402 - same directory

SRC = pathlib.Path("figma")
DST = pathlib.Path("docs/figma")

# The navigation states are referred to by what they are, not by their number. The
# export renumbers as pages come and go, and a map keyed to "21-index-menu-…" stops
# matching the moment it does: the four states then drop out of the listing and are
# served under their numbers. Match the role, never the prefix.
RENAME = [
    (re.compile(r"^\d+-index-menu-desktop\.svg$"), "pull-down-desktop.svg"),
    (re.compile(r"^\d+-index-drawer-desktop\.svg$"), "side-tab-desktop.svg"),
    (re.compile(r"^\d+-index-menu-phone\.svg$"), "pull-down-phone.svg"),
    (re.compile(r"^\d+-index-drawer-phone\.svg$"), "side-tab-phone.svg"),
]
BUNDLE = "site-figma.svg"

# The Pantone sheet is not an artboard of a page: it is handed over beside them, under
# its own heading. tools/pantone_sheet.py writes it from the palettes in site.css.
SHEET = "pantone.svg"

# The listing counts the run in words, so it has to be told what the run has.
WORDS = {19: "Nineteen", 20: "Twenty", 21: "Twenty-one", 22: "Twenty-two",
         23: "Twenty-three", 24: "Twenty-four", 25: "Twenty-five", 26: "Twenty-six",
         27: "Twenty-seven", 28: "Twenty-eight"}

# The exporter writes every image twice: the payload under plain `href` and again
# under `xlink:href`. One copy paints the picture and the other is dead weight --
# just over a megabyte across the served artboards. Drop the redundant attribute
# and spell what is left the same way in every file: `xlink:href`, which every SVG
# reader accepts and Figma's importer expects.
IMAGE_TAG = re.compile(r"<image\b[^>]*?/>")
ATTRIBUTE = re.compile(r'([\w:-]+)="([^"]*)"')
XLINK = "http://www.w3.org/1999/xlink"


def one_payload(text):
    if "xmlns:xlink" not in text[:4000]:
        text = text.replace("<svg ", '<svg xmlns:xlink="%s" ' % XLINK, 1)

    def strip(match):
        tag = match.group(0)
        hrefs = {k: v for k, v in ATTRIBUTE.findall(tag) if k.endswith("href")}
        if not hrefs or len(set(hrefs.values())) > 1:
            return tag  # nothing to drop, or the copies disagree: leave it alone
        value = next(iter(hrefs.values()))
        tag = re.sub(r'\s+href="[^"]*"', "", tag, count=1)
        if not re.search(r'\sxlink:href="', tag):
            tag = tag[: -len("/>")].rstrip() + ' xlink:href="%s"/>' % value
        return tag

    return IMAGE_TAG.sub(strip, text)


def describe(path):
    head = path.read_text(errors="replace")[:2500]
    title = re.search(r"<title>(.*?)</title>", head)
    size = re.search(r'width="([\d.]+)" height="([\d.]+)"', head)
    dims = "×".join("%g" % float(size.group(i)) for i in (1, 2)) if size else ""
    return (title.group(1) if title else path.stem), dims


def human(n):
    return "%.0f KB" % (n / 1024) if n < 1048576 else "%.0f MB" % (n / 1048576)


def palette_cards():
    """Every solid colour of the four palettes, built the way the sheet builds them.

    Each row opens with the site's own colour, then the nearest Pantone chip and two
    alternatives side by side, every one carrying a square of its own colour so the eye
    reads the colour before the code. No image is involved: the page is built from
    site.css and tools/pantone-data.json, the same two files figma/pantone.svg is.
    """
    data = json.loads(DATA.read_text())
    coated, tcx = data["coated"], data["tcx"]
    cards = []
    for name, roles in palettes():
        rows = []
        for hexv, role_list in roles.items():
            done = order_roles(role_list)
            picks = nearest(coated, hexv, count=3)
            twin = nearest(tcx, hexv, count=1)[0][1]
            cells = []
            for i, (dist, chip, _) in enumerate(picks):
                extra = ""
                if i == 0:
                    extra = '<em class="tcx">%s %s</em>' % (
                        html.escape(twin["code"]),
                        html.escape(twin["name"].replace("-", " ").title()))
                cells.append(
                    '<span class="pm"><i style="background:%s"></i>'
                    '<span class="txt"><b>%s</b><code>%s</code><em>\u0394E %.1f</em>%s</span></span>'
                    % (html.escape(chip["hex"]), html.escape(pretty(chip["code"])),
                       chip["hex"].upper(), dist, extra))
            also = [r.replace("-", " ") for r in done[1:]]
            note = ('<em class="also">also %s</em>' % html.escape(", ".join(also[:3]))) if also else ""
            rows.append(
                '<li><i class="own" style="background:%s"></i>'
                '<div class="role"><b>%s</b><code>%s</code>%s</div>'
                '<div class="alts">%s</div></li>'
                % (html.escape(hexv), html.escape(done[0].replace("-", " ")),
                   hexv.upper(), note, "".join(cells)))
        cards.append('<article class="palette"><h3>%s</h3><ul class="swatches">%s</ul></article>'
                     % (name.title(), "".join(rows)))
    return ('<section class="palettes">\n  <h2>Palettes</h2>\n'
            '  <p class="note">Every solid colour the site ships, then its nearest Pantone Solid '
            'Coated chip and two alternatives, each with a square of its own colour. '
            '<b>\u0394E00 is how far a chip sits from the colour</b>: 0 is the same colour, under 3 '
            'cannot be told apart by eye, 3 to 5 is a close but visible step, and over 5 is a '
            'different shade kept only as a further option. The same table is handed over as '
            '<a href="pantone.svg">pantone.svg</a>.</p>\n  %s\n</section>\n'
            % "".join(cards))


def listing(names):
    facts = {n: describe(DST / n) + (human((DST / n).stat().st_size),) for n in names}
    count = len([n for n in names if n.endswith(".svg") and n not in (BUNDLE, SHEET)])
    lede = ("%s sketch artboards of this site, the desktop set on one canvas, and every colour the "
            "four palettes declare.") % WORDS.get(count, count)

    def item(name):
        title, dims, size = facts[name]
        return '<li><a href="%s">%s</a><span>%s · %s</span></li>' % (
            html.escape(name), html.escape(title), html.escape(dims), size)

    def group(prefix, names_):
        if not names_:
            return ""
        return "<h2>%s</h2>\n  <ul>\n    %s\n  </ul>\n  " % (prefix, "\n    ".join(item(n) for n in names_))

    pages = sorted(n for n in names if re.match(r"\d\d-", n))
    states = sorted(n for n in names if n.startswith(("pull-down-", "side-tab-")))
    desktop = [n for n in pages if n.endswith("-desktop.svg")]
    phone = [n for n in pages if n.endswith("-phone.svg")]
    body = (
        group("Pages, desktop", desktop)
        + group("Pages, phone", phone)
        + group("Navigation, open", states)
        + group("The desktop set on one canvas", [BUNDLE] if BUNDLE in names else [])
        + group("Colour reference", [SHEET] if SHEET in names else [])
    )
    return """<!DOCTYPE html>
<html lang="en" data-palette="ash" data-theme="dark">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<meta name="theme-color" content="#f0e7d4" />
<meta name="description" content="Every page as a Figma-importable artboard, the whole set on one canvas, and the Pantone match for every palette colour." />
<title>Design files — every sketch and every palette</title>
<link rel="icon" href="../assets/favicon.svg" type="image/svg+xml" />
<link rel="stylesheet" href="../assets/site.css" />
<script src="../assets/site.js" defer></script>
<style>
  main { max-width: 1040px; }
  .lede { margin: 0 0 10px; }
  .note { margin: 0 0 34px; max-width: 70ch; font-size: 15px; color: var(--on-dim); }
  h2 { margin: 38px 0 12px; font-size: 20px; font-weight: 500; }
  ul { list-style: none; margin: 0; padding: 0; border-top: 1px solid var(--rule); }
  li { display: flex; align-items: baseline; justify-content: space-between; gap: 18px; padding: 14px 0; border-bottom: 1px solid var(--rule); }
  li a { font-size: 17px; text-decoration: none; }
  li a:hover { text-decoration: underline; }
  li span { flex: none; font-size: 13px; color: var(--on-dim); white-space: nowrap; }
  .back { margin: 40px 0 0; font-size: 15px; }
  .palettes { margin: 46px 0 0; }
  .palettes h2 { margin-top: 0; }
  .palette { margin: 24px 0 0; padding-top: 16px; border-top: 1px solid var(--rule); }
  .palette h3 { margin: 0 0 6px; font-size: 17px; font-weight: 500; }
  ul.swatches { list-style: none; margin: 0; padding: 0; }
  ul.swatches li {
    display: grid; grid-template-columns: 44px minmax(8rem, 1fr) minmax(0, 3fr);
    gap: 16px; align-items: start; padding: 12px 0; border-bottom: 1px solid var(--rule); font-size: 13px;
  }
  ul.swatches .own { display: block; width: 44px; height: 32px; box-shadow: inset 0 0 0 1px rgba(0, 0, 0, .18); }
  ul.swatches .role { display: flex; flex-direction: column; gap: 1px; }
  ul.swatches .role b { font-size: 14px; font-weight: 500; }
  ul.swatches .role code { padding: 0; background: none; color: var(--on-dim); }
  ul.swatches .also { font-style: normal; font-size: 11.5px; color: var(--on-dim); }
  ul.swatches .alts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
  ul.swatches .pm { display: flex; align-items: flex-start; gap: 8px; min-width: 0; }
  ul.swatches .pm i { flex: none; width: 14px; height: 14px; margin-top: 3px; box-shadow: inset 0 0 0 1px rgba(0, 0, 0, .2); }
  ul.swatches .pm .txt { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
  ul.swatches .pm b { font-size: 13px; font-weight: 500; line-height: 1.35; overflow-wrap: anywhere; }
  ul.swatches .pm code { padding: 0; background: none; color: var(--on-dim); font-size: 11px; }
  ul.swatches .pm em { font-style: normal; font-size: 11px; color: var(--on-dim); }
  ul.swatches .pm .tcx { font-size: 10.5px; }
  ul.swatches .role b, ul.swatches .role code { overflow-wrap: anywhere; }
  @media (max-width: 700px) {
    li { flex-wrap: wrap; gap: 2px 14px; }
    ul.swatches li { grid-template-columns: 30px minmax(0, 1fr); gap: 4px 12px; padding: 12px 0; }
    ul.swatches .own { width: 30px; height: 30px; }
    ul.swatches .alts { grid-column: 2; gap: 10px 14px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
    ul.swatches .pm b { font-size: 12px; }
    ul.swatches .pm code { display: none; }
    ul.swatches .pm .tcx { display: none; }
  }
</style>
</head>
<body>

<header class="bar">
<div class="lead">
    <button class="menu-btn" id="menuBtn" type="button" aria-expanded="false" aria-controls="menu" aria-label="Open sections">
      <svg class="chev" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M8 13L12 17L16 13M8 7L12 11L16 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
    </button>
    <a class="page-name" href="./">Figma</a>
  </div>

  <a class="mark" href="../" aria-label="ADD — home">
    <svg viewBox="0 0 120.6 123.11" role="img" aria-label="ADD logo" fill="currentColor">
        <path d="m29.58,61.92c.11,1.85.17,3.7.36,5.54.11,1.02-.23,1.23-1.19,1.29-4.47.26-5.38,1.22-5.27,5.67.13,5.14,2.74,9.13,6.27,12.56,1.53,1.49,2.96,1.05,3.89-.88.61-1.27.96-2.64,1.04-4.06.06-1.13.53-1.24,1.41-.69,1.72,1.08,2.92,2.71,4.38,4.07,8.66,8.04,18.38,13.89,30.58,14.51.52.03,1.04-.02,1.56,0,1.36.07,1.57-.53.89-1.58-.36-.56-.82-1.05-1.21-1.58-1.5-2.04-.63-4.17,1.89-4.53,3.34-.47,6.44.69,9.57,1.56,4.15,1.15,8.3,2.3,12.65,1.03,2.54-.74,2.78-1.66.84-3.54-.63-.61-1.41-1.08-2.11-1.63-2.43-1.91-2.63-3.35-.64-5.76,2.51-3.05,6.01-4.22,9.75-4.74,4.81-.67,9.26.31,13.25,3.19,1.39,1.01,1.75,1.97.94,3.7-9.48,20.25-25.12,32.69-47.2,36.28-29.25,4.75-57.41-12.11-67.54-39.86C-8.51,49.01,10.62,11.88,44.98,2.31c31.94-8.9,65.21,8.7,75.4,39.81.46,1.42.23,2.38-.66,3.4-4.28,4.87-9.78,6.13-15.92,5.16-4.99-.79-9.16-3.27-12.65-6.88-1.34-1.38-2.66-2.79-3.87-4.28-13.61-16.78-43.35-15.8-54.84,7.54-2.32,4.71-3.19,9.66-2.85,14.86Z"/>
        <path d="m80.89,57.02c.88-.09,2.06-.21,3.22.13,1.56.46,1.88,1.6.75,2.8-1.16,1.23-2.59,1.52-4.25,1.33-4.23-.48-5.14.47-4.58,4.78.43,3.35,1.88,6.19,4.52,8.36.99.81,1.67.93,2.18-.62,1.55-4.74,1.61-4.72,6.46-4.56,1.28.04,2.56-.04,3.49-1.05.76-.83,1.38-1.54,2.5-.44,1.07,1.05,1.29,2.15.42,3.35-1.18,1.61-2.84,2.29-4.82,2.23-1.25-.04-2.77-.51-3.68.03-.98.58-.39,2.29-.7,3.49-.75,2.89-2.94,3.94-5.77,2.62-5.71-2.66-10.17-13.01-8.12-18.87.86-2.45,2.86-3.21,5.21-3.41.96-.08,1.92-.11,3.18-.18Z"/>
        <path d="m52.62,58.41c.19-1.57.26-3.23,1.99-3.92,1.62-.65,2.9.23,3.92,1.33,2.41,2.61,3.79,5.64,2.86,9.26-.6,2.33-2.43,2.97-4.42,1.62-2.92-1.97-3.98-4.98-4.35-8.29Z"/>
        <path d="m44.97,66.78c-.03,1.9-.18,3.24-1.63,3.9-1.5.68-2.61-.25-3.53-1.19-2.06-2.11-3.15-4.68-2.7-7.66.4-2.62,2.46-3.4,4.53-1.73,2.25,1.82,3.29,4.3,3.33,6.68Z"/>
      </svg>
  </a>

<div class="who">
<button class="burger" id="burger" type="button" aria-expanded="false" aria-controls="drawer" aria-label="Open side tab">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 7H19M5 12H19M5 17H19" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
    </button>
  </div>
</header>

<nav class="menu" id="menu" data-open="false" aria-label="Sections">
  <a href="../">Index<span>the shelf</span></a>
  <a href="../tools/">Resources<span>icons · fonts · engine</span></a>
  <a href="../download/">Download<span>one app</span></a>
  <a href="https://artbase-handoff.vercel.app/">Handoff<span>build handoff</span></a>
</nav>

<div class="scrim" id="scrim" data-open="false"></div>
<aside class="drawer" id="drawer" data-open="false" aria-label="More">
  <div class="drawer-head">
    <p>More</p>
    <button class="drawer-close" id="drawerClose" type="button" aria-label="Close side tab"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M6 6L18 18M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
  </div>
  <a href="../download/">Download<span>1 app</span></a>
  <a href="../portfolio/">Portfolio<span>3 projects</span></a>
  <a href="../renders/">Renders<span>gallery</span></a>
  <a href="../releases/">Releases<span>history</span></a>
  <p class="drawer-note">ADD · a design shelf: icons, type, photos and words, in one quiet place.</p>
</aside>

<main>
  <header class="page-head">
    <h1>Design files</h1>
    <p class="lede">%s</p>
  </header>
  <p class="note">Each artboard is the page as Chromium laid it out: real text, a rect per box, PNG marks, Poppins embedded. Drag one into Figma for editable layers. The palettes below are read from the stylesheet the site actually ships.</p>
%s%s
</main>

<footer>
  <span>ADD<span class="dot" aria-hidden="true">·</span>one app, four tools</span>
  <span><a href="../">Index</a><span class="dot" aria-hidden="true">·</span><a href="../download/">Download</a><span class="dot" aria-hidden="true">·</span>no account</span>
</footer>
</body>
</html>
""" % (lede, body, palette_cards())


def stale_export():
    """Refuse to publish artboards older than the pages they were measured from.

    The export failing is quiet: the old files stay, the bundle rebuilds them and this
    step serves them, so the site carries a hand-off from the release before while
    every command reported success.
    """
    pages = [p for p in pathlib.Path("docs").rglob("index.html")
             if p.parts[1] not in ("figma", "renders")]
    boards = list(SRC.glob("[0-9][0-9]-*.svg"))
    if not pages or not boards:
        return
    page = max(pages, key=lambda p: p.stat().st_mtime)
    board = max(boards, key=lambda p: p.stat().st_mtime)
    if page.stat().st_mtime > board.stat().st_mtime + 2:
        sys.exit("figma/: %s is older than %s\n"
                 "run tools/figma_export.js and tools/figma_bundle.py first; publishing "
                 "now would serve the previous release's artboards" % (board, page))


def main():
    stale_export()
    exports = sorted(SRC.glob("[0-9][0-9]-*.svg"))
    if not exports:
        sys.exit("no artboards in figma/ - run tools/figma_export.js first")
    bundle = SRC / BUNDLE
    if not bundle.exists():
        sys.exit("figma/%s is missing - run tools/figma_bundle.py" % BUNDLE)

    issues = figma_problems(str(SRC))
    if issues:
        sys.exit("\n".join(["refusing to publish a hand-off Figma cannot import:"] + issues))

    DST.mkdir(parents=True, exist_ok=True)
    wanted = {}
    for artboard in exports:
        # One name per artboard. Serving the four pull-outs under their number as well
        # as their plain name cost 181 KB of byte-identical copies, and now that every
        # file carries its own fonts there is no room for two of the same artboard.
        name = artboard.name
        for pattern, plain in RENAME:
            if pattern.match(name):
                name = plain
                break
        if name in wanted:
            sys.exit("two artboards would be served as %s: %s and %s"
                     % (name, wanted[name].name, artboard.name))
        wanted[name] = artboard
    wanted[BUNDLE] = bundle
    sheet = SRC / SHEET
    if sheet.exists():
        wanted[SHEET] = sheet

    dropped = sorted(p.name for p in DST.glob("*.svg") if p.name not in wanted)
    for name in dropped:
        (DST / name).unlink()
    for name, artboard in wanted.items():
        if name in (BUNDLE, SHEET):
            shutil.copyfile(artboard, DST / name)
        else:
            (DST / name).write_text(one_payload(artboard.read_text()))

    (DST / "index.html").write_text(pin(listing(list(wanted))))
    total = sum(p.stat().st_size for p in DST.glob("*.svg"))
    print("docs/figma: %d files, %.0f MB, listing written%s" % (
        len(wanted), total / 1e6, (", dropped " + ", ".join(dropped)) if dropped else ""))

    budget = 10485760
    docs = sum(p.stat().st_size for p in pathlib.Path("docs").rglob("*") if p.is_file())
    note = "OVER BUDGET - the host will refuse this deploy" if docs > budget else "ok"
    print("docs: %s of 10.0 MiB, %s left - %s" % (human(docs), human(abs(budget - docs)), note))


if __name__ == "__main__":
    main()
