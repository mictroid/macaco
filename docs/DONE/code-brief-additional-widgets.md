# Macaco — Three New Home-Screen Widgets: Quick Add, Travel Stats, Adventures Map Mini

Adds three new `AppWidgetProvider`s alongside the existing `OnThisDayWidgetProvider`: a 1×1
quick-add button, a 2×1 travel-stats card, and a 4×2 decorative "places mapped" card. Touches
`AndroidManifest.xml`, three new Kotlin provider classes under `ui/widget/`, three new layout
XMLs, three new widget-info XMLs, `data/model/TravelEntry.kt` (one new aggregate helper), and
`ui/viewmodel/JournalViewModel.kt` (refresh hooks). Read `code-brief-widget-exported-fix.md`
first — all three new receivers below are declared `exported="true"` from the start so they
don't ship with the same bug.

All three reuse patterns already in the codebase rather than inventing new ones:
- `OnThisDayWidgetProvider.kt` for the `goAsync()` + Firestore-read + `RemoteViews` pattern.
- `ReminderWorker.kt`'s `ACTION_NEW_ENTRY` deep link for the quick-add button.
- `widget_background.xml` / `ic_add.xml` for consistent visuals with the existing widget.

---

## 1. Quick Add widget (1×1 button → New Entry screen)

**Problem:** There's no fast path from the home screen into entry creation — the existing widget
only opens the app to whatever screen was last active. `MainActivity` already exposes a deep link
for this (`ACTION_NEW_ENTRY`, used today by `ReminderWorker.kt` lines 82–90 for the "write an
entry" notification) but nothing wires it to a widget.

**Fix:** A static widget — no data fetch, no `onUpdate` logic beyond setting one `PendingIntent`.
Reuses the exact intent shape `ReminderWorker` already builds.

```
┌──────────┐
│    +     │   1x1, tap anywhere → opens New Entry
│ New entry│
└──────────┘
```

```kotlin
// NEW FILE — app/src/main/java/com/houseofmmminq/macaco/ui/widget/QuickAddWidgetProvider.kt
package com.houseofmmminq.macaco.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.houseofmmminq.macaco.MainActivity
import com.houseofmmminq.macaco.R

class QuickAddWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_NEW_ENTRY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
            views.setOnClickPendingIntent(R.id.widget_quick_add_root, pendingIntent)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
```

```xml
<!-- NEW FILE — app/src/main/res/layout/widget_quick_add.xml -->
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_quick_add_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:orientation="vertical"
        android:gravity="center">

        <ImageView
            android:layout_width="28dp"
            android:layout_height="28dp"
            android:src="@drawable/ic_add"
            android:contentDescription="@null" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="@string/widget_quick_add_label"
            android:textColor="#FFFFFF"
            android:textSize="12sp" />
    </LinearLayout>
</FrameLayout>
```

```xml
<!-- NEW FILE — app/src/main/res/xml/quick_add_widget_info.xml -->
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="70dp"
    android:minHeight="70dp"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_quick_add"
    android:resizeMode="none"
    android:widgetCategory="home_screen"
    android:previewImage="@drawable/widget_background" />
```

`updatePeriodMillis="0"` and `resizeMode="none"` are deliberate — this widget has no data to
refresh and is only meant at its native 1×1 size (a resized quick-add button doesn't gain
anything, unlike a photo or stats card).

**Files:** `QuickAddWidgetProvider.kt` (new), `widget_quick_add.xml` layout (new),
`quick_add_widget_info.xml` (new), `AndroidManifest.xml` (receiver, see §4), `strings.xml` ×11
(one new key, see §5).

---

## 2. Travel Stats widget (2×1: entry count + places visited)

**Problem:** No widget surfaces the "how much have I journalled" number that Year in Travel shows
inside the app. Note: `TravelEntry` has no country field — `location` is free text (see
`data/model/TravelEntry.kt`) — so this counts **distinct places** (reusing the existing
`List<TravelEntry>.locations()` extension), not "countries," to avoid showing a stat the data
model can't actually back.

**Fix:** Same `goAsync()` + one-shot Firestore read pattern as `OnThisDayWidgetProvider`, but
aggregates instead of picking one entry.

```
┌──────────┬──────────┐
│    42    │    9     │
│  entries │  places  │
└──────────┴──────────┘
```

```kotlin
// NEW FILE — app/src/main/java/com/houseofmmminq/macaco/ui/widget/TravelStatsWidgetProvider.kt
package com.houseofmmminq.macaco.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.houseofmmminq.macaco.MainActivity
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.locations
import com.houseofmmminq.macaco.data.storage.toTravelEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TravelStatsWidgetProvider : AppWidgetProvider() {

    companion object {
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, TravelStatsWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, TravelStatsWidgetProvider::class.java).apply {
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
            val (entryCount, placeCount) = uid?.let { fetchCounts(it) } ?: (0 to 0)
            appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id, entryCount, placeCount, signedIn = uid != null) }
            pendingResult.finish()
        }
    }

    private suspend fun fetchCounts(uid: String): Pair<Int, Int> = runCatching {
        val snapshot = FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("entries")
            .get().await()
        val entries = snapshot.documents.mapNotNull { it.toTravelEntry() }
        entries.size to entries.locations().size
    }.getOrDefault(0 to 0)

    private fun updateOne(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        entryCount: Int,
        placeCount: Int,
        signedIn: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_travel_stats)
        val openAppIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_stats_root, openAppIntent)
        if (signedIn) {
            views.setTextViewText(R.id.widget_stats_entries_value, entryCount.toString())
            views.setTextViewText(R.id.widget_stats_places_value, placeCount.toString())
        } else {
            views.setTextViewText(R.id.widget_stats_entries_value, "–")
            views.setTextViewText(R.id.widget_stats_places_value, "–")
        }
        views.setTextViewText(R.id.widget_stats_entries_label, context.getString(R.string.widget_stats_entries_label))
        views.setTextViewText(R.id.widget_stats_places_label, context.getString(R.string.widget_stats_places_label))
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
```

```xml
<!-- NEW FILE — app/src/main/res/layout/widget_travel_stats.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_stats_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background"
    android:orientation="horizontal"
    android:gravity="center"
    android:weightSum="2">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:gravity="center">
        <TextView
            android:id="@+id/widget_stats_entries_value"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#FFFFFF"
            android:textSize="20sp"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/widget_stats_entries_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#CCFFFFFF"
            android:textSize="11sp" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:gravity="center">
        <TextView
            android:id="@+id/widget_stats_places_value"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#FFFFFF"
            android:textSize="20sp"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/widget_stats_places_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#CCFFFFFF"
            android:textSize="11sp" />
    </LinearLayout>
</LinearLayout>
```

```xml
<!-- NEW FILE — app/src/main/res/xml/travel_stats_widget_info.xml -->
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="70dp"
    android:updatePeriodMillis="10800000"
    android:initialLayout="@layout/widget_travel_stats"
    android:resizeMode="horizontal"
    android:widgetCategory="home_screen"
    android:previewImage="@drawable/widget_background" />
```

`updatePeriodMillis` of 3 hours (`10800000`ms) matches how infrequently these totals actually
move for a typical user — no need for the 30-minute cadence `OnThisDayWidgetProvider` uses, since
that widget's "on this day" match changes daily but stats only creep up per new entry (which the
`requestUpdate` hook below covers immediately anyway).

**Refresh hook** — mirrors the existing `OnThisDayWidgetProvider.requestUpdate` calls:

```kotlin
// EDIT — ui/viewmodel/JournalViewModel.kt, saveEntry() and deleteEntry()
// BEFORE (saveEntry, line 494; deleteEntry, line 562 — same call in both):
OnThisDayWidgetProvider.requestUpdate(appContext)

// AFTER — add the stats widget's own refresh alongside it in both places:
OnThisDayWidgetProvider.requestUpdate(appContext)
TravelStatsWidgetProvider.requestUpdate(appContext)
```

(Needs `import com.houseofmmminq.macaco.ui.widget.TravelStatsWidgetProvider` added to
`JournalViewModel.kt`'s import block.)

**Files:** `TravelStatsWidgetProvider.kt` (new), `widget_travel_stats.xml` layout (new),
`travel_stats_widget_info.xml` (new), `AndroidManifest.xml` (receiver, see §4),
`JournalViewModel.kt` (2-line edit), `strings.xml` ×11 (two new keys, see §5).

---

## 3. Adventures Map Mini widget (4×2, decorative "places mapped" card)

**Problem/scope decision:** The in-app Adventures map (`MapScreen.kt`) renders live Google Maps
tiles with pins geocoded from each entry's free-text `location` via Android's `Geocoder`
(`JournalViewModel.geocodeLocations`, in-memory cache only, populated only while the map screen is
open — never persisted). `RemoteViews` cannot host a `GoogleMap` composable, and re-running
`Geocoder.getFromLocationName` for every distinct location inside a widget's `goAsync()` window
is slow and unreliable (device-dependent, network-backed, no guaranteed latency) — not something
to do on every widget refresh.

**This brief implements a decorative, non-geographic version**: a dark card with a stylized
constellation of dots (not real coordinates) and a "N places mapped" caption, tapping through to
the Adventures tab. It is honest about not being a real map preview — the dots are visual texture,
not pins — same spirit as `ic_launcher_foreground` standing in for a missing photo today.

*Explicitly out of scope:* a literal static map snapshot (e.g. via Google's Static Maps API) is a
separate, larger decision — it means enabling/paying for the Static Maps API on top of the
Maps SDK already in use, and geocoding every location server-side or caching lat/lngs on
`TravelEntry` itself (a schema change). Flagging this as a possible v2, not building it now.

```
┌────────────────────────────────┐
│  · ·      ·                    │
│      ·         ·      ·        │  4x2, dots are decorative
│                                 │
│  9 places mapped                │
│  Tap to open Adventures         │
└────────────────────────────────┘
```

```kotlin
// NEW FILE — app/src/main/java/com/houseofmmminq/macaco/ui/widget/AdventuresMapWidgetProvider.kt
package com.houseofmmminq.macaco.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.houseofmmminq.macaco.MainActivity
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.locations
import com.houseofmmminq.macaco.data.storage.toTravelEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdventuresMapWidgetProvider : AppWidgetProvider() {

    companion object {
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, AdventuresMapWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, AdventuresMapWidgetProvider::class.java).apply {
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
            val placeCount = uid?.let { fetchPlaceCount(it) } ?: 0
            appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id, placeCount, signedIn = uid != null) }
            pendingResult.finish()
        }
    }

    private suspend fun fetchPlaceCount(uid: String): Int = runCatching {
        FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("entries")
            .get().await()
            .documents.mapNotNull { it.toTravelEntry() }
            .locations().size
    }.getOrDefault(0)

    private fun updateOne(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        placeCount: Int,
        signedIn: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_adventures_map)
        val openAppIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_map_root, openAppIntent)
        views.setTextViewText(
            R.id.widget_map_caption,
            if (signedIn) context.resources.getQuantityString(R.plurals.widget_map_places_count, placeCount, placeCount)
            else context.getString(R.string.widget_signed_out_subtitle)
        )
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
```

```xml
<!-- NEW FILE — app/src/main/res/layout/widget_adventures_map.xml -->
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_map_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_map_dots_background">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|start"
        android:orientation="vertical"
        android:padding="12dp">

        <TextView
            android:id="@+id/widget_map_caption"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#FFFFFF"
            android:textSize="14sp"
            android:textStyle="bold" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/widget_map_tap_hint"
            android:textColor="#CCFFFFFF"
            android:textSize="11sp" />
    </LinearLayout>
</FrameLayout>
```

The decorative dot texture is a static drawable (not generated per-update — it's stylistic, not
data), so it's a plain vector reusing the app's brand teal + amber:

```xml
<!-- NEW FILE — app/src/main/res/drawable/widget_map_dots_background.xml -->
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/widget_background" />
    <item android:gravity="center">
        <!-- Placeholder decorative dot cluster — swap for a designed asset if desired.
             Kept as simple ovals so it's cheap to theme-match and doesn't need an SVG import. -->
        <shape android:shape="oval">
            <solid android:color="#00000000" />
        </shape>
    </item>
</layer-list>
```

Note for Code: the layer-list above is a minimal placeholder (transparent — relies on
`widget_background` alone) rather than hand-coding scattered dot positions in XML, which is
awkward without a canvas API. If a real decorative texture is wanted, the cleanest path is a
small designed PNG/WebP (e.g. exported from the mockup already shown to the user) dropped in as
`widget_map_dots_background.xml`'s base layer — flag this back to Michael rather than guessing at
placement.

```xml
<!-- NEW FILE — app/src/main/res/xml/adventures_map_widget_info.xml -->
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:updatePeriodMillis="10800000"
    android:initialLayout="@layout/widget_adventures_map"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:previewImage="@drawable/widget_background" />
```

**Refresh hook** — same pattern as §2, add alongside the other two calls:

```kotlin
// EDIT — ui/viewmodel/JournalViewModel.kt, saveEntry() and deleteEntry()
OnThisDayWidgetProvider.requestUpdate(appContext)
TravelStatsWidgetProvider.requestUpdate(appContext)
AdventuresMapWidgetProvider.requestUpdate(appContext)
```

**Files:** `AdventuresMapWidgetProvider.kt` (new), `widget_adventures_map.xml` layout (new),
`widget_map_dots_background.xml` (new), `adventures_map_widget_info.xml` (new),
`AndroidManifest.xml` (receiver, see §4), `JournalViewModel.kt` (shared edit with §2), `strings.xml`
×11 (plurals + one key, see §5).

---

## 4. Manifest registration (all three, `exported="true"` from day one)

```xml
<!-- ADD to AndroidManifest.xml, alongside the existing OnThisDayWidgetProvider receiver -->

<receiver
    android:name=".ui.widget.QuickAddWidgetProvider"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/quick_add_widget_info" />
</receiver>

<receiver
    android:name=".ui.widget.TravelStatsWidgetProvider"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/travel_stats_widget_info" />
</receiver>

<receiver
    android:name=".ui.widget.AdventuresMapWidgetProvider"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/adventures_map_widget_info" />
</receiver>
```

**File:** `app/src/main/AndroidManifest.xml`

---

## 5. New strings (×11 locales)

| Key | EN value |
|-----|----------|
| `widget_quick_add_label` | New entry |
| `widget_stats_entries_label` | entries |
| `widget_stats_places_label` | places |
| `widget_map_tap_hint` | Tap to open Adventures |
| `widget_map_places_count` (plurals: one/other) | one: "%d place mapped" · other: "%d places mapped" |

All five need adding to `values/strings.xml` and translated equivalents in the other 10 locale
files (`values-fr`, `values-es`, `values-pl`, `values-sv`, `values-ja`, `values-nl`, `values-pt`,
`values-it`, `values-zh-rCN`, `values-de`), matching the existing widget string block's tone
(`widget_signed_out_title` etc., same files, ~line 271 in the base file).

---

## 6. Also update Help & About

Once these ship, `help_faq_widget_q`/`help_faq_widget_a` (base `strings.xml` lines 90-91) will be
stale the same way the free-trial FAQ was — it currently only mentions the "On This Day" widget.
Not in scope for this brief (it's a copy-only follow-up, same shape as
`code-brief-help-about-trial-copy-fix.md`), but flagging so it isn't forgotten once these land.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | New Quick Add widget provider | `QuickAddWidgetProvider.kt` (new) |
| 2 | Quick Add layout | `widget_quick_add.xml` (new) |
| 3 | Quick Add widget info | `quick_add_widget_info.xml` (new) |
| 4 | New Travel Stats widget provider | `TravelStatsWidgetProvider.kt` (new) |
| 5 | Travel Stats layout | `widget_travel_stats.xml` (new) |
| 6 | Travel Stats widget info | `travel_stats_widget_info.xml` (new) |
| 7 | New Adventures Map Mini widget provider | `AdventuresMapWidgetProvider.kt` (new) |
| 8 | Adventures Map Mini layout | `widget_adventures_map.xml` (new) |
| 9 | Decorative background drawable | `widget_map_dots_background.xml` (new) |
| 10 | Adventures Map Mini widget info | `adventures_map_widget_info.xml` (new) |
| 11 | Register all 3 receivers, `exported="true"` | `AndroidManifest.xml` |
| 12 | Refresh hooks for stats + map widgets | `JournalViewModel.kt` |
| 13 | 5 new string keys × 11 locales | `strings.xml` (×11) |
