package com.example.jobmatrix.model

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderRole: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)