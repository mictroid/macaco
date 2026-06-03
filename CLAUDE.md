# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Wanderlog** — a cloud-synced travel journal Android app. Entries sync per-user via Firebase
(Firestore + Auth), and premium access is gated by a RevenueCat in-app purchase. Single-module,
single-activity, Compose-only UI.

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
./gradlew test --tests "com.example.myapplication.ExampleUnitTest"
```

## Architecture

**MVVM + Repository**, pure Kotlin Flow (no LiveData, no Hilt).

```
TravelJournalApp (Application)            ← manual service locator
  ├── preferencesManager: PreferencesManager  ← DataStore (theme, profile, local-billing fallback)
  ├── authRepository: AuthRepository           ← FirebaseAuthRepository (active)
  ├── cloudEntrySync: CloudEntrySync           ← Firestore-backed entry sync (per uid)
  └── billingManager: BillingManager           ← RevenueCat entitlement gate

MainActivity
  └── JournalViewModel (via custom Factory)
        ├── StateFlow<List<TravelEntry>>   (from CloudEntrySync)
        ├── StateFlow<Boolean?> isPurchased (from BillingManager.isPremium; null = loading)
        ├── StateFlow<UserProfile?> currentUser
        └── theme / profile-photo state

NavGraph (Compose Navigation)
  ├── JournalListScreen
  ├── NewEditEntryScreen (shared for create + edit, receives optional entryId)
  ├── EntryDetailScreen
  ├── LoginScreen
  ├── ProfileScreen
  ├── SettingsScreen
  ├── SubscriptionInfoScreen
  └── PurchaseScreen  ← shown until isPurchased == true
```

**Dependency injection:** manual constructor injection via `TravelJournalApp` as a service locator.
`JournalViewModel.Factory` takes `CloudEntrySync`, `PreferencesManager`, `AuthRepository`, and
`BillingManager` explicitly — no Hilt/Dagger.

## Data Layer

- **`TravelEntry`** (`data/model/`) — `@Serializable` data class, UUID id, immutable. Fields:
  `title`, `location`, `dateMillis`, `description`, `mood`, `photoUris`, `tags`, `createdAt`.
- **`CloudEntrySync`** (`data/storage/`) — the active entry store. Listens to
  `users/{uid}/entries` in Firestore (ordered by `createdAt` desc) and exposes
  `StateFlow<List<TravelEntry>>`. Re-subscribes on auth change; clears to empty list when signed
  out. `save`/`delete` write to the signed-in user's subcollection. Snapshot errors are swallowed
  (no error UI). **Photos are stored as local content URIs**, so images are only visible on the
  device they were added from.
- **`LegacyEntryMigration`** (`data/storage/`) — one-time import of entries from the legacy local
  `filesDir/entries.json` (written by older on-device app versions) into the signed-in user's cloud
  account, then renames the file to `.imported` so it never runs again. The old `EntryStorage`
  class that wrote that file has been removed.
- **`PreferencesManager`** (`data/`) — DataStore. Keys: `is_purchased` (local billing fallback),
  `dark_mode`, `profile_photo_uri`, `app_theme`, `theme_image_uri`.

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

Routes are a sealed class in `Screen.kt`. `NavGraph.kt` wires all composable destinations and owns
argument passing (entry IDs passed as string nav args). `NewEditEntryScreen` handles both create
and edit depending on whether an `entryId` arg is present.

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
  `google-services.json` + `com.google.gms.google-services` plugin. Supports Google (Credential
  Manager), Apple (OAuthProvider), and Email/Password.
- **`MockAuthRepository`** — simulates auth locally; swap into `TravelJournalApp.kt` for
  development without Firebase. Currently **not** wired in.

`google-services.json` is present (project `wanderlog-11d28`) and the plugin is applied, so Firebase
initializes automatically. `FirebaseConfig.kt` holds the project values (filled in) and is now used
only for `GOOGLE_WEB_CLIENT_ID` in `LoginScreen` (Google Sign-In). For Google Sign-In on a new
machine, add the debug SHA-1 to the Firebase console:
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
| Google Sign-In | AndroidX Credential Manager + googleid |
| Billing | RevenueCat (`com.revenuecat.purchases`) over Google Play Billing |
| Async | Kotlin Coroutines + coroutines-play-services |

- **Min SDK:** 24 · **Target/Compile SDK:** 36
- **No Room/SQLite** — Firestore is the cloud database; the only remaining local-file logic is the
  one-time `LegacyEntryMigration` import of the old `entries.json`.
</content>
</invoke>
