#!/usr/bin/env python3
"""Fail when the Icons bundle is missing a file one of its pages fetches.

    python3 tools/check_bundle.py [app/assets/www]

The app is a WebView over a staged copy of `docs/`, and the staging list in
`build.sh` is written by hand. A page whose JSON never got staged still loads and
draws its chrome, then paints nothing: 2.5 shipped exactly that, with the
all-brands page asking for sheet catalogs the bundle did not carry, and every step
of the release reported success because nothing looked.

This reads the references out of the staged pages and resolves each one inside the
bundle. Anything that points above the bundle root is left alone -- a page is
allowed to name the site for what the app deliberately does not carry.
"""
import json
import pathlib
import re
import sys

# Only assets a page cannot render without. A missing stylesheet or catalog shows
# up as a blank or unstyled page, which is the failure this exists to catch.
REF = re.compile(r"""["']((?:\.\./|\./)*)([\w-]+(?:/[\w.-]+)*\.(?:json|css|js|woff2|svg))["']""")


def main(argv):
    root = pathlib.Path(argv[1] if len(argv) > 1 else "app/assets/www")
    if not root.is_dir():
        sys.exit("%s is not staged: run ./build.sh stage first" % root)
    root = root.resolve()
    pages = sorted(p for p in root.rglob("*.html"))
    if not pages:
        sys.exit("no staged pages under %s" % root)

    checked, missing = 0, []
    for page in pages:
        text = page.read_text(errors="replace")
        for up, ref in REF.findall(text):
            target = (page.parent / (up + ref)).resolve()
            if root not in target.parents:
                continue          # names the site, not the bundle
            checked += 1
            if not target.exists():
                missing.append("%s -> %s" % (page.relative_to(root), ref))

    # A chunked catalog names its glyph files from inside JSON, so the reference
    # scan above cannot see them; a missing chunk would render an empty sheet.
    for data in sorted(root.rglob("*.json")):
        try:
            payload = json.loads(data.read_text(errors="replace"))
        except ValueError:
            continue
        for name in (payload.get("chunks") or []) if isinstance(payload, dict) else []:
            checked += 1
            if not (data.parent / name).exists():
                missing.append("%s -> %s" % (data.relative_to(root), name))

    if missing:
        print("bundle check: %d of %d references resolve" % (checked - len(missing), checked))
        for line in missing[:20]:
            print("  missing " + line)
        if len(missing) > 20:
            print("  ... %d more" % (len(missing) - 20))
        sys.exit("check_bundle: the app would render these pages empty or unstyled")
    print("bundle check: %d pages, %d references, all resolve" % (len(pages), checked))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
