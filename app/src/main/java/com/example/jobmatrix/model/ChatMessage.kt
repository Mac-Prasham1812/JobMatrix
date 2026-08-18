package com.example.jobmatrix.model

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderRole: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val edited: Boolean = false,
    val isDeleted: Boolean = false,
    val isRead: Boolean = false,
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSender: String = "",
    val deletedFor: List<String> = emptyList(),
    val attachmentKey: String = "",
    val attachmentUrl: String = "",
    val attachmentType: String = "", // "image" or "file"
    val attachmentName: String = "",
    val attachmentSize: Long = 0L
)