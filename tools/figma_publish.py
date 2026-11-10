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
            '<a href="pantone.svg">pantone.svg</a>.</p>\n  %s\n</section>\n  '
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
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<meta name="theme-color" content="#0fbf92" />
<meta name="description" content="Every page as a Figma-importable artboard, the whole set on one canvas, and the Pantone match for every palette colour." />
<title>Design files — every sketch and every palette</title>
<link rel="icon" href="../assets/favicon.svg" type="image/svg+xml" />
<link rel="stylesheet" href="../assets/site.css" />
<script src="../assets/site.js" defer></script>
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
    main { padding: 32px 20px 48px; }
    h1 { font-size: 30px; }
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
<main>
  <h1>Design files</h1>
  <p class="lede">%s</p>
  <p class="note">Each artboard is the page as Chromium laid it out: real text, a rect per box, PNG marks, Poppins embedded. Drag one into Figma for editable layers. The palettes below are read from the stylesheet the site actually ships.</p>
  %s%s<p class="back"><a href="../">← Back to the site</a></p>
</main>
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
