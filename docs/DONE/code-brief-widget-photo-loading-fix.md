# Macaco — Widgets: Fix Missing Photos (On This Day + Recent Entries)

Fixes the "no images loading in the widgets" bug seen on-device (S8 debug build, screenshot:
9 entries/9 places synced correctly, but every widget photo slot shows the fallback icon or plain
teal card). Touches `OnThisDayWidgetProvider.kt` and `RecentEntriesWidgetService.kt`. Builds on
the exported-fix and Drive file:// stream-fix already shipped for these widgets
(`docs/DONE/code-brief-home-widget.md`, `code-brief-widget-exported-fix.md`).

---

## Root cause

`WidgetPhotos.readableSource()` (unchanged, still correct) checks two things in order: (1) is the
entry's local `content://` photo URI readable on **this** device, (2) if not, does
`cacheDir/drive_photos/<driveFileId>.jpg` already exist from a prior Drive download. Both widgets
call it, get `null` for every entry, and correctly fall back to the placeholder — the fallback
logic isn't the bug.

The bug is what's missing: **nothing ever populates that Drive cache from the widget side.**
`cacheDir/drive_photos/` is only ever written by `DrivePhotoSync.downloadMissing()`
(`data/sync/DrivePhotoSync.kt:253`), which is only invoked from `JournalViewModel` — i.e. only
runs while `MainActivity` is alive and has triggered a sync (`saveEntry`, `syncAll`, or the
auto-download on entry-list change). An `AppWidgetProvider`/`RemoteViewsService` can run on its
own schedule (`updatePeriodMillis`, or immediately after being added to the home screen) without
the app ever having been opened in that process lifetime, or on a device where these particular
entries were created elsewhere and their photos never got pulled down locally. In that state:

- The local `content://` URI is dead (photo was added on a different device).
- The Drive cache file was never downloaded, because only the ViewModel downloads it.

So `readableSource()` correctly returns `null` for both hero and row thumbnails — this matches
the screenshot exactly (Firestore text data present, zero photos anywhere) and explains why it's
specific to widgets: the in-app entry list goes through `JournalViewModel`'s Drive sync path and
shows photos fine once that's run; the widgets never do.

`DrivePhotoSync` already has the exact suspend function needed for this —
`ensurePhotosCached(entries)` (`data/sync/DrivePhotoSync.kt:244`) — it awaits a bounded-concurrency
download pass and returns the driveFileId → cache-URI map, and is already used by `JournalBackup`
for the same "make sure Drive-only photos are local before I need them" reason. It safely no-ops
when nothing is missing (checks needed files before touching the network) and when Drive isn't
connected (`getDriveService()` failure short-circuits to a no-op). Reusing it from the widgets
means each widget becomes self-sufficient instead of depending on the main app having synced
first.

---

## Fix 1 — On This Day hero photo

**Problem:** `fetchHighlight` picks the highlighted entry but never tries to pull its photo from
Drive before `updateOne` calls `WidgetPhotos.readableSource`.

```kotlin
// BEFORE — OnThisDayWidgetProvider.kt
private suspend fun fetchHighlight(context: Context, uid: String): TravelEntry? = runCatching {
    val snapshot = FirebaseFirestore.getInstance()
        .collection("users").document(uid).collection("entries")
        .get().await()
    val entries = snapshot.documents.mapNotNull { it.toTravelEntry() }
    // Photo-aware highlight: an "On This Day" match always wins (that's the widget's promise),
    // preferring one whose photo actually resolves; only when there's no anniversary match do we
    // fall back to the most-recent entry — and there too we prefer one with a showable photo, so
    // the card isn't stuck photoless just because the newest-by-date entry happens to have none.
    fun hasPhoto(e: TravelEntry) =
        WidgetPhotos.readableSource(context, e.photoUris.firstOrNull(), e.driveFileIds.firstOrNull()) != null
    val onThisDay = entries.onThisDayEntries()          // already sorted, most recent first
    val recent = entries.sortedByDescending { it.dateMillis }
    onThisDay.firstOrNull { hasPhoto(it) }
        ?: onThisDay.firstOrNull()
        ?: recent.firstOrNull { hasPhoto(it) }
        ?: recent.firstOrNull()
}.getOrNull()
```

```kotlin
// AFTER — download any missing Drive photos for these entries before picking a highlight, so
// hasPhoto() reflects reality instead of a cold cache. TravelJournalApp already owns a
// process-wide DrivePhotoSync instance (used by JournalViewModel) — reuse it rather than
// constructing a second one.
private suspend fun fetchHighlight(context: Context, uid: String): TravelEntry? = runCatching {
    val snapshot = FirebaseFirestore.getInstance()
        .collection("users").document(uid).collection("entries")
        .get().await()
    val entries = snapshot.documents.mapNotNull { it.toTravelEntry() }

    // Best-effort: make sure any Drive-only photos for these entries are cached locally before
    // we decide which entry "has a photo". ensurePhotosCached() no-ops quickly when everything's
    // already cached or Drive isn't connected, so this is cheap on the common repeat-refresh case.
    val app = context.applicationContext as com.houseofmmminq.macaco.TravelJournalApp
    runCatching { app.drivePhotoSync.ensurePhotosCached(entries) }

    // Photo-aware highlight: an "On This Day" match always wins (that's the widget's promise),
    // preferring one whose photo actually resolves; only when there's no anniversary match do we
    // fall back to the most-recent entry — and there too we prefer one with a showable photo, so
    // the card isn't stuck photoless just because the newest-by-date entry happens to have none.
    fun hasPhoto(e: TravelEntry) =
        WidgetPhotos.readableSource(context, e.photoUris.firstOrNull(), e.driveFileIds.firstOrNull()) != null
    val onThisDay = entries.onThisDayEntries()          // already sorted, most recent first
    val recent = entries.sortedByDescending { it.dateMillis }
    onThisDay.firstOrNull { hasPhoto(it) }
        ?: onThisDay.firstOrNull()
        ?: recent.firstOrNull { hasPhoto(it) }
        ?: recent.firstOrNull()
}.getOrNull()
```

No import changes needed beyond the fully-qualified `TravelJournalApp` reference above (or add
`import com.houseofmmminq.macaco.TravelJournalApp` and drop the qualifier — either is fine,
match whatever style the rest of the file uses for cross-package references).

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/widget/OnThisDayWidgetProvider.kt`

---

## Fix 2 — Recent Entries row thumbnails

Same root cause, same fix shape, applied where the recent-entries list does its one-shot Firestore
read.

```kotlin
// BEFORE — RecentEntriesWidgetService.kt
override fun onDataSetChanged() {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    entries = if (uid == null) emptyList() else runCatching {
        val snapshot = Tasks.await(
            FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("entries")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(MAX_ROWS.toLong())
                .get()
        )
        snapshot.documents.mapNotNull { it.toTravelEntry() }
    }.getOrDefault(emptyList())
}
```

```kotlin
// AFTER — onDataSetChanged runs on a binder thread (not a coroutine), and DrivePhotoSync's
// ensurePhotosCached is a suspend fun, so block on it with Tasks-style runBlocking the same way
// this method already blocks on the Firestore read via Tasks.await.
override fun onDataSetChanged() {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    entries = if (uid == null) emptyList() else runCatching {
        val snapshot = Tasks.await(
            FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("entries")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(MAX_ROWS.toLong())
                .get()
        )
        val result = snapshot.documents.mapNotNull { it.toTravelEntry() }
        val app = context.applicationContext as com.houseofmmminq.macaco.TravelJournalApp
        runCatching {
            kotlinx.coroutines.runBlocking { app.drivePhotoSync.ensurePhotosCached(result) }
        }
        result
    }.getOrDefault(emptyList())
}
```

`runBlocking` is safe here specifically because `onDataSetChanged()` is documented by the
`RemoteViewsFactory` contract to run synchronously on its own binder thread (the same reason the
existing code already blocks on `Tasks.await` right above it) — it is not the main thread, so this
doesn't risk an ANR the way `runBlocking` would in an `Activity`.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/widget/RecentEntriesWidgetService.kt`

---

## Fix 3 — remove leftover debug logging

`OnThisDayWidgetProvider.kt` currently has five `android.util.Log.d`/`Log.e` calls marked
`// DEBUG` from the investigation that led to this brief (in `updateOne` and `decodeThumbnail`).
Now that the root cause is understood and fixed above, remove all five so the shipped build isn't
spamming logcat for every user on every widget refresh:

```kotlin
// REMOVE (updateOne):
android.util.Log.d("OnThisDayWidget", "source=$source bitmap=${bitmap?.width}x${bitmap?.height}") // DEBUG

// REMOVE (decodeThumbnail, 3 call sites):
android.util.Log.d("OnThisDayWidget", "decode uri=$uri scheme=${uri.scheme} path=${uri.path} streamNull=${WidgetPhotos.openStream(context, uri) == null}") // DEBUG
android.util.Log.d("OnThisDayWidget", "decode bounds=${boundsOpts.outWidth}x${boundsOpts.outHeight}") // DEBUG

// REMOVE (decodeThumbnail, trailing onFailure — replace with a plain getOrNull()):
}.onFailure { android.util.Log.e("OnThisDayWidget", "decodeThumbnail threw", it) }.getOrNull() // DEBUG
// becomes:
}.getOrNull()
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/widget/OnThisDayWidgetProvider.kt`

---

## Scope notes

- **Not touched, and correct as-is:** `WidgetPhotos.kt`'s `file://` vs `content://` stream-open
  split from the prior fix — that was solving a real, separate bug (Samsung One UI returning null
  from `ContentResolver.openInputStream` for `file://` cache URIs) and stays in place.
- **Network cost is bounded:** `ensurePhotosCached` checks which files are actually missing
  *before* touching the network, and returns immediately if Drive isn't connected — so on every
  refresh after the first successful one, this call is a fast local no-op, not a repeated Drive
  hit. The two widgets refresh every 30 min / 3 hr (`on_this_day_widget_info.xml`,
  `recent_entries_widget_info.xml`) plus on-demand via `requestUpdate()`, so worst case is one real
  download burst per period if new Drive-only photos appear.
- **Needs on-device verification** (this fix can't be confirmed from source alone): rebuild and
  test on the same S8 debug install used for the original report, with the device online and
  signed into the same Google account that has Drive access. Confirm both widgets show a real
  photo within one refresh cycle (or immediately after re-adding the widget). If a widget still
  shows no photo after this fix, the next thing to check is whether `isDriveConnected()` is
  actually true on that device (i.e. the account was signed in via Google Sign-In with the Drive
  scope granted, not email/password) — that's a legitimately no-photo-available state, not a bug.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Trigger `DrivePhotoSync.ensurePhotosCached()` before picking the On This Day highlight | `OnThisDayWidgetProvider.kt` |
| 2 | Trigger `DrivePhotoSync.ensurePhotosCached()` before building Recent Entries rows | `RecentEntriesWidgetService.kt` |
| 3 | Remove 5 leftover `// DEBUG` logcat calls | `OnThisDayWidgetProvider.kt` |
