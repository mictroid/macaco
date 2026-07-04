# Macaco — First-Run Funnel: Short Splash + Paywall After 3 Free Entries

Approved UX priority #1. Two changes: the cold-start splash drops from ~5 s to ~1.5 s and
becomes tap-to-skip; the hard pre-app paywall becomes a soft gate — users can write 3 entries
free, the paywall appears when they try to create a 4th (premium features stay gated as
today). Touches `SplashScreen.kt`, `NavGraph.kt`, `Screen.kt`, `PurchaseScreen.kt`,
`strings.xml` ×11.

**Conflicts with pending briefs:** none — no pending video brief touches these files
(`video-permissions` touches `MainActivity`, not `NavGraph`).

---

## Change 1 — Splash: ≤1.5 s and tap-to-skip

**Problem:** LOGO_FADE 850 + delay 350 + TEXT_FADE 750 + HOLD 2300 + FADE_OUT 750 ≈ 5 s on
*every* cold start, not skippable. People open a journal to capture a moment.

**Fix:** Compress the timeline (~1.45 s total) and finish immediately on tap.

```kotlin
// BEFORE (SplashScreen.kt, ~line 50)
private const val LOGO_FADE_IN_MS = 850
private const val TEXT_FADE_IN_MS = 750
private const val TEXT_FADE_IN_DELAY_MS = 350L   // wordmark/tagline trail the logo
private const val HOLD_MS = 2300L                // fully-revealed dwell time (~5s total splash)
private const val FADE_OUT_MS = 750              // dissolve into the app

// AFTER
private const val LOGO_FADE_IN_MS = 450
private const val TEXT_FADE_IN_MS = 350
private const val TEXT_FADE_IN_DELAY_MS = 100L   // wordmark/tagline trail the logo
private const val HOLD_MS = 400L                 // fully-revealed dwell (~1.5s total splash)
private const val FADE_OUT_MS = 300              // dissolve into the app
```

Tap-to-skip — the root Box gets a no-ripple click that ends the splash:

```kotlin
// BEFORE (~line 83)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(macacoBrandBackground()),
        contentAlignment = Alignment.Center
    ) {

// AFTER
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(macacoBrandBackground())
            // Tap anywhere skips straight into the app (no ripple — it's a full-screen surface).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { currentOnFinished() },
        contentAlignment = Alignment.Center
    ) {
```

Imports: `androidx.compose.foundation.clickable`,
`androidx.compose.foundation.interaction.MutableInteractionSource`. Calling
`currentOnFinished()` twice (tap + timeline end) is harmless — it just sets
`showSplash = false` — but Code may guard with a local `finished` flag if preferred.

**File:** `SplashScreen.kt`

---

## Change 2 — Paywall becomes a dismissible route

**Problem:** `PurchaseScreen` is a pre-NavHost gate with no exit; users must pay before
writing anything.

**Fix:** Add a `Paywall` route + `onBack` support so PurchaseScreen works both as a
destination (dismissible) and — during rollout — anywhere else it's reused.

```kotlin
// Screen.kt — add:
    object Paywall : Screen("paywall")
```

```kotlin
// PurchaseScreen.kt — BEFORE (~line 64)
@Composable
fun PurchaseScreen(viewModel: JournalViewModel) {

// AFTER
@Composable
fun PurchaseScreen(
    viewModel: JournalViewModel,
    onBack: (() -> Unit)? = null,   // null = not dismissible (kept for any gated usage)
    showFreeLimitNote: Boolean = false
) {
```

Inside the top-level `Box` (after the header background Box, so it draws above it), add a
close button and the context line:

```kotlin
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = Color.White
                )
            }
        }
```

And under the existing tagline Text (`purchase_tagline`):

```kotlin
            if (showFreeLimitNote) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.purchase_free_limit_reached),
                    style = MaterialTheme.typography.bodySmall,
                    color = SplashGoldBright,
                    textAlign = TextAlign.Center
                )
            }
```

Imports: `Icons.Filled.Close`, `IconButton`, `statusBarsPadding` as needed.

**Files:** `Screen.kt`, `PurchaseScreen.kt`

---

## Change 3 — NavGraph: remove the hard gate, enforce the free limit at creation

**Problem/Fix:** Drop `isPurchased == false → PurchaseScreen`; signed-in users land in the
journal. Creating an entry beyond the free limit routes to the paywall instead. Editing
existing entries stays free. The app-lock condition loses its purchase requirement (free users
deserve the lock too).

```kotlin
// BEFORE (~line 135)
    if (appLockEnabled && isAppLocked && currentUser != null && isPurchased == true) {

// AFTER
    if (appLockEnabled && isAppLocked && currentUser != null) {
```

```kotlin
// BEFORE (~line 159)
        // Logged in but not yet purchased
        isPurchased == false -> {
            PurchaseScreen(viewModel = viewModel)
        }

        // Logged in and purchased — full journal
        else -> {

// AFTER
        // Logged in — full journal. Premium is enforced per-feature (backup, reel) and at
        // entry creation beyond the free limit (see goToNewEntry), not as an app-wide wall.
        else -> {
```

Add the free-limit helper inside the `else` branch (near `reopenDrawer`), and route every
new-entry trigger through it:

```kotlin
            val entryCount = viewModel.entries.collectAsState().value.size
            // isPurchased == false is the only state that gates; null (still loading) lets the
            // user through — worst case one extra free entry, never a wrongly-blocked premium user.
            val goToNewEntry: () -> Unit = {
                if (isPurchased == false && entryCount >= FREE_ENTRY_LIMIT) {
                    navController.navigate(Screen.Paywall.route)
                } else {
                    navController.navigate(Screen.NewEntry.route)
                }
            }
```

- `onNewEntry = { navController.navigate(Screen.NewEntry.route) }` (JournalList composable) →
  `onNewEntry = goToNewEntry`
- The reminder deep link (`LaunchedEffect(openNewEntry)`, ~line 171):
  `navController.navigate(Screen.NewEntry.route)` → `goToNewEntry()`

New route alongside the other composables:

```kotlin
                composable(Screen.Paywall.route) {
                    PurchaseScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        showFreeLimitNote = true
                    )
                }
```

File-level constant next to `LOCK_TIMEOUT_MS`:

```kotlin
// Entries a signed-in user can create before the paywall appears at the next creation.
private const val FREE_ENTRY_LIMIT = 3
```

Note: after a successful purchase on the Paywall route the entitlement flips and the screen
stays — add `LaunchedEffect(isPurchased)` inside the Paywall composable:
`if (isPurchased == true) navController.popBackStack()`.

**File:** `NavGraph.kt`

---

## Change 4 — Strings (×11)

| Key | EN value |
|-----|----------|
| `purchase_free_limit_reached` | You've used your 3 free memories — go premium for unlimited |

---

## Scope notes

- Login stays required (the store is cloud-only; guest mode is a separate, larger decision).
- Premium features keep their existing gates untouched: reel (TripHeader `isPurchased`),
  backup/restore (Settings), and the free user now sees them locked — that's intentional
  exposure, not a bug.
- SubscriptionInfoScreen and Settings "Restore purchase" already work for free users.
- QA test matrix: fresh account → 3 entries free → 4th routes to paywall → dismiss → still 3
  entries; purchase on paywall → auto-pops to NewEntry path; reminder deep link at the limit
  routes to paywall; app lock engages for free users.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Splash ~1.5 s + tap-to-skip | `SplashScreen.kt` |
| 2 | Paywall route: dismissible PurchaseScreen + free-limit note | `Screen.kt`, `PurchaseScreen.kt` |
| 3 | Hard gate removed; `FREE_ENTRY_LIMIT = 3` enforced at creation; lock condition updated | `NavGraph.kt` |
| 4 | 1 new key ×11 | `res/values*/strings.xml` |
