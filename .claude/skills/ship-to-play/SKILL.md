---
name: ship-to-play
description: Release the Macaco app to Play closed testing via the WIF GitHub Actions workflow. Use when the user wants to bump the version, ship/publish, push to Play, or dispatch a release. Bumps versionCode, refreshes the (otherwise stale) release notes, pushes, verifies the remote, dispatches release.yml, and records the run ID + dispatch timestamp.
---

# Ship Macaco to Play (closed testing)

The canonical publishing path is the **WIF GitHub Actions workflow** (`.github/workflows/release.yml`,
manual dispatch → closed testing). It needs no local signing key. A local
`./gradlew publishReleaseBundle` path exists only as a fallback. This skill drives the WIF path.

Prerequisite: the code to ship is already committed (e.g. via `implement-cowork-brief`). This skill
handles bump → notes → push → verify → dispatch → record.

## Procedure

### 1. Bump versionCode
In `app/build.gradle.kts`, increment `versionCode` by 1. **Leave `versionName` as `"1.5"`** unless the
user explicitly asks to change it — every release on this project keeps versionName 1.5 by convention.

### 2. Refresh release notes — DO NOT SKIP
Rewrite `app/src/main/play/release-notes/en-US/default.txt` to describe **this** version's changes.

⚠️ **This is the #1 recurring mistake on this project.** Gradle Play Publisher republishes `default.txt`
verbatim on every release, so if you don't edit it the "What's new" goes stale — vc28/29/30 all shipped
vc27's notes (see the `play-release-notes-stale` memory). Always edit it on every bump.
- Keep it user-facing (no internal/brief jargon), ~5 bullet lines, lead with `What's new:`.
- Summarize the whole batch since the last shipped version, not just the last commit.

### 3. Commit the bump + notes
```
Bump vc<N> + refresh release notes for the batch

versionCode <N-1>-><N> (versionName 1.5). Batch since vc<N-1>: <one-line list>.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

### 4. Push and VERIFY remote == local BEFORE dispatching
```powershell
git push origin master; git rev-parse HEAD; git rev-parse origin/master
```
The two SHAs **must match**. Dispatching before the push lands would build the old tree. Do not
proceed to step 5 until they're identical.

### 5. Dispatch the WIF workflow (separate command, after the push landed)
`gh` is on PATH. Manual-dispatch only — this is the "minimal push": dispatch, do **not** watch.
```powershell
gh workflow run release.yml --ref master; Write-Output "Dispatched at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"
```
Then capture the run ID:
```powershell
gh run list --workflow=release.yml --limit 1
```
Note: `gh run list`'s timestamp is the run **start**, not completion (see `wif-dispatch-timestamp`
memory). Record the **local dispatch time** you printed above plus the run ID.

### 6. Record + report
- Show the user the dispatch time, the run ID, and the run URL
  (`https://github.com/mictroid/macaco/actions/runs/<run-id>`).
- Update the dated worklog (`docs/worklog-YYYY-MM-DD.md`) and the `current-state` memory with: HEAD SHA,
  "origin == HEAD verified", run ID, dispatch timestamp, and the batch contents. Commit + push that.
- State plainly that the run is **dispatched, not confirmed** — it can be checked later with
  `gh run list` (builds typically take ~6 min). Do not block waiting for it.

### 7. Email the user the summary — ALWAYS, standing request (2026-06-27)
After every push/dispatch to Play, the user wants the ship summary emailed to them. Invoke the `me`
skill to draft it to `michael.tromp78@gmail.com` (vc, run ID, dispatch time, run URL, batch contents,
and what still needs on-device verification). The Gmail connector can only **create a draft** (it
can't send) — so tell the user the draft is in their inbox ready to send. Do this without being asked
each time; it's a standing rule.

## Notes
- A build typically takes ~6 minutes; a SUCCESS run publishes to the `alpha`/closed-testing track.
- WIF is keyed to repo + ref + workflow, **not** commit SHAs — a force-push doesn't disrupt it.
- Sideloaded debug APKs can't complete a real purchase (paywall) — verify on a Play-installed build.

## Maintaining this skill
Keep this SKILL.md current **deliberately, not automatically**. If the release path changes (workflow
name, track, branch, a new required step) or you hit a new release-time gotcha, update this file in the
same commit and note it in the worklog. Don't auto-rewrite on every ship — a successful release gives
no signal that the procedure is stale.

## Related memory
`play-publish-wif`, `play-release-notes-stale`, `wif-dispatch-timestamp`, `build-env`,
`worklog-on-change`, `current-state`.
