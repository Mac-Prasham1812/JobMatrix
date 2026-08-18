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
    suspend fun sendNotification(@Body body: NotifyRequest): Response<Map<String, Boolean>>

    @Multipart
    @POST("upload-chat-attachment")
    suspend fun uploadChatAttachment(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
        @Part("applicationId") applicationId: okhttp3.RequestBody
    ): Response<ChatUploadResponse>

    @GET("chat-attachment/{key}")
    suspend fun getChatAttachmentUrl(
        @Header("Authorization") token: String,
        @Path(value = "key", encoded = true) key: String
    ): Response<ResumeUrlResponse>

}

data class UploadResponse(
    val key: String,
    val url: String
)

data class ResumeUrlResponse(
    val url: String
)

data class NotifyRequest(
    val token: String,
    val title: String,
    val body: String
)

data class ChatUploadResponse(
    val key: String,
    val url: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String
)