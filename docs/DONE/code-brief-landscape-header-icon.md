# Macaco — Landscape Headers: Icon Above "macaco" Wordmark

Three landscape compact headers currently place the monkey icon to the LEFT of "macaco" in a
Row. All three should show the icon ABOVE the text (a Column). Touches `JournalListScreen.kt`,
`ProfileScreen.kt`, and `SettingsScreen.kt`.

---

## Fix 1 — JournalListScreen landscape header

**Problem:** The landscape compact header center block is a `Row` with a 20dp icon left of the
"macaco" text. User wants icon above text.

**Fix:** Replace the center `Row` with a `Column` (icon on top, then a `Row` for the wordmark
+ memory count below it). Remove the `offset(y = (-2).dp)` that was only needed for vertical
alignment in a Row.

```
BEFORE               AFTER
[🐒] macaco · N      [🐒]
                     macaco · N
```

```kotlin
// BEFORE — inside the center Box(Modifier.weight(1f), Alignment.Center):
Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier
            .size(20.dp)
            .offset(y = (-2).dp)
    )
    Spacer(Modifier.width(6.dp))
    Text(
        text = "macaco",
        color = SplashGoldBright,
        fontSize = 14.sp,
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

// AFTER
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
```

File: `JournalListScreen.kt` — landscape compact header, inside the `Box(Modifier.weight(1f))`.

---

## Fix 2 — ProfileScreen landscape header

**Problem:** The landscape profile header center is a `Row(align=Center)` with a 20dp icon
beside "macaco". User wants icon above.

**Fix:** Replace the `Row` with a `Column`. Remove the `offset(y = (-2).dp)` and reduce
vertical padding slightly so the header height stays compact.

```kotlin
// BEFORE (ProfileScreen.kt, landscape header Box, ~line 336)
Row(
    modifier = Modifier
        .align(Alignment.Center)
        .padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(20.dp).offset(y = (-2).dp),
        colorFilter = ColorFilter.tint(SplashGoldBright)
    )
    Text(
        text = "macaco",
        color = SplashGoldBright,
        fontSize = 18.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 4.sp
    )
}

// AFTER
Column(
    modifier = Modifier
        .align(Alignment.Center)
        .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        colorFilter = ColorFilter.tint(SplashGoldBright)
    )
    Text(
        text = "macaco",
        color = SplashGoldBright,
        fontSize = 16.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 4.sp
    )
}
```

File: `ProfileScreen.kt` — landscape `Column` branch, inside the full-width header `Box`.

---

## Fix 3 — SettingsScreen landscape header

**Problem:** The landscape Settings header is a single `Row` with back button + small icon +
"macaco · Settings" inline. User wants icon above text. The current Row structure needs to
change to a Box (back button aligned start, brand column centered) — matching the Profile
landscape header pattern.

**Fix:** Replace the `if (isLandscape)` Row with a Box that aligns the back button to
`CenterStart` and places a Column (icon + text row) at `Center`.

```kotlin
// BEFORE (SettingsScreen.kt, ~line 348–386)
if (isLandscape) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Color.White
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .offset(y = (-2).dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "macaco",
            color = SplashGoldBright,
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 3.sp
        )
        Text(
            text = " · " + stringResource(R.string.common_settings),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

// AFTER
if (isLandscape) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .size(40.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Color.White
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "macaco",
                    color = SplashGoldBright,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 3.sp
                )
                Text(
                    text = " · " + stringResource(R.string.common_settings),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}
```

No new imports needed.

File: `SettingsScreen.kt` — landscape branch of the branded header `Box`.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Landscape center brand: Row(icon+text) → Column(icon above text) | `JournalListScreen.kt` |
| 2 | Landscape profile header center: Row → Column(icon above macaco) | `ProfileScreen.kt` |
| 3 | Landscape settings header: Row → Box with Column(icon above macaco·Settings) at center | `SettingsScreen.kt` |
