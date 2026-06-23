---
name: context-checkpoint
description: Checkpoint the session and recommend /clear to stay economical with tokens. Use when the context-budget hook warns the transcript is large, when the native context indicator is high, at a natural task boundary, or when the user asks whether to /clear. Saves everything to memory first, then recommends /clear and states exactly what was saved.
---

# Context checkpoint — when and how to recommend /clear

Long conversations cost tokens on every turn (the whole context is re-read each time). `/clear`
resets context to start fresh and cheap. The goal of this skill: clear at the **right moment**
(a task boundary, not mid-task) and **never lose state** when doing so.

## When to recommend /clear

Recommend it when BOTH are true:
1. **A natural boundary** — the current task/thread is finished (a brief shipped, a batch pushed, a
   question answered) and the next thing is unrelated. Never recommend /clear mid-task: clearing
   loses the working context you'd just have to rebuild.
2. **Context is getting expensive** — any of:
   - the `context-budget` UserPromptSubmit hook injected a "CONTEXT BUDGET" reminder, or
   - Claude Code's native context indicator is high (≈75%+ used), or
   - the session has run long with large tool outputs (builds, schema dumps, file reads).

If context is high but you're mid-task, don't clear — note that you'll checkpoint at the next
boundary, and keep going.

## The checkpoint procedure (do this BEFORE recommending /clear)

1. **Save everything to memory** so a fresh session can resume with zero loss:
   - Update `current-state` memory: repo HEAD + in-sync status, what shipped (versionCodes + run
     IDs/results), open items, and any pending briefs. This is the single most important file.
   - Update/create any session-specific memories (new conventions, tooling, decisions). Follow the
     normal memory rules (one fact per file, update the `MEMORY.md` index, link with `[[name]]`).
   - The memory folder is a Drive junction ([[memory-drive-sync]]) — writes sync automatically, no
     commit needed.
2. **Flush the repo** if anything's uncommitted: commit + push so git history (which Cowork also
   reads) and `origin/master` reflect reality. Verify `HEAD == origin/master`.
3. **Recommend /clear explicitly, and say what you saved.** Tell the user plainly: that it's a good
   point to `/clear`, why (token economy + clean boundary), and that you've saved everything
   (name the memories updated + confirm repo is pushed and in sync). Make it a recommendation, not
   an action — the user runs `/clear` (or restarts) themselves.

Phrase it like: "Good point to `/clear` — <task> is shipped and context is ~X%. I've saved
everything: `current-state` + <memories> updated, repo pushed (HEAD `<sha>` == origin). Safe to
clear/restart whenever."

## Notes
- Token measurement is approximate. The hook uses transcript file size as a proxy (it can over-count
  after a compaction, so it errs toward warning early). Tune `THRESHOLD_BYTES` in
  `.claude/hooks/context-budget.sh` if it nags too soon or too late.
- `/clear` does not reload settings/hooks (same session); a full restart does. Either way, saved
  memory + pushed repo make resumption lossless.

## Related memory
`current-state`, `macaco-workflow-skills`, `worklog-on-change`, `memory-drive-sync`.
