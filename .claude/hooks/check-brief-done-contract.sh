#!/usr/bin/env bash
# Stop-hook guard for the Cowork done-contract.
#
# Cowork reads ONLY three filesystem locations to learn a brief's status:
#   docs/code-brief-*.md   (root)  -> PENDING
#   docs/DONE/                       -> DONE
#   docs/worklog-YYYY-MM-DD.md       -> the vc / commit / deviations record
#
# The failure this guard catches: a brief was moved into docs/DONE/ in this
# session's local (unpushed) or uncommitted work, but no dated worklog file was
# touched. That leaves Cowork able to see "done" but with no record of which vc,
# which commit, or what deviated — the worklog is its only source for that.
#
# Scope is deliberately limited to unpushed commits + the working tree, so the
# many already-shipped briefs in docs/DONE/ never trigger a false positive.
#
# Exit 2 + stderr => blocks the stop and feeds the reason back so it gets fixed.
# Exit 0 => everything consistent, stop normally.

cd "$CLAUDE_PROJECT_DIR" 2>/dev/null || exit 0
command -v git >/dev/null 2>&1 || exit 0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0

changed=""

# Committed-but-unpushed changes (only if we have an upstream to compare to).
if git rev-parse --verify --quiet origin/master >/dev/null 2>&1; then
  changed="$(git diff --name-only origin/master...HEAD 2>/dev/null)"
fi
# Uncommitted changes (staged + unstaged + untracked).
changed="$changed
$(git status --porcelain 2>/dev/null | sed 's/^...//')"

# Did this local work add/move a brief into docs/DONE/ ?
done_touched="$(printf '%s\n' "$changed" | grep -E 'docs/DONE/code-brief-.*\.md' | head -1)"
[ -n "$done_touched" ] || exit 0

# Was any dated worklog touched alongside it?
worklog_touched="$(printf '%s\n' "$changed" | grep -E 'docs/worklog-[0-9]{4}-[0-9]{2}-[0-9]{2}\.md' | head -1)"
[ -n "$worklog_touched" ] && exit 0

# Drift detected: brief marked DONE locally but no worklog entry.
echo "Cowork done-contract incomplete: a brief was moved to docs/DONE/ in your unpushed/uncommitted work, but no docs/worklog-YYYY-MM-DD.md was updated. Cowork reads the worklog for the versionCode, commit hash, and deviations. Add that entry (then commit) before finishing." >&2
exit 2
