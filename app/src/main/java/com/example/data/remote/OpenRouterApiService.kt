package com.example.data.remote

import com.example.data.model.ChatRequest
import com.example.data.model.ChatResponse
import com.example.data.model.ModelsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApiService {

    @GET("api/v1/models")
    suspend fun getModels(): Response<ModelsResponse>

    @POST("api/v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://aicouncil.app",
        @Header("X-Title") title: String = "AI Council",
        @Body request: ChatRequest
    ): Response<ChatResponse>
}
