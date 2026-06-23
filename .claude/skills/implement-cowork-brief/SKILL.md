---
name: implement-cowork-brief
description: Implement a Cowork code brief (docs/code-brief-*.md) for the Macaco app. Use when a new brief is waiting in docs/ (the SessionStart/UserPromptSubmit hook flags them) or when the user says to implement/verify a brief. Enforces the critical "verify signatures against the live repo before implementing" rule — Cowork drafts from the correct repo now, but still makes import/signature mistakes.
---

# Implement a Cowork brief

Cowork writes implementation briefs as `docs/code-brief-*.md`. As of 2026-06-21 it drafts from the
**correct** repo (`mictroid/macaco`, package `com.houseofmmminq.macaco`) — it was given an explicit
workspace directive + `docs/cowork-repo-source-of-truth.md`. (Historically it drafted off the stale
`C:\Users\micke\AndroidStudioProjects\MyApplication` clone with package `com.example.myapplication`;
that's fixed.)

Briefs are reliably accurate on *intent, package, and paths* — but **still not fully trustworthy on
signatures and imports**, even against the right repo. The most common error is a false "X is already
imported / already in scope" claim. In one recent batch, three separate briefs each wrongly claimed an
import already existed (`DisposableEffect`, `CameraUpdate`, `JournalBackup.ImportPhase`) — all three
actually had to be added (or referenced fully-qualified). **So every brief must still be verified
against live source before you touch a file** — the cost is one grep, the payoff is a clean build.

**Quick "is Cowork on the right repo?" spot-check** (cheap insurance, especially for older briefs that
may predate the switch): confirm the brief cites `com.houseofmmminq.macaco` + real dirs
(`data/sync/`, `ui/screens/`), not `com/example/myapplication/`, and that one "BEFORE" snippet matches
the live file. If a brief still references the stale package, treat the whole brief as suspect.

## How Cowork reads status — the done-contract

Cowork reads the **filesystem and git history directly** (never Firestore). Its entire view of brief
status comes from these places, so keeping them accurate IS how Cowork learns you're done:

| Location | Meaning to Cowork |
|----------|-------------------|
| `docs/code-brief-*.md` (repo root) | **Pending** — not yet implemented. |
| `docs/DONE/` | **Done** — Code moved the brief here after implementing it. |
| `docs/worklog-YYYY-MM-DD.md` | The record: which versionCode it landed in, the commit hash, and any deviations from the brief. |
| **git history** | Commit messages / diffs (Cowork added git-history reading — keep commit messages descriptive: name the brief, the files, and deviations). |

The contract is satisfied only when **all** of these agree for a brief: it's out of `docs/`, into
`docs/DONE/`, written up in the dated worklog (vc + commit + deviations), and committed with a clear
message. A `Stop` hook (`.claude/hooks/check-brief-done-contract.sh`) blocks you from finishing if a
brief reached `docs/DONE/` in unpushed/uncommitted work without a worklog entry — fix it before
stopping. Note: `macaco-brief.skill` / `macaco-status.skill` at the repo root are **Cowork's** files
(its own brief/status skills) — do not move or delete them.

## Procedure

For **each** loose brief in `docs/` (not yet in `docs/DONE/`):

### 1. Read the brief
Read the whole `docs/code-brief-*.md`. Note the files it claims to touch, the "BEFORE" snippets, and
any "X is already imported / already in scope" claims — those are the claims most likely to be wrong.

### 2. Verify against live source — do NOT skip, even for a "one-liner"
- Grep/read each target file. Confirm the function/composable signature, the exact "BEFORE" text,
  and the surrounding structure match what the brief shows.
- **Independently verify every import the brief assumes.** Grep the file's import block for it. If the
  brief says "no new imports needed" or "X is already imported," prove it — don't take its word.
- Confirm the line numbers are in the right ballpark (they drift; that's fine, but it tells you the
  brief is stale and the rest needs extra scrutiny).
- If the brief references a symbol (enum, helper, color), confirm whether it's imported or used
  fully-qualified in that file, and match the file's existing convention.

If a brief's "BEFORE" doesn't match live source, **stop and report the mismatch** rather than forcing
an edit — the brief may be targeting a version that no longer exists.

### 3. Implement
- Make the edits exactly as specified, **except** correct any verified signature/import errors. Add a
  missing import the brief wrongly assumed; use a fully-qualified name if that matches the file's
  existing pattern (e.g. the SettingsScreen progress card uses
  `com.houseofmmminq.macaco.data.sync.JournalBackup.ImportPhase` fully-qualified — match it).
- Match surrounding code style (comment density, naming, idiom).
- For new user-facing strings, add the key to **all 11 locales** — use the `bulk-localize-strings`
  skill (don't leave non-English locales missing the key).

### 4. Build
Run a build to prove it compiles (see `build-env` memory — `gradlew` needs JAVA_HOME set to the
Android Studio JBR):
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew assembleDebug 2>&1 | Select-String "BUILD SUCCESSFUL|BUILD FAILED|error:|e: "
```
`compileDebugKotlin` is fine for a faster Kotlin-only check; use `assembleDebug` when resources
(strings) changed.

### 5. Archive + commit
- Move the brief to `docs/DONE/` (`Move-Item docs\code-brief-*.md docs\DONE\`).
- Commit with a message that records **what was verified and any deviations from the brief**, e.g.:
  ```
  feat(area): <what changed>

  <brief-name> (File.kt): <change>. Verified vs live source first.
  Deviation: added `import …CameraUpdate` (brief wrongly said already imported).

  Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
  ```

### 6. Update the durable record
Per the `worklog-on-change` convention, update the dated worklog (`docs/worklog-YYYY-MM-DD.md`), the
briefs ledger, and the `current-state` memory — note each brief, file(s), and any deviation.

## Shipping
Implementing a brief does **not** publish it. If the user wants it released, use the `ship-to-play`
skill to bump the versionCode, refresh release notes, push, and dispatch the WIF workflow. Multiple
briefs are usually batched into one version bump.

## Maintaining this skill
Keep this SKILL.md current **deliberately, not automatically**. After a run, if you hit a *new* class
of brief error (a new kind of wrong signature, a recurring deviation, a changed convention) or the
procedure here was wrong, update this file **in the same commit as the work** and note it in the
worklog. Do NOT auto-rewrite the skill on every use: most runs succeed and would add only churn, and
a wrong *rationale* (like the old "stale clone" reason) won't surface from a successful run — it takes
human judgment. Update on a real signal, with evidence, reviewed like code.

## Related memory
`cowork-brief-workflow`, `brief-detection-hook`, `build-env`, `strings-localization`,
`worklog-on-change`, `current-state`.
