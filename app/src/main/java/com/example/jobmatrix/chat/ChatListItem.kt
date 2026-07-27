package com.example.jobmatrix.chat

import com.example.jobmatrix.model.ChatMessage

sealed class ChatListItem {
    data class Header(val label: String) : ChatListItem()
    data class MessageItem(val message: ChatMessage) : ChatListItem()
}