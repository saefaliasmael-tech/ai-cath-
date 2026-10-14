package com.example.data.provider

import com.example.data.model.ChatRequest
import com.example.data.model.MessageDto
import com.example.data.remote.OpenRouterApiService
import com.example.domain.model.AiModel
import com.example.domain.model.ChatMessage
import com.example.domain.model.ProviderException
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenRouterAiProvider(
    private val apiService: OpenRouterApiService? = null
) : AiProvider {

    private val service: OpenRouterApiService by lazy {
        apiService ?: run {
            val logging = HttpLoggingInterceptor { message ->
                // Safe logging: disabled body logging to protect privacy and secrets
                android.util.Log.d("OpenRouterHttp", "Network request executed")
            }.apply {
                level = HttpLoggingInterceptor.Level.NONE
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            Retrofit.Builder()
                .baseUrl("https://openrouter.ai/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(OpenRouterApiService::class.java)
        }
    }

    override suspend fun fetchModels(apiKey: String): Result<List<AiModel>> {
        return try {
            val response = service.getModels()
            if (response.isSuccessful) {
                val body = response.body()
                val models = body?.data?.map { dto ->
                    val promptPrice = dto.pricing?.prompt?.toDoubleOrNull() ?: 1.0
                    val completionPrice = dto.pricing?.completion?.toDoubleOrNull() ?: 1.0
                    val isFree = (promptPrice == 0.0 && completionPrice == 0.0) ||
                            dto.id.contains("free", ignoreCase = true)

                    AiModel(
                        id = dto.id,
                        name = dto.name ?: dto.id,
                        isFree = isFree,
                        description = dto.description
                    )
                } ?: emptyList()
                Result.success(models)
            } else {
                val code = response.code()
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                val exception = when (code) {
                    401 -> ProviderException.AuthenticationException("مفتاح API غير صالح (401)")
                    429 -> ProviderException.RateLimitException(null, "تجاوز معدل الطلبات (429)")
                    else -> ProviderException.UnknownException("خطأ في جلب النماذج: $code - $errorBody", null)
                }
                Result.failure(exception)
            }
        } catch (e: IOException) {
            Result.failure(ProviderException.NetworkException("خطأ في الاتصال بالشبكة", e))
        } catch (e: Exception) {
            Result.failure(ProviderException.UnknownException("حدث خطأ غير متوقع: ${e.localizedMessage}", e))
        }
    }

    override suspend fun chatCompletion(
        modelId: String,
        messages: List<ChatMessage>,
        apiKey: String
    ): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(ProviderException.AuthenticationException("مفتاح OpenRouter API مفقود"))
        }

        return try {
            val messageDtos = messages.map { MessageDto(role = it.role, content = it.content) }
            val request = ChatRequest(model = modelId, messages = messageDtos)
            val authHeader = "Bearer $apiKey"

            val response = service.chatCompletion(authorization = authHeader, request = request)

            if (response.isSuccessful) {
                val body = response.body()
                val choice = body?.choices?.firstOrNull()
                val content = choice?.message?.content
                if (!content.isNullOrBlank()) {
                    Result.success(content)
                } else {
                    Result.failure(ProviderException.UnknownException("استجابة فارغة من النموذج", null))
                }
            } else {
                val code = response.code()
                val retryAfterHeader = response.headers()["Retry-After"]
                val retryAfterSeconds = retryAfterHeader?.toLongOrNull()

                val exception = when (code) {
                    401, 403 -> ProviderException.AuthenticationException("خطأ في مصادقة مفتاح API ($code)")
                    429 -> ProviderException.RateLimitException(retryAfterSeconds, "تجاوز حد الاستخدام (429). الرجاء الانتظار قليلاً.")
                    402 -> ProviderException.QuotaExceededException("رصيد الحساب نفد (402). يرجى التحقق من الرصيد.")
                    404 -> ProviderException.ModelUnavailableException("النموذج المطلوب غير متوفر ($modelId)")
                    else -> {
                        val err = response.errorBody()?.string() ?: "خطأ غير معروف"
                        ProviderException.UnknownException("خطأ API ($code): $err", null)
                    }
                }
                Result.failure(exception)
            }
        } catch (e: IOException) {
            Result.failure(ProviderException.NetworkException("فشل الاتصال بالشبكة", e))
        } catch (e: Exception) {
            Result.failure(ProviderException.UnknownException("خطأ غير متوقع: ${e.localizedMessage}", e))
        }
    }
}
