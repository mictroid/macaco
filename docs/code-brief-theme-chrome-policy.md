# Macaco — Theme Chrome Policy: Content Chrome Follows the Theme

Approved UX priority #5. Establishes and applies one rule: **brand-fixed colours are allowed
only on brand moments (splash, app lock, and the `macacoBrandBackground()` screen headers);
everything inside content follows the selected theme's tokens.** Today a user on a non-teal
theme gets a teal bottom nav and gold month headers under a differently-coloured app, while
EntryDetail's header follows the theme — the worst of both. Touches `NavGraph.kt`,
`JournalListScreen.kt`, `NewEditEntryScreen.kt`, `PurchaseScreen.kt`.

**Conflicts with pending briefs:**
- `code-brief-first-run-funnel.md` and `code-brief-journal-list-navigation.md` also edit
  `NavGraph.kt` / `JournalListScreen.kt` — different regions (gates/params vs. the bottom-nav
  composable and MonthHeader), but implement those two FIRST so this brief's line references
  hold.
- `code-brief-touch-targets-a11y.md` Change 4 touches `MoodChip`'s modifier chain; this brief
  touches its `background(...)` line — compatible, batch in either order.

---

## Change 1 — Bottom nav: theme tokens

**Problem:** `MacacoBottomNavBar` hardcodes `NavTeal`/`NavGold`/`NavGoldBright` — fixed
teal+gold under all 7 themes.

```kotlin
// BEFORE (NavGraph.kt, ~line 354)
private val NavTeal = Color(0xFF0E5A6B) // brand teal — same shade in light and dark mode
private val NavGold = Color(0xFFE8B020)
private val NavGoldBright = Color(0xFFF0C840)

@Composable
private fun MacacoBottomNavBar(navController: NavController, currentRoute: String?) {
    NavigationBar(containerColor = NavTeal) {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = NavGoldBright,
            unselectedIconColor = NavGold.copy(alpha = 0.55f),
            selectedTextColor = NavGoldBright,
            unselectedTextColor = NavGold.copy(alpha = 0.55f),
            indicatorColor = NavGold.copy(alpha = 0.20f)
        )

// AFTER — primary surface with secondary-family accents: on the default Macaco theme this
// still renders teal + amber, and on the other 6 themes it matches the user's choice.
@Composable
private fun MacacoBottomNavBar(navController: NavController, currentRoute: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.primary) {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.secondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f),
            selectedTextColor = MaterialTheme.colorScheme.secondaryContainer,
            unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f),
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.20f)
        )
```

Delete the three private `Nav*` vals (and the `Color` import if now unused in this file).
Verify on the default theme that `secondaryContainer` reads as the amber family against
`primary` — if a theme's pairing lacks contrast, fall back to `onPrimary` for the selected
state on that token pair (Code judges visually across the 7 themes).

**File:** `NavGraph.kt`

---

## Change 2 — Month headers: gold → secondary token

```kotlin
// BEFORE (JournalListScreen.MonthHeader, ~line 1412)
        Text(
            month.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = SplashGold
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = SplashGold.copy(alpha = 0.3f)
        )

// AFTER
        Text(
            month.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
        )
```

**File:** `JournalListScreen.kt`

---

## Change 3 — Mood chips + add-mood button: gold → secondaryContainer

```kotlin
// BEFORE (NewEditEntryScreen.MoodChip background, ~line 1084)
            .background(
                // Selected: Macaco gold — consistent with splash, nav bar, and brand moments.
                // Unselected: neutral surface so the emoji reads clearly at rest.
                if (selected) SplashGold else MaterialTheme.colorScheme.surfaceVariant
            )

// AFTER
            .background(
                // Selected: the theme's accent container (amber on the default theme).
                // Unselected: neutral surface so the emoji reads clearly at rest.
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
```

And the add-custom-mood tile (MoodSelector, ~line 1058):

```kotlin
// BEFORE
                    .background(SplashGold.copy(alpha = 0.18f))
                    .clickable { showAddDialog = true },
                …
                    tint = SplashGold,

// AFTER
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                    .clickable { showAddDialog = true },
                …
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
```

**File:** `NewEditEntryScreen.kt`

---

## Change 4 — Purchase restore link: token

The paywall itself is a brand moment (fixed dark-teal design) — its card colours stay. Only
the raw hex link colour goes:

```kotlin
// BEFORE (PurchaseScreen footer, ~line 295)
                    Text(
                        stringResource(R.string.purchase_restore),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5FD4E8),

// AFTER — bright-gold family, consistent with the other accents on this screen
                    Text(
                        stringResource(R.string.purchase_restore),
                        style = MaterialTheme.typography.bodySmall,
                        color = SplashGoldBright,
```

**File:** `PurchaseScreen.kt`

---

## Explicitly staying brand-fixed (do NOT change)

`macacoBrandBackground()` headers (list/map/settings/profile/login), SplashScreen, AppLock,
the paywall's dark plan cards, `Splash*` colour constants, and the reel encoder's pixel
Paints. These are the "brand moments" side of the policy.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Bottom nav → `primary`/`secondaryContainer` tokens; delete `Nav*` constants | `NavGraph.kt` |
| 2 | Month header gold → `secondary` | `JournalListScreen.kt` |
| 3 | Mood selected/add-tile gold → `secondaryContainer` family | `NewEditEntryScreen.kt` |
| 4 | Restore link hex → `SplashGoldBright` | `PurchaseScreen.kt` |
