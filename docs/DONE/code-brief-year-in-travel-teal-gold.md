# Macaco — Year in Travel: Teal/Gold Split + Bigger Highlight Stats

Recolors `YearInTravelScreen.kt` so teal marks the year and the secondary numbers while gold is
reserved for the hero "Memories" stat, and upgrades the mood/tag/busiest-month line items from
plain stacked text into larger, centered highlight chips. One file touched:
`app/src/main/java/com/houseofmmminq/macaco/ui/screens/YearInTravelScreen.kt`.

---

## Change 1 — Teal for the year numeral and secondary stat numbers, gold stays on Memories

**Problem:** Every number on the screen — the big year numeral, and all four stat values
(Memories, Trips, Locations, Media) — currently uses the same fixed `SplashGold` constant
(`SplashScreen.kt:43`, `0xFFE8B020`). Nothing distinguishes the headline "Memories" stat from the
rest, and none of it echoes the teal half of the brand.

**Fix:** Use `MaterialTheme.colorScheme.primary` for the year numeral and for the three secondary
stats (Trips, Locations, Media). `primary` is already the app's teal token and is genuinely
theme-aware — `AppTheme.kt`'s default Macaco scheme resolves it to `0xFF0E5A6B` in light mode and
a brighter `0xFF5FD4E8` in dark mode, so it stays legible without any manual dark-mode branching.
Leave the "Memories" `StatItem` on `SplashGold` — it's the one number this screen wants to draw
the eye to first, and gold-on-navy already has plenty of contrast in dark mode (confirmed against
`AppTheme.kt`'s dark `background = 0xFF0B1A1F`).

```kotlin
// BEFORE — YearInTravelScreen.kt:118-125
Text(
    selectedYear.toString(),
    style = MaterialTheme.typography.displayMedium,
    fontWeight = FontWeight.Bold,
    color = SplashGold,
    modifier = Modifier.fillMaxWidth(),
    textAlign = TextAlign.Center
)

// AFTER
Text(
    selectedYear.toString(),
    style = MaterialTheme.typography.displayMedium,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.fillMaxWidth(),
    textAlign = TextAlign.Center
)
```

```kotlin
// BEFORE — YearInTravelScreen.kt:154-164
Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatItem(value = recap.entryCount.toString(), label = stringResource(R.string.profile_memories), valueColor = SplashGold)
        StatItem(value = recap.tripCount.toString(), label = stringResource(R.string.profile_trips), valueColor = SplashGold)
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatItem(value = recap.locationCount.toString(), label = stringResource(R.string.profile_locations), valueColor = SplashGold)
        StatItem(value = recap.mediaCount.toString(), label = stringResource(R.string.profile_media), valueColor = SplashGold)
    }
}

// AFTER — Memories stays gold (the hero stat), everything else goes teal
Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatItem(value = recap.entryCount.toString(), label = stringResource(R.string.profile_memories), valueColor = SplashGold)
        StatItem(value = recap.tripCount.toString(), label = stringResource(R.string.profile_trips), valueColor = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatItem(value = recap.locationCount.toString(), label = stringResource(R.string.profile_locations), valueColor = MaterialTheme.colorScheme.primary)
        StatItem(value = recap.mediaCount.toString(), label = stringResource(R.string.profile_media), valueColor = MaterialTheme.colorScheme.primary)
    }
}
```

```
┌─────────────────────────────┐
│   42 (gold)   12 (teal)     │   Memories · Trips
│                              │
│    8 (teal)   96 (teal)     │   Locations · Media
└─────────────────────────────┘
```

**File:** `YearInTravelScreen.kt`

---

## Change 2 — Mood / tag / busiest-month as larger, centered highlight chips

**Problem:** `topMood`, `topTag`, and `busiestMonth` (lines 167-193) render as three plain,
left-aligned `bodyLarge` `Text` lines stacked in the scroll column — no container, no visual
weight, easy to skim past even though these are the most personality-driven facts on the recap.

**Fix:** Replace the three stacked `Text`s with a small reusable `YearRecapHighlightChip` —
a pill (`Surface`, fully rounded, `primaryContainer` background) with an icon + `titleMedium`
bold label — laid out in a centered, wrapping row so it reads as a confident three-up highlight
strip instead of a footnote list. `primaryContainer`/`onPrimaryContainer` are theme tokens, so
the pills stay legible in dark mode and under any of the app's other themes without extra work.

```
BEFORE (left-aligned, stacked, bodyLarge)          AFTER (centered, titleMedium, pill chips)
Top mood: 😊 Happy                                 ┌────────────┐ ┌────────────┐ ┌─────────────┐
Most used tag: #beach                              │ 😊 Happy   │ │ 🏷 #beach  │ │ 📅 July     │
Busiest month: July                                └────────────┘ └────────────┘ └─────────────┘
                                                         (FlowRow, centered, wraps on narrow screens)
```

```kotlin
// BEFORE — YearInTravelScreen.kt:166-193
Spacer(Modifier.height(24.dp))
recap.topMood?.let {
    Text(
        stringResource(R.string.year_recap_top_mood, it),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = SplashGold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
recap.topTag?.let {
    Text(
        stringResource(R.string.year_recap_top_tag, it),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = SplashGold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
recap.busiestMonth?.let {
    Text(
        stringResource(R.string.year_recap_busiest_month, it),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = SplashGold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

// AFTER
Spacer(Modifier.height(24.dp))
FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    recap.topMood?.let {
        YearRecapHighlightChip(
            icon = Icons.Filled.Mood,
            text = stringResource(R.string.year_recap_top_mood, it),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
    recap.topTag?.let {
        YearRecapHighlightChip(
            icon = Icons.Filled.Sell,
            text = stringResource(R.string.year_recap_top_tag, it),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
    recap.busiestMonth?.let {
        YearRecapHighlightChip(
            icon = Icons.Filled.CalendarMonth,
            text = stringResource(R.string.year_recap_busiest_month, it),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
```

New private composable (add near the bottom of the file, alongside the existing `StatItem`
usage — `StatItem` itself lives in `ProfileScreen.kt` and is untouched):

```kotlin
@Composable
private fun YearRecapHighlightChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
```

New imports needed:

```kotlin
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.CalendarMonth
```

`FlowRow` is `@ExperimentalLayoutApi` — add `@OptIn(ExperimentalLayoutApi::class)` to
`YearInTravelScreen` (it's already `@OptIn(ExperimentalMaterial3Api::class)` at line 58; extend
that annotation rather than adding a second one).

**File:** `YearInTravelScreen.kt`

---

## Considered and deprioritized: full teal background

Michael also floated converting the whole screen background to the Macaco teal gradient
(matching the splash/lock-screen treatment) instead of just the header band. Recommend **not**
doing this for now: the stat card and new highlight chips lean on `surface`/`primaryContainer`
tokens for contrast, and a full teal backdrop — especially in dark mode, where the theme's dark
scheme is already a near-black teal-adjacent navy — would flatten that contrast and fight the
per-theme background instead of complementing it. The teal header band + teal numbers already
deliver the "more teal" ask without that risk. Leaving this out of scope; revisit only if the
recolor above ships and still feels like it needs more impact.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Year numeral + Trips/Locations/Media stats recolored to `colorScheme.primary` (teal); Memories stays `SplashGold` | `YearInTravelScreen.kt` |
| 2 | Mood/tag/busiest-month converted from stacked left-aligned text to centered `titleMedium` pill chips (`YearRecapHighlightChip`, new private composable) | `YearInTravelScreen.kt` |
| — | Full-teal background considered, deprioritized (see note above) | — |
