package com.example.jobmatrix.model

data class ApplicationModel(
    val applicationId: String = "",
    val jobId: String = "",
    val jobTitle: String = "",
    val companyName: String = "",
    val studentId: String = "",
    val resumeLink: String = "",
    val status: String = "Applied",
    val appliedAt: Long = 0L,
    val hasNotification: Boolean = false,
    val isRead: Boolean = false
)



