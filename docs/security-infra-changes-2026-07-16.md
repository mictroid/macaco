# Security & Infra Changes — 2026-07-16 (for Claude Code awareness)

This session was a security review + hardening done mostly in the Google Cloud / Firebase consoles
(via Cowork). Most changes are **not visible in git**, so this note exists so Code doesn't get
surprised. Full findings: `docs/security-review-2026-07-16.md`.

## Repo change (in git-ignored file — will NOT show in diffs)

- **`local.properties` → `MAPS_API_KEY` was replaced.** Old value `AIzaSy…nCP5s` → new value
  `AIzaSyCcAotZACiiiTrYwMniBzbRnh1juCPwiCg`. The manifest still reads the `MAPS_API_KEY` placeholder,
  so **no code change** — but if you regenerate `local.properties` or a teammate has an old copy, the
  map won't render until the new key is set. The GitHub Actions **`MAPS_API_KEY` secret** was updated
  to the same new key for CI release builds.

## Console changes (not in git at all)

- **Google Maps key migrated** out of a stray "My First Project" GCP project into the Macaco project
  (`macaco-499016`). New key "Macaco Maps SDK Android" is restricted to **Android apps** (debug +
  upload + Play-signing SHA-1s) and **Maps SDK for Android only**. Billing (free-trial credit) is now
  linked to `macaco-499016`. The old key still works (restricted to the app) and will be deleted after
  a release with the new key rolls out.
- **Firebase Android API key** (`google-services.json` `current_key`) is now restricted to **Android
  apps** with the three app SHA-1s. It still gates Auth + Firestore; the SDK sends package+cert so this
  is safe. **Implication for Code:** if you add a new signing config / build variant / CI key with a
  *different* SHA-1, Google Sign-In, Firestore, and Maps will fail for that build until the new SHA-1
  is added to these keys in the console. The three current SHA-1s are the ones already registered in
  Firebase for Google Sign-In.
- **Firebase Browser API key** left as-is intentionally (it backs the web-hosted password-reset /
  email-verification action links; restricting it would break those).
- **Firestore security rules updated + published.** The orphaned `/shared_trips` match block (leftover
  from the retired trip-share feature, `docs/DONE/code-brief-retire-trip-share-link.md`) was **removed**.
  Rules are now exactly:
  ```
  rules_version = '2';
  service cloud.firestore {
    match /databases/{database}/documents {
      match /users/{userId}/{document=**} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }
  }
  ```
- 2-Step Verification was enabled on the Firebase-owning Google account (unrelated to code).

## Pending briefs (written this session, NOT yet implemented — for Code to pick up)

- **`docs/code-brief-security-firestore-rules-in-repo.md`** — add `firestore.rules` + `firebase.json`
  to the repo so the rules above are version-controlled (they currently live only in the console). Safe,
  small, recommended next.
- **`docs/code-brief-security-app-check.md`** — *(optional hardening)* add Firebase App Check with Play
  Integrity. Larger lift; defer OK.

## What Code does NOT need to change

Nothing is broken and no code edit is required by these changes. The app builds and runs as-is. This
note is purely so the console-side restrictions and the new Maps key are understood if a build issue
(sign-in / map failing) ever traces back to a signing-cert or API-key mismatch.
