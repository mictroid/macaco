# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Macaco** (formerly **Wanderlog**) — a cloud-synced travel journal Android app. Entries sync
per-user via Firebase (Firestore + Auth), entry photos back up to the user's Google Drive, and
premium access is gated by a RevenueCat in-app purchase. Single-module, single-activity,
Compose-only UI.

The app was renamed Wanderlog → Macaco. The Kotlin package, the Play `applicationId`, and the git
remote are all now `macaco`-based: source lives under `com.houseofmmminq.macaco`, the
`applicationId` is `com.houseofmmminq.macaco`, the git remote is `github.com:mictroid/macaco`, and
the Firebase/GCP project is `macaco-499016` (migrated off the legacy `wanderlog-11d28` project —
Auth users and Firestore entries were copied across with UIDs preserved). Treat
"Wanderlog"/`wanderlog-11d28`/`com.example.myapplication` in any identifiers as legacy; the active
code, user-facing strings, and Drive/gallery folders all say "Macaco". (A separate stale clone at
`C:\Users\micke\AndroidStudioProjects\MyApplication` still uses the old `com.example.myapplication`
package — it is not this project.)

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests on device/emulator
./gradlew clean                  # Clean build artifacts
```

Run a single test class:
```bash
./gradlew test --tests "com.houseofmmminq.macaco.ExampleUnitTest"
```

## Architecture

**MVVM + Repository**, pure Kotlin Flow (no LiveData, no Hilt).

```
TravelJournalApp (Application)            ← manual service locator
  ├── preferencesManager: PreferencesManager  ← DataStore (theme, profile, reminders, app lock, local-billing fallback)
  ├── authRepository: AuthRepository           ← FirebaseAuthRepository (active)
  ├── cloudEntrySync: CloudEntrySync           ← Firestore-backed entry sync (per uid)
  ├── billingManager: BillingManager           ← RevenueCat entitlement gate
  └── drivePhotoSync: DrivePhotoSync           ← Google Drive photo backup/restore

MainActivity
  └── JournalViewModel (via custom Factory)
        ├── StateFlow<List<TravelEntry>>      (from CloudEntrySync)
        ├── StateFlow<Boolean?> isPurchased    (from BillingManager.isPremium; null = loading)
        ├── StateFlow<UserProfile?> currentUser
        ├── StateFlow<DrivePhotoSyncState> driveSyncState + cachedDrivePhotos map
        ├── Flow<String> syncErrors            (merged Firestore + Drive errors → snackbars)
        ├── reminders (enabled / interval) + app-lock state (appLockEnabled, isAppLocked)
        ├── selectedTags filter (lifted so EntryDetail can filter the list by a tag)
        ├── exportBackup / importBackup        (local .zip via JournalBackup)
        └── theme / profile-photo state

NavGraph (Compose Navigation) — gate order, outermost first:
  SplashScreen (cold-start branded splash)
  → AppLockScreen     ← if app lock enabled, on, and signed-in+purchased
  → blank box         ← while isPurchased == null (DataStore loading)
  → LoginScreen       ← currentUser == null (login required)
  → PurchaseScreen    ← isPurchased == false
  → NavHost (full journal):
      ├── JournalListScreen
      ├── NewEditEntryScreen (shared create + edit via Screen.NewEntry / Screen.EditEntry)
      ├── EntryDetailScreen  (receives cachedDrivePhotos for Drive-restored images)
      ├── LoginScreen
      ├── ProfileScreen
      ├── SettingsScreen     (Drive sync, backup/restore, reminders, app lock live here)
      ├── SubscriptionInfoScreen
      └── HelpAboutScreen
```

**Dependency injection:** manual constructor injection via `TravelJournalApp` as a service locator.
`JournalViewModel.Factory` takes `CloudEntrySync`, `PreferencesManager`, `AuthRepository`,
`BillingManager`, and `DrivePhotoSync` explicitly — no Hilt/Dagger.

## Data Layer

- **`TravelEntry`** (`data/model/`) — `@Serializable` data class, UUID id, immutable. Fields:
  `title`, `location`, `dateMillis`, `description`, `mood`, `photoUris`, `tags`, `createdAt`, and
  `driveFileIds` (parallel to `photoUris`; `""` = that photo isn't uploaded to Drive yet). Also
  defines `onThisDayEntries()` and `tagsByFrequency()` list extensions.
- **`CloudEntrySync`** (`data/storage/`) — the active entry store. Listens to
  `users/{uid}/entries` in Firestore (ordered by `createdAt` desc) and exposes
  `StateFlow<List<TravelEntry>>`. Re-subscribes on auth change; clears to empty list when signed
  out. `save`/`delete` write to the signed-in user's subcollection. Exposes an `errors` flow.
- **`LegacyEntryMigration`** (`data/storage/`) — one-time import of entries from the legacy local
  `filesDir/entries.json` (written by older on-device app versions) into the signed-in user's cloud
  account, then renames the file to `.imported` so it never runs again. The old `EntryStorage`
  class that wrote that file has been removed.
- **`PreferencesManager`** (`data/`) — DataStore. Keys: `is_purchased` (local billing fallback),
  `dark_mode`, `profile_photo_uri`, `app_theme`, `theme_image_uri`, `reminders_enabled`,
  `reminder_interval_days` (default 4), `app_lock_enabled`.

### Photos & sync

Entry photos are no longer device-local. The flow is:

- **`ImageStorage`** (`util/`) — `persistToGallery` copies a picked/captured photo into the shared
  **Pictures/Macaco** MediaStore collection and returns a `content://` URI (survives uninstall, so
  cloud-synced entries can re-show photos after reinstall + media permission). `persist` keeps
  local-only personalization (profile photo, theme background) in `filesDir`. `persistBytesToGallery`
  writes raw bytes (used by backup import). Also handles camera temp files (`newCameraTempUri` +
  FileProvider) and best-effort `delete` when photos are removed from an entry.
- **`DrivePhotoSync`** (`data/sync/`) — backs entry photos up to a **"Macaco"** folder in the user's
  Google Drive (migrating a pre-rebrand "Wanderlog" folder in place if found). `uploadEntryPhotos`
  fills in missing `driveFileIds`; `downloadMissingPhotos` pulls photos that have a Drive ID but no
  readable local URI into `cacheDir/drive_photos/` and exposes them via `cachedPhotoUris`
  (consumed by `EntryDetailScreen`). `syncAll` does a full upload+download pass with progress
  (`DrivePhotoSyncState`: Idle/NotConnected/Syncing/Synced/Error) and user-friendly error mapping.
  Requires the `DRIVE_FILE` OAuth scope on the signed-in Google account (`isDriveConnected()`).
  The ViewModel auto-uploads on `saveEntry` and auto-downloads when the entry list changes.
- **`JournalBackup`** (`data/sync/`) — full local backup/restore as a single `.zip` picked via SAF
  (premium feature). Bundles `backup.json` (all entries) + `photos/<id>_<i>.jpg` bytes so the
  backup is portable; import re-materializes photos into the gallery and clears `driveFileIds` so
  they re-upload fresh. Wired as `exportBackup` / `importBackup` on the ViewModel.

## Billing

- **`BillingManager`** (`data/billing/`) — wraps RevenueCat (which wraps Google Play Billing).
  Exposes `isPremium: StateFlow<Boolean?>` (null = loading) and `priceLabel`. Ties the RevenueCat
  identity to the signed-in user's `uid` so purchases follow the account. `purchase(activity)` and
  `restore()` are suspend functions returning `Result<Boolean>`.
- **`RevenueCatConfig`** (`data/billing/`) — holds `GOOGLE_API_KEY` and `ENTITLEMENT_ID`
  (`"premium"`). When the API key is unset (still `goog_YOUR_…`), `BillingManager` falls back to
  the local DataStore `is_purchased` flag so the gate stays testable without a RevenueCat account.
- Real Play Billing purchases require an app uploaded to a Play Console track and signed with the
  matching key — a sideloaded debug APK cannot complete a real purchase.

## Navigation & Routing

Routes are a sealed class in `Screen.kt` (`JournalList`, `NewEntry`, `EntryDetail`, `EditEntry`,
`Login`, `Profile`, `Settings`, `Subscription`, `HelpAbout`). `NavGraph.kt` wires all composable
destinations and owns argument passing (entry IDs passed as string nav args). Create vs. edit are
separate routes (`NewEntry` / `EditEntry`) both rendered by `NewEditEntryScreen` (passed
`existingEntry = null` for create). `NavGraph` also owns the pre-NavHost gating (splash → app lock
→ loading → login → purchase) and the re-lock-on-background timer (`LOCK_TIMEOUT_MS = 30s`).

### Reminders & app lock

- **`ReminderScheduler`** (`util/`) — schedules/cancels periodic "write an entry" reminders via the
  `reminders_enabled` + `reminder_interval_days` prefs (the ViewModel calls it when those change).
- **App lock** — when `app_lock_enabled` is on, `AppLockScreen` covers the journal after the app
  has been backgrounded longer than `LOCK_TIMEOUT_MS`. Only active once signed-in and purchased.

## Key Conventions

- **State in screens:** local `remember { mutableStateOf(...) }` for ephemeral UI state (form
  fields, image picker selection). ViewModel owns the source-of-truth list, purchase flag, auth,
  and theme state.
- **Coroutine scope:** `viewModelScope` for all suspend calls from the ViewModel; `CloudEntrySync`
  and `BillingManager` own their own `CoroutineScope`s (IO / Main + SupervisorJob).
- **Image loading:** Coil `AsyncImage` with local content URIs. Media permissions declared in
  manifest (`READ_MEDIA_IMAGES` for API 33+, `READ_EXTERNAL_STORAGE` for ≤32).
- **Theming:** Material Design 3, full light/dark support. Theme is user-selectable
  (`AppTheme` enum, persisted via DataStore) with an optional custom theme image.

## Authentication

`AuthRepository` interface with two implementations:

- **`FirebaseAuthRepository`** (active) — Firebase Auth, auto-initialized via the
  `google-services.json` + `com.google.gms.google-services` plugin. Supports Google, Apple
  (OAuthProvider), and Email/Password.
- **`MockAuthRepository`** — simulates auth locally; swap into `TravelJournalApp.kt` for
  development without Firebase. Currently **not** wired in.

**Google Sign-In uses the legacy GMS `GoogleSignIn` intent**, not Credential Manager. `LoginScreen`
builds a `GoogleSignInClient` with `requestIdToken(GOOGLE_WEB_CLIENT_ID)` **and**
`requestScopes(Scope(DriveScopes.DRIVE_FILE))`, so the Drive backup scope is granted in the same
consent dialog as login; the returned idToken is handed to `FirebaseAuthRepository`. This path was
chosen deliberately ("avoids Credential Manager cancellation issues") and is also what lets the one
GMS account back both Firebase auth and `DrivePhotoSync.isDriveConnected()`. (See **Photos & sync**
for how Apple/email users connect Drive separately via Settings.)

`google-services.json` is present (project `macaco-499016`) and the plugin is applied, so Firebase
initializes automatically. `FirebaseConfig.kt` holds the project values (filled in) and is now used
only for `GOOGLE_WEB_CLIENT_ID` in `LoginScreen`. For Google Sign-In on a new machine, add the debug
SHA-1 to the Firebase console:
`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`.

## Tech Stack

| Concern | Library |
|---------|---------|
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation |
| State | Kotlin StateFlow / Flow |
| Serialization | kotlinx.serialization (JSON) |
| Image loading | Coil |
| Preferences | AndroidX DataStore |
| Cloud DB | Firebase Firestore (per-user entry sync) |
| Auth | Firebase Auth (`google-services` plugin) |
| Google Sign-In | Legacy Play Services Auth (`GoogleSignIn` intent) |
| Drive backup | Google Drive REST API (`google-api-services-drive`) + Play Services Auth |
| Billing | RevenueCat (`com.revenuecat.purchases`) over Google Play Billing |
| Growth | Play In-App Review + share/support intents (`util/AppActions`) |
| Async | Kotlin Coroutines + coroutines-play-services |

- **Min SDK:** 24 · **Target/Compile SDK:** 36
- **No Room/SQLite** — Firestore is the cloud database; the only remaining local-file logic is the
  one-time `LegacyEntryMigration` import of the old `entries.json`.

## Backup

Machine-local files needed to rebuild on a fresh machine are mirrored to Google Drive at
`G:\My Drive\Macaco-backup\` (with its own `README.md`):

- **`debug.keystore`** — debug signing key, **not in git**. This is the only irreplaceable file:
  it fixes the build's SHA-1, which is registered in Firebase for Google Sign-In. Restore it to
  `~/.android/debug.keystore` so the SHA-1 is unchanged and Google Sign-In keeps working.
- **`google-services.json`** — Firebase config; already tracked in git, mirrored to Drive as a
  fallback.
- **`local.properties`** — Android SDK path; git-ignored and auto-regenerated by Android Studio,
  so the mirrored copy is reference only (you normally won't restore it).
- **`ssh/id_ed25519` (+ `.pub`)** — SSH key for pushing to `github.com:mictroid/macaco`, **not
  in git**. Empty passphrase; the public half is registered on GitHub. Replaceable (generate a new
  key with `ssh-keygen -t ed25519` and add it at https://github.com/settings/keys), but restoring
  this copy avoids that. The repo is configured to use it via `core.sshCommand = ssh -i ~/.ssh/id_ed25519`.
- **`play-service-account.json`** — *fallback only.* Play Developer API service-account key for the
  local `./gradlew publishReleaseBundle` path. Git-ignored; lives at the repo root. **Not present on
  this machine and not in this Drive backup** — re-mint in Google Cloud Console if you need the local
  path. The **canonical publishing path is the WIF GitHub Actions workflow** (`.github/workflows/release.yml`,
  manual dispatch → closed testing), which needs no local key. See `docs/release-setup.md` →
  **Automated upload**.

Restore: clone the repo, drop `debug.keystore` into `~/.android/`, copy the `ssh/` keys into
`~/.ssh/` (for push access), open in Android Studio (it regenerates `local.properties`).
</content>
</invoke>
