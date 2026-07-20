# Macaco — Recent Entries Widget: Merge Meta Line Into Date Row

Removes the separate third text row (mood/weather/tag) from each widget list item and folds it
into the location/date row instead, closing the empty space visible under short date lines.
Touches `widget_recent_entry_item.xml` and `RecentEntriesWidgetService.kt`.

---

## Merge mood/weather/tag onto the location · date line

**Problem:** Each row in the Recent Entries widget (4×2 collection widget, `ListView` backed by
`RecentEntriesWidgetService`) currently renders three lines: title, "location · date", then a
separate `recent_item_meta` row for mood/weather/tag (only visible if non-empty). Because the
second line is usually short ("Berlin · Jul 14, 2026"), it doesn't use the row's full width, and
the meta row underneath it — the mood emoji, weather icon+temp, first tag — sits in what's mostly
wasted vertical space rather than sharing the line above it. Each entry currently uses more
vertical space than it needs, so fewer entries are visible in the widget.

**Fix:** Append the meta parts to the same string used for the subtitle line, separated by " · ",
same as they're already joined among themselves. Remove the separate `recent_item_meta` TextView
from the layout, and its show/hide + text-setting logic from the service. The combined line keeps
`maxLines="1"` + `ellipsize="end"` (already set), so if it doesn't fit, it truncates from the end
rather than wrapping or growing the row — acceptable per the approved design ("merge into date
line, remove meta row" — no overflow fallback row).

```
BEFORE                                  AFTER
┌─────────────────────────────┐        ┌─────────────────────────────┐
│ [photo] Berlin                │        │ [photo] Berlin                │
│         Berlin · Jul 14, 2026 │        │         Berlin · Jul 14, 2026 │
│         🥵 ☔ 24°C · #city     │        │         · 🥵 ☔ 24°C · #city   │
└─────────────────────────────┘        └─────────────────────────────┘
     3 rows, meta row often mostly empty      2 rows, one line, ellipsized if long
```

```xml
<!-- BEFORE — widget_recent_entry_item.xml, lines 35–53 -->
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

<!-- AFTER — remove recent_item_meta entirely; subtitle unchanged (it now carries everything) -->
        <TextView
            android:id="@+id/recent_item_subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="#CCFFFFFF"
            android:textSize="11sp"
            android:maxLines="1"
            android:ellipsize="end" />
```

```kotlin
// BEFORE — RecentEntriesWidgetService.kt, lines 73–97
        views.setTextViewText(R.id.recent_item_title, entry.title)
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.dateMillis))
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

// AFTER
        views.setTextViewText(R.id.recent_item_title, entry.title)
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.dateMillis))
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
        val subtitleParts = buildList {
            if (entry.location.isNotBlank()) add(entry.location)
            add(date)
            addAll(metaParts)
        }
        views.setTextViewText(R.id.recent_item_subtitle, subtitleParts.joinToString(" · "))
```

Note the `recent_item_meta` view ID is now unused — remove its `id` from the layout entirely
rather than leaving it declared-but-unreferenced.

**Files:**
- `app/src/main/res/layout/widget_recent_entry_item.xml`
- `app/src/main/java/com/houseofmmminq/macaco/ui/widget/RecentEntriesWidgetService.kt`

---

## Out of scope

No fallback/overflow row for long combined lines — per the approved design, truncation via
`ellipsize="end"` on a single line is intentional, not a gap to fill later.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove separate `recent_item_meta` row from layout | `widget_recent_entry_item.xml` |
| 2 | Append mood/weather/tag onto the location · date line, drop meta view logic | `RecentEntriesWidgetService.kt` |
