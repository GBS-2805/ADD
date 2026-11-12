# Deployment storage operations

This is the runbook for the Vercel deployment storage of `artbase`, `artbase-handoff`,
`artbase-handoff-apk` and `artbase-shots`. It is an operational and evidence record,
not a compliance certificate.

## Current baseline

- Keep the newest **10** production deployments per project.
- Retention is **1 day** for preview, production, canceled and errored deployments.
  Vercel rejected `3d`; `1d` is the shortest accepted value.
- The gate warns above 40 deployments per project, 450 MiB estimated total (early
  warning), 512 MiB estimated total, or 7-day retention. It stops a release above 80
  deployments per project, 1 GiB per project, or 1 GiB estimated total.
- The size figure is an estimate: current output folder x retained deployments. Exact
  Deployment Storage bytes are dashboard-only on Hobby.

## Normal releases

```bash
python3 tools/deploy_vercel.py                 # docs / artbase
python3 tools/deploy_review.py apk             # artifacts/handoff-apk
python3 tools/deploy_review.py handoff         # artifacts/handoff
python3 tools/deploy_review.py shots           # artifacts/shots
python3 tools/deploy_review.py review          # docs / artbase-review
```

All four run a strict storage gate. A critical report **or an unavailable report**
stops the upload. The review target creates a temporary `artbase-review` project; it
is not part of the storage report, so delete that project after the verdict.

Use `--check` on `deploy_vercel.py` or `deploy_review.py` to print the plan without
deploying. `deploy_review.py --check` does not need a token.

## Credentials

Use the narrowest token available:

- `VERCEL_ARTBASE_TOKEN` for the docs deploy, otherwise `VERCEL_TOKEN`.
- `VERCEL_APK_TOKEN` for the review APK deploy, otherwise `VERCEL_TOKEN`.
- `VERCEL_HANDOFF_TOKEN` for the handoff deploy, otherwise `VERCEL_TOKEN`.
- `VERCEL_REVIEW_TOKEN` for the temporary review deploy, otherwise `VERCEL_TOKEN`.
- `VERCEL_SHOTS_TOKEN` for the shots deploy, otherwise `VERCEL_TOKEN`.
- `VERCEL_ADMIN_TOKEN` for the storage report, audit export and cleanup. If it is not
  set, those tools fall back to `VERCEL_TOKEN`.

Create project-scoped tokens in Vercel Account Settings -> Tokens: choose the team,
then the individual project, and set an expiry. A project-scoped token only reaches
that project, which limits the blast radius. It can still delete deployments or change
settings **inside** that project.

Keep `VERCEL_ADMIN_TOKEN` out of deploy-only environments. The deploy wrappers remove
it (and the other project tokens) from the Vercel CLI child environment. Hobby has no
RBAC, so this is a tooling convention, not enforced role separation; Pro adds team
roles, Enterprise adds project roles.

## Changing retention

1. Record the reason, date and proposed values in the change log below (or an issue /
   pull request for a multi-person project).
2. Keep at least 10 production deployments; do not go below the current keep floor.
3. Apply the dedicated endpoint, not a project PATCH:

   ```text
   PATCH /v9/projects/<project-id>/deployment-expiration?teamId=<team-id>
   {"expiration":"1d","expirationProduction":"1d",
    "expirationCanceled":"1d","expirationErrored":"1d"}
   ```

4. Read the project back and confirm all four `expirationDays` fields and
   `deploymentsToKeep`.
5. Run `python3 tools/deploy_storage.py`; expect a clean report.
6. Export the change evidence (below). Vercel logs a successful change as
   `project-deployment-retention-updated`.

`3d` was rejected by the API. Do not retry it; verify the accepted value by reading
the project back.

## Pruning deployments

1. Get approval (owner entry in the change log for a personal project; issue/PR for a
   team).
2. Protect the current production aliases and the newest 10 production deployments.
   Never delete the deployment holding `artbase-olive.vercel.app`,
   `artbase-handoff.vercel.app`, `artbase-handoff-apk.vercel.app` or
   `artbase-shots.vercel.app`.
3. List candidates and record project, deployment ID, age and target.
4. Delete with `vercel remove --safe --yes --token "$VERCEL_ADMIN_TOKEN" --scope
   alfredglezespitia-1605 <deployment>`; do not use an unrestricted pattern.
5. Re-run `tools/deploy_storage.py`; confirm counts, retention and aliases.
6. Export deletion evidence and archive it outside the repo.
7. Check the live sites (HTTP 200) and the APK content type/size before closing.

## Audit export and monitoring

```bash
python3 tools/deploy_audit.py --since 90d \
  --out artifacts/audit/vercel-activity.jsonl
python3 tools/deploy_audit.py --since 1d --fail-on-delete
```

The first command merges deletion and retention events into JSONL without duplicate
event IDs; use `90d` for the full cleanup history and `1d`/`7d` for monitoring. The
export queries the team and each project explicitly, so a project-specific gap in the
team-wide feed does not drop events. The second command is a monitoring hook: it exits
1 when a deployment was deleted in the window. Run it after a planned cleanup or with
`--fail-on-delete` in a scheduled job; it does not schedule itself.

The JSONL keeps the actor email, event ID, timestamp, project, deployment ID, request
ID and event text. It deliberately omits the token ID. `artifacts/` is gitignored;
copy the file to Drive or another durable store after each review. This export is the
Hobby substitute, not Enterprise Audit Logs: it has no IP address, user agent,
before/after state, CSV export, SIEM streaming, or immutable retention guarantee.

## Scheduled audit check

`.github/workflows/deploy-audit.yml` runs daily at **06:17 UTC** and can be dispatched
manually. It first runs the storage report and posts a warning annotation at the
450 MiB early threshold, then exports the last day of deletion and retention events,
fails when any deployment deletion is present, and uploads the JSONL as a 90-day
GitHub artifact even when the check fails.

For a manual test of the warning annotation, set `storage_warning_mib` on the
dispatch to a value below the current estimate, for example `300`. That overrides
only the early-warning size for that run; the 512 MiB warning and the critical gates
stay fixed, and scheduled runs keep the 450 MiB default.

Add `VERCEL_ADMIN_TOKEN` (preferred) or `VERCEL_TOKEN` under repository Settings ->
Secrets and variables -> Actions. Scheduled workflows only run from the default
branch, and the workflow does not exist remotely until these changes are pushed.

The check is deliberately sensitive: a planned cleanup or an automatic retention
deletion also produces a failure. Inspect the artifact, confirm the actor and IDs, and
record the planned cleanup in the change log. GitHub's failure notification is the
alert; the artifact is copyable evidence, not an immutable audit system.

Vercel's [Activity Log](https://vercel.com/docs/activity-log) is available on Hobby.
[Audit Logs](https://vercel.com/docs/audit-log) are Enterprise-only.

## Change log

- **2026-11-12** — Before publishing the 3.9.1 field preview, the storage report was
  critical at 1.18 GiB. Pruned the two oldest `artbase-handoff-apk` deployments
  (`dpl_5GNZWbue7o8pQm7eEUNJQoKhAB3d`, `dpl_B3N6HKs4CT1mGqUFbPiYsp3rSt49`), keeping the
  deployment that holds `artbase-handoff-apk.vercel.app`. Every historical APK URL on
  that alias was checked 200 before deletion. The APK and handoff deployments then
  brought the estimate to 921.1 MiB (warning, below the 1.00 GiB gate). Evidence:
  `artifacts/audit/prune-2026-11-12-before.json`,
  `artifacts/audit/prune-2026-11-12-remove.log`,
  `artifacts/audit/deploy-apk-2026-11-12.log`,
  `artifacts/audit/deploy-handoff-2026-11-12.log` and
  `artifacts/audit/vercel-activity-2026-11-12.jsonl`.

- **2026-11-11** — After the 3.9 publication, pruned 4 superseded deployments:
  the 2 oldest `artbase` and the 2 oldest `artbase-handoff-apk`, keeping the newest
  3 site builds and 2 APK builds and every live alias. Estimated storage fell from
  566.0 MiB to 340.6 MiB, with no warnings. Evidence:
  `artifacts/audit/prune-2026-11-11-before.json`,
  `artifacts/audit/prune-2026-11-11-remove.log` and the one-day audit export
  `artifacts/audit/vercel-activity-2026-11-11.jsonl`.

- **2026-11-11** — After approval, pruned 61 deployments: the 5 oldest
  `artbase`, 7 oldest `artbase-handoff-apk`, and 49 oldest `artbase-shots`
  deployments, keeping the newest 3/2/10 and protecting every live alias.
  Estimated storage fell from 1.13 GiB to 337.7 MiB. The signed 16 MB ADD 3.8
  APK was then published to the site and handoff; current retained set is
  4 `artbase`, 8 `artbase-handoff`, 3 `artbase-handoff-apk`, 10 `artbase-shots`,
  estimated 451.9 MiB, no warnings.

- **2026-11-09** — Moved the 29,041,927-byte build 47 review APK to the dedicated
  `artbase-handoff-apk` project and removed it from `artifacts/handoff`, so a handoff
  deployment carries the page and five screenshots (~1.4 MiB) instead of the APK.
  The handoff page links the stable `https://artbase-handoff-apk.vercel.app/` URL.
  The handoff estimate drops from 931.2 MiB to ~46 MiB; the old fat deployments remain
  retained until Vercel expires them, and the new project adds one ~27.7 MiB copy.
  Retention on the new project is 1d/keep 10, matching the other projects.
- **2026-11-07** — Replaced the 54-image shots showcase with nine green-dark
  representatives plus a 3×3 contact sheet, published `artbase-shots`, and pruned
  the five oldest deployments to the newest 10; the live alias was protected and
  rechecked. Current retained set: 10 `artbase`, 13 `artbase-handoff`, 10
  `artbase-shots`; estimated 429.8 MiB, no storage warnings.
- **2026-11-07** — Retention set to 1 day; keep floor 10 verified on all three
  projects. 142 cleanup deletions (95 `artbase`, 10 `artbase-handoff`, 37
  `artbase-shots`) are present in the Activity Log; one deleted review project is
  also present. Current retained set: 10/10/10, estimated 389.0 MiB.
- Earlier `3d` attempt was rejected by Vercel; no policy change resulted.

## What a personal project can skip

Worth keeping: the strict gate, the 10-deployment floor, the runbook, and the daily
scheduled audit export. Those are cheap and cover the realistic failure: accidental
storage growth or an unexplained deletion.

Worth skipping until there is another person or a compliance requirement: Enterprise
Audit Logs/SIEM streaming, formal RBAC, and a separate approval system. Use a
project-scoped token for deploys and keep the admin token out of that environment.

The one thing not to skip if the project matters: archive the audit export somewhere
outside Vercel. Hobby's Activity Log is evidence, but it is not an immutable audit
system.
