#!/usr/bin/env python3
"""Report retained Vercel deployments, retention and estimated storage.

    python3 tools/deploy_storage.py

The report is an estimate, not the dashboard's Deployment Storage total: Vercel does
not expose that byte total to Hobby teams through the API. It multiplies the current
output folder by the number of retained deployments, so it moves as soon as a project
starts accumulating full copies again. `deploy_vercel.py` and `deploy_review.py` call
`check(strict=True)` so a critical or unavailable report stops a release;
`deploy_audit.py` exports deletion and retention events.

Exit status is 0 for a clean report, 1 when the report cannot be fetched, and 2 when
a critical storage threshold is crossed.
"""

import argparse
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
API = "https://api.vercel.com"
PROJECTS = (
    ("artbase", ROOT / "docs"),
    ("artbase-handoff", ROOT / "artifacts" / "handoff"),
    ("artbase-shots", ROOT / "artifacts" / "shots"),
)
COUNT_WARN = 40
COUNT_CRIT = 80
SIZE_WARN = 512 * 1024 ** 2
SIZE_CRIT = 1024 ** 3
RETENTION_WARN_DAYS = 7
OK = 0
UNAVAILABLE = 1
CRITICAL = 2


class ReportError(RuntimeError):
    pass


def admin_token():
    """The read/audit token; keep it out of deploy-only environments."""
    token = os.environ.get("VERCEL_ADMIN_TOKEN") or os.environ.get("VERCEL_TOKEN")
    if not token:
        raise ReportError("VERCEL_ADMIN_TOKEN (or VERCEL_TOKEN) is not set")
    return token


def _api(path):
    request = urllib.request.Request(
        API + path,
        headers={
            "Authorization": "Bearer " + admin_token(),
            "User-Agent": "artbase-deployment-report",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read(300).decode("utf-8", "replace").strip()
        raise ReportError("HTTP %s%s" % (error.code, ": " + detail if detail else "")) from error
    except (urllib.error.URLError, TimeoutError) as error:
        raise ReportError(str(error)) from error


def _project(name):
    return _api("/v9/projects/" + urllib.parse.quote(name, safe=""))


def _deployments(project_id):
    """Every deployment Vercel currently returns, following its cursor."""
    deployments = []
    until = None
    while True:
        path = "/v6/deployments?projectId=%s&limit=100" % urllib.parse.quote(project_id, safe="")
        if until is not None:
            path += "&until=%s" % until
        payload = _api(path)
        deployments.extend(payload.get("deployments") or [])
        following = (payload.get("pagination") or {}).get("next")
        if not following or following == until or len(deployments) >= 2000:
            break
        until = following
    return deployments


def _output_bytes(path):
    if not path.exists():
        return 0
    return sum(item.stat().st_size for item in path.rglob("*") if item.is_file())


def _retention(policy):
    production = policy.get("expirationDaysProduction")
    preview = policy.get("expirationDays")
    keep = policy.get("deploymentsToKeep")
    if production is None and preview is None:
        return "unset"
    return "%sd/%sd keep %s" % (production, preview, keep)


def _max_retention(policy):
    values = [
        policy.get("expirationDays"),
        policy.get("expirationDaysProduction"),
        policy.get("expirationDaysCanceled"),
        policy.get("expirationDaysErrored"),
    ]
    return max([value for value in values if isinstance(value, int)], default=0)


def _size(value):
    if value >= 1024 ** 3:
        return "%.2f GiB" % (value / 1024 ** 3)
    if value >= 1024 ** 2:
        return "%.1f MiB" % (value / 1024 ** 2)
    if value >= 1024:
        return "%.1f KiB" % (value / 1024)
    return "%d B" % value


def report():
    """Print the report; return an OK, UNAVAILABLE or CRITICAL status."""
    rows = []
    try:
        for name, output in PROJECTS:
            project = _project(name)
            project_id = project.get("id")
            if not project_id:
                raise ReportError("%s has no project id" % name)
            deployments = _deployments(project_id)
            production = sum(1 for item in deployments if item.get("target") == "production")
            policy = project.get("deploymentExpiration") or {}
            rows.append({
                "name": name,
                "count": len(deployments),
                "production": production,
                "preview": len(deployments) - production,
                "retention": _retention(policy),
                "retention_days": _max_retention(policy),
                "output": _output_bytes(output),
                "estimate": _output_bytes(output) * len(deployments),
            })
    except ReportError as error:
        print("deployment storage: unavailable (%s)" % error)
        return UNAVAILABLE

    print("deployment storage (estimated from current output)")
    print("%-20s %5s %5s %7s %18s %12s" %
          ("project", "kept", "prod", "preview", "retention", "estimate"))
    for row in rows:
        print("%-20s %5d %5d %7d %18s %12s" %
              (row["name"], row["count"], row["production"], row["preview"],
               row["retention"], _size(row["estimate"])))

    total_deployments = sum(row["count"] for row in rows)
    total_estimate = sum(row["estimate"] for row in rows)
    print("%-20s %5d %5s %7s %18s %12s" %
          ("total", total_deployments, "", "", "", _size(total_estimate)))
    print("thresholds: %d deployments per project, %s total estimated, %d-day retention" %
          (COUNT_WARN, _size(SIZE_WARN), RETENTION_WARN_DAYS))

    warnings = []
    critical = False
    for row in rows:
        if row["count"] > COUNT_CRIT:
            critical = True
            warnings.append("%s has %d retained deployments (critical: over %d)" %
                            (row["name"], row["count"], COUNT_CRIT))
        elif row["count"] > COUNT_WARN:
            warnings.append("%s has %d retained deployments (over %d)" %
                            (row["name"], row["count"], COUNT_WARN))
        if row["estimate"] > SIZE_CRIT:
            critical = True
            warnings.append("%s estimates %s of output (critical: over %s)" %
                            (row["name"], _size(row["estimate"]), _size(SIZE_CRIT)))
        if row["retention_days"] > RETENTION_WARN_DAYS:
            warnings.append("%s keeps deployments for %d days (over %d)" %
                            (row["name"], row["retention_days"], RETENTION_WARN_DAYS))
    if total_estimate > SIZE_CRIT:
        critical = True
        warnings.append("all projects estimate %s of output (critical: over %s)" %
                        (_size(total_estimate), _size(SIZE_CRIT)))
    elif total_estimate > SIZE_WARN:
        warnings.append("all projects estimate %s of output (over %s)" %
                        (_size(total_estimate), _size(SIZE_WARN)))

    if warnings:
        print("warnings:")
        for warning in warnings:
            print("  " + warning)
    else:
        print("warnings: none")

    print("note: estimate = current output folder x retained deployments; Vercel may "
          "deduplicate, and exact Deployment Storage bytes are dashboard-only on Hobby.")
    return CRITICAL if critical else OK


def check(strict=False):
    """Run the report and stop on critical; optionally stop when it is unavailable."""
    status = report()
    if status == CRITICAL:
        raise SystemExit("deployment storage: critical threshold crossed; prune old "
                         "deployments or lower retention before deploying")
    if strict and status == UNAVAILABLE:
        raise SystemExit("deployment storage: report unavailable; refusing to deploy "
                         "without a storage check")
    return status


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--gate", action="store_true",
                        help="exit non-zero when storage is critical or unavailable")
    args = parser.parse_args()
    raise SystemExit(check(strict=args.gate))


if __name__ == "__main__":
    main()
