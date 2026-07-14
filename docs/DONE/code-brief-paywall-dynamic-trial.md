# Macaco — Paywall: Read Free-Trial Info From RevenueCat Instead of Hardcoding It

Covers `PurchaseScreen.kt` (paywall UI) and `strings.xml` ×11. The paywall currently hardcodes
"7 days free" copy only on the Annual card and never shows any trial messaging on Monthly — even
though Play Console now has an **active 7-day-free-trial offer on both the `annual` and `monthly`
base plans** (confirmed in Play Console → Subscriptions → Macaco Premium, both offers `Active`).
This brief makes the paywall render trial messaging *from what RevenueCat actually returns* for
each package, so it can never drift out of sync with Play Console again.

Context for scope: the paywall is only ever shown after the 3-free-entry limit is hit or when a
premium-gated feature (Drive sync, backup, print book) is tapped — this brief doesn't touch when
the paywall appears, only what it says once it's on screen.

---

## 1. Trial info isn't read from RevenueCat at all today

**Problem:** `PurchaseScreen.kt` lines 82–97 pull only the flat price off each package and assume
per-plan whether a trial exists — it never inspects the package's actual pricing phases:

```kotlin
// BEFORE — PurchaseScreen.kt, lines 82–97
// Prices from RevenueCat offerings; fall back to display strings if not loaded yet
val monthlyPrice = offerings?.current?.monthly?.product?.price?.formatted ?: "$2.99"
val annualPrice  = offerings?.current?.annual?.product?.price?.formatted  ?: "$17.99"
val lifetimePrice = offerings?.current?.lifetime?.product?.price?.formatted ?: "$39.99"

val selectedPkg: Package? = when (selectedPlan) {
    PlanSelection.Annual   -> offerings?.current?.annual
    PlanSelection.Monthly  -> offerings?.current?.monthly
    PlanSelection.Lifetime -> offerings?.current?.lifetime
}

val ctaLabel = when (selectedPlan) {
    PlanSelection.Monthly  -> stringResource(R.string.purchase_cta_monthly, monthlyPrice)
    PlanSelection.Annual   -> stringResource(R.string.purchase_cta_annual)
    PlanSelection.Lifetime -> stringResource(R.string.purchase_cta_lifetime, lifetimePrice)
}
```

Then the Annual `PlanCard` hardcodes trial copy unconditionally, and the Monthly `PlanCard` never
mentions a trial at all:

```kotlin
// BEFORE — PurchaseScreen.kt, lines 191–214
// Annual — highlighted as Best Value
PlanCard(
    title = stringResource(R.string.purchase_plan_annual),
    subtitle = stringResource(R.string.purchase_free_trial, annualPrice),
    detail = stringResource(R.string.purchase_save_50),
    badge = stringResource(R.string.purchase_best_value),
    note = stringResource(R.string.purchase_trial_note),
    selected = selectedPlan == PlanSelection.Annual,
    isRecommended = true,
    onClick = { selectedPlan = PlanSelection.Annual }
)

Spacer(Modifier.height(6.dp))

// Monthly
PlanCard(
    title = stringResource(R.string.purchase_plan_monthly),
    subtitle = "$monthlyPrice ${stringResource(R.string.purchase_per_month)}",
    detail = stringResource(R.string.purchase_cancel_anytime),
    badge = null,
    note = null,
    selected = selectedPlan == PlanSelection.Monthly,
    onClick = { selectedPlan = PlanSelection.Monthly }
)
```

**Fix:** add a small helper that reads the free-trial pricing phase directly off the package's
`StoreProduct`, and drive both cards + the CTA label from it. RevenueCat's Android SDK (v8, Play
Billing Library v6+ model, `com.revenuecat.purchases:purchases:8.10.0` per
`gradle/libs.versions.toml`) exposes subscription pricing as a list of phases per subscription
option — the free-trial phase is the one with a zero price. Add this near the top of
`PurchaseScreen.kt`, after the existing imports:

```kotlin
import com.revenuecat.purchases.models.Period

/**
 * Days in this package's free-trial phase, read from RevenueCat's actual pricing-phase data
 * (not assumed per-plan) — so the paywall can never say "free trial" for a plan that doesn't
 * have one in Play Console, or omit it for one that does.
 *
 * NOTE FOR IMPLEMENTATION: the exact accessor names below (`subscriptionOptions`, `.freeTrial`,
 * `.freePhase`, `.billingPeriod`) reflect RevenueCat's documented v8 pricing-phases API as of
 * this brief. Verify against the installed 8.10.0 AAR via Android Studio autocomplete on
 * `Package.product` (type `StoreProduct`) before compiling — if the property names differ
 * slightly in this exact version, adjust to match, but keep the logic: find the subscription
 * option with a free trial phase, read its billing period length in days.
 */
private fun Package.trialDays(): Int? {
    val freeOption = product.subscriptionOptions?.freeTrial ?: return null
    val period = freeOption.freePhase?.billingPeriod ?: return null
    return when (period.unit) {
        Period.Unit.DAY  -> period.value
        Period.Unit.WEEK -> period.value * 7
        else             -> null // months/years as a "trial" phase would be unusual; ignore
    }
}
```

Then replace the price/CTA block:

```kotlin
// AFTER — PurchaseScreen.kt, replaces lines 82–97
// Prices from RevenueCat offerings; fall back to display strings if not loaded yet
val monthlyPrice = offerings?.current?.monthly?.product?.price?.formatted ?: "$2.99"
val annualPrice  = offerings?.current?.annual?.product?.price?.formatted  ?: "$17.99"
val lifetimePrice = offerings?.current?.lifetime?.product?.price?.formatted ?: "$39.99"

// Trial length read live from RevenueCat — null if that package has no trial phase configured
// in Play Console. Do not assume; this is what makes Monthly and Annual behave identically
// once Play Console has a trial on both, and stops silently showing/hiding trial copy by hand.
val annualTrialDays  = offerings?.current?.annual?.trialDays()
val monthlyTrialDays = offerings?.current?.monthly?.trialDays()

val selectedPkg: Package? = when (selectedPlan) {
    PlanSelection.Annual   -> offerings?.current?.annual
    PlanSelection.Monthly  -> offerings?.current?.monthly
    PlanSelection.Lifetime -> offerings?.current?.lifetime
}

val ctaLabel = when (selectedPlan) {
    PlanSelection.Monthly  -> monthlyTrialDays?.let { stringResource(R.string.purchase_cta_trial, it) }
        ?: stringResource(R.string.purchase_cta_monthly, monthlyPrice)
    PlanSelection.Annual   -> annualTrialDays?.let { stringResource(R.string.purchase_cta_trial, it) }
        ?: stringResource(R.string.purchase_cta_annual_no_trial, annualPrice)
    PlanSelection.Lifetime -> stringResource(R.string.purchase_cta_lifetime, lifetimePrice)
}
```

And replace the two `PlanCard` calls:

```kotlin
// AFTER — PurchaseScreen.kt, replaces lines 191–214
// Annual — highlighted as Best Value
PlanCard(
    title = stringResource(R.string.purchase_plan_annual),
    subtitle = annualTrialDays?.let { stringResource(R.string.purchase_free_trial_annual, it, annualPrice) }
        ?: "$annualPrice ${stringResource(R.string.purchase_per_year)}",
    detail = stringResource(R.string.purchase_save_50),
    badge = stringResource(R.string.purchase_best_value),
    note = annualTrialDays?.let { stringResource(R.string.purchase_trial_note) },
    selected = selectedPlan == PlanSelection.Annual,
    isRecommended = true,
    onClick = { selectedPlan = PlanSelection.Annual }
)

Spacer(Modifier.height(6.dp))

// Monthly
PlanCard(
    title = stringResource(R.string.purchase_plan_monthly),
    subtitle = monthlyTrialDays?.let { stringResource(R.string.purchase_free_trial_monthly, it, monthlyPrice) }
        ?: "$monthlyPrice ${stringResource(R.string.purchase_per_month)}",
    detail = stringResource(R.string.purchase_cancel_anytime),
    badge = null,
    note = monthlyTrialDays?.let { stringResource(R.string.purchase_trial_note) },
    selected = selectedPlan == PlanSelection.Monthly,
    onClick = { selectedPlan = PlanSelection.Monthly }
)
```

Behavior: if Play Console ever drops the trial from one plan (or adds one to Lifetime — not
expected, it's one-time), the paywall follows automatically with zero code changes. Before
offerings load (`offerings == null`), both trial-day values are `null`, so the UI falls back to
the same non-trial copy it shows today during that brief loading window — no flash of incorrect
"free trial" text.

**Files:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/PurchaseScreen.kt`

---

## 2. String resources

Two existing keys change meaning/params, and new keys are added. Update `values/strings.xml`
(English, source of truth) and propagate to all 10 translated locales
(`de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`, `sv`, `zh-rCN`).

```xml
<!-- BEFORE -->
<string name="purchase_free_trial">7 days free, then %s / year</string>
<string name="purchase_cta_annual">Try Free for 7 Days</string>

<!-- AFTER -->
<string name="purchase_free_trial_annual">%1$d days free, then %2$s / year</string>
<string name="purchase_free_trial_monthly">%1$d days free, then %2$s / month</string>
<string name="purchase_cta_trial">Try Free for %1$d Days</string>
<string name="purchase_cta_annual_no_trial">Start Annual — %s</string>
```

`purchase_trial_note` and `purchase_cta_monthly` are unchanged (still used for the no-trial
fallback path).

| Key | EN value | Notes |
|-----|----------|-------|
| `purchase_free_trial_annual` | `%1$d days free, then %2$s / year` | Replaces `purchase_free_trial`; now takes trial-day count as `%1$d` |
| `purchase_free_trial_monthly` | `%1$d days free, then %2$s / month` | New — Monthly card trial subtitle |
| `purchase_cta_trial` | `Try Free for %1$d Days` | New — shared CTA for whichever plan is selected when it has a trial |
| `purchase_cta_annual_no_trial` | `Start Annual — %s` | New — fallback CTA if Annual's trial phase is ever removed in Play Console |

Delete `purchase_free_trial` and `purchase_cta_annual` only after confirming no other screen
references them (`grep -rn "purchase_free_trial\b\|purchase_cta_annual\b"` — `HelpAboutScreen.kt`
references `help_faq_free_trial_q/a`, a different key, so it's unaffected).

**Files:** `app/src/main/res/values/strings.xml` + all 10 `values-*/strings.xml` locale files.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `Package.trialDays()` helper reading RevenueCat's actual pricing-phase data | `PurchaseScreen.kt` |
| 2 | Drive Annual + Monthly card subtitle/note/CTA from live trial data instead of hardcoded per-plan assumptions | `PurchaseScreen.kt` |
| 3 | New/renamed string keys, propagated across all 11 locales | `values*/strings.xml` |
