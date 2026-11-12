#!/usr/bin/env python3
"""Check that the bytes served by a deployed origin match this checkout.

The deploy receipt is a small JSON file written before the upload:

    {"schema": 1, "commit": "...", "deployedAt": "...", "project": "artbase"}

`deploy_vercel.py` writes it locally and checks parity after the upload. The
daily drift job has no local receipt (the file is ignored), so `--receipt live`
fetch it from `<origin>/_deploy.json` instead. That distinction matters: a
missing local file is a setup mistake, while a missing live file means the
deployment predates receipts and is itself drift.

    python3 tools/check_live.py --origin URL --docs docs --expect-commit SHA
    python3 tools/check_live.py ... --receipt live
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
RECEIPT = DOCS / "_deploy.json"
SITE = ROOT / "tools/site.json"
MAX_HASH_BYTES = 2 * 1024 * 1024


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise SystemExit("check_live: cannot read %s: %s" % (path, error))


def _fetch(url: str, timeout: int = 30) -> bytes:
    request = urllib.request.Request(
        url, headers={"Cache-Control": "no-cache", "User-Agent": "check-live"}
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read()


def receipt_for(origin: str, commit: str, receipt: str, docs: Path = DOCS) -> dict:
    """Read a local receipt, a path, or the receipt served by the origin."""
    if receipt == "live":
        url = origin.rstrip("/") + "/_deploy.json?deploy=" + commit
        try:
            return json.loads(_fetch(url).decode("utf-8"))
        except urllib.error.HTTPError as error:
            raise SystemExit(
                "check_live: no live receipt at %s (HTTP %s); deploy once with the "
                "receipt writer before using --receipt live" % (url, error.code)
            )
        except (ValueError, UnicodeDecodeError) as error:
            raise SystemExit("check_live: live receipt is not JSON: %s" % error)

    path = docs / "_deploy.json" if receipt == "local" else Path(receipt)
    if not path.is_absolute():
        path = ROOT / path
    if not path.exists():
        raise SystemExit("check_live: no receipt at %s" % path)
    return _json(path)


def apk_paths(docs: Path = DOCS) -> list[Path]:
    """The APKs named by generated data, with their expected digests."""
    data = _json(docs / "data/tools.json")
    found = []
    for tool in data.get("tools", []):
        if not tool.get("apk") or not tool.get("sha256"):
            continue
        path = docs / tool["apk"].lstrip("/")
        if path.exists():
            found.append(path)
    return found


def local_files(docs: Path = DOCS) -> list[Path]:
    """Every file whose live bytes matter, excluding the receipt itself."""
    paths = list(docs.rglob("*.html"))
    paths += sorted((docs / "renders").glob("*.webp"))
    paths += sorted((docs / "figma").glob("*.svg"))
    for relative in ("renders/manifest.json", "data/assets.json", "data/tools.json",
                     "assets/og.png"):
        path = docs / relative
        if path.exists():
            paths.append(path)
    paths += sorted((docs / "assets").glob("site.*"))
    paths += apk_paths(docs)
    unique = []
    seen = set()
    for path in paths:
        if path in seen or not path.is_file():
            continue
        # The APK is intentionally larger; the rest should be page-sized.
        if path.suffix != ".apk" and path.stat().st_size > MAX_HASH_BYTES:
            continue
        seen.add(path)
        unique.append(path)
    return sorted(unique)


def check(origin: str, docs: Path, expect_commit: str, receipt: str = "local") -> list[str]:
    """Return every parity problem; an empty list means the live bytes match."""
    origin = origin.rstrip("/")
    data = receipt_for(origin, expect_commit, receipt, docs)
    if data.get("commit") != expect_commit:
        return [
            "receipt commit %s != expected %s"
            % (data.get("commit", "(missing)"), expect_commit)
        ]

    paths = local_files(docs)
    problems = []

    def compare(path: Path) -> str | None:
        relative = path.relative_to(docs).as_posix()
        local = sha256(path.read_bytes())
        url = origin + "/" + relative + "?deploy=" + expect_commit
        try:
            live = sha256(_fetch(url))
        except urllib.error.HTTPError as error:
            return "%s: HTTP %s" % (relative, error.code)
        except Exception as error:  # noqa: BLE001 - report the URL and failure
            return "%s: %s: %s" % (relative, type(error).__name__, error)
        if local != live:
            return "%s: local %s != live %s" % (relative, local, live)
        return None

    with ThreadPoolExecutor(max_workers=8) as pool:
        problems.extend(item for item in pool.map(compare, paths) if item)

    print("live: %d files match %s at %s" % (len(paths), origin, expect_commit)
          if not problems else "live: %d of %d files differ from %s"
          % (len(problems), len(paths), origin))
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--origin", required=True, help="site origin, e.g. https://artbase-olive.vercel.app")
    parser.add_argument("--docs", type=Path, default=DOCS, help="local docs tree")
    parser.add_argument("--expect-commit", required=True, help="commit the deploy should carry")
    parser.add_argument("--receipt", default="local",
                        help="local, live, or a receipt file path (default: local)")
    args = parser.parse_args()

    problems = check(args.origin, args.docs, args.expect_commit, args.receipt)
    for problem in problems:
        print("  " + problem)
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
