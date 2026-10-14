package com.example.domain.model

data class AiModel(
    val id: String,
    val name: String,
    val isFree: Boolean,
    val description: String? = null
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class DiscussionTopic(
    val topic: String,
    val model1Id: String,
    val model2Id: String,
    val maxRounds: Int = 1
)

data class DiscussionStage(
    val name: String,
    val progress: Float, // 0.0 to 1.0
    val description: String,
    val isComplete: Boolean = false
)

data class ModelAnalysis(
    val modelId: String,
    val interpretation: String,
    val proposedAnswer: String,
    val reasoning: String,
    val assumptions: String,
    val risks: String
)

data class ModelCrossReview(
    val modelId: String,
    val reviewText: String,
    val revisedConclusion: String?
)

data class SynthesisResult(
    val recommendation: String,
    val strongestReasons: List<String>,
    val pointsOfAgreement: List<String>,
    val unresolvedDisagreements: List<String>,
    val risks: List<String>,
    val missingInfo: List<String>,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class DiscussionResult(
    val stages: List<DiscussionStage> = emptyList(),
    val analyses: Map<String, ModelAnalysis> = emptyMap(),
    val reviews: Map<String, List<ModelCrossReview>> = emptyMap(),
    val synthesis: SynthesisResult? = null,
    val error: String? = null,
    val participatingModels: List<String> = emptyList()
)

sealed class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class RateLimitException(val retryAfterSeconds: Long?, message: String) : ProviderException(message)
    class AuthenticationException(message: String) : ProviderException(message)
    class QuotaExceededException(message: String) : ProviderException(message)
    class ModelUnavailableException(message: String) : ProviderException(message)
    class NetworkException(message: String, cause: Throwable?) : ProviderException(message, cause)
    class UnknownException(message: String, cause: Throwable?) : ProviderException(message, cause)
}
