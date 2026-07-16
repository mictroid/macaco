# Macaco — Subscription screen: stop showing "Premium — Active — Lifetime" to free users

Fixes a real bug (not a RevenueCat account/config issue — confirmed against the RevenueCat
dashboard, which correctly shows "No current entitlements" for the affected test accounts):
every signed-in user, premium or not, can reach `SubscriptionInfoScreen`, and that screen has no
"not premium" state at all — it unconditionally renders the Premium/Active band and defaults to
labelling the plan "Lifetime" whenever it can't match a real subscription. Touches
`ProfileScreen.kt`, `SubscriptionInfoScreen.kt`, `NavGraph.kt`, and `strings.xml` ×11.

**Note — this is unrelated to the free-trial-not-showing issue.** That's a Play Console
offer-eligibility / product-catalog question (see the separate "Monthly" package with two
products attached in RevenueCat). This brief only stops free accounts from being shown a false
"you have Premium" screen; it has no effect on whether the paywall itself shows trial pricing.

---

## Problem

Two bugs stack to produce the false-positive:

**1. The entry point isn't gated.** `ProfileScreen.kt`'s "Subscription" tile (two call sites —
wide tablet layout and phone layout) calls `onSubscription` unconditionally, unlike the "Print
Book" row in `SettingsScreen.kt` right above it, which correctly branches on `isPurchased`:

```kotlin
// SettingsScreen.kt — the pattern that already exists and works correctly
onClick = { if (isPurchased == true) onPrintBook() else onNavigateToPaywall() },
```

`ProfileScreen.kt` has no equivalent check, so a free account can open the Subscription screen at
all.

**2. The screen itself has no "not premium" branch.** `SubscriptionInfoScreen.kt` renders the
"Macaco Premium — ACTIVE" card unconditionally, and its plan-label `when` (lines 131–145) falls
through to `stringResource(R.string.subscription_lifetime)` as the `else` case whenever
`manageableSubscription` is false and `currentPlanId` doesn't match — which is precisely the state
a free account with zero entitlements is in. So instead of surfacing "no subscription," it defaults
to lying that one exists.

## Fix

Gate the tile the same way Print Book already does, and give `SubscriptionInfoScreen` a real
non-premium state that offers to upgrade instead of falling through to a fake "Lifetime" label.

```
Free account taps "Subscription":

BEFORE                                   AFTER
┌─────────────────────────┐              ┌─────────────────────────┐
│   macaco                │              │   macaco                │
│  Macaco Premium         │              │  No active subscription │
│     ACTIVE              │   ──────▶    │                          │
│  Lifetime access        │              │  [ Upgrade to Premium ] │
│  (false — no purchase)  │              │                          │
└─────────────────────────┘              └─────────────────────────┘
```

### 1. `ProfileScreen.kt` — gate the Subscription tile, add `onNavigateToPaywall`

```kotlin
// BEFORE
fun ProfileScreen(
    viewModel: JournalViewModel,
    onSignOut: () -> Unit,
    onLogin: () -> Unit,
    onSubscription: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onYearInTravel: () -> Unit,
    onDeleteAccount: (String?, (Result<Unit>) -> Unit) -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val profilePhotoUri by viewModel.profilePhotoUri.collectAsState()
```

```kotlin
// AFTER
fun ProfileScreen(
    viewModel: JournalViewModel,
    onSignOut: () -> Unit,
    onLogin: () -> Unit,
    onSubscription: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onYearInTravel: () -> Unit,
    onDeleteAccount: (String?, (Result<Unit>) -> Unit) -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val profilePhotoUri by viewModel.profilePhotoUri.collectAsState()
    // Gates the "Subscription" tile below — same isPurchased check SettingsScreen already uses
    // for the Print Book row. A free account is routed to the paywall instead of a screen that
    // has nothing real to show it.
    val isPurchased by viewModel.isPurchased.collectAsState()
```

Both tile call sites (wide layout and phone layout) change identically:

```kotlin
// BEFORE (appears twice — line ~594 wide layout, line ~627 phone layout)
ProfileActionTile(Icons.Outlined.WorkspacePremium, stringResource(R.string.common_subscription), onClick = onSubscription)
```

```kotlin
// AFTER (both call sites)
ProfileActionTile(
    Icons.Outlined.WorkspacePremium,
    stringResource(R.string.common_subscription),
    onClick = { if (isPurchased == true) onSubscription() else onNavigateToPaywall() }
)
```

### 2. `SubscriptionInfoScreen.kt` — real non-premium state instead of falling through to "Lifetime"

```kotlin
// BEFORE
@Composable
fun SubscriptionInfoScreen(viewModel: JournalViewModel, onBack: () -> Unit) {
    val currentPlanId by viewModel.currentPlanId.collectAsState()
    val context = LocalContext.current
```

```kotlin
// AFTER
@Composable
fun SubscriptionInfoScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onUpgrade: () -> Unit
) {
    val isPurchased by viewModel.isPurchased.collectAsState()
    val currentPlanId by viewModel.currentPlanId.collectAsState()
    val context = LocalContext.current
```

Add an early return inside the `Scaffold` content — before the existing premium band — for the
not-purchased case. This keeps the existing premium rendering exactly as-is for real subscribers,
and only replaces content when `isPurchased != true`:

```kotlin
// AFTER — inside the Scaffold's content lambda, right after Spacer(Modifier.height(24.dp))
// and before the existing "Column ... macacoBrandBackground()" premium band.
if (isPurchased != true) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(76.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.subscription_not_premium_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.subscription_not_premium_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onUpgrade,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.subscription_upgrade_cta))
    }
    Spacer(Modifier.height(32.dp))
    return@Scaffold
}

// Everything below (the macacoBrandBackground() premium band, "What's included" card, etc.)
// is unchanged and only renders when isPurchased == true.
```

Note: this requires importing `androidx.compose.material3.Button` (not currently imported in this
file — `OutlinedButton` already is) and the `return@Scaffold` needs the surrounding `Column`
structure kept intact; place the early-return block as the first content inside the scrollable
`Column`, before the existing premium-band `Column`.

### 3. `NavGraph.kt` — wire the new callbacks

```kotlin
// BEFORE
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        viewModel = viewModel,
                        // On sign-out currentUser becomes null, NavGraph auto-shows LoginScreen
                        onSignOut = { navController.popBackStack() },
                        onLogin = {
                            navController.popBackStack()
                            navController.navigate(Screen.Login.route)
                        },
                        onSubscription = { navController.navigate(Screen.Subscription.route) },
                        onSettings = { navController.navigate(Screen.Settings.route) },
                        onHelp = { navController.navigate(Screen.HelpAbout.route) },
```

```kotlin
// AFTER
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        viewModel = viewModel,
                        // On sign-out currentUser becomes null, NavGraph auto-shows LoginScreen
                        onSignOut = { navController.popBackStack() },
                        onLogin = {
                            navController.popBackStack()
                            navController.navigate(Screen.Login.route)
                        },
                        onSubscription = { navController.navigate(Screen.Subscription.route) },
                        onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) },
                        onSettings = { navController.navigate(Screen.Settings.route) },
                        onHelp = { navController.navigate(Screen.HelpAbout.route) },
```

```kotlin
// BEFORE
                composable(Screen.Subscription.route) {
                    SubscriptionInfoScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
```

```kotlin
// AFTER
                composable(Screen.Subscription.route) {
                    SubscriptionInfoScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onUpgrade = { navController.navigate(Screen.Paywall.route) }
                    )
                }
```

## Localization

New strings, all 11 supported languages:

| Key | EN value |
|-----|----------|
| `subscription_not_premium_title` | No active subscription |
| `subscription_not_premium_body` | Upgrade to Macaco Premium to unlock unlimited entries, cloud sync, and more. |
| `subscription_upgrade_cta` | Upgrade to Premium |

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Gate the "Subscription" tile (both layouts) behind `isPurchased`, add `onNavigateToPaywall` param | `ProfileScreen.kt` |
| 2 | Add a real non-premium state instead of defaulting to a false "Lifetime — Active" | `SubscriptionInfoScreen.kt` |
| 3 | Wire `onNavigateToPaywall` / `onUpgrade` callbacks to `Screen.Paywall.route` | `NavGraph.kt` |
| 4 | 3 new string keys × 11 locales | `strings.xml` |
