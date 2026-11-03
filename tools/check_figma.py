#!/usr/bin/env python3
"""Fail when a Figma hand-off file cannot import as layers.

    python3 tools/check_figma.py [figma]

Figma reads presentation attributes off the element and does not paint <use>
instances. A run that inherits its family, size or fill from a wrapper group comes
in as an empty layer, and a mark interned into <defs> and painted with <use>
arrives as a gap -- either way the file looks right in a browser and shows only
blocks in Figma. This reads the exported files and refuses both.
"""
import pathlib
import re
import sys

TEXT = re.compile(r"<text\b([^>]*)>")
ATTR = re.compile(r'([\w:-]+)="([^"]*)"')
USE = re.compile(r"<use\b")
NEEDED = ("font-family", "font-size", "fill")
LIMIT = 12   # enough to name the pattern without burying the first failure


def problems(path):
    """What would keep this file from importing, in the order a reader hits it."""
    text = path.read_text(encoding="utf-8", errors="replace")
    found = []
    if USE.search(text):
        found.append("%s: paints a mark with <use>, which Figma does not import" % path.name)
    for number, match in enumerate(TEXT.finditer(text), 1):
        attrs = dict(ATTR.findall(match.group(1)))
        missing = [name for name in NEEDED if name not in attrs]
        if not missing:
            continue
        end = text.find("</text>", match.end())
        label = text[match.end(): end if end > 0 else match.end() + 40][:40]
        found.append("%s: text run %d (%r) lacks %s"
                     % (path.name, number, label, ", ".join(missing)))
        if len(found) >= LIMIT:
            found.append("%s: stopped after %d problems" % (path.name, LIMIT))
            break
    return found


def check(directory="figma"):
    root = pathlib.Path(directory)
    if not root.is_dir():
        return ["%s is not a directory" % root]
    files = sorted(root.glob("*.svg"))
    if not files:
        return ["no SVGs in %s" % root]
    issues = []
    for path in files:
        issues.extend(problems(path))
    return issues


def main(argv):
    directory = pathlib.Path(argv[1] if len(argv) > 1 else "figma")
    issues = check(str(directory))
    if issues:
        print("figma check: FAILED")
        for issue in issues:
            print("  " + issue)
        return 1
    print("figma check: OK - %d files, every text run carries its own type, no <use>"
          % len(list(directory.glob("*.svg"))))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
