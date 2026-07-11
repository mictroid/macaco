# Macaco — "On This Day" Home Screen Widget

New feature: a home-screen widget showing today's "On This Day" memory (same entries
`onThisDayEntries()` already surfaces as an in-app banner) when one exists, falling back to the
most recent entry otherwise — tapping it opens the app. Built as a classic `AppWidgetProvider` +
`RemoteViews`, not Glance — the app has zero Compose-for-Glance today and WorkManager is already
a dependency (`ReminderScheduler.kt`), so this needs **no new Gradle dependency at all**. Six
files touched, four new: `data/model/TravelEntry.kt`, `ui/widget/OnThisDayWidgetProvider.kt`
(NEW), `res/xml/on_this_day_widget_info.xml` (NEW), `res/layout/widget_on_this_day.xml` (NEW),
`res/drawable` (a small widget preview image — see Scope), `AndroidManifest.xml`,
`ui/viewmodel/JournalViewModel.kt`.

---

## Change 1 — Widget-friendly "pick one entry" helper

**Problem:** `onThisDayEntries()` returns a list (the in-app banner can show several); the widget
needs exactly one entry to render, with a defined fallback when there's no "on this day" match.

**Fix:** small extension reusing the existing function rather than duplicating its date logic.

```kotlin
// data/model/TravelEntry.kt — ADD, directly below onThisDayEntries()

/** The single entry the widget shows: the most recent "on this day" match if one exists,
 *  otherwise the most recently created entry overall, or null for an empty journal. */
fun List<TravelEntry>.widgetHighlight(): TravelEntry? =
    onThisDayEntries().firstOrNull() ?: maxByOrNull { it.dateMillis }
```

**File:** `data/model/TravelEntry.kt`.

---

## Change 2 — `OnThisDayWidgetProvider` (NEW FILE)

Runs in the app's normal process (the default for `AppWidgetProvider` unless a separate
`:remote` process is declared, which this doesn't), so it can reach `FirebaseAuth`/`FirebaseFirestore`
directly. Deliberately does its **own one-shot Firestore query** rather than reading
`CloudEntrySync`'s cached `StateFlow` — a widget update can be the thing that cold-starts the
process (e.g. the OS's periodic widget alarm after the app was killed), in which case
`CloudEntrySync`'s snapshot listener may not have delivered data yet. A direct one-shot `.get()`
is slower per-call but always correct.

```kotlin
package com.houseofmmminq.macaco.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.houseofmmminq.macaco.MainActivity
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.data.model.widgetHighlight
import com.houseofmmminq.macaco.util.ImageStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OnThisDayWidgetProvider : AppWidgetProvider() {

    companion object {
        /** Called after a successful save/delete (JournalViewModel) so the widget doesn't wait for
         *  its next periodic refresh to reflect a new/changed entry. */
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, OnThisDayWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, OnThisDayWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val entry = uid?.let { fetchHighlight(it) }
            appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id, entry, signedIn = uid != null) }
            pendingResult.finish()
        }
    }

    private suspend fun fetchHighlight(uid: String): TravelEntry? = runCatching {
        val snapshot = FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("entries")
            .get().await()
        // Same decode shape as CloudEntrySync.startListening — kept in sync manually since a
        // widget-process query can't share that private mapper; see Scope for the cleanup note.
        val entries = snapshot.documents.mapNotNull { doc ->
            runCatching {
                TravelEntry(
                    id = doc.getString("id") ?: doc.id,
                    title = doc.getString("title") ?: return@runCatching null,
                    location = doc.getString("location") ?: "",
                    dateMillis = doc.getLong("dateMillis") ?: 0L,
                    description = doc.getString("description") ?: "",
                    mood = doc.getString("mood") ?: "",
                    photoUris = (doc.get("photoUris") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    createdAt = doc.getLong("createdAt") ?: 0L
                )
            }.getOrNull()
        }
        entries.widgetHighlight()
    }.getOrNull()

    private fun updateOne(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        entry: TravelEntry?,
        signedIn: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_on_this_day)
        val openAppIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)

        when {
            !signedIn -> {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_signed_out_title))
                views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_signed_out_subtitle))
                views.setImageViewResource(R.id.widget_photo, R.drawable.ic_launcher_foreground)
            }
            entry == null -> {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
                views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_empty_subtitle))
                views.setImageViewResource(R.id.widget_photo, R.drawable.ic_launcher_foreground)
            }
            else -> {
                views.setTextViewText(R.id.widget_title, entry.title)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    entry.location.ifBlank { null } ?: context.getString(R.string.widget_no_location)
                )
                // Decoded to a small in-memory Bitmap (not setImageViewUri) so there's no
                // cross-process content:// permission question for the launcher/widget host to
                // resolve — same EXIF-safe decode pattern as JournalBackup.compressToBytes.
                val bitmap = entry.photoUris.firstOrNull()?.let { decodeThumbnail(context, it) }
                if (bitmap != null) views.setImageViewBitmap(R.id.widget_photo, bitmap)
                else views.setImageViewResource(R.id.widget_photo, R.drawable.ic_launcher_foreground)
            }
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun decodeThumbnail(context: Context, uriString: String): Bitmap? = runCatching {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) } ?: return null
        var sample = 1
        while (boundsOpts.outWidth / (sample * 2) >= 300 && boundsOpts.outHeight / (sample * 2) >= 300) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        ImageStorage.applyExifOrientation(bitmap, orientation)
    }.getOrNull()
}
```

`.await()` needs `import kotlinx.coroutines.tasks.await` (the `kotlinx-coroutines-play-services`
artifact — already a dependency per the Tech Stack table's "Async" row). `applyExifOrientation`
is `internal`, callable from anywhere in this module.

**File:** `ui/widget/OnThisDayWidgetProvider.kt` (new).

---

## Change 3 — Widget metadata + layout (NEW FILES)

```xml
<!-- res/xml/on_this_day_widget_info.xml -->
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_on_this_day"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:previewImage="@drawable/widget_preview" />
```

`updatePeriodMillis` is clamped to a 30-minute floor by the OS regardless of the value set — this
is why `OnThisDayWidgetProvider.requestUpdate()` (Change 4) exists, for same-session freshness
after a save/delete rather than waiting up to 30 minutes.

```xml
<!-- res/layout/widget_on_this_day.xml — simple two-line card over a photo, dark scrim for
     legibility regardless of the photo's own brightness. Uses plain View attributes (RemoteViews
     can't use most Compose/Material3 machinery), but the scrim/text colors below are chosen to
     match the app's dark-teal brand rather than hardcoded arbitrarily. -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background">

    <ImageView
        android:id="@+id/widget_photo"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="centerCrop" />

    <View
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#99000000" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:orientation="vertical"
        android:padding="12dp">

        <TextView
            android:id="@+id/widget_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#FFFFFF"
            android:textSize="16sp"
            android:textStyle="bold"
            android:maxLines="1"
            android:ellipsize="end" />

        <TextView
            android:id="@+id/widget_subtitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#CCFFFFFF"
            android:textSize="12sp"
            android:maxLines="1"
            android:ellipsize="end" />
    </LinearLayout>
</FrameLayout>
```

`@drawable/widget_background` is a rounded-rect shape drawable (dark-teal fill, matching
`OUTRO_BG`/`SplashTealMid` used elsewhere) for the corner radius Android widgets are expected to
respect on modern launchers — add as a small new `res/drawable/widget_background.xml`
(`<shape><solid android:color="#0A4A58"/><corners android:radius="16dp"/></shape>`).

**Files:** `res/xml/on_this_day_widget_info.xml` (new), `res/layout/widget_on_this_day.xml` (new),
`res/drawable/widget_background.xml` (new).

---

## Change 4 — Manifest registration + refresh nudge on save/delete

```xml
<!-- AndroidManifest.xml — ADD inside <application>, alongside the existing SnoozeReceiver -->
<receiver
    android:name=".ui.widget.OnThisDayWidgetProvider"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/on_this_day_widget_info" />
</receiver>
```

```kotlin
// ui/viewmodel/JournalViewModel.kt — inside saveEntry(), right after cloudEntrySync.save(entry)
            cloudEntrySync.save(entry)
            OnThisDayWidgetProvider.requestUpdate(appContext)
```

Add the same call inside the existing `deleteEntry`-equivalent function (search this file for
`cloudEntrySync.delete(` and add the nudge immediately after it) — a deleted entry that was the
widget's current highlight should refresh away, not linger.

New import: `com.houseofmmminq.macaco.ui.widget.OnThisDayWidgetProvider`.

**Files:** `AndroidManifest.xml`, `ui/viewmodel/JournalViewModel.kt`.

---

## Localization

| Key | EN value |
|-----|----------|
| `widget_signed_out_title` | Sign in to Macaco |
| `widget_signed_out_subtitle` | Tap to open the app |
| `widget_empty_title` | Start your travel journal |
| `widget_empty_subtitle` | Tap to write your first entry |
| `widget_no_location` | Somewhere memorable |

All 11 languages need these 5 keys.

---

## Scope

- **In:** a single fixed-size resizable widget showing the "on this day" entry (falling back to
  the latest entry, then a signed-out/empty state), tap-to-open, refreshed on save/delete plus the
  OS's own ~30-min floor.
- **Out:** deep-linking the tap directly into that entry's detail screen — v1 opens the app to its
  normal start destination. Wiring a nav deep link (`NavDeepLink` + an intent extra carrying the
  entry id, consumed once in `NavGraph`) is a small, self-contained fast-follow.
- **Out:** a real widget preview screenshot at `res/drawable/widget_preview.xml` /
  `.png` — reference it in the widget-info XML above, but generating the actual preview asset is
  a Cowork/design task (a static image), not something Code should draw from scratch; a plain
  placeholder color drawable is an acceptable stand-in until that exists.
- **Out:** a duplicate of `CloudEntrySync`'s Firestore→`TravelEntry` mapper living in the widget
  provider (Change 2) is intentional for cold-process reliability, not an oversight — but it does
  mean the two mappers must be kept in sync if `TravelEntry`'s schema changes. Extracting a single
  shared `FirestoreEntryMapper` used by both is a reasonable cleanup once this ships, not required
  for v1.
- **Out:** multiple widget sizes/variants (a larger 4×2 layout with more text, a square variant)
  — v1 is one resizable layout.

---

## Verification

1. Add the widget to the home screen while signed out — confirm the "Sign in" state, and tapping
   it opens the app.
2. Sign in with zero entries — confirm the empty-journal state.
3. Add an entry dated today with a photo. Confirm the widget updates within moments (via
   `requestUpdate`), showing that entry's photo, title, and location.
4. Add an entry dated exactly one year (and two years) ago on today's month/day, in addition to a
   more recent entry. Confirm the widget prefers the "on this day" match over the more recent
   entry (`widgetHighlight()`'s ordering).
5. Delete the entry currently shown on the widget. Confirm it updates to the next-best highlight
   (or the empty state) rather than showing a stale photo.
6. Force-stop the app, then wait for (or manually trigger via `adb shell am broadcast
   -a android.appwidget.action.APPWIDGET_UPDATE`) a fresh widget update from a cold process.
   Confirm the one-shot Firestore query still resolves correctly rather than showing stale/blank
   data — this is the scenario Change 2's design note is protecting against.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `widgetHighlight()` extension | `TravelEntry.kt` |
| 2 | `OnThisDayWidgetProvider`: one-shot Firestore query + RemoteViews render | `OnThisDayWidgetProvider.kt` (new) |
| 3 | Widget metadata, layout, background drawable | `on_this_day_widget_info.xml`, `widget_on_this_day.xml`, `widget_background.xml` (new) |
| 4 | Manifest receiver + refresh nudge on save/delete | `AndroidManifest.xml`, `JournalViewModel.kt` |
| — | 5 new string keys × 11 languages | `strings.xml` × 11 |
