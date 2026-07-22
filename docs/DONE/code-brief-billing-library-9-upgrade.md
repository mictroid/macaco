# Macaco — Billing: Upgrade RevenueCat SDK to Satisfy Play Billing Library 8.0.0+ Requirement

Google Play Console flagged Macaco on 2026-07-22: apps must use Google Play Billing Library
8.0.0+ by **Aug 31, 2026** or updates will be rejected. Macaco doesn't call Play Billing
directly — it goes through RevenueCat, which bundles Billing Library internally. The fix is a
dependency version bump, not app code touching Billing Library APIs. Touches
`gradle/libs.versions.toml` and `app/build.gradle.kts` only; `BillingManager.kt` and
`RevenueCatConfig.kt` need no code changes (see verification below).

**Deadline:** 2026-08-31. Needs to ship (new version published to whichever Play tracks are
active) before then — build + smoke test + publish, don't leave this to the last day.

---

## Why the current version doesn't qualify

`gradle/libs.versions.toml` pins `revenueCat = "8.10.0"`. RevenueCat's Purchases Android SDK only
started bundling Google Play Billing Library 8.0.0+ starting at **Purchases SDK v9.0.0**
(released to support Billing Library 8). `8.10.0` predates that and ships an older Billing
Library that no longer meets Play's requirement.

## Change 1 — Bump the RevenueCat version

**Fix:** Bump to the latest stable 9.x release, `9.29.1` (confirmed on Maven Central as of
2026-07-22). This stays on the documented 8.x→9.x migration path (Billing Library 8.3.0) without
pulling in RevenueCat's newer major version 10.x, whose full migration notes weren't verifiable
at brief-writing time — safer for a compliance-deadline change. If you want to double check
Maven for anything newer before implementing, query:
`https://repo1.maven.org/maven2/com/revenuecat/purchases/purchases/maven-metadata.xml`

```toml
# BEFORE (gradle/libs.versions.toml, line 22)
revenueCat = "8.10.0"

# AFTER
revenueCat = "9.29.1"
```

No other lines in `libs.versions.toml` or `app/build.gradle.kts` reference the RevenueCat version
directly — `implementation(libs.revenuecat.purchases)` (line 144 of `app/build.gradle.kts`)
resolves through the version catalog automatically.

**File:** `gradle/libs.versions.toml`

---

## Change 2 — Verify no code changes are needed (do this, don't skip)

The 8.x→9.x migration guide has two code-breaking changes. I checked `BillingManager.kt` and
`RevenueCatConfig.kt` against both — neither applies today, but re-verify after the bump since
new lint/compile errors will show up immediately if something was missed:

1. **RevenueCat model classes are no longer Kotlin `data class`** (no `copy()` /
   `componentN()` destructuring). `BillingManager.kt` only reads properties off
   `EntitlementInfo`, `CustomerInfo`, `Offerings`, and `Package` (`.isActive`,
   `.productIdentifier`, `.productPlanIdentifier`, `.expirationDate`, `.entitlements[...]`,
   `.current?.annual`, etc.) — no `.copy()` or destructuring anywhere in the file. Should
   compile clean.
2. **Kotlin minimum bumped to 1.8.0+.** Project is on Kotlin 2.2.10 already — no change needed.

If the build fails after the version bump, it's almost certainly a signature change on a
RevenueCat type not listed above — check the compiler error against
`https://www.revenuecat.com/docs/sdk-guides/android-native-8x-to-9x-migration`.

**File:** none (verification only)

---

## Scope notes / do NOT touch

- Don't change `RevenueCatConfig.ENTITLEMENT_ID`, `GOOGLE_API_KEY`, or `ANNUAL_BASE_PLAN_ID` —
  unrelated to this fix.
- Don't refactor `BillingManager.kt`'s structure — this is a dependency bump, keep the diff
  minimal so it's easy to verify against the compliance deadline.

## ⚠️ Action for Michael, not Code — check before this ships

Play Billing Library 8 **removed the ability to query expired subscriptions and consumed
one-time products**. Macaco sells a **Lifetime** one-time purchase (see `PurchaseScreen.kt`,
`offerings?.current?.lifetime`) alongside the Monthly/Annual subscription. If that Lifetime
product is misconfigured in the RevenueCat dashboard as **consumable** instead of
**non-consumable**, RevenueCat will consume it on purchase and future restores will silently
fail for those users from this version onward.

**Before publishing this build:** in the RevenueCat dashboard → Products, confirm the Lifetime
product is set to non-consumable. This is a dashboard setting, not a code change — flagging it
here so it isn't missed.

## Testing

- Real purchase/restore flow needs a build uploaded to a Play Console track (internal testing is
  fine) and signed with the matching key — a sideloaded debug APK can't complete a real
  purchase, per existing project convention.
- Test all three paths: Monthly purchase, Annual purchase, Lifetime purchase, and Restore
  Purchases (existing premium test account) — confirm `isPremium`, `currentPlanId`,
  `manageableSubscription`, and `currentExpirationDate` all populate as before.
- Confirm sign-out/sign-in entitlement reset still works (see prior brief
  `docs/DONE/code-brief-billing-signout-reset.md` — this bump doesn't touch that logic but it's
  the same file, worth a quick regression check).

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Bump `revenueCat` version 8.10.0 → 9.29.1 | `gradle/libs.versions.toml` |
| 2 | Verify no `.copy()`/destructuring on RevenueCat types (none found) | — (verification) |
