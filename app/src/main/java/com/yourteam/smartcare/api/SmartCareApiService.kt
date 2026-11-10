package com.yourteam.smartcare.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface SmartCareApiService {
    // This perfectly matches the @app.route("/analyze", methods=["POST"]) in your Python app.py
    @Multipart
    @POST("analyze")
    suspend fun uploadPillImage(
        @Part image: MultipartBody.Part
    ): Response<ScanResponse>
}