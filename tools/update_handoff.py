#!/usr/bin/env python3
"""Refresh the handoff page's provenance block from the published tree.

    python3 tools/update_handoff.py --commit SHA --deployed-at ISO
    python3 tools/update_handoff.py --check --commit SHA --deployed-at pending

`pending` writes an honest "not yet deployed from this tree" line for a committed
handoff source; the publish job replaces it with the real deployment time.

Only the lines between the provenance markers change. The APK digest and size come
from the generated `docs/data/tools.json` and are verified against the APK that will
ship; the live origin comes from `tools/site.json`. A missing marker, a malformed
commit or a digest mismatch fails before anything is written.
"""

import argparse
import datetime
import hashlib
import html
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PAGE = ROOT / "handoff" / "index.html"
TOOLS = ROOT / "docs" / "data" / "tools.json"
CATALOG = ROOT / "tools" / "catalog.json"
SITE = ROOT / "tools" / "site.json"
START = "<!-- provenance:start -->"
END = "<!-- provenance:end -->"
SHA_RE = re.compile(r"^[0-9a-f]{7,40}$")


def load_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit("update_handoff: cannot read %s: %s" % (path, exc))


def published_app():
    data = load_json(TOOLS)
    tools = data.get("tools") or []
    if not tools:
        raise SystemExit("update_handoff: docs/data/tools.json has no tools")
    app = next((item for item in tools if item.get("id") == "add"), tools[0])
    for key in ("version", "apk", "bytes", "sha256"):
        if not app.get(key):
            raise SystemExit("update_handoff: the published app has no %s" % key)
    return app


def verify_apk(app):
    path = ROOT / "docs" / app["apk"].lstrip("/")
    if not path.is_file():
        raise SystemExit("update_handoff: %s is missing" % path.relative_to(ROOT))
    raw = path.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if len(raw) != int(app["bytes"]) or digest != app["sha256"]:
        raise SystemExit(
            "update_handoff: %s does not match docs/data/tools.json (%d B, %s); "
            "run tools/build_catalog.py after the APK changes"
            % (path.relative_to(ROOT), len(raw), digest))


def parse_time(value):
    text = value.strip()
    if text.lower() == "pending":
        return None
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    try:
        stamp = datetime.datetime.fromisoformat(text)
    except ValueError:
        raise SystemExit("update_handoff: --deployed-at must be ISO 8601, got %r" % value)
    if stamp.tzinfo is None:
        raise SystemExit("update_handoff: --deployed-at needs a timezone (use Z)")
    return stamp.astimezone(datetime.timezone.utc).replace(microsecond=0).isoformat().replace(
        "+00:00", "Z")


def origin_of(value):
    origin = value.rstrip("/")
    if not re.match(r"^https?://[^/\s]+$", origin):
        raise SystemExit("update_handoff: --origin must be an http(s) origin, got %r" % value)
    return origin


def block(commit, deployed_at, origin, gates, app):
    sha = html.escape(commit)
    when = html.escape(deployed_at) if deployed_at else None
    url = html.escape(origin)
    gate_text = html.escape(gates)
    version = html.escape(str(app["version"]))
    size = "{:,}".format(int(app["bytes"]))
    digest = html.escape(app["sha256"])
    short = html.escape(app["sha256"][:8] + "…")
    if when:
        state = "deployed <time datetime=\"%s\">%s</time> from" % (when, when)
    else:
        state = "not yet deployed from this tree; live origin"
    return (
        "<li><b>Committed:</b> <code>%s</code> — %s <a href=\"%s/\">%s</a>; "
        "APK %s, %s B, SHA-256 <code>%s</code> (<code>%s</code>); gates: %s.</li>"
        % (sha, state, url, url, version, size, digest, short, gate_text)
    )


def replace_block(text, body):
    if text.count(START) != 1 or text.count(END) != 1:
        raise SystemExit("update_handoff: handoff/index.html needs exactly one of each marker")
    before, rest = text.split(START, 1)
    _, after = rest.split(END, 1)
    return before + START + "\n        " + body + "\n        " + END + after


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--commit", required=True, help="deployed commit")
    parser.add_argument("--deployed-at", required=True,
                        help="deploy time, ISO 8601 with Z, or 'pending'")
    parser.add_argument("--origin", help="live origin (default: tools/site.json url)")
    parser.add_argument("--gates", default="shelf and visual",
                        help="gate state to print (default: shelf and visual)")
    parser.add_argument("--check", action="store_true", help="validate, print, write nothing")
    args = parser.parse_args()

    commit = args.commit.strip()
    if not SHA_RE.match(commit):
        raise SystemExit("update_handoff: --commit must be 7-40 lowercase hex characters")
    deployed_at = parse_time(args.deployed_at)
    origin = origin_of(args.origin or load_json(SITE).get("url", ""))
    app = published_app()
    verify_apk(app)

    if not PAGE.is_file():
        raise SystemExit("update_handoff: handoff/index.html is missing")
    text = PAGE.read_text(encoding="utf-8")
    body = block(commit, deployed_at, origin, args.gates, app)
    updated = replace_block(text, body)
    print(body)
    if args.check:
        print("update_handoff: check only; handoff/index.html not written")
        return 0
    PAGE.write_text(updated, encoding="utf-8")
    print("update_handoff: wrote provenance for %s" % commit)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
