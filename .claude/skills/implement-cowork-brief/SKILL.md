---
name: implement-cowork-brief
description: Implement a Cowork code brief (docs/code-brief-*.md) for the Macaco app. Use when a new brief is waiting in docs/ (the SessionStart/UserPromptSubmit hook flags them) or when the user says to implement/verify a brief. Enforces the critical "verify signatures against the live repo before implementing" rule because Cowork drafts off a stale clone and gets imports/signatures wrong.
---

# Implement a Cowork brief

Cowork writes implementation briefs as `docs/code-brief-*.md`. They are usually accurate on
*intent* but **not trustworthy on signatures** — historically Cowork drafted against the stale
`C:\Users\micke\AndroidStudioProjects\MyApplication` clone (`com.example.myapplication`), so package
names, line numbers, "this is already imported" claims, and exact "BEFORE" snippets drift from the
live repo (`…\wanderlog`, `com.houseofmmminq.macaco`). **Every brief must be verified against live
source before you touch a file.** In one recent batch, three separate briefs each falsely claimed an
import already existed (`DisposableEffect`, `CameraUpdate`, `JournalBackup.ImportPhase`).

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

## Related memory
`cowork-brief-workflow`, `brief-detection-hook`, `build-env`, `strings-localization`,
`worklog-on-change`, `current-state`.
