# Agent Operating Rules Implementation Plan

> **Superseded by `docs/superpowers/plans/2026-11-12-checks-and-rules.md`.** This file is the source/history for Part II; execute the compiled plan.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 823-line mixed `AGENTS.md` with a short, current operating contract, add a policy for using skills, move deep project detail into a reference document, and add a lint gate that prevents stale paths and contradictory rules from returning.

**Architecture:** `AGENTS.md` becomes the executable policy: scope, work loop, delivery matrix, verification commands, publish path, review rules and prohibitions. `docs/agent-reference.md` keeps the detailed render setup, project shape and historical implementation notes. `CLAUDE.md` points at `AGENTS.md` instead of duplicating it. `tools/check_agent_rules.py` validates both files against the current tree, so a rule that names a removed path or old theme fails a check.

**Tech Stack:** Markdown, Python 3.12 standard library, the existing `check_all.py` gate runner, GitHub Actions.

**Spec:** Current `AGENTS.md`; the checks plan at `docs/superpowers/plans/2026-11-12-checks.md`; and the 12 November 2026 audit in Context below.

## Context

The rules are not merely long. They are contradictory and partly obsolete:

- `AGENTS.md` is 823 lines, but most of it is project-internals documentation, not operating policy.
- Lines 48–49 say the gallery follows a dark page mode and `RENDERS_THEME=light` shoots the other mode.
- Lines 220–221 say the site is one printed edition with no light/dark switch.
- Line 299 describes “all eight themes” for native renders, but the app now has one edition.
- Line 494 says the Pantone sheet reads “the four palettes”; the current site has one edition.
- Lines 502 and 792 say the handoff source is under `artifacts/`, while the checks plan moves it to tracked `handoff/` source.
- The “verdict” rule says a UI change waits before *commit, deploy and Drive refresh*. The practical workflow needs a distinction: visual changes need a public review before merge; non-visual changes still need checks but not a design verdict.
- The same delivery instructions are repeated three times, with slightly different counts and examples.
- The rules contain no lint against removed commands or paths, so every refactor silently makes them less trustworthy.

The target is not “shorter for its own sake.” It is a document an agent can follow without guessing, and a reference an agent can search when it needs implementation detail.

### Rule audit

| Current block | Problem | Target |
|---|---|---|
| Delivery rule / “learned the hard way” / phone delivery | Three overlapping versions of one rule | One delivery matrix plus one link rule |
| Render mode and `RENDERS_THEME` | Contradicts the one-edition site | Remove the old mode language |
| Eight themes / four palettes | Historical app and site states | Reference only, corrected to current edition |
| Handoff under `artifacts/` | Ignored source cannot be deployed by CI | `handoff/` after Checks Task 5 |
| “Wait for a verdict” | Ambiguous scope and blocks non-visual work | Visual UI only; code/docs follow checks |
| Project shape (321–752) | Valuable detail, wrong document | `docs/agent-reference.md` |
| Local render setup | Detailed environment instructions | Reference, with commands kept executable |
| Completion notification and handoff | Policy, still current | Keep in `AGENTS.md`, shortened |

## Global Constraints

- `AGENTS.md` must stay at or below 260 lines. If a rule needs a long explanation, put the explanation in `docs/agent-reference.md` and link it.
- `docs/agent-reference.md` may be long, but every path and command in it must resolve.
- `CLAUDE.md` contains no duplicate rules; it points to `AGENTS.md`.
- Preserve every still-valid technical command and warning. Moving a section is allowed; deleting a hard-won operational fact is not.
- Do not encode a commit SHA, deployment ID, date-sensitive count or branch name as a permanent rule.
- Do not add a secret, verification token or private URL to either document.
- The public-link and phone-width rules remain mandatory for visual deliveries and build handoffs.
- Visual UI changes still require a public review before merge. Non-visual code, copy, metadata and documentation changes require the relevant checks, not a design verdict.
- The checks plan is the authority for publish, parity, render freshness, artboards and handoff paths. This plan must not redefine those commands independently.
- No new runtime dependency. The linter uses the Python standard library.
- Skills are methods, not scope expansion. Use the smallest skill that matches the task, read it fully before acting, and keep `AGENTS.md` and the repository checks authoritative when generic advice conflicts.

## Review Focus

These are the failure modes a rule rewrite is most likely to introduce:

1. **False confidence from a shorter file:** a policy sentence loses a command or warning that made the old rule work. Preserve the actual commands and test for them.
2. **Lint false positives:** backticks contain shell commands, URLs, globs, placeholders and generated paths. Classify tokens before checking existence.
3. **Path drift after the Checks work:** `artifacts/handoff` becomes `handoff/`, `check_all` gains gates, and the default branch changes. Sequence this plan after Checks Tasks 0–5 or update both documents in the same commit.
4. **Ambiguous “visual”:** copy inside a screenshot, metadata-only changes and generated pages are easy to misclassify. The delivery matrix must name examples for each row.
5. **Two sources of truth:** a command or path repeated in both `AGENTS.md` and the reference can drift. Keep policy in `AGENTS.md`, mechanics in the reference, and make the linter check both.
6. **Review rule overreach:** turning every code change into a design review blocks delivery. Limit the review gate to changes that alter what a reader sees.
7. **Skill cargo-culting:** loading several skills for a routine edit, citing a skill without reading it, or treating a skill as permission to add scope. The policy must require a matching workflow, the whole skill, and repo precedence.

---

### Task 0: Freeze the target state and preserve today’s reference

**Files:**
- Read: `AGENTS.md`
- Read: `docs/superpowers/plans/2026-11-12-checks.md`
- Create: `docs/agent-reference.md`

**Interfaces:**
- Produces: a reference document containing the detailed sections currently at `AGENTS.md` lines 165–752, with stale theme/palette claims corrected.
- Produces: one recorded baseline of the current rules before editing.

- [ ] **Step 1: Confirm the checks plan dependencies**

  Read the checks plan and confirm these tasks are either complete or explicitly scheduled before this plan:

  - Task 0: `main` is the default branch and `1b6b911` is in `main`;
  - Task 5: handoff source is tracked at `handoff/`;
  - Task 7: Vercel CLI is pinned;
  - Task 10: SEO metadata is in the generators.

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
  - Replace `artifacts/handoff/index.html` with `handoff/index.html` only after Checks Task 5; otherwise leave the current path and add a `<!-- TODO(handoff-move) -->` marker that the linter will reject once the move lands.
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

### Task 1: Write the short operating contract

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
  the checks plan and the reference. Do not restate the project history here.

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
  - `docs/superpowers/plans/2026-11-12-checks.md` for the publish/checks build.
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

### Task 2: Make the delivery matrix executable

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

### Task 3: Add the rule-lint gate

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

### Task 4: Reconcile the project plans and links

**Files:**
- Modify: `PLAN.md` (top “Current decision” note only)
- Modify: `README.md` (link the operating contract)
- Modify: `docs/superpowers/plans/2026-11-12-checks.md` (link this plan and record dependencies)
- Modify: `AGENTS.md` (reference links only)

**Interfaces:**
- Produces: one policy source (`AGENTS.md`), one technical reference (`docs/agent-reference.md`), one checks plan, one rules plan.
- Produces: no duplicate “current state” paragraph across them.

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

- [ ] **Step 3: Cross-link the two plans**

  At the top of the checks plan add:

  ```markdown
  > **Rules plan:** `docs/superpowers/plans/2026-11-12-agent-rules.md` updates
  > the agent contract after the publish and handoff paths here are stable.
  ```

  At the top of this plan add a dependency note pointing to Checks Tasks 0, 5 and 7.

- [ ] **Step 4: Verify no duplicate policy**

  ```bash
  rg -n "wait for a verdict|four palettes|all eight themes|artifacts/handoff" AGENTS.md docs/agent-reference.md PLAN.md README.md
  ```

  Expected: no matches in policy/reference. Historical mentions in `PLAN.md` are acceptable only under the supersession note.

- [ ] **Step 5: Commit**

  ```bash
  git add PLAN.md README.md AGENTS.md docs/agent-reference.md docs/superpowers/plans/2026-11-12-checks.md docs/superpowers/plans/2026-11-12-agent-rules.md
  git commit -m "Make the agent contract the single source of delivery policy"
  ```

---

### Task 5: Add a rule-review checklist and change protocol

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
    moved project mechanics to `docs/agent-reference.md`; added the rules gate.
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

1. **Task 0 after Checks Tasks 0 and 5.** The reference move needs the final `main` and handoff path.
2. **Task 1 immediately after Task 0.** The contract must exist before examples or lint.
3. **Task 2 before Task 3.** The lint should enforce the delivery rows that already have worked examples.
4. **Task 3 after the Checks path changes.** Do not add a lint that fails on a path the migration has not created yet.
5. **Task 4 last among the content changes.** Cross-links should point at stable plans.
6. **Task 5 can run with Task 4.** It adds the rule-change protocol and changelog.

## Verification checklist

- [ ] `wc -l AGENTS.md` is at most 260.
- [ ] `CLAUDE.md` is a pointer, not a duplicate.
- [ ] `python3 tools/check_agent_rules.py --check` passes.
- [ ] `python3 -m unittest discover -s tools/tests -v` passes.
- [ ] `python3 tools/check_all.py` passes with the new `rules` gate.
- [ ] No `RENDERS_THEME=light`, “four palettes”, “all eight themes” or `artifacts/handoff` remains in policy/reference.
- [ ] The delivery matrix has an example and a required-link column for every row.
- [ ] `PLAN.md` points to `AGENTS.md` as the current operating contract.
- [ ] The checks plan links this plan and the rules plan links the checks plan.
- [ ] No secret, token or private URL appears in either document.

## Deferred, with reasons

- **Rewriting the full `PLAN.md`:** it is a product/engine roadmap, not a policy file. Only the supersession note changes here.
- **Auto-generating the reference:** the detail is stable prose and code snippets; generating it would add a build step without preventing drift.
- **Linting prose quality:** the linter checks paths, phrases, headings and commands. It does not judge whether a rule is wise; that remains a review.
- **Moving historical sections out of git history:** keep them in `docs/agent-reference.md`; deleting them loses the hard-won operational warnings.
