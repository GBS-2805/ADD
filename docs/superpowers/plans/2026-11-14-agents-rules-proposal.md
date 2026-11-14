# AGENTS Rules Merge Proposal

**Status:** Merged and verified on 2026-11-14. The active contract is now **135 lines**,
**6,799 characters** and **13 headings**, still under the 260-line ceiling.
**Before merge:** 107 lines, 5,271 characters, 12 headings.
**Reference limit:** the pasted reference allows 64,000 characters; this project already has
a stricter 260-line ceiling, so the project limit wins.

The goal is not to copy the reference. It is to keep the rules that are true for this
repository, rewrite the generic ones into project language, and reject rules that would
damage the existing plan/publish system.

---

## 1. What already exists and should not be duplicated

| Reference section | Existing coverage | Decision |
|---|---|---|
| Ground rules | `Scope`, `Verification`, `Work loop` | Keep current; add one evidence sentence |
| Start of task | `Work loop` | Add requirement table and active-plan line |
| During the task | `Work loop`, `Verification` | Add drift check; keep current plan discipline |
| Definition of done | `Verification`, `Never`, plan gates | Add final diff read and debris rule |
| Project commands | `docs/agent-reference.md`, delivery matrix | Add a verified command table there, not in AGENTS |
| File upkeep | `Scope`, `Never` | Add no orphan/duplicate/dead code |
| Scope | `Scope` | Keep current; no change |
| Banned phrases | `Work loop`, `Verification` | Replace with a compact evidence-language rule |
| Final report | Handoff / verification language | Add a short final-report section |
| Resuming | `Work loop` | Add `git status` / `git diff` / active plan |
| Self-audit | Not explicit | Add a six-question final check |

---

## 2. Proposed merge, in project terms

These are the exact additions I recommend. They fit the existing section order and keep
the contract under 260 lines.

### A. `## Work loop` — add three sentences

```markdown
Restate the request in one paragraph, then list every explicit requirement and the
checks that prove it. Read the active plan under `docs/superpowers/plans/`; do not
create or overwrite root `PLAN.md`. A step without a verification method is not a step.
At the start of a resumed turn, read the active plan, `git status` and `git diff`; trust
the diff over the checkboxes.
```

Why: the reference’s “write PLAN.md” conflicts with the repository’s existing historical
`PLAN.md` and its plans directory. This keeps the useful requirement-table and resume
discipline without creating a second plan system.

### B. `## Verification` — add one paragraph

```markdown
Use evidence language: say “done”, “fixed”, “works” or “tested” only with the command and
the observed result from this session. If a check could not run, say “not checked” and
name the blocker. Do not use “should work”, “looks good”, “the rest is straightforward” or
“tests are passing” without output.
```

Why: the reference’s long banned-phrase list is mostly a restatement of this rule. One
paragraph is easier to enforce and leaves room for the evidence exceptions.

### C. `## Scope` — add one sentence

```markdown
Leave no orphan files, duplicate logic, debug logs, commented-out code, scratch scripts or
dead code. Do not reformat files you did not otherwise change.
```

Why: this is the useful part of “File upkeep” and belongs beside scope discipline.

### D. New `## Final report` after `## Handoff`

```markdown
Done in one sentence. Then: requirements met n/n; every changed file and why; the exact
commands run and their result; what was not checked and why; assumptions; and what was
suggested but not touched. A visual change that was not viewed is reported as not viewed,
not as working.
```

Why: the current contract says “report the exact pass/fail output” but does not fix the
report shape. This closes the “would the user have to remind me?” gap.

### E. New `## Final self-audit` before `## Changing these rules`

```markdown
Before sending: every requirement has evidence; the last edit was followed by its check;
the diff is only the requested scope; no half-finished work is described as finished; no
plan item was silently dropped; the user can tell what works, what does not, and what to
check themselves.
```

Why: this is the reference’s best section. It is short enough to keep, and it catches the
failure mode the user actually named: missing work that was not surfaced.

### F. `docs/agent-reference.md` — add the project command table

Do not put this in `AGENTS.md`; keep policy and mechanics separate.

| Need | Command |
|---|---|
| Fast verdict | `python3 tools/check_all.py` |
| Visual verdict | `python3 tools/check_all.py --visual` |
| Release verdict | `python3 tools/check_all.py --visual --artboards` |
| Rules lint | `python3 tools/check_agent_rules.py --check` |
| Checker self-tests | `python3 -m unittest discover -s tools/tests -v` |
| APK verify | `python3 tools/check_apk.py --apk dist/ADD-3.9.apk --catalog tools/catalog.json --published docs/data/tools.json` |
| Site deploy | `python3 tools/deploy_vercel.py` |
| Review surface | `python3 tools/deploy_review.py review` |
| Review shots | `python3 tools/deploy_review.py shots` |
| Handoff deploy | `python3 tools/deploy_review.py handoff` |
| APK handoff | `python3 tools/deploy_review.py apk` |
| Handoff provenance | `python3 tools/update_handoff.py --commit <sha> --deployed-at <iso>` |
| Engine suite | `bash tools/engine-spike/verify-suite.sh` |
| Chrome goldens | `bash tools/engine-spike/verify-chrome.sh` |
| Site build | `./build.sh stage` |
| APK build | `./build.sh add` |

The table is verified against the repository. Commands that do not exist are not listed.

---

## 3. What I would not adopt literally

| Reference rule | Why not | Project replacement |
|---|---|---|
| “Write PLAN.md” at the start of every task | Root `PLAN.md` is a large historical plan; overwriting it destroys project history | Use the active plan under `docs/superpowers/plans/`; never overwrite root `PLAN.md` |
| 64,000-character limit | The project already enforces 260 lines, which is stricter and keeps policy readable | Keep the 260-line ceiling; record characters only for awareness |
| “Ask at most one question” | The current rule is clearer: ask only when a missing fact blocks the work | Keep current |
| “Never write done/fixed/works/tested” | Too absolute; these words are valid with evidence | “Only with the command and observed result” |
| Long banned-phrase list | Duplicates the evidence rule and grows the contract | One evidence-language paragraph |
| Generic “Project commands” in AGENTS | Policy and mechanics would drift; the reference itself says not to invent commands | Command table in `docs/agent-reference.md` |
| “No new dependencies” absolute | The project already pins dependencies and sometimes needs one | Keep “no unrequested dependency; name any addition” |
| “Rewrite every plan on resume” | Plans are historical records; rewriting them hides drift | Trust the diff; update the active plan to match reality |

---

## 4. Estimated effect

| Metric | Before merge | Active after merge |
|---|---:|---:|
| `AGENTS.md` lines | 107 | **135** |
| `AGENTS.md` characters | 5,271 | **6,799** |
| 260-line ceiling | pass | **pass with margin** |
| New headings | 12 | **13** |
| Existing required headings | 11 | **11 unchanged** |

The additions are intentionally small. The reference’s full text would make the contract
longer than the project’s gate allows and would duplicate mechanics already in
`docs/agent-reference.md`.

---

## 5. Applied files and tests

| File | Change |
|---|---|
| `AGENTS.md` | Add A–E only |
| `docs/agent-reference.md` | Add the command table |
| `docs/agent-rules-examples.md` | Add one worked example for the final report/evidence rule |
| `docs/agent-rules-changelog.md` | Add a dated entry |
| `tools/check_agent_rules.py` | Add required strings for the new evidence/final-report rules |
| `tools/tests/test_check_agent_rules.py` | Add failing cases for the new strings |

The reference was not merged wholesale. A–E and the command table were applied; the
rules lint, its 14 tests, the changelog and the command reference were updated in the same
change.

---

## 6. Outcome

**A–F were merged.** The eight literal rules above remain rejected. The highest-value
additions are the requirement table, evidence language, final report and self-audit; they
directly address work being missed or described as finished before it was verified.

The active contract remains under the 260-line ceiling. No further decision is needed for
this merge.
