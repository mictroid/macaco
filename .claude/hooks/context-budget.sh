#!/usr/bin/env bash
# UserPromptSubmit hook: nudge toward /clear when the session transcript grows large.
#
# No hook gets a precise live token count, so this uses the transcript file SIZE as a
# proxy for context cost. It over-counts after a compaction (the on-disk transcript keeps
# everything), so it errs toward warning early — acceptable for a "consider /clear" nudge.
#
# When over THRESHOLD_BYTES it injects a reminder telling Code to run the context-checkpoint
# skill (save everything to memory, then recommend /clear and state what was saved). Fires on
# each prompt while over threshold — a persistent nudge until you clear/restart.
#
# Fail-safe: any parsing/stat problem -> exit 0 (silent), never blocks the prompt.
# Tune THRESHOLD_BYTES to taste; ~3 MB is a rough "long session" mark on this project.

THRESHOLD_BYTES=3000000

input="$(cat)"

# Extract "transcript_path" from the stdin JSON without jq (not installed on this machine).
tp="$(printf '%s' "$input" | sed -n 's/.*"transcript_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
[ -n "$tp" ] || exit 0

# JSON-unescape Windows backslashes ("C:\\Users\\..." -> "C:/Users/..."); Git Bash reads C:/ paths.
tp="$(printf '%s' "$tp" | sed 's/\\\\/\//g')"
[ -f "$tp" ] || exit 0

bytes="$(wc -c < "$tp" 2>/dev/null | tr -d ' ')"
case "$bytes" in (''|*[!0-9]*) exit 0;; esac
[ "$bytes" -gt "$THRESHOLD_BYTES" ] || exit 0

mb=$((bytes / 1000000))
printf '{"hookSpecificOutput":{"hookEventName":"UserPromptSubmit","additionalContext":"CONTEXT BUDGET: session transcript ~%sMB (over threshold). If the current task is at a natural boundary, run the context-checkpoint skill — save everything to memory, then recommend /clear and state what was saved. If mid-task, keep going and checkpoint at the next boundary."}}\n' "$mb"
