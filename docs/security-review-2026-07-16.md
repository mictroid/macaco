# Macaco — Security Review (2026-07-16)

Deep-dive security review of the Macaco Android app, its Firebase/GCP backend, the git repo,
and its external surface. Scope agreed with Michael: codebase, Firestore/Drive rules, secrets in
git history, and external surface — prioritizing (1) user-data leaks, (2) credential/key exposure,
(3) account takeover/auth, (4) GDPR/privacy. Live console checks were done against project
`macaco-499016` signed in as `houseofmmminq@gmail.com`.

**Bottom line:** No active data-leak or credential-exposure vulnerability was found. The single most
important control — Firestore per-user isolation — is correctly enforced. One high-severity gap (no
2FA on the backend-owning Google account) was **fixed live during this review**. The remaining items
are hardening: unrestricted API keys, one orphaned public-read rule left over from a retired feature,
and a few low-severity notes.

---

## Severity summary

| # | Severity | Finding | Owner | Status |
|---|----------|---------|-------|--------|
| H1 | High | Backend-owning Google account (`houseofmmminq@gmail.com`) had no 2-Step Verification | You | ✅ Fixed during review |
| M1 | Medium | Firebase Android + Browser API keys have **no application restriction** (Google-flagged) | You (Cloud console) | Open |
| M2 | Medium | Google **Maps API key** (billable) — restrictions unverified; not among this project's keys | You (Cloud console) | Open — verify |
| M3 | Medium | Orphaned `/shared_trips` Firestore rule allows **unauthenticated public read** (leftover from a retired feature) | You (Firebase console) | Open |
| L1 | Low | Firestore rules are **not version-controlled** (live only in the console) | Code brief | Open |
| L2 | Low | Release keystore password stored in plaintext locally (gitignored, not leaked) | You | Note |
| L3 | Low | `allowBackup="true"` (mitigated — sensitive prefs excluded) | — | Accepted |
| L4 | Low | Privacy policy discloses location only lightly vs. the photo-roll scanner's use | You / copy | Optional |
| I1 | Info | App lock is a UI gate only; local `is_purchased` fallback is client-controlled | — | By design |

---

## What's solid (verified clean)

- **Firestore per-user isolation — the top concern — is correctly enforced.** Live rules read
  from the console:
  ```
  match /users/{userId}/{document=**} {
    allow read, write: if request.auth != null && request.auth.uid == userId;
  }
  ```
  A signed-in user can only ever read or write their own `uid` path. The client-side `uid` used in
  `CloudEntrySync` / `FirebaseAuthRepository` is backed by a real server-side boundary — no user can
  reach another user's entries by changing the path. ~11 user documents exist, each with its own
  `entries` subcollection, all under this rule.
- **No secrets in git history.** Full-history scan (`git log --all -S …`) found no keystore, no
  service-account JSON, no private keys, no passwords ever committed. Only a `keystore.properties.template`
  with `CHANGE_ME` placeholders is tracked. `.gitignore` correctly covers `keystore.properties`,
  `*.jks`, `*.keystore`, `play-service-account.json`, and `local.properties`.
- **CI secret handling is good practice.** `.github/workflows/release.yml` pulls all secrets from
  GitHub Actions secrets and authenticates to Google via **Workload Identity Federation** (no
  long-lived service-account key on disk). No hardcoded secrets anywhere in `.github/`.
- **Auth flows are sound.** Account deletion **re-authenticates first** (before wiping data), so a
  stale session can't half-delete; sign-out clears the cached GMS Google account; error messages are
  user-facing and don't leak internals.
- **Minimal, safe logging.** Only 9 `Log.*` calls in the whole app; the few that reference entries
  log only the entry UUID and exception — never titles, locations, photos, tokens, or emails.
- **Release build hardening.** `isMinifyEnabled` + `isShrinkResources` are on (R8). No cleartext
  HTTP, no custom `TrustManager`/hostname-verifier bypass anywhere in the app.
- **Dependencies are current** (Firebase BOM 33.7.0, RevenueCat 8.10.0, Coil 2.7.0, Compose BOM
  2026.02.01, etc.) — no obviously vulnerable versions.
- **Firebase Storage is not enabled** (project is on the Spark plan). There is no Storage bucket and
  therefore no Storage security rules to misconfigure. Entry photos back up to the **user's own
  Google Drive**, not a shared server bucket.
- **Location stays on-device.** `PhotoRollScanner` reads GPS EXIF, clusters, and reverse-geocodes
  locally; location leaves the phone only inside an entry the user chooses to save. `ACCESS_MEDIA_LOCATION`
  is runtime-requested only when the feature runs.
- **Manifest hygiene.** Only `MainActivity` is exported (launcher, `singleTop`); receivers, the
  FileProvider, and the locale service are all `exported="false"`. `AD_ID` permission is stripped to
  match the Play data-safety declaration.

---

## Findings & remediation

### H1 — Backend-owning Google account had no 2FA  ✅ Fixed during review
**Severity: High (resolved).** The `houseofmmminq@gmail.com` account owns the entire `macaco-499016`
Firebase/GCP project — Firestore data, Auth, security rules, and the linked Play publishing and
RevenueCat service accounts. Google's own "Enable MFA to gain access to Firebase" wall showed it had
no 2-Step Verification. A single leaked/phished password on that account would have handed an attacker
full control of the backend and every user's data. **You enabled 2SV live during this session**
(authenticator + passkeys + backup codes confirmed). No further action beyond keeping the backup
codes somewhere safe. Consider enabling 2FA on the other Google accounts that can touch the project
too.

### M1 — Firebase API keys have no application restriction
**Severity: Medium.** Both API keys in the project — "Android key" and "Browser key" (auto-created by
Firebase) — have **Application restrictions = "None"**. The Cloud console flags both:
*"this key can currently be used with any application. Restrict where it can be used to improve
security."* API restrictions are set (scoped to ~25 Firebase APIs), which is good, but with no
app/package lock the key (which ships inside the APK and is trivially extractable) can be used from
anywhere. This does **not** expose user data — Firestore is protected by the rules + Auth above — but
it allows abuse of enabled APIs such as Identity Toolkit (triggering password-reset / verification
emails, account-creation attempts) against your quota.

**Fix (you, ~5 min):** Cloud console → APIs & Services → Credentials →
- **Android key** → Application restrictions → **Android apps** → add package `com.houseofmmminq.macaco`
  with your **release SHA-1** and your **debug SHA-1** (both, or Google Sign-In / Firebase break in
  debug builds). Get the debug SHA-1 with:
  `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
- **Browser key** → if nothing web-facing uses it, restrict to **Websites** with only your GitHub
  Pages origin (`mictroid.github.io`), or leave API-restricted. Don't delete it — Firebase auto-created
  it and may reference it.
- Give it ~5 minutes to propagate, then smoke-test a debug build (sign-in, save an entry) before
  shipping.

### M2 — Maps API key (billable) — restrictions unverified
**Severity: Medium.** The app injects a Maps key (`MAPS_API_KEY` from `local.properties`, value
`AIzaSy…nCP5s`) into the manifest for the Maps SDK. That key is **not** one of the two keys in
`macaco-499016`'s credential list, and neither project key includes "Maps SDK for Android" in its API
list — so the Maps key lives elsewhere and its restrictions could not be verified in this session.
Maps usage is **billed**, so an unrestricted Maps key is the classic billing-abuse vector: someone
extracts it from the APK and runs up your Maps bill.

**Fix (you):** Find the project that owns `AIzaSy…nCP5s` (Cloud console → search the key), then set
**Application restrictions → Android apps** (package + release/debug SHA-1) **and** **API restrictions
→ Maps SDK for Android only**. Also confirm a billing budget/alert is set on whichever project bills
Maps.

### M3 — Orphaned `/shared_trips` rule allows unauthenticated public read
**Severity: Medium.** The live Firestore rules still contain:
```
match /shared_trips/{shareId} {
  allow read: if resource.data.expiresAt == null
    || request.time.toMillis() < resource.data.expiresAt;
  allow create: if request.auth != null && request.resource.data.ownerUid == request.auth.uid;
  allow update, delete: if request.auth != null && resource.data.ownerUid == request.auth.uid;
}
```
The `read` rule has **no auth check** — anyone can read any `shared_trips/{shareId}` doc (permanently,
if `expiresAt` is null). This was for the view-only trip-share links feature, which was **retired from
the app** (`docs/DONE/code-brief-retire-trip-share-link.md`) — no current code writes to or reads this
collection. So the collection is empty today and there's no active leak, but the public-read grant is
**dormant attack surface**: if anything ever lands in `shared_trips`, it's world-readable. The
write rules themselves are well-formed (owner-only), so the risk is purely the leftover public read on
a collection the app no longer uses.

**Fix (you):** Delete the entire `match /shared_trips/{shareId} { … }` block from the console rules
(and adopt the version-controlled rules in L1 so this can't silently drift again). Corrected rules are
in `docs/code-brief-security-firestore-rules-in-repo.md`.

### L1 — Firestore rules are not version-controlled
**Severity: Low.** The rules exist only in the Firebase console — not in the repo — so there's no
history, no review, and no way to tell if they've been changed. The retired-feature rule (M3) drifting
un-noticed is exactly the failure mode this causes. **Fix:** commit `firestore.rules` + `firebase.json`
to the repo and deploy from source. See the brief `docs/code-brief-security-firestore-rules-in-repo.md`.

### L2 — Release keystore password stored in plaintext locally
**Severity: Low (note).** `keystore.properties` at the repo root holds the upload-key store/key
passwords in plaintext. It is **gitignored and was never committed** (history-verified), so this is not
a leak — it's the normal local-signing setup. Two hygiene notes: (a) make sure that password is
**unique** to this keystore and not reused for any account or email; (b) the upload `.jks` + this
password together are what sign your Play releases, so keep the keystore backed up (per `CLAUDE.md` it's
mirrored to Drive) and the password out of any synced plaintext note.

### L3 — `allowBackup="true"`
**Severity: Low (accepted).** Cloud backup is on, but `backup_rules.xml` / `data_extraction_rules.xml`
correctly **exclude** the DataStore prefs (which hold the app-lock flag and the local purchase flag),
so app-lock can't silently re-arm on a restored device and a stale purchase flag can't ride along. The
only other backed-up app data is the Firestore local cache — which is the user's *own* data on their
*own* Google backup. No cross-user exposure. No change needed.

### L4 — Privacy policy location disclosure is light
**Severity: Low (optional).** The privacy policy discloses Firebase, Drive, Analytics, Crashlytics, and
photos well, but mentions **location only twice** — thin relative to the photo-roll scanner reading GPS
EXIF (`ACCESS_MEDIA_LOCATION`) to suggest un-journaled trips. Processing is on-device, but for Play
data-safety accuracy consider a sentence making explicit that photo location metadata is read on-device
to power trip suggestions and is only stored/synced if the user saves an entry.

### I1 — App lock & local purchase flag (by design)
**Info, no action.** The app lock is a UI gate (biometric prompt → `onUnlocked`), not a cryptographic
data lock — appropriate for its threat model, since entry data is protected by Auth + Firestore rules,
not by the lock. The local `is_purchased` DataStore flag is a client-controlled fallback used only when
RevenueCat isn't configured; in production RevenueCat is the source of truth. Neither protects other
users' data, so neither is a security risk.

---

## Remediation checklist

**You (console / account) — highest leverage first:**
- [x] Enable 2FA on the backend-owning Google account (H1) — done this session
- [ ] Restrict the Firebase **Android key** to Android apps (package + release & debug SHA-1) (M1)
- [ ] Restrict/limit the Firebase **Browser key** (M1)
- [ ] Locate and restrict the **Maps API key** to Android app + Maps SDK only; set a billing alert (M2)
- [ ] Delete the orphaned `/shared_trips` block from the Firestore rules (M3)
- [ ] Confirm the keystore password is unique and backed up (L2)
- [ ] (Optional) Expand the privacy policy's location disclosure (L4)

**Code (Claude Code briefs written alongside this report):**
- [ ] `docs/code-brief-security-firestore-rules-in-repo.md` — version-control Firestore rules + remove
  the orphaned `shared_trips` block (L1 + M3)
- [ ] `docs/code-brief-security-app-check.md` — *(recommended hardening)* add Firebase App Check with
  Play Integrity, the strongest code-side mitigation for the extractable-API-key risk (M1/M2)

---

## What still needs runtime / manual verification (not checkable from static review)

- The **Maps key's** owning project and restrictions (M2) — needs a console lookup you can do.
- Whether a **billing budget/alert** exists on any project with billing enabled.
- That restricting the keys (M1) doesn't break debug Google Sign-In — smoke-test a debug build after.
- 2FA on any **other** Google accounts with access to the project.

*Static review only for the codebase: this did not build or run the app, and did not perform live
penetration testing against Firestore. The rule verification above was done by reading the live
published rules in the console, which is authoritative for the isolation guarantee.*
