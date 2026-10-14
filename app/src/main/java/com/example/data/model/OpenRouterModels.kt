package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ModelsResponse(
    @Json(name = "data") val data: List<ModelDto>?
)

@JsonClass(generateAdapter = true)
data class ModelDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String?,
    @Json(name = "description") val description: String?,
    @Json(name = "pricing") val pricing: PricingDto?
)

@JsonClass(generateAdapter = true)
data class PricingDto(
    @Json(name = "prompt") val prompt: String?,
    @Json(name = "completion") val completion: String?,
    @Json(name = "request") val request: String?,
    @Json(name = "image") val image: String?
)

@JsonClass(generateAdapter = true)
data class ChatRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<MessageDto>
)

@JsonClass(generateAdapter = true)
data class MessageDto(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class ChatResponse(
    @Json(name = "choices") val choices: List<ChoiceDto>?,
    @Json(name = "error") val error: ErrorDto?
)

@JsonClass(generateAdapter = true)
data class ChoiceDto(
    @Json(name = "message") val message: MessageDto?
)

@JsonClass(generateAdapter = true)
data class ErrorDto(
    @Json(name = "message") val message: String?,
    @Json(name = "code") val code: Int?
)
