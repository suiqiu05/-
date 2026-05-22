package com.example.ln1

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiService {
    @FormUrlEncoded
    @POST("api/register")
    suspend fun register(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<ApiResponse>

    @FormUrlEncoded
    @POST("api/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    @Multipart
    @POST("api/send")
    suspend fun sendMail(
        @Part("userId") userId: RequestBody,
        @Part("receiver") receiver: RequestBody,
        @Part("subject") subject: RequestBody,
        @Part("content") content: RequestBody,
        @Part attachments: List<MultipartBody.Part>
    ): Response<ApiResponse>

    @FormUrlEncoded
    @POST("api/send")
    suspend fun sendMailSimple(
        @Field("userId") userId: String,
        @Field("receiver") receiver: String,
        @Field("subject") subject: String,
        @Field("content") content: String
    ): Response<ApiResponse>

    @GET("api/mails")
    suspend fun getMails(@Query("userId") username: String): Response<MailListResponse>

    @FormUrlEncoded
    @POST("api/read")
    suspend fun markAsRead(@Field("mailId") mailId: String): Response<ApiResponse>

    @FormUrlEncoded
    @POST("api/delete")
    suspend fun deleteMail(@Field("mailId") mailId: String): Response<ApiResponse>

    @GET("api/search")
    suspend fun searchMail(
        @Query("userId") username: String,
        @Query("keyword") keyword: String
    ): Response<MailListResponse>
}