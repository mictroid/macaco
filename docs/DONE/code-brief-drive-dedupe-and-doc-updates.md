# Macaco — Drive Sync: Fix Duplicate Folders/Photos + Update FAQ/Privacy/Terms for Video Backup

Fixes a race condition in `DrivePhotoSync.kt` that creates duplicate "Macaco" Drive folders and
duplicate photo/video uploads, makes uploads idempotent against Drive itself so the fix holds
across multiple devices signed into the same account (not just within one app process), adds
self-healing for accounts that already have duplicate folders, and brings `strings.xml` (all 11
locales), `privacy-policy.html`, and `terms-of-service.html` up to date now that video backup to
Drive has shipped (it was already implemented in `uploadEntryVideos`/`videoFileIds` — this brief
does not add that feature, only documents it and fixes the upload race).

---

## Fix 1: serialize Drive folder lookup/creation and uploads (root cause of duplicates)

**Problem:** `getOrCreateDriveFolder()` caches the folder id in a plain `var driveFolderId`
with no locking. `uploadEntryPhotos()` and `uploadEntryVideos()` are called from multiple
independent coroutines that can run concurrently for the same account:

- `JournalViewModel.saveEntry()` launches a background upload on every save (one `launch{}` per
  save call — saving two entries back-to-back starts two concurrent upload passes).
- `JournalViewModel.syncPhotosToGoogleDrive()` (the Settings "Sync Now" button) calls
  `drivePhotoSync.syncAll(...)`, which can run at the same time as a save-triggered auto-upload
  still in flight.

When two such calls race while `driveFolderId == null` (very common right after install, after
an account switch resets it in `onAccountChanged()`, or any time before the first successful
upload), both read `driveFolderId == null`, both query Drive for a folder named "Macaco", both
find nothing (the first call's `create()` hasn't landed yet), and both create a folder — two
"Macaco" folders. The same race on `entry.driveFileIds` (both calls see the same photo's slot as
`""` and both upload it) produces two Drive files for one local photo; only one id survives in
Firestore, so the other is an orphaned duplicate sitting in the Drive folder — exactly what was
reported ("multiple images" visible when browsing Drive directly, even from a single account).

Testing from multiple Google accounts is a *different*, expected behavior (each account gets its
own private "Macaco" folder in its own Drive) — that is not a bug. The bug is same-account
duplication from concurrent upload passes.

**Fix:** Add a `Mutex` that serializes the entire "find-or-create folder, then upload" critical
section so at most one upload pass touches Drive folder/file creation at a time app-wide. Upload
volume is tiny (≤3 photos + ≤3 videos per entry) so full serialization has no meaningful
performance cost, and it eliminates the race outright rather than narrowing it.

```kotlin
// ADD import near the other kotlinx.coroutines.sync import:
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
```

```kotlin
// ADD as a class member, near `private var driveFolderId: String? = null`:
private var driveFolderId: String? = null
// Serializes folder lookup/creation AND the upload loops below. Two concurrent upload passes
// (e.g. auto-upload-on-save racing the manual "Sync Now" button) used to both see
// driveFolderId == null / driveFileIds[i] == "" and both create a folder / upload the same
// photo — this lock makes the whole find-or-create-and-upload sequence atomic.
private val driveUploadMutex = Mutex()
```

Wrap the bodies of `uploadEntryPhotos` and `uploadEntryVideos` in `driveUploadMutex.withLock { }`:

```kotlin
// BEFORE:
suspend fun uploadEntryPhotos(entry: TravelEntry): List<String> = withContext(Dispatchers.IO) {
    if (!isDriveConnected()) return@withContext entry.driveFileIds
    val drive = getDriveService()
    val folderId = getOrCreateDriveFolder(drive)
    val result = entry.driveFileIds.toMutableList()
    while (result.size < entry.photoUris.size) result.add("")
    entry.photoUris.forEachIndexed { i, uriString ->
        if (result[i].isEmpty()) {
            uploadPhoto(drive, folderId, uriString)?.let { result[i] = it }
        }
    }
    result.toList()
}

// AFTER:
suspend fun uploadEntryPhotos(entry: TravelEntry): List<String> = withContext(Dispatchers.IO) {
    if (!isDriveConnected()) return@withContext entry.driveFileIds
    driveUploadMutex.withLock {
        val drive = getDriveService()
        val folderId = getOrCreateDriveFolder(drive)
        val result = entry.driveFileIds.toMutableList()
        while (result.size < entry.photoUris.size) result.add("")
        entry.photoUris.forEachIndexed { i, uriString ->
            if (result[i].isEmpty()) {
                uploadPhoto(drive, folderId, uriString)?.let { result[i] = it }
            }
        }
        result.toList()
    }
}
```

```kotlin
// BEFORE:
suspend fun uploadEntryVideos(entry: TravelEntry): List<String> = withContext(Dispatchers.IO) {
    if (!isDriveConnected()) return@withContext entry.videoFileIds
    val drive = getDriveService()
    val folderId = getOrCreateDriveFolder(drive)
    val result = entry.videoFileIds.toMutableList()
    while (result.size < entry.videoUris.size) result.add("")
    entry.videoUris.forEachIndexed { i, uriString ->
        if (result[i].isEmpty()) {
            uploadVideo(drive, folderId, uriString)?.let { result[i] = it }
        }
    }
    result.toList()
}

// AFTER:
suspend fun uploadEntryVideos(entry: TravelEntry): List<String> = withContext(Dispatchers.IO) {
    if (!isDriveConnected()) return@withContext entry.videoFileIds
    driveUploadMutex.withLock {
        val drive = getDriveService()
        val folderId = getOrCreateDriveFolder(drive)
        val result = entry.videoFileIds.toMutableList()
        while (result.size < entry.videoUris.size) result.add("")
        entry.videoUris.forEachIndexed { i, uriString ->
            if (result[i].isEmpty()) {
                uploadVideo(drive, folderId, uriString)?.let { result[i] = it }
            }
        }
        result.toList()
    }
}
```

Note: `getOrCreateDriveFolder`, `uploadPhoto`, and `uploadVideo` stay unchanged — they're only
ever called from inside the now-locked sections, so no other caller needs updating.

**File:** `data/sync/DrivePhotoSync.kt`

---

## Fix 2: make uploads idempotent against Drive itself (not just local state) — closes the multi-device gap

**Problem:** Fix 1's `Mutex` only serializes uploads *within one app process*. It does not
protect the case where the same account is signed in on two devices (e.g. phone + tablet) and
both auto-upload the same entry around the same time — each device has its own in-memory mutex,
so the lock doesn't reach across devices. Worse, the upload loop only checks the *local*
`driveFileIds`/`videoFileIds` list for an empty slot before uploading — it never asks Drive
itself whether a file already exists. If that local flag is ever stale (e.g. an upload succeeded
but the Firestore write of the new ID lagged or lost a race with an edit), a retry — from any
device — re-uploads with nothing to catch it. `uploadPhoto`/`uploadVideo` also name every file
with `System.currentTimeMillis()`, which is non-deterministic, so there's no natural key to check
existence against even if we wanted to.

**Fix:** Give each photo/video a deterministic filename derived from the entry id and its index
in the list, and before creating a new file, query Drive for a file with that exact name in the
folder first. If found, reuse its id (no upload, no duplicate). Only create when nothing matches.
This makes the upload idempotent regardless of which device or process runs it — a genuine
"check Drive before uploading" guarantee, layered on top of (not instead of) Fix 1's mutex.

```kotlin
// BEFORE:
private fun uploadPhoto(drive: Drive, folderId: String, uriString: String): String? {
    val stream = context.contentResolver.openInputStream(Uri.parse(uriString)) ?: return null
    val content = InputStreamContent("image/jpeg", stream)
    val meta = File().apply {
        name = "macaco_${System.currentTimeMillis()}.jpg"
        parents = listOf(folderId)
    }
    return drive.files().create(meta, content).setFields("id").execute().id
}

// AFTER:
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

// Same treatment for uploadVideo:
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

// ADD new private helper (put it next to uploadPhoto/uploadVideo):
/** Looks up a file by exact name within [folderId]. Returns its id if found, else null. Used to
 *  make uploads idempotent — a retry (from any device/process) reuses the existing file instead
 *  of creating a duplicate. */
private fun findExistingFile(drive: Drive, folderId: String, fileName: String): String? =
    drive.files().list()
        .setQ("name='$fileName' and '$folderId' in parents and trashed=false")
        .setSpaces("drive")
        .setFields("files(id)")
        .execute()
        .files
        .firstOrNull()
        ?.id
```

Update the two call sites inside `uploadEntryPhotos`/`uploadEntryVideos` (from Fix 1's `AFTER`
blocks) to pass the new parameters:

```kotlin
// In uploadEntryPhotos, change:
uploadPhoto(drive, folderId, uriString)?.let { result[i] = it }
// to:
uploadPhoto(drive, folderId, uriString, entry.id, i)?.let { result[i] = it }
```

```kotlin
// In uploadEntryVideos, change:
uploadVideo(drive, folderId, uriString)?.let { result[i] = it }
// to:
uploadVideo(drive, folderId, uriString, entry.id, i)?.let { result[i] = it }
```

Note: this adds one Drive `list()` call per photo/video before each create — negligible given the
≤6 items per entry, and it's the only way to get a real cross-device guarantee rather than an
in-process one.

**File:** `data/sync/DrivePhotoSync.kt`

---

## Fix 3: self-heal accounts that already have duplicate "Macaco" folders

**Problem:** Fix 1 stops *new* duplicates but does nothing for accounts (including the reporting
user's test accounts) that already have two or more folders named "Macaco" from the race. Right
now `getOrCreateDriveFolder` takes `current.files[0].id` — whichever folder the Drive API happens
to list first, which is arbitrary and can silently flip between syncs, scattering new uploads
across whichever duplicate got picked that time.

**Fix:** When more than one "Macaco" folder is found, deterministically prefer the **oldest** one
(by `createdTime`) as canonical, so all future uploads converge on a single folder instead of
continuing to scatter. This doesn't delete or merge the extra folders (deleting Drive files
programmatically without certainty is riskier than leaving an inert empty-ish folder behind) —
call this out to the user as a manual cleanup step afterward.

```kotlin
// BEFORE:
private fun getOrCreateDriveFolder(drive: Drive): String {
    driveFolderId?.let { return it }

    fun findFolder(name: String) = drive.files().list()
        .setQ("name='$name' and mimeType='application/vnd.google-apps.folder' and trashed=false")
        .setSpaces("drive")
        .setFields("files(id)")
        .execute()

    // Prefer the current "Macaco" folder. If it doesn't exist yet but a legacy "Wanderlog"
    // folder (from before the rebrand) does, rename that in place so existing users' photos
    // move with the brand instead of being stranded in an old folder.
    val current = findFolder(FOLDER_NAME)
    val id = when {
        current.files.isNotEmpty() -> current.files[0].id
        else -> {
            val legacy = findFolder(LEGACY_FOLDER_NAME)
            if (legacy.files.isNotEmpty()) {
                val legacyId = legacy.files[0].id
                drive.files().update(legacyId, File().apply { name = FOLDER_NAME }).execute()
                legacyId
            } else {
                val meta = File().apply {
                    name = FOLDER_NAME
                    mimeType = "application/vnd.google-apps.folder"
                }
                drive.files().create(meta).setFields("id").execute().id
            }
        }
    }
    return id.also { driveFolderId = it }
}

// AFTER:
private fun getOrCreateDriveFolder(drive: Drive): String {
    driveFolderId?.let { return it }

    // Fields include createdTime so that if duplicate folders exist (e.g. from the upload race
    // fixed alongside this), we can deterministically pick the oldest one instead of whichever
    // the API happens to list first — keeps future uploads converging on one folder.
    fun findFolder(name: String) = drive.files().list()
        .setQ("name='$name' and mimeType='application/vnd.google-apps.folder' and trashed=false")
        .setSpaces("drive")
        .setFields("files(id, createdTime)")
        .setOrderBy("createdTime")
        .execute()

    // Prefer the current "Macaco" folder. If it doesn't exist yet but a legacy "Wanderlog"
    // folder (from before the rebrand) does, rename that in place so existing users' photos
    // move with the brand instead of being stranded in an old folder.
    val current = findFolder(FOLDER_NAME)
    val id = when {
        current.files.isNotEmpty() -> {
            if (current.files.size > 1) {
                Log.w(
                    "DrivePhotoSync",
                    "Found ${current.files.size} '$FOLDER_NAME' folders — using the oldest " +
                        "(${current.files[0].id}). Extra duplicates are left in place; the user " +
                        "can merge/delete them manually in Drive."
                )
            }
            current.files[0].id // list is ordered by createdTime ascending — oldest first
        }
        else -> {
            val legacy = findFolder(LEGACY_FOLDER_NAME)
            if (legacy.files.isNotEmpty()) {
                val legacyId = legacy.files[0].id
                drive.files().update(legacyId, File().apply { name = FOLDER_NAME }).execute()
                legacyId
            } else {
                val meta = File().apply {
                    name = FOLDER_NAME
                    mimeType = "application/vnd.google-apps.folder"
                }
                drive.files().create(meta).setFields("id").execute().id
            }
        }
    }
    return id.also { driveFolderId = it }
}
```

**File:** `data/sync/DrivePhotoSync.kt`

---

## Fix 4: update in-app FAQ strings — video backup is live, not just photos

**Problem:** `help_faq_a_photos`, `help_faq_a_backup`, and `help_faq_drive_connect_a` in
`strings.xml` only mention photo backup to Drive. Video backup to Drive already shipped
(`uploadEntryVideos`/`videoFileIds` in `DrivePhotoSync.kt`, `docs/DONE/code-brief-video-entries.md`,
`code-brief-video-sync-integrity.md`, `code-brief-drive-video-download-fix.md`) and
`help_faq_delete_drive_a` already correctly says "photos and videos" — these three are the ones
that fell behind.

**Fix:** Update the English values, then propagate the same wording change to all 11 locale
files (`values/strings.xml`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`,
`values-nl`, `values-pl`, `values-pt`, `values-sv`, `values-zh-rCN`) — translate naturally into
each language rather than literal find/replace, matching the tone of the existing translation in
each file.

| Key | Current EN | New EN |
|-----|-----------|--------|
| `help_faq_a_photos` | Photos are saved on the device you added them from. Connect Google Drive in Settings, or use Backup to file, to carry photos across devices. | Photos and videos are saved on the device you added them from. Connect Google Drive in Settings, or use Backup to file, to carry them across devices. |
| `help_faq_a_backup` | Settings → Backup & Restore lets you export all entries and photos to a single .zip file you can re-import anytime. Both this and automatic Google Drive photo backup are Premium features. | Settings → Backup & Restore lets you export all entries, photos, and videos to a single .zip file you can re-import anytime. Both this and automatic Google Drive backup are Premium features. |
| `help_faq_drive_connect_a` | Go to Settings → Drive Sync and tap Connect Google Drive. Sign in with a Google account — this can be the same account you use to log in, or a different one. Once connected, Macaco automatically backs up entry photos to a "Macaco" folder in your Drive and restores them on any device you sign in to. | Go to Settings → Drive Sync and tap Connect Google Drive. Sign in with a Google account — this can be the same account you use to log in, or a different one. Once connected, Macaco automatically backs up entry photos and videos to a "Macaco" folder in your Drive and restores them on any device you sign in to. |

**File:** `app/src/main/res/values*/strings.xml` (all 11 locales)

---

## Fix 5: update Privacy Policy and Terms of Service — video backup, remove stale Apple Sign-In mention

**Problem:** `privacy-policy.html` and `terms-of-service.html` (repo root — the actual source
served by GitHub Pages at `mictroid.github.io/macaco/...`, per `CLAUDE.md`) describe Drive backup
and stored content as photo-only in every one of their embedded language sections (en, de, fr,
es, it, nl, pt, sv, ja — same file, repeated per-language blocks). Two more issues found while
reviewing:

1. `privacy-policy.html` §2b/2d/2e (English block) still lists **Apple Sign-In** as a supported
   sign-in method whose data is collected, and §4's service table still lists "Apple Sign in with
   Apple." Apple Sign-In was removed in vc24 (`CLAUDE.md`) — this is stale and should be removed
   so the policy matches what the app actually offers (Google + email/password only).
2. Every "photo backup" / "Photos are stored in..." phrase across both files' language blocks
   needs a video mention, matching what's already true of the app's behavior and already correctly
   worded in the in-app FAQ's `help_faq_delete_drive_a` string.

**Fix:**
- In `privacy-policy.html`, for **every language block**: change "Photos" / "photo backup" /
  "back up your entry photos" phrasing to "Photos and videos" / "photo and video backup" /
  "back up your entry photos and videos" wherever Drive or stored journal content is described
  (§2b "Photos you attach", §2e "Google Drive (optional)", the data-collected tables, and each
  translated equivalent).
- In `privacy-policy.html` English block specifically: remove "Apple" from the sign-in method
  list in §2a ("Google, Apple, or email/password" → "Google or email/password") and remove the
  "Apple Sign in with Apple" row from the §4 third-party services table. Check whether any other
  language block in the file also lists Apple as a sign-in option and remove it there too for
  consistency.
- In `terms-of-service.html`, for every language block: update phrasing like "journal entries
  with photos" → "journal entries with photos and videos", "Photos in your device gallery or
  Google Drive" → "Photos and videos in your device gallery or Google Drive", and the data-table
  row "Google Drive access (if connected) → Optional photo backup" → "Optional photo and video
  backup" (and its translated equivalents).
- Bump the "Last updated" date at the top of `privacy-policy.html` (and the equivalent in
  `terms-of-service.html` if it has one) to today's date.

Do a full read-through of both files rather than a blind find/replace — some sentences will need
light rewording (not just "photo" → "photo and video") to stay grammatically natural in each
language.

**Files:** `privacy-policy.html`, `terms-of-service.html` (repo root)

Note: `docs/privacy-policy.md` in this repo is an older markdown draft that is **not** the live
page (the live page is the HTML at repo root, confirmed via `CLAUDE.md`'s GitHub Pages note) —
it also still mentions Apple Sign-In and photo-only backup, but leave it alone unless asked; it
appears to be a stale leftover from before the HTML versions were created and updating it isn't
load-bearing for what users see.

---

## Manual follow-up (not code — flag to the user, don't do automatically)

The existing duplicate "Macaco" folders and orphaned duplicate photos/videos already sitting in
Drive from before this fix are **not** cleaned up automatically — deleting Drive files
programmatically without being fully certain they're unreferenced orphans risks real data loss.
After this ships, the user (and the reporting user, if reachable) should manually open Google
Drive, look for more than one "Macaco" folder, and merge/delete the extras by hand. Fix 2 ensures
the app converges on the oldest folder going forward, so the "youngest" duplicate(s) are the safe
ones to remove.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `Mutex` around Drive folder lookup/creation + upload loops — fixes same-process races (the reported bug's actual mechanism) | `data/sync/DrivePhotoSync.kt` |
| 2 | Deterministic filenames + existence check before create — makes uploads idempotent against Drive itself, closing the multi-device gap Fix 1 alone doesn't cover | `data/sync/DrivePhotoSync.kt` |
| 3 | Prefer oldest "Macaco" folder when duplicates already exist (self-heal, no deletion) | `data/sync/DrivePhotoSync.kt` |
| 4 | Update 3 FAQ strings to mention video backup, across all 11 locales | `app/src/main/res/values*/strings.xml` |
| 5 | Update Privacy Policy + Terms of Service for video backup across all language blocks; remove stale Apple Sign-In mentions | `privacy-policy.html`, `terms-of-service.html` |
