package com.example.jobmatrix.admin

data class ApplicationAdminModel(
    val applicationId: String,
    val studentId: String,
    val jobId: String,
    val jobTitle: String,
    val companyName: String,
    val resumeLink: String,
    val status: String
)
