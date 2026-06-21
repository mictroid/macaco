# Macaco — AppLockScreen: Biometrics Not Detected on API < 30 (Galaxy S8)

`isBiometricAvailable()` and `showBiometricPrompt()` both use `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`
combined. This combination is only valid on API 30+. On API 28 (Galaxy S8's ceiling),
`canAuthenticate` returns `BIOMETRIC_ERROR_UNSUPPORTED` even when a fingerprint is enrolled,
so the toggle is permanently blocked. Fix: branch on API level in both functions.

---

## 1. Fix isBiometricAvailable()

**Problem:** Current code:

```kotlin
fun isBiometricAvailable(context: android.content.Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
```

`BIOMETRIC_STRONG or DEVICE_CREDENTIAL` combined is unsupported below API 30 — the call returns
`BIOMETRIC_ERROR_UNSUPPORTED` on Galaxy S8 (API 26–28) even if fingerprint is enrolled.

**Fix:** Branch on API level.

**File:** `ui/screens/AppLockScreen.kt`

Replace the single-line function:

```kotlin
fun isBiometricAvailable(context: android.content.Context): Boolean {
    val manager = BiometricManager.from(context)
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        // API 30+: BIOMETRIC_STRONG or DEVICE_CREDENTIAL is valid as a combined mask.
        manager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    } else {
        // API < 30: combining the two masks is unsupported; check for strong biometric only.
        manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
```

No new imports needed — `Build` is in `android.os`.

---

## 2. Fix showBiometricPrompt()

**Problem:** Current code sets `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`,
which throws on API < 30. The prompt never shows on Galaxy S8.

```kotlin
val info = BiometricPrompt.PromptInfo.Builder()
    .setTitle(context.getString(R.string.app_lock_biometric_title))
    .setSubtitle(context.getString(R.string.app_lock_biometric_subtitle))
    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)  // ← crashes API < 30
    .build()
```

**Fix:** On API < 30, use `BIOMETRIC_STRONG` only with a `setNegativeButtonText` cancel button
(required by the library when `DEVICE_CREDENTIAL` is excluded). Device credential fallback is
dropped on older Android — fingerprint is sufficient for Galaxy S8.

**File:** `ui/screens/AppLockScreen.kt`

Replace the `info` builder block inside `showBiometricPrompt()`:

```kotlin
val info = BiometricPrompt.PromptInfo.Builder()
    .setTitle(context.getString(R.string.app_lock_biometric_title))
    .setSubtitle(context.getString(R.string.app_lock_biometric_subtitle))
    .apply {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // API 30+: allow fingerprint OR device PIN/pattern as fallback.
            setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        } else {
            // API < 30: fingerprint only; negative button is mandatory.
            setAllowedAuthenticators(BIOMETRIC_STRONG)
            setNegativeButtonText(context.getString(R.string.common_cancel))
        }
    }
    .build()
```

No new imports needed.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Branch `isBiometricAvailable()` on `Build.VERSION.SDK_INT >= R` | `ui/screens/AppLockScreen.kt` |
| 2 | Branch `showBiometricPrompt()` prompt builder on API level; add `setNegativeButtonText` for API < 30 | `ui/screens/AppLockScreen.kt` |
