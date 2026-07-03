package com.houseofmmminq.macaco.data.auth

import com.houseofmmminq.macaco.data.model.AuthProvider
import com.houseofmmminq.macaco.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MockAuthRepository : AuthRepository {
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    override val currentUser: StateFlow<UserProfile?> = _currentUser

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<UserProfile> {
        val user = UserProfile(uid = "mock_google", displayName = "Wanderer", email = "wanderer@gmail.com", provider = AuthProvider.Google)
        _currentUser.value = user
        return Result.success(user)
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<UserProfile> {
        if (password.length < 6) return Result.failure(Exception("Password must be at least 6 characters"))
        val user = UserProfile(
            uid = "mock_${email.hashCode()}",
            displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
            email = email,
            provider = AuthProvider.Email
        )
        _currentUser.value = user
        return Result.success(user)
    }

    override suspend fun createAccount(email: String, password: String, displayName: String): Result<UserProfile> {
        if (password.length < 6) return Result.failure(Exception("Password must be at least 6 characters"))
        val user = UserProfile(
            uid = "mock_new_${email.hashCode()}",
            displayName = displayName.ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } },
            email = email,
            provider = AuthProvider.Email
        )
        _currentUser.value = user
        return Result.success(user)
    }

    override suspend fun signOut() { _currentUser.value = null }

    override suspend fun deleteAccount(password: String?): Result<Unit> {
        _currentUser.value = null
        return Result.success(Unit)
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = Result.success(Unit)
}
