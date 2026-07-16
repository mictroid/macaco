# Macaco — Add Firebase App Check (Play Integrity)  [recommended hardening]

Adds Firebase App Check with the Play Integrity provider so Firebase backend calls (Firestore, Auth,
Identity Toolkit) are accepted only from genuine, unmodified installs of the app. This is the
strongest **code-side** mitigation for the extractable-API-key finding (security review
`docs/security-review-2026-07-16.md`, M1/M2): even if someone lifts the API key out of the APK, App
Check attestation stops them replaying it from a script or a repackaged app.

**Scope note / when to do this:** this is optional hardening, not a fix for an active vulnerability —
user data is already protected by the Firestore rules + Auth. It's a larger lift than the other briefs
(SDK + init + console registration + a monitored rollout), so it's fine to defer. Restricting the API
keys (M1/M2, console-side) is the quick win; App Check is the durable one. Do the console registration
**before** enforcing, and roll out in **monitor mode first** so you don't lock out real users.

---

## Change 1 — Add the App Check dependencies

In `gradle/libs.versions.toml`, add (versions resolve via the existing Firebase BOM — no explicit
version needed on the artifacts):

```toml
# [libraries]
firebase-appcheck-playintegrity = { group = "com.google.firebase", name = "firebase-appcheck-playintegrity" }
firebase-appcheck-debug         = { group = "com.google.firebase", name = "firebase-appcheck-debug" }
```

In `app/build.gradle.kts` dependencies:

```kotlin
implementation(libs.firebase.appcheck.playintegrity)
debugImplementation(libs.firebase.appcheck.debug)   // debug builds use the debug provider
```

**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`.

---

## Change 2 — Install the App Check provider at app startup

In `TravelJournalApp.onCreate()` (the Application/service-locator class), **after** Firebase is
initialized and **before** the first Firestore/Auth call, install the provider. Use the debug provider
for debug builds and Play Integrity for release:

```kotlin
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.ktx.Firebase

// in onCreate(), early:
Firebase.appCheck.installAppCheckProviderFactory(
    if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
    else PlayIntegrityAppCheckProviderFactory.getInstance()
)
```

On first debug run, Logcat prints a **debug token** (`DebugAppCheckProvider`); register that token in
the console (Change 3) so your debug builds keep working once enforcement is on.

**File:** `TravelJournalApp.kt`.

---

## Change 3 — Register App Check in the Firebase console (your step, before enforcing)

Not a code change — listed so it isn't skipped:

- Firebase console → **App Check** → register the Android app with the **Play Integrity** provider
  (this ties attestation to the app's Play signing identity).
- App Check → **Apps → Manage debug tokens** → add the debug token from Change 2's Logcat output.
- Leave Firestore/Auth enforcement in **monitor mode** initially. Watch the App Check metrics for a
  release cycle or two; only flip **enforce** once verified requests dominate — otherwise older or
  non-Play installs get locked out.

---

## Verification

- Debug build: Firestore reads/writes still work with the debug provider + registered debug token.
- Release build via a Play track: App Check metrics show **verified** requests for Firestore and Auth.
- Do **not** enable enforcement until monitor-mode metrics confirm real traffic is attesting cleanly.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add Play Integrity + debug App Check dependencies | `libs.versions.toml`, `app/build.gradle.kts` |
| 2 | Install the App Check provider at startup (debug vs. Play Integrity) | `TravelJournalApp.kt` |
| 3 | Register the app + debug token in console; roll out in monitor mode first | — (your step) |

**Owner note:** Changes 1–2 are code. Change 3 and the monitor→enforce flip are yours, on the live
project.
