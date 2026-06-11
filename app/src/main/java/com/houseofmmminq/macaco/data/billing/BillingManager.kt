package com.houseofmmminq.macaco.data.billing

import android.app.Activity
import android.content.Context
import com.houseofmmminq.macaco.data.PreferencesManager
import com.houseofmmminq.macaco.data.auth.AuthRepository
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.logInWith
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
    appContext: Context,
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

    // Product identifier of the active entitlement, e.g. "macaco_annual"
    private val _currentPlanId = MutableStateFlow<String?>(null)
    val currentPlanId: StateFlow<String?> = _currentPlanId.asStateFlow()

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
                    _isPremium.value = info.entitlements[RevenueCatConfig.ENTITLEMENT_ID]?.isActive == true
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
                                _isPremium.value =
                                    info.entitlements[RevenueCatConfig.ENTITLEMENT_ID]?.isActive == true
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

    private fun refreshEntitlement() {
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { if (_isPremium.value == null) _isPremium.value = false },
            onSuccess = { info ->
                val entitlement = info.entitlements[RevenueCatConfig.ENTITLEMENT_ID]
                _isPremium.value = entitlement?.isActive == true
                if (entitlement?.isActive == true) _currentPlanId.value = entitlement.productIdentifier
            }
        )
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
                    val active = entitlement?.isActive == true
                    _isPremium.value = active
                    if (active) _currentPlanId.value = entitlement?.productIdentifier
                    cont.resume(Result.success(active))
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
                    val active = info.entitlements[RevenueCatConfig.ENTITLEMENT_ID]?.isActive == true
                    _isPremium.value = active
                    cont.resume(Result.success(active))
                }
            )
        }
    }

}
