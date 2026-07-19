# Macaco — Architecture

Macaco (formerly Wanderlog) is a cloud-synced travel-journal Android app. It is a
**single-module, single-activity, Compose-only** app following **MVVM + Repository** with pure
Kotlin `Flow`/`StateFlow` (no LiveData, no Hilt/Dagger — dependency injection is manual constructor
injection through the `Application` acting as a service locator).

- **Package / applicationId:** `com.houseofmmminq.macaco`
- **Min SDK 24 · Target/Compile SDK 36**
- **Backend:** Firebase (Auth + Firestore + App Check + Analytics/Crashlytics), Google Drive REST
  API, RevenueCat (over Google Play Billing), Open-Meteo, Google Maps.

All source lives under `app/src/main/java/com/houseofmmminq/macaco/`. Paths below are relative to
that root.

---

## Layer map at a glance

| Layer | Location | Responsibility |
|-------|----------|----------------|
| App / DI | `TravelJournalApp.kt`, `MainActivity.kt` | Service locator, process setup, single Activity |
| **Business logic** | `ui/viewmodel/JournalViewModel.kt` | Single orchestrating ViewModel |
| Domain model | `data/model/` | `TravelEntry`, `UserProfile` + list extensions |
| Repositories / stores | `data/storage/`, `data/auth/`, `data/billing/` | Entry sync, auth, entitlement |
| Sync & export | `data/sync/` | Drive backup, local zip backup, print/reel/recap renderers |
| **API calls** | `data/auth/`, `data/storage/`, `data/billing/`, `data/sync/`, `util/WeatherLookup.kt` | All network I/O |
| **UI views** | `ui/screens/`, `ui/components/`, `ui/navigation/`, `ui/theme/` | Compose screens + routing |
| Widgets | `ui/widget/` | Home-screen `AppWidgetProvider`s |
| Utilities | `util/` | Images, video, reminders (WorkManager), geocoding, tags |

---

## 1. Application entry & dependency injection

**`TravelJournalApp.kt`** (Application) is the **manual service locator**. It lazily constructs the
five singletons the rest of the app depends on:

```
preferencesManager : PreferencesManager   ← DataStore (theme, profile, reminders, app-lock, billing fallback)
authRepository     : AuthRepository       ← FirebaseAuthRepository
cloudEntrySync     : CloudEntrySync        ← Firestore-backed entry store (per uid)
billingManager     : BillingManager        ← RevenueCat entitlement gate
drivePhotoSync     : DrivePhotoSync         ← Google Drive photo/video backup
```

`onCreate()` installs **Firebase App Check** (Play Integrity in release, a reflectively-loaded
debug provider in debug) so Firestore/Auth calls are attested before the first use.

**`MainActivity.kt`** is the **only Activity** (`AppCompatActivity`, `singleTop`). It:
- builds `JournalViewModel` via its `Factory`, passing the five singletons from the Application;
- hosts the entire Compose tree (`setContent { WanderlogTheme { … NavGraph(...) } }`);
- requests runtime permissions (media read for reinstalled photos, `POST_NOTIFICATIONS`);
- runs the **Play flexible in-app update** flow (`onResume` check → "Restart" snackbar);
- refreshes the RevenueCat entitlement on every `onResume`;
- consumes **deep links** from notifications and widgets via intent actions
  (`ACTION_NEW_ENTRY`, `ACTION_OPEN_SUBSCRIPTION`, `ACTION_OPEN_ENTRY` + `EXTRA_ENTRY_ID`),
  surfacing them to `NavGraph` as one-shot flags.

---

## 2. Where the core business logic lives

### `ui/viewmodel/JournalViewModel.kt` — the single source of truth (~760 lines)

There is **one ViewModel** for the whole app. It is the hub where the repositories, preferences,
billing, and Drive sync are combined into the state the UI observes. It owns:

- `entries: StateFlow<List<TravelEntry>>` — re-exposed from `CloudEntrySync`.
- `syncErrors: Flow<String>` — merged Firestore + Drive errors → snackbars.
- `driveSyncState` + `cachedDrivePhotos` — Drive backup progress and the cache map consumed by the
  detail screen.
- Entry CRUD (`saveEntry`/delete), which also triggers **auto-upload to Drive** on save and a
  **lazy weather fetch** (`WeatherLookup`) for past-dated entries.
- Backup export/import run in `viewModelScope` (so leaving Settings can't cancel them), with
  progress/busy state flows.
- The lifted **tag filter** (`selectedTags`) so the detail screen can filter the list.
- Reminders enable/interval, **app-lock** state, theme/profile state, and premium gating helpers
  (`FREE_ENTRY_LIMIT`, entitlement checks).
- Kicks off `LegacyEntryMigration` and refreshes widgets on data change.

The domain **model** (`data/model/`) carries the rest of the pure logic as `List<TravelEntry>`
extensions — `onThisDayEntries()`, `tagsByFrequency()`, `matchingSearch()`, `toYearRecap()`,
`widgetHighlight()`, `tripNames()`, `inYear()` — so filtering/aggregation is testable without
Android. `TravelEntry` is an immutable `@Serializable` data class (UUID id, photos+videos with
parallel Drive-id lists, tags, mood, weather fields, `mediaOrder`).

---

## 3. Where the API calls / network I/O live

**Every network call is confined to the `data/` and `util/` layers** — screens never touch the
network directly, they go through the ViewModel.

| Concern | File | External API |
|---------|------|--------------|
| Auth (Google + Email/Pw), account delete | `data/auth/FirebaseAuthRepository.kt` | Firebase Auth |
| Entry sync CRUD | `data/storage/CloudEntrySync.kt` | Cloud Firestore |
| Firestore ⇆ model mapping | `data/storage/TravelEntryFirestoreMapper.kt` | — |
| Legacy local import | `data/storage/LegacyEntryMigration.kt` | (local file → Firestore) |
| Entitlement / purchase / restore | `data/billing/BillingManager.kt` | RevenueCat → Play Billing |
| Photo/video Drive backup | `data/sync/DrivePhotoSync.kt` | Google Drive REST API |
| Historical weather | `util/WeatherLookup.kt` | Open-Meteo archive API |
| Map tiles / markers | `ui/screens/MapScreen.kt` | Google Maps (maps-compose) |

Details:

- **`CloudEntrySync`** listens to `users/{uid}/entries` (ordered by `createdAt` desc) and exposes a
  `StateFlow<List<TravelEntry>>`; it re-subscribes on auth change, clears on sign-out, and writes
  `save`/`delete` to the signed-in user's subcollection. Exposes an `errors` flow.
- **`FirebaseAuthRepository`** supports Google (legacy GMS `GoogleSignIn` intent, requesting the
  `DRIVE_FILE` scope in the same consent dialog) and Email/Password, plus `deleteAccount()`
  (chunked Firestore delete under the 500-op batch limit, then Auth account) for GDPR/Play.
- **`BillingManager`** wraps RevenueCat, ties the RC identity to the Firebase `uid`, exposes
  `isPremium: StateFlow<Boolean?>` (null = loading), and falls back to the local DataStore
  `is_purchased` flag when the RevenueCat key is unset (`RevenueCatConfig`).
- **`DrivePhotoSync`** backs entry photos/videos up to a "Macaco" Drive folder (migrating a legacy
  "Wanderlog" folder in place). `uploadEntryPhotos` fills missing `driveFileIds`;
  `downloadMissingPhotos` pulls Drive-only media into `cacheDir/drive_photos/` with **bounded
  concurrency** (fixed the old one-at-a-time slow load); `syncAll` does a full pass exposing
  `DrivePhotoSyncState` (Idle/NotConnected/Syncing/Synced/Error). Requires the `DRIVE_FILE` OAuth
  scope.

Config values (not network code, but where the endpoints/keys are wired): `FirebaseConfig.kt`
(`GOOGLE_WEB_CLIENT_ID`), `RevenueCatConfig.kt` (`GOOGLE_API_KEY`, `ENTITLEMENT_ID = "premium"`),
`MAPS_API_KEY` injected from `local.properties` as a manifest placeholder.

---

## 4. Where the UI views live

All UI is **Jetpack Compose + Material 3**. No XML layouts.

### `ui/navigation/` — routing & gating
- **`Screen.kt`** — sealed route definitions (entry ids passed as string nav args).
- **`NavGraph.kt`** — wires every composable destination and owns the **pre-NavHost gate chain**
  (outermost first):
  ```
  blank (onboarding loading) → Onboarding → Splash → AppLock → blank (entitlement loading)
    → Login → VerifyEmail → NavHost (full journal)
  ```
  It also owns the re-lock-on-background timer (`LOCK_TIMEOUT_MS = 30s`) and consumes the deep-link
  flags from `MainActivity`. The journal is **freemium** — premium is enforced per-feature and at
  entry creation beyond `FREE_ENTRY_LIMIT = 3`, not as an app-wide wall.

### `ui/screens/` — one file per screen
Bottom-tab roots: **`JournalListScreen`**, **`MapScreen`** (Adventures map), **`ProfileScreen`**.
Flow screens: `NewEditEntryScreen` (shared create+edit), `EntryDetailScreen` (swipe pager over the
tag-filtered set, Drive-cached photos), `SearchScreen`, `SettingsScreen`, `PrintExportScreen`,
`YearInTravelScreen`, `PurchaseScreen` (paywall), `SubscriptionInfoScreen`, `HelpAboutScreen`,
`LoginScreen`, `VerifyEmailScreen`, `OnboardingScreen`, `SplashScreen`, `AppLockScreen`.
The four largest (~1.5k lines each) are the list, settings, editor, and detail screens.

**State convention:** screens hold ephemeral UI state in local `remember { mutableStateOf(...) }`;
the ViewModel owns the source-of-truth list, auth, entitlement, and theme. Screens read state via
`collectAsState()` and call ViewModel methods — they do not call repositories or the network
directly.

### `ui/components/` — reusable pieces
`MacacoBrandBlock`, `MacacoSnackbar`, `MacacoWatermark`, `VideoTrimDialog`.

### `ui/theme/` — Material 3 theming
`Theme.kt` (`WanderlogTheme`), `AppTheme` (user-selectable theme enum, DataStore-persisted, optional
custom background image), `Type`, `Gradients`, `MapTheme`, `ContentWidth`.

### `ui/widget/` — home-screen widgets
`AppWidgetProvider`s (`OnThisDayWidgetProvider`, `RecentEntriesWidgetProvider` +
`RecentEntriesWidgetService`, `TravelStatsWidgetProvider`, `QuickAddWidgetProvider`) plus
`WidgetIntents`, `WidgetPhotos`. Widget row taps deep-link into `MainActivity`.

---

## 5. Export, rendering & media (`data/sync/` + `util/`)

Not network APIs, but the app's other "output" logic:

- **`JournalBackup`** — full local backup/restore as one SAF-picked `.zip` (`backup.json` + photo
  bytes); premium.
- **`PrintBookExporter`** — renders a printable book (Print Book feature).
- **`AdventureReelEncoder`** — encodes an "adventure reel" video from entry media.
- **`YearRecapRenderer`** — renders the Year-in-Travel recap.
- **`util/ImageStorage`** — persists picked/captured photos into shared **Pictures/Macaco**
  MediaStore (survives uninstall), handles camera temp files (FileProvider) and best-effort delete.
- **`util/VideoTranscoder` / `VideoThumbnails` / `VideoTrimDialog`** — video import/trim/thumbs.
- **`util/WeatherLookup`** — Open-Meteo historical lookup (see §3).
- **`util/Cities`** — offline bundled city autocomplete (gzipped asset).
- **Reminders (WorkManager):** `ReminderScheduler` + `ReminderWorker` (write-an-entry reminders),
  `RenewalReminderScheduler` + `RenewalReminderWorker` (subscription renewal), `SnoozeReceiver`,
  `NotificationCopy`.
- **`util/AppActions`** — share/support/in-app-review intents; hosts the privacy/terms URLs.
- **`util/PhotoRollScanner`, `SuggestedTags`, `WidgetPins`, `RenewalReminder…`** — misc helpers.

---

## 6. Data flow, end to end

```
Firestore  ──listen──►  CloudEntrySync ──StateFlow──►  JournalViewModel ──StateFlow──►  Compose screens
   ▲                                                        │  │  │
   └────────────── save/delete ─────────────────────────────┘  │  │
Google Drive ◄── auto-upload on save · download-missing ── DrivePhotoSync
RevenueCat   ──► BillingManager.isPremium ──► gate features / paywall
DataStore    ──► PreferencesManager ──► theme, reminders, app-lock, billing fallback
```

- **Reads:** Firestore snapshot → `CloudEntrySync.entries` → `JournalViewModel.entries` →
  `collectAsState()` in screens. Missing photos are pulled from Drive into a cache and merged in via
  `cachedDrivePhotos`.
- **Writes:** screen → `JournalViewModel.saveEntry` → `CloudEntrySync.save` (Firestore) →
  fire-and-forget Drive upload + lazy weather fetch → widgets refreshed.
- **Auth changes** re-drive everything: `CloudEntrySync` re-subscribes, `BillingManager` re-identifies,
  `NavGraph` re-gates.

---

## 7. Conventions & constraints

- **No Room/SQLite** — Firestore is the database; the only local-file logic is the one-time
  `LegacyEntryMigration` of the old `entries.json`.
- **Coroutines:** `viewModelScope` for ViewModel suspend calls; `CloudEntrySync` and
  `BillingManager` own their own `CoroutineScope`s (IO / Main + `SupervisorJob`).
- **Screens never call repositories or the network directly** — always through the ViewModel.
- **Serialization:** kotlinx.serialization (JSON) for `TravelEntry`.
- **Images:** Coil `AsyncImage` over `content://` URIs.
- **R8/release caution:** reflectively-used constructors have been stripped before — always launch
  the R8'd release build on-device before shipping.

See `CLAUDE.md` for build commands, billing/keys setup, release pipeline, and backup/recovery.
