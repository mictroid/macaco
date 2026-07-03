# Macaco — JournalViewModel: Don't Overwrite Edits When a Drive Upload Finishes

Fixes a race where the background Drive-upload completion re-saves a stale copy of the entry,
silently reverting any edit the user made while the upload was in flight. Touches
`JournalViewModel.kt` only.

---

## Change 1 — saveEntry: merge driveFileIds into the LATEST entry

**Problem:** `saveEntry` launches `uploadEntryPhotosOrReport(entry)` in the background. On
completion it writes `cloudEntrySync.save(entry.copy(driveFileIds = updatedIds))` — the whole
entry *as captured when the save started*. A multi-photo upload can take minutes; if the user
edits the entry meanwhile (title, description, photos), the completion callback overwrites
their edit with the old content.

**Fix:** On completion, look up the current version of the entry and merge only the
`driveFileIds`. If the photo list changed since the upload started, skip the write entirely —
the indices no longer match and the next save/sync will re-upload correctly.

```kotlin
// BEFORE (JournalViewModel.saveEntry, ~line 292)
    fun saveEntry(entry: TravelEntry) {
        viewModelScope.launch {
            // On edit, free the files for any photos the user dropped from the entry.
            entries.value.find { it.id == entry.id }?.let { old ->
                ImageStorage.delete(appContext, old.photoUris - entry.photoUris.toSet())
            }
            cloudEntrySync.save(entry)
            // Upload new photos to Drive in the background; persist updated IDs when done.
            if (entry.photoUris.isNotEmpty()) {
                launch {
                    val updatedIds = drivePhotoSync.uploadEntryPhotosOrReport(entry)
                    if (updatedIds != entry.driveFileIds) {
                        cloudEntrySync.save(entry.copy(driveFileIds = updatedIds))
                    }
                }
            }
        }
    }

// AFTER
    fun saveEntry(entry: TravelEntry) {
        viewModelScope.launch {
            // On edit, free the files for any photos the user dropped from the entry.
            entries.value.find { it.id == entry.id }?.let { old ->
                ImageStorage.delete(appContext, old.photoUris - entry.photoUris.toSet())
            }
            cloudEntrySync.save(entry)
            // Upload new photos to Drive in the background; persist updated IDs when done.
            if (entry.photoUris.isNotEmpty()) {
                launch {
                    val updatedIds = drivePhotoSync.uploadEntryPhotosOrReport(entry)
                    if (updatedIds != entry.driveFileIds) {
                        // Merge into the LATEST entry — the user may have edited it while the
                        // upload ran; writing the captured `entry` back would revert that edit.
                        val latest = entries.value.find { it.id == entry.id } ?: entry
                        // Ids are positional to the photo list captured at upload start; if the
                        // photos changed since, skip — the next save re-uploads correctly.
                        if (latest.photoUris == entry.photoUris) {
                            cloudEntrySync.save(latest.copy(driveFileIds = updatedIds))
                        }
                    }
                }
            }
        }
    }
```

**File:** `JournalViewModel.kt`

---

## Change 2 — syncPhotosToGoogleDrive: same merge in the syncAll callback

**Problem:** `DrivePhotoSync.syncAll(entries.value) { … }` captures the entry list at sync
start; `onEntryUpdated` hands back `entry.copy(driveFileIds = newIds)` built from that stale
snapshot. A full sync over many entries runs long — same revert risk as Change 1.

**Fix:** Merge by id against the live list in the callback.

```kotlin
// BEFORE (~line 412)
    fun syncPhotosToGoogleDrive() {
        drivePhotoSync.syncAll(entries.value) { updated ->
            cloudEntrySync.save(updated)
        }
    }

// AFTER
    fun syncPhotosToGoogleDrive() {
        drivePhotoSync.syncAll(entries.value) { updated ->
            // `updated` was built from the entry list captured at sync start; merge only the
            // driveFileIds into the live entry so a mid-sync user edit isn't reverted.
            val latest = entries.value.find { it.id == updated.id } ?: updated
            if (latest.photoUris == updated.photoUris) {
                cloudEntrySync.save(latest.copy(driveFileIds = updated.driveFileIds))
            }
        }
    }
```

**File:** `JournalViewModel.kt`

---

## Scope notes

- No change inside `DrivePhotoSync` — its callback contract stays the same.
- Depends conceptually on `code-brief-drive-ids-alignment.md` (both protect the same
  invariant) but neither blocks the other; implement both.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Upload completion merges `driveFileIds` into the latest entry, skips on photo-list change | `JournalViewModel.kt` |
| 2 | Same merge in the `syncAll` → `onEntryUpdated` callback | `JournalViewModel.kt` |
