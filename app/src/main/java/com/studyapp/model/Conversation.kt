package com.studyapp.model

data class Conversation(
    val id: Int,
    val otherUserId: Int,
    val otherUsername: String,
    val otherUserRole: String,
    val otherUserAvatarUrl: String? = null,
    val lastMessage: String,
    val lastMessageAt: String,
    val unreadCount: Int
)
