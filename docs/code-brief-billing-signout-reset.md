# Macaco — BillingManager: Reset Entitlement on Sign-Out

Fixes the previous user's premium entitlement leaking to the next account on the same device.
Touches `BillingManager.kt` only.

**Background:** `BillingManager` ties the RevenueCat identity to the Firebase uid via
`logInWith` when a user signs in, but does nothing when `currentUser` becomes null. After
sign-out, `isPremium` keeps the old user's `true` and the RevenueCat SDK stays logged in as the
old uid. A different, non-premium account that signs in next skips the paywall until the
`logIn` callback returns — or indefinitely if that call fails (offline). `restore()` also
updates `_isPremium` directly, bypassing `applyEntitlement`, so `currentPlanId` /
`currentBasePlanId` / `manageableSubscription` go stale after a restore.

---

## Change 1 — Handle the signed-out branch of the auth collect

**Problem:** The `authRepository.currentUser.collect` in `init` only handles `user != null`.

**Fix:** On null, immediately clear all entitlement-derived state (so the paywall gate is
correct the instant the next account signs in), then log RevenueCat out to the anonymous user.
`logOut` errors when the current RevenueCat user is already anonymous (e.g. cold start while
signed out) — treat that as a no-op.

```kotlin
// BEFORE (init, ~line 93)
            scope.launch {
                authRepository.currentUser.collect { user ->
                    if (user != null) {
                        Purchases.sharedInstance.logInWith(
                            user.uid,
                            onError = { },
                            onSuccess = { info, _ ->
                                applyEntitlement(info.entitlements[RevenueCatConfig.ENTITLEMENT_ID])
                            }
                        )
                    }
                }
            }

// AFTER
            scope.launch {
                authRepository.currentUser.collect { user ->
                    if (user != null) {
                        Purchases.sharedInstance.logInWith(
                            user.uid,
                            onError = { },
                            onSuccess = { info, _ ->
                                applyEntitlement(info.entitlements[RevenueCatConfig.ENTITLEMENT_ID])
                            }
                        )
                    } else {
                        // Signed out: drop the previous account's entitlement NOW so the next
                        // sign-in can't skip the paywall on stale state, then detach the
                        // RevenueCat identity. logOut fails if already anonymous — ignore.
                        applyEntitlement(null)
                        Purchases.sharedInstance.logOutWith(
                            onError = { },
                            onSuccess = { info ->
                                applyEntitlement(info.entitlements[RevenueCatConfig.ENTITLEMENT_ID])
                            }
                        )
                    }
                }
            }
```

Add the import: `import com.revenuecat.purchases.logOutWith`.

Note: `applyEntitlement(null)` sets `_isPremium.value = false` (not null), so the NavGraph gate
shows LoginScreen normally for signed-out users — no blank-box regression.

**File:** `BillingManager.kt`

---

## Change 2 — restore() goes through applyEntitlement

**Problem:** `restore()` sets `_isPremium` directly; plan id and manage-subscription state
don't update after a successful restore.

**Fix:**

```kotlin
// BEFORE (~line 182)
        return suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.restorePurchasesWith(
                onError = { error -> cont.resume(Result.failure(Exception(error.message))) },
                onSuccess = { info ->
                    val active = info.entitlements[RevenueCatConfig.ENTITLEMENT_ID]?.isActive == true
                    _isPremium.value = active
                    cont.resume(Result.success(active))
                }
            )
        }

// AFTER
        return suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.restorePurchasesWith(
                onError = { error -> cont.resume(Result.failure(Exception(error.message))) },
                onSuccess = { info ->
                    val entitlement = info.entitlements[RevenueCatConfig.ENTITLEMENT_ID]
                    applyEntitlement(entitlement)
                    cont.resume(Result.success(entitlement?.isActive == true))
                }
            )
        }
```

**File:** `BillingManager.kt`

---

## Scope notes

- One behaviour change to be aware of: `applyEntitlement(null)` currently leaves
  `_currentPlanId` untouched in its else-branch — that's fine for this fix (it's only read
  when premium), no need to alter `applyEntitlement`.
- Testing: needs two Google accounts on a device, one with an active test entitlement — sign
  in premium → sign out → sign in non-premium → PurchaseScreen must appear.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Sign-out branch: `applyEntitlement(null)` + RevenueCat `logOutWith` | `BillingManager.kt` |
| 2 | `restore()` routes through `applyEntitlement` | `BillingManager.kt` |
