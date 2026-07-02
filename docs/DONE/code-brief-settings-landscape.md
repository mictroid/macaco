# Macaco — SettingsScreen: compact header + side-by-side theme/background in landscape

One file: `ui/screens/SettingsScreen.kt`.

Three fixes for landscape mode: (1) compact header, (2) Theme Color + Background Image
cards side-by-side instead of stacked, (3) smaller Background Image picker button when
no image is set.

---

## Fix 1 — Compact landscape header

**Problem:** The Settings header (lines ~341–386) always uses the full portrait Column:
44dp icon + "macaco" wordmark + "Settings" label ≈ 100dp tall. On A53 in landscape
(screen height ≈ 360dp), this header alone eats 28% of the screen, leaving the theme
section barely visible and pushing backup/reminders below the fold.

**Fix:** Detect landscape and replace the Column with a slim Row — the same pattern
used by MapScreen's landscape header.

```
Portrait header (unchanged in portrait):
┌────────────────────────────┐
│  ← [monkey 44dp]           │
│     macaco                 │  ~100dp tall
│     Settings               │
└────────────────────────────┘

Landscape header (new):
┌────────────────────────────┐
│  ←   🐒 macaco · Settings  │  ~40dp tall
└────────────────────────────┘
```

Add a landscape check and conditional header inside the branded Box. The existing
`Box` + `IconButton` + `Column` structure stays for portrait; in landscape, emit a
single slim `Row`:

```kotlin
// BEFORE (line ~343 in SettingsScreen.kt)
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
) {
    IconButton(
        onClick = onBack,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(4.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.common_back),
            tint = Color.White
        )
    }
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(44.dp).offset(y = 4.dp)
        )
        Text(
            text = "macaco",
            color = SplashGoldBright,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 5.sp
        )
        Text(
            stringResource(R.string.common_settings),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

// AFTER — landscape-aware header
val isLandscape = LocalConfiguration.current.screenHeightDp < 480

Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
) {
    if (isLandscape) {
        // ── Compact landscape: slim single row ───────────────────────────────
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
    } else {
        // ── Portrait: existing layout (unchanged) ──────────────────────────
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(4.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Color.White
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(44.dp).offset(y = 4.dp)
            )
            Text(
                text = "macaco",
                color = SplashGoldBright,
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 5.sp
            )
            Text(
                stringResource(R.string.common_settings),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
```

**Note:** `isLandscape` must be declared before the `Box` so it's in scope for the
header. The `LocalConfiguration` import is already present in SettingsScreen.

**File:** `ui/screens/SettingsScreen.kt`

---

## Fix 2 — Theme Color + Background Image side-by-side in landscape

**Problem:** In portrait the Theme Color and Background Image cards stack vertically —
combined height ≈ 180dp. In landscape this pushes all settings below Appearance (Drive,
reminders, backup, app lock) below the fold or off-screen entirely.

**Fix:** In landscape, place Theme Color and Background Image in a `Row` with
`weight(0.5f)` each. In portrait, keep the existing stacked layout unchanged.

```
Portrait (unchanged):
┌──────────────────────────┐
│  Theme Color             │  ~95dp
└──────────────────────────┘
┌──────────────────────────┐
│  Background Image        │  ~80–110dp
└──────────────────────────┘

Landscape (new):
┌─────────────────┬────────────────┐
│  Theme Color    │  Background    │  ~100dp
│                 │  Image         │
└─────────────────┴────────────────┘
```

Replace the Appearance section in the scrollable Column with a landscape-aware wrapper.
The `isLandscape` value from Fix 1 is already in scope:

```kotlin
// BEFORE (line ~408) — stacked in all orientations
// ── Theme Color ───────────────────────────────────────────────────
Spacer(Modifier.height(4.dp))
SettingsSectionHeader(stringResource(R.string.settings_theme_color))

Card(
    modifier = Modifier.fillMaxWidth(),
    ...
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AppTheme.entries.forEach { theme ->
                ThemeSwatch(theme = theme, selected = appTheme == theme, onClick = { viewModel.setAppTheme(theme) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(appTheme.displayName, ...)
    }
}

// ── Background Image ──────────────────────────────────────────────
Spacer(Modifier.height(4.dp))
SettingsSectionHeader(stringResource(R.string.settings_background_image))

Card(
    modifier = Modifier.fillMaxWidth(),
    ...
) {
    // ... picker content
}

// AFTER — side-by-side in landscape, stacked in portrait
if (isLandscape) {
    // Landscape: single row header + two cards side by side
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Theme Color card (left)
        Column(modifier = Modifier.weight(1f)) {
            SettingsSectionHeader(stringResource(R.string.settings_theme_color))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AppTheme.entries.forEach { theme ->
                            ThemeSwatch(
                                theme = theme,
                                selected = appTheme == theme,
                                onClick = { viewModel.setAppTheme(theme) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        appTheme.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Background Image card (right)
        Column(modifier = Modifier.weight(1f)) {
            SettingsSectionHeader(stringResource(R.string.settings_background_image))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                // Same content as portrait background card — see Fix 3 for button height
                Column(modifier = Modifier.padding(12.dp)) {
                    // existing thumbnail + picker button content (verbatim, with Fix 3 height)
                }
            }
        }
    }
} else {
    // Portrait: existing stacked layout — verbatim, no changes
    Spacer(Modifier.height(4.dp))
    SettingsSectionHeader(stringResource(R.string.settings_theme_color))
    Card(...) { /* existing theme card content */ }

    Spacer(Modifier.height(4.dp))
    SettingsSectionHeader(stringResource(R.string.settings_background_image))
    Card(...) { /* existing background card content */ }
}
```

**File:** `ui/screens/SettingsScreen.kt`

---

## Fix 3 — Reduce picker button height in landscape

**Problem:** When no background image is set, the "Pick background image" button
(lines ~512–548) is `height(80.dp)` — oversized relative to the other controls on the
same screen. In landscape this is particularly jarring since vertical space is scarce.

**Fix:** In landscape reduce the no-image state button height from 80dp to 44dp and
switch from the `Column` (icon + text stacked) to a single-line `Row` (icon + text inline).
The `themeImageUri != null` variant (44dp, text only) is already correct — no change there.

```kotlin
// BEFORE (line ~512 in the background card, no-image state)
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(if (themeImageUri == null) 80.dp else 44.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(pickerBg)
        .clickable { imagePicker.launch(...) },
    contentAlignment = Alignment.Center
) {
    if (themeImageUri == null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = pickerFg, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.settings_pick_bg), style = MaterialTheme.typography.labelLarge, color = pickerFg)
        }
    } else {
        Text(stringResource(R.string.settings_change_image), ...)
    }
}

// AFTER — compact in landscape when no image is set
val pickerHeight = when {
    themeImageUri != null -> 44.dp
    isLandscape -> 44.dp        // compact in landscape
    else -> 80.dp               // portrait: keep the generous tap target
}
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(pickerHeight)
        .clip(RoundedCornerShape(10.dp))
        .background(pickerBg)
        .clickable { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    contentAlignment = Alignment.Center
) {
    if (themeImageUri == null) {
        if (isLandscape) {
            // Compact inline row in landscape
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = pickerFg, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.settings_pick_bg), style = MaterialTheme.typography.labelMedium, color = pickerFg)
            }
        } else {
            // Portrait: existing icon-above-text layout
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = pickerFg, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.settings_pick_bg), style = MaterialTheme.typography.labelLarge, color = pickerFg)
            }
        }
    } else {
        Text(stringResource(R.string.settings_change_image), style = MaterialTheme.typography.labelLarge, color = pickerFg, fontWeight = FontWeight.Medium)
    }
}
```

**Note:** Fix 3 applies to BOTH the portrait Background Image card and the landscape
right-column Background Image card (Fix 2). The `isLandscape` check inside the picker
Box handles both contexts consistently.

**File:** `ui/screens/SettingsScreen.kt`

---

## Scope

- **In:** Compact landscape header; Theme Color + Background Image side-by-side in landscape;
  picker button height/layout in landscape.
- **Out:** Drive sync, reminders, app lock, backup — unchanged in both orientations.
- **Out:** Dark mode toggle — already compact, unchanged.
- **Out:** Portrait layout — completely unchanged for all fixes.
- **Out:** Tablet (Settings screen is full single-column on tablets; tablet landscape height
  > 600dp so `isLandscape` stays false — correct).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Landscape header: portrait Column → slim landscape Row (same pattern as MapScreen) | `SettingsScreen.kt` |
| 2 | Landscape appearance section: Theme Color + Background Image side-by-side in a Row | `SettingsScreen.kt` |
| 3 | Picker button: 80dp → 44dp + inline Row layout in landscape (no-image state) | `SettingsScreen.kt` |
