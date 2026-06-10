# Changelog

All notable changes to Macaco are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/). The app `versionName` is `1.2`
(`versionCode` 3); debug builds are mirrored to Drive as `app-debugv1.NN.apk`.

## 2026-06-10

### Infrastructure
- **Migrated off the legacy `wanderlog-11d28` Firebase/GCP project onto the new
  `macaco-499016` project** and changed the Play `applicationId` from
  `com.houseofmmminq.wanderlog` to `com.houseofmmminq.macaco`. Swapped
  `google-services.json`, updated `FirebaseConfig.kt`, and refreshed CLAUDE.md.
  Firebase Auth users (3) and Firestore entries (15) were copied across with
  UIDs preserved, so existing accounts resolve their data after signing in.
  Published the per-user Firestore security rule and enabled the Drive API on
  the new project. (`ebd219f`)

### Added
- Official multicolor Google "G" logo on the sign-in button. (`5a53fa7`)
- **"Adventures" drawer item** — a labelled entry pointing to the journal/entries
  list; closes the menu and clears any active tag filter. Localized in all 11
  languages.

### Changed
- **Branded the profile and settings screens** with the splash identity: a teal
  header banner (gold "macaco" wordmark) on both, the profile avatar overlapping
  the banner, and a teal footer band with the monkey face at the bottom of the
  profile.
- **Back from a drawer-launched screen reopens the menu.** Pressing back from
  Profile/Settings/Subscription/Help now returns to the list with the drawer open.
- **Rebranded the remaining off-brand screens** to the Macaco splash identity
  (teal radial + gold "macaco" wordmark): the purchase/paywall screen (replacing
  the ✈️ hero), the subscription-info premium band (replacing the ⭐ band), and
  the Help/About header. Also swapped the ✈️ in shared-entry text for 🐒.
- **Rebranded the login screen** to the Macaco splash identity: deep-teal radial
  header, monkey icon, gold "macaco" wordmark and slogan — matching the journal
  header and drawer. Replaced the old plane-emoji hero; header now runs
  edge-to-edge under the status bar. (`5a53fa7`)
- **Lifted the entry list and drawer out of flat white**: separated card surface
  from page background in both themes, brand-teal drawer icons, gold month-section
  headers, a faint teal background wash, gold date pills, and teal mood-circle
  badges. (`54fa899`)

### Fixed
- **Crash when saving an entry with photos.** The save-entry auto-upload ran the
  Drive call on the main thread (`GoogleAccountCredential.getToken` hard-crashes
  there); moved it onto `Dispatchers.IO`. Also, a failing Drive call (e.g. the
  Drive API disabled → 403) crashed the fire-and-forget upload coroutine — it now
  catches the failure and surfaces it via the error snackbar instead. (`7c3f530`)
