# Macaco — Login: Remove Apple Sign-In

Apple Sign-In is built but has near-zero real usage on Android. This brief removes it cleanly
from the interface, the interface implementation, the ViewModel, the data model, and all string
resources. Touches: `LoginScreen.kt`, `AuthRepository.kt`, `FirebaseAuthRepository.kt`,
`MockAuthRepository.kt`, `JournalViewModel.kt`, `UserProfile.kt`, `ProfileScreen.kt`,
`FirebaseConfig.kt`, and `strings.xml` ×11 languages.

---

## 1. Remove the Apple button from LoginScreen

**Problem:** The login screen shows a "Continue with Apple" button that is unused by virtually
all Android users and adds visual clutter below the Google button.

**Fix:** Delete the entire Apple `Button` composable (lines ~227–248) and remove the
`signInWithApple` call. The Google button and the `— or —` divider leading to email/password
remain untouched.

**Before (layout):**
```
┌─────────────────────────────┐
│  [G] Continue with Google   │
├─────────────────────────────┤
│  [🍎] Continue with Apple   │  ← remove this entire block
├─────────────────────────────┤
│  ─────────── or ──────────  │
│  [email / password fields]  │
└─────────────────────────────┘
```

**After (layout):**
```
┌─────────────────────────────┐
│  [G] Continue with Google   │
├─────────────────────────────┤
│  ─────────── or ──────────  │
│  [email / password fields]  │
└─────────────────────────────┘
```

```kotlin
// LoginScreen.kt — DELETE this entire Button composable:

Button(
    onClick = {
        isLoading = true
        errorMessage = null
        viewModel.signInWithApple(context) { result ->
            isLoading = false
            result.fold(onSuccess = { onBack() }, onFailure = { errorMessage = it.message })
        }
    },
    modifier = Modifier
        .fillMaxWidth()
        .height(50.dp),
    shape = RoundedCornerShape(12.dp),
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface
    ),
    enabled = !isLoading
) {
    Text(stringResource(R.string.login_apple), fontWeight = FontWeight.Medium)
}
```

**File:** `ui/screens/LoginScreen.kt`

---

## 2. Remove signInWithApple from AuthRepository interface

**Problem:** The interface declares `signInWithApple` which all implementations must satisfy.

**Fix:** Delete the `signInWithApple` method from the interface.

```kotlin
// AuthRepository.kt — DELETE this line:
suspend fun signInWithApple(context: Context): Result<UserProfile>

// Also remove the unused Context import if no other method needs it:
// import android.content.Context
```

**File:** `data/auth/AuthRepository.kt`

---

## 3. Remove signInWithApple from FirebaseAuthRepository

**Problem:** The concrete implementation contains the full OAuthProvider flow for Apple.

**Fix:** Delete the Apple section (marked with `// ── Apple ──`) and remove the `AuthProvider.Apple`
branch in the provider detection logic at the bottom of the file.

```kotlin
// FirebaseAuthRepository.kt — DELETE the entire Apple block:

// ── Apple ─────────────────────────────────────────────────────────────────
override suspend fun signInWithApple(context: Context): Result<UserProfile> {
    val activity = context as? Activity
        ?: return Result.failure(Exception("Activity context required for Apple Sign-In"))
    // ... entire implementation ...
}

// Also in the provider detection (near the UserProfile mapping), DELETE:
providerData.any { it.providerId == "apple.com" } -> AuthProvider.Apple
```

The `when` branch for Apple in provider detection should fall through to the `else` branch
(which maps unknown providers to `AuthProvider.Email` or `AuthProvider.Guest` — check the
existing else and keep it intact).

**File:** `data/auth/FirebaseAuthRepository.kt`

---

## 4. Remove signInWithApple from MockAuthRepository

**Problem:** The mock also implements `signInWithApple`.

**Fix:** Delete the override.

```kotlin
// MockAuthRepository.kt — DELETE:
override suspend fun signInWithApple(context: Context): Result<UserProfile> {
    val user = UserProfile(uid = "mock_apple", displayName = "Wanderer", email = "wanderer@icloud.com", provider = AuthProvider.Apple)
    // ...
}
```

**File:** `data/auth/MockAuthRepository.kt`

---

## 5. Remove signInWithApple from JournalViewModel

**Problem:** The ViewModel exposes a `signInWithApple` wrapper called from `LoginScreen`.

**Fix:** Delete the function.

```kotlin
// JournalViewModel.kt — DELETE:
fun signInWithApple(context: Context, onResult: (Result<UserProfile>) -> Unit) {
    viewModelScope.launch { onResult(authRepository.signInWithApple(context)) }
}
```

**File:** `ui/viewmodel/JournalViewModel.kt`

---

## 6. Remove Apple from AuthProvider enum

**Problem:** `UserProfile.kt` declares `enum class AuthProvider { Google, Apple, Email, Guest }`.
`Apple` will be unused after the above changes.

**Fix:** Remove the `Apple` variant.

```kotlin
// UserProfile.kt — BEFORE:
enum class AuthProvider { Google, Apple, Email, Guest }

// AFTER:
enum class AuthProvider { Google, Email, Guest }
```

**File:** `data/model/UserProfile.kt`

---

## 7. Remove Apple branch from ProfileScreen

**Problem:** `ProfileScreen` has a `when` branch that shows "Apple Account" for Apple users.

**Fix:** Delete the `AuthProvider.Apple` branch. The `else` branch already handles unknown cases.

```kotlin
// ProfileScreen.kt — DELETE:
AuthProvider.Apple -> stringResource(R.string.profile_apple_account)
```

**File:** `ui/screens/ProfileScreen.kt`

---

## 8. Update FirebaseConfig comments

**Problem:** `FirebaseConfig.kt` has setup comments mentioning Apple Sign-In configuration steps.

**Fix:** Delete the two Apple-related comment lines:

```
// DELETE:
// •  For Apple Sign-In: requires an Apple Developer account and Service ID
//    configured in both Firebase Console and Apple Developer Portal

// Also remove "and Apple" from the Enable line:
// BEFORE: •  Enable Email/Password, Google, and Apple in Firebase Console →
// AFTER:  •  Enable Email/Password and Google in Firebase Console →
```

**File:** `data/auth/FirebaseConfig.kt`

---

## 9. Remove Apple string keys from all 11 string files

**Problem:** Two string keys reference Apple and will be unused after the above changes,
causing lint warnings.

**Fix:** Delete both entries from all 11 `strings.xml` files.

| Key | EN value |
|-----|----------|
| `login_apple` | `Continue with Apple` |
| `profile_apple_account` | `Apple Account` |

Files to update (delete the matching `<string>` element from each):

- `values/strings.xml` (EN — base)
- `values-de/strings.xml`
- `values-es/strings.xml`
- `values-fr/strings.xml`
- `values-it/strings.xml`
- `values-ja/strings.xml`
- `values-nl/strings.xml`
- `values-pl/strings.xml`
- `values-pt/strings.xml`
- `values-sv/strings.xml`
- `values-zh-rCN/strings.xml`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove Apple `Button` composable | `ui/screens/LoginScreen.kt` |
| 2 | Remove `signInWithApple` from interface | `data/auth/AuthRepository.kt` |
| 3 | Remove `signInWithApple` impl + Apple provider detection | `data/auth/FirebaseAuthRepository.kt` |
| 4 | Remove `signInWithApple` stub | `data/auth/MockAuthRepository.kt` |
| 5 | Remove `signInWithApple` ViewModel wrapper | `ui/viewmodel/JournalViewModel.kt` |
| 6 | Remove `Apple` from `AuthProvider` enum | `data/model/UserProfile.kt` |
| 7 | Remove Apple `when` branch | `ui/screens/ProfileScreen.kt` |
| 8 | Remove Apple setup comments | `data/auth/FirebaseConfig.kt` |
| 9 | Delete `login_apple` + `profile_apple_account` strings | `values*/strings.xml` ×11 |
