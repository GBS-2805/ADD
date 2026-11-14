#!/usr/bin/env python3
"""Check the live site and handoff security headers.

    python3 tools/check_headers.py --origin https://artbase-olive.vercel.app
    python3 tools/check_headers.py --origin https://artbase-olive.vercel.app \
        --handoff-origin https://artbase-handoff.vercel.app

This is an explicit network step; it is not part of the offline fast path. The
policy starts report-only, so either the report-only or enforcing CSP header is
accepted, but neither may contain unsafe-eval.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from collections.abc import Callable
from pathlib import Path
from typing import NamedTuple

ROOT_URL = "/"
ROOT = Path(__file__).resolve().parent.parent


class Fetched(NamedTuple):
    url: str
    status: int
    headers: dict[str, str]


def fetch(url: str) -> Fetched:
    request = urllib.request.Request(url, headers={"User-Agent": "ADD-header-gate/1"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return Fetched(response.geturl(), response.status,
                       {key.lower(): value for key, value in response.headers.items()})


def _hsts(value: str) -> tuple[bool, str]:
    match = re.search(r"max-age\s*=\s*(\d+)", value or "")
    if not match:
        return False, "missing max-age"
    if int(match.group(1)) < 31536000:
        return False, "max-age is below one year"
    if "includesubdomains" not in value.lower():
        return False, "missing includeSubDomains"
    return True, ""


def _csp(headers: dict[str, str]) -> tuple[str | None, str]:
    value = headers.get("content-security-policy") or headers.get(
        "content-security-policy-report-only")
    if not value:
        return None, "missing CSP or CSP-Report-Only"
    if "unsafe-eval" in value.lower():
        return value, "CSP contains unsafe-eval"
    for directive in ("default-src 'self'", "object-src 'none'", "frame-ancestors 'none'"):
        if directive not in value:
            return value, "CSP lacks %s" % directive
    return value, ""


def problems(origin: str, *, handoff_origin: str | None = None,
             fetch_fn: Callable[[str], Fetched] | None = None,
             expected: dict[str, str] | None = None,
             asset_path: str | None = None) -> list[str]:
    fetch_fn = fetch_fn or fetch
    found = []
    origins = [("site", origin.rstrip("/"))]
    if handoff_origin:
        origins.append(("handoff", handoff_origin.rstrip("/")))
    required = {
        "x-content-type-options": "nosniff",
        "x-frame-options": "DENY",
        "referrer-policy": "strict-origin-when-cross-origin",
        "cross-origin-opener-policy": "same-origin",
        "cross-origin-resource-policy": "same-origin",
        "x-permitted-cross-domain-policies": "none",
    }
    required.update(expected or {})
    for label, base in origins:
        try:
            response = fetch_fn(base + ROOT_URL)
        except (OSError, urllib.error.URLError) as error:
            found.append("%s: cannot fetch %s (%s)" % (label, base, error))
            continue
        if response.url.startswith("http://"):
            found.append("%s: redirected to insecure %s" % (label, response.url))
        if response.status != 200:
            found.append("%s: status %d, expected 200" % (label, response.status))
        for name, wanted in required.items():
            actual = response.headers.get(name)
            if actual is None:
                found.append("%s: missing %s" % (label, name))
            elif wanted and wanted.lower() not in actual.lower():
                found.append("%s: %s is %r, expected %r"
                             % (label, name, actual, wanted))
        hsts = response.headers.get("strict-transport-security", "")
        ok, why = _hsts(hsts)
        if not ok:
            found.append("%s: Strict-Transport-Security %s" % (label, why))
        csp, why = _csp(response.headers)
        if why:
            found.append("%s: %s" % (label, why))
        permissions = response.headers.get("permissions-policy", "")
        if "camera=()" not in permissions:
            found.append("%s: Permissions-Policy does not deny camera" % label)
        if label == "site" and csp and "style-src 'self' 'unsafe-inline'" in csp:
            # Recorded residual risk, not a failure: inline style attributes remain.
            pass
        if label == "site" and asset_path:
            try:
                asset = fetch_fn(base + asset_path)
            except (OSError, urllib.error.URLError) as error:
                found.append("site: cannot fetch hashed asset %s (%s)" % (asset_path, error))
            else:
                cache = asset.headers.get("cache-control", "")
                if "max-age=31536000" not in cache or "immutable" not in cache:
                    found.append("site: hashed asset cache-control is %r" % cache)
    return found


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--origin", required=True)
    parser.add_argument("--handoff-origin")
    parser.add_argument("--asset", help="hashed asset path to check (default from the asset map)")
    args = parser.parse_args(argv)

    asset = args.asset
    if asset is None:
        try:
            names = (ROOT / "docs" / "data" / "assets.json").read_text(encoding="utf-8")
            asset = "/assets/" + json.loads(names)["files"]["site.css"]
        except (OSError, ValueError, KeyError):
            asset = None
    found = problems(args.origin, handoff_origin=args.handoff_origin, asset_path=asset)
    if found:
        for item in found:
            print("  " + item)
        print("headers: %d problem(s)" % len(found))
        return 1
    where = "site and handoff" if args.handoff_origin else "site"
    print("headers: OK - %s carry CSP, HSTS, frame denial and cache policy" % where)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
