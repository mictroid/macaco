# Macaco — Localization: Replace Hardcoded English Strings

Moves all remaining hardcoded user-visible English into `strings.xml` (×11 languages). The app
ships en, de, fr, es, it, nl, pt, pl, sv, ja, zh-rCN — every string below currently renders in
English for all of them. Touches `MapScreen.kt`, `ProfileScreen.kt`, `JournalListScreen.kt`,
`PurchaseScreen.kt`, `EntryDetailScreen.kt`, `NewEditEntryScreen.kt`, `ReminderWorker.kt`,
`ReminderScheduler.kt`, `NotificationCopy.kt`, and all 11 `strings.xml` files.

**Explicitly out of scope:** the brand slogan "Roam Freely. Forget Nothing." and the "macaco"
wordmark are intentionally identical in every locale (`app_tagline` in values-de confirms) —
leave the hardcoded occurrences of those alone.

---

## Change 1 — New string keys

Add to `res/values/strings.xml` and translate for the other 10 locales:

| Key | EN value |
|-----|----------|
| `map_adventures_title` | Adventures |
| `map_locations_mapped` | %1$d of %2$d locations mapped |
| `map_no_locations_title` | No locations yet |
| `map_no_locations_subtitle` | Add a location to your journal entries\nto see them on the map. |
| `map_marker_snippet_one` | 1 memory · tap to open |
| `map_marker_snippet_many` | %1$d memories · tap to open |
| `profile_member_since` | Member since %1$s |
| `drawer_not_signed_in` | Not signed in |
| `purchase_footer_no_fees` | No hidden fees. Cancel anytime. |
| `entry_share_chooser` | Share your memory |
| `entry_share_caption_copied` | Caption copied — paste it into the photo caption |
| `entry_share_credit` | — shared from Macaco |
| `new_entry_trip_label` | Trip |
| `new_entry_trip_placeholder` | e.g. Thailand 2026 |
| `new_entry_trip_clear_cd` | Clear trip |
| `reminder_action_add` | + Add Memory |
| `reminder_action_snooze` | Remind me later |
| `reminder_channel_name` | Travel reminders |
| `reminder_channel_desc` | Periodic nudges to log a new travel memory |
| `reminder_copy_location_title` | Still thinking about %1$s? 🌏 |
| `reminder_copy_location_body` | Roam Freely. Forget Nothing. Log today. |
| `reminder_copy_days_title` | Your last memory was %1$d days ago… |
| `reminder_copy_days_body` | Don't let today become a blur. What's your story? |
| `reminder_copy_count_title` | %1$d memories and counting 📖 |
| `reminder_copy_count_body` | You've been somewhere great. Macaco's waiting. |
| `reminder_copy_new_title` | Roam Freely. Forget Nothing. 🐒 |
| `reminder_copy_new_body` | Where did Macaco take you today? |

(For `map_marker_snippet_many` and `reminder_copy_days_title`, plural-sensitive languages may
prefer `<plurals>` — Code's call; the two-key form matches the existing snippet logic.)

---

## Change 2 — MapScreen.kt

Replace at the noted lines (both portrait and the landscape ` · `-joined variants):

```kotlin
// BEFORE (~line 445, landscape)                     // AFTER
" · Adventures"                                       " · " + stringResource(R.string.map_adventures_title)
// BEFORE (~line 453, landscape)
" · $mappedCount/${locations.size} mapped"            " · " + stringResource(R.string.map_locations_mapped, mappedCount, locations.size)
// BEFORE (~line 490, portrait)
"Adventures"                                          stringResource(R.string.map_adventures_title)
// BEFORE (~line 500, portrait)
"$mappedCount of ${locations.size} locations mapped"  stringResource(R.string.map_locations_mapped, mappedCount, locations.size)
// BEFORE (~line 572/578, empty state)
"No locations yet"                                    stringResource(R.string.map_no_locations_title)
"Add a location to your journal entries\n…"           stringResource(R.string.map_no_locations_subtitle)
```

Marker snippet (~line 548) is inside the non-composable `GoogleMap` content lambda? It is
composable — but simpler and safe: resolve via `context`:

```kotlin
// BEFORE
snippet = if (count == 1) "1 memory · tap to open" else "$count memories · tap to open",
// AFTER
snippet = if (count == 1) context.getString(R.string.map_marker_snippet_one)
          else context.getString(R.string.map_marker_snippet_many, count),
```

---

## Change 3 — ProfileScreen.kt (two occurrences, ~line 513 and ~line 869)

```kotlin
// BEFORE
text = "Member since $memberSince",
// AFTER
text = stringResource(R.string.profile_member_since, memberSince),
```

---

## Change 4 — JournalListScreen.kt (~line 368)

```kotlin
// BEFORE
text = if (currentUser != null) currentUser!!.displayName else "Not signed in",
// AFTER
text = currentUser?.displayName ?: stringResource(R.string.drawer_not_signed_in),
```

---

## Change 5 — PurchaseScreen.kt (~line 283)

```kotlin
// BEFORE
"No hidden fees. Cancel anytime.",
// AFTER
stringResource(R.string.purchase_footer_no_fees),
```

---

## Change 6 — EntryDetailScreen.kt, shareEntry (~lines 1103–1160)

`shareEntry` is a plain function with a `Context` — use `context.getString`:

```kotlin
// BEFORE
append("— shared from Macaco")
// AFTER
append(context.getString(R.string.entry_share_credit))

// BEFORE
Toast.makeText(context, "Caption copied — paste it into the photo caption", Toast.LENGTH_LONG).show()
// AFTER
Toast.makeText(context, context.getString(R.string.entry_share_caption_copied), Toast.LENGTH_LONG).show()

// BEFORE
context.startActivity(Intent.createChooser(intent, "Share your memory"))
// AFTER
context.startActivity(Intent.createChooser(intent, context.getString(R.string.entry_share_chooser)))
```

(The internal ClipData labels "Macaco photo(s)"/"Macaco entry" are not user-visible — leave.)

---

## Change 7 — NewEditEntryScreen.kt, TripField (~lines 876–884)

```kotlin
// BEFORE
label = { Text("Trip") },
placeholder = { Text("e.g. Thailand 2026") },
…
Icon(Icons.Filled.Close, contentDescription = "Clear trip")
// AFTER
label = { Text(stringResource(R.string.new_entry_trip_label)) },
placeholder = { Text(stringResource(R.string.new_entry_trip_placeholder)) },
…
Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.new_entry_trip_clear_cd))
```

---

## Change 8 — ReminderWorker.kt / ReminderScheduler.kt

```kotlin
// BEFORE (ReminderWorker, notification actions)
.addAction(R.drawable.ic_add, "+ Add Memory", newEntryPending)
.addAction(R.drawable.ic_snooze, "Remind me later", snoozePending)
// AFTER
.addAction(R.drawable.ic_add, ctx.getString(R.string.reminder_action_add), newEntryPending)
.addAction(R.drawable.ic_snooze, ctx.getString(R.string.reminder_action_snooze), snoozePending)

// BEFORE (ReminderScheduler.ensureChannel)
NotificationChannel(CHANNEL_ID, "Travel reminders", NotificationManager.IMPORTANCE_DEFAULT)
    .apply { description = "Periodic nudges to log a new travel memory" }
// AFTER
NotificationChannel(
    CHANNEL_ID,
    context.getString(R.string.reminder_channel_name),
    NotificationManager.IMPORTANCE_DEFAULT
).apply { description = context.getString(R.string.reminder_channel_desc) }
```

---

## Change 9 — NotificationCopy.kt: take a Context, return string resources

**Problem:** All four copy variants are hardcoded English.

**Fix:** Change the signature to take `Context` and resolve resources; update the one call
site in `ReminderWorker`.

```kotlin
// AFTER (whole function body pattern)
fun buildNotificationCopy(
    context: Context,
    entryCount: Int,
    daysSinceLast: Int?,
    lastLocation: String?
): Pair<String, String> {
    if (lastLocation != null) {
        return Pair(
            context.getString(R.string.reminder_copy_location_title, lastLocation),
            context.getString(R.string.reminder_copy_location_body)
        )
    }
    if (daysSinceLast != null && daysSinceLast >= 3) {
        return Pair(
            context.getString(R.string.reminder_copy_days_title, daysSinceLast),
            context.getString(R.string.reminder_copy_days_body)
        )
    }
    if (entryCount > 0) {
        return Pair(
            context.getString(R.string.reminder_copy_count_title, entryCount),
            context.getString(R.string.reminder_copy_count_body)
        )
    }
    return Pair(
        context.getString(R.string.reminder_copy_new_title),
        context.getString(R.string.reminder_copy_new_body)
    )
}
```

Call site in `ReminderWorker.doWork`:

```kotlin
// BEFORE
val (title, body) = buildNotificationCopy(
    entryCount = entries.size,
// AFTER
val (title, body) = buildNotificationCopy(
    context = ctx,
    entryCount = entries.size,
```

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | 27 new keys ×11 languages | `res/values*/strings.xml` |
| 2 | Map title, mapped-count, empty state, marker snippets | `MapScreen.kt` |
| 3 | "Member since" ×2 | `ProfileScreen.kt` |
| 4 | "Not signed in" | `JournalListScreen.kt` |
| 5 | Paywall footer | `PurchaseScreen.kt` |
| 6 | Share chooser/caption-toast/credit | `EntryDetailScreen.kt` |
| 7 | Trip field label/placeholder/clear | `NewEditEntryScreen.kt` |
| 8 | Notification actions + channel | `ReminderWorker.kt`, `ReminderScheduler.kt` |
| 9 | Context-based copy builder | `NotificationCopy.kt` |
