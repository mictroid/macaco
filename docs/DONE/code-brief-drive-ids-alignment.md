# Macaco — Entry Editing: Keep photoUris ↔ driveFileIds Aligned

Fixes a data-integrity bug where editing an entry's photos desyncs the positional
`driveFileIds` list from `photoUris`, mis-pairing photos with other photos' Drive files.
Touches `NewEditEntryScreen.kt` and `EntryDetailScreen.kt`.

**Background (read first):** `TravelEntry.driveFileIds` is parallel to `photoUris` by index;
`""` means "not uploaded yet". Every mutation of one list must mirror the other. The detail
screen's helpers (`withCover`/`withRemoved`/`withSwapped`) try to do this but skip the driveIds
mutation when that list is shorter; the edit screen doesn't do it at all.

---

## Change 1 — Edit screen: mirror every photo mutation into a parallel driveIds state

**Problem:** In `NewEditEntryScreen`, photos can be added (picker/camera), removed (×), and
reordered (long-press drag) — all of which mutate only `photoUris`. On Save the entry is built
with `driveFileIds = existingEntry?.driveFileIds ?: emptyList()`, i.e. the *original* order.
Any edit that changes photo order/count silently mis-pairs every photo with another photo's
Drive backup: wrong photos after a reinstall, wrong photos in backup export, and a new photo
occupying a slot with a stale ID never uploads.

**Fix:** Add a `driveIds` state list, initialised padded to `photoUris.size` with `""`, and
mirror every mutation. On Save, pass it as `driveFileIds`. Invariant to keep at all times:
`driveIds.size == photoUris.size`.

```kotlin
// BEFORE (state declarations, ~line 157)
    var photoUris by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(existingEntry?.photoUris ?: emptyList())
    }

// AFTER
    var photoUris by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(existingEntry?.photoUris ?: emptyList())
    }
    // Parallel Drive-file ids, padded to photoUris length ("" = not uploaded). Every photo
    // add/remove/reorder below mutates BOTH lists so the pairing survives editing.
    var driveIds by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(
            List(existingEntry?.photoUris?.size ?: 0) { i ->
                existingEntry?.driveFileIds?.getOrNull(i) ?: ""
            }
        )
    }
```

Picker add (~line 183). Note `.distinct()` is dropped — `persistToGallery` always creates a
fresh uniquely-named copy, so duplicates cannot occur, and `distinct()` would break the
parallel-list invariant:

```kotlin
// BEFORE
    ) { uris ->
        val stored = uris.mapNotNull { ImageStorage.persistToGallery(context, it) }
        sessionAdded = sessionAdded + stored
        photoUris = (photoUris + stored).distinct()
    }

// AFTER
    ) { uris ->
        val stored = uris.mapNotNull { ImageStorage.persistToGallery(context, it) }
        sessionAdded = sessionAdded + stored
        photoUris = photoUris + stored
        driveIds = driveIds + List(stored.size) { "" }
    }
```

Camera add (~line 196):

```kotlin
// BEFORE
            ImageStorage.persistToGallery(context, captured)?.let { stored ->
                sessionAdded = sessionAdded + stored
                photoUris = (photoUris + stored).distinct()
            }

// AFTER
            ImageStorage.persistToGallery(context, captured)?.let { stored ->
                sessionAdded = sessionAdded + stored
                photoUris = photoUris + stored
                driveIds = driveIds + ""
            }
```

Remove button (~line 443):

```kotlin
// BEFORE
                                    .clickable {
                                        photoUris = photoUris.toMutableList().also { it.remove(uri) }

// AFTER
                                    .clickable {
                                        val idx = photoUris.indexOf(uri)
                                        if (idx >= 0) {
                                            photoUris = photoUris.toMutableList().also { it.removeAt(idx) }
                                            driveIds = driveIds.toMutableList().also { it.removeAt(idx) }
                                        }
```

Drag-reorder (~line 413, inside `onDrag`) — move the element in both lists:

```kotlin
// BEFORE
                                            if (dragOffsetX > slotPx / 2 && from < livePhotoUris.lastIndex) {
                                                photoUris = livePhotoUris.toMutableList()
                                                    .also { it.add(from + 1, it.removeAt(from)) }
                                                dragOffsetX -= slotPx
                                            } else if (dragOffsetX < -slotPx / 2 && from > 0) {
                                                photoUris = livePhotoUris.toMutableList()
                                                    .also { it.add(from - 1, it.removeAt(from)) }
                                                dragOffsetX += slotPx
                                            }

// AFTER
                                            if (dragOffsetX > slotPx / 2 && from < livePhotoUris.lastIndex) {
                                                photoUris = livePhotoUris.toMutableList()
                                                    .also { it.add(from + 1, it.removeAt(from)) }
                                                driveIds = driveIds.toMutableList()
                                                    .also { it.add(from + 1, it.removeAt(from)) }
                                                dragOffsetX -= slotPx
                                            } else if (dragOffsetX < -slotPx / 2 && from > 0) {
                                                photoUris = livePhotoUris.toMutableList()
                                                    .also { it.add(from - 1, it.removeAt(from)) }
                                                driveIds = driveIds.toMutableList()
                                                    .also { it.add(from - 1, it.removeAt(from)) }
                                                dragOffsetX += slotPx
                                            }
```

Save (~line 342):

```kotlin
// BEFORE
                                        driveFileIds = existingEntry?.driveFileIds ?: emptyList(),

// AFTER
                                        driveFileIds = driveIds,
```

**File:** `NewEditEntryScreen.kt`

---

## Change 2 — Detail screen: pad driveFileIds before reordering

**Problem:** `withCover` / `withSwapped` in `EntryDetailScreen.kt` only mutate `driveFileIds`
when the index fits (`if (index < size)`). When some photos haven't uploaded yet the list is
shorter, the mutation is skipped, and the lists desync — e.g. photos `[A,B,C]` with ids
`[idA]`: `withCover(2)` yields photos `[C,A,B]` but ids still `[idA]`, pairing A's Drive file
with C.

**Fix:** Pad to `photoUris.size` with `""` first, then mutate unconditionally.

```kotlin
// BEFORE (bottom of file, ~line 951)
private fun TravelEntry.withCover(index: Int): TravelEntry {
    if (index <= 0 || index >= photoUris.size) return this
    val photos = photoUris.toMutableList().apply { add(0, removeAt(index)) }
    val driveIds = driveFileIds.toMutableList().apply { if (index < size) add(0, removeAt(index)) }
    return copy(photoUris = photos, driveFileIds = driveIds)
}

private fun TravelEntry.withRemoved(index: Int): TravelEntry {
    if (index < 0 || index >= photoUris.size) return this
    val photos = photoUris.toMutableList().apply { removeAt(index) }
    val driveIds = driveFileIds.toMutableList().apply { if (index < size) removeAt(index) }
    return copy(photoUris = photos, driveFileIds = driveIds)
}

private fun TravelEntry.withSwapped(a: Int, b: Int): TravelEntry {
    if (a == b || a < 0 || b < 0 || a >= photoUris.size || b >= photoUris.size) return this
    val photos = photoUris.toMutableList().also { list -> val tmp = list[a]; list[a] = list[b]; list[b] = tmp }
    val driveIds = if (a < driveFileIds.size && b < driveFileIds.size) {
        driveFileIds.toMutableList().also { list -> val tmp = list[a]; list[a] = list[b]; list[b] = tmp }
    } else driveFileIds
    return copy(photoUris = photos, driveFileIds = driveIds)
}

// AFTER
/** driveFileIds padded to photoUris length ("" = not uploaded), so positional edits never desync. */
private fun TravelEntry.paddedDriveIds(): MutableList<String> =
    MutableList(photoUris.size) { i -> driveFileIds.getOrNull(i) ?: "" }

private fun TravelEntry.withCover(index: Int): TravelEntry {
    if (index <= 0 || index >= photoUris.size) return this
    val photos = photoUris.toMutableList().apply { add(0, removeAt(index)) }
    val driveIds = paddedDriveIds().apply { add(0, removeAt(index)) }
    return copy(photoUris = photos, driveFileIds = driveIds)
}

private fun TravelEntry.withRemoved(index: Int): TravelEntry {
    if (index < 0 || index >= photoUris.size) return this
    val photos = photoUris.toMutableList().apply { removeAt(index) }
    val driveIds = paddedDriveIds().apply { removeAt(index) }
    return copy(photoUris = photos, driveFileIds = driveIds)
}

private fun TravelEntry.withSwapped(a: Int, b: Int): TravelEntry {
    if (a == b || a < 0 || b < 0 || a >= photoUris.size || b >= photoUris.size) return this
    val photos = photoUris.toMutableList().also { list -> val tmp = list[a]; list[a] = list[b]; list[b] = tmp }
    val driveIds = paddedDriveIds().also { list -> val tmp = list[a]; list[a] = list[b]; list[b] = tmp }
    return copy(photoUris = photos, driveFileIds = driveIds)
}
```

**File:** `EntryDetailScreen.kt`

---

## Scope notes

- `DrivePhotoSync.uploadEntryPhotos` already pads its working copy with `""` — no change there.
- Existing cloud entries that are ALREADY desynced can't be auto-repaired (there's no record of
  the original pairing) — out of scope.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Parallel `driveIds` state mirrored through add/remove/reorder; saved on commit | `NewEditEntryScreen.kt` |
| 2 | `paddedDriveIds()` helper; unconditional mirrored mutation in withCover/withRemoved/withSwapped | `EntryDetailScreen.kt` |
