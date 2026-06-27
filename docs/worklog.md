# Worklog

Running log of notable work sessions. Newest first.

## 2026-06-27 — 2-brief batch (dark-mode card borders + map-camera-v6), local

Both Cowork briefs verified vs live source first; `assembleDebug` SUCCESSFUL. Not yet
bumped/pushed/published. User confirmed vc31/36/37 working on-device except the map-camera fit —
which `map-camera-v6` here addresses.
- **dark-mode-card-borders** (`JournalListScreen`): `EntryCard` + "On This Day" small card now get a
  `0.5.dp outlineVariant` border in dark mode only (`colorScheme.background.luminance() < 0.5f`), so
  cards stop bleeding into the near-identical dark `surface`/`background`. Light mode unchanged; works
  across all 7 themes. Added `foundation.border` + `ui.graphics.luminance` imports.
- **map-camera-v6** (`MapScreen`): replaced v5's bounds-fit + zoom-table with most-recent-entry
  centering — `entries.sortedByDescending { createdAt }` → first geocoded location →
  `newLatLngZoom(target, 6f)`. Fixes the cross-continental regression where a >90° lng span fell
  through to zoom 2 (world view). Removed now-unused `CameraUpdate` + `LatLngBounds` imports.
- Visual verify deferred to the A53 after ship (emulator blocked by the login+premium gate; real
  RevenueCat key means a sideloaded debug APK can't pass the paywall). See
  `docs/worklog-2026-06-27.md` for full per-brief detail.

> **NEXT (2026-06-23):** vc33/1.5 is **published to closed testing** (WIF run `28010450490`
> SUCCESS). **Only open item is on-device verification** of the four shipped items: tablet-ui
> `sw600dp+` layouts (needs the **Tab A9+**), backup ZipFile extraction (truncated-Drive-download
> error path), map global fit (multi-continent zoom), and profile Google-photo fallback. Also still
> unverified from vc31: large-backup import progress bar + Drive 401 banner clearing on reconnect.
> Still open from before: enable **R8** with keep rules before production.
> (Per-vc detail lives in the dated `docs/worklog-2026-06-23.md` mirror + the Drive backup; this
> rolling log skipped vc22–vc31 — see those files for the gap.)

## 2026-06-23 — 3-brief batch #2 (local, NOT yet shipped)

`entry-detail-image-height`, `forgot-password`, `map-camera-v5` implemented + committed to `master`
(not yet bumped/pushed/published). All verified vs live source first; `assembleDebug` SUCCESSFUL.
- **entry-detail-image-height** (`EntryDetailScreen`): hero photo now scales to 52% of screen height
  on `sw600dp+` tablets (was hardcoded 260dp), phones unchanged.
- **forgot-password** (`AuthRepository`/`FirebaseAuthRepository`/`MockAuthRepository`/`JournalViewModel`/
  `LoginScreen` + strings ×11): Firebase password-reset flow — "Forgot password?" button in sign-in
  mode + hedged success banner. Deviations: had to add the method to `MockAuthRepository` too (build
  break), and escape the French apostrophe (`d\'abord`) for aapt.
- **map-camera-v5** (`MapScreen`): replaced `newLatLngBounds` (threw on un-laid-out tablet maps) with
  layout-independent center+zoom via `newLatLngZoom`; gated scrim-drop on move success.

See `docs/worklog-2026-06-23.md` for full per-brief detail.

## 2026-06-23 — vc32 + vc33 shipped (6 Cowork briefs + tablet UI)

Two release sessions, both via the WIF `release.yml` workflow (minimal-push: dispatch without
watching, confirm later with `gh run list`). All briefs verified vs live source first — clean, no
stale-clone drift either round.

**vc32** (run `28004897407`, SUCCESS) — 3-brief batch, commit `2bfd11a` + bump `2245c32`:
- **backup-import-zlib-fix** (`JournalBackup`): two-phase import — download the SAF/Drive source to a
  local `tempDir/source.zip` first (reusing `CountingInputStream`), then open *that* as
  `ZipInputStream`. Fixes `ZLIB inflate error` from decompressing directly over a chunked Drive stream
  (`closeEntry()` stalls mid-drain). Removed now-unused `BufferedInputStream` import.
- **map-default-position-v2** (`JournalViewModel` + `MapScreen`): fixed v1 regression where the camera
  locked onto the *first* geocoded result. Added `geocodingComplete: StateFlow<Boolean>`; camera
  `LaunchedEffect` keys on it (not `geocodedLocations`) so it fits all locations once geocoding ends.
- **profile-photo-account-switch** (`JournalViewModel`): one-liner — clear `profilePhotoUri` on
  sign-out so a new account doesn't inherit the previous user's custom photo.

**tablet-ui** (commit `4d2f41d`, rode along into vc33) — four `sw600dp+` layout fixes gated on
`LocalConfiguration.current.screenWidthDp >= 600`: TagChips ellipsis + `widthIn(max=100.dp)`,
entry-list 80dp horizontal pad, EntryPhotoArea 200dp, New/Edit form 100dp pad. No new deps.

**vc33** (run `28010450490`, SUCCESS) — 3 follow-up briefs, commit `0188ade` + bump `a1b6fd2`,
bundling the local-only tablet-ui commit:
- **backup-import-zlib-fix-v2** (`JournalBackup`): switched extraction from `ZipInputStream` →
  `java.util.zip.ZipFile`, which reads the central directory on open, so a truncated `source.zip`
  throws `ZipException` *immediately* (clear "incomplete download — open in Files app" message) instead
  of an opaque mid-stream ZLIB error. Removed now-unused `ZipInputStream` import.
- **map-default-position-v3** (`MapScreen`): removed the ±30° geographic-median outlier-rejection
  block — it dropped all-but-one location for globe-spanning travellers (Argentina/Iceland/Japan/
  Germany → only Germany survived → zoom-to-single). `latlngs` now built straight from the Null-Island
  filter.
- **profile-google-photo-fallback** (`ProfileScreen`): three-tier avatar — `profilePhotoUri ?:
  user.photoUrl` Coil model, initials only when both null; `error = rememberVectorPainter(Icons.
  Default.Person)` for unreachable Google URLs (offline/revoked).

`default.txt` refreshed for vc33 (tablet layouts / Drive restore / map fit / Google photo fallback).
Nothing device-verified yet. The detailed dated worklog is `docs/worklog-2026-06-23.md` (+ Drive
backup `worklog-2026-06-23.md`).

## 2026-06-20 — Four more Cowork briefs (delete blank-screen, map flash, hint readability, mood keyboard)

All four verified against live code first, build green.

- **Delete → blank screen** (`NavGraph`): `EntryDetail.onDelete` did `deleteEntry()` then
  `popBackStack()`, but Firestore's local cache drops the entry so fast the
  `entries.none { it.id == id }` `LaunchedEffect` popped first → the explicit pop then removed
  `JournalListScreen` too → blank screen. Fix: pop **before** deleting (composable leaves the stack,
  so the LaunchedEffect can't fire a second pop). One-line reorder.
- **Map opens on default position** (`MapScreen`): camera inits at `LatLng(20.0, 0.0)` zoom 2 and the
  arbitrary default was visible until geocoding finished. Added `geocodingReady` flag + an opaque
  scrim with a `CircularProgressIndicator` over the map until the first geocoded point arrives.
  (Brief's geography label was wrong — 20°N/0°E is the Sahara, not "ocean" — cosmetic, fix unaffected.)
- **Hint readability** (`NewEditEntryScreen.HintRow`): `primary.copy(alpha=0.6f)` faded into the
  watermark; switched to `onSurfaceVariant` over a translucent `surface` pill. Imports already present.
- **Mood dialog keyboard** (`MoodSelector`): the custom-mood `AlertDialog` field didn't auto-focus;
  added a `FocusRequester` + `LaunchedEffect { requestFocus() }` so the keyboard (and emoji panel)
  opens immediately.

**`code-brief-share-app-branding.md`** (#5) — user chose **copy only**: refreshed `share_app_subject`
(🐒 prefix) + `share_app_text` plural blurb (chattier, travel emoji) across all 11 locales. The
brief's second half — switching the share intent from `text/plain` to a generated icon-on-teal
image card (+ FileProvider `cache-path`) — was **declined** (flat-icon card, modest payoff, and
`image/png` shares degrade on non-chat targets); revisit later with a properly designed card if
wanted. `shareApp()`/`file_paths.xml` unchanged. Build green; none of these five committed/released.

## 2026-06-20 — Fix: feedback email body/subject empty in Gmail (Cowork brief)

Follow-up to the vc21 templated-email feature: on Gmail the feature/bug-report (and plain contact)
emails opened blank. Cause: **Gmail ignores `EXTRA_SUBJECT`/`EXTRA_TEXT` on `ACTION_SENDTO`** and
only reads `mailto:` URI query params (other clients honour the extras, which is why it looked fine
elsewhere). Fix (`AppActions.kt`): `sendEmail()` and `contactSupport()` now encode subject (and body)
directly into the `mailto:` URI via `Uri.encode`, dropping the extras — the URI form populates all
clients. Bodies are short templates so URI length isn't a concern. Build green. Ships as vc23.

## 2026-06-20 — Fix: App Lock bypassed on cold start (OEM background-kill)

Tester on the A53 reported reopening Macaco didn't trigger the biometric lock. Root cause (not a
regression from the API<30 biometric work — that only touched `AppLockScreen`, and the A53 is API
30+): App Lock only engaged on a **warm resume**. `JournalViewModel._isAppLocked` is in-memory and
starts `false`, and NavGraph's re-lock fires on `ON_RESUME` only if the same process survived
backgrounded (`pauseTime > 0 && elapsed > 30s`). On a Samsung-style background-kill, reopening is a
**cold start** — fresh process, `pauseTime` reset, `isAppLocked=false` — so the app came up unlocked
and the lock was bypassed entirely. Fix: in the ViewModel `init`, read the persisted `appLockEnabled`
once (`first()`) and set `_isAppLocked = true` if enabled, so a fresh process comes up locked. Runs
once per process (ViewModel survives config changes); the lock screen still shows only after the
login + purchase gate. Build green; not yet released.

## 2026-06-20 — Fix: onboarding Skip button was dead (z-order)

A closed-testing user reported the intro **Skip** button doesn't work. Root cause: in
`OnboardingScreen`'s `Box`, the Skip `TextButton` (TopEnd) was declared **before** the full-screen
`HorizontalPager`, so the pager was drawn on top in the Box z-order and intercepted the taps — Skip
was visible (pager pages are transparent) but never received touches. The bottom Next/Get-Started
button worked precisely because it's declared *after* the pager. Fix: moved the Skip block to be the
**last** child of the `Box` so it sits on top; no logic change. Note: Skip ends the intro slides
only — it doesn't bypass sign-in (last slide says "Sign in to start"). Build green; not yet released.

## 2026-06-20 — Firebase Dynamic Links shutdown: confirmed no-op for Macaco

Firebase console surfaced the Dynamic Links shutdown notice (the FAQ section on *email link
authentication* impacts). **Checked — Macaco is unaffected, no action needed.** The app uses classic
email/password auth (`signInWithEmailAndPassword` / `createUserWithEmailAndPassword` in
`FirebaseAuthRepository.kt`), **not** the passwordless email-link flow (`sendSignInLinkToEmail` /
`signInWithEmailLink`) that relied on Dynamic Links. There's no `firebase-dynamic-links` dependency,
no `ActionCodeSettings`/`DynamicLink` usage anywhere, and the app never calls `sendPasswordReset` or
`sendEmailVerification` (so no email *action* links either). All three sign-in paths (Google, Apple,
email/password) keep working. The console message is a generic project-wide notice — safe to dismiss.

## 2026-06-20 — vc21 published to closed testing (six briefs)

Committed the six 2026-06-20 Cowork briefs (`508d732`) + versionCode 20→21 (`65e8a51`, versionName
stays 1.5), pushed `master` (`origin` was actually at `7cd1eec` — `49f7fa4` hadn't been pushed
either, so all three local commits went up together), and dispatched the WIF `release.yml` workflow
**without watching** (per the user's "minimal push" convention — see [[play-publish-wif]]). Run
`27866944480` completed **success** in 5m45s → vc21 bundle on the `alpha` (closed-testing) track.
Release backlog clear; not yet device-verified.

## 2026-06-20 — Two more Cowork briefs: API<30 biometrics + mood-selector refresh

Two further briefs, both verified against the live repo before implementing (build green).

- **Biometrics on API < 30** (`AppLockScreen`): `isBiometricAvailable()` and `showBiometricPrompt()`
  used the combined `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` authenticator mask, which is only valid
  on API 30+ — on the Galaxy S8 (API 28) `canAuthenticate` returns `ERROR_UNSUPPORTED` even with a
  fingerprint enrolled, permanently blocking the App Lock toggle. Both functions now branch on
  `Build.VERSION.SDK_INT >= R`: 30+ keeps the combined mask; below 30 falls back to `BIOMETRIC_STRONG`
  only, with a mandatory `setNegativeButtonText` (cancel) on the prompt. The 30+ path is unchanged,
  so no regression to current devices. **Not device-verified** — the S8 ADB setup is still paused.
- **Mood selector refresh** (`NewEditEntryScreen` + `PreferencesManager` + `JournalViewModel` +
  `NavGraph`): replaced the place/feeling-mixed `MOODS` set with a feeling-first set (shipped the
  brief's emojis as-is per the user), and added user-defined custom moods — a gold "+" chip opens a
  dialog to paste an emoji, persisted in DataStore (`custom_moods`, "|"-delimited) via new
  `PreferencesManager.customMoods`/`addCustomMood` and `JournalViewModel.customMoods`/`addCustomMood`.
  **Deviation from the brief:** the brief collected `viewModel.customMoods` inside `NewEditEntryScreen`,
  but that screen takes no `viewModel` (state is passed from `NavGraph`, like `tripSuggestions`); wired
  `customMoods` + `onAddCustomMood` as screen params instead, fed from both NavGraph call sites. Also
  kept the existing tap-to-deselect behaviour in the new `MoodChip` (the brief's version dropped it)
  and used `SharingStarted.Eagerly` to match the codebase (brief said `WhileSubscribed`). Old presets
  still render on existing entries but aren't selectable chips; custom moods cover re-adding them.
  3 new `mood_add_custom_*` strings (×11). **Backward-compatible** — `mood` is still a plain emoji
  string in Firestore.

## 2026-06-20 — Four Cowork code briefs implemented

Implemented four independent UI/UX briefs that Cowork authored. Root issue surfaced first: Cowork's
mounted folder is the **stale `AndroidStudioProjects\MyApplication` clone**, so the first draft of
each brief used the old `com.example.myapplication` package and guessed-wrong signatures
(`SettingsRow`, `viewModel.restorePurchase()` as suspend, `StatItem(count=…)`, `VerticalDivider()`).
Verified every assumption against the live `macaco` repo, fed corrections back, and Cowork re-issued
the briefs against the live code before implementation. Also fixed stale claims in `CLAUDE.md`
(package is `com.houseofmmminq.macaco`, remote is `mictroid/macaco`) — commit `49f7fa4`.

- **Feedback emails** (`AppActions` + `HelpAboutScreen`): "Request a feature" / "Report an issue"
  now open with a templated body + an auto-appended device footer (app version, manufacturer/model,
  Android version) via new `requestFeature()`/`reportIssue()`/`sendEmail()`/`deviceFooter()`. The
  distinct subject lines already existed, so the brief was re-scoped from subjects → bodies.
- **Photo row clipping** (`NewEditEntryScreen`): swapped the `Row + horizontalScroll` for a
  `LazyRow(contentPadding = PaddingValues(end = 16.dp))` and moved the + button to the end, so the
  last item is no longer flush against the screen edge.
- **Trips counter** (`ProfileScreen`): added a Trips stat (distinct non-blank `tripName`) between
  Memories and Locations, hidden when 0 — builds on the v1.5 `tripName` field.
- **Restore purchase** (`SettingsScreen`): added a Subscription section with a "Restore purchase"
  `SettingsClickRow` calling the existing `viewModel.restorePurchase { … }` callback (Toast
  feedback), so premium is recoverable after a reinstall without going through PurchaseScreen.
  Updated the `help_faq_premium_broken_a` FAQ to point at Settings → Subscription.

Strings: added `profile_trips` + 5 `settings_*` keys to the default and all 10 locales (real
translations, since locales translate UI chrome); the FAQ string stays English-only like the rest
of the help section. `./gradlew assembleDebug` is green. **Not yet committed or device-verified.**

> **NEXT (2026-06-17):** vc15/1.4 shipping via CI, live on **closed testing** once that run
> completes — **not yet installed/verified on a device** (vc10 is still the last build actually
> checked on the A53, four releases behind). Install vc15 and confirm nothing regressed —
> especially the watermark's third sizing pass, and check that Settings/Help & About now show the
> versionCode ("1.4 (15)"). Still open from earlier: enable **R8** with keep rules before
> production; the paused **Galaxy S8+ ADB** setup (toggle USB debugging on the phone).

## 2026-06-17 — Show versionCode in-app — vc15 build

Settings and Help & About only displayed `versionName` ("1.4"), which hasn't changed across the
last several builds — no way to tell which versionCode is actually installed without ADB or Play
Console (came up directly while debugging the CI pipeline above). Both screens now show
`"${versionName} (${versionCode})"` e.g. "1.4 (15)", via `PackageInfoCompat.getLongVersionCode`.
Bumped `versionCode` **14→15**, shipped via CI.

## 2026-06-17 — Watermark v3 (tighter spacing) — vc14 build

Third Cowork pass on the same brief (`watermark-v2.md`, updated in place again): spacing tightened
another 40% (130dp×90dp → 78dp×54dp) and icon radius shrunk to a fixed 16dp (from 26dp); opacity
unchanged at a fixed 16%. As with the previous two passes, the brief's prose note and summary table
hadn't been updated to match the new code block (still said "3%"/"26dp"/"130dp×90dp") — went with
the code block as authoritative, consistent with how the prior two stale-doc mismatches were
resolved. Bumped `versionCode` **13→14**, shipped via the now-proven CI pipeline to closed testing.

## 2026-06-17 — Release pipeline moved to GitHub Actions via Workload Identity Federation (vc12 shipped)

Replaced the manual local `bundleRelease` + `play-service-account.json` upload flow with a
GitHub Actions workflow (`.github/workflows/release.yml`, manual `workflow_dispatch` trigger) that
authenticates to GCP without any long-lived key. **Working end-to-end as of vc12** — took seven CI
runs to get there; logging the failures since the fixes aren't obvious.

### Infra setup
- Created a Workload Identity Pool (`github-pool`) + OIDC provider (`github-provider`) in
  `macaco-499016`, trusting GitHub's OIDC issuer with an attribute condition locked to the repo.
  **Caught mid-setup:** the condition was first written for `mictroid/wanderlog`, but the actual
  GitHub repo had been renamed to `mictroid/macaco` (the old name just redirects) — the OIDC
  token's `repository` claim is the *current* name, so the trust condition and IAM binding both
  had to target `mictroid/macaco` or every exchange would fail the attribute condition silently.
- Granted that federated identity `roles/iam.workloadIdentityUser` *and* `roles/iam.
  serviceAccountTokenCreator` on the existing `play-publisher@macaco-499016.iam.gserviceaccount.com`
  service account, scoped via `principalSet://.../attribute.repository/mictroid/macaco`.
- Installed and authenticated `gh`/`gcloud` CLIs in the WSL2 dev environment to do all this (apt
  install needs an interactive terminal — `sudo` fails non-interactively over this session's
  command runner, so those steps had to run in a real terminal window).
- 5 new GitHub Actions repo secrets: `RELEASE_KEYSTORE_BASE64` (the upload `.jks`, base64),
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `MAPS_API_KEY`.

### Seven attempts to get the credential plumbing right
1. **403 "No credentials specified"** — gradle-play-publisher doesn't fall back to Application
   Default Credentials just because no JSON key was configured; it needs
   `useApplicationDefaultCredentials = true` explicitly (confirmed in GPP's README).
2. **403 PERMISSION_DENIED from Android Publisher API** — auth step impersonated play-publisher
   *and* GPP's ADC path scoped whatever ADC resolved to; the resulting token wasn't right. Switched
   to GPP's own `impersonateServiceAccount` (its dedicated, explicitly-`androidpublisher`-scoped
   impersonation path) and removed the action-level impersonation to avoid a double hop.
3. **Same 403** — explicit scoping didn't fix it either (this turned out to be a red herring; see
   #7). At this point switched strategy entirely: feed the WIF auth step's generated credentials
   file straight to `serviceAccountCredentials` (the pattern documented as working by
   `r0adkll/upload-google-play`), instead of going through any ADC auto-detection.
4. **New error** — `Error getting subject token from metadata server: PKIX path building failed`.
   Progress: this is the JVM itself making a live HTTPS call to GitHub's OIDC token endpoint to
   re-fetch the subject token at publish time, and Java's PKIX validator rejects the cert chain (a
   known JVM/PKIX interop gotcha — curl validates the same endpoint fine via the OS trust store;
   the server likely omits intermediate certs that browsers/curl fetch via AIA but Java doesn't).
5. Bumped to vc12 partway through (no prior attempt got far enough to actually upload, so this
   wasn't a real re-release, just a clean versionCode for continued testing) and added
   `--stacktrace` for better diagnostics — same PKIX error, now with the full stack trace.
6. **Fix for the PKIX error**: stopped using `google-github-actions/auth`'s default (URL-sourced)
   credential config entirely. Fetch the GitHub OIDC token with `curl` ourselves right before the
   build (OS trust store, known-good) and build a **file-sourced** `external_account` credential
   config pointing at the local token file. Java then only ever talks to `sts.googleapis.com` /
   `iamcredentials.googleapis.com` for the actual exchange — both trusted fine. This fixed the PKIX
   error, but resurfaced the exact same 403 from attempt #2/#3.
7. With the credential mechanics now confirmed solid across three different approaches all hitting
   the identical generic 403, the real cause was elsewhere: **play-publisher's Play Console "Users
   and permissions" grant**. User re-invited/fixed the service account's app-level release
   permission directly in Play Console → **next run succeeded**: vc12 uploaded and committed to
   the internal track in 5m43s.

### Track switched to closed testing (vc13)
Mid-verification, found the app under test had moved from internal testing to a **closed testing**
group — user had manually released vc12 there directly in Play Console. Closed testing's track
identifier in the Play Developer API (and GPP) is still `"alpha"` even though Play Console's UI
just calls it "Closed testing". Updated `track.set("alpha")`, bumped to vc13 (vc12 was already
consumed on this track from the manual release), and re-ran the pipeline to confirm it targets the
new track correctly: **succeeded** — `Updating [completed] release (com.houseofmmminq.macaco:[13])
in track 'alpha'`, 5m56s. Two clean runs in a row now; the pipeline is trustworthy going forward.

### Final working shape
- `.github/workflows/release.yml`: checkout → setup-java 21 → decode keystore + write
  `keystore.properties`/`local.properties` from secrets → fetch GitHub OIDC token via `curl` →
  build a file-sourced WIF credential config → `./gradlew publishReleaseBundle --stacktrace`.
- `app/build.gradle.kts`'s `play {}` block: `serviceAccountCredentials` from
  `play-service-account.json` locally, or from `$GOOGLE_APPLICATION_CREDENTIALS` in CI (the
  workflow exports that path after writing the WIF credential config). No long-lived key in CI at
  all; local dev is unaffected either way.

## 2026-06-17 — vc10 verified on-device; watermark v2 + vc11 build

### vc10 verified on the A53 (Play internal testing install)
User uploaded vc10 to Play and installed it on the A53. Connected over ADB and drove the app
directly (taps/swipes/`uiautomator dump`, which exposes full off-screen text of share intents) to
confirm all five vc10 changes work on the real release build:
- **Watermark lattice** — staggered diamond pattern measured directly off a screenshot (row offset
  ≈ half horizontal spacing, vertical:horizontal ratio ≈ 0.6), not the old random scatter.
- **Entry swipe + pager polish** — swiping moves cleanly between entries; counter updates live
  (caught it mid-update during a mid-gesture screenshot); edge-peek (two entries partially visible
  with a gap) confirmed in the same mid-swipe capture.
- **Share text** — `uiautomator dump` captured the exact share-intent strings: entry share
  (`"test\n📍 Oranjestad, Aruba  ·  June 10, 2026\n\n\n— shared from Macaco"` + photo attached) and
  app-referral share (`"I've logged 14 travel memories in Macaco — ...\n\nhttps://play.google.com/..."`,
  count matched the real journal size) — both exact matches to spec.
- **Adventures map** — subtitle read "9 of 10 locations mapped" (sane), country/region labels thin
  and legible.

**Found in passing (pre-existing, not caused by today's changes):** the "Dresden City Museum" entry's
**title** field contained leftover pasted share-text output from earlier testing (garbled into the
title, not the description as first suspected). Attempted to fix it via ADB text injection
mid-session; the replacement typing went wrong (title ended up as "my Paris" — keyboard
autocomplete likely interfered) before the cleanup could be verified. **User is fixing the title
directly on-device.** Location/date/description/tags on that entry were never affected.

### Watermark v2 (Cowork brief: watermark-v2)
Follow-up refinement to the lattice from the previous brief — `drawMacacoIcon` itself unchanged,
only `MacacoWatermarkBackground`'s grid parameters:
- Horizontal spacing 150dp → 130dp (vertical stays 90dp).
- Icon radius: fixed 26dp (was random 10–16.5dp).
- Opacity: fixed 3% (was random 13–22%) — much lighter, "whisper level."
- Removed `java.util.Random` entirely — no randomness left to seed.

### vc11 / 1.4 build
Bumped `versionCode` **10→11** (still `versionName` 1.4) and built a clean signed `bundleRelease`
(~22 MB) on top of vc10, with only the watermark v2 change. **Not yet uploaded to Play or installed.**

## 2026-06-17 — Entry swipe, share text, watermark lattice, map fixes — vc10 / 1.4 build

Five Cowork-brief-driven changes, all compiled clean and bundled into a signed AAB. None verified
on-device yet (see NEXT above).

### Swipe between entries on the detail screen
Finished wiring a `HorizontalPager` across `entries` on `EntryDetailScreen` (the signature change
to take the full list + `initialEntryId` had been started in an earlier session but left
non-compiling — body still referenced the old single-`entry` parameter, and `NavGraph` hadn't been
updated to match). Swiping left/right now moves to the next/previous entry without backing out to
the list; the toolbar and delete dialog always act on whichever entry is currently in view.
`NavGraph.kt` updated to pass `entries` + `initialEntryId` instead of a single looked-up entry.

### Premium pager polish (Cowork brief: horizontal-pager-premium)
Layered six refinements onto the new pager: parallax photo header (translates at 40% of swipe
offset, clipped to bounds), edge-peek with inactive-page scale (0.93→1.0) and fade (75%→100%) via
`contentPadding`/`pageSpacing`, animated "N / total" counter in the top bar (hidden for single-entry
journals), per-page scroll-to-top on page change, a single haptic tick per swipe settle (suppressed
on initial screen open via `snapshotFlow`+`drop(1)`), and Coil crossfade on entry photos.

### Share text improvements (Cowork brief: share-text)
**App-referral share** (`AppActions.shareApp`) now pulls live entry count into a pluralized
`share_app_text` string ("I've logged N travel memories...") with the Play Store URL on its own
line so receiving apps auto-link it. Translated into all 11 locales. **Entry share**
(`EntryDetailScreen.shareEntry`) reformatted to a cleaner layout — plain title, combined
"📍 location · date" line, description capped at 300 chars, lowercase "— shared from Macaco" credit.
Kept the existing photo-attachment intent logic (single/multi-photo, clipboard caption fallback)
intact since the brief's reference code didn't know about it.

### Watermark redesign (Cowork brief: watermark-background)
Resolves the open "clarify watermark design expectations" item from the previous NEXT note. Switched
`MacacoWatermarkBackground`'s layout from random jitter to the brief's structured staggered diamond
lattice — odd rows offset by half a spacing unit, 150dp horizontal / 90dp vertical spacing, only
icon size/alpha still randomized (fixed seed). Icon art and screen coverage (journal-list empty
state, full new/edit-entry form background, entry-detail empty-description area) already matched
the brief — no changes needed there.

### Adventures map fixes (Cowork brief: adventures-map-fixes)
**Label weight:** added `weight: 0.5` to `labels.text.stroke` on `administrative.country/locality/
province` in both `map_style.json` (dark) and `map_style_light.json` — thins the bold halo around
country/region labels. No `map_style_standard.json` exists (Standard theme uses Google's default
style, `styleRes = null`), so that's the only file the brief assumed that doesn't apply. **Counter
bug:** the brief diagnosed "12 of 2 locations mapped" as swapped `entries.size`/`uniqueLocations.size`
arguments, but this codebase doesn't use those variables in that string at all — the real cause is
`geocodedLocations` being an append-only cache in the ViewModel that's never pruned when entries are
edited/deleted, so its raw size can exceed the current unique-location count. Fixed by counting the
overlap between current `locations` and `geocodedLocations` instead of using the raw cache size.

### vc10 / 1.4 build
Bumped `versionCode` **9→10** (still `versionName` 1.4) and built a clean signed `bundleRelease`
(~22 MB) bundling all five changes above on top of vc9. **Not yet uploaded to Play.**

## 2026-06-16 — Watermark refinement + vc8 / 1.4 build (`9d047d5`, `62291ea`)

On-device review of the watermark (on vc7) showed it read **too large** and **bled through the
new-entry form's transparent text fields**. Fixed:
- Smaller icons (~18–32dp diameter) and sparser spacing in `MacacoWatermarkBackground`.
- Gave the new-entry form's five `OutlinedTextField`s an **opaque background fill**
  (`focused/unfocusedContainerColor = background`) so the watermark only shows in the gaps, behind
  the fields — not through them.

Couldn't verify the fix on-device: a sideloaded debug build hits the paywall ("product not found",
can't transact), so built the release instead. Bumped `versionCode` **7→8** (still `versionName` 1.4),
clean `bundleRelease`. **First build to include native debug symbols** (`debugSymbolLevel = FULL`), so
it also clears Play Warning 2. **vc8 uploaded to the Play internal-testing track.**

### Denser watermark → vc9 / 1.4 (`c92f5ad`)
Reviewed the refined watermark and it read too sparse, so **increased icon density ~2.5×** (tile
spacing 165dp→105dp, jitter 80dp→50dp; icon size unchanged). Since vc8 was already uploaded, bumped
`versionCode` **8→9** (still 1.4) and built the signed AAB at
`app/build/outputs/bundle/release/app-release.aab` (~22 MB). **Not yet uploaded.** (No on-device
install this round — the A53's sideloaded debug build was removed due to the paywall; verify on the
next Play install.)

## 2026-06-16 — Play upload warnings: native symbols fixed, R8 deferred

Addressed the two non-blocking Play upload **warnings** (seen on vc6/vc7):
- **Native debug symbols** — added `ndk { debugSymbolLevel = "FULL" }` to the release build type so
  Play can symbolicate native crashes/ANRs. Clears the warning on the **next** build (vc7 already
  uploaded still shows it). Config validated; no behaviour change.
- **Deobfuscation/mapping file** — only clears by enabling R8 (`isMinifyEnabled = true`). **Deferred**
  by decision: R8 needs keep rules for the reflection-heavy Google Drive REST client,
  kotlinx.serialization (@Serializable models), and Firebase/RevenueCat, plus a full on-device test
  pass (Drive sync, billing, backup/restore). Harmless while not obfuscating. Revisit as its own
  tested task before public launch. **TODO: enable R8 with keep rules before production.**

## 2026-06-16 — Macaco watermark on empty-state screens (`6683e25`)

Third Cowork brief: a subtle repeating line-art **macaco icon pattern** as an empty-state background.
New `ui/components/MacacoWatermark.kt` — a `drawMacacoIcon` `DrawScope` helper (goggled-monkey line
art: head, concentric ears, goggles, muzzle, nostrils) and a `MacacoWatermarkBackground` wrapper that
tiles jittered, randomly-sized icons in the theme's **`primary` colour at low alpha** (so it adapts
across all 7 themes), with a fixed RNG seed for a stable layout. Applied behind: the **journal-list
empty state**, the **entry-detail content when the description is blank**, and the **new/edit entry
form**. Brief was already theme-adaptive, so no colour deviation — only dropped a no-op `shuffle`.
Compiles clean; **not yet verified on-device** (chose to commit now rather than re-do the
uninstall/sideload dance on the A53). The new-entry-form placement is the densest screen and most
likely to want an alpha/spacing tweak or removal after a look.

### Built release AAB at vc7 / 1.4
Bumped `versionCode` **6→7** and `versionName` **1.3→1.4** (first versionName bump since 1.3 — vc7
adds the watermark on top of the fully-verified vc6). Clean `bundleRelease`; signed AAB at
`app/build/outputs/bundle/release/app-release.aab` (~22 MB). Only new feature vs vc6 is the
empty-state watermark — so on the next install, eyeball the empty states (especially the new-entry
form) and adjust alpha/spacing if needed. **Uploaded to the Play internal-testing track.**

## 2026-06-16 — Play purchase verified + in-app subscription management

### Play internal-testing purchase → entitlement verified ✅
Installed Macaco from the Play **internal-testing** track and ran a real test purchase. The full
chain works end-to-end: purchase completes in Play Billing → RevenueCat reports the `premium`
entitlement → the app unlocks (opens straight to the journal). This closes out the last open billing
item — the promo-entitlement workaround is no longer the only path; **real test purchases now work**.

### Added in-app subscription management (`bf86b23`)
Closed the launch gap where users had no in-app way to cancel/refund (also a Google recommendation):
- **`AppActions.manageSubscriptions()`** — deep-links to the Play subscription centre
  (`play.google.com/store/account/subscriptions?package=…`).
- **`SubscriptionInfoScreen`** — a "Manage subscription" `OutlinedButton`, shown only for recurring
  plans (`currentPlanId` contains `annual`/`monthly`); hidden for lifetime, which has nothing to cancel.
- **`HelpAboutScreen`** — new FAQ entry "How do I cancel or get a refund?" explaining the Play-billing flow.
- Strings (`subscription_manage`, `help_faq_q/a_billing`) added across all 11 locales.

**On-device test (A53):** the promo entitlement carries no real product id, so the button stays
hidden there. Forced `isRecurring = true` temporarily, sideloaded the debug build (had to uninstall
the Play build first — signature mismatch), and confirmed the button renders, the deep link opens the
Play subscriptions page, and the FAQ entry reads correctly. Reverted the temp override and reinstalled
the A53 from the Play track (premium restored via the account-tied entitlement).

### Fixed: Google sign-out didn't show the account picker (`1f97c91`)
After signing out from a Google login, the next "Sign in with Google" silently reused the previous
account — you could never switch Google accounts. Cause: `FirebaseAuthRepository.signOut()` only
cleared Firebase auth and left the legacy GMS `GoogleSignInClient`'s cached account intact. Fix: also
call `GoogleSignIn.getClient(...).signOut()` on sign-out (using the previously-unused `appContext`),
so the next `signInIntent` shows the account chooser. Shipped in vc6; **verified on-device
2026-06-16 — sign-out now shows the account picker.** ✅

### Added Adventures map theme setting + gold country names (`49921ed`)
Made the Adventures map (the `MapScreen` reached from the drawer) customizable:
- **Dark / Light / Standard** map theme, selectable in **Settings → Map** via a `FilterChip` row.
  Persisted as the `map_theme` DataStore pref (default Dark), exposed as `MapTheme` on the ViewModel,
  and applied live by `MapScreen` (mirrors the existing `AppTheme` plumbing).
- New `map_style_light.json` (light teal-tinted) for the Light option; Standard uses Google's default
  map (no style override).
- **Country labels are now Macaco bright gold (`#F0C840`)** in both branded styles — with a dark
  stroke on the light style for legibility.
- `MapTheme` enum added; `settings_map*` / `map_theme_*` strings across all 11 locales.

Shipped in vc6; **verified on-device 2026-06-16** — the Dark/Light/Standard selector works. ✅

### Built release AAB for Play (`a9c57a9`)
Bumped `versionCode` 4→5 and `versionName` 1.2→1.3, then built the signed release bundle
(`./gradlew bundleRelease`, signed with the upload keystore via the git-ignored `keystore.properties`).
Output: `app/build/outputs/bundle/release/app-release.aab` (~22 MB). Bundles subscription management,
the sign-out fix, and the map theme feature. Same two non-blocking warnings expected on upload (no
mapping file — `isMinifyEnabled = false` — and native debug symbols). **vc5 was uploaded to the Play
internal-testing track** (superseded by vc6 below — see the next entry's correction).

### Set up gradle-play-publisher (`9f4fbcc`)
Wired the Triple-T `com.github.triplet.play` plugin so `./gradlew publishReleaseBundle` can push the
signed AAB straight to the internal track. Hit a real AGP-compat issue — 3.12.1 fails on AGP 9.2.1
(`BaseAppModuleExtension` gone); **4.0.0** applies cleanly. Configured `track = internal`, AAB mode.
Credential (`play-service-account.json`, git-ignored) **not yet created** — that one-time
service-account setup (documented in `docs/release-setup.md`) is the only thing blocking automated
uploads.

### Branded the entry detail screen, theme-adaptive (`97b25cd`)
Followed an old design brief (found in the stale `MyApplication` clone — legacy `com.example.myapplication`
package) to bring `EntryDetailScreen` in line with the app's branding, but using **Material theme
colours instead of the brief's hardcoded teal/amber** so it holds up across all 7 selectable themes:
- Header bar → theme `primary` with `onPrimary` icons.
- Mood + date chips → filled `secondaryContainer` accent chips (same token as the journal-list date
  pill); tag chips → filled `secondaryContainer` matching list tags; location chip tinted `primary`.
- Blank descriptions now show a tappable **"Add your story…"** prompt (opens the editor) + a subtle
  bottom fade instead of dead space. String added across all 11 locales.
- Photo full-bleed (also in the brief) was already in place.
- Chose `secondaryContainer` over hardcoded brand colours partly because that old clone already tried
  "gold tag text across all screens" and **reverted** it. Shipped in vc6.
- **Verified on-device 2026-06-16** (driven over ADB with screenshots): header band, mood/date/location/
  tag chips, full-bleed photo, and the empty-description **"Add your story…"** prompt + bottom fade
  (tap → opens the editor). **Theme adaptivity confirmed** by switching to the Rose theme — the header
  and chips adopt the theme's colours (the deliberate deviation from the brief's hardcoded teal). ✅

### Fixed: Manage-subscription button never showed for subscribers (`9fbee37`)
Tester reported the "Manage subscription" button missing on a fresh account after buying **monthly**.
Root cause: the button (and the plan label) keyed off the entitlement's `productIdentifier`
containing `"monthly"`/`"annual"`, but **both base plans live under one product id (`macaco_premium`)**,
so it returned `"macaco_premium"` for every subscriber — the check never matched, the button never
showed for *anyone*, and subscribers were mislabeled "Lifetime". Fix: `BillingManager` now derives
**`manageableSubscription`** from `store == PLAY_STORE && expirationDate != null` (a real
auto-renewing Play sub), via a single `applyEntitlement()` helper shared by all RevenueCat callbacks;
`SubscriptionInfoScreen` gates the button on that and shows a neutral "Auto-renewing subscription"
label instead of "Lifetime". New string in 11 locales.

### Show which plan the user is on (`2282d0d`)
Display **Monthly / Annual / Lifetime** on the subscription page. Resolves cadence by matching the
entitlement's base-plan id (`productPlanIdentifier`) against the offering's monthly/annual packages
(`googleProduct.basePlanId`) — exact, since the product id alone can't distinguish them. Falls back to
the neutral label only when unresolved.

Both billing fixes shipped in vc6. **Verified on-device 2026-06-16:** with a real **monthly** Play
subscription, the "Manage subscription" button now shows on the Subscription screen. ✅ (Annual /
lifetime not separately re-checked, but share the same gating path.)

### Added contextual empty-state hints to the new/edit entry screen (`78c23c8`)
Second Cowork brief from the stale `MyApplication` clone — three "whispered guidance" hints in
`NewEditEntryScreen`, each shown only while its field is empty and gone on first interaction:
- Photos: "Add photos to bring your memory to life" (📷, below the photo row).
- Tags: "Try #beach, #food, #family" (below the tags field).
- Story: "Tap the mic to speak your memory" (🎤, below the description field).

Shared `HintRow` helper, **theme-adaptive (`primary` @ 60% alpha)** instead of the brief's hardcoded
teal. Story copy points at the screen's own mic button rather than the keyboard mic. Strings in all
11 locales. Shipped in vc6; **verified on-device 2026-06-16 — all three hints show.** ✅

### Rebuilt the 1.3 AAB at vc6 with all the latest changes
**Correction:** vc5 *was* uploaded to the Play internal-testing track (the earlier "not yet uploaded"
note was wrong — fixed above). Since vc5 was consumed, bumped `versionCode` **5→6** (still
`versionName` 1.3) and did a clean `bundleRelease`. The signed vc6 AAB at
`app/build/outputs/bundle/release/app-release.aab` (~22 MB) bundles everything since vc5: the
sign-out fix, gradle-play-publisher setup, entry-detail branding, the subscription button fix + plan
display, and the new-entry hints. **Uploaded to the Play internal-testing track.**

### vc6 on-device verification — COMPLETE ✅
Installed from the Play internal-testing track and verified every feature in the release on a real
Play build (some driven over ADB with screenshots, the entry-detail pass included a throwaway entry
since cleaned up):

| Feature | Result |
|---------|--------|
| Manage-subscription button (monthly sub) | ✅ shows; plan label correct |
| Google sign-out → account picker | ✅ picker appears |
| Entry-detail branding (header/chips/empty-state prompt) | ✅ + tap-to-edit |
| Entry-detail **theme adaptivity** (tested under Rose) | ✅ header/chips adopt theme |
| Map themes (Dark/Light/Standard selector) | ✅ |
| New-entry contextual hints (all three) | ✅ also theme-adaptive |

Nothing outstanding for vc6. Only open thread in the repo is the paused Galaxy S8+ ADB setup below.

## 2026-06-15 — Galaxy S8+ ADB connection (IN PROGRESS, paused for PC reboot)

> **2026-06-16 update — RNDIS issue RESOLVED; new blocker is USB debugging.** The phone now
> enumerates as **PID_6860 (MTP / File Transfer)** — the RNDIS device (PID_6863) is no longer
> present, so the Default USB Configuration change worked. But the composite device advertises only
> **MTP + Modem, no ADB interface**, and there are no undriven/problem devices — the signature of
> **USB debugging being off** on the phone. **Next step (on the phone, not Windows):** Developer
> options → toggle **USB debugging** off→on (and *Revoke USB debugging authorizations*), replug,
> accept the **"Allow USB debugging?"** prompt with *Always allow*. Then re-scan + `adb devices`.
> The Device-Manager / RNDIS driver steps below are now obsolete. (Paused again at user's request.)

**Goal:** add a Samsung Galaxy S8+ as a second USB-connected test device (alongside the A53, which
is on wireless ADB). Resuming after a planned PC reboot.

**Status: not connected yet.** `adb devices` only shows the A53
(`adb-RZCT80LVGNY-...` transport_id varies). The S8+ never enumerates as an ADB device.

**Root cause identified:** Windows keeps binding the S8+'s USB interface as a
**`SAMSUNG Mobile USB Remote NDIS Network Device`** (RNDIS / USB-Ethernet) instead of an
**Android ADB Interface**. Every Device-Manager fix bounced back to RNDIS because the *phone* is
actively advertising RNDIS as its USB mode — so it must be changed on the phone, not in Windows.

**Tried (all still left it as RNDIS):** enabled USB debugging; installed Samsung USB Driver;
confirmed USB tethering OFF; restarted adb server; uninstalled the RNDIS device.

**Driver facts (confirmed installed):**
- Samsung ADB inf is present at `C:\Program Files\Samsung\USB Drivers\25_escape\ssudadb.inf`
  (defines "SAMSUNG Android ADB Interface"); also in the Windows DriverStore.
- Google USB driver is NOT installed.

**Next steps after reboot:**
1. On the S8+: **Settings → Developer options → Default USB Configuration → set to File Transfer /
   MTP** (it was likely RNDIS/USB-tethering — the suspected real cause). Confirm USB debugging ON.
2. Replug; on the notification shade pick **File Transfer**. The RNDIS "Network Device" should be
   replaced by an MTP device + an ADB interface, and the "Allow USB debugging?" prompt should appear
   — accept with *Always allow*.
3. If it still binds as RNDIS: Device Manager → Network adapters → the RNDIS device → **Update
   driver → Have Disk →** `C:\Program Files\Samsung\USB Drivers\25_escape\ssudadb.inf` → pick
   **SAMSUNG Android ADB Interface**.
4. Then `adb devices` should show the S8+; install Macaco on it. With two devices attached, target
   each explicitly via `-s <transport_id>`.

**Note:** S8+ runs Android 9 (API 28), above the app's `minSdk 24`. Pre-Android-11 wireless ADB
still needs one working USB session first (`adb tcpip 5555` then `adb connect <ip>:5555`), so USB
must bind correctly at least once.

adb path: `C:/Users/micke/AppData/Local/Android/Sdk/platform-tools/adb.exe`. Note: `adb kill-server`
drops the A53's wireless link too — reconnect it afterward if needed.

## 2026-06-15 — RevenueCat live + first internal-testing build

**Focus: Wired up live RevenueCat billing and got the first internal-testing build ready for Play.**

### Billing went live
- Replaced the placeholder RevenueCat key with the real public SDK key (`goog_yRpbApeYi…`). This
  flips `BillingManager` from the local-fallback paywall to **live entitlement checks**. Committed
  (`a3d1dde`).
- Reverted an in-progress debug paywall-bypass experiment — superseded by the real key, so the
  commit is just the key change.

### Verified the full billing chain on the A53 test device
Installed debug builds and read RevenueCat logcat to confirm, end-to-end:
- Identity correctly tied to the Firebase uid (`6x4Gh8bHKUb6yNncd7KpgdcXok92`).
- Offering loads with both products: **`macaco_premium`** (€18.99/yr + 7-day trial · €3.09/mo) and
  **`macaco_lifetime`** (€40.99 one-time).
- `premium` entitlement attached to both products (confirmed dashboard-side).

### Unlocked the test device via promotional entitlement
- The old local-DataStore "premium flag" trick no longer works now that RevenueCat is live (the
  configured path ignores it).
- Granted a **promotional `premium` entitlement** to the test uid in RevenueCat. First attempt
  landed on the wrong customer (device kept getting HTTP 304, stayed paywalled); after re-granting
  on the exact uid, a relaunch pulled HTTP 200 with the entitlement and **the app opened straight to
  the journal**. ✅

### Prepared the internal testing track
- Built and signed the release **AAB** with the upload keystore (`versionCode 4`, `versionName 1.2`).
- Bumped `versionCode` 3→4 after Play rejected the duplicate. Committed (`7bcae83`), both commits
  pushed to `origin/master`.
- Reviewed the two Play upload warnings (missing mapping file + native debug symbols) — both
  non-blocking, safe to ignore for testing.

### Still open
- ~~Finish the Play Console internal-testing rollout (testers list, license testing) so **real test
  purchases** work.~~ **Done 2026-06-16** — see top entry.
- ~~Once the build is installed from the Play track, verify the real purchase → entitlement flow
  end-to-end.~~ **Done 2026-06-16** — verified working.
