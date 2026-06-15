package com.studyapp.model

data class ChatMessage(
    val id: Int,
    val conversationId: Int,
    val senderId: Int,
    val content: String,
    val createdAt: String,
    val isRead: Boolean
)
