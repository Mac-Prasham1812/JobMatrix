package com.example.jobmatrix.model

data class NotificationModel(
    val notificationId: String = "",
    val studentId: String = "",
    val applicationId: String = "",
    val jobTitle: String = "",
    val companyName: String = "",
    val message: String = "",
    val type: String = "Info",
    val createdAt: Long = 0L,
    val isRead: Boolean = false
)