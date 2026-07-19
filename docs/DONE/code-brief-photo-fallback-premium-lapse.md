# Macaco — Entries with broken photos after premium/trial lapses

Fixes entries showing broken/empty images once a user's premium or trial expires. Root cause:
`JournalViewModel`'s auto-download of Drive-backed photos is gated behind `isPurchased == true`,
so as soon as an account lapses, the app stops pulling any photo whose local URI doesn't resolve
on this device (typically because the entry was created on a different device, or the device's
gallery entry was removed) — even though those photos were already legitimately uploaded to Drive
while the account was premium. Touches `JournalViewModel.kt` only.

---

## Change 1 — Stop gating the Drive photo *download* behind premium; keep the *upload* gate as-is

**Problem:** `JournalViewModel.kt:478-489` only calls `drivePhotoSync.downloadMissingPhotos(entryList)`
when `isPurchased.value == true`:

```kotlin
// BEFORE — JournalViewModel.kt:478-489
// When the entry list changes, download any Drive photos missing on this device.
viewModelScope.launch {
    cloudEntrySync.entries.collect { entryList ->
        if (isPurchased.value == true) {
            drivePhotoSync.downloadMissingPhotos(entryList)
            // Photos just cached from Drive — refresh the photo-showing widgets so they
            // display them (on a device that didn't add the photo locally).
            OnThisDayWidgetProvider.requestUpdate(appContext)
            RecentEntriesWidgetProvider.requestUpdate(appContext)
        }
    }
}
```

`EntryDetailScreen.kt`'s `displayPhotoUri` (line ~1219) already falls back correctly:

```kotlin
private fun TravelEntry.displayPhotoUri(index: Int, cached: Map<String, String>): String? =
    driveFileIds.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let { cached[it] }
        ?: photoUris.getOrNull(index)
```

— but `cached` only ever gets populated by `downloadMissingPhotos`, and `photoUris` (the local
URI) is frequently *not* readable on a device other than the one the photo was taken on. So the
one path that could resolve the photo is the one premium-gates away. Net effect: a lapsed user
who synced entries across two devices, or whose local gallery copy was removed, sees a broken
image with no way to recover it short of re-purchasing.

This gate is also inconsistent with intent: `docs/DONE/code-brief-premium-gating-corrections.md`
deliberately gates Drive **backup** (connecting an account, uploading new photos) behind premium
because that's the feature with ongoing cost. Read-only *retrieval* of photos a premium account
already paid to upload isn't a new cost center in the same way, and silently disappearing photos
a user already owns is a worse experience than a soft paywall would be.

**Fix:** Let `downloadMissingPhotos` run regardless of purchase state — it only restores photos
that were already uploaded while the account was premium, so it doesn't grant any *new* premium
capability. Leave every other Drive gate untouched: `saveEntry`'s upload path
(`JournalViewModel.kt:507`, `isPurchased.value == true && ...`), `syncPhotosToGoogleDrive`
(`:679-680`, early `return` when not purchased), and `SettingsScreen.kt`'s `DriveBackupCard`
connect gating all stay exactly as they are — this brief only removes the gate on the passive,
already-entitled restore path.

```kotlin
// AFTER — JournalViewModel.kt:478-489
// When the entry list changes, download any Drive photos missing on this device. Deliberately
// NOT gated on isPurchased: this only restores photos a (possibly now-lapsed) account already
// uploaded to Drive while premium — it grants no new capability, and silently showing broken
// images for photos the user already owns is worse than letting them keep seeing what they paid
// to back up. New uploads and new Drive connections remain premium-gated (saveEntry,
// syncPhotosToGoogleDrive, SettingsScreen's DriveBackupCard).
viewModelScope.launch {
    cloudEntrySync.entries.collect { entryList ->
        drivePhotoSync.downloadMissingPhotos(entryList)
        // Photos just cached from Drive — refresh the photo-showing widgets so they
        // display them (on a device that didn't add the photo locally).
        OnThisDayWidgetProvider.requestUpdate(appContext)
        RecentEntriesWidgetProvider.requestUpdate(appContext)
    }
}
```

Note: `drivePhotoSync.downloadMissingPhotos` internally no-ops safely if the account was never
Drive-connected (no folder id / no permitted scope), so this doesn't attempt network calls for
accounts that never used Drive backup — see `DrivePhotoSync.kt:80-83` (`isDriveConnected`) and the
`downloadMissing` body it calls into.

**Explicitly out of scope:** re-enabling *new* uploads, re-enabling the manual "Sync Now" button,
or changing `SettingsScreen`'s premium-gated Drive connect flow for users who never connected.
Those all correctly require premium today and should keep doing so — this brief only stops
already-backed-up photos from vanishing when premium lapses.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove the `isPurchased == true` gate around the auto `downloadMissingPhotos` call so previously Drive-backed photos keep resolving after premium/trial lapses; upload/connect paths remain gated | `JournalViewModel.kt` |
