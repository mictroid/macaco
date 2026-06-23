---
name: me
description: Email the user (michael.tromp78@gmail.com) a recap of the relevant content — a session summary, a recommendation, a checklist, etc. Use when the user says "send that to my email", "email me this", "/me", or otherwise wants the current discussion delivered to their inbox. Creates a Gmail draft (the connected Gmail connector cannot send directly).
---

# Email me ("/me")

Send the user a recap of whatever is relevant by drafting it to their email. The user is
**Michael**; their address is **michael.tromp78@gmail.com**.

## Key constraint: drafts only

The connected claude.ai **Gmail connector supports `create_draft`, NOT direct send.** So this skill
**creates a draft** in the user's Gmail and tells them to hit Send. Never claim the email was sent —
say the draft is ready in their Drafts.

## Procedure

1. **Decide the content.** If the user passed args (e.g. `/me the 1.5→1.6 plan`), scope the email to
   that. Otherwise, default to a recap of the current discussion / what was just done this turn —
   the same thing you'd summarize back in chat, written as a self-contained email (the reader has no
   chat context).
2. **Load the tool** if not already available:
   `ToolSearch select:mcp__claude_ai_Gmail__create_draft`
3. **Create the draft** with `mcp__claude_ai_Gmail__create_draft`:
   - `to`: `["michael.tromp78@gmail.com"]` (plain address; the "Name <addr>" form is NOT supported).
   - `subject`: short and specific, prefix with `Macaco — ` for project work
     (e.g. `Macaco — on-device verification checklist (vc36)`).
   - `body`: plain text. Lead with `Hi Michael,`, use clear sections (`== Heading ==`), keep it
     self-contained (spell out run IDs, commit SHAs, versionCodes — the email stands alone). Sign
     off `— Claude`.
   - Avoid characters that mangle in plain-text mail; ASCII arrows/dashes are fine.
4. **Confirm** by reporting the returned draft ID and that it's waiting in Drafts to send. If the
   user already has other drafts from this session, note any overlap so they can prune.

## Scheduling a future send

If the user wants it delivered *later* ("email me this tomorrow at 9"), use the **schedule** skill to
create a one-time cloud routine that runs `create_draft` — embed the full content in the routine
prompt (the cloud agent has no local files / chat context) and attach the Gmail connector
(connector_uuid `0d812d61-1588-4187-a183-74144cdfc431`). Still a draft, not a send.

## Related
`schedule` skill (for delayed/recurring sends), `current-state` memory (source for recaps).
