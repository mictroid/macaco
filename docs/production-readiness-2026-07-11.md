# Macaco — Production Readiness Review, 2026-07-11 (vc65 / v1.6)

Companion to `qa-report-2026-07-11.md` (the static code audit). This document covers what that
report deliberately left out: the Play Console / policy side of going to production, the go/no-go
list, and the on-device test plan. Together the two documents are the full pre-production QA pass.

**Verdict:** no policy blocker found. The path to production is: ship B1 + the two hygiene briefs,
run the on-device pass below, then submit the production-access application (draft already in
`docs/production-access-application.md`).

---

## 1. Findings re-verified today against source (vc65, commit `8feef6e`)

Every headline claim in `qa-report-2026-07-11.md` was independently re-checked against the live
source before this review; all confirmed:

- **B1 (BLOCKER)** — premature `if (needed.isEmpty()) return@withContext` at
  `DrivePhotoSync.kt:250`, before videos are evaluated. Confirmed present. Fix brief is pending:
  `code-brief-drive-video-download-fix.md`.
- **S1/S2 (locales)** — base catalogue 368 keys; `de` missing 18, `es/fr/it/ja/nl/pl/pt/zh-rCN`
  missing 20 each, `sv` missing 47. Confirmed by key diff. Full translation brief now written:
  **`code-brief-paywall-locale-translations.md`** (all 10 locales, translations included).
- **L2 (release logs)** — three `Log.d("MapCamera", …)` at `MapScreen.kt` 304/325/348. Confirmed.
- **L5 (backup rules stubs)** — both XML files still IDE samples. Confirmed.
- L2 + L5 + L6 (comment drift) are now bundled in one brief: **`code-brief-release-hygiene.md`**.

Also re-confirmed sound: manifest (AD_ID stripped with `tools:node="remove"`, media permissions
correctly SDK-gated, fingerprint `uses-feature required=false`, VIDEO_CAPTURE `queries`), release
signing via `keystore.properties`, versioning (vc65 / 1.6), min 24 / target+compile 36.

## 2. Play Console production checklist

| Area | Status | Notes |
|------|--------|-------|
| Target API level | ✅ | targetSdk 36 — comfortably above the current Play minimum. |
| Closed-testing requirement (personal dev account: 12 testers / 14 days) | ✅ prerequisite met, application drafted | `docs/production-access-application.md` has all answers pre-written. Update the tester numbers/dates to actuals on the day you apply. |
| Privacy policy URL | ✅ | Live at `mictroid.github.io/macaco/privacy-policy.html`, linked in-app (Login + Help & About) and referenced from the listing docs. |
| Account deletion (policy: in-app + declared) | ✅ / ⚠️ declare | In-app Profile → Delete Account wipes Firestore + Auth. In the Data safety form's "Account deletion" question, provide a URL — the privacy policy's "Your rights" section (§6) documents the in-app path and the support email; that page is an acceptable URL. Optional polish: add an anchor (`privacy-policy.html#delete`) so the link lands on the section. |
| Data safety form | ⚠️ verify against §3 below | Must match what the app actually does — see the declaration cheat-sheet. |
| Ads declaration | ✅ | No ads; AD_ID permission stripped in manifest, so the "no advertising ID" declaration stays consistent (Play cross-checks the manifest). |
| In-app purchases | ✅ | RevenueCat products (monthly w/ 7-day trial, annual, lifetime) configured; listing must tick "in-app purchases". Trial/cancel terms are shown on the paywall. |
| Content rating questionnaire | ✅ (re-check UGC answers) | Journal content is private per-user, never shared publicly through the app — answer the UGC section accordingly (private diary, no user-to-user content exposure). |
| Login credentials for review | ⚠️ action | App is login-gated: provide a working test account (email/password) in App content → App access, or reviewers/pre-launch report can't get past LoginScreen. |
| Release signing / upload | ✅ | Canonical path is the WIF GitHub Actions workflow (`release.yml`) → closed testing; promoting to production happens in the Console. Play App Signing manages the release key. |
| Pre-launch report | ⚠️ advice | After promoting, check the PLR run: with a login-gated app expect mostly LoginScreen screenshots unless test credentials are configured. Watch for crashes on low-RAM devices. |
| Store listing | ✅ | `docs/play-store-listing.md` + ASO doc are done; 11-locale listing text exists. Paywall localization (S1) should ship first so store locale ≠ half-English app. |

## 3. Data safety form — declaration cheat-sheet

What the code actually collects (declare exactly this, nothing more):

- **Personal info → Email address + Name:** collected via Firebase Auth (Google / email+password).
  Required for app functionality (account). Not shared with third parties for their own purposes.
- **Photos and videos:** entry media. Stored in the user's own device gallery and the user's own
  Google Drive (`DRIVE_FILE` scope — app-created files only). Journal text/locations/moods go to
  Firestore under the developer's Firebase project → declare as "collected", encrypted in transit,
  user can request deletion (in-app deletion exists).
- **App activity / diagnostics:** Firebase Analytics + Crashlytics are auto-initialized → declare
  crash logs + diagnostics (and analytics interaction data) as collected, not linked to identity
  beyond what Firebase does by default.
- **Advertising ID: NOT collected** (manifest strips it — keep declaration in sync).
- **Data deletion:** in-app account deletion removes Firestore entries + user doc + Auth account.
  Note honestly: media in the user's own Drive/gallery is user-controlled and not deleted by us
  (QA L4 — defensible, but the privacy policy already says "delete everything at any time"; §6
  wording is fine since it points at the in-app flow which deletes everything *we* hold).

## 4. Go / no-go list

Must happen before promoting to production, in order:

1. **Ship B1** (`code-brief-drive-video-download-fix.md`, pending) — video-only entries currently
   break the cross-device/reinstall promise of the headlined feature. One-line fix.
2. **Ship the stale-URL fix** (`code-brief-adventure-reel-outro-card-v3.md`, pending) — corrects
   the wrong `REEL_SHARE_URL` that shipped in vc65.
3. **Ship `code-brief-paywall-locale-translations.md`** — conversion-critical paywall renders
   half-English in all 10 translated markets today.
4. **Ship `code-brief-release-hygiene.md`** — release logs, backup rules, comment drift.
5. **On-device pass (§5)** on the resulting build — several vc63–65 changes shipped with on-device
   verification waived; production is the wrong place to discover a regression.
6. Play Console: test credentials in App access, Data safety per §3, then submit
   `production-access-application.md`.

Explicitly *not* gating production: R8/minify stays off (standing decision — larger APK, no
obfuscation; revisit post-launch), Drive orphan cleanup on entry delete (L3, FAQ line instead),
CLAUDE.md freemium drift (L7, docs only).

## 5. On-device test plan (one full pass on the release-candidate build, closed track)

Run on one phone (API 33+) end-to-end; repeat the starred items on a second device or after
reinstall — that's what validates sync. Items marked **[B1]** specifically verify the blocker fix.

**Install & gates**
1. Fresh install from the closed track → onboarding → splash. No crash, correct branding.
2. Google Sign-In (grants Drive scope in the same consent) and, separately, email+password
   sign-up. ToS/privacy links on LoginScreen open.
3. Free tier: create 3 entries → 4th attempt hits the freemium paywall.

**Paywall & billing (Play track build, real account)**
4. Paywall shows all three plans with real prices; trial wording on Annual. Buy monthly (or use a
   license-tester account) → premium unlocks. `Restore purchase` works after clear-data/reinstall.
5. Sign out → sign into a second account → premium does NOT leak across accounts.

**Entries & media**
6. Create entries: photo-only, video-only, photo+video (3 clips, trim one), text-only. EXIF-rotated
   camera photo displays upright.
7. Edit an entry (add/remove media), delete an entry — list and detail stay consistent, no blank
   screen.
8. ★ Second device / reinstall: all entries appear; photos restore from Drive; **[B1] the
   video-only entry's clips download and play**.

**Drive & backup**
9. Settings → Drive sync: connected state shows account; full `syncAll` completes; disconnect and
   reconnect (email/password user connects Drive from Settings).
10. Backup export → clear data → import: entries + photos + **[B1] videos of video-only entries**
    all present; media re-uploads to Drive afterwards.

**Sync robustness**
11. Airplane mode: create/edit an entry offline → goes online → syncs without duplicate/ loss.
    Sync errors surface as snackbars, not crashes.

**App lock, reminders, misc**
12. Enable app lock → background >30s → lock screen; biometric and device-credential paths.
    After reinstall+cloud-restore the app must start UNLOCKED (backup-rules change from the
    hygiene brief).
13. Reminders: enable, interval change; notification actions (+ Add Memory / snooze) work.
14. Map: opens without flash-of-default-ocean, pins correct, marker tap opens entry, collapse
    behavior on pan.
15. Adventure reel: generate + share; QR/short link resolves to the *corrected* share URL (v3
    brief).
16. Account deletion: Profile → Delete Account (re-auth prompt) → lands on LoginScreen; second
    device signs out of a now-dead account gracefully.

**Locale & form factor**
17. Set device to German and Swedish: paywall, map, reminders, share sheet fully translated (the
    translations brief); no clipped layouts from longer strings.
18. Tablet + landscape rotation spot-check on Journal, Detail, Settings, Profile. Rotate during
    entry creation — no state loss/crash.
19. In-app update flow: install previous vc from the track, publish new vc → flexible update
    snackbar "Restart" works.

## 6. Ship sequence (recommended)

vc66 = B1 + reel-outro-v3 + release-hygiene (small, low-risk batch) → vc67 = locale translations
(mechanical but large diff) → full §5 pass on vc67 → Data safety + test credentials in Console →
submit production application.
