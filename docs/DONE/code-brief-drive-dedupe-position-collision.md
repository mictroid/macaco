# Macaco — Drive upload dedupe: positional filename collision reassigns the wrong photo/video

Fixes a data-correctness bug in the vc74 Drive-dedupe fix: `DrivePhotoSync`'s idempotent-upload
filename lookup keys on array *position*, but position isn't stable across a photo/video
delete or reorder, so a later upload can silently reuse a Drive file that actually belongs to a
*different* still-present photo in the same entry. Touches `DrivePhotoSync.kt` only.

---

## Background (read first)

`docs/DONE/code-brief-drive-dedupe-and-doc-updates.md` (vc74) made Drive uploads idempotent by
naming files deterministically as `macaco_<entryId>_<index>.jpg`/`.mp4` and looking up that exact
name before creating a new file, so a retried upload (crash mid-save, or two devices racing)
reuses the existing file instead of duplicating it. The intent was sound for the retry case it
was built for. The bug is that `index` is recomputed from the *current* `photoUris`/`driveFileIds`
list position every time `uploadEntryPhotos`/`uploadEntryVideos` runs — and `EntryDetailScreen.kt`'s
`withRemoved`/`withCover`/`withSwapped` (used by `NewEditEntryScreen.kt`'s delete/reorder/cover
actions) shift every later photo's position when one is removed or moved, without ever renaming or
deleting its already-uploaded Drive file.

**Concrete repro:** an entry has 3 photos, already synced: index 0 → Drive file
`macaco_e1_0.jpg` (id `A`), index 1 → `macaco_e1_1.jpg` (id `B`), index 2 → `macaco_e1_2.jpg`
(id `C`). User deletes the photo at index 1. The list collapses: what was index-2 (Drive id `C`,
file `macaco_e1_2.jpg`) is now at index 1; `driveFileIds` correctly carries `C` along with it
(the positional pairing between `photoUris`/`driveFileIds` is intentional and correct — see
`docs/DONE/code-brief-drive-ids-alignment.md`). The user then adds one new photo, appended at the
end — now at index 2. `uploadEntryPhotos` sees `result[2]` is empty (new photo, no id yet) and
calls `uploadPhoto(..., index = 2)`, which builds the filename `macaco_e1_2.jpg` — the **exact
name of the file already legitimately in use by the photo now sitting at index 1** (id `C`).
`findExistingFile` matches it by name and returns `C`, so the *new* photo is assigned Drive id `C`
— the same id as the *other, still-present* photo. Both entries in `driveFileIds` now point at the
same Drive file: the new photo silently displays (and, on `EntryDetailScreen`'s Drive-cache lookup)
the old photo's image instead of its own.

This reproduces on a single device with no crash, no retry, and no multi-device race — it's the
ordinary case of delete-then-add. It also applies identically to `uploadVideo`/`videoFileIds`, and
to reordering (`withSwapped`, "set as cover" via `withCover`) since those also shift positions.

---

## Change 1 — Don't reuse a Drive file that's already claimed by another position in this entry

**Problem:** `uploadPhoto`/`uploadVideo` (`DrivePhotoSync.kt:168-184`, `:237-253`) look up
`findExistingFile` purely by the position-derived filename, with no check against what
`driveFileIds` already contains elsewhere in the same entry. If the found file's id is already
assigned to a different index, reusing it corrupts the entry (two photos pointing at one Drive
file; the new photo never gets its own upload).

**Fix:** Pass the in-progress `result` list (the ids already resolved for this entry, including
ones assigned earlier in the same pass) into `uploadPhoto`/`uploadVideo`, and only accept a
`findExistingFile` match if that id isn't already claimed by another index. If it's already
claimed, treat it as a name collision from stale position reuse — skip the reuse and upload a
fresh file (Drive permits duplicate names in a folder; this only costs a second file entry, not a
misattributed photo). This keeps the retry/cross-device idempotency behavior for the case it was
actually built for (an index whose `driveFileIds` slot is still genuinely empty and no other index
already owns that name's file) while closing the position-collision case.

```kotlin
// BEFORE — DrivePhotoSync.kt:168-184
private fun uploadPhoto(
    drive: Drive,
    folderId: String,
    uriString: String,
    entryId: String,
    index: Int
): String? {
    val fileName = "macaco_${entryId}_$index.jpg"
    findExistingFile(drive, folderId, fileName)?.let { return it }
    val stream = context.contentResolver.openInputStream(Uri.parse(uriString)) ?: return null
    val content = InputStreamContent("image/jpeg", stream)
    val meta = File().apply {
        name = fileName
        parents = listOf(folderId)
    }
    return drive.files().create(meta, content).setFields("id").execute().id
}
```

```kotlin
// AFTER — DrivePhotoSync.kt:168-184
private fun uploadPhoto(
    drive: Drive,
    folderId: String,
    uriString: String,
    entryId: String,
    index: Int,
    claimedIds: List<String>
): String? {
    val fileName = "macaco_${entryId}_$index.jpg"
    // Only reuse a name-matched file if it isn't already this entry's Drive id for a DIFFERENT
    // photo. Position shifts after a delete/reorder (withRemoved/withSwapped/withCover) can make
    // a stale index collide with a still-live file's name — reusing it would silently reassign
    // that photo's Drive file to this one instead of uploading a new file. See
    // docs/DONE/code-brief-drive-dedupe-position-collision.md.
    findExistingFile(drive, folderId, fileName)
        ?.takeIf { it !in claimedIds }
        ?.let { return it }
    val stream = context.contentResolver.openInputStream(Uri.parse(uriString)) ?: return null
    val content = InputStreamContent("image/jpeg", stream)
    val meta = File().apply {
        name = fileName
        parents = listOf(folderId)
    }
    return drive.files().create(meta, content).setFields("id").execute().id
}
```

Call site — `uploadEntryPhotos` (`DrivePhotoSync.kt:204-220`):

```kotlin
// BEFORE
entry.photoUris.forEachIndexed { i, uriString ->
    if (result[i].isEmpty()) {
        uploadPhoto(drive, folderId, uriString, entry.id, i)?.let { result[i] = it }
    }
}
```

```kotlin
// AFTER
entry.photoUris.forEachIndexed { i, uriString ->
    if (result[i].isEmpty()) {
        uploadPhoto(drive, folderId, uriString, entry.id, i, result)?.let { result[i] = it }
    }
}
```

**File:** `DrivePhotoSync.kt`

---

## Change 2 — Same fix, mirrored for video uploads

**Problem:** `uploadVideo` (`DrivePhotoSync.kt:237-253`) and its caller `uploadEntryVideos`
(`:260-274`) have the identical positional-filename-reuse flaw for `videoUris`/`videoFileIds`.

```kotlin
// BEFORE — DrivePhotoSync.kt:237-253
private fun uploadVideo(
    drive: Drive,
    folderId: String,
    uriString: String,
    entryId: String,
    index: Int
): String? {
    val fileName = "macaco_${entryId}_$index.mp4"
    findExistingFile(drive, folderId, fileName)?.let { return it }
    val stream = context.contentResolver.openInputStream(Uri.parse(uriString)) ?: return null
    val content = InputStreamContent("video/mp4", stream)
    val meta = File().apply {
        name = fileName
        parents = listOf(folderId)
    }
    return drive.files().create(meta, content).setFields("id").execute().id
}
```

```kotlin
// AFTER — DrivePhotoSync.kt:237-253
private fun uploadVideo(
    drive: Drive,
    folderId: String,
    uriString: String,
    entryId: String,
    index: Int,
    claimedIds: List<String>
): String? {
    val fileName = "macaco_${entryId}_$index.mp4"
    findExistingFile(drive, folderId, fileName)
        ?.takeIf { it !in claimedIds }
        ?.let { return it }
    val stream = context.contentResolver.openInputStream(Uri.parse(uriString)) ?: return null
    val content = InputStreamContent("video/mp4", stream)
    val meta = File().apply {
        name = fileName
        parents = listOf(folderId)
    }
    return drive.files().create(meta, content).setFields("id").execute().id
}
```

Call site — `uploadEntryVideos` (`DrivePhotoSync.kt:260-274`):

```kotlin
// BEFORE
entry.videoUris.forEachIndexed { i, uriString ->
    if (result[i].isEmpty()) {
        uploadVideo(drive, folderId, uriString, entry.id, i)?.let { result[i] = it }
    }
}
```

```kotlin
// AFTER
entry.videoUris.forEachIndexed { i, uriString ->
    if (result[i].isEmpty()) {
        uploadVideo(drive, folderId, uriString, entry.id, i, result)?.let { result[i] = it }
    }
}
```

**File:** `DrivePhotoSync.kt`

---

## Verification

Static: confirm `claimedIds` (the `result` list) reflects ids already assigned earlier in the same
`forEachIndexed` pass, since Kotlin mutates `result` in place before the next iteration reads it —
no change needed there, `result` is already the right list to pass, just not currently threaded
through.

On-device repro to confirm the fix: create an entry with 3 photos, let Drive sync finish (Settings
→ Drive shows "all photos backed up"), delete the middle photo, add one new (different) photo,
save, let Drive sync finish again. Confirm in the app (or via the Drive "Macaco" folder) that the
new photo has its own distinct Drive file — not the same id as the remaining photo it now sits
next to in position. Repeat for videos.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `uploadPhoto`/`uploadEntryPhotos`: don't reuse a name-matched Drive file if its id is already claimed by another photo in this entry | `DrivePhotoSync.kt` |
| 2 | Same fix mirrored for `uploadVideo`/`uploadEntryVideos` | `DrivePhotoSync.kt` |
