# Brief: Add Firebase Crashlytics and Analytics

**Priority:** High (Crashlytics essential before production; Analytics for future insight)  
**Files:**
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `build.gradle.kts` (project-level, for Crashlytics plugin)

## Rationale

- **Crashlytics**: Essential for production monitoring. Automatically captures crashes and ANRs
  with stack traces, device info, and session context. No code changes needed beyond adding the
  dependency and plugin — it instruments automatically.
- **Analytics**: Firebase Analytics logs automatic events (app open, session start, first open,
  user engagement) with zero extra code. Provides retention, engagement, and funnel data in the
  Firebase console. RevenueCat already handles purchase analytics; this adds behavioural signals.

## Changes

### 1. `gradle/libs.versions.toml` — add Crashlytics plugin version and library aliases

In `[versions]`:
```toml
firebaseCrashlyticsPlugin = "3.0.3"
```

In `[libraries]`:
```toml
firebase-crashlytics = { group = "com.google.firebase", name = "firebase-crashlytics-ktx" }
firebase-analytics = { group = "com.google.firebase", name = "firebase-analytics-ktx" }
```

In `[plugins]`:
```toml
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "firebaseCrashlyticsPlugin" }
```

### 2. `build.gradle.kts` (project-level) — add Crashlytics plugin to classpath

```kotlin
plugins {
    // ... existing plugins ...
    alias(libs.plugins.firebase.crashlytics) apply false
}
```

### 3. `app/build.gradle.kts` — apply plugin and add dependencies

Apply the plugin:
```kotlin
plugins {
    // ... existing plugins ...
    alias(libs.plugins.firebase.crashlytics)
}
```

Add dependencies inside the `dependencies { }` block, under the existing Firebase BOM section:
```kotlin
implementation(libs.firebase.crashlytics)
implementation(libs.firebase.analytics)
```

Both are covered by the existing `platform(libs.firebase.bom)` import, so no version numbers
are needed in the dependency declarations.

## No code changes required

Crashlytics and Analytics both initialise automatically when the Firebase BOM is present and
`google-services.json` is configured (which it already is for project `macaco-499016`).

- Crashlytics captures all uncaught exceptions and ANRs automatically.
- Analytics logs automatic events (first_open, session_start, app_remove, etc.) automatically.
- No `FirebaseAnalytics.getInstance(context)` calls or manual logging needed unless custom events
  are desired later.

## Note for privacy policy

After this change, `privacy-policy.html` **must** be updated:
- Line ~88: remove "We do not... use analytics SDKs" — replace with a statement that Firebase
  Analytics is used for aggregate usage insights (see `code-brief-privacy-policy-update.md`).
- Add Firebase Analytics to the third-party services table.
- Add Firebase Crashlytics to the third-party services table.
