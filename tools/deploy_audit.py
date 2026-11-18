#!/usr/bin/env python3
"""Export Vercel deployment deletions and retention changes as JSONL.

    python3 tools/deploy_audit.py --since 30d
    python3 tools/deploy_audit.py --since 30d --out artifacts/audit/vercel-activity.jsonl
    python3 tools/deploy_audit.py --since 1d --fail-on-delete

This is Hobby-compatible evidence, not Enterprise Audit Logs. It preserves the actor,
event, project, deployment and request IDs, but not IP address, user agent, before/after
state, CSV export or streaming. Archive the output outside the repository.
"""

import argparse
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CLI = ("npx", "-y", "vercel@65.14.0")
EVENT_TYPES = ("deployment-delete", "project-deployment-retention-updated")
PROJECTS = ("artbase", "artbase-handoff", "artbase-handoff-apk", "artbase-shots")
_PROJECT_TEXT = re.compile(r"\bon project ([A-Za-z0-9._-]+)\s*$")

sys.path.insert(0, str(ROOT / "tools"))
from deploy_storage import ReportError, admin_token  # noqa: E402 - the tools directory is on the path above
from vercel_cli import Timeout as CliTimeout  # noqa: E402 - the tools directory is on the path above
from vercel_cli import capture  # noqa: E402 - the tools directory is on the path above


def _activity(token, since, scope, project=None, cursor=None):
    cmd = [*CLI, "activity", "--since", since, "--limit", "100", "--json",
           "--type", ",".join(EVENT_TYPES)]
    if project:
        cmd += ["--project", project]
    else:
        cmd += ["--all"]
    if scope:
        cmd += ["--scope", scope]
    if cursor:
        cmd += ["--next", str(cursor)]
    env = {**os.environ, "VERCEL_TOKEN": token}
    try:
        code, out, err = capture(cmd, env, cwd=ROOT)
    except CliTimeout as error:
        raise RuntimeError("vercel activity stopped responding for %d seconds and was "
                           "killed; the window is incomplete" % error.timeout) from None
    if code:
        detail = (err or out).strip()
        raise RuntimeError("vercel activity failed (exit %d): %s" % (code, detail))
    try:
        return json.loads(out)
    except json.JSONDecodeError as error:
        raise RuntimeError("vercel activity returned invalid JSON: %s" % error) from error


def collect(token, since, scope):
    events = {}
    for project in (None, *PROJECTS):
        cursor = None
        for _ in range(20):
            payload = _activity(token, since, scope, project, cursor)
            for event in payload.get("events") or []:
                event_id = event.get("id")
                if event_id:
                    events[event_id] = event
            cursor = (payload.get("pagination") or {}).get("next")
            if not cursor:
                break
        if cursor:
            raise RuntimeError("activity result exceeded 2,000 events for %s; narrow --since"
                               % (project or "team"))
    return list(events.values())


def _timestamp(value):
    if not isinstance(value, (int, float)):
        return ""
    return datetime.fromtimestamp(value / 1000, tz=timezone.utc).isoformat().replace("+00:00", "Z")


def normalise(event):
    payload = event.get("payload") or {}
    deployment = payload.get("deployment") or {}
    project = payload.get("project") or {}
    if isinstance(project, str):
        project = {"name": project}
    actor = event.get("principal") or {}
    project_name = deployment.get("name") or project.get("name")
    if not project_name:
        match = _PROJECT_TEXT.search(event.get("text") or "")
        project_name = match.group(1) if match else None
    return {
        "event_id": event.get("id"),
        "type": event.get("type"),
        "created_at": _timestamp(event.get("createdAt")),
        "actor": actor.get("email"),
        "project": project_name,
        "project_id": deployment.get("projectId") or payload.get("projectId") or project.get("id"),
        "deployment_id": deployment.get("id"),
        "request_id": event.get("requestId"),
        "text": event.get("text") or "",
    }


def merge(path, events):
    merged = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            item = json.loads(line)
            merged[item["event_id"]] = item
    for event in events:
        merged[event["event_id"]] = event
    ordered = sorted(merged.values(), key=lambda item: (item.get("created_at", ""),
                                                        item.get("event_id", "")))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(item, sort_keys=True) + "\n" for item in ordered),
                    encoding="utf-8")
    return len(ordered)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--since", default="30d", help="Vercel activity window (default: 30d)")
    parser.add_argument("--scope", default=os.environ.get("VERCEL_SCOPE") or None,
                        help="Vercel team slug; defaults to the token's active scope")
    parser.add_argument("--out", type=Path, help="append/merge JSONL at this path")
    parser.add_argument("--fail-on-delete", action="store_true",
                        help="exit 1 when the window contains a deployment deletion")
    args = parser.parse_args()

    try:
        token = admin_token()
    except ReportError as error:
        print("deployment audit: unavailable (%s)" % error)
        return 1

    try:
        events = [normalise(event) for event in collect(token, args.since, args.scope)]
    except RuntimeError as error:
        print("deployment audit: unavailable (%s)" % error)
        return 1

    deletions = sum(1 for event in events if event["type"] == "deployment-delete")
    retention = len(events) - deletions
    if args.out:
        path = args.out if args.out.is_absolute() else ROOT / args.out
        total = merge(path, events)
        try:
            shown = path.relative_to(ROOT)
        except ValueError:
            shown = path
        print("audit: %d events (%d deletions, %d retention changes); %d in %s" %
              (len(events), deletions, retention, total, shown))
    else:
        for event in sorted(events, key=lambda item: (item["created_at"], item["event_id"])):
            print(json.dumps(event, sort_keys=True))
        print("audit: %d events (%d deletions, %d retention changes)" %
              (len(events), deletions, retention), file=sys.stderr)

    if args.fail_on_delete and deletions:
        print("audit: deployment deletions found in the window", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
