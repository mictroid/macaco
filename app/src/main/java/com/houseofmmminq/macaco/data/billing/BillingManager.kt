package com.houseofmmminq.macaco.data.billing

import android.app.Activity
import android.content.Context
import com.houseofmmminq.macaco.data.PreferencesManager
import com.houseofmmminq.macaco.data.auth.AuthRepository
import com.houseofmmminq.macaco.util.RenewalReminderScheduler
import com.revenuecat.purchases.EntitlementInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.Store
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.logInWith
import com.revenuecat.purchases.logOutWith
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import com.revenuecat.purchases.PurchaseParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wraps RevenueCat (which itself wraps Google Play Billing — Play Store compliant).
 *
 * When RevenueCatConfig is not filled in, this degrades to the local DataStore
 * unlock flag so the unlock flow stays testable without a RevenueCat account.
 */
class BillingManager(
    private val appContext: Context,
    private val preferencesManager: PreferencesManager,
    authRepository: AuthRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val configured = RevenueCatConfig.isConfigured

    // null = still loading, false = not entitled, true = entitled
    private val _isPremium = MutableStateFlow<Boolean?>(null)
    val isPremium: StateFlow<Boolean?> = _isPremium.asStateFlow()

    private val _priceLabel = MutableStateFlow("$2.99")
    val priceLabel: StateFlow<String> = _priceLabel.asStateFlow()

    private val _offerings = MutableStateFlow<Offerings?>(null)
    val offerings: StateFlow<Offerings?> = _offerings.asStateFlow()

    // Product identifier of the active entitlement. NOTE: with one subscription product
    // (macaco_premium) carrying both the monthly and annual base plans, this is "macaco_premium"
    // for both cadences — it does NOT encode monthly/annual. Use [manageableSubscription] for the
    // "is this a manageable Play subscription?" question rather than string-matching this.
    private val _currentPlanId = MutableStateFlow<String?>(null)
    val currentPlanId: StateFlow<String?> = _currentPlanId.asStateFlow()

    // True when the active entitlement is an actual Play Store subscription (has a renewal/expiry),
    // as opposed to a one-time lifetime purchase (no expiry) or a promotional/other-store grant.
    // Drives whether the "Manage subscription" link is shown.
    private val _manageableSubscription = MutableStateFlow(false)
    val manageableSubscription: StateFlow<Boolean> = _manageableSubscription.asStateFlow()

    // The active entitlement's Google base-plan id (e.g. the monthly vs annual base plan under
    // macaco_premium). Matched against the offering's packages by the UI to show which plan the
    // user is on; null when not on a subscription.
    private val _currentBasePlanId = MutableStateFlow<String?>(null)
    val currentBasePlanId: StateFlow<String?> = _currentBasePlanId.asStateFlow()

    // Epoch millis of the active entitlement's next renewal/expiry. Null when not on an active
    // subscription (lifetime purchases have no expiry either). Drives the "Renews on ..." line
    // on SubscriptionInfoScreen and the annual pre-renewal notification schedule.
    private val _currentExpirationDate = MutableStateFlow<Long?>(null)
    val currentExpirationDate: StateFlow<Long?> = _currentExpirationDate.asStateFlow()

    init {
        if (configured) {
            if (!Purchases.isConfigured) {
                Purchases.logLevel = LogLevel.WARN
                Purchases.configure(
                    PurchasesConfiguration.Builder(appContext, RevenueCatConfig.GOOGLE_API_KEY).build()
                )
            }

            // React to entitlement changes pushed by the SDK
            Purchases.sharedInstance.updatedCustomerInfoListener =
                UpdatedCustomerInfoListener { info ->
                    applyEntitlement(info.entitlements[RevenueCatConfig.ENTITLEMENT_ID])
                }

            refreshEntitlement()
            loadOfferings()

            // Tie RevenueCat identity to the signed-in account so purchases follow the user
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
        } else {
            // Fallback: mirror the local DataStore flag
            scope.launch {
                preferencesManager.isPurchased.collect { _isPremium.value = it }
            }
        }
    }

    fun refreshEntitlement() {
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { if (_isPremium.value == null) _isPremium.value = false },
            onSuccess = { info ->
                applyEntitlement(info.entitlements[RevenueCatConfig.ENTITLEMENT_ID])
            }
        )
    }

    /**
     * Apply the latest "premium" entitlement to all derived state. Single source of truth so every
     * RevenueCat callback (refresh, login, purchase, push update) stays consistent.
     */
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

    private fun loadOfferings() {
        Purchases.sharedInstance.getOfferingsWith(
            onError = { },
            onSuccess = { offerings ->
                _offerings.value = offerings
                // Default price label: annual if available, else first available
                val priceFormatted = offerings.current?.annual?.product?.price?.formatted
                    ?: offerings.current?.availablePackages?.firstOrNull()?.product?.price?.formatted
                priceFormatted?.let { _priceLabel.value = it }
            }
        )
    }

    suspend fun purchase(activity: Activity, packageToPurchase: Package): Result<Boolean> {
        if (!configured) {
            preferencesManager.setPurchased(true)
            return Result.success(true)
        }
        return suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.purchaseWith(
                PurchaseParams.Builder(activity, packageToPurchase).build(),
                onError = { error, userCancelled ->
                    if (userCancelled) cont.resume(Result.success(false))
                    else cont.resume(Result.failure(Exception(error.message)))
                },
                onSuccess = { _, customerInfo ->
                    val entitlement = customerInfo.entitlements[RevenueCatConfig.ENTITLEMENT_ID]
                    applyEntitlement(entitlement)
                    cont.resume(Result.success(entitlement?.isActive == true))
                }
            )
        }
    }

    suspend fun restore(): Result<Boolean> {
        if (!configured) {
            return Result.success(_isPremium.value == true)
        }
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
    }

}
