# Macaco — Journal List: Retire the Drawer + Collapsing Header

Approved UX priorities #2 and #3, merged into ONE brief because both rewrite the same
`JournalListScreen` top-bar/drawer region (two separate briefs would hand Code conflicting
BEFORE snippets). The drawer is removed (its items move to Profile), and the tall brand header
collapses to the existing compact row once the list scrolls. Touches `JournalListScreen.kt`,
`NavGraph.kt`, `ProfileScreen.kt`.

**Why retire the drawer:** it predates the bottom nav and now only duplicates it — Profile is
reachable three ways, dark mode lives in drawer + Settings, Sign out in drawer + Profile. Its
remaining items (Settings, Share, Rate, Help) are utilities, not destinations, and the drawer
has already cost two landscape-layout fix briefs. One navigation model, less to test ×
7 themes × 11 locales × 2 orientations.

**Conflicts with pending briefs:** none of the pending video briefs touch these three files.
Implement `code-brief-first-run-funnel.md` FIRST if batching — it edits nearby NavGraph lines
(gate block + `onNewEntry`), and this brief's NavGraph snippets assume the funnel brief's
`goToNewEntry` is already in place (marked below).

---

## Change 1 — JournalListScreen: remove the drawer

**Fix:** Delete the `ModalNavigationDrawer` wrapper, drawer content, drawer state, and the
reopen-on-return plumbing; the Scaffold becomes the composable root. The hamburger IconButton
in both headers (portrait ~line 626, landscape compact row) is removed — the top-left is now
empty in portrait (the brand block is centered) and the landscape row loses its leading
button. The top-bar avatar stays (it's the quickest path to Profile from the top of the list).

Signature:

```kotlin
// BEFORE (~line 117)
fun JournalListScreen(
    viewModel: JournalViewModel,
    onNewEntry: () -> Unit,
    onEntryClick: (String) -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    onHelp: () -> Unit,
    // True when returning from a drawer-launched screen: reopen the menu so back lands on it.
    openDrawerOnEnter: Boolean = false,
    onDrawerConsumed: () -> Unit = {}
) {

// AFTER
fun JournalListScreen(
    viewModel: JournalViewModel,
    onNewEntry: () -> Unit,
    onEntryClick: (String) -> Unit,
    onProfile: () -> Unit
) {
```

Delete: `drawerState`/`rememberDrawerState`, the `LaunchedEffect(openDrawerOnEnter)` block,
the entire `ModalNavigationDrawer(drawerState = …, drawerContent = { … }) {` wrapper +
its closing brace (the Scaffold moves up one level), the drawer imports
(`ModalNavigationDrawer`, `ModalDrawerSheet`, `NavigationDrawerItem`,
`NavigationDrawerItemDefaults`, `DrawerValue`, `rememberDrawerState`, `Menu` icon, and the
now-unused `Logout`/`HelpOutline`/`StarRate`/`Settings`/`Person` icons if nothing else uses
them). `rememberCoroutineScope`/`scope` goes too if the drawer was its last user. `isDarkMode`
and `AppActions` usages in the drawer disappear with it (dark mode stays in Settings; Share/
Rate move to Profile in Change 3).

**File:** `JournalListScreen.kt`

---

## Change 2 — JournalListScreen: header collapses on scroll

**Problem:** The portrait brand block (48dp icon + wordmark + slogan + count) costs ~120dp on
a screen users open daily; the first card starts a third of the way down. The compact
single-row variant already exists for landscape.

**Fix:** Hoist a `LazyListState`, derive a `collapsed` flag, and render the existing compact
row whenever `isLandscape || collapsed`. Drop the slogan from this screen entirely (it stays
on splash/login/purchase — the persuasion surfaces).

Hoist the state (near the other top-level state, ~line 170):

```kotlin
    val listState = rememberLazyListState()
    // Collapse the tall brand header as soon as the list scrolls away from the top.
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24
        }
    }
```

Wire it into the list (~line 777): `LazyColumn(state = listState, modifier = …)`.

Header switch — the existing branch `if (isLandscape) { compact row } else { tall block }`
(~line 521 after drawer removal) becomes:

```kotlin
// BEFORE
                val isLandscape = LocalConfiguration.current.screenHeightDp < 480
                …
                  if (isLandscape) {
// AFTER — same compact row serves landscape AND scrolled-portrait; animate the swap.
                val isLandscape = LocalConfiguration.current.screenHeightDp < 480
                …
                  if (isLandscape || collapsed) {
```

Wrap the two header variants' container Box content in
`AnimatedContent(targetState = isLandscape || collapsed, label = "headerCollapse")` (or
`Modifier.animateContentSize()` on the header Box — Code's call, `animateContentSize` is the
smaller diff). In the compact row, remove the (now deleted) hamburger`IconButton` and keep the
40dp trailing avatar anchor so the centered brand cluster stays symmetric — replace the leading
IconButton with a matching 40dp `Spacer`/`Box`.

Slogan removal — delete the portrait header's slogan Text (~line 694):

```kotlin
// DELETE
                            Text(
                                text = "Roam Freely. Forget Nothing.",
                                color = SplashGold.copy(alpha = 0.82f),
                                fontSize = 9.sp,
                                …
                            )
```

While here: the memory-count Text below it renders at `labelSmall` — bump to
`MaterialTheme.typography.labelMedium` (≥12sp) per the a11y pass.

Imports: `androidx.compose.foundation.lazy.rememberLazyListState`,
`androidx.compose.runtime.derivedStateOf` (+ `AnimatedContent`/`animateContentSize` per
choice).

**File:** `JournalListScreen.kt`

---

## Change 3 — Profile: adopt the drawer's orphans

**Fix:** Below the stats card / "Member since" line and ABOVE the pinned Subscription button,
add a utility card with four rows: Settings, Help & About, Share Macaco, Rate us. Reuse the
existing drawer string keys — no new strings. `ProfileScreen` gains two callbacks.

```kotlin
// BEFORE (~line 90)
fun ProfileScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onLogin: () -> Unit,
    onSubscription: () -> Unit,
    onDeleteAccount: ((Result<Unit>) -> Unit) -> Unit
) {

// AFTER
fun ProfileScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onLogin: () -> Unit,
    onSubscription: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onDeleteAccount: ((Result<Unit>) -> Unit) -> Unit
) {
```

New composable at the bottom of the file, called from BOTH the portrait column (after the
"Member since" text) and the landscape right pane (above the Subscribe/Sign-out row):

```kotlin
/** Utility rows relocated from the retired navigation drawer. */
@Composable
private fun ProfileUtilityCard(
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    entryCount: Int
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            ProfileUtilityRow(Icons.Filled.Settings, stringResource(R.string.common_settings), onSettings)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ProfileUtilityRow(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.drawer_help), onHelp)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ProfileUtilityRow(Icons.Filled.Share, stringResource(R.string.drawer_share_app)) {
                AppActions.shareApp(context, entryCount)
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ProfileUtilityRow(Icons.Filled.StarRate, stringResource(R.string.drawer_rate_us)) {
                AppActions.requestReview(context)
            }
        }
    }
}

@Composable
private fun ProfileUtilityRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
```

Portrait call site (after `memberSince`, ~line 875):

```kotlin
                Spacer(Modifier.height(8.dp))
                ProfileUtilityCard(
                    onSettings = onSettings,
                    onHelp = onHelp,
                    entryCount = entries.size
                )
                Spacer(Modifier.height(8.dp))
```

Landscape: same call inside the right-pane Column, above the Subscribe/Sign-out row (space is
tighter there — acceptable, the pane scrolls… it doesn't: right pane is a fixed Box. Put the
card in the LEFT scrollable pane after the stats card instead).

Imports: `AppActions`, `Settings`/`Share`/`StarRate`/`HelpOutline` icons, `ImageVector`,
`HorizontalDivider`.

**File:** `ProfileScreen.kt`

---

## Change 4 — NavGraph: rewire

```kotlin
// BEFORE (JournalList composable, ~line 193 — assumes funnel brief already applied)
                    JournalListScreen(
                        viewModel = viewModel,
                        openDrawerOnEnter = reopenDrawer,
                        onDrawerConsumed = { reopenDrawer = false },
                        onNewEntry = goToNewEntry,
                        onEntryClick = { id ->
                            navController.navigate(Screen.EntryDetail.createRoute(id))
                        },
                        onProfile = { navController.navigateToTab(Screen.Profile.route) },
                        onSettings = { reopenDrawer = true; navController.navigate(Screen.Settings.route) },
                        onLogin = { navController.navigate(Screen.Login.route) },
                        onHelp = { reopenDrawer = true; navController.navigate(Screen.HelpAbout.route) }
                    )

// AFTER
                    JournalListScreen(
                        viewModel = viewModel,
                        onNewEntry = goToNewEntry,
                        onEntryClick = { id ->
                            navController.navigate(Screen.EntryDetail.createRoute(id))
                        },
                        onProfile = { navController.navigateToTab(Screen.Profile.route) }
                    )
```

Delete `var reopenDrawer by remember { mutableStateOf(false) }`. Profile composable gains:

```kotlin
                        onSettings = { navController.navigate(Screen.Settings.route) },
                        onHelp = { navController.navigate(Screen.HelpAbout.route) },
```

Routes `Screen.Settings` / `Screen.HelpAbout` / `Screen.Login` stay — Settings and Help are
now reached from Profile; the Login route remains used by ProfileScreen's signed-out state.

**File:** `NavGraph.kt`

---

## Scope notes

- Back-stack behaviour changes by design: back from Settings/Help now lands on Profile (their
  new launch point) instead of reopening a drawer.
- The drawer's "Sign in" row was dead code (the list is only reachable signed-in) — it goes
  away with the drawer, nothing to relocate.
- Keep `drawer_*` string keys (reused by the Profile rows); do not rename — ×11 churn for
  nothing.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove ModalNavigationDrawer + hamburger + reopen plumbing | `JournalListScreen.kt` |
| 2 | Hoisted list state; compact header when scrolled; slogan removed; count ≥12sp | `JournalListScreen.kt` |
| 3 | `ProfileUtilityCard` (Settings/Help/Share/Rate) in portrait + landscape | `ProfileScreen.kt` |
| 4 | Rewire params; drop `reopenDrawer`; Profile gains onSettings/onHelp | `NavGraph.kt` |
