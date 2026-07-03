# Macaco — SettingsScreen: Revert to Stacked Layout + Reduce Section Spacing

The landscape side-by-side Theme Color / Background Image layout added by `settings-landscape`
is unwanted. Revert to a single stacked column for all orientations, and reduce inter-section
spacing so the settings list is tighter. Touches `SettingsScreen.kt` only.

Read `docs/DONE/code-brief-settings-landscape.md` for the side-by-side layout this reverts.

---

## Change 1 — Remove the landscape side-by-side branch, always use stacked

**Problem:** The `if (isLandscape)` branch places Theme Color and Background Image in a `Row`
with `weight(1f)` columns. The user wants them back to being stacked vertically regardless of
orientation. The content column already has `verticalScroll`, so landscape users can scroll to
Background Image with no extra work.

**Fix:** Delete the `if (isLandscape) { Row(...) { ... } }` branch and keep only the `else`
body (the stacked layout), removing the condition entirely.

```kotlin
// BEFORE (SettingsScreen.kt, ~line 631–655)
if (isLandscape) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SettingsSectionHeader(stringResource(R.string.settings_theme_color))
            themeColorCard()
        }
        Column(modifier = Modifier.weight(1f)) {
            SettingsSectionHeader(stringResource(R.string.settings_background_image))
            backgroundImageCard()
        }
    }
} else {
    // ── Theme Color ───────────────────────────────────────────────
    Spacer(Modifier.height(4.dp))
    SettingsSectionHeader(stringResource(R.string.settings_theme_color))
    themeColorCard()

    // ── Background Image ──────────────────────────────────────────
    Spacer(Modifier.height(4.dp))
    SettingsSectionHeader(stringResource(R.string.settings_background_image))
    backgroundImageCard()
}

// AFTER — always stacked; no landscape branch
// ── Theme Color ───────────────────────────────────────────────
SettingsSectionHeader(stringResource(R.string.settings_theme_color))
themeColorCard()

// ── Background Image ──────────────────────────────────────────
SettingsSectionHeader(stringResource(R.string.settings_background_image))
backgroundImageCard()
```

The `Spacer(Modifier.height(4.dp))` between sections is removed because the outer Column's
`verticalArrangement` already provides spacing (see Change 2 below).

---

## Change 2 — Reduce outer Column vertical spacing

**Problem:** The settings content Column uses `Arrangement.spacedBy(8.dp)` and each section
has additional `Spacer(Modifier.height(4.dp))` before section headers, adding up to 12dp of
gap between sections. Also `padding(vertical = 8.dp)` on the column itself adds extra space
at top and bottom. The result is a loose layout with too much whitespace.

**Fix:** Reduce `spacedBy` from 8dp to 4dp and change `padding(vertical = 8.dp)` to
`padding(vertical = 4.dp)`. Remove the `Spacer(Modifier.height(4.dp))` blocks before section
headers that appear elsewhere in the Column (lines ~657, ~667 — before Map and Language
sections), since the spacedBy gap is now sufficient.

```kotlin
// BEFORE (SettingsScreen.kt, ~line 428–435)
Column(
    modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {

// AFTER
Column(
    modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
) {
```

Also remove the `Spacer(Modifier.height(4.dp))` spacers that appear before the Map section
header and Language section header in the Column body (typically around lines 657 and 667):

```kotlin
// REMOVE these two Spacers from within the Column body:
Spacer(Modifier.height(4.dp))  // before SettingsSectionHeader(settings_map)
Spacer(Modifier.height(4.dp))  // before SettingsSectionHeader(settings_language)
```

The `isLandscape` variable is still needed for the `themeColorCard` and `backgroundImageCard`
lambdas (they read it for internal padding decisions), so keep the `val isLandscape = ...` line.

No new imports needed.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove landscape side-by-side branch; always use stacked Theme Color + Background Image | `SettingsScreen.kt` |
| 2 | Outer Column: `spacedBy(8.dp)` → `spacedBy(4.dp)`, `padding(vertical=8.dp)` → `4.dp`; remove redundant height(4.dp) spacers before Map and Language headers | `SettingsScreen.kt` |
