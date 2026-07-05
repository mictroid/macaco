---
name: check-ci
description: Check GitHub Actions / Pages run status and diagnose failures for the Macaco repo. Delegates the token-heavy log-fetching to a Sonnet subagent that returns a compact, classified report; the main (Opus) context decides what to do. Use after dispatching a release, when GitHub emails a failed run/deploy, or when asked to check CI. For a single quick status check, just run `gh run view` inline — delegate only when pulling failure logs or auditing several runs.
---

# Check CI (GitHub Actions + Pages)

Two workflows run in this repo:
- **Release to Play Store** (`workflow_dispatch`, `release.yml`) — the WIF publish path (~6 min).
- **pages build and deployment** (`dynamic`) — auto-fires on every push to master; publishes the legal
  HTML at the repo root (`privacy-policy.html`, `terms-of-service.html`) to GitHub Pages.

Checking them well means two different-cost jobs: **fetching** run status + failure logs (token-heavy,
noisy — `--log-failed` dumps a lot) and **deciding** what a failure means (judgment, needs project
knowledge). Split them: a **Sonnet subagent** fetches + classifies; the main (Opus) context acts.

## When to delegate vs do inline

- **Inline (no subagent):** a single quick status check — "did the vc59 run pass?" → one
  `gh run view <id>`. Cheap; a subagent would just add a round-trip.
- **Delegate to the subagent:** any time you'd pull `gh run --log-failed`, or audit several runs at
  once (e.g. "any red runs today?"). That's where the log volume would otherwise flood this context.

## Procedure

### 1. Delegate the fetch + classification to a Sonnet subagent

Spawn a subagent with the **Agent** tool, **`model: "sonnet"`**. It works entirely from `gh` in the repo
— give it this task verbatim:

> You are triaging GitHub Actions runs for the Macaco repo (`gh` is authenticated, on PATH). Do NOT
> paste raw logs back — return a compact classified report only.
>
> 1. `gh run list --limit 15` to see recent runs. Identify every run whose conclusion is **not**
>    `success` (failure / cancelled / timed_out) in the relevant window (default: today; or the run ID
>    the caller names).
> 2. For each non-success run: `gh run view <id>` to find the failing job/step, then
>    `gh run view <id> --log-failed` and extract only the **3–8 key error lines** (the actual error,
>    not setup/deprecation noise).
> 3. **Classify each failure** against these known Macaco signatures:
>    - **Pages `Deployment failed, try again later.`** on a `pages-build-deployment` run →
>      `TRANSIENT-pages-superseded`. Cause: rapid successive pushes to master each trigger a Pages
>      deploy to the same environment; concurrent ones fail. Harmless if a later Pages deploy succeeded
>      OR the pushed commits didn't touch the repo-root legal HTML (privacy-policy.html /
>      terms-of-service.html) — check `git show --stat` of the commit. Action: re-run the deploy.
>    - **`Release to Play Store` failing at `publishReleaseBundle` with `OAuthException invalid_grant`
>      / "ID Token … is stale to sign-in"** → `TRANSIENT-wif-stale-token`. Build/bundle/sign were fine;
>      only the Play upload token went stale. Action: re-dispatch release.yml, no code change.
>    - **`403 PERMISSION_DENIED` at `commitEdit`** on a release requesting `READ_MEDIA_VIDEO` → 
>      `KNOWN-play-media-declaration`. The one-time Photo/Video permissions declaration; should already
>      be done and persistent. Action: flag for human — if it recurs, the declaration regressed.
>    - **Anything else** → `REAL`. Return the failing step + the key error lines; do not guess a fix.
> 4. **Report** compactly, one block per non-success run: run ID, workflow name, conclusion, failing
>    step, the 3–8 key error lines, classification tag, and suggested action. If everything is green,
>    say so in one line. Also confirm live Pages if a Pages run failed: `curl -s -o /dev/null -w "%{http_code}"
>    https://mictroid.github.io/macaco/privacy-policy.html`.

### 2. Act on the report (main context)

- **TRANSIENT-pages-superseded** → `gh run rerun <id>` (note: an auto `pages-build-deployment` run can
  only be re-run if not already running). Confirm the live site is HTTP 200. No commit needed.
- **TRANSIENT-wif-stale-token** → re-dispatch via the `ship-to-play` skill's dispatch step (or
  `gh workflow run release.yml`); the versionCode wasn't consumed. Record the new run ID.
- **KNOWN-play-media-declaration** → surface to the user; this shouldn't recur post-vc54.
- **REAL** → diagnose here with the error lines the subagent returned; fix, don't re-run blindly.

### 3. Avoid causing the failure you're checking for

The most common red X in this repo is a **self-inflicted Pages race** from batching doc pushes. Per
`batch-doc-pushes`, fold doc/worklog commits into **one** push, not several rapid ones — that stops the
superseded-deploy failures at the source rather than re-running them after the fact.

## Related
Skills: `ship-to-play`, `sync-cowork-status`. Memory: `batch-doc-pushes`, `play-publish-wif`,
`play-media-permissions-declaration`, `wif-dispatch-timestamp`, `current-state`.
