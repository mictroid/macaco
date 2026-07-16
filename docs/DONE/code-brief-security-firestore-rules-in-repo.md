# Macaco — Version-control Firestore rules + remove orphaned `/shared_trips` block

Puts the Firestore security rules under source control and removes the leftover public-read rule for
the retired trip-share feature. Two new files, one console deploy. No app (Kotlin) code changes.

**Why:** The Firestore rules currently live **only** in the Firebase console — no git history, no
review, no way to detect drift. That's exactly how the `/shared_trips` rule survived: the trip-share
feature was removed from the app (`docs/DONE/code-brief-retire-trip-share-link.md`) but its
public-read rule was left behind in the console (security review `docs/security-review-2026-07-16.md`,
finding M3). That rule grants **unauthenticated read** of any `shared_trips/{shareId}` document — a
dormant world-readable surface on a collection the app no longer uses. This brief captures the current
`users/**` isolation rule as code and drops the orphaned block.

---

## Change 1 — Add `firestore.rules` at the repo root

Create `firestore.rules` with the verified per-user isolation rule and **without** the `shared_trips`
block:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Each user can only read/write their own subtree. This is the entire data-access boundary.
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    // NOTE: the /shared_trips block was removed. The view-only trip-share feature was retired
    // (docs/DONE/code-brief-retire-trip-share-link.md); nothing writes or reads this collection,
    // and its old rule allowed unauthenticated public reads. See security-review-2026-07-16.md (M3).
  }
}
```

**File:** new `firestore.rules` (repo root).

---

## Change 2 — Add `firebase.json` pointing at the rules

If a `firebase.json` doesn't already exist, create it so the rules can be deployed from source:

```json
{
  "firestore": {
    "rules": "firestore.rules"
  }
}
```

If one already exists, just add/merge the `"firestore"` key. **File:** `firebase.json` (repo root).

---

## Change 3 — Deploy the rules and confirm the orphaned block is gone

This is a one-time console/CLI step (not a code change), listed so it isn't forgotten:

- With the Firebase CLI: `firebase deploy --only firestore:rules --project macaco-499016`
  (signed in as `houseofmmminq@gmail.com`), **or**
- Manually: Firebase console → Firestore Database → Rules → paste the Change 1 rules → Publish.

After publishing, reopen the Rules tab and confirm there is **no** `match /shared_trips/{shareId}`
block remaining.

---

## Verification

- Publishing must succeed with no syntax errors (the rules compile in the console editor / CLI).
- Smoke test on a signed-in build: saving, editing, and deleting an entry still works (exercises
  `users/{uid}/entries` read+write).
- Confirm the `shared_trips` match block is absent from the published rules.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add per-user isolation rules, drop the orphaned `shared_trips` block | new `firestore.rules` |
| 2 | Point Firebase deploy at the rules file | `firebase.json` |
| 3 | Deploy rules; verify `shared_trips` block is gone (console/CLI step) | — |

**Owner note:** Changes 1–2 are safe for Claude Code to create in the repo. Change 3 (the deploy) is
yours to run, since it publishes to the live project.
