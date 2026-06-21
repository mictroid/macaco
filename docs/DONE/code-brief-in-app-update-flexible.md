# Macaco — MainActivity: Flexible In-App Update

Adds the Google Play flexible in-app update flow to `MainActivity.kt` so users are prompted
to download and apply updates without leaving the app. Touches `libs.versions.toml`,
`app/build.gradle.kts`, and `MainActivity.kt`.

---

## 1. Add dependency

**Problem:** The Play In-App Update library is not yet in the project.

**Fix:** Add a version alias and library alias for `app-update-ktx`, then declare the
implementation dependency.

### `gradle/libs.versions.toml`

In `[versions]`, add:
```toml
playInAppUpdate = "2.1.0"
```

In `[libraries]`, add:
```toml
play-in-app-update-ktx = { group = "com.google.android.play", name = "app-update-ktx", version.ref = "playInAppUpdate" }
```

### `app/build.gradle.kts`

In the `dependencies { }` block (alongside the existing `play-review-ktx` entry):
```kotlin
implementation(libs.play.in.app.update.ktx)
```

---

## 2. Implement flexible update flow in MainActivity

**Problem:** The app has no mechanism to notify users that a newer version is available on
Google Play. Users only discover updates by chance via the Play Store app.

**Fix:** In `MainActivity`, register an `ActivityResultLauncher` for the update intent, check
for an available update in `onResume()`, and show an Android `Snackbar` (on the root view)
when the flexible download completes — prompting the user to restart and apply.

### Full changes to `MainActivity.kt`

Add these imports at the top:
```kotlin
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.material.snackbar.Snackbar
```

Add Material dependency if not already present (check `build.gradle.kts` — it may already be
pulled in transitively via AppCompat):
```kotlin
implementation("com.google.android.material:material:1.12.0")
```

### In the `MainActivity` class body, add:

```kotlin
private val appUpdateManager by lazy { AppUpdateManagerFactory.create(this) }

// Launcher for the flexible update UI (the Play Store in-app overlay).
private val updateLauncher = registerForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
) { /* flexible: no action needed on result; download continues in background */ }

// Listens for download state changes while the app is in the foreground.
private val installStateListener = InstallStateUpdatedListener { state ->
    if (state.installStatus() == InstallStatus.DOWNLOADED) {
        showUpdateReadySnackbar()
    }
}
```

### Override `onResume()`:

```kotlin
override fun onResume() {
    super.onResume()
    appUpdateManager.registerListener(installStateListener)
    checkForUpdate()
}
```

### Override `onPause()`:

```kotlin
override fun onPause() {
    super.onPause()
    appUpdateManager.unregisterListener(installStateListener)
}
```

### Add private helper functions:

```kotlin
private fun checkForUpdate() {
    appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
        when {
            // A new version is available — start the flexible download.
            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                )
            }
            // Update was downloaded in a previous session — prompt to complete.
            info.installStatus() == InstallStatus.DOWNLOADED -> {
                showUpdateReadySnackbar()
            }
        }
    }
}

private fun showUpdateReadySnackbar() {
    Snackbar.make(
        window.decorView.rootView,
        getString(R.string.update_ready_message),
        Snackbar.LENGTH_INDEFINITE
    ).setAction(getString(R.string.update_ready_action)) {
        appUpdateManager.completeUpdate()
    }.show()
}
```

---

## 3. String resources

Add to `strings.xml` and all 11 language translation files:

| Key | EN value |
|-----|----------|
| `update_ready_message` | An update is ready to install |
| `update_ready_action` | Restart |

---

## Scope notes

- **Flexible only** — no immediate (forced) update. Macaco has no breaking data migrations
  that would require forcing an update.
- The update check fires on every `onResume()`. The Play library rate-limits how often the
  overlay is actually shown — repeated checks do not spam the user.
- On sideloaded/debug builds, `appUpdateInfo` returns `UPDATE_NOT_AVAILABLE` (Play is not
  involved). No special debug handling is needed.
- If the `material` library is already a transitive dependency, no extra entry in
  `build.gradle.kts` is needed for it — check before adding.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `playInAppUpdate` version + `play-in-app-update-ktx` alias | `gradle/libs.versions.toml` |
| 2 | Add `implementation(libs.play.in.app.update.ktx)` | `app/build.gradle.kts` |
| 3 | Add update manager, launcher, listener, `onResume`/`onPause`, helpers | `MainActivity.kt` |
| 4 | Add `update_ready_message` + `update_ready_action` strings | `strings.xml` ×11 |
