package com.example.jobmatrix.admin

data class JobAdminModel(
    val jobId: String,
    val title: String,
    val company: String,
    val category: String,
    var status: String
)
