# Macaco — Subscription Info: Renewal Date/Price + Annual Renewal Reminder

Adds a renewal date+price line to `SubscriptionInfoScreen` for both cadences, plus a one-time
local notification ~7 days before an **annual** subscriber's renewal (monthly subscribers do not
get a renewal notification — only the on-screen date/price). Touches `BillingManager.kt`,
`SubscriptionInfoScreen.kt`, `MainActivity.kt`, `NavGraph.kt`, `RevenueCatConfig.kt`, and adds two
new files (`RenewalReminderScheduler.kt`, `RenewalReminderWorker.kt`).

---

## 1. Expose the renewal date from BillingManager

**Problem:** `BillingManager.applyEntitlement()` receives `entitlement.expirationDate` on every
refresh/login/purchase callback but never stores or exposes it. `SubscriptionInfoScreen` has no
way to show a renewal date today.

**Fix:** Add a `StateFlow<Long?> currentExpirationDate` (epoch millis, null when not on an active
subscription) alongside the existing `_currentPlanId` / `_currentBasePlanId` pattern already in
the file.

```kotlin
// BEFORE (BillingManager.kt, lines 60-73)
    private val _currentPlanId = MutableStateFlow<String?>(null)
    val currentPlanId: StateFlow<String?> = _currentPlanId.asStateFlow()

    private val _manageableSubscription = MutableStateFlow(false)
    val manageableSubscription: StateFlow<Boolean> = _manageableSubscription.asStateFlow()

    private val _currentBasePlanId = MutableStateFlow<String?>(null)
    val currentBasePlanId: StateFlow<String?> = _currentBasePlanId.asStateFlow()
```

```kotlin
// AFTER — add a new StateFlow next to currentBasePlanId
    private val _currentPlanId = MutableStateFlow<String?>(null)
    val currentPlanId: StateFlow<String?> = _currentPlanId.asStateFlow()

    private val _manageableSubscription = MutableStateFlow(false)
    val manageableSubscription: StateFlow<Boolean> = _manageableSubscription.asStateFlow()

    private val _currentBasePlanId = MutableStateFlow<String?>(null)
    val currentBasePlanId: StateFlow<String?> = _currentBasePlanId.asStateFlow()

    // Epoch millis of the active entitlement's next renewal/expiry. Null when not on an active
    // subscription (lifetime purchases have no expiry either). Drives the "Renews on ..." line
    // on SubscriptionInfoScreen and the annual pre-renewal notification schedule.
    private val _currentExpirationDate = MutableStateFlow<Long?>(null)
    val currentExpirationDate: StateFlow<Long?> = _currentExpirationDate.asStateFlow()
```

Now update `applyEntitlement()` to set it, and to (re)schedule/cancel the annual renewal
notification whenever the entitlement changes:

```kotlin
// BEFORE (BillingManager.kt, lines 139-154)
    private fun applyEntitlement(entitlement: EntitlementInfo?) {
        val active = entitlement?.isActive == true
        _isPremium.value = active
        if (active && entitlement != null) {
            _currentPlanId.value = entitlement.productIdentifier
            _currentBasePlanId.value = entitlement.productPlanIdentifier
            // A Play subscription has an expiry (next renewal); a one-time lifetime purchase has
            // none, and promo/other-store grants can't be managed in Play. This is the reliable
            // way to gate "Manage subscription" — the product id can't tell monthly/annual apart.
            _manageableSubscription.value =
                entitlement.store == Store.PLAY_STORE && entitlement.expirationDate != null
        } else {
            _manageableSubscription.value = false
            _currentBasePlanId.value = null
        }
    }
```

```kotlin
// AFTER
    private fun applyEntitlement(entitlement: EntitlementInfo?) {
        val active = entitlement?.isActive == true
        _isPremium.value = active
        if (active && entitlement != null) {
            _currentPlanId.value = entitlement.productIdentifier
            _currentBasePlanId.value = entitlement.productPlanIdentifier
            _currentExpirationDate.value = entitlement.expirationDate?.time
            // A Play subscription has an expiry (next renewal); a one-time lifetime purchase has
            // none, and promo/other-store grants can't be managed in Play. This is the reliable
            // way to gate "Manage subscription" — the product id can't tell monthly/annual apart.
            _manageableSubscription.value =
                entitlement.store == Store.PLAY_STORE && entitlement.expirationDate != null

            // Only annual subscribers get a pre-renewal notification (monthly would be roughly
            // once a month — too noisy). Base plan id is the literal Play Console base plan id
            // ("annual" / "monthly"), see RevenueCatConfig.ANNUAL_BASE_PLAN_ID.
            val expiryMillis = entitlement.expirationDate?.time
            if (_manageableSubscription.value &&
                entitlement.productPlanIdentifier == RevenueCatConfig.ANNUAL_BASE_PLAN_ID &&
                expiryMillis != null
            ) {
                RenewalReminderScheduler.schedule(appContext, expiryMillis)
            } else {
                RenewalReminderScheduler.cancel(appContext)
            }
        } else {
            _manageableSubscription.value = false
            _currentBasePlanId.value = null
            _currentExpirationDate.value = null
            RenewalReminderScheduler.cancel(appContext)
        }
    }
```

`BillingManager`'s constructor parameter is currently named `appContext` (see line 39) but isn't
stored as a property — add `private val appContext = appContext` (or keep the constructor param
name and store it) so `applyEntitlement()` can reach it. Concretely, change the constructor line:

```kotlin
// BEFORE (BillingManager.kt, line 38-42)
class BillingManager(
    appContext: Context,
    private val preferencesManager: PreferencesManager,
    authRepository: AuthRepository
) {
```

```kotlin
// AFTER
class BillingManager(
    private val appContext: Context,
    private val preferencesManager: PreferencesManager,
    authRepository: AuthRepository
) {
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/data/billing/BillingManager.kt`

---

## 2. Add the annual base-plan id constant

**Problem:** No single source of truth for the literal Play Console base plan id string used to
detect "this is the annual subscription."

**Fix:**

```kotlin
// BEFORE (RevenueCatConfig.kt, lines 25-33)
object RevenueCatConfig {
    const val GOOGLE_API_KEY = "goog_yRpbApeYiaVGynRsivoAwHwmJMI"

    // Must match the entitlement identifier configured in the RevenueCat dashboard.
    const val ENTITLEMENT_ID = "premium"

    val isConfigured: Boolean
        get() = GOOGLE_API_KEY.startsWith("goog_") && !GOOGLE_API_KEY.contains("YOUR")
}
```

```kotlin
// AFTER
object RevenueCatConfig {
    const val GOOGLE_API_KEY = "goog_yRpbApeYiaVGynRsivoAwHwmJMI"

    // Must match the entitlement identifier configured in the RevenueCat dashboard.
    const val ENTITLEMENT_ID = "premium"

    // Literal Play Console base plan id for the annual cadence under the macaco_premium
    // subscription (see Play Console > Macaco Premium > Base plans and offers > "annual").
    // Used to gate the pre-renewal notification to annual subscribers only.
    const val ANNUAL_BASE_PLAN_ID = "annual"

    val isConfigured: Boolean
        get() = GOOGLE_API_KEY.startsWith("goog_") && !GOOGLE_API_KEY.contains("YOUR")
}
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/data/billing/RevenueCatConfig.kt`

---

## 3. Show "Renews on <date> — <price>" on SubscriptionInfoScreen

**Problem:** The plan card in `SubscriptionInfoScreen` shows the plan label ("Monthly annual"
etc.) but nothing about when or for how much it renews.

**Fix:** Add a new line directly under the existing `planLabel` `Text` in the brand-background
card. Reuse the module-`internal` `formatDate(millis: Long)` helper already defined in
`JournalListScreen.kt` (same module, so it's visible here without an import — it formats as
`"MMMM d, yyyy"`). Resolve the price by matching `currentBasePlanId` against the offering's
`monthly`/`annual` packages — the same matching already done a few lines above for `planLabel`.

```
┌──────────────────────────────┐
│           (icon)             │
│           macaco              │
│           PREMIUM             │
│           ACTIVE              │
│         Annual plan           │  ← existing planLabel
│  Renews July 14, 2027 — €29.99│  ← NEW
└──────────────────────────────┘
```

```kotlin
// BEFORE (SubscriptionInfoScreen.kt, lines 130-152)
                Spacer(Modifier.height(4.dp))
                val planLabel = when {
                    // Exact base-plan match first (one product, two base plans).
                    manageableSubscription && annualBasePlanId != null && currentBasePlanId == annualBasePlanId ->
                        stringResource(R.string.purchase_plan_annual)
                    manageableSubscription && monthlyBasePlanId != null && currentBasePlanId == monthlyBasePlanId ->
                        stringResource(R.string.purchase_plan_monthly)
                    // Fallbacks for a separate-product setup where the cadence is in the product id.
                    currentPlanId?.contains("annual") == true   -> stringResource(R.string.purchase_plan_annual)
                    currentPlanId?.contains("monthly") == true  -> stringResource(R.string.purchase_plan_monthly)
                    currentPlanId?.contains("lifetime") == true -> stringResource(R.string.purchase_plan_lifetime)
                    // A subscription whose cadence we couldn't resolve — don't mislabel it as
                    // "Lifetime"; show a neutral subscription label instead.
                    manageableSubscription -> stringResource(R.string.subscription_plan_recurring)
                    else -> stringResource(R.string.subscription_lifetime)
                }
                Text(
                    planLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }
```

```kotlin
// AFTER
                Spacer(Modifier.height(4.dp))
                val planLabel = when {
                    // Exact base-plan match first (one product, two base plans).
                    manageableSubscription && annualBasePlanId != null && currentBasePlanId == annualBasePlanId ->
                        stringResource(R.string.purchase_plan_annual)
                    manageableSubscription && monthlyBasePlanId != null && currentBasePlanId == monthlyBasePlanId ->
                        stringResource(R.string.purchase_plan_monthly)
                    // Fallbacks for a separate-product setup where the cadence is in the product id.
                    currentPlanId?.contains("annual") == true   -> stringResource(R.string.purchase_plan_annual)
                    currentPlanId?.contains("monthly") == true  -> stringResource(R.string.purchase_plan_monthly)
                    currentPlanId?.contains("lifetime") == true -> stringResource(R.string.purchase_plan_lifetime)
                    // A subscription whose cadence we couldn't resolve — don't mislabel it as
                    // "Lifetime"; show a neutral subscription label instead.
                    manageableSubscription -> stringResource(R.string.subscription_plan_recurring)
                    else -> stringResource(R.string.subscription_lifetime)
                }
                Text(
                    planLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )

                // Renewal date + price — subscriptions only, matches the resolved cadence's
                // package for price (annual vs monthly have different prices).
                if (manageableSubscription && currentExpirationDate != null) {
                    val matchedPackage = when {
                        annualBasePlanId != null && currentBasePlanId == annualBasePlanId -> offerings?.current?.annual
                        monthlyBasePlanId != null && currentBasePlanId == monthlyBasePlanId -> offerings?.current?.monthly
                        else -> null
                    }
                    val priceFormatted = matchedPackage?.product?.price?.formatted
                    val renewalText = if (priceFormatted != null) {
                        stringResource(R.string.subscription_renews_on_with_price, formatDate(currentExpirationDate), priceFormatted)
                    } else {
                        stringResource(R.string.subscription_renews_on, formatDate(currentExpirationDate))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        renewalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
```

Also add the collected state near the top of the composable, next to the other `collectAsState()`
calls:

```kotlin
// BEFORE (SubscriptionInfoScreen.kt, lines 62-65)
    val currentBasePlanId by viewModel.currentBasePlanId.collectAsState()
    val offerings by viewModel.offerings.collectAsState()
    val annualBasePlanId = offerings?.current?.annual?.product?.googleProduct?.basePlanId
    val monthlyBasePlanId = offerings?.current?.monthly?.product?.googleProduct?.basePlanId
```

```kotlin
// AFTER
    val currentBasePlanId by viewModel.currentBasePlanId.collectAsState()
    val currentExpirationDate by viewModel.currentExpirationDate.collectAsState()
    val offerings by viewModel.offerings.collectAsState()
    val annualBasePlanId = offerings?.current?.annual?.product?.googleProduct?.basePlanId
    val monthlyBasePlanId = offerings?.current?.monthly?.product?.googleProduct?.basePlanId
```

Add `currentExpirationDate` to `JournalViewModel`, right next to the existing billing pass-throughs:

```kotlin
// BEFORE (JournalViewModel.kt, lines 134-137)
    val offerings = billingManager.offerings
    val currentPlanId = billingManager.currentPlanId
    val manageableSubscription = billingManager.manageableSubscription
    val currentBasePlanId = billingManager.currentBasePlanId
```

```kotlin
// AFTER
    val offerings = billingManager.offerings
    val currentPlanId = billingManager.currentPlanId
    val manageableSubscription = billingManager.manageableSubscription
    val currentBasePlanId = billingManager.currentBasePlanId
    val currentExpirationDate = billingManager.currentExpirationDate
```

**Files:**
- `app/src/main/java/com/houseofmmminq/macaco/ui/screens/SubscriptionInfoScreen.kt`
- `app/src/main/java/com/houseofmmminq/macaco/ui/viewmodel/JournalViewModel.kt`

### New strings

| Key | EN value |
|-----|----------|
| `subscription_renews_on` | Renews on %1$s |
| `subscription_renews_on_with_price` | Renews on %1$s — %2$s |

Add to `strings.xml` for all 11 supported languages (translate the phrase, keep `%1$s`/`%2$s`
positional and in the same order — some languages will want to reorder date vs. price, which
`%1$s`/`%2$s` positional args already support per-locale).

---

## 4. Annual pre-renewal notification (7 days out)

**Problem:** No mechanism exists to remind annual subscribers before their (larger, less
frequent) charge lands. This is the churn/refund-reduction piece, separate from the always-on
Subscription Info date display above.

**Fix:** New `WorkManager` one-time work, scheduled/rescheduled every time `BillingManager`
resolves an active annual entitlement (step 1), with a delay computed to land 7 days before
`expirationDate`. Mirrors the existing `ReminderScheduler` / `ReminderWorker` pair used for
journal reminders, but as its own channel/id since it's a different kind of notification (not
user-toggleable in Settings — it's automatic and tied to being an annual subscriber).

**New file:** `app/src/main/java/com/houseofmmminq/macaco/util/RenewalReminderScheduler.kt`

```kotlin
package com.houseofmmminq.macaco.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.houseofmmminq.macaco.R
import java.util.concurrent.TimeUnit

/**
 * Schedules the one-time "your annual subscription renews soon" notification, timed to land 7
 * days before the active entitlement's expirationDate. Rescheduled by BillingManager.applyEntitlement
 * every time the entitlement refreshes (login, resume, push update), so it stays correct if the
 * renewal date ever shifts (e.g. a plan change). Monthly subscribers never get this — see
 * RevenueCatConfig.ANNUAL_BASE_PLAN_ID gating in BillingManager.
 */
object RenewalReminderScheduler {
    const val CHANNEL_ID = "subscription_renewal"
    const val NOTIFICATION_ID = 4002
    private const val WORK_NAME = "annual_renewal_reminder_work"
    private val LEAD_TIME_MILLIS = TimeUnit.DAYS.toMillis(7)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.renewal_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.renewal_channel_desc) }
            )
        }
    }

    /** [expirationMillis] is the entitlement's expirationDate (epoch millis). */
    fun schedule(context: Context, expirationMillis: Long) {
        ensureChannel(context)
        val fireAt = expirationMillis - LEAD_TIME_MILLIS
        val delay = fireAt - System.currentTimeMillis()
        if (delay <= 0) {
            // Already within 7 days of renewal (or past it) — don't fire a stale/late reminder.
            cancel(context)
            return
        }
        val request = OneTimeWorkRequestBuilder<RenewalReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
```

**New file:** `app/src/main/java/com/houseofmmminq/macaco/util/RenewalReminderWorker.kt`

```kotlin
package com.houseofmmminq.macaco.util

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.houseofmmminq.macaco.MainActivity
import com.houseofmmminq.macaco.R

/**
 * Posts the "your annual Macaco subscription renews in 7 days" notification. Scheduled by
 * RenewalReminderScheduler, timed off the live entitlement's expirationDate — see that file for
 * the scheduling/rescheduling logic.
 */
class RenewalReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        RenewalReminderScheduler.ensureChannel(ctx)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        // Deep-link into Subscription Info so the user can see the exact date/price immediately.
        val intent = Intent(ctx, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_SUBSCRIPTION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, RenewalReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.renewal_notification_title))
            .setContentText(ctx.getString(R.string.renewal_notification_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(R.string.renewal_notification_body)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(ctx).notify(RenewalReminderScheduler.NOTIFICATION_ID, notification)
        }
        return Result.success()
    }
}
```

### Deep link wiring (mirrors the existing ACTION_NEW_ENTRY pattern)

```kotlin
// BEFORE (MainActivity.kt, line 230, inside companion object)
        const val ACTION_NEW_ENTRY = "com.houseofmmminq.macaco.ACTION_NEW_ENTRY"
```

```kotlin
// AFTER
        const val ACTION_NEW_ENTRY = "com.houseofmmminq.macaco.ACTION_NEW_ENTRY"
        const val ACTION_OPEN_SUBSCRIPTION = "com.houseofmmminq.macaco.ACTION_OPEN_SUBSCRIPTION"
```

Add an `openSubscription` mutable state next to `openNewEntry`, set it in `onCreate`/`onNewIntent`
the same way, and pass it into `NavGraph`:

```kotlin
// BEFORE (MainActivity.kt, line 81)
        if (intent?.action == ACTION_NEW_ENTRY) openNewEntry = true
```

```kotlin
// AFTER
        if (intent?.action == ACTION_NEW_ENTRY) openNewEntry = true
        if (intent?.action == ACTION_OPEN_SUBSCRIPTION) openSubscription = true
```

```kotlin
// BEFORE (MainActivity.kt, line 185, in onNewIntent)
        if (intent.action == ACTION_NEW_ENTRY) openNewEntry = true
```

```kotlin
// AFTER
        if (intent.action == ACTION_NEW_ENTRY) openNewEntry = true
        if (intent.action == ACTION_OPEN_SUBSCRIPTION) openSubscription = true
```

Declare `openSubscription` as a `mutableStateOf(false)` the same way `openNewEntry` is declared
(find that declaration near the top of `MainActivity` — same pattern, new variable), and thread
it into `NavGraph`:

```kotlin
// BEFORE (MainActivity.kt, lines 164-169)
                Box(Modifier.fillMaxSize()) {
                    NavGraph(
                        viewModel = vm,
                        openNewEntry = openNewEntry,
                        onOpenNewEntryConsumed = { openNewEntry = false }
                    )
```

```kotlin
// AFTER
                Box(Modifier.fillMaxSize()) {
                    NavGraph(
                        viewModel = vm,
                        openNewEntry = openNewEntry,
                        onOpenNewEntryConsumed = { openNewEntry = false },
                        openSubscription = openSubscription,
                        onOpenSubscriptionConsumed = { openSubscription = false }
                    )
```

In `NavGraph.kt`, add the two new parameters and a `LaunchedEffect`, mirroring the existing
`openNewEntry` block exactly:

```kotlin
// BEFORE (NavGraph.kt, lines 77-80)
fun NavGraph(
    ...,
    openNewEntry: Boolean = false,
    onOpenNewEntryConsumed: () -> Unit = {}
```

```kotlin
// AFTER
fun NavGraph(
    ...,
    openNewEntry: Boolean = false,
    onOpenNewEntryConsumed: () -> Unit = {},
    openSubscription: Boolean = false,
    onOpenSubscriptionConsumed: () -> Unit = {}
```

```kotlin
// BEFORE (NavGraph.kt, lines 172-179)
            // Deep link from the journal-reminder notification: jump straight to the new-entry
            // screen. Only reachable here (signed-in + purchased), so the gates are respected.
            LaunchedEffect(openNewEntry) {
                if (openNewEntry) {
                    goToNewEntry()
                    onOpenNewEntryConsumed()
                }
            }
```

```kotlin
// AFTER
            // Deep link from the journal-reminder notification: jump straight to the new-entry
            // screen. Only reachable here (signed-in + purchased), so the gates are respected.
            LaunchedEffect(openNewEntry) {
                if (openNewEntry) {
                    goToNewEntry()
                    onOpenNewEntryConsumed()
                }
            }

            // Deep link from the annual-renewal reminder notification: jump to Subscription Info.
            LaunchedEffect(openSubscription) {
                if (openSubscription) {
                    navController.navigate(Screen.Subscription.route)
                    onOpenSubscriptionConsumed()
                }
            }
```

**Files:**
- `app/src/main/java/com/houseofmmminq/macaco/util/RenewalReminderScheduler.kt` (new)
- `app/src/main/java/com/houseofmmminq/macaco/util/RenewalReminderWorker.kt` (new)
- `app/src/main/java/com/houseofmmminq/macaco/MainActivity.kt`
- `app/src/main/java/com/houseofmmminq/macaco/ui/navigation/NavGraph.kt`

### New strings

| Key | EN value |
|-----|----------|
| `renewal_channel_name` | Subscription renewal |
| `renewal_channel_desc` | A one-time reminder before your annual Macaco subscription renews. |
| `renewal_notification_title` | Your annual plan renews soon |
| `renewal_notification_body` | Your Macaco Premium subscription renews in 7 days. Tap to review your plan. |

Add to `strings.xml` for all 11 supported languages.

**Note on POST_NOTIFICATIONS:** the runtime permission is already requested at app launch in
`MainActivity.kt` (for journal reminders) and the manifest already declares it — no manifest or
permission-request changes needed for this feature; `RenewalReminderWorker` just reuses the
existing grant check.

**Explicitly out of scope:** no Settings toggle for this notification (unlike journal reminders)
— it's automatic for annual subscribers, not user-configurable, per the "annual only, no monthly
spam" decision. No monthly renewal notification of any kind.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Expose `currentExpirationDate` StateFlow; reschedule/cancel annual reminder on entitlement change | `BillingManager.kt` |
| 2 | Add `ANNUAL_BASE_PLAN_ID` constant | `RevenueCatConfig.kt` |
| 3 | Show "Renews on `<date>` — `<price>`" line | `SubscriptionInfoScreen.kt`, `JournalViewModel.kt` |
| 4 | New WorkManager scheduler + worker for the 7-day annual pre-renewal notification | `RenewalReminderScheduler.kt` (new), `RenewalReminderWorker.kt` (new) |
| 5 | Deep-link wiring (`ACTION_OPEN_SUBSCRIPTION`) from notification tap to Subscription Info | `MainActivity.kt`, `NavGraph.kt` |
| 6 | New string keys (renewal line + notification channel/copy) × 11 languages | `strings.xml` |
