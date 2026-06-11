package com.houseofmmminq.macaco.data.model

data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val provider: AuthProvider = AuthProvider.Guest,
    val createdAt: Long? = null
)

enum class AuthProvider { Google, Apple, Email, Guest }
