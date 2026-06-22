# Macaco — MainActivity: Samsung In-App Update Requires Two Taps (v2)

The previous brief (`docs/DONE/code-brief-inapp-update-and-backup-transitions.md`) implemented
`finish()` before `completeUpdate()` to avoid a black screen. On Samsung devices this causes
`completeUpdate()` to fail silently — the activity is being destroyed when Play tries to start
the installer overlay, so the installation never runs. The user must tap "Restart" a second time
before it works. File touched: `MainActivity.kt`.

---

## Background

The previous fix added:
```kotlin
if (result == SnackbarResult.ActionPerformed) {
    finish()                        // ← closes activity
    appUpdateManager.completeUpdate()  // ← called after finish()
}
```

`completeUpdate()` for a FLEXIBLE update needs an active foreground activity to attach the
system installer overlay. By calling `finish()` first, the activity begins destroying itself.
On Samsung One UI, the Play Core library checks the activity state before attaching, finds it
finishing, and silently aborts — no install starts. The update stays in `DOWNLOADED` state.

Next time the user opens the app:
1. `onResume()` → `checkForUpdate()` → `installStatus() == DOWNLOADED` → `updateReady = true`
2. Snackbar shows again
3. User taps "Restart" a second time → sometimes `completeUpdate()` succeeds this time (timing
   difference), sometimes it takes more attempts

The black screen concern that motivated `finish()` is already handled: `android:windowBackground`
is set to `@color/splash_background` (teal) in `themes.xml`, so the window shown during the
Play installer transition is already branded, not black.

---

## Fix: remove `finish()`, add `addOnFailureListener` to catch silent failures

**Problem:** `finish()` races with `completeUpdate()` on Samsung, causing silent failures.

**Fix:** Remove `finish()`. Let Play Core handle the lifecycle. Add an
`addOnFailureListener` to `completeUpdate()` so failures are logged (and if needed, re-surfaced
as a snackbar on the next `onResume` via the existing `DOWNLOADED` check).

```kotlin
// MainActivity.kt — replace the LaunchedEffect(updateReady) block:

LaunchedEffect(updateReady) {
    if (updateReady) {
        val result = snackbarHostState.showSnackbar(
            message = updateMessage,
            actionLabel = updateAction,
            duration = SnackbarDuration.Indefinite
        )
        // Reset before acting so a later onResume can re-set it to true and show a
        // fresh snackbar (LaunchedEffect only re-fires on a value change).
        updateReady = false
        if (result == SnackbarResult.ActionPerformed) {
            // Do NOT call finish() here — it races with completeUpdate() on Samsung
            // and causes the installer to abort silently. The windowBackground teal
            // already handles the visual transition; Play restarts the app when done.
            appUpdateManager.completeUpdate()
                .addOnFailureListener { e ->
                    // Installation failed (rare). Log it; the updateReady=false reset
                    // above means the snackbar won't reappear until the next onResume
                    // detects installStatus == DOWNLOADED and sets updateReady = true.
                    android.util.Log.e("MainActivity", "completeUpdate failed", e)
                }
        }
    }
}
```

No other changes needed. The `updateFlowStarted` guard (prevents double-triggering the
download flow) and `updateReady = false` reset (allows re-showing snackbar if install doesn't
proceed) remain in place.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove `finish()` before `completeUpdate()`; add `.addOnFailureListener` for logging | `MainActivity.kt` |
