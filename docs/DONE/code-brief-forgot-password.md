# Macaco — Login Screen: Forgot Password

Adds a "Forgot password?" flow to `LoginScreen.kt`, backed by a new `sendPasswordResetEmail`
method in `AuthRepository` / `FirebaseAuthRepository` and a matching ViewModel wrapper.

---

## 1. Add `sendPasswordResetEmail` to `AuthRepository`

**Problem:** No way to trigger a Firebase password reset — users who forget their email/password
credentials are locked out permanently.

**Fix:** Add the method to the interface and implement it in `FirebaseAuthRepository`. Firebase's
`sendPasswordResetEmail` deliberately returns success even when no account exists for the address
(to prevent email enumeration), so the UI should reflect that with hedged language.

```kotlin
// BEFORE — AuthRepository.kt (line 20, after deleteAccount)
    suspend fun deleteAccount(): Result<Unit>
}

// AFTER
    suspend fun deleteAccount(): Result<Unit>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
}
```

**File:** `data/auth/AuthRepository.kt`

---

## 2. Implement in `FirebaseAuthRepository`

**Fix:** Add the implementation after `createAccount`, before `signOut`. Map
`FirebaseAuthInvalidUserException` — even though Firebase won't throw it here for enumeration
safety, handle it defensively.

```kotlin
// BEFORE — FirebaseAuthRepository.kt (line 90, the signOut block)
    // ── Sign Out ──────────────────────────────────────────────────────────────

// AFTER — insert before signOut
    // ── Password Reset ────────────────────────────────────────────────────────

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        runCatching {
            auth.sendPasswordResetEmail(email.trim()).await()
        }.mapFailure { e ->
            Exception(e.localizedMessage ?: "Could not send reset email")
        }

    // ── Sign Out ──────────────────────────────────────────────────────────────
```

**File:** `data/auth/FirebaseAuthRepository.kt`

---

## 3. Add ViewModel wrapper

**Fix:** Follow the same callback pattern as `signInWithEmail` and `createAccount`.

```kotlin
// BEFORE — JournalViewModel.kt (line 272, after createAccount)
    fun createAccount(email: String, password: String, displayName: String, onResult: (Result<UserProfile>) -> Unit) {
        viewModelScope.launch { onResult(authRepository.createAccount(email, password, displayName)) }
    }

// AFTER
    fun createAccount(email: String, password: String, displayName: String, onResult: (Result<UserProfile>) -> Unit) {
        viewModelScope.launch { onResult(authRepository.createAccount(email, password, displayName)) }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(authRepository.sendPasswordResetEmail(email)) }
    }
```

**File:** `ui/viewmodel/JournalViewModel.kt`

---

## 4. Add "Forgot password?" UI to `LoginScreen`

**Problem:** No password recovery entry point on the login form.

**Fix:** Add two new state variables and a `TextButton` + inline confirmation banner. The button
appears only in sign-in mode (not account creation). Tapping it uses the email already in the
field — if the field is blank it shows a prompt to fill it first. On success it shows a
`primaryContainer` banner with hedged confirmation text. The banner is separate from
`errorMessage` so typing in the email field doesn't dismiss it.

Layout (sign-in mode only):

```
┌──────────────────────────────────────────┐
│  [Email field]                           │
│  [Password field]                        │
│                      [Forgot password?]  │  ← right-aligned TextButton, small
│  [Sign In button]                        │
│  [No account? Create one]                │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │ ✓ If an account exists for this  │    │  ← primaryContainer banner, shown on success
│  │   email, a reset link has been   │    │
│  │   sent to foo@bar.com            │    │
│  └──────────────────────────────────┘    │
└──────────────────────────────────────────┘
```

```kotlin
// BEFORE — LoginScreen.kt state variables (lines 93–99)
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isCreatingAccount by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

// AFTER — add two new state variables
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isCreatingAccount by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var resetEmailSent by remember { mutableStateOf(false) }
    var resetSentTo by remember { mutableStateOf("") }
```

```kotlin
// BEFORE — LoginScreen.kt (line 308, closing brace of password OutlinedTextField)
                )

                Button(

// AFTER — insert "Forgot password?" button between password field and Sign In button
                )

                // "Forgot password?" — only shown in sign-in mode, not account creation
                if (!isCreatingAccount) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = {
                                if (email.isBlank()) {
                                    errorMessage = context.getString(R.string.login_forgot_enter_email)
                                } else {
                                    isLoading = true
                                    errorMessage = null
                                    viewModel.sendPasswordResetEmail(email.trim()) { result ->
                                        isLoading = false
                                        result.fold(
                                            onSuccess = {
                                                resetSentTo = email.trim()
                                                resetEmailSent = true
                                            },
                                            onFailure = { errorMessage = it.message }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterEnd),
                            enabled = !isLoading
                        ) {
                            Text(
                                stringResource(R.string.login_forgot_password),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Reset-email confirmation banner — shown after successful send
                if (resetEmailSent) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            stringResource(R.string.login_forgot_sent, resetSentTo),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Button(
```

Also clear `resetEmailSent` when the user toggles to account creation mode — add to the
`isCreatingAccount` toggle button's `onClick`:

```kotlin
// BEFORE — LoginScreen.kt (line 351)
                TextButton(
                    onClick = { isCreatingAccount = !isCreatingAccount; errorMessage = null },

// AFTER
                TextButton(
                    onClick = {
                        isCreatingAccount = !isCreatingAccount
                        errorMessage = null
                        resetEmailSent = false
                    },
```

**File:** `ui/screens/LoginScreen.kt`

---

## 5. New string keys

Add to `strings.xml` ×11 languages:

| Key | EN value |
|-----|----------|
| `login_forgot_password` | Forgot password? |
| `login_forgot_enter_email` | Enter your email address above first |
| `login_forgot_sent` | If an account exists for %1$s, a reset link has been sent |

The `%1$s` placeholder in `login_forgot_sent` receives the email address at runtime via
`stringResource(R.string.login_forgot_sent, resetSentTo)`.

**File:** `res/values/strings.xml` + all 10 translation files

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `sendPasswordResetEmail` to interface | `data/auth/AuthRepository.kt` |
| 2 | Implement `sendPasswordResetEmail` via Firebase | `data/auth/FirebaseAuthRepository.kt` |
| 3 | Add ViewModel wrapper (callback pattern) | `ui/viewmodel/JournalViewModel.kt` |
| 4 | Add "Forgot password?" button + confirmation banner | `ui/screens/LoginScreen.kt` |
| 5 | Add 3 new string keys ×11 languages | `res/values/strings.xml` + translations |
