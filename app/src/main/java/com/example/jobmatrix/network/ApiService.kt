package com.example.jobmatrix.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("upload-resume")
    suspend fun uploadResume(
        @Header("Authorization") token: String,
        @Part resume: MultipartBody.Part
    ): Response<UploadResponse>
}

data class UploadResponse(val key: String, val url: String)