# Macaco — Landscape: Compact Header + Bottom Nav + Fix Adventures Title Row

On the A53 in landscape (~408dp height), the top header and bottom navigation bar each eat
significant vertical space. Two fixes:

1. **Bottom nav** — reduce height in landscape (48dp instead of the default ~80dp) and hide
   labels so icon-only shows; this reclaims ~32dp.
2. **Adventures header** — the landscape row puts icon + "macaco" + " · Adventures" + count all
   on one line, making it hard to read. Stack "macaco" on line 1 and "Adventures · count" on
   line 2 (a compact Column, like the portrait header but smaller).

The Journal header in landscape is already a single slim row and does not need changing.

---

## Change 1 — Compact bottom nav in landscape

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/navigation/NavGraph.kt`

### BEFORE
```kotlin
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
        NavigationBarItem(
            selected = currentRoute == Screen.JournalList.route,
            onClick = { navController.navigateToTab(Screen.JournalList.route) },
            colors = itemColors,
            icon = {
                Icon(
                    if (currentRoute == Screen.JournalList.route) Icons.Filled.AutoStories
                    else Icons.Outlined.AutoStories,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.nav_journal)) }
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Adventures.route,
            onClick = { navController.navigateToTab(Screen.Adventures.route) },
            colors = itemColors,
            icon = {
                Icon(
                    if (currentRoute == Screen.Adventures.route) Icons.Filled.Explore
                    else Icons.Outlined.Explore,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.drawer_adventures)) }
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Profile.route,
            onClick = { navController.navigateToTab(Screen.Profile.route) },
            colors = itemColors,
            icon = {
                Icon(
                    if (currentRoute == Screen.Profile.route) Icons.Filled.Person
                    else Icons.Outlined.Person,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.common_profile)) }
        )
    }
}
```

### AFTER
```kotlin
@Composable
private fun MacacoBottomNavBar(navController: NavController, currentRoute: String?) {
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480
    NavigationBar(
        containerColor = NavTeal,
        modifier = if (isLandscape) Modifier.height(48.dp) else Modifier
    ) {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = NavGoldBright,
            unselectedIconColor = NavGold.copy(alpha = 0.55f),
            selectedTextColor = NavGoldBright,
            unselectedTextColor = NavGold.copy(alpha = 0.55f),
            indicatorColor = NavGold.copy(alpha = 0.20f)
        )
        NavigationBarItem(
            selected = currentRoute == Screen.JournalList.route,
            onClick = { navController.navigateToTab(Screen.JournalList.route) },
            colors = itemColors,
            icon = {
                Icon(
                    if (currentRoute == Screen.JournalList.route) Icons.Filled.AutoStories
                    else Icons.Outlined.AutoStories,
                    contentDescription = null
                )
            },
            label = if (isLandscape) null else ({ Text(stringResource(R.string.nav_journal)) })
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Adventures.route,
            onClick = { navController.navigateToTab(Screen.Adventures.route) },
            colors = itemColors,
            icon = {
                Icon(
                    if (currentRoute == Screen.Adventures.route) Icons.Filled.Explore
                    else Icons.Outlined.Explore,
                    contentDescription = null
                )
            },
            label = if (isLandscape) null else ({ Text(stringResource(R.string.drawer_adventures)) })
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Profile.route,
            onClick = { navController.navigateToTab(Screen.Profile.route) },
            colors = itemColors,
            icon = {
                Icon(
                    if (currentRoute == Screen.Profile.route) Icons.Filled.Person
                    else Icons.Outlined.Person,
                    contentDescription = null
                )
            },
            label = if (isLandscape) null else ({ Text(stringResource(R.string.common_profile)) })
        )
    }
}
```

Also add this import at the top of NavGraph.kt:
```kotlin
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalConfiguration
```

---

## Change 2 — Adventures landscape header: two-line Column instead of one-line Row

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

The `isLandscape` branch of the map header (lines ~414–462) puts everything on one line.
Replace the single `Row` with a compact `Column` that puts "macaco" on line 1 and
"Adventures · count" on line 2.

### BEFORE
```kotlin
if (isLandscape) {
    // ── Compact landscape header: single slim row ──────────────────────
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
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
        Text(
            text = " · " + stringResource(R.string.map_adventures_title),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
        if (locations.isNotEmpty()) {
            val mappedCount = locations.count { it in geocodedLocations }
            Text(
                text = " · " + stringResource(R.string.map_locations_mapped, mappedCount, locations.size),
                color = SplashGold.copy(alpha = 0.70f),
                fontSize = 11.sp,
                fontFamily = MacacoFontFamily
            )
        }
        // Globe-spanning hint — compact, dot-separated, same line
        if (globeSpanning) {
            Text(
                text = " · " + stringResource(R.string.map_globe_spanning_hint),
                style = MaterialTheme.typography.labelSmall,
                color = SplashGold.copy(alpha = 0.75f),
                letterSpacing = 0.5.sp
            )
        }
    }
```

### AFTER
```kotlin
if (isLandscape) {
    // ── Compact landscape header: two-line Column (icon+wordmark / title+count) ──
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Line 1: icon + "macaco" wordmark
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .offset(y = (-1).dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "macaco",
                color = SplashGoldBright,
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp
            )
        }
        // Line 2: Adventures title + location count + globe hint
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.map_adventures_title),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            if (locations.isNotEmpty()) {
                val mappedCount = locations.count { it in geocodedLocations }
                Text(
                    text = " · " + stringResource(R.string.map_locations_mapped, mappedCount, locations.size),
                    color = SplashGold.copy(alpha = 0.70f),
                    fontSize = 11.sp,
                    fontFamily = MacacoFontFamily
                )
            }
            if (globeSpanning) {
                Text(
                    text = " · " + stringResource(R.string.map_globe_spanning_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = SplashGold.copy(alpha = 0.75f),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
```

No change to the `else` (portrait) branch.
