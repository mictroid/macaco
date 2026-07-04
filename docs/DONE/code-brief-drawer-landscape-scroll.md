# Macaco — Drawer: Landscape Overflow Scroll

All drawer items are visible in portrait, but in landscape (A53/S8) the last items —
Help & About and Sign Out — are cut off. The `code-brief-drawer-landscape-restore` reduced
spacers to 4 dp, but the total content is still ~442 dp vs ~408 dp available landscape height.

Fix: wrap all drawer items (after the conditional header) in a `Column` that applies
`verticalScroll` only in landscape. In portrait the `weight(1f)` spacer still pins
Sign Out to the bottom (scroll is NOT applied there, so weight works fine).

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change 1 — Add verticalScroll import (~line 9)

`rememberScrollState` is already imported; add `verticalScroll`.

```kotlin
// ADD:
import androidx.compose.foundation.verticalScroll
```

---

## Change 2 — Wrap drawer items in a conditional scroll Column (~line 386)

The header (landscape `Row` / portrait `Box`) stays outside the scrollable region so it
never scrolls away. Everything after the header goes inside a `Column` that is scrollable
in landscape only.

### BEFORE (items placed directly in ModalDrawerSheet body, starting after the header):
```kotlin
                Spacer(Modifier.height(if (drawerIsLandscape) 4.dp else 8.dp))

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.common_settings)) },
```

### AFTER:
```kotlin
                Column(
                    modifier = if (drawerIsLandscape)
                        Modifier.verticalScroll(rememberScrollState())
                    else
                        Modifier
                ) {
                    Spacer(Modifier.height(if (drawerIsLandscape) 4.dp else 8.dp))

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.common_settings)) },
```

Also close this new Column just before the `ModalDrawerSheet` closing brace. The final
`Spacer(Modifier.height(if (drawerIsLandscape) 4.dp else 8.dp))` at the bottom of the
drawer stays inside the new Column.

The `Spacer(Modifier.weight(1f))` in portrait remains inside this Column; since
`verticalScroll` is NOT applied in portrait, `weight` still works and Sign Out stays
pinned to the bottom.

---

## Why scroll, not smaller items

`NavigationDrawerItem` has a 56 dp minimum touch target (M3 spec). Reducing it further
requires overriding internal M3 padding — invasive and risks a11y regression. A
`verticalScroll` wrapper is one modifier line and degrades gracefully: in portrait the
column never scrolls, in landscape it lets the user reach Sign Out with a short swipe.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `import androidx.compose.foundation.verticalScroll` | `JournalListScreen.kt` |
| 2 | Wrap post-header drawer items in `Column(if landscape → verticalScroll)` | `JournalListScreen.kt` |
