package com.example.jobmatrix.admin

data class EmployerModel(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val companyName: String = "",
    val role: String = "Employer"
)
