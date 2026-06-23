# Macaco — JournalViewModel: Clear Profile Photo on Sign-Out

The locally stored profile photo is never cleared when a user signs out, so a subsequent
sign-in with any account inherits the previous user's custom photo. One-line fix in
`JournalViewModel.kt`'s auth-change listener.

---

## Change: clear `profilePhotoUri` on sign-out

**Problem:** `profile_photo_uri` in DataStore is account-agnostic — it has no UID binding and is
never removed on sign-out. When User A sets a custom profile photo, signs out, and User B signs
in, `profilePhotoUri` still emits User A's photo because nothing cleared it. In single-account
usage this is invisible; on a device where multiple accounts are tested (or on a shared device)
the wrong photo appears.

**Fix:** In the existing `authRepository.currentUser.collect` block in `JournalViewModel.init`,
detect the sign-out moment (`user == null`) and call `preferencesManager.setProfilePhotoUri(null)`.
`setProfilePhotoUri(null)` already removes the DataStore key — no new method needed.

The relevant block currently reads:

```kotlin
// JournalViewModel.kt — inside init { }, the authRepository listener:
var lastUid: String? = null
authRepository.currentUser.collect { user ->
    // On any account change (including sign-out), drop the Drive sync's per-account
    // cached folder id + photo cache so backups don't target the previous account.
    if (user?.uid != lastUid) {
        drivePhotoSync.onAccountChanged()
        lastUid = user?.uid
    }
    if (user != null) LegacyEntryMigration.run(appContext, cloudEntrySync)
}
```

Change to:

```kotlin
var lastUid: String? = null
authRepository.currentUser.collect { user ->
    if (user?.uid != lastUid) {
        drivePhotoSync.onAccountChanged()
        // On sign-out, clear the locally stored profile photo so the next account that
        // signs in starts with a clean slate. setProfilePhotoUri(null) removes the DataStore
        // key; if no custom photo has ever been set this is a no-op.
        if (user == null) {
            preferencesManager.setProfilePhotoUri(null)
        }
        lastUid = user?.uid
    }
    if (user != null) LegacyEntryMigration.run(appContext, cloudEntrySync)
}
```

**Scope notes:**
- This only clears the *custom* profile photo (the one the user picks in ProfileScreen and is
  persisted to `filesDir` via `ImageStorage.persist`). The Firebase `photoUrl` (Google account
  avatar shown when no custom photo is set) is per-account automatically — it is not affected.
- If the same user signs back in after signing out, their custom photo will be gone and the
  Google avatar will show instead. This is the correct and expected trade-off: the custom photo
  is a local-only override and is not synced, so it is already lost on reinstall anyway.
- No UI changes needed — `profilePhotoUri` emitting `null` already falls through to showing
  the Firebase `photoUrl` in `ProfileScreen`.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `if (user == null) preferencesManager.setProfilePhotoUri(null)` inside the UID-change block in the auth listener | `ui/viewmodel/JournalViewModel.kt` |
