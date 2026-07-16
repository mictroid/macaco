package com.houseofmmminq.macaco.data.billing

/**
 * RevenueCat configuration.
 *
 * Setup steps (one-time):
 *  1. Create an account at https://app.revenuecat.com and add a new project.
 *  2. Add an app → Google Play. Paste your Play Console service-account credentials
 *     (RevenueCat needs these to validate purchases server-side).
 *  3. In RevenueCat → API Keys, copy the PUBLIC Google SDK key (starts with "goog_").
 *     Put it in GOOGLE_API_KEY below.
 *  4. In Google Play Console → Monetize → Products → In-app products, create a
 *     one-time product (e.g. id "wanderlog_premium", price $2.99).
 *  5. In RevenueCat → Products, add that product. Then create an Entitlement
 *     called "premium" and attach the product. Create an Offering (the default
 *     "current" offering) containing a package that points to the product.
 *
 * Until GOOGLE_API_KEY is filled in, BillingManager falls back to a local
 * unlock flag so the app stays testable without a RevenueCat account.
 *
 * NOTE: Google Play Billing only works for an app uploaded to Play Console
 * (at least an internal-testing track) and signed with the matching key.
 * It will NOT complete a real purchase from a plain sideloaded debug APK.
 */
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
