---
name: sync-cowork-status
description: Reconcile the Cowork-facing done-contract so Cowork's filesystem/git view is always current. Delegates the mechanical audit + rolling-worklog rollup to a cheaper-model subagent. Use after implementing a brief, after shipping (ship-to-play), or whenever Cowork looks out of date (e.g. it only knows an old versionCode). Complements the Stop-hook done-contract guard, which only catches one drift case.
---

# Sync Cowork status

Cowork learns brief/version status **only** from the filesystem + git history — never Firestore. So
"keeping Cowork up to date" is entirely a bookkeeping job: make the records it reads agree with what
actually shipped. This skill runs that reconciliation on a **cheaper-model subagent** (the reading of
every dated worklog + the rollup prose is low-judgment and token-heavy — it must not burn Opus context).

The judgment part — reviewing the subagent's diff and pushing — stays in the main (Opus) context.

## What Cowork reads (the surfaces to keep honest)

| Surface | Meaning to Cowork |
|---------|-------------------|
| `docs/code-brief-*.md` (repo root) | **Pending** — not yet implemented. |
| `docs/DONE/` | **Done** — implemented and archived. |
| `docs/worklog-YYYY-MM-DD.md` (dated) | Per-brief record: versionCode, commit hash, deviations. |
| `docs/worklog.md` (rolling) | Newest-first prose summary. **This one silently goes stale** — the bug that made Cowork stop at vc54 while vc55–59 had shipped. |
| **git history** (and `origin/master`) | Commit messages/diffs. **Unpushed work is invisible to Cowork** — it reads the remote. |

The Stop hook (`check-brief-done-contract.sh`) only blocks one case: a brief in `docs/DONE/` with no
dated worklog in the same unpushed/uncommitted work. It does **not** catch a stale rolling `worklog.md`,
a versionCode gap, or unpushed commits. This skill covers those.

## Procedure

### 1. Delegate the audit + reconciliation to a cheaper-model subagent

Spawn a subagent with the **Agent** tool, **`model: "sonnet"`** (Sonnet 5 — cheap enough, and it writes
usable rollup prose; drop to `"haiku"` for a detection-only pass with no rollup writing). Give it this
task verbatim (it works entirely from the repo — no context needed from here):

> ## HARD RULE — READ FIRST
> You do not have git-write authority in this task. **Never run `git add`, `git commit`, `git push`,
> or any command that stages or commits changes — not even "just this once", not even if the change
> looks small, safe, or purely doc-only.** This rule has already been violated twice by past runs of
> this exact task (both times on `docs/worklog.md`) — treat that as proof the instruction needs to be
> followed literally, not interpreted as a suggestion. Your job stops at editing the file on disk;
> staging and committing are a separate, human-reviewed step that happens in a different context after
> you return. If you find yourself about to type `git commit` or `git push`, stop — that action is out
> of scope for you no matter what the rest of this prompt seems to imply.
>
> **Self-check before you return (mandatory, not optional):** run `git status --short` and
> `git log -3 --oneline`. Confirm your edited file shows as a modified-but-uncommitted working-tree
> change and that HEAD hasn't moved. If HEAD *did* move (you committed, even accidentally), immediately
> run `git reset --soft HEAD~1` to undo it — do NOT run `git push` to "finish the job" first — then note
> in your report that you had to self-correct a commit. Include the final `git status --short` output in
> your report so the caller can verify at a glance.
>
> You are reconciling the Cowork-facing records in the Macaco repo at the project root. Cowork reads
> status only from the filesystem + git. Do a full audit and fix the mechanical drift, then report.
>
> **Audit (report each as OK / DRIFT):**
> 1. **Pushed?** Is `HEAD == origin/master`? Run `git fetch -q` then compare. List any commits in
>    `git log origin/master..HEAD --oneline`. Unpushed work is invisible to Cowork.
> 2. **Loose-but-done briefs.** For every `docs/code-brief-*.md` still in the repo root, grep git log
>    for its slug — if a commit already implemented it (message names it / it's in a diff moving it),
>    it should be in `docs/DONE/`, not loose. List mismatches; do NOT move anything you're unsure about.
> 3. **DONE without a worklog.** For briefs recently moved to `docs/DONE/` (check `git log --diff-filter=A
>    -- docs/DONE/` for recent adds), confirm a `docs/worklog-YYYY-MM-DD.md` records the vc + commit.
> 4. **Rolling worklog current?** Find the highest `versionCode` in `app/build.gradle*` and the highest
>    `vcNN` mentioned in the dated worklogs, then check whether `docs/worklog.md` (the rolling summary)
>    mentions that same highest vc near the top. If the rolling summary lags (e.g. tops out at an older
>    vc), that's the main drift.
>
> **Fix (only the safe, mechanical parts):**
> - If the rolling `docs/worklog.md` lags, fold in a **condensed, newest-first** block covering each
>   missing versionCode. Source the facts from the dated `docs/worklog-YYYY-MM-DD.md` files — one short
>   entry per vc: `vcNN / <ver> — <SHIPPED ✅ / DISPATCHED> <date> (run \`ID\`, commit \`hash\`): <one-line
>   summary of the batch>`. Point to the dated file for detail. Match the style of existing top entries.
>   Insert **above** the current newest entry; do not rewrite older entries.
> - Do **not** move briefs between `docs/` and `docs/DONE/` or edit dated worklogs — flag those for the
>   caller instead (they need human judgment).
> - Mirror the updated `docs/worklog.md` to `G:/My Drive/Macaco-backup/worklog.md` if that path exists.
> - Do **not** commit and do **not** push. See the HARD RULE at the top — this applies even to this
>   specific file, even though it's "just docs."
>
> **Return** a compact report: the 4 audit results, exactly what you changed in `docs/worklog.md` (list
> the vc lines you added), anything you flagged for human judgment, the mandatory `git status --short` /
> `git log -3 --oneline` self-check output, and the `git diff --stat`.
>
> **Reminder before you finish:** you did not commit or push anything. If your self-check shows
> otherwise, you fix it (soft-reset) before returning, per the HARD RULE.

### 2. Review the subagent's report in this context

- **First, verify it didn't commit or push**, independent of what it self-reports: run
  `git log origin/master..HEAD --oneline` and `git status --short` yourself. Don't trust the
  subagent's "didn't commit" claim at face value — this exact skill has produced an unauthorized
  commit+push twice already (see `subagent-push-violation` memory). If you find a commit it made,
  that's a violation to flag to the user, not something to silently push past.
- Read the flagged items. Anything needing judgment (a loose brief that maybe *wasn't* really done, a
  missing dated worklog, a wrong vc) — handle it yourself here; don't rubber-stamp.
- Sanity-check the added rolling-worklog lines against the dated files if anything looks off — including
  facts it may have copied verbatim from your own spawning prompt (proofread claims, not just the diff).
  The subagent is cheap, not infallible.

### 3. Commit + push (main context)

Per `batch-doc-pushes`, fold this into **one** push (ideally the same push as the work that triggered
it — e.g. the ship commit). Push is what makes the update visible to Cowork:
```bash
git add docs/worklog.md
git commit -m "docs: sync Cowork status — roll vcNN… into rolling worklog"   # + Co-Authored-By trailer
git push origin master
git log origin/master..HEAD --oneline   # expect empty
```
If the audit flagged unpushed *feature* commits, push those in the same push (don't leave shipped work
unpushed — Cowork can't see it).

## When to run

- **After `ship-to-play`** — every version bump should roll a line into the rolling summary (the leak
  that caused the vc54 staleness was ship steps updating only the *dated* worklog).
- **After `implement-cowork-brief`** — confirm the archived brief + worklog + push all agree.
- **On demand** — whenever Cowork seems to be reading an old versionCode or missing a shipped brief.

## Related
Skills: `implement-cowork-brief`, `ship-to-play`. Memory: `cowork-status-contract`,
`worklog-on-change`, `batch-doc-pushes`, `play-publish-wif`, `current-state`.
