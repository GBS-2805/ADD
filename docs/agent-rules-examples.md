# Agent rules — worked examples

These show how each row of the delivery matrix runs in practice. They are examples,
not a second policy: `AGENTS.md` wins.

## 1. Copy-only page edit

Change a sentence in `docs/portfolio/index.html`, then run
`python3 tools/check_all.py`. No render, no review URL and no deploy are required,
because the change cannot alter what a reader sees. Commit only if asked. **Not
required:** captures, artboards, handoff.

## 2. Visual page edit

Change the layout of a page, then run `python3 tools/check_all.py --visual`. Publish a
review with `python3 tools/deploy_review.py review` and captures with
`python3 tools/deploy_review.py shots`; capture 1300 and 390 widths. Send the live or
review link, the gallery link and a labelled phone capture, then wait for the verdict.
After approval, recapture, re-export the artboards, deploy and refresh the handoff.
**Links required:** live site, gallery, phone capture.

## 3. APK review

Build the APK, run `python3 tools/check_apk.py` against it, install it on the x86_64
smoke device, publish the handoff and report the arm64 phone gap. **Links required:**
handoff, APK. **Not required:** a site deploy unless the site changed.

## 4. Deploy-only

Confirm the commit is on `main`, run `python3 tools/check_all.py --visual`, then
`python3 tools/deploy_vercel.py`. The deploy writes the receipt and runs live parity.
Refresh the handoff only when the APK or the status changed. **Links required:** live
site, handoff.

## 5. Skill-assisted task

Check the available catalog, read the one matching skill in full, then apply its
required steps. Say which parts actually ran. A one-line copy edit uses no skill.
**Not required:** loading several skills or adding their optional scope.
