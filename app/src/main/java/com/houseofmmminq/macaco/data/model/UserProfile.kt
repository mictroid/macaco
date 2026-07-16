package com.houseofmmminq.macaco.data.model

data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val provider: AuthProvider = AuthProvider.Guest,
    val createdAt: Long? = null,
    val emailVerified: Boolean = true
)

enum class AuthProvider { Google, Email, Guest }
