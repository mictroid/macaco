# Macaco — JournalListScreen: Bigger Collapsed-Header Brand, Same Height (v2, corrected)

The collapsed (scrolled / landscape) journal header shows the macaco icon at 22 dp with a
13 sp wordmark — undersized next to the Adventures (44 dp) and Profile (36 dp) headers. The
v1 brief bumped the sizes in place, but missed that the compact block stacks the icon ABOVE
the wordmark: growing both would make the collapsed bar ~14 dp taller, defeating the point of
collapsing. v2 correction: **lay the icon and wordmark side-by-side** — the brand gets a
32 dp icon and 16 sp wordmark while the bar gets *shorter*, not taller. (Also fixes the v1
line reference: the code is at ~line 234, not "~11503".)

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

```
Current (stacked, ~42dp content):        After (side-by-side, ~32dp content):

┌────────────────────────────────┐        ┌────────────────────────────────┐
│         [22dp icon]            │        │  [32dp] macaco · 8 memories    │
│   macaco · 8 memories (13sp)   │        │         (16sp / 4sp spacing)   │
└────────────────────────────────┘        └────────────────────────────────┘
```

---

## Change — Compact brand block: stacked Column → single Row, 32 dp / 16 sp (~lines 230–261)

**Problem:** Icon 22 dp + wordmark 13 sp is hard to read at a glance and visibly mismatched
against the other tabs' headers. Simply enlarging in place (v1) would grow the bar height —
the collapse exists to give that height back to content.

**Fix:** Replace the inner stacked `Column` with a horizontal `Row`; icon 32 dp with the
standard `-2.dp` optical offset (the adaptive icon's internal padding makes it sit low),
wordmark 16 sp / 4 sp letter-spacing. Bump the count/filter suffix from `labelSmall` to
`labelMedium` — it now sits beside a 16 sp wordmark, and this also honours the 12 sp floor
from the a11y pass. `animateContentSize()` on the header Box already animates the (now
smaller) collapse — no other changes.

### BEFORE
```kotlin
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "macaco",
                                        color = SplashGoldBright,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Light,
                                        letterSpacing = 3.sp
                                    )
                                    if (entries.isNotEmpty()) {
                                        val count = visibleEntries.size
                                        val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
                                        Text(
                                            text = " · " + memoriesText +
                                                if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SplashGold.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

### AFTER
```kotlin
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .offset(y = (-2).dp)   // optical: adaptive icon sits low
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "macaco",
                                    color = SplashGoldBright,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = 4.sp
                                )
                                if (entries.isNotEmpty()) {
                                    val count = visibleEntries.size
                                    val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
                                    Text(
                                        text = " · " + memoriesText +
                                            if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SplashGold.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
```

(`Spacer(Modifier.width(6.dp))` and `TextOverflow` may need imports:
`androidx.compose.ui.text.style.TextOverflow` is already imported in this file; `offset` is
already imported. The `maxLines`/`ellipsis` guard is new: on narrow landscape screens the
one-line brand + count must truncate rather than wrap under the 32 dp icon.)

---

## Scope notes

- Consistency context, for the record: Adventures (44 dp) and Profile (36 dp) are TALL
  headers; the collapsed journal bar is intentionally slimmer. 32 dp side-by-side reads as
  the same brand at a glance without matching their footprint.
- The expanded (scroll-top portrait) brand block is untouched.
- Verify the collapse/expand `animateContentSize` transition still looks right — the compact
  state is now shorter than before, so the animation travel increases slightly.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Compact brand block restructured to a single Row: icon 22→32 dp (side-by-side), wordmark 13→16 sp, spacing 3→4 sp, count `labelSmall`→`labelMedium` + ellipsis — net bar height decreases | `JournalListScreen.kt` |
