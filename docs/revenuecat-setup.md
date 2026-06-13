# RevenueCat + Play Console setup

How to take the paywall from its local-fallback state to real Google Play purchases.

Until this is done, `RevenueCatConfig.GOOGLE_API_KEY` is the placeholder `goog_YOUR_…`, so
`BillingManager` falls back to the local DataStore `is_purchased` flag and the paywall shows
hardcoded prices. Completing the steps below flips `RevenueCatConfig.isConfigured` to true and the
gate is driven by the real `premium` entitlement.

## What the code already assumes (your targets)

These are wired in code — match them exactly:

- **App ID:** `com.houseofmmminq.macaco`
- **Entitlement:** `premium` (`RevenueCatConfig.ENTITLEMENT_ID`)
- **Three packages** in the default `current` offering, using RevenueCat's standard package types
  (read by `BillingManager` as `offerings.current.annual` / `.monthly` / `.lifetime`):
  - **Annual** (`$rc_annual`) — must include a **7-day free trial** (the CTA says "Try Free for 7 Days")
  - **Monthly** (`$rc_monthly`)
  - **Lifetime** (`$rc_lifetime`) — a one-time, non-consumable product
- Fallback display prices (cosmetic only, until live): annual $17.99, monthly $2.99, lifetime $39.99

Product / base-plan **IDs below are suggestions** — only the entitlement name (`premium`) and the
package types (annual/monthly/lifetime) must match the code.

## Part A — Google Play Console

1. **App on a track.** Real Play Billing only works for an app uploaded to at least an **internal
   testing** track, signed with the upload/Play key. Create the app (applicationId
   `com.houseofmmminq.macaco`) and upload a release AAB once.
2. **Play Billing.** Confirm the Play Billing Library is detected (RevenueCat wraps Billing v6+).
3. **Create the products:**
   - **Subscriptions** (Monetize → Products → Subscriptions): one subscription product, e.g.
     `macaco_premium_sub`, with **two base plans**:
     - `annual` — billing period 1 year, ~$17.99, with an **Offer** that has a **7-day free trial** phase.
     - `monthly` — billing period 1 month, ~$2.99.
   - **In-app product** (Monetize → Products → In-app products):
     - `macaco_lifetime` — **non-consumable**, one-time, ~$39.99.
   - Activate all of them.
4. **Service account for RevenueCat** (server-side purchase validation):
   - Google Cloud Console (project `macaco-499016`) → create a service account.
   - Play Console → Setup → API access → link it and grant **Financial data / Manage orders &
     subscriptions** permissions.
   - Download the JSON key (pasted into RevenueCat in Part B).
5. **License testers:** Play Console → Setup → License testing → add your Google account(s) so test
   purchases don't charge real money.

## Part B — RevenueCat dashboard

1. **Create project** at https://app.revenuecat.com → add a **Google Play** app, applicationId
   `com.houseofmmminq.macaco`. Upload the **Play service-account JSON** from A4.
2. **Add products** (Products tab) — import the annual base plan, the monthly base plan, and
   `macaco_lifetime`.
3. **Entitlement** — create one named exactly **`premium`** and attach all three products.
4. **Offering** — create the default **`current`** offering with **three packages**, using the
   standard types so `offerings.current.annual/.monthly/.lifetime` resolve:
   - Annual package → annual base plan
   - Monthly package → monthly base plan
   - Lifetime package → `macaco_lifetime`
5. **Copy the public Google SDK API key** (Project → API keys → the **public** key starting with
   `goog_`).

## Part C — Code (the only change)

In `app/src/main/java/com/houseofmmminq/macaco/data/billing/RevenueCatConfig.kt`:

```kotlin
const val GOOGLE_API_KEY = "goog_<your real public key>"
```

That flips `isConfigured` to true: `BillingManager` stops using the local DataStore fallback, live
prices populate the paywall, and `purchase()` / `restore()` go through real Play Billing. The
`premium` entitlement drives the NavGraph gate automatically.

## Part D — Testing (gotchas)

- Install the build **from the Play internal-testing track** (or a build signed with the Play key)
  on a device logged in with a **license-tester** account. A locally sideloaded debug APK **cannot**
  complete a real purchase.
- First propagation of products into RevenueCat can take a few hours.
- The annual plan **must** have the 7-day trial offer, or the "Try Free for 7 Days" CTA is misleading.
- On the dev Samsung A53, premium is currently faked via the local DataStore `is_purchased` flag.
  Once RevenueCat is live that flag is ignored, so test the *real* paywall on a device without the
  flag (fresh install / cleared data).

## Related code

- `data/billing/RevenueCatConfig.kt` — `GOOGLE_API_KEY`, `ENTITLEMENT_ID`, `isConfigured`
- `data/billing/BillingManager.kt` — entitlement gate, offerings load, `purchase()` / `restore()`,
  local fallback when not configured
- `ui/screens/PurchaseScreen.kt` — the three-tier paywall that renders the offering
