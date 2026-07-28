package com.example.jobmatrix.student

import com.example.jobmatrix.model.NotificationModel

sealed class NotificationListItem {
    data class Header(val label: String) : NotificationListItem()
    data class Item(val notification: NotificationModel) : NotificationListItem()
}