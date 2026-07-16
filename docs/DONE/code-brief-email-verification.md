# Macaco — Auth: Require Email Verification on Email/Password Signup

Right now `createAccount()` in `FirebaseAuthRepository` creates a Firebase Auth user for any
syntactically-valid email with no ownership check, and `NavGraph` immediately grants full app
access. This brief adds a Firebase email-verification gate: after signup, the user must click
the link Firebase emails them before they can enter the journal. Google Sign-In users are
unaffected (Google already verifies their email). Touches `UserProfile.kt`, `AuthRepository.kt`,
`FirebaseAuthRepository.kt`, `MockAuthRepository.kt`, `JournalViewModel.kt`, `NavGraph.kt`,
`Screen.kt`, a new `VerifyEmailScreen.kt`, and `strings.xml`.

---

## 1. Track verification status on `UserProfile`

**Problem:** `UserProfile` has no way to express "signed in but unverified." `NavGraph` can only
branch on `currentUser == null`.

**Fix:** Add `emailVerified: Boolean = true`. Default `true` so Google accounts (always verified)
and any call site that doesn't care need no changes.

```kotlin
// BEFORE — UserProfile.kt
data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val provider: AuthProvider = AuthProvider.Guest,
    val createdAt: Long? = null
)

// AFTER
data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val provider: AuthProvider = AuthProvider.Guest,
    val createdAt: Long? = null,
    val emailVerified: Boolean = true
)
```

**File:** `data/model/UserProfile.kt`

---

## 2. Send the verification email on signup, expose verification actions

**Problem:** `createAccount()` never calls Firebase's `sendEmailVerification()`. There's also no
way to resend the email or refresh verification status once the user clicks the link.

**Fix:** In `FirebaseAuthRepository`:
- `createAccount()` sends the verification email right after account creation.
- `toUserProfile()` reads `isEmailVerified` for email accounts (Google is always `true`).
- Add `sendEmailVerification()` (resend) and `reloadAndCheckEmailVerified()` (called from the
  "I've verified" button — `reload()` doesn't trigger Firebase's auth listener, so the repo must
  push the refreshed value into `_currentUser` manually, same pattern already used in `init {}`).

```kotlin
// BEFORE — FirebaseAuthRepository.kt, createAccount()
override suspend fun createAccount(email: String, password: String, displayName: String): Result<UserProfile> =
    runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user!!
        val name = displayName.ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } }
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
        user.reload().await()
        user.toUserProfile()
    }.mapFailure { e ->
        when (e) {
            is FirebaseAuthWeakPasswordException -> Exception("Password must be at least 6 characters")
            is FirebaseAuthUserCollisionException -> Exception("An account already exists for this email")
            else -> Exception(e.localizedMessage ?: "Account creation failed")
        }
    }

// AFTER
override suspend fun createAccount(email: String, password: String, displayName: String): Result<UserProfile> =
    runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user!!
        val name = displayName.ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } }
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
        user.sendEmailVerification().await()
        user.reload().await()
        user.toUserProfile()
    }.mapFailure { e ->
        when (e) {
            is FirebaseAuthWeakPasswordException -> Exception("Password must be at least 6 characters")
            is FirebaseAuthUserCollisionException -> Exception("An account already exists for this email")
            else -> Exception(e.localizedMessage ?: "Account creation failed")
        }
    }

// NEW — add near sendPasswordResetEmail()
override suspend fun sendEmailVerification(): Result<Unit> = runCatching {
    val user = auth.currentUser ?: error("Not signed in")
    user.sendEmailVerification().await()
    Unit
}.mapFailure { e -> Exception(e.localizedMessage ?: "Could not send verification email") }

override suspend fun reloadAndCheckEmailVerified(): Result<Boolean> = runCatching {
    val user = auth.currentUser ?: error("Not signed in")
    user.reload().await()
    // reload() doesn't fire the auth listener — push the refreshed profile manually so
    // NavGraph's currentUser.emailVerified updates immediately.
    _currentUser.value = user.toUserProfile()
    user.isEmailVerified
}.mapFailure { e -> Exception(e.localizedMessage ?: "Could not check verification status") }
```

```kotlin
// BEFORE — toUserProfile()
private fun FirebaseUser.toUserProfile(): UserProfile {
    val provider = when {
        providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } -> AuthProvider.Google
        providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID } -> AuthProvider.Email
        else -> AuthProvider.Email
    }
    return UserProfile(
        uid = uid,
        displayName = displayName
            ?: email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "User",
        email = email ?: "",
        photoUrl = photoUrl?.toString(),
        provider = provider,
        createdAt = metadata?.creationTimestamp
    )
}

// AFTER
private fun FirebaseUser.toUserProfile(): UserProfile {
    val provider = when {
        providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } -> AuthProvider.Google
        providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID } -> AuthProvider.Email
        else -> AuthProvider.Email
    }
    return UserProfile(
        uid = uid,
        displayName = displayName
            ?: email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "User",
        email = email ?: "",
        photoUrl = photoUrl?.toString(),
        provider = provider,
        createdAt = metadata?.creationTimestamp,
        // Google accounts are pre-verified by Google; only email/password enforces the gate.
        emailVerified = provider == AuthProvider.Google || isEmailVerified
    )
}
```

**File:** `data/auth/FirebaseAuthRepository.kt`

---

## 3. Extend the `AuthRepository` interface and mock

```kotlin
// AuthRepository.kt — add alongside sendPasswordResetEmail()
/** Resends the verification email to the signed-in (unverified) user. */
suspend fun sendEmailVerification(): Result<Unit>

/** Reloads the current user from Firebase and returns the fresh isEmailVerified state. */
suspend fun reloadAndCheckEmailVerified(): Result<Boolean>
```

```kotlin
// MockAuthRepository.kt — email/password mock accounts start unverified so the new screen
// is exercisable without Firebase; Google mock stays verified.
override suspend fun createAccount(email: String, password: String, displayName: String): Result<UserProfile> {
    if (password.length < 6) return Result.failure(Exception("Password must be at least 6 characters"))
    val user = UserProfile(
        uid = "mock_new_${email.hashCode()}",
        displayName = displayName.ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } },
        email = email,
        provider = AuthProvider.Email,
        emailVerified = false
    )
    _currentUser.value = user
    return Result.success(user)
}

override suspend fun sendEmailVerification(): Result<Unit> = Result.success(Unit)

override suspend fun reloadAndCheckEmailVerified(): Result<Boolean> {
    // Mock: flip verified true on the second check so the "I've verified" flow is testable.
    val user = _currentUser.value ?: return Result.success(false)
    val updated = user.copy(emailVerified = true)
    _currentUser.value = updated
    return Result.success(true)
}
```

Also update the existing `signInWithEmail()` mock to keep `emailVerified = true` by default
(unchanged — the data class default already handles it) since sign-in of an already-created mock
account shouldn't re-block a session.

**Files:** `data/auth/AuthRepository.kt`, `data/auth/MockAuthRepository.kt`

---

## 4. `JournalViewModel` wrappers

```kotlin
// Add next to sendPasswordResetEmail()
fun sendEmailVerification(onResult: (Result<Unit>) -> Unit) {
    viewModelScope.launch { onResult(authRepository.sendEmailVerification()) }
}

fun reloadAndCheckEmailVerified(onResult: (Result<Boolean>) -> Unit) {
    viewModelScope.launch { onResult(authRepository.reloadAndCheckEmailVerified()) }
}
```

**File:** `ui/viewmodel/JournalViewModel.kt`

---

## 5. New `VerifyEmailScreen`

**Problem:** No screen exists to hold an unverified user before they reach the journal.

**Fix:** New composable, styled like `LoginScreen`'s branded header (reuse `macacoBrandBackground()`
from `LoginScreen.kt` — it's file-private in the same package, so either make it internal or
duplicate the small gradient Box). Shows the pending email, a "Resend email" button (disabled
for a short cooldown after tapping), a primary "I've verified my email" button that calls
`reloadAndCheckEmailVerified` and shows an error if still unverified, and a "Sign out" text
button (uses a different email / made a typo).

```
┌─────────────────────────────┐
│      [macaco header]        │
│                              │
│   Verify your email          │
│   We sent a link to          │
│   you@example.com            │
│                              │
│  [ I've verified — Continue ]│
│  [       Resend email       ]│
│         Sign out             │
└─────────────────────────────┘
```

```kotlin
package com.houseofmmminq.macaco.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import kotlinx.coroutines.delay

@Composable
fun VerifyEmailScreen(
    viewModel: JournalViewModel,
    onSignOut: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    var isChecking by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var resendCooldown by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown--
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Filled.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.verify_email_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.verify_email_subtitle, currentUser?.email ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            if (errorMessage != null) {
                Text(
                    errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }
            if (infoMessage != null) {
                Text(
                    infoMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    isChecking = true
                    errorMessage = null
                    viewModel.reloadAndCheckEmailVerified { result ->
                        isChecking = false
                        result.fold(
                            onSuccess = { verified ->
                                if (!verified) {
                                    errorMessage =
                                        it.getString(R.string.verify_email_still_unverified)
                                }
                            },
                            onFailure = { errorMessage = it.message }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.verify_email_continue))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    isResending = true
                    viewModel.sendEmailVerification { result ->
                        isResending = false
                        result.fold(
                            onSuccess = {
                                infoMessage = null
                                resendCooldown = 30
                            },
                            onFailure = { errorMessage = it.message }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isResending && resendCooldown == 0
            ) {
                Text(
                    if (resendCooldown > 0)
                        stringResource(R.string.verify_email_resend_cooldown, resendCooldown)
                    else stringResource(R.string.verify_email_resend)
                )
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { viewModel.signOut(); onSignOut() }) {
                Text(stringResource(R.string.verify_email_sign_out))
            }
        }
    }
}
```

> Note: the snippet references `viewModel.signOut()` — confirm the existing sign-out entry point
> on `JournalViewModel` (likely already wraps `authRepository.signOut()`) and call that, not
> `authRepository` directly from the screen.

**File:** new `ui/screens/VerifyEmailScreen.kt`

---

## 6. Gate `NavGraph` on `emailVerified`

**Problem:** `NavGraph` treats any non-null `currentUser` as fully authenticated.

**Fix:** Insert the verification check between the "not logged in" and "full journal" branches.
Google accounts always have `emailVerified = true` so they fall through untouched.

```kotlin
// BEFORE — NavGraph.kt, the `when` block
when {
    isPurchased == null -> { /* ... */ }

    currentUser == null -> {
        LoginScreen(viewModel = viewModel, onBack = {})
    }

    else -> {
        // full journal
    }
}

// AFTER
when {
    isPurchased == null -> { /* ... */ }

    currentUser == null -> {
        LoginScreen(viewModel = viewModel, onBack = {})
    }

    currentUser?.emailVerified == false -> {
        VerifyEmailScreen(
            viewModel = viewModel,
            onSignOut = {} // currentUser becomes null → this branch exits automatically
        )
    }

    else -> {
        // full journal
    }
}
```

Add the import:

```kotlin
import com.houseofmmminq.macaco.ui.screens.VerifyEmailScreen
```

**File:** `ui/navigation/NavGraph.kt`

---

## 7. Strings

| Key | EN value |
|-----|----------|
| `verify_email_title` | Verify your email |
| `verify_email_subtitle` | We sent a verification link to %1$s. Click it, then come back here. |
| `verify_email_continue` | I've verified — Continue |
| `verify_email_still_unverified` | Not verified yet — check your inbox (and spam folder) |
| `verify_email_resend` | Resend email |
| `verify_email_resend_cooldown` | Resend email (%1$d s) |
| `verify_email_sign_out` | Sign out |

All 11 supported languages need these keys added to their respective `strings.xml`.

---

## Out of scope

- Disposable-email-domain blocklisting and App Check bot mitigation — worth a follow-up brief if
  fake signups remain a problem after this ships, but not needed for the core verification gate.
- Rate-limiting resend taps beyond the client-side 30s cooldown above — Firebase already
  rate-limits `sendEmailVerification()` server-side, so this is just UX polish, not a security
  control.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `emailVerified` field | `data/model/UserProfile.kt` |
| 2 | Send verification email on signup, add resend/reload methods, map `isEmailVerified` | `data/auth/FirebaseAuthRepository.kt` |
| 3 | Extend interface + mock | `data/auth/AuthRepository.kt`, `data/auth/MockAuthRepository.kt` |
| 4 | Expose verification actions | `ui/viewmodel/JournalViewModel.kt` |
| 5 | New verification-pending screen | `ui/screens/VerifyEmailScreen.kt` (new) |
| 6 | Gate journal access on `emailVerified` | `ui/navigation/NavGraph.kt` |
| 7 | New string keys ×11 languages | `res/values*/strings.xml` |
