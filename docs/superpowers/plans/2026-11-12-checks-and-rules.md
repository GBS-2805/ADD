# Checks and Agent Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a merge to `main` deploy the exact checked tree, and replace the 823-line mixed `AGENTS.md` with a short operating contract that names the real paths, gates, review rules and skill workflow.

**Architecture:** Part I keeps `check_all` as the verdict and adds a deploy receipt, byte parity, publish-after-checks, render freshness, required artboards, tracked handoff source, branch guard and pinned toolchain. Part II then moves project mechanics into a reference, writes the short contract, adds worked examples, and adds a rule-lint gate. The order matters: the rules must describe the paths and gates that exist after Part I, not the old ones.

**Tech Stack:** Python 3.12 standard library, Node 22, Playwright 1.65.0, GitHub Actions, Vercel CLI 65.14.0, JDK 17 and Android platform 35 for the visual/APK jobs, and Markdown for the rules.

**Spec:** Current `AGENTS.md`; the 12 November 2026 incident and rules audit below.

**Revision:** 12 November 2026. Audited against the current tree and corrected the receipt source, APK digest source, SEO/render interaction, handoff job isolation, gate counts and line-number references.

## Context

### Publish reliability

The 12 November incident was not a failing test. It was a missing publish step:

- `ae3f1a5` contained the new `/icons` page, but `docs/` was never deployed. Production served the old page while every local gate was green.
- The public gallery still contained pre-3.9 captures after the page changed.
- PR #1 was merged before the follow-up `1b6b911`, so the gallery refresh is on the branch but not in `main`.
- The repository's default branch is the feature branch, not `main`.
- The handoff source is under ignored `artifacts/`, so a GitHub Actions runner cannot deploy it.
- `deploy_vercel.py` verifies status and content type, not bytes.
- Both Vercel deploy tools use `vercel@latest`, so a CLI release can change deploy behavior without a repository change.

### Agent rules

The rules are not merely long. They are contradictory and partly obsolete:

- `AGENTS.md` is 823 lines, but most of it is project-internals documentation, not operating policy.
- It still says the gallery is dark and `RENDERS_THEME=light` produces another mode, while the current site is one edition with no light/dark switch.
- It refers to “all eight themes” and “the four palettes.”
- It points the handoff at `artifacts/handoff/`, while Part I moves it to tracked `handoff/`.
- Its “wait for a verdict” rule reads as if it applies to every change, including code and copy, rather than visual UI changes.
- The public-link rules are repeated three times with slightly different wording.
- There is no lint against removed commands or paths, so every refactor silently makes the rules less trustworthy.
- There is no skills policy: the agent can either ignore an applicable skill or load one as if it were permission to expand scope.

### Automation inventory

| Area | Already automated | Gap Part I closes | Priority |
|---|---|---|---|
| Site publish | Manual `deploy_vercel.py` | Publish on `main` after checks; no token on PRs | P0 |
| Live verification | Status and content type only | SHA-256 byte parity plus commit receipt | P0 |
| Gallery | Manual `render_pages.js` | Source-hash manifest and a Python-only freshness gate | P1 |
| Artboards | Opt-in `check_artboards.py` | Required in visual CI and release preflight | P1 |
| Handoff | Manual source in ignored `artifacts/` | Tracked source, generated provenance, CI deploy | P1 |
| Branch state | None | Merged-branch guard; `main` default; auto-delete | P1 |
| Visual toolchain | Per-run install in CI and `/tmp` locally | Pinned image; pinned Vercel CLI | P2 |
| Live drift | None | Daily read-only parity run | P2 |
| SEO metadata | Titles, descriptions, H1s, alt text, sitemap, robots | Canonical, social preview, homepage schema and one-off audit | P2 |
| APK release | Local build and sign; no manifest/privacy gate | APK manifest, signature and backup/exported-component check | P2 |
| PR review | Manual `deploy_review.py review` | Automatic public review URL per same-repo PR | P2 |
| Drive refresh | Manual integration | Keep manual/OAuth-driven | Deferred |
| Physical device | x86_64 smoke test | Device farm or human verdict | Deferred |

### Rule audit

| Current block | Problem | Target |
|---|---|---|
| Delivery rule / “learned the hard way” / phone delivery | Three overlapping versions of one rule | One delivery matrix plus one link rule |
| Render mode and `RENDERS_THEME` | Contradicts the one-edition site | Remove the old mode language |
| Eight themes / four palettes | Historical app and site states | Reference only, corrected to current edition |
| Handoff under `artifacts/` | Ignored source cannot be deployed by CI | `handoff/` after Task 5 |
| “Wait for a verdict” | Ambiguous scope and blocks non-visual work | Visual UI only; code/docs follow checks |
| Project shape (321–752) | Valuable detail, wrong document | `docs/agent-reference.md` |
| Local render setup | Detailed environment instructions | Reference, with commands kept executable |
| No skills policy | Ignores applicable methods or expands scope | Smallest matching skill, read in full; repo wins |

## Global Constraints

- Deploy only from a clean checkout of `main`; never from a PR, fork, tag or feature branch.
- Never deploy unless `shelf` and `visual` both pass on the same commit.
- Live parity must compare SHA-256 bytes, not just HTTP status or content type.
- Never commit `docs/_deploy.json`; it is generated per deploy and belongs in `.gitignore`.
- No secret value in the repository, logs, handoff, command arguments or chat. Use GitHub Actions secrets and Vercel project-scoped tokens.
- Do not automate deletion of Vercel deployments. The existing audit treats deletions as evidence requiring a recorded reason.
- Keep every existing gate. Add gates; do not weaken or skip one to make the deploy green. The count grows from 14 to 18 once `renders`, `artboards`, `seo` and `rules` are enabled.
- Use `vercel@65.14.0` in every tool; no `@latest`.
- Keep Python stdlib for the new checkers and Node/Playwright for rendering. Do not add a service or dependency.
- Do not change page copy or UI in Part I. Part II changes rules, not product behavior.
- Every task ends with a focused test and a commit.
- `AGENTS.md` must stay at or below 260 lines. If a rule needs a long explanation, put the explanation in `docs/agent-reference.md` and link it.
- `docs/agent-reference.md` may be long, but every path and command in it must resolve.
- `CLAUDE.md` contains no duplicate rules; it points to `AGENTS.md`.
- Preserve every still-valid technical command and warning. Moving a section is allowed; deleting a hard-won operational fact is not.
- Do not encode a commit SHA, deployment ID, date-sensitive count or branch name as a permanent rule.
- The public-link and phone-width rules remain mandatory for visual deliveries and build handoffs.
- Visual UI changes still require a public review before merge. Non-visual code, copy, metadata and documentation changes require the relevant checks, not a design verdict.
- Skills are methods, not scope expansion. Use the smallest skill that matches the task, read it fully before acting, and keep `AGENTS.md` and the repository checks authoritative when generic advice conflicts.
- No new runtime dependency. The rule linter uses the Python standard library.

## Review Focus

These are the failure modes most likely to make the automation or the rules look correct while the project is wrong:

1. **CDN or alias propagation:** a parity check reads an old edge copy immediately after deploy. Use a cache-busting query, `Cache-Control: no-cache`, a bounded retry, and report the deployment URL on failure.
2. **Receipt self-reference:** `_deploy.json` cannot be compared to a pre-deploy hash of itself. Exclude it from the generic file set and validate its `commit` field separately.
3. **CI scope error:** a `pull_request` or fork run must never receive a Vercel token or reach the publish job. Guard on `github.event_name == 'push'` and `github.ref == 'refs/heads/main'`.
4. **Stale gallery misses CSS changes:** hashing only `index.html` misses a changed shared stylesheet. Include the current `docs/data/assets.json` mapping and the referenced `site.<hash>.css`/`.js` bytes in each source hash.
5. **Handoff relocation breakage:** moving the handoff out of ignored `artifacts/` can break relative fonts/images and the storage target. Copy the whole directory, update the target, and assert local assets return 200 after deploy.
6. **Merged-branch false positive:** a branch whose PR merged and whose HEAD is already contained in `main` is healthy. Fail only when the merged branch has a commit not reachable from `main`.
7. **False confidence from shorter rules:** a policy sentence loses a command or warning that made the old rule work. Preserve the actual commands and test for them.
8. **Lint false positives:** backticks contain shell commands, URLs, globs, placeholders and generated paths. Classify tokens before checking existence.
9. **Path drift after Part I:** `artifacts/handoff` becomes `handoff/`, `check_all` gains gates, and the default branch changes. Update both rule documents in the same commit as the path change.
10. **Ambiguous “visual”:** copy inside a screenshot, metadata-only changes and generated pages are easy to misclassify. The delivery matrix must name examples for each row.
11. **Two sources of truth:** a command or path repeated in both `AGENTS.md` and the reference can drift. Keep policy in `AGENTS.md`, mechanics in the reference, and make the linter check both.
12. **Review rule overreach:** turning every code change into a design review blocks delivery. Limit the review gate to changes that alter what a reader sees.
13. **Skill cargo-culting:** loading several skills for a routine edit, citing a skill without reading it, or treating a skill as permission to add scope. The policy must require a matching workflow, the whole skill, and repo precedence.

### Review findings from this audit

- **Live drift needs a live receipt.** `docs/_deploy.json` is intentionally ignored, so a fresh checkout on `main` has none. `check_live.py` must support `--receipt live` and fetch `<origin>/_deploy.json`; a daily job must not depend on a local generated file.
- **The APK digest is generated.** `tools/catalog.json` is the source and has no `sha256`/`bytes`; `docs/data/tools.json` and `docs/data/index.json` carry the measured values. Use the generated data for parity and handoff, and keep the version/package lookup in the source catalog.
- **SEO changes invalidate renders.** The render manifest hashes page bytes. Adding canonical/social metadata or homepage JSON-LD changes those bytes, so Tasks 10 and 11 must re-render every affected page and pass `check_renders.py --check` before committing.
- **Handoff must not be a publish step.** A failed handoff step would fail the publish job after the site is already live. Run handoff as its own job with `needs: publish`; the site remains published and the workflow reports the handoff failure separately.
- **Gate counts move.** The visual verdict starts with 14 gates; `renders` and `artboards` take it to 16, `seo` to 17, and `rules` to 18. Keep every existing gate; do not describe the new total as the old count.
- **Rules reference by section, not line.** `AGENTS.md` line numbers move when handoff and publish paths change. Task 15 should name sections and search strings instead of `165–752`.

---

## Part I — Publish and verification

### Task 0: Repair the current branch state and repository settings

**Files:**
- No source files.
- GitHub repository settings.

**Interfaces:**
- Produces: `origin/main` contains the published site and gallery, default branch is `main`, merged head branches are deleted.

- [ ] **Step 1: Bring `1b6b911` into `main` before adding a guard that rejects it**

  Open a follow-up PR from `freebuff/8e2249648ff2a1234d12caa7` to `main` containing only `1b6b911`, or cherry-pick that commit onto a fresh branch from `main`. Merge it before enabling the branch guard.

  Verify:

  ```bash
  git fetch origin
  git merge-base --is-ancestor 1b6b911 origin/main
  echo $?   # 0
  ```

- [ ] **Step 2: Set the default branch to `main`**

  Repository Settings → General → Default branch → `main`.

  Verify:

  ```bash
  gh api repos/GBS-2805/ADD --jq .default_branch
  # main
  ```

- [ ] **Step 3: Turn on automatic head-branch deletion**

  Repository Settings → General → “Automatically delete head branches”.

  This prevents the next post-merge commit from landing on an already-merged branch by default.

- [ ] **Step 4: Protect `main`**

  Require a pull request, require the existing `checks / shelf` and `checks / visual` jobs, disallow force pushes, and disallow direct pushes. Keep the existing merge method.

- [ ] **Step 5: Record the settings in `DEPLOYMENT_STORAGE.md`**

  Add a short “Repository settings” section naming the default branch, auto-delete and required checks. Do not put tokens in it.

- [ ] **Step 6: Commit**

  ```bash
  git add DEPLOYMENT_STORAGE.md
  git commit -m "Record the repository settings that keep main deployable"
  ```

---

### Task 1: Generate a deploy receipt and verify live bytes

**Files:**
- Create: `tools/check_live.py`
- Create: `tools/tests/test_check_live.py`
- Modify: `tools/deploy_vercel.py`
- Modify: `.gitignore`
- Modify: `tools/check_all.py` only if the new self-tests are added to the suite.

**Interfaces:**
- Consumes: the existing `site_routes()` and deploy URL parsing in `tools/deploy_vercel.py`.
- Produces: `write_receipt(commit: str, project: str) -> Path`, `check(origin: str, docs: Path, expect_commit: str, receipt: str = "local") -> list[str]`, and a CLI `python3 tools/check_live.py --origin URL --docs docs --expect-commit SHA [--receipt local|live|PATH]`.
- Produces: `docs/_deploy.json` with `{schema, commit, deployedAt, project}`. The origin is a CLI input, not part of the receipt, so a first deploy cannot write the wrong host into an uploaded file.

- [ ] **Step 1: Write the failing parity tests**

  Use `unittest`, `tempfile`, and `http.server`. Cover:

  ```python
  def test_identical_tree_passes(): ...
  def test_changed_html_fails_with_the_file_name(): ...
  def test_missing_local_receipt_fails(): ...
  def test_receipt_commit_mismatch_fails(): ...
  def test_live_receipt_is_fetched_and_must_match_head(): ...
  def test_missing_live_receipt_fails(): ...
  def test_receipt_is_not_hashed_as_a_page(): ...
  ```

  Run:

  ```bash
  python3 -m unittest tools.tests.test_check_live -v
  ```

  Expected: import failure because `check_live` does not exist yet.

- [ ] **Step 2: Implement `tools/check_live.py`**

  Requirements:

  - Read every `*.html` under `docs/`, every `renders/*.webp`, `data/assets.json`, `data/tools.json`, the two hashed `assets/site.*` files, the Figma and Pantone SVGs, and the catalog APK. Include `renders/manifest.json` and `assets/og.png` when they exist; before Tasks 3 and 10 create them, print `optional: missing` and continue rather than failing on a future artifact. Skip files larger than 2 MiB except the APK; compare the APK by the generated `docs/data/tools.json` `sha256` and `bytes`, not by the source `tools/catalog.json`.
  - Exclude `docs/_deploy.json` from the generic hash set.
  - Resolve the receipt before hashing: `local` reads `docs/_deploy.json`; `live` fetches `<origin>/_deploy.json`; a path reads that file. Compare its `commit` with `--expect-commit` and fail with both values when they differ.
  - Fetch with `?deploy=<expect_commit>` and `Cache-Control: no-cache`.
  - Retry 6 times with a 5-second delay for HTML; do not retry a content mismatch more than the bounded window.
  - Use a thread pool of 8 for independent files.
  - Print `live: N files match <origin> at <commit>` on success; print each mismatch as `path: local <sha> != live <sha>`.
  - Exit 1 on any mismatch, missing receipt, or commit mismatch.

- [ ] **Step 3: Add the receipt writer to `deploy_vercel.py`**

  Before the upload, write `docs/_deploy.json` with the current `git rev-parse HEAD`, UTC timestamp and project. Do not put the per-deployment URL in the receipt; `check_live` receives the origin as an argument. The file must be written after the palette/link checks and before `vercel deploy`.

  After the existing `verify(new)` call, run `check_live.check(new, ROOT / "docs", commit, receipt="local")`. If parity fails, print the deployment URL and exit non-zero; do not rewrite links.

- [ ] **Step 4: Exclude the receipt from git**

  Add:

  ```gitignore
  # Generated by tools/deploy_vercel.py; the live parity check reads it.
  docs/_deploy.json
  ```

- [ ] **Step 5: Verify against a local fixture, not production**

  The current live host has no `_deploy.json` yet, so a production run here would fail on the missing receipt, not on a byte mismatch. Test the complete path against a local static server instead:

  ```bash
  python3 - <<'PY'
  import json
  from pathlib import Path
  head = __import__('subprocess').check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
  Path('docs/_deploy.json').write_text(json.dumps({
      'schema': 1, 'commit': head, 'deployedAt': '2026-11-12T00:00:00Z', 'project': 'artbase'
  }, indent=2) + '\n')
  PY
  python3 -m http.server 8794 --directory docs >/tmp/parity-server.log 2>&1 &
  python3 tools/check_live.py --origin http://127.0.0.1:8794 \
    --docs docs --expect-commit "$(git rev-parse HEAD)"
  ```

  Expected: all checked files match. Stop the server. Run against production only after Task 2 has published the first receipt.

- [ ] **Step 6: Run the new tests**

  ```bash
  python3 -m unittest tools.tests.test_check_live -v
  ```

  Expected: 7 tests pass.

- [ ] **Step 7: Commit**

  ```bash
  git add .gitignore tools/check_live.py tools/tests/test_check_live.py tools/deploy_vercel.py
  git commit -m "Refuse a deploy whose live bytes do not match the tree"
  ```

---

### Task 2: Publish from `main` only after both check jobs pass

**Files:**
- Modify: `.github/workflows/checks.yml`
- Create: `tools/tests/test_workflow_contract.py` (static contract test)

**Interfaces:**
- Consumes: Task 1’s `deploy_vercel.py` and `docs/_deploy.json`.
- Produces: a `publish` job that runs on `push` to `main`, needs `shelf` and `visual`, deploys once, uploads the receipt, and writes a job summary.

- [ ] **Step 1: Write the failing workflow contract test**

  The test reads `.github/workflows/checks.yml` as text and asserts:

  - the publish job has `needs: [shelf, visual]`;
  - it is conditional on `github.event_name == 'push'`;
  - it is conditional on `github.ref == 'refs/heads/main'`;
  - it uses the `production` environment;
  - it has `concurrency` with `cancel-in-progress: false`;
  - it does not appear under `pull_request` permissions with a Vercel secret.

  Run it and confirm it fails because the job is absent.

- [ ] **Step 2: Add the publish job**

  Add to `checks.yml`:

  ```yaml
  publish:
    needs: [shelf, visual]
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-24.04
    environment: production
    timeout-minutes: 15
    concurrency:
      group: publish-production
      cancel-in-progress: false
  ```

  Steps:

  1. Checkout with `fetch-depth: 0`.
  2. Set up Python 3.12 and Node 22.
  3. Require `VERCEL_ARTBASE_TOKEN`; the deploy tool falls back to `VERCEL_TOKEN`.
  4. Run `python3 tools/deploy_vercel.py`.
  5. Upload `docs/_deploy.json` as `deploy-receipt-${{ github.sha }}`.
  6. Append the commit, live origin and parity result to `$GITHUB_STEP_SUMMARY`.

  Use only the production environment secrets. Do not put the token in `env` for the whole workflow; scope it to the publish step.

- [ ] **Step 3: Add the self-test command to the shelf job**

  Add one step after the existing Python verdict:

  ```bash
  python3 -m unittest discover -s tools/tests -v
  ```

  This makes the parity and workflow contract tests part of the required check.

- [ ] **Step 4: Test the workflow locally without deploying**

  ```bash
  python3 tools/deploy_vercel.py --check
  python3 -m unittest discover -s tools/tests -v
  ```

  Expected: the plan prints, the token check reports whether it is configured, and the contract test passes.

- [ ] **Step 5: Trigger one real publish**

  After this merges to `main`, use the GitHub Actions run page to confirm:

  - `shelf` and `visual` pass first;
  - `publish` runs once;
  - `docs/_deploy.json` exists in the artifact;
  - live `/icons/` and `/renders/` match the local bytes;
  - the job summary names the deployed commit.

  If the workflow cannot be triggered without a new commit, use a documentation-only commit on `main`; do not bypass the job.

- [ ] **Step 6: Commit**

  ```bash
  git add .github/workflows/checks.yml tools/tests/test_workflow_contract.py
  git commit -m "Deploy main only after the full check verdict"
  ```

---

### Task 3: Fail when the public gallery is stale

**Files:**
- Modify: `tools/render_pages.js`
- Create: `tools/check_renders.py`
- Create: `tools/tests/test_check_renders.py`
- Modify: `tools/check_all.py`
- Regenerate: `docs/renders/manifest.json` and `docs/renders/*.webp` once.

**Interfaces:**
- `render_pages.js` produces `docs/renders/manifest.json`:
  ```json
  {
    "schema": 1,
    "shots": {
      "page-icons": {
        "page": "icons/",
        "width": 1300,
        "full": true,
        "source": "<sha256>",
        "image": "page-icons.webp",
        "imageSha": "<sha256>"
      }
    }
  }
  ```
- `check_renders.py --check` returns non-zero when any source hash or image hash is stale.

- [ ] **Step 1: Write the failing checker tests**

  Cover:

  - current manifest passes;
  - changing the page HTML while keeping the manifest fails;
  - changing `docs/data/assets.json` fails;
  - deleting a listed WebP fails;
  - an orphan WebP not listed in the manifest fails;
  - `--check` never writes.

- [ ] **Step 2: Implement the source hash**

  Add a helper in `render_pages.js`:

  ```js
  function sourceHash(pagePath) {
    // page bytes + docs/data/assets.json + the referenced site.<hash>.css/js bytes
  }
  ```

  Use `crypto.createHash("sha256")`. The result must be deterministic across runs and independent of screenshot timestamps.

- [ ] **Step 3: Write the manifest after WebP conversion**

  After `to_webp.py` succeeds, write the manifest and the gallery in the same run. Include every shot, including extras, and hash the final `.webp` files. Do not include a run timestamp.

- [ ] **Step 4: Add the gate to `check_all.py`**

  Insert `renders` after `links`:

  ```python
  ["renders", [sys.executable, "tools/check_renders.py", "--check"]]
  ```

  Add `"renders": "renders:"` to `VERDICT`. The default Python-only check now catches a stale gallery without a browser.

- [ ] **Step 5: Regenerate and inspect**

  ```bash
  node tools/render_pages.js http://127.0.0.1:8792/ artifacts docs/renders
  python3 tools/check_renders.py --check
  ```

  Run the local static server over `docs/` on port 8792 first. Confirm the new `/icons` desktop and phone captures are in the manifest and visually match the live page.

- [ ] **Step 6: Prove the gate fails**

  Temporarily append one character to `docs/icons/index.html`; run `python3 tools/check_renders.py --check` and confirm it names `page-icons` and `phone-icons`. Revert the character.

- [ ] **Step 7: Commit**

  ```bash
  git add tools/render_pages.js tools/check_renders.py tools/tests/test_check_renders.py tools/check_all.py docs/renders
  git commit -m "Fail the checks when published captures go stale"
  ```

---

### Task 4: Make artboards a required check and refresh the current boards

**Files:**
- Modify: `.github/workflows/checks.yml`
- Modify: `tools/bump_version.py:569`
- Regenerate: `figma/*.svg`, `docs/figma/*`, `figma/site-figma.svg`.

**Interfaces:**
- Consumes: `tools/check_artboards.py`, which already re-exports into a scratch directory and compares bytes.
- Produces: every visual CI run and every release bump fails on a stale board.

- [ ] **Step 1: Refresh the current boards from the deployed pages**

  Start the local server, export, bundle and publish:

  ```bash
  python3 -m http.server 8793 --directory docs &
  node tools/figma_export.js http://127.0.0.1:8793/ figma
  python3 tools/figma_bundle.py
  python3 tools/figma_publish.py
  python3 tools/check_artboards.py
  ```

  Use the render environment from `tools/render_env.py`. Stop the server after the export.

- [ ] **Step 2: Add the artboard gate to the visual CI job**

  Add a step after the existing `check_all --visual` step or change the run to:

  ```bash
  python3 tools/check_all.py --visual --artboards
  ```

  The existing CI installs Playwright, Chromium and the Android platform, so this needs no new dependency.

- [ ] **Step 3: Add the same flag to the release preflight**

  Change the `bump_version.py` preflight from `check_all.py --visual` to `check_all.py --visual --artboards`. Update its docstring and any dry-run text that names the verdict.

- [ ] **Step 4: Verify**

  ```bash
  python3 tools/check_all.py --visual --artboards
  ```

  Expected: all 16 gates green: the 14-gate `--visual` baseline, plus `artboards` and `renders` from Task 3. Confirm `figma/` and `docs/figma/` are current.

- [ ] **Step 5: Commit**

  ```bash
  git add .github/workflows/checks.yml tools/bump_version.py figma docs/figma
  git commit -m "Check artboards on every publish and release"
  ```

---

### Task 5: Track the handoff and generate its provenance

**Files:**
- Move: `artifacts/handoff/` → `handoff/`
- Modify: `tools/deploy_review.py`
- Create: `tools/update_handoff.py`
- Create: `tools/tests/test_update_handoff.py`
- Modify: `AGENTS.md` handoff path and “Handoff standard”

**Interfaces:**
- `TARGETS["handoff"]` becomes `("handoff", "artbase-handoff", "VERCEL_HANDOFF_TOKEN")`.
- `update_handoff.py --commit SHA --deployed-at ISO [--origin URL] --check` replaces only the block between:
  ```html
  <!-- provenance:start -->
  <!-- provenance:end -->
  ```
- Produces: a tracked handoff whose visible provenance block names the deployed commit, time, live URL, APK hash and gate state. `--origin` defaults to `tools/site.json["url"]`.
- Produces: a separate `handoff` workflow job, so a handoff failure cannot fail or roll back the already-published site.

- [ ] **Step 1: Move the whole handoff directory**

  Copy `artifacts/handoff` to `handoff` including `fonts/`, `motion.js`, local PNGs and `vercel.json`. Do not copy only `index.html`.

  Remove the ignored copy after the tracked copy is verified.

- [ ] **Step 2: Add the provenance markers and script**

  Put the markers around the existing “Committed”/“Updated” area in `handoff/index.html`. `update_handoff.py` must replace only that block, escape HTML, and refuse to run when a marker is missing.

  The script reads the APK hash and size from the generated `docs/data/tools.json` (the source `tools/catalog.json` has no measured digest), and the live URL from `tools/site.json["url"]`; it does not invent either. If the APK and the generated data disagree, fail rather than publishing a stale digest.

- [ ] **Step 3: Update the deploy target**

  Change `tools/deploy_review.py`’s `handoff` source to `handoff`. Keep the storage gate. Update `AGENTS.md` paths and any `artifacts/handoff` references.

- [ ] **Step 4: Deploy the handoff as its own job**

  Add a separate `handoff` job to the publish workflow, not another step in `publish`:

  ```yaml
  handoff:
    needs: publish
    runs-on: ubuntu-24.04
    environment: production
  ```

  In that job, check out the published commit and run:

  ```bash
  python3 tools/update_handoff.py \
    --commit "$GITHUB_SHA" \
    --deployed-at "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  python3 tools/deploy_review.py handoff
  ```

  Require `VERCEL_HANDOFF_TOKEN` only in this job. Because it is a separate job, a handoff failure leaves the `publish` job green and the site live, while the workflow is red and the handoff job names the failure. Do not put the handoff step back into `publish`, and do not hide the failure behind a green summary.

- [ ] **Step 5: Test the replacement and the public page**

  ```bash
  python3 -m unittest tools.tests.test_update_handoff -v
  python3 tools/update_handoff.py --check --commit deadbeef --deployed-at 2026-11-12T00:00:00Z
  ```

  After deploy, confirm the handoff returns 200, contains the deployed SHA, and every local image/font it references returns 200. Also confirm the job summary says `handoff: ok` only when the page and its local assets passed.

- [ ] **Step 6: Commit**

  ```bash
  git add handoff tools/deploy_review.py tools/update_handoff.py tools/tests/test_update_handoff.py AGENTS.md
  git commit -m "Deploy the handoff from tracked source and generated provenance"
  ```

---

### Task 6: Refuse new commits on a branch whose PR is already merged

**Files:**
- Create: `tools/check_branch_state.py`
- Create: `.github/workflows/branch-guard.yml`
- Create: `tools/tests/test_branch_state.py`

**Interfaces:**
- `check_branch_state.py --repo GBS-2805/ADD --branch NAME --base main --token-env GITHUB_TOKEN` exits 1 when a merged PR exists for `NAME` and `HEAD` is not reachable from `origin/main`.
- The workflow runs on `push` to any branch except `main`, with `contents: read` and `pull-requests: read`.

- [ ] **Step 1: Write the failing state tests**

  Use a temporary local git repository and a fake API response. Cover:

  - merged PR and HEAD contained in base → pass;
  - merged PR and HEAD one commit ahead → fail with “create a new branch from main”;
  - open PR → pass;
  - no PR → pass;
  - missing `GITHUB_TOKEN` on a private repo → fail with an actionable message.

- [ ] **Step 2: Implement the guard**

  Query `/repos/{owner}/{repo}/pulls?head={owner}:{branch}&state=all&per_page=100`. Find a PR with `merged_at`. If none, pass. If one exists, run `git merge-base --is-ancestor HEAD origin/main`; fail when it is not an ancestor.

  The script must not push, close, merge or delete anything.

- [ ] **Step 3: Add the workflow**

  Trigger on `push` to branches other than `main`; checkout with `fetch-depth: 0`; run the guard with `GITHUB_TOKEN`. Do not add a Vercel secret.

- [ ] **Step 4: Verify against the real branch**

  Run locally with a token:

  ```bash
  python3 tools/check_branch_state.py \
    --repo GBS-2805/ADD \
    --branch freebuff/8e2249648ff2a1234d12caa7 \
    --base main \
    --token-env GITHUB_TOKEN
  ```

  Expected before Task 0’s follow-up merge: fail, naming `1b6b911`. Expected after the follow-up is in `main`: pass.

- [ ] **Step 5: Commit**

  ```bash
  git add tools/check_branch_state.py tools/tests/test_branch_state.py .github/workflows/branch-guard.yml
  git commit -m "Block new commits on an already-merged branch"
  ```

---

### Task 7: Pin the deploy CLI and add scheduled live drift detection

**Files:**
- Modify: `tools/deploy_vercel.py`, `tools/deploy_review.py`, `tools/deploy_audit.py`
- Create: `.github/workflows/live-drift.yml`
- Modify: `tools/render_env.py` only if a missing-environment message is needed.

**Interfaces:**
- All tools use `vercel@65.14.0`.
- `live-drift.yml` runs daily at 06:47 UTC and on `workflow_dispatch`, checks out `main`, runs `check_live.py --receipt live` against the public origin, and fails when the live receipt or live bytes differ from `HEAD`.

- [ ] **Step 1: Pin the CLI**

  Replace every `vercel@latest` with `vercel@65.14.0`. Verify:

  ```bash
  rg -n "vercel@latest" tools || true
  ```

  Expected: no output.

- [ ] **Step 2: Add the drift workflow**

  Use Python 3.12 and no secrets. Run:

  ```bash
  ORIGIN="$(python3 -c 'import json; print(json.load(open("tools/site.json"))["url"])')"
  python3 tools/check_live.py \
    --origin "$ORIGIN" \
    --docs docs \
    --expect-commit "$(git rev-parse HEAD)" \
    --receipt live
  ```

  The checkout has no `docs/_deploy.json`; `--receipt live` is required. The workflow must be read-only. It should not deploy or open an issue; GitHub’s failed-run notification is the alert.

- [ ] **Step 3: Test locally**

  ```bash
  python3 tools/check_live.py --help
  ORIGIN="$(python3 -c 'import json; print(json.load(open("tools/site.json"))["url"])')"
  python3 tools/check_live.py --origin "$ORIGIN" --docs docs --expect-commit "$(git rev-parse HEAD)" --receipt live
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add tools/deploy_vercel.py tools/deploy_review.py tools/deploy_audit.py .github/workflows/live-drift.yml
  git commit -m "Pin the deploy CLI and watch live bytes daily"
  ```

---

### Task 8: Make the visual check environment reproducible

**Files:**
- Create: `tools/check-image/Dockerfile`
- Create: `tools/check-image/run.sh`
- Create: `tools/check-image/README.md`
- Create: `.github/workflows/check-image.yml`

**Interfaces:**
- Produces: an image that runs `python3 tools/check_all.py --visual --artboards` with no host setup.
- Does not replace the current CI visual job until the image has passed three consecutive runs.

- [ ] **Step 1: Pin the image contents**

  Base on `ubuntu:24.04`. Install Python 3.12, Node 22, Temurin JDK 17, Android platform 35 and build-tools 35, `fonts-dejavu-core`, the Chromium runtime libraries, Playwright 1.65.0 at `/opt/pw`, and the Chromium browser at `/ms-playwright`.

  Set `PLAYWRIGHT_BROWSERS_PATH=/ms-playwright`, `NODE_PATH=/opt/pw/node_modules`, `ANDROID_SDK_ROOT` and `ANDROID_HOME` in the image.

- [ ] **Step 2: Add the entrypoint**

  `run.sh` accepts a workspace path, mounts it at `/work`, and runs:

  ```bash
  python3 tools/check_all.py --visual --artboards
  ```

  It must fail on the verdict’s exit status, not print a green summary after a failure.

- [ ] **Step 3: Build and publish the image**

  Add `check-image.yml` on `workflow_dispatch` and on changes to `tools/check-image/**`. Build with `docker/build-push-action` and push to GHCR using `GITHUB_TOKEN` with `packages: write`. Do not use a Vercel token in this workflow.

- [ ] **Step 4: Verify on a Docker-capable machine**

  ```bash
  docker build -t add-checks tools/check-image
  docker run --rm -v "$PWD:/work" add-checks
  ```

  Expected: all gates present at that point pass. Record the image digest in the README.

- [ ] **Step 5: Switch CI only after three green runs**

  Keep the existing `ubuntu-24.04` setup as the fallback. Once the image has passed three consecutive visual jobs, change the visual job to use the image and remove the duplicated setup steps.

- [ ] **Step 6: Commit**

  ```bash
  git add tools/check-image .github/workflows/check-image.yml
  git commit -m "Pin the visual check toolchain in one image"
  ```

---

### Task 9: Decide the remaining operational boundaries

**Files:**
- Modify: `DEPLOYMENT_STORAGE.md`
- Modify: `AGENTS.md` only where a manual step is still required.
- Create: `tools/report_storage.py` (or add `--json` to `tools/deploy_storage.py`)

- [ ] **Step 1: Add a dry-run storage report**

  Add `python3 tools/deploy_storage.py --json` (or a small `tools/report_storage.py`) to the publish job summary. Do not delete deployments automatically. The existing daily audit remains the deletion record.

- [ ] **Step 2: Record the manual boundaries**

  Write these in `DEPLOYMENT_STORAGE.md`:

  - APK signing stays local because the keystore is outside the repository; CI can compile and test, but a release APK is still built and signed by the release owner.
  - Drive refresh stays manual or integration-driven because it needs Google OAuth; it happens after the publish verdict.
  - A physical arm64 phone verdict stays manual until a device farm is configured; the x86_64 emulator is a smoke test, not a renderer of record.
  - Vercel deployment deletion is not automated; the audit’s deletion events require a recorded reason.

- [ ] **Step 3: Commit**

  ```bash
  git add DEPLOYMENT_STORAGE.md AGENTS.md tools/report_storage.py
  git commit -m "Record the remaining manual release boundaries"
  ```

---

### Task 10: Add canonical and social metadata to all modern pages

**Files:**
- Create: `tools/seo_meta.py`
- Create: `tools/tests/test_seo_meta.py`
- Create: `tools/og_image.js`
- Create: `docs/assets/og.png`
- Modify: the 16 modern `docs/**/index.html` pages
- Modify: generated-page sources so regeneration preserves the block: `tools/icons_hub_template.html`, `tools/icons_brand_template.html`, `tools/icons_all_template.html`, `tools/releases_template.html`, `tools/render_pages.js`, `tools/figma_publish.py`, `tools/build_site.py`
- Modify: `tools/check_all.py`

**Interfaces:**
- `seo_meta.py --write` inserts or replaces one marked SEO block in every modern page.
- `seo_meta.py --check` fails when a page lacks or disagrees with canonical, Open Graph or Twitter metadata.
- `seo_meta.py --page PATH --origin URL` returns the expected values for a page; the writer and checker share it.

- [ ] **Step 1: Write the failing metadata tests**

  Cover a temporary page with a title and description, then assert:

  ```python
  def test_check_rejects_a_missing_canonical(): ...
  def test_write_inserts_the_marked_block(): ...
  def test_check_rejects_a_wrong_origin(): ...
  def test_modern_pages_are_in_scope_and_classic_is_not(): ...
  def test_og_and_twitter_fields_match_the_page(): ...
  ```

  Run `python3 -m unittest tools.tests.test_seo_meta -v`; expect failure because the tool does not exist.

- [ ] **Step 2: Implement the marked block**

  Insert this block before `</head>`, replacing an existing block with the same markers:

  ```html
  <!-- seo:start -->
  <link rel="canonical" href="https://artbase-olive.vercel.app/icons/" />
  <meta property="og:type" content="website" />
  <meta property="og:site_name" content="ADD" />
  <meta property="og:title" content="Icons — 4,373 icons in four free brands, offline" />
  <meta property="og:description" content="The Icons app and its browser..." />
  <meta property="og:url" content="https://artbase-olive.vercel.app/icons/" />
  <meta property="og:image" content="https://artbase-olive.vercel.app/assets/og.png" />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="630" />
  <meta property="og:image:alt" content="ADD — a free design shelf" />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="Icons — 4,373 icons in four free brands, offline" />
  <meta name="twitter:description" content="The Icons app and its browser..." />
  <meta name="twitter:image" content="https://artbase-olive.vercel.app/assets/og.png" />
  <!-- seo:end -->
  ```

  Derive the title and description from the existing `<title>` and `meta[name=description]`. Derive the origin from `tools/site.json["url"]`, not a literal host; derive the URL from the page path: root becomes `/`, every other folder ends in `/`. Exclude `docs/classic/` explicitly; those pages are frozen and `noindex`. The HTML above is a shape example, not the source of truth for the origin.

- [ ] **Step 3: Create the social card**

  Add `tools/og_image.js`, a 1200×630 Playwright render using the same paper, ink, plum, wordmark and fonts as the site. It writes `docs/assets/og.png` and prints its dimensions. The card is the ADD wordmark and one line, not a page screenshot; keep it stable so social previews do not churn.

  Verify the PNG signature and `1200x630` dimensions in `test_seo_meta.py` or a small Node assertion.

- [ ] **Step 4: Generate and inspect all 16 pages**

  ```bash
  python3 tools/seo_meta.py --write
  python3 tools/seo_meta.py --check
  ```

  Inspect the diff: only the marked block may appear, and no page copy or layout may move. Inspect `docs/assets/og.png` before committing it.

  Generated pages must carry the block in their generator/template, not only in the checked-in output. Add it to the seven sources listed above, or have each generator run `seo_meta.py --write` as its final step. Prove this by regenerating `fetch_icons.py --pages-only`, `build_site.py`, `build_releases.py`, `render_pages.js` and `figma_publish.py`, then running `seo_meta.py --check`; a regenerated page without the block is a failure.

- [ ] **Step 5: Re-render every page the metadata changed**

  The render manifest hashes page bytes, so adding the SEO block makes every page's captures stale even though the visible layout did not move. Re-render before adding the gate:

  ```bash
  python3 -m http.server 8792 --directory docs >/tmp/seo-server.log 2>&1 &
  node tools/render_pages.js http://127.0.0.1:8792/ artifacts docs/renders
  python3 tools/check_renders.py --check
  ```

  Expected: the manifest updates, every affected WebP is refreshed, and `check_renders` passes. Stop the server.

- [ ] **Step 6: Add the SEO gate to `check_all.py`**

  Add after `links`:

  ```python
  ["seo", [sys.executable, "tools/seo_meta.py", "--check"]]
  ```

  Add `"seo": "seo:"` to `VERDICT`. This is a Python-only gate; it catches missing or stale metadata on every push.

- [ ] **Step 7: Verify**

  ```bash
  python3 tools/check_all.py --visual --artboards
  python3 -m unittest tools.tests.test_seo_meta -v
  ```

  Expected: all 17 gates green, the metadata tests pass, and the render manifest matches the pages after the generator regeneration in Step 4.

- [ ] **Step 8: Commit**

  ```bash
  git add tools/seo_meta.py tools/tests/test_seo_meta.py tools/og_image.js docs/assets/og.png docs tools/check_all.py docs/renders
  git commit -m "Add canonical and social metadata to every modern page"
  ```

---

### Task 11: Add homepage WebSite schema and make the search target real

**Files:**
- Modify: `docs/index.html`
- Modify: `tools/build_site.py` (the search page is generated; editing `docs/search/index.html` alone is overwritten)
- Modify: `tools/seo_meta.py`
- Create: `tools/search-query-check.js`

**Interfaces:**
- Produces one JSON-LD `WebSite` block on the homepage with a `SearchAction` whose target is `<origin>/search/?q={search_term_string}`, where `<origin>` comes from `tools/site.json["url"]`.
- The search page reads `?q=` into `#q` and runs the existing filter on load.

- [ ] **Step 1: Write the failing schema test**

  In `test_seo_meta.py`, extract the JSON-LD blocks from `docs/index.html`, parse them, and assert exactly one block with `@type == "WebSite"`, the correct `url`, and a `potentialAction` target containing `/search/?q={search_term_string}`.

- [ ] **Step 2: Add the schema**

  Add one `application/ld+json` script on the homepage only, containing `@context`, `@type`, `name`, `url`, and the `SearchAction` with `query-input` required. Derive `url` and the target origin from `tools/site.json["url"]`; do not hardcode the host. Do not add schema to every page; the site is one entity and one search action.

- [ ] **Step 3: Read the query parameter**

  In the search page template inside `tools/build_site.py`, read `new URLSearchParams(location.search).get("q")` on load, set `#q.value`, and call the existing `render()` function. Regenerate `docs/search/index.html` with `python3 tools/build_site.py`; do not hand-edit the generated page. If the parameter is absent, behaviour is unchanged.

- [ ] **Step 4: Test the real search URL**

  Add `tools/search-query-check.js` using Playwright. Serve `docs/` locally and open `/search/?q=icons`; assert the result list is non-empty and the heading reflects the query. Run it under the existing render environment.

- [ ] **Step 5: Re-render the changed pages**

  The homepage and search page bytes changed, so their captures are stale:

  ```bash
  python3 -m http.server 8792 --directory docs >/tmp/search-server.log 2>&1 &
  node tools/render_pages.js http://127.0.0.1:8792/ artifacts docs/renders
  python3 tools/check_renders.py --check
  ```

  Expected: the affected captures are refreshed and the manifest matches. Leave the server running for Step 6; stop it after the search check.

- [ ] **Step 6: Verify**

  ```bash
  node tools/search-query-check.js http://127.0.0.1:8792
  python3 -m unittest tools.tests.test_seo_meta -v
  python3 tools/check_all.py --visual --artboards
  ```

  Stop the server after this step.

- [ ] **Step 7: Commit**

  ```bash
  git add docs/index.html docs/search/index.html docs/renders tools/build_site.py tools/seo_meta.py tools/tests/test_seo_meta.py tools/search-query-check.js
  git commit -m "Describe the site and its search action to crawlers"
  ```

---

### Task 12: Verify Search Console and measure performance once

**Files:**
- Create: `SEO.md`
- Modify: `docs/index.html` only when the owner supplies the exact verification token

**Interfaces:**
- Produces a recorded Search Console verification, sitemap submission and one Lighthouse/PageSpeed result for `/` and `/icons/`.
- Does not add a recurring performance gate.

- [ ] **Step 1: Verify the Search Console property**

  The owner verifies the URL-prefix property `https://artbase-olive.vercel.app/` in Google Search Console. Use the HTML tag method: the owner supplies the exact `google-site-verification` token; add it to the homepage head and nowhere else. Never invent, guess or reuse a token.

- [ ] **Step 2: Submit the sitemap**

  Submit `https://artbase-olive.vercel.app/sitemap.xml` in Search Console. Record the submission date and the indexed/not-indexed counts in `SEO.md`.

- [ ] **Step 3: Measure once**

  Run Lighthouse against the live homepage and `/icons/` after the next deploy:

  ```bash
  npx -y lighthouse@12.8.2 https://artbase-olive.vercel.app/ \
    --only-categories=performance,accessibility,best-practices,seo \
    --output=json --output-path=artifacts/lighthouse-home.json \
    --chrome-flags="--headless --no-sandbox"
  ```

  Repeat for `/icons/`. Record the four scores and the page weight in `SEO.md`. Do not add Lighthouse to CI; the site is static and the network-dependent scores would be noisy.

- [ ] **Step 4: Fix only measured regressions**

  If a score is below 90, fix the specific cause (for example an oversized image or a missing label). Do not restyle pages or add speculative optimization.

- [ ] **Step 5: Commit**

  ```bash
  git add SEO.md docs/index.html
  git commit -m "Record search verification and the first performance measurement"
  ```

---

### Task 13: Audit the Android manifest and verify the signed APK

**Files:**
- Modify: `app/AndroidManifest.xml`
- Create: `tools/check_apk.py`
- Create: `tools/tests/test_check_apk.py`
- Modify: `build.sh`
- Modify: `DEPLOYMENT_STORAGE.md` only for the release-owner note.

**Interfaces:**
- `check_apk.py --apk PATH --catalog tools/catalog.json [--published docs/data/tools.json]` returns non-zero on a wrong package, version, SDK range, permission set, exported component, backup setting, signature or published digest. The source catalog supplies package/version; the generated data supplies `sha256`/`bytes`.
- `build.sh` runs the checker after signing, before it prints the finished APK path.

- [ ] **Step 1: Write the failing manifest tests**

  Use fixture XML strings and a tiny fake catalog. Cover:

  ```python
  def test_accepts_the_current_package_and_version(): ...
  def test_rejects_extra_permissions(): ...
  def test_rejects_an_exported_activity_without_an_intent_filter(): ...
  def test_rejects_allow_backup_true(): ...
  def test_rejects_cleartext_traffic(): ...
  def test_rejects_a_published_hash_mismatch(): ...
  ```

- [ ] **Step 2: Decide and enforce the backup policy**

  Set `android:allowBackup="false"` in `app/AndroidManifest.xml` for the offline app. The app’s data is local preferences and downloaded packs; users keep exported images in `Pictures`, and the app already has no account or sync. If the product later wants backup, add explicit `dataExtractionRules`/`fullBackupContent` and change the checker’s expectation in the same commit; never leave the Android default.

  Set `add.ShelfActivity` to `android:exported="false"`. It has no intent filter and is launched explicitly by `MainActivity`; exporting it only widens the surface. Keep `MainActivity` exported for the launcher and the `SEND`/`SEND_MULTIPLE` filters.

- [ ] **Step 3: Implement `check_apk.py`**

  Use the `aapt2` already selected by `build.sh` to dump the manifest; do not parse the binary APK by hand. Check:

  - package `app.offline.compress`;
  - `versionName`/`versionCode` versus `tools/catalog.json`;
  - `minSdkVersion=26`, `targetSdkVersion=35`;
  - permissions exactly `INTERNET` and `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion=28`;
  - no activity with `exported=true` and no intent filter except the launcher/share entry;
  - `allowBackup=false` and `usesCleartextTraffic=false`;
  - `apksigner verify --print-certs` succeeds;
  - SHA-256 and byte size match `docs/data/tools.json` when `--published` is supplied. `tools/catalog.json` has no measured digest; do not pretend it does.

  Run the checker against the existing review APK first, then against a fresh `./build.sh add` output.

- [ ] **Step 4: Wire it into `build.sh`**

  After the signature verification, run the checker with the APK path and `--catalog tools/catalog.json`; omit `--published` for a fresh local build, because `docs/data/tools.json` still describes the previous published APK. A failed manifest or signature check must stop the build before the file is copied into `docs/`. After the APK is copied into `docs/` and `build_catalog.py` refreshes the generated data, verify the published digest with `--published docs/data/tools.json`.

- [ ] **Step 5: Verify**

  ```bash
  python3 -m unittest tools.tests.test_check_apk -v
  ./build.sh add
  python3 tools/build_catalog.py
  python3 tools/check_apk.py --apk docs/ADD-<version>.apk \
    --catalog tools/catalog.json --published docs/data/tools.json
  ```

  Expected: package, version, permissions, exported components, backup flag, cleartext flag, signature and published digest all pass.

- [ ] **Step 6: Commit**

  ```bash
  git add app/AndroidManifest.xml tools/check_apk.py tools/tests/test_check_apk.py build.sh DEPLOYMENT_STORAGE.md
  git commit -m "Verify the APK manifest and stop backing up the offline app"
  ```

---

### Task 14: Publish a review URL for every same-repo PR

**Files:**
- Create: `.github/workflows/review.yml`
- Modify: `tools/deploy_review.py` only if a stable JSON output is needed for the comment.

**Interfaces:**
- Consumes: the existing `deploy_review.py review` target and `artbase-review` project.
- Produces: a PR comment with `https://artbase-review.vercel.app` and the head SHA.

- [ ] **Step 1: Add a same-repo-only review job**

  Trigger on `pull_request`. Require `github.event.pull_request.head.repo.full_name == github.repository`; never run with a Vercel secret on a fork. Use `permissions: contents: read, pull-requests: write`, `environment: review`, and `needs: [shelf, visual]` if the jobs are in the same workflow. If this is a separate workflow, gate on the checks workflow completing successfully.

- [ ] **Step 2: Deploy and comment**

  Run:

  ```bash
  python3 tools/deploy_review.py review
  ```

  Then comment on the PR:

  ```bash
  gh pr comment "$PR_NUMBER" --body "Review build for ${HEAD_SHA}: https://artbase-review.vercel.app"
  ```

  The URL is public by design; do not add a sign-in gate that a phone cannot pass. Do not deploy the APK from a PR.

- [ ] **Step 3: Handle the overwrite explicitly**

  The review project serves one PR at a time. The comment must include the head SHA, and the job must not claim the URL is specific to an older PR. If a second PR deploys, the newest review wins; the old comment stays as history.

- [ ] **Step 4: Test with a real PR**

  Open a documentation-only PR, confirm the comment appears, open the URL on a phone, and verify it serves the PR’s `docs/` commit. Close the PR without merging.

- [ ] **Step 5: Commit**

  ```bash
  git add .github/workflows/review.yml tools/deploy_review.py
  git commit -m "Show every same-repo PR on a public review URL"
  ```

---

## Part II — Agent operating rules

### Task 15: Freeze the target state and preserve today’s reference

**Files:**
- Read: `AGENTS.md`
- Read: Part I of this plan
- Create: `docs/agent-reference.md`

**Interfaces:**
- Produces: a reference document containing the sections named in Step 2, with stale theme/palette claims corrected.
- Produces: one recorded baseline of the current rules before editing.

- [ ] **Step 1: Confirm the Part I dependencies**

  Read Part I and confirm these tasks are either complete or scheduled before Task 15:

  - Task 0: `main` is the default branch and `1b6b911` is in `main`;
  - Task 5: handoff source is tracked at `handoff/`;
  - Task 7: Vercel CLI is pinned;
  - Task 10: SEO metadata is in the generators, if the rules will name the SEO gate.

  If they are not complete, record the dependency in the commit message and keep current paths in the rules until the corresponding task lands.

- [ ] **Step 2: Extract the reference material**

  Copy these `AGENTS.md` sections into `docs/agent-reference.md`, preserving headings and commands:

  - “Artifacts are also kept in Drive”
  - “Local render setup”
  - “The app: native screens render; the emulator is a smoke test”
  - “Project shape”
  - “The address is rented, not owned”

  Leave “Delivery rule”, “Renders”, “Completion notifications” and “Handoff standard” in `AGENTS.md`; those are policy.

- [ ] **Step 3: Correct the known stale claims in the reference**

  - Replace “all eight themes” with the current single edition.
  - Replace “the four palettes declare” with the current edition token set.
  - Remove `RENDERS_THEME=light` and the “dark gallery” language; state that the site has one edition.
  - Replace `artifacts/handoff/index.html` with `handoff/index.html` only after Task 5; otherwise leave the current path and add a `<!-- TODO(handoff-move) -->` marker that the linter will reject once the move lands.
  - Keep the local render commands, but mark the environment as “see `tools/render_env.py`; CI uses the pinned job”.

- [ ] **Step 4: Add a reference header**

  Start the reference with:

  ```markdown
  # ADD — agent reference

  This file holds the mechanics behind `AGENTS.md`: project shape, render setup,
  native-app notes, Drive copies and deployment-host history. It is reference, not
  policy. If this file and `AGENTS.md` disagree, `AGENTS.md` wins.
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add docs/agent-reference.md
  git commit -m "Move the detailed project reference out of the agent rules"
  ```

---

### Task 16: Write the short operating contract

**Files:**
- Modify: `AGENTS.md`
- Create: `CLAUDE.md`

**Interfaces:**
- Produces: an `AGENTS.md` with these required sections in order:
  `Mission and current state`, `Work loop`, `Skills`, `Scope`, `Delivery matrix`, `Verification`, `Publish`, `Visual review`, `Handoff`, `Never`, `Where to look next`.
- Produces: a `CLAUDE.md` containing a pointer to `AGENTS.md` and nothing else.

- [ ] **Step 1: Write the skeleton and fail the shape test**

  Create `tools/tests/test_agent_rules.py` with a test that reads `AGENTS.md` and asserts:

  - it is at most 260 lines;
  - the required headings exist in order;
  - it contains `python3 tools/check_all.py --visual`;
  - it contains `python3 tools/deploy_vercel.py`;
  - it contains `https://artbase-olive.vercel.app/`;
  - it contains `https://artbase-handoff.vercel.app/`;
  - it contains the word `phone` in the visual-review section;
  - it contains the `Skills` heading, `whole matching skill` and `AGENTS.md wins`;
  - `CLAUDE.md` contains `AGENTS.md` and fewer than 8 lines.

  Run it; expect failure because the rewrite has not happened.

- [ ] **Step 2: Write `AGENTS.md`**

  Target structure and content:

  ```markdown
  # ADD — agent operating rules

  ## Mission and current state
  One offline Android app, one design shelf, one 3.9 edition. Link to README,
  this plan and the reference. Do not restate the project history here.

  ## Work loop
  Read the task, the relevant files and the current plan. Batch independent reads
  and edits. Make the smallest complete change. Run the named check. Report exact
  pass/fail output. Commit only when asked or when the plan says so.

  ## Skills
  Before a task with a recognizable workflow -- planning, debugging, TDD, UI or
  design review, deployment, documents -- check the available skills. Read the
  whole matching skill, including its required subskills or resources, before
  acting. Use the smallest relevant skill, not a pile of them. A skill is a method,
  not permission to add scope or dependencies. `AGENTS.md` wins over generic skill
  advice; the repository checks still decide. If they conflict, say so and follow the
  repo. If the host exposes skills only through a tool, use that tool; do not search
  outside the repo for them. Never claim a skill was used when only its description
  was read.

  ## Scope
  Do the request; no unrequested features, pages, dependencies or polish. A
  question gets an answer, not a change. Ask only when a missing fact blocks the
  work.

  ## Delivery matrix
  | Change | Before merge | After approval | Required links |
  |---|---|---|---|
  | code, tests, metadata, copy-only docs | relevant checks; no render | commit/push only when asked | none |
  | visual site page | `check_all --visual`, public review URL, phone capture | captures, artboards, deploy, handoff | live site, gallery, phone capture |
  | APK/review build | build, manifest/signature check, device smoke | handoff, APK hash | handoff, APK |
  | deploy-only | checks green, deploy receipt, live parity | handoff refresh if build changed | live site, handoff |

  ## Verification
  Name the command and read its exit status. Do not claim a check passed from a
  prior run or a screenshot. If a check fails, show the failure and fix the cause.
  The standard verdict is `python3 tools/check_all.py --visual`; add
  `--artboards` for release work.

  ## Publish
  `main` only, after `shelf` and `visual`. `deploy_vercel.py` writes the receipt,
  uploads `docs/`, and checks live bytes. Never deploy from a PR or a dirty tree.
  The live host is https://artbase-olive.vercel.app/.

  ## Visual review
  A visual change needs a public review URL and a phone-width capture before
  merge. Use `deploy_review.py review` and `deploy_review.py shots`; verify 200
  and `image/png`. A workspace path is not a review link.

  ## Handoff
  Every build or review reply includes https://artbase-handoff.vercel.app/.
  Keep the handoff under five phone screens. Say review-only when it is.

  ## Never
  No secrets, no guessed tokens, no force push, no skipped checks, no generated
  file hand-edits, no drawn screenshots, no live deploy from a feature branch.

  ## Where to look next
  - `docs/agent-reference.md` for project shape, render setup and app notes.
  - `docs/superpowers/plans/2026-11-12-checks-and-rules.md` for the publish/checks build.
  - `README.md` for the public product story.
  ```

  Expand each section with only the commands and exceptions needed to remove ambiguity. Keep the required headings.

- [ ] **Step 3: Write `CLAUDE.md`**

  ```markdown
  # Claude entry point

  Read `AGENTS.md`. It is the only operating contract; this file intentionally
  contains no duplicate rules.
  ```

- [ ] **Step 4: Run the shape test**

  ```bash
  python3 -m unittest tools.tests.test_agent_rules -v
  wc -l AGENTS.md CLAUDE.md
  ```

  Expected: pass, and `AGENTS.md` is under 260 lines.

- [ ] **Step 5: Commit**

  ```bash
  git add AGENTS.md CLAUDE.md tools/tests/test_agent_rules.py
  git commit -m "Replace the agent rules with a short operating contract"
  ```

---

### Task 17: Make the delivery matrix executable

**Files:**
- Modify: `AGENTS.md`
- Create: `docs/agent-rules-examples.md`
- Modify: `tools/tests/test_agent_rules.py`

**Interfaces:**
- Produces: one worked example for each delivery row, with the exact commands and links.
- Produces: a test that each row names its verification command and its required links.

- [ ] **Step 1: Add the examples**

  Write five short examples in `docs/agent-rules-examples.md`:

  1. **Copy-only page edit:** change a sentence, run `check_all`, no render, commit only if asked.
  2. **Visual page edit:** run `check_all --visual`, deploy review, capture 1300 + 390, send live/review/gallery/phone links, wait for the verdict, then recapture and deploy.
  3. **APK review:** build, run `check_apk.py`, install on the x86_64 smoke device, publish handoff, report the arm64 phone gap.
  4. **Deploy-only:** confirm the commit is on `main`, deploy, run live parity, refresh handoff only if the APK or status changed.
  5. **Skill-assisted task:** check the available catalog, read the one matching skill in full, apply its required steps, and say which parts were actually run. A one-line copy edit uses no skill.

  Each example states what is **not** required, so the rules do not overreach.

- [ ] **Step 2: Add the matrix test**

  In `test_agent_rules.py`, assert each row contains a command or a named check and a link or an explicit “none”. Fail when a row is added without both.

- [ ] **Step 3: Review against the last three real tasks**

  Read the last three changes in `git log` and check that the matrix would have described them without ambiguity. If not, adjust the row, not the example.

- [ ] **Step 4: Commit**

  ```bash
  git add AGENTS.md docs/agent-rules-examples.md tools/tests/test_agent_rules.py
  git commit -m "Show the agent how each delivery path actually runs"
  ```

---

### Task 18: Add the rule-lint gate

**Files:**
- Create: `tools/check_agent_rules.py`
- Create: `tools/tests/test_check_agent_rules.py`
- Modify: `tools/check_all.py`

**Interfaces:**
- `check_agent_rules.py --check` returns non-zero for a missing required heading, stale path, forbidden legacy phrase, missing command, or an overlong `AGENTS.md`.
- `check_agent_rules.py --explain` prints every token it treats as a path and every ignored token class.
- A `rules` gate in `check_all.py` runs the checker with no browser.

- [ ] **Step 1: Write the failing positive and negative fixtures**

  Tests:

  ```python
  def test_current_rules_pass(): ...
  def test_missing_required_heading_fails(): ...
  def test_old_artifacts_handoff_path_fails(): ...
  def test_old_dark_theme_phrase_fails(): ...
  def test_unknown_local_path_fails(): ...
  def test_url_glob_and_flag_are_not_paths(): ...
  def test_agents_over_260_lines_fails(): ...
  def test_skills_rule_requires_whole_skill_and_precedence(): ...
  def test_claude_must_point_at_agents(): ...
  ```

- [ ] **Step 2: Define token classification**

  Check local paths only when a backticked token:

  - starts with `docs/`, `tools/`, `app/`, `figma/`, `handoff/`, `.github/` or `artifacts/`; and
  - contains no whitespace, `<`, `>`, `*`, `?`, `$`, `(`, `)` or `{`.

  Ignore URLs, shell commands, environment variables, flags, glob patterns, code identifiers and generated names that do not start with a known root. Print ignored classes under `--explain`.

  Check files that are expected to exist at this point. If `handoff/` is not moved yet, the check must name the dependency instead of silently passing.

- [ ] **Step 3: Define the forbidden legacy phrases**

  Start with:

  ```text
  four palettes
  all eight themes
  RENDERS_THEME=light
  the gallery follows the page's own mode, which is dark
  artifacts/handoff/index.html
  vercel@latest
  ```

  Each phrase is allowed only in a changelog or a migration note. The linter fails when it appears in the policy or reference body.

- [ ] **Step 4: Add the gate**

  In `tools/check_all.py`:

  ```python
  ["rules", [sys.executable, "tools/check_agent_rules.py", "--check"]]
  ```

  Add `"rules": "rules:"` to `VERDICT`. Put it after `links` so a rules failure is visible beside the docs/link checks.

- [ ] **Step 5: Verify**

  ```bash
  python3 -m unittest tools.tests.test_check_agent_rules -v
  python3 tools/check_agent_rules.py --check
  python3 tools/check_all.py
  ```

  Expected: all pass on the clean rules; a temporary stale phrase fails with its line number.

- [ ] **Step 6: Commit**

  ```bash
  git add tools/check_agent_rules.py tools/tests/test_check_agent_rules.py tools/check_all.py
  git commit -m "Fail the checks when the agent rules go stale"
  ```

---

### Task 19: Reconcile the project docs and the compiled plan

**Files:**
- Modify: `PLAN.md` (top “Current decision” note only)
- Modify: `README.md` (link the operating contract)
- Modify: `AGENTS.md` (reference links only)

**Interfaces:**
- Produces: one policy source (`AGENTS.md`), one technical reference (`docs/agent-reference.md`) and one compiled plan.
- Produces: no duplicate “current state” paragraph across them, and no link to a superseded source plan.

- [ ] **Step 1: Add the supersession note to `PLAN.md`**

  At the top, beneath “Current decision”, add:

  ```markdown
  > **Operating rules:** `AGENTS.md` is the current delivery, verification and
  > publish contract. This document remains the product/engine roadmap; where an
  > old instruction here conflicts with `AGENTS.md`, `AGENTS.md` wins.
  ```

  Do not rewrite the historical sections.

- [ ] **Step 2: Link the contract from `README.md`**

  Add one line near the top or development section:

  ```markdown
  Agents and contributors: read [`AGENTS.md`](AGENTS.md).
  ```

- [ ] **Step 3: Confirm the compiled plan is authoritative**

  The Checks and Agent Rules plans were merged into this file. The two source files should carry a `Superseded by` banner and no current document should treat them as the plan to execute:

  ```bash
  head -3 docs/superpowers/plans/2026-11-12-checks.md docs/superpowers/plans/2026-11-12-agent-rules.md
  rg -n "2026-11-12-(checks|agent-rules)\.md" README.md PLAN.md AGENTS.md docs/agent-reference.md || true
  ```

  Expected: both source files name `2026-11-12-checks-and-rules.md`; the second command prints nothing.

- [ ] **Step 4: Verify no duplicate policy**

  ```bash
  rg -n "wait for a verdict|four palettes|all eight themes|artifacts/handoff" AGENTS.md docs/agent-reference.md PLAN.md README.md
  ```

  Expected: no matches in policy/reference. Historical mentions in `PLAN.md` are acceptable only under the supersession note.

- [ ] **Step 5: Commit**

  ```bash
  git add PLAN.md README.md AGENTS.md docs/agent-reference.md
  git commit -m "Make the agent contract the single source of delivery policy"
  ```

---

### Task 20: Add a rule-review checklist and change protocol

**Files:**
- Modify: `AGENTS.md` (one short “Changing these rules” section)
- Create: `docs/agent-rules-changelog.md`
- Modify: `tools/tests/test_agent_rules.py`

**Interfaces:**
- Produces: a rule change protocol that requires updating the reference, tests and examples together.
- Produces: a dated changelog with one line per rule change, not a duplicate of the rules.

- [ ] **Step 1: Add the change protocol**

  Add to `AGENTS.md`:

  ```markdown
  ## Changing these rules
  Change the rule, its reference section, its example and its test in one commit.
  Run `python3 tools/check_agent_rules.py --check`. Never edit a generated file or
  add a rule that only restates the system prompt.
  ```

- [ ] **Step 2: Start the changelog**

  ```markdown
  # Agent rules changelog

  - 2026-11-12: replaced the 823-line mixed rules with the operating contract;
    added the skills policy; moved project mechanics to `docs/agent-reference.md`;
    added the rules gate.
  ```

  Future entries name the rule changed and the reason, not the whole diff.

- [ ] **Step 3: Add the test**

  Assert `AGENTS.md` contains the section and `docs/agent-rules-changelog.md` has at least one dated entry. This is a documentation test, not a content-quality claim.

- [ ] **Step 4: Commit**

  ```bash
  git add AGENTS.md docs/agent-rules-changelog.md tools/tests/test_agent_rules.py
  git commit -m "Make changing the agent rules a tested change"
  ```

---

## Rollout order

1. **Task 0 first.** The branch guard and deploy job both assume `main` is the default and contains `1b6b911`.
2. **Tasks 1 and 2 together.** A parity check without a publish job cannot catch the original incident; a publish job without parity can publish the wrong bytes.
3. **Task 3 next.** This is cheap, Python-only, and prevents the stale gallery from recurring on any branch.
4. **Task 4 before the next visual change.** Running `--artboards` in CI while the boards are stale would make every push red; refresh first.
5. **Task 5 after the site publish works.** The handoff is allowed to lag the site by one step, but it must not block the site.
6. **Task 6 only after Task 0's follow-up merge.** Otherwise the first run fails on the known `1b6b911` divergence.
7. **Tasks 7 and 8 are hardening.** They do not block the first green publish.
8. **Task 9 records what stays manual.** Do not hide a manual boundary behind a green automation summary.
9. **Tasks 10–12 are discoverability, not reliability.** Do them after the first green publish so SEO work cannot hide a deploy failure.
10. **Task 13 can run in parallel with SEO.** It changes the Android manifest and build gate, not the site.
11. **Task 14 before the next visual change.** It replaces the manual review-link step and makes the pre-merge check tappable.
12. **Tasks 15–20 after Tasks 0, 5 and 7.** The reference needs `main`, the tracked handoff and the pinned CLI. If Tasks 10–12 are already done, include the SEO gate in the reference; otherwise leave it out.
13. **Task 16 after 15, Task 17 before 18, Task 18 after the path changes, Task 19 after the content is stable, and Task 20 with 19.** The rules gate must not fail on a path or phrase that the earlier tasks have not yet changed.

## Verification checklist for the whole plan

- [ ] `python3 tools/check_all.py` passes with the new `renders`, `seo` and `rules` gates.
- [ ] `python3 tools/check_all.py --visual --artboards` passes with 18 gates after Part II.
- [ ] `python3 -m unittest discover -s tools/tests -v` passes.
- [ ] A push to `main` runs `shelf`, `visual`, then `publish`; a PR runs only the first two.
- [ ] `docs/_deploy.json` is generated, ignored by git, and present on the live host after the first publish.
- [ ] `check_live.py --receipt live` passes on a clean `main` checkout without a local `_deploy.json`.
- [ ] A deliberately changed local page makes `check_live.py` fail.
- [ ] A deliberately edited page without a render refresh makes `check_renders.py` fail.
- [ ] The live `/icons/` and `/renders/` bytes match the tree after deploy.
- [ ] The handoff names the deployed SHA and all its local assets return 200.
- [ ] A merged branch with a new commit fails the branch guard; a clean merged branch passes.
- [ ] The default branch is `main`; merged heads are deleted automatically.
- [ ] The pinned check image passes all gates present when it is built.
- [ ] Every modern page has a canonical URL and Open Graph/Twitter metadata, and the SEO metadata change re-rendered the captured pages rather than leaving `check_renders` stale.
- [ ] The homepage has one valid `WebSite`/`SearchAction` JSON-LD block and `/search/?q=icons` returns filtered results, with the homepage render refreshed.
- [ ] Search Console verification, sitemap submission and the one-off Lighthouse scores are recorded in `SEO.md`.
- [ ] The manifest check rejects `allowBackup=true`, an exported filterless activity, a bad APK signature and a digest that disagrees with `docs/data/tools.json`.
- [ ] A same-repo PR posts a working review URL; a fork PR does not receive a Vercel token.
- [ ] No secret appears in the repository, logs, handoff or output.
- [ ] `wc -l AGENTS.md` is at most 260.
- [ ] `CLAUDE.md` is a pointer, not a duplicate.
- [ ] `python3 tools/check_agent_rules.py --check` passes.
- [ ] No `RENDERS_THEME=light`, “four palettes”, “all eight themes” or `artifacts/handoff` remains in policy/reference.
- [ ] The delivery matrix has an example and a required-link column for every row.
- [ ] `AGENTS.md` requires the smallest matching skill, reading it in full, and repo precedence.
- [ ] `PLAN.md` points to `AGENTS.md` as the current operating contract.

## Deferred, with reasons

- **APK signing in CI:** needs the keystore and signing secrets; keep local. The manifest and signature *verification* can run in CI after a local build.
- **Native app unit tests:** the engine has 166 tests, but the Java app has no test harness. The APK manifest check is the first guard; a JVM/Robolectric harness for compressor, pack and style logic is a separate project.
- **CSP:** the site uses inline styles and scripts for generated pages. A restrictive policy needs nonces/hashes or a generator change; the current HSTS, frame, referrer, permissions and content-type headers stay.
- **Drive refresh:** needs Google OAuth and file-folder reconciliation; handle after the publish verdict.
- **Physical arm64 device verdict:** needs a real device or a device farm; the emulator is not a renderer of record.
- **Automatic Vercel deployment deletion:** conflicts with the audit's evidence model; prune only with a recorded reason.
- **Full handoff content generation:** this plan generates provenance only. The rest of the handoff stays editable copy; generating every section would be a separate content-system project.
- **Rewriting the full `PLAN.md`:** it is a product/engine roadmap, not a policy file. Only the supersession note changes here.
- **Auto-generating the agent reference:** the detail is stable prose and code snippets; generating it would add a build step without preventing drift.
- **Linting prose quality:** the rule linter checks paths, phrases, headings and commands. It does not judge whether a rule is wise; that remains a review.
- **Moving historical sections out of git history:** keep them in `docs/agent-reference.md`; deleting them loses the hard-won operational warnings.
- **Stale edition copy outside the rules:** `tools/catalog.json` and the generated `docs/figma/` copy still say “four palettes” while the live site says one palette. Treat that as a separate copy change with its own render/artboard refresh; do not hide it inside the rules rewrite or claim the audit fixed it.
