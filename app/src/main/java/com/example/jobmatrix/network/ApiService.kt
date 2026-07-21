package com.example.jobmatrix.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Part
import retrofit2.http.Body

interface ApiService {
    @Multipart
    @POST("upload-resume")
    suspend fun uploadResume(
        @Header("Authorization") token: String,
        @Part resume: MultipartBody.Part
    ): Response<UploadResponse>

    @GET("resume/{key}")
    suspend fun getResumeUrl(
        @Header("Authorization") token: String,
        @Path("key") key: String
    ): Response<ResumeUrlResponse>

    @POST("send-notification")
    suspend fun sendNotification(@Body body: NotifyRequest): Response<Unit>

}

data class UploadResponse(val key: String, val url: String)
data class ResumeUrlResponse(val url: String)
data class NotifyRequest(val token: String, val title: String, val body: String)