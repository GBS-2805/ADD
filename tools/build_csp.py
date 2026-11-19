#!/usr/bin/env python3
"""Generate and check the enforcing CSP for the site and handoff.

    python3 tools/build_csp.py --check
    python3 tools/build_csp.py --write

The policy carries a SHA-256 for every inline script in the shipped site and
handoff. `style-src 'unsafe-inline'` remains a recorded residual risk until
inline style attributes are extracted; it is not a script-src exception.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import sys
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONFIGS = ("docs/vercel.json", "handoff/vercel.json")
CSP_HEADER = "Content-Security-Policy"
REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only"
SITE_ORIGIN = "https://artbase-olive.vercel.app"


class InlineScripts(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.scripts: list[str] = []
        self.current: list[str] | None = None
        self.external = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag != "script":
            return
        attrs = {key: value or "" for key, value in attrs}
        if attrs.get("src"):
            self.external = True
            self.current = None
        else:
            self.external = False
            self.current = []

    def handle_data(self, data: str) -> None:
        if self.current is not None:
            self.current.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag == "script" and self.current is not None:
            self.scripts.append("".join(self.current))
            self.current = None


def _pages(root: Path) -> list[Path]:
    return sorted((root / "docs").rglob("index.html"))


def inline_scripts(root: Path = ROOT) -> list[tuple[Path, str]]:
    found = []
    docs = root / "docs"
    for page in _pages(root):
        parser = InlineScripts()
        parser.feed(page.read_text(encoding="utf-8"))
        for script in parser.scripts:
            if script.strip():
                found.append((page.relative_to(docs), script))
    return found


def script_hash(script: str) -> str:
    digest = hashlib.sha256(script.encode("utf-8")).digest()
    return "sha256-" + base64.b64encode(digest).decode("ascii")


def policy(root: Path = ROOT, *, handoff: bool = False) -> str:
    hashes = sorted({script_hash(script) for _, script in inline_scripts(root)})
    script_src = "script-src 'self'" + "".join(" '%s'" % item for item in hashes)
    # The handoff intentionally embeds screenshots served by the site origin.
    # Keep that exception on the handoff only; the site itself stays same-origin.
    #
    # `blob:` is for /twin/, the canvas editor. It builds images it imported with
    # URL.createObjectURL, so every imported PNG/SVG reaches the page as a blob: URL.
    # Without this, opening the editor works and importing a picture is silently
    # blocked. connect-src needs it too: exporting a .twin re-reads those same blob
    # URLs to inline them. Both are page-local objects, not remote sources.
    img_src = "img-src 'self' data: blob:"
    if handoff:
        img_src += " " + SITE_ORIGIN
    return (
        "default-src 'self'; base-uri 'none'; object-src 'none'; "
        "frame-ancestors 'none'; form-action 'self'; "
        + img_src
        + "; font-src 'self'; connect-src 'self' blob:; "
        + script_src
        + "; style-src 'self' 'unsafe-inline'"
    )


def _csp_value(data: dict) -> str | None:
    for entry in data.get("headers", []):
        for header in entry.get("headers", []):
            if header.get("key") == CSP_HEADER:
                return header.get("value")
    return None


def _set_csp(data: dict, value: str) -> None:
    entries = data.setdefault("headers", [])
    entry = next((item for item in entries if item.get("source") == "/(.*)"), None)
    if entry is None:
        entry = {"source": "/(.*)", "headers": []}
        entries.append(entry)
    headers = entry.setdefault("headers", [])
    # Replace the staging header rather than keeping both; two policies on one
    # response would leave the old report-only one looking active forever.
    headers[:] = [header for header in headers
                  if header.get("key") not in {CSP_HEADER, REPORT_ONLY_HEADER}]
    headers.append({"key": CSP_HEADER, "value": value})


def write_configs(root: Path = ROOT) -> None:
    for relative in CONFIGS:
        value = policy(root, handoff=relative.startswith("handoff/"))
        path = root / relative
        data = json.loads(path.read_text(encoding="utf-8"))
        _set_csp(data, value)
        path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def problems(root: Path = ROOT) -> list[str]:
    root = Path(root)
    found = []
    for relative in CONFIGS:
        path = root / relative
        expected = policy(root, handoff=relative.startswith("handoff/"))
        hashes = {part for part in expected.split() if part.startswith("'sha256-")}
        expected_img = next(part for part in expected.split(";") if part.strip().startswith("img-src"))
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as error:
            found.append("%s: cannot read config (%s)" % (path, error))
            continue
        value = _csp_value(data)
        if not value:
            found.append("%s: missing enforcing %s" % (path, CSP_HEADER))
        if any(header.get("key") == REPORT_ONLY_HEADER
               for entry in data.get("headers", [])
               for header in entry.get("headers", [])):
            found.append("%s: still carries %s" % (path, REPORT_ONLY_HEADER))
        if not value:
            continue
        actual_img = next((part for part in value.split(";")
                           if part.strip().startswith("img-src")), "")
        if actual_img.strip() != expected_img.strip():
            found.append("%s: CSP img-src is %r, expected %r"
                         % (path, actual_img.strip(), expected_img.strip()))
        for item in sorted(hashes):
            if item not in value:
                found.append("%s: CSP is missing inline-script hash %s" % (path, item))
        if "unsafe-eval" in value:
            found.append("%s: CSP contains unsafe-eval" % path)
        if "style-src 'self' 'unsafe-inline'" not in value:
            found.append("%s: CSP does not record the style-src residual" % path)
    return found


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="check configs (default)")
    parser.add_argument("--write", action="store_true", help="write the generated policy")
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)

    if args.write:
        write_configs(args.root)
        print("csp: wrote %s to %s" % (CSP_HEADER, ", ".join(CONFIGS)))
        return 0
    found = problems(args.root)
    if found:
        for item in found:
            print("  " + item)
        print("csp: %d problem(s)" % len(found))
        return 1
    count = len(inline_scripts(args.root))
    print("csp: OK - %d inline script(s), hashes present in both configs" % count)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
