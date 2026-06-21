# Macaco — FirebaseAuthRepository: Refresh Display Name on Launch

Firebase caches the user's display name at sign-in and never updates it automatically.
If the user changes their Google Account name, the old name persists until `reload()` is
called. One-line fix: call `reload()` once at repository init so the name is always current.
Touches only `data/auth/FirebaseAuthRepository.kt`.

---

## Change: call reload() at init

**Problem:** `addAuthStateListener` fires on sign-in/sign-out only — not on profile changes.
`currentUser.displayName` stays stale if the user renames their Google Account between sessions.

**Fix:** After registering the auth listener in `init { }`, launch a coroutine that calls
`auth.currentUser?.reload()` and then manually pushes the refreshed profile into `_currentUser`.
The `addAuthStateListener` does not fire after a reload, so the manual update is required.

Find the `init` block in `FirebaseAuthRepository.kt`:

```kotlin
// CURRENT:
init {
    _currentUser.value = auth.currentUser?.toUserProfile()
    auth.addAuthStateListener { _currentUser.value = it.currentUser?.toUserProfile() }
}

// AFTER:
init {
    _currentUser.value = auth.currentUser?.toUserProfile()
    auth.addAuthStateListener { _currentUser.value = it.currentUser?.toUserProfile() }
    // Reload once at startup to pick up any Google Account name changes since last sign-in.
    // reload() refreshes the FirebaseUser object in-place but does not trigger the auth
    // listener, so _currentUser must be updated manually afterwards.
    repositoryScope.launch {
        runCatching { auth.currentUser?.reload()?.await() }
        auth.currentUser?.let { _currentUser.value = it.toUserProfile() }
    }
}
```

`repositoryScope` is the existing `CoroutineScope` already declared in this class (check the
class body — it is used elsewhere for other background work). If the scope is named differently,
use that name. If no scope exists, create one:

```kotlin
private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```

No new imports needed — `reload()`, `await()`, and `runCatching` are already used elsewhere
in this file.

---

## Scope notes

- `runCatching` swallows network errors silently — if the device is offline, the cached name
  is used as-is. This is the correct behaviour; no error surfacing needed.
- This fires once per process start, not on every screen open. The overhead is one lightweight
  Firebase network call.
- The fix covers Google Sign-In users only. Email/password users set their display name
  explicitly via `updateProfile()` so it can't drift.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `reload()` + manual `_currentUser` update in `init { }` | `data/auth/FirebaseAuthRepository.kt` |
