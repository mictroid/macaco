# Macaco — Drawer Landscape: Restore Share App & Rate Us

The landscape drawer currently hides Share App and Rate Us with an `if (!drawerIsLandscape)` guard
(implemented in drawer-landscape-v2). This was the wrong fix. All drawer items must be visible in
landscape. Remove the guard and show all items. Compact the three spacers in landscape from 8 dp to
4 dp so the total content fits comfortably in the ~443 dp A53 landscape height.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change 1 — Remove the landscape guard around Share App and Rate Us (~line 421)

### BEFORE
```kotlin
// Share App + Rate Us are secondary discovery actions; hide them in landscape so
// the essential items (Settings, Dark Mode, Help, Sign Out) always fit the ~443dp
// landscape height. Both are still reachable from Help & About.
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

### AFTER
```kotlin
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
```

---

## Change 2 — Compact the top spacer in landscape (~line 382)

### BEFORE
```kotlin
Spacer(Modifier.height(8.dp))

NavigationDrawerItem(
    label = { Text(stringResource(R.string.common_settings)) },
```

### AFTER
```kotlin
Spacer(Modifier.height(if (drawerIsLandscape) 4.dp else 8.dp))

NavigationDrawerItem(
    label = { Text(stringResource(R.string.common_settings)) },
```

---

## Change 3 — Compact the spacer before the bottom divider (~line 461)

### BEFORE
```kotlin
if (drawerIsLandscape) {
    Spacer(Modifier.height(8.dp))
} else {
    Spacer(Modifier.weight(1f))
}
```

### AFTER
```kotlin
if (drawerIsLandscape) {
    Spacer(Modifier.height(4.dp))
} else {
    Spacer(Modifier.weight(1f))
}
```

---

## Change 4 — Compact the trailing spacer after Sign Out (~line 508)

### BEFORE
```kotlin
                )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    ) {
```

### AFTER
```kotlin
                )
                }

                Spacer(Modifier.height(if (drawerIsLandscape) 4.dp else 8.dp))
            }
        }
    ) {
```

---

## Why this fits

Landscape drawer content with all items and 4 dp spacers:
header(~52) + spacer(4) + Settings(48) + divider(9) + DarkMode(48) + ShareApp(48) + RateUs(48) +
HelpAbout(48) + spacer(4) + divider(9) + SignOut(48) + spacer(4) ≈ **380 dp** — well within the
~443 dp A53 landscape height. Portrait is unchanged (spacers stay 8 dp, weight spacer keeps
Sign Out pinned to the bottom).
