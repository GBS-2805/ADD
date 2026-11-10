#!/usr/bin/env python3
"""Build the classic ADD 3.8 shelf as a self-contained archive.

    python3 tools/archive_classic.py
    python3 tools/archive_classic.py --check

The source is the git tag, never the working tree: the archive must be the shelf
that actually shipped, not a copy of uncommitted design work. The snapshot lives
at docs/classic/ so every relative link in the old pages keeps working when the
site is served from the domain root.

The old robots.txt, sitemap.xml and vercel.json are intentionally not copied:
they describe the old site as the canonical one. Each archived page gets a
noindex meta tag and a small banner back to the current edition. The current
site's generators ignore docs/classic/ so its 16 pages and 16 routes stay
separate from the classic tree.
"""

import argparse
import io
import pathlib
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
DEFAULT_REV = "classic-3.8"
DEFAULT_OUT = DOCS / "classic"
EXPECTED_PAGES = 16
SKIP_ROOT = {"robots.txt", "sitemap.xml", "vercel.json"}

ROBOTS = '<meta name="robots" content="noindex, nofollow" />'
BANNER = """<div class="add-classic-banner" role="note">
  <span><b>ADD</b> <span aria-hidden="true">·</span> Classic shelf, 3.8</span>
  <a href="/">Current edition <span aria-hidden="true">→</span></a>
</div>"""
STYLE = """<style id="add-classic-banner-style">
.add-classic-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 20px;
  box-sizing: border-box;
  padding: 9px 20px;
  border-top: 4px solid #DD4124;
  border-bottom: 1px solid #15120E;
  background: #E9E0CB;
  color: #15120E;
  font-family: "Poppins", "Helvetica Neue", Arial, sans-serif;
  font-size: 11px;
  line-height: 1.4;
  letter-spacing: .1em;
  text-transform: uppercase;
}
.add-classic-banner b {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -.04em;
  text-transform: uppercase;
}
.add-classic-banner a {
  color: inherit;
  text-decoration: none;
  border-bottom: 1px solid #DD4124;
  padding-bottom: 1px;
  white-space: nowrap;
}
.add-classic-banner a:hover { color: #DD4124; }
@media (max-width: 600px) {
  .add-classic-banner { padding: 8px 14px; font-size: 10px; }
  .add-classic-banner b { font-size: 14px; }
}
</style>"""


def run_archive(rev, out):
    """Read the docs tree from the tag and copy it into the archive directory."""
    proc = subprocess.run(
        ["git", "archive", "--format=tar", rev, "docs"],
        cwd=ROOT,
        capture_output=True,
        check=False,
    )
    if proc.returncode:
        raise SystemExit("archive_classic: cannot read %s: %s"
                         % (rev, proc.stderr.decode("utf-8", "replace").strip()))

    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)

    with tempfile.TemporaryDirectory(prefix="add-classic-") as tmp:
        with tarfile.open(fileobj=io.BytesIO(proc.stdout)) as tar:
            tar.extractall(tmp, filter="data")
        source = pathlib.Path(tmp) / "docs"
        if not source.is_dir():
            raise SystemExit("archive_classic: %s has no docs tree" % rev)

        for item in sorted(source.rglob("*")):
            if not item.is_file():
                continue
            rel = item.relative_to(source)
            if rel.as_posix() in SKIP_ROOT:
                continue
            target = out / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(item, target)

    pages = sorted(out.rglob("*.html"))
    if len(pages) != EXPECTED_PAGES:
        raise SystemExit("archive_classic: expected %d pages, found %d"
                         % (EXPECTED_PAGES, len(pages)))
    for page in pages:
        add_archive_markup(page)


def add_archive_markup(page):
    """Mark one old page as an archive and give it a way back to the new shelf."""
    text = page.read_text(encoding="utf-8")
    if "</head>" not in text or not re.search(r"<body", text, re.I):
        raise SystemExit("archive_classic: %s has no normal head/body"
                         % page.relative_to(ROOT))
    if '<meta name="robots"' not in text:
        text = text.replace(
            "</head>",
            ROBOTS + "\n" + STYLE + "\n</head>",
            1,
        )
    if 'class="add-classic-banner"' not in text:
        text = re.sub(
            r"(<body[^>]*>)",
            lambda match: match.group(1) + "\n\n" + BANNER,
            text,
            count=1,
            flags=re.I,
        )
    page.write_text(text, encoding="utf-8")


def check(out):
    if not out.is_dir():
        raise SystemExit("archive_classic: %s is missing; run the builder" % out)
    pages = sorted(out.rglob("*.html"))
    problems = []
    if len(pages) != EXPECTED_PAGES:
        problems.append("expected %d pages, found %d" % (EXPECTED_PAGES, len(pages)))
    for page in pages:
        text = page.read_text(encoding="utf-8")
        if '<meta name="robots" content="noindex, nofollow"' not in text:
            problems.append("%s has no noindex meta" % page.relative_to(ROOT))
        if 'class="add-classic-banner"' not in text:
            problems.append("%s has no classic banner" % page.relative_to(ROOT))
    for name in SKIP_ROOT:
        if (out / name).exists():
            problems.append("%s should not be in the archive" % name)
    if not (out / "ADD-3.8.apk").is_file():
        problems.append("ADD-3.8.apk is missing")
    if problems:
        print("classic: stale")
        for problem in problems[:20]:
            print("  " + problem)
        raise SystemExit(1)
    print("classic: OK - %d pages, old APK preserved, noindex on every page" % len(pages))


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--rev", default=DEFAULT_REV,
                        help="git tag or commit to snapshot (default: %s)" % DEFAULT_REV)
    parser.add_argument("--out", default=str(DEFAULT_OUT),
                        help="archive directory (default: docs/classic)")
    parser.add_argument("--check", action="store_true",
                        help="check the archive instead of rebuilding it")
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    if not out.is_absolute():
        out = ROOT / out
    if args.check:
        check(out)
        return
    run_archive(args.rev, out)
    check(out)


if __name__ == "__main__":
    main()
