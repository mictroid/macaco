# Macaco — JournalListScreen: Drawer Landscape Sign Out Cut Off (v2)

The landscape drawer still cuts off Sign Out (and Help & About) even after the v1 spacer fix.
Root cause: total content height exceeds the A53's ~443dp landscape screen height. Fix: hide
the two lowest-priority items (Share App and Rate Us) in landscape so Sign Out is always visible.
Touches `JournalListScreen.kt` only.

Read `docs/DONE/code-brief-drawer-landscape.md` for the v1 fix (spacer → 8dp fixed).

---

## Why it's still cut off

After v1, the landscape spacer is a fixed 8dp. But the total drawer content height is still
~456dp — about 13dp taller than the A53's ~443dp landscape height. The five menu items
(Settings, Dark Mode, Share App, Rate Us, Help) each take ~52dp, plus the compact header
(~52dp), dividers, and spacers fill the rest.

Hiding Share App and Rate Us in landscape removes ~104dp, dropping total content to ~352dp —
comfortably within 443dp with no scrolling needed.

Share App and Rate Us are secondary discovery actions; the essential drawer items are Settings,
Dark Mode, Help & About, and Sign Out. Users can find Share and Rate inside Help & About.

---

## Fix — Guard Share App and Rate Us with `!drawerIsLandscape`

```kotlin
// BEFORE (JournalListScreen.kt, ~line 421 and ~line 432)
NavigationDrawerItem(
    label = { Text(stringResource(R.string.drawer_share_app)) },
    selected = false,
    colors = drawerItemColors,
    icon = { Icon(Icons.Filled.Share, contentDescription = null) },
    onClick = {
        scope.launch { drawerState.close() }
        AppActions.shareApp(context, entries.size)
    }
)

NavigationDrawerItem(
    label = { Text(stringResource(R.string.drawer_rate_us)) },
    selected = false,
    colors = drawerItemColors,
    icon = { Icon(Icons.Filled.StarRate, contentDescription = null) },
    onClick = {
        scope.launch { drawerState.close() }
        AppActions.requestReview(context)
    }
)

// AFTER — wrap both in !drawerIsLandscape guards
if (!drawerIsLandscape) {
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.drawer_share_app)) },
        selected = false,
        colors = drawerItemColors,
        icon = { Icon(Icons.Filled.Share, contentDescription = null) },
        onClick = {
            scope.launch { drawerState.close() }
            AppActions.shareApp(context, entries.size)
        }
    )

    NavigationDrawerItem(
        label = { Text(stringResource(R.string.drawer_rate_us)) },
        selected = false,
        colors = drawerItemColors,
        icon = { Icon(Icons.Filled.StarRate, contentDescription = null) },
        onClick = {
            scope.launch { drawerState.close() }
            AppActions.requestReview(context)
        }
    )
}
```

Portrait drawer is unchanged — all items remain visible there.

No new imports needed.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Hide Share App and Rate Us drawer items when `drawerIsLandscape` | `JournalListScreen.kt` |
