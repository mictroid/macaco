# Macaco — Home-screen widgets: On This Day contrast, Recent Entries metadata, Travel Stats / Quick Add branding

Post-vc74 QA pass on the 4 home-screen widgets (`app/src/main/res/layout/widget_*.xml` +
`app/src/main/java/.../ui/widget/*.kt`). Three changes: lighten the On This Day scrim, surface
mood/weather/tags on Recent Entries rows (currently unused space), and add the Macaco mark to
Travel Stats and Quick Add (currently the only two widgets with zero brand mark).

Note on styling convention: these are `RemoteViews`-backed AppWidget layouts, not Compose
screens — the existing widget XMLs already use literal hex colors (`#0A4A58`, `#FFFFFF`,
`#99000000`, etc.) matching the app's teal brand rather than `MaterialTheme` tokens, because
RemoteViews can't resolve Compose theme colors. This brief keeps that existing convention
(literal hex, matched to the colors already used across the 4 widget layouts) rather than
introducing MD3 tokens that don't apply here.

---

## 1. On This Day — lighten the photo scrim

**Problem:** `widget_on_this_day.xml` overlays a flat `#99000000` (60% opaque black) scrim across
the *entire* card whenever a photo is shown (`OnThisDayWidgetProvider.showPhoto()` toggles
`R.id.widget_scrim` to `VISIBLE`). Combined with the dark teal `widget_background` showing at the
card's edges, the tile reads as near-black — the photo barely shows through, which is the
"looks a bit dark" feedback. The scrim is uniform because text needs to stay legible over any
photo, but that legibility is only needed at the bottom third where the title/subtitle/date
sit — the top two-thirds carry no text today.

**Fix:** Replace the flat scrim with a top-to-bottom gradient drawable — transparent at the top
(photo fully visible) fading to a darker band only behind the text block at the bottom. This
keeps text contrast where it's needed and removes the wash-of-black effect everywhere else.

```
BEFORE (flat 60% black over whole photo):        AFTER (gradient, photo visible up top):
┌──────────────────────────┐                     ┌──────────────────────────┐
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │ ← 60% black         │  🐵 On This Day          │ ← photo clear
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │   over entire photo │                          │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │                     │      (photo)             │
│ ▓▓▓▓ Title               │                     │ ░░░░░░░░░░░░░░░░░░░░░░░░ │ ← gradient starts
│ ▓▓▓▓ Subtitle · date     │                     │ ▓▓▓▓ Title               │
└──────────────────────────┘                     │ ▓▓▓▓ Subtitle · date     │
                                                  └──────────────────────────┘
```

New drawable — `app/src/main/res/drawable/widget_on_this_day_scrim.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Bottom-weighted gradient scrim for the On This Day photo card: transparent at the top so the
     photo reads clearly, darkening only behind the text block at the bottom for legibility. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:type="linear"
        android:angle="90"
        android:startColor="#00000000"
        android:centerColor="#1A000000"
        android:endColor="#B3000000"
        android:centerY="0.55" />
</shape>
```

`widget_on_this_day.xml` — swap the scrim's background:

```xml
<!-- BEFORE -->
<FrameLayout
    android:id="@+id/widget_scrim"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#99000000" />

<!-- AFTER -->
<FrameLayout
    android:id="@+id/widget_scrim"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_on_this_day_scrim" />
```

No Kotlin changes needed — `OnThisDayWidgetProvider.showPhoto()` already just toggles this view's
visibility; the gradient replaces the flat color underneath it.

**Files:** `app/src/main/res/drawable/widget_on_this_day_scrim.xml` (new),
`app/src/main/res/layout/widget_on_this_day.xml`.

---

## 2. Recent Entries — surface mood / weather / tags in the unused row space

**Problem:** `widget_recent_entry_item.xml` only shows a thumbnail, title, and a
"location · date" subtitle. Each entry already carries `mood` (an emoji string, e.g. `"😄"` — see
`TravelEntry.kt` and how `JournalListScreen.kt` renders it directly as `Text(entry.mood, ...)`),
`weatherCode`/`weatherTempMaxC` (resolved via `WeatherLookup.describe()` /
`WeatherLookup.formatTemp()`, already used the same way in `EntryDetailScreen.kt`), and `tags`.
None of this appears in the widget row even though there's blank space below the subtitle line
today, so the row under-uses what the entry already has.

**Fix:** Add a third, small "meta" line to each row: mood emoji + weather emoji/temp + top tag,
space-separated with `·`, built from whichever fields the entry actually has (older entries
without weather/mood/tags just show a shorter line; the row hides the line entirely if the entry
has none of the three, keeping old-entry rows compact).

```
BEFORE:                                   AFTER:
┌────┬─────────────────────────┐          ┌────┬─────────────────────────┐
│    │ Sagrada Família          │          │    │ Sagrada Família          │
│IMG │ Barcelona · Jul 12, 2024 │          │IMG │ Barcelona · Jul 12, 2024 │
│    │                          │          │    │ 😄 · ☀️ 24° · #architecture│
└────┴─────────────────────────┘          └────┴─────────────────────────┘
```

`widget_recent_entry_item.xml` — add the new TextView after `recent_item_subtitle`:

```xml
<!-- BEFORE -->
        <TextView
            android:id="@+id/recent_item_subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="#CCFFFFFF"
            android:textSize="11sp"
            android:maxLines="1"
            android:ellipsize="end" />
    </LinearLayout>
</LinearLayout>

<!-- AFTER -->
        <TextView
            android:id="@+id/recent_item_subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="#CCFFFFFF"
            android:textSize="11sp"
            android:maxLines="1"
            android:ellipsize="end" />

        <TextView
            android:id="@+id/recent_item_meta"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textColor="#F0C840"
            android:textSize="11sp"
            android:maxLines="1"
            android:ellipsize="end"
            android:visibility="gone" />
    </LinearLayout>
</LinearLayout>
```

(`#F0C840` matches the gold accent `widget_context` already uses on the On This Day card, so the
new line reads as an accent detail rather than competing with the white title/subtitle.)

`RecentEntriesWidgetService.kt` — `getViewAt(position)`: build and set the meta line right after
the existing subtitle line, hiding it when there's nothing to show.

```kotlin
// BEFORE
        views.setTextViewText(
            R.id.recent_item_subtitle,
            if (entry.location.isBlank()) date else "${entry.location} · $date"
        )
        val source = WidgetPhotos.readableSource(

// AFTER
        views.setTextViewText(
            R.id.recent_item_subtitle,
            if (entry.location.isBlank()) date else "${entry.location} · $date"
        )
        val metaParts = buildList {
            if (entry.mood.isNotBlank()) add(entry.mood)
            entry.weatherCode?.let { code ->
                val (icon, _) = com.houseofmmminq.macaco.util.WeatherLookup.describe(context, code)
                val temp = entry.weatherTempMaxC?.let {
                    com.houseofmmminq.macaco.util.WeatherLookup.formatTemp(
                        context, it, entry.weatherIsFahrenheit ?: false
                    )
                }
                add(if (temp != null) "$icon $temp" else icon)
            }
            entry.tags.firstOrNull()?.let { add("#$it") }
        }
        if (metaParts.isEmpty()) {
            views.setViewVisibility(R.id.recent_item_meta, android.view.View.GONE)
        } else {
            views.setTextViewText(R.id.recent_item_meta, metaParts.joinToString(" · "))
            views.setViewVisibility(R.id.recent_item_meta, android.view.View.VISIBLE)
        }
        val source = WidgetPhotos.readableSource(
```

Scope note: only the *first* tag is shown (not the full list) — rows are already tight at 8 max
visible, and a full tag list would wrap or get truncated unpredictably across devices. Same
reasoning as `RecentEntriesFactory` already using `photoUris.firstOrNull()` rather than a carousel.

**Files:** `app/src/main/res/layout/widget_recent_entry_item.xml`,
`app/src/main/java/com/houseofmmminq/macaco/ui/widget/RecentEntriesWidgetService.kt`.

---

## 3. Travel Stats & Quick Add — add the Macaco mark

**Problem:** Every widget except these two carries the `ic_macaco_widget_mark` brand icon in a
small header row (`widget_on_this_day.xml` line ~33, `widget_recent_entries.xml` line ~19). Travel
Stats (`widget_travel_stats.xml`) and Quick Add (`widget_quick_add.xml`) currently render with zero
brand mark — just numbers, or a generic `+` icon — so on a home screen they don't visually read as
Macaco at a glance next to the other two widgets.

**Fix — Travel Stats:** add the same small header row style (icon + widget label) used on the
other two widgets, above the two-column stat layout.

```
BEFORE:                        AFTER:
┌─────────────────┐            ┌─────────────────┐
│                 │            │ 🐵 Travel Stats  │
│   12      8     │            │                 │
│ entries places  │            │   12      8     │
│                 │            │ entries places  │
└─────────────────┘            └─────────────────┘
```

`widget_travel_stats.xml` — wrap the existing horizontal stat row in a vertical LinearLayout with
a new header on top (mirrors the header block already used in `widget_recent_entries.xml`):

```xml
<!-- BEFORE -->
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
        ...

<!-- AFTER -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_stats_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background"
    android:orientation="vertical"
    android:padding="10dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingBottom="6dp">

        <ImageView
            android:layout_width="16dp"
            android:layout_height="16dp"
            android:src="@drawable/ic_macaco_widget_mark"
            android:contentDescription="@null" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="6dp"
            android:text="@string/settings_widget_stats"
            android:textColor="#FFFFFF"
            android:textSize="12sp"
            android:textStyle="bold" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:orientation="horizontal"
        android:gravity="center"
        android:weightSum="2">

        <LinearLayout
            android:layout_width="0dp"
            ...
```

(Close the new wrapping `LinearLayout` after the existing two stat columns; the two inner
`LinearLayout` stat columns are unchanged, just re-parented one level deeper with the header
above them.) `settings_widget_stats` already exists (`"Travel Stats"`) — no new string needed.

**Fix — Quick Add:** the "+' icon already fills the branding role at this widget's tiny size
(typically 1×1); a full header row would crowd it. Add the Macaco mark as a small corner badge
instead, so the widget stays minimal but is still identifiable as Macaco's.

```
BEFORE:              AFTER:
┌───────┐            ┌───────┐
│       │            │🐵     │
│   +   │            │   +   │
│New    │            │New    │
│entry  │            │entry  │
└───────┘            └───────┘
```

`widget_quick_add.xml` — add a small top-start `ImageView` using the existing mark, as a sibling
of the centered content `LinearLayout`:

```xml
<!-- BEFORE -->
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

<!-- AFTER -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_quick_add_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background">

    <ImageView
        android:layout_width="14dp"
        android:layout_height="14dp"
        android:layout_gravity="top|start"
        android:layout_margin="8dp"
        android:src="@drawable/ic_macaco_widget_mark"
        android:alpha="0.85"
        android:contentDescription="@null" />

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:orientation="vertical"
        android:gravity="center">
```

No Kotlin changes for either — both providers only set text/click intents, layout is static.

**Files:** `app/src/main/res/layout/widget_travel_stats.xml`,
`app/src/main/res/layout/widget_quick_add.xml`.

---

## Summary

| # | Change | File(s) |
|---|--------|---------|
| 1 | Gradient (not flat) scrim on On This Day so photos read lighter | `widget_on_this_day_scrim.xml` (new), `widget_on_this_day.xml` |
| 2 | Recent Entries rows show mood/weather/top-tag meta line | `widget_recent_entry_item.xml`, `RecentEntriesWidgetService.kt` |
| 3 | Travel Stats gets a branded header row | `widget_travel_stats.xml` |
| 4 | Quick Add gets a small Macaco corner mark | `widget_quick_add.xml` |

No string additions required — `settings_widget_stats` already exists. No localization work
needed for this brief.
