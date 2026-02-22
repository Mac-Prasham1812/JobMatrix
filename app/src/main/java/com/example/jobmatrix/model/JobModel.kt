package com.example.jobmatrix.model

import com.google.firebase.Timestamp

data class JobModel(
    val jobId: String = "",
    val title: String = "",
    val company: String = "",
    val location: String = "",
    val category: String = "",
    val salary: String = "",
    val experience: String = "",
    val employerId: String = "",
    val companyOverview: String = "",
    val status: String = "Active",
    val createdAt: Any? = null
)


