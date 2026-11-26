package com.example.data.local

import com.example.model.User

data class UserEntity(
    val id: Int,
    val username: String,
    val email: String,
    val name: String = "",
    val avatarUrl: String? = null,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    fun toUser(): User {
        return User(
            id = id,
            username = username,
            email = email,
            name = name.ifBlank { username },
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() }
        )
    }

    companion object {
        fun fromUser(user: User, lastSyncedAt: Long = System.currentTimeMillis()): UserEntity {
            return UserEntity(
                id = user.id,
                username = user.username,
                email = user.email,
                name = user.name,
                avatarUrl = user.avatarUrl,
                lastSyncedAt = lastSyncedAt
            )
        }
    }
}
