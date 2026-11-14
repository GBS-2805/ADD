#!/usr/bin/env python3
"""Generate and check the report-only CSP for the site and handoff.

    python3 tools/build_csp.py --check
    python3 tools/build_csp.py --write

The policy is report-only on purpose. It carries a SHA-256 for every inline
script in the modern site; `style-src 'unsafe-inline'` remains a recorded
residual risk until inline style attributes are extracted.
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
CSP_HEADER = "Content-Security-Policy-Report-Only"


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
    docs = root / "docs"
    return sorted(path for path in docs.rglob("index.html")
                  if "classic" not in path.relative_to(docs).parts)


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


def policy(root: Path = ROOT) -> str:
    hashes = sorted({script_hash(script) for _, script in inline_scripts(root)})
    script_src = "script-src 'self'" + "".join(" '%s'" % item for item in hashes)
    return (
        "default-src 'self'; base-uri 'none'; object-src 'none'; "
        "frame-ancestors 'none'; form-action 'self'; "
        "img-src 'self' data:; font-src 'self'; connect-src 'self'; "
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
    item = next((header for header in headers if header.get("key") == CSP_HEADER), None)
    if item is None:
        headers.append({"key": CSP_HEADER, "value": value})
    else:
        item["value"] = value


def write_configs(root: Path = ROOT) -> None:
    value = policy(root)
    for relative in CONFIGS:
        path = root / relative
        data = json.loads(path.read_text(encoding="utf-8"))
        _set_csp(data, value)
        path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def problems(root: Path = ROOT) -> list[str]:
    root = Path(root)
    expected = policy(root)
    hashes = {part for part in expected.split() if part.startswith("'sha256-")}
    found = []
    for relative in CONFIGS:
        path = root / relative
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as error:
            found.append("%s: cannot read config (%s)" % (path, error))
            continue
        value = _csp_value(data)
        if not value:
            found.append("%s: missing %s" % (path, CSP_HEADER))
            continue
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
