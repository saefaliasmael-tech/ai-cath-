package com.example.data.provider

import com.example.domain.model.AiModel
import com.example.domain.model.ChatMessage

interface AiProvider {
    suspend fun fetchModels(apiKey: String): Result<List<AiModel>>
    suspend fun chatCompletion(
        modelId: String,
        messages: List<ChatMessage>,
        apiKey: String
    ): Result<String>
}
