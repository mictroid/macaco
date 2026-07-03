# Macaco — Full-App Code QA, 2026-07-03 (pre-production sweep)

Static review of all 46 Kotlin files (~12.9k lines) at vc49, plus manifest, gradle config and
string resources. Findings are ordered by severity. Items marked **[BRIEF]** have an
implementation brief in `docs/code-brief-*.md`; the rest are documented here for a decision.

---

## HIGH — data integrity / user-visible corruption

### H1. Photo ↔ Drive-ID desync on edit **[BRIEF: code-brief-drive-ids-alignment.md]**
`NewEditEntryScreen` reorders/removes/adds photos in `photoUris` only, then saves with
`driveFileIds = existingEntry?.driveFileIds` untouched. The two lists are positional parallels,
so any edit that changes photo order or count mis-pairs every photo with another photo's Drive
file. Consequences: wrong photos shown on a restored device, wrong photos bundled per entry in
backup export, and new photos that never upload (their slot already holds a stale ID).
`EntryDetailScreen.withCover`/`withSwapped` have the same bug when `driveFileIds` is shorter
than `photoUris` (photos pending upload): the guard skips the driveIds mutation and desyncs.

### H2. Stale-entry overwrite after background Drive upload **[BRIEF: code-brief-save-entry-upload-race.md]**
`JournalViewModel.saveEntry` launches a Drive upload, then on completion writes
`cloudEntrySync.save(entry.copy(driveFileIds = updatedIds))` — the *entire entry as it was when
the upload started*. If the user edits the entry while a multi-photo upload is in flight
(seconds to minutes), the completion callback silently reverts their edit. Same pattern in
`syncPhotosToGoogleDrive`'s `onEntryUpdated`.

### H3. Camera photos display rotated (EXIF orientation dropped) **[BRIEF: code-brief-photo-exif-rotation.md]**
`ImageStorage.compressForStorage` decodes and re-encodes every entry photo without reading the
EXIF orientation tag; `BitmapFactory` ignores it and the re-encoded JPEG has none. Photos taken
with the camera (which stores portrait shots as rotated-landscape + EXIF flag — standard on
Samsung/Pixel) will render sideways everywhere in the app, in Drive backups and in exports.
`JournalBackup.compressToBytes` (compact export) has the identical bug.

---

## MEDIUM

### M1. Backup export/import dies if the user leaves Settings **[BRIEF: code-brief-backup-lifecycle.md]**
The export/import coroutines run in `rememberCoroutineScope()` in `SettingsScreen`. Navigating
back (or any disposal) cancels them mid-write → truncated/corrupt zip at the SAF destination, or
a half-finished import. The `backupBusy` overlay also doesn't intercept the system back button.

### M2. Previous user's premium entitlement leaks across account switch **[BRIEF: code-brief-billing-signout-reset.md]**
`BillingManager` never calls `Purchases.logOut()` and never resets `isPremium` when
`currentUser` becomes null. After sign-out, the old user's `true` persists; a different,
non-premium account that signs in skips the paywall until RevenueCat's `logIn` callback lands
(or indefinitely if it fails offline). `restore()` also bypasses `applyEntitlement`, leaving
`currentPlanId` / `manageableSubscription` stale.

### M3. Hardcoded English strings in an 11-locale app **[BRIEF: code-brief-hardcoded-strings.md]**
User-visible English hardcoded in: MapScreen (title, "N of M locations mapped", empty state,
marker snippets), ProfileScreen ("Member since …" ×2), JournalListScreen ("Not signed in"),
PurchaseScreen ("No hidden fees. Cancel anytime."), EntryDetailScreen share flow ("Share your
memory", "Caption copied…", "— shared from Macaco"), NewEditEntryScreen TripField ("Trip",
"e.g. Thailand 2026", "Clear trip"), ReminderWorker actions ("+ Add Memory", "Remind me later"),
the notification channel name/description, and *all* of `NotificationCopy.kt`.

### M4. Assorted UX/robustness fixes **[BRIEF: code-brief-qa-polish.md]**
- System back while the full-screen photo gallery is open pops the whole detail screen instead
  of closing the gallery (no `BackHandler`).
- `pendingCameraUri` is `remember`, not `rememberSaveable` (NewEditEntryScreen + ProfileScreen):
  if the OS kills the process while the camera is open — common — the captured photo is lost.
- Adventure Reel uses `photoUris` blindly when non-empty; on a reinstalled device those URIs are
  dead and the reel encodes zero frames yet reports success with a broken mp4. Also
  `drawBranding` allocates 2–3 `Paint`s per frame — the vc47 "pre-allocated Paints" fields exist
  but are never used.
- `shareEntry` ignores `cachedDrivePhotos`, so photos are missing from shares on any device
  other than the one that took them.

### M5. Account deletion can strand a half-deleted account — *decision needed, no brief*
`FirebaseAuthRepository.deleteAccount()` deletes all Firestore data first, then
`user.delete()` — which Firebase rejects with `FirebaseAuthRecentLoginRequiredException` if the
last sign-in is old. Result: data gone, auth account still exists, user sees a raw error.
Proper fix needs a re-authentication step (Google `silentSignIn` / password prompt) before
deleting. Recommend designing this flow before production.

---

## LOW / decisions & polish

| # | Finding | Where |
|---|---------|-------|
| L1 | `isMinifyEnabled = false` and empty `proguard-rules.pro` — release ships unshrunken and unobfuscated. Deliberate? R8 would need rules for Drive/Gson/kotlinx-serialization. | `build.gradle.kts` |
| L2 | Deleting an entry never deletes its photos from the user's Drive folder — orphans accumulate silently. Arguably a feature (extra safety); worth a line in the FAQ either way. | `JournalViewModel.deleteEntry` |
| L3 | `android:minSdkVersion="33"` on the `READ_MEDIA_IMAGES` `<uses-permission>` is not a valid attribute (harmless — the permission doesn't exist below 33 — but should be removed or replaced). | `AndroidManifest.xml` |
| L4 | `LegacyEntryMigration` comments say a failed write leaves the file for retry, but `CloudEntrySync.save` never throws (fire-and-forget listener), so `onSuccess` always renames. Harmless offline (writes queue), comment misleading. | `LegacyEntryMigration.kt` |
| L5 | Cover hint (`coverHintCount < 3`) increments once per composed pager page, so one visit can burn 2–3 of the 3 allotted showings. | `EntryDetailScreen` |
| L6 | `Log.d("MapCamera", …)` diagnostics still emitted in release builds. | `MapScreen.kt` |
| L7 | Duplicate `tabRoutes` definitions (local val + file-level private val, one unused). | `NavGraph.kt` |
| L8 | Edit screen: if the entry is deleted remotely mid-edit, `entries.find … ?: return@composable` renders a blank screen instead of popping. | `NavGraph.kt` (EditEntry route) |
| L9 | Reel progress can never reach 100% when a photo fails to decode (`continue` skips frames but `totalFrames` still counts them). Cosmetic. | `AdventureReelEncoder.kt` |
| L10 | Purchase fallback price labels hardcoded USD ("$2.99" etc.) shown until offerings load — brief flash of wrong currency for non-USD users. | `PurchaseScreen.kt` |

---

## What was checked and found sound

Gate ordering (splash → lock → loading → login → purchase), Firestore offline-write handling,
zip path-traversal on import (neutralised by `replace("/", "_")`), the delete-entry double-pop
guards, biometric API-level handling, WorkManager reminder scheduling, media-permission
matrix across API 24–36, FileProvider usage, WIF release workflow, and the language-split
bundle config.
