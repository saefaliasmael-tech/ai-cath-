package com.example.domain.engine

import com.example.data.provider.AiProvider
import com.example.domain.model.ChatMessage
import com.example.domain.model.DiscussionResult
import com.example.domain.model.DiscussionStage
import com.example.domain.model.DiscussionTopic
import com.example.domain.model.ModelAnalysis
import com.example.domain.model.ModelCrossReview
import com.example.domain.model.ProviderException
import com.example.domain.model.SynthesisResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

class DiscussionEngine(
    private val aiProvider: AiProvider,
    private val apiKey: String,
    private val maxRequestBudget: Int = 10
) {

    suspend fun runDiscussion(
        topic: DiscussionTopic,
        onStageUpdate: (DiscussionStage) -> Unit,
        onPartialResult: (DiscussionResult) -> Unit,
        isCancelled: () -> Boolean
    ): DiscussionResult {
        val model1 = topic.model1Id
        val model2 = topic.model2Id
        val participatingModels = listOf(model1, model2)
        var requestCount = 0

        fun checkBudget() {
            if (requestCount >= maxRequestBudget) {
                throw IllegalAccessException("تم تجاوز حد الطلبات الأقصى المسموح به ($maxRequestBudget).")
            }
        }

        suspend fun executeWithRetry(modelId: String, messages: List<ChatMessage>): Result<String> {
            var attempts = 0
            val maxRetries = 2
            var lastException: Throwable? = null

            while (attempts <= maxRetries && !isCancelled()) {
                checkBudget()
                requestCount++

                val result = aiProvider.chatCompletion(modelId, messages, apiKey)
                if (result.isSuccess) {
                    return result
                } else {
                    val error = result.exceptionOrNull()
                    lastException = error

                    if (error is ProviderException.AuthenticationException ||
                        error is ProviderException.QuotaExceededException
                    ) {
                        // Do not retry auth or quota errors
                        return result
                    }

                    if (error is ProviderException.RateLimitException) {
                        attempts++
                        if (attempts <= maxRetries) {
                            val baseDelay = (2.0.pow(attempts.toDouble()) * 1000).toLong()
                            val jitter = Random.nextLong(0, 500)
                            val waitTime = (error.retryAfterSeconds?.times(1000) ?: (baseDelay + jitter)).coerceAtMost(10000)
                            delay(waitTime)
                            continue
                        }
                    } else if (error is ProviderException.NetworkException) {
                        attempts++
                        if (attempts <= maxRetries) {
                            delay(1500L * attempts)
                            continue
                        }
                    } else {
                        // Other errors: try once more if attempts remain
                        attempts++
                        if (attempts <= maxRetries) {
                            delay(1000L)
                            continue
                        }
                    }
                }
            }
            return Result.failure(lastException ?: Exception("فشل الطلب بعد إعادة المحاولة"))
        }

        val stages = mutableListOf<DiscussionStage>()
        val analyses = mutableMapOf<String, ModelAnalysis>()
        val reviews = mutableMapOf<String, MutableList<ModelCrossReview>>()

        try {
            // Stage A: Independent Analysis
            stages.add(DiscussionStage("التحليل المستقل", 0.2f, "جاري جمع التحليلات المستقلة من النماذج...", false))
            onStageUpdate(stages.last())
            onPartialResult(DiscussionResult(stages = stages.toList(), participatingModels = participatingModels))

            if (isCancelled()) return DiscussionResult(error = "تم الإلغاء بواسطة المستخدم", participatingModels = participatingModels)

            val promptA = buildString {
                append("أنت نموذج ذكاء اصطناعي في مجلس AI Council. يرجى تحليل الموضوع التالي بشكل مستقل ودقيق.\n")
                append("الموضوع: ${topic.topic}\n\n")
                append("يرجى هيكلة ردك بالنقاط التالية بوضوح:\n")
                append("1. التفسير: تفسيرك للمشكلة أو الموضوع.\n")
                append("2. الحل المقترح: مقترحك المفصل للحل.\n")
                append("3. التبرير: الأسباب والمنطق الداعم.\n")
                append("4. الافتراضات: الافتراضات الكامنة خلف تحليلك.\n")
                append("5. المخاطر والضعف: المخاطر ونقاط الضعف والمعلومات المفقودة.")
            }

            val messagesA = listOf(ChatMessage("user", promptA))

            val result1 = executeWithRetry(model1, messagesA)
            val result2 = executeWithRetry(model2, messagesA)

            if (result1.isSuccess) {
                val content = result1.getOrThrow()
                analyses[model1] = parseAnalysis(model1, content)
            }
            if (result2.isSuccess) {
                val content = result2.getOrThrow()
                analyses[model2] = parseAnalysis(model2, content)
            }

            if (analyses.isEmpty()) {
                return DiscussionResult(
                    stages = stages,
                    error = "فشل النماذج في تقديم التحليل المستقل.",
                    participatingModels = participatingModels
                )
            }

            stages[0] = stages[0].copy(progress = 1.0f, description = "تم إكمال التحليل المستقل بنجاح", isComplete = true)
            onStageUpdate(stages[0])
            onPartialResult(DiscussionResult(stages = stages.toList(), analyses = analyses.toMap(), participatingModels = participatingModels))

            if (isCancelled()) return DiscussionResult(stages = stages, analyses = analyses, error = "تم الإلغاء", participatingModels = participatingModels)

            // Stage B: Cross-Review (for configured rounds)
            val rounds = topic.maxRounds.coerceIn(1, 2)
            for (round in 1..rounds) {
                stages.add(DiscussionStage("المرحلة ب: المراجعة المتبادلة (دورة $round)", 0.5f + (0.2f * round), "جاري تبادل المراجعات النقدية بين النماذج...", false))
                onStageUpdate(stages.last())

                val currentAnalyses = analyses.toMap()
                val newReviewsForRound = mutableMapOf<String, ModelCrossReview>()

                for (modelId in participatingModels) {
                    if (isCancelled()) break
                    val otherModelId = participatingModels.firstOrNull { it != modelId } ?: continue
                    val otherAnalysis = currentAnalyses[otherModelId] ?: continue
                    val myAnalysis = currentAnalyses[modelId]

                    val promptB = buildString {
                        append("أنت مشارك في مجلس AI Council. إليك تحليل زميلك ($otherModelId):\n")
                        append("التفسير: ${otherAnalysis.interpretation}\n")
                        append("الحل المقترح: ${otherAnalysis.proposedAnswer}\n")
                        append("المنطق: ${otherAnalysis.reasoning}\n")
                        append("المخاطر: ${otherAnalysis.risks}\n\n")
                        if (myAnalysis != null) {
                            append("وكان تحليلك السابق:\n${myAnalysis.proposedAnswer}\n\n")
                        }
                        append("يرجى تقديم مراجعة نقدية تتضمن:\n")
                        append("1. تحديد نقاط الضعف والافتراضات غير المدعومة في رد الزميل.\n")
                        append("2. تحدي التفكير غير الكامل.\n")
                        append("3. اقتراح تحسينات ملموسة.\n")
                        append("4. مراجعة استنتاجك الخاص إذا قدم الزميل حجة أقوى.")
                    }

                    val reviewResult = executeWithRetry(modelId, listOf(ChatMessage("user", promptB)))
                    if (reviewResult.isSuccess) {
                        val reviewContent = reviewResult.getOrThrow()
                        val reviewObj = ModelCrossReview(modelId, reviewContent, null)
                        newReviewsForRound[modelId] = reviewObj
                        reviews.getOrPut(modelId) { mutableListOf() }.add(reviewObj)
                    }
                }

                stages[stages.size - 1] = stages[stages.size - 1].copy(progress = 1.0f, description = "تم إكمال المراجعة المتبادلة دورة $round", isComplete = true)
                onStageUpdate(stages.last())
                onPartialResult(DiscussionResult(stages = stages.toList(), analyses = analyses.toMap(), reviews = reviews.mapValues { it.value.toList() }, participatingModels = participatingModels))
            }

            if (isCancelled()) return DiscussionResult(stages = stages, analyses = analyses, reviews = reviews.mapValues { it.value.toList() }, error = "تم الإلغاء", participatingModels = participatingModels)

            // Stage C: Final Synthesis
            stages.add(DiscussionStage("المرحلة ج: التركيب النهائي", 0.9f, "جاري استخلاص التركيب النهائي للحل...", false))
            onStageUpdate(stages.last())

            val synthesisPrompt = buildString {
                append("أنت المنسق النهائي لمجلس الذكاء الاصطناعي (AI Council). الموضوع الأصلي: ${topic.topic}\n\n")
                append("نتائج التحليلات والمراجعات:\n")
                analyses.forEach { (id, ana) ->
                    append("- نموذج $id:\n  * الحل المقترح: ${ana.proposedAnswer}\n  * المنطق: ${ana.reasoning}\n")
                }
                reviews.forEach { (id, revList) ->
                    revList.forEach { rev ->
                        append("- مراجعة من $id: ${rev.reviewText.take(300)}...\n")
                    }
                }
                append("\nبناءً على ذلك، قدم تركيباً نهائياً يتضمن:\n")
                append("1. التوصية النهائية أو الحل الأفضل.\n")
                append("2. أقوى الأسباب الداعمة.\n")
                append("3. نقاط الاتفاق الحقيقية بين النماذج.\n")
                append("4. الخلافات أو الاختلافات غير الحلولة.\n")
                append("5. المخاطر والافتراضات الهامة.\n")
                append("6. معلومات مفقودة قد تغير النتيجة.")
            }

            // Use model1 or a synthesis prompt on model1 to generate synthesis
            val synthesisModel = participatingModels.first()
            val synthResult = executeWithRetry(synthesisModel, listOf(ChatMessage("user", synthesisPrompt)))

            val synthesis: SynthesisResult = if (synthResult.isSuccess) {
                parseSynthesis(synthResult.getOrThrow(), true)
            } else {
                SynthesisResult(
                    recommendation = "تعذر إنتاج التركيب الآلي الكامل، لكن النتائج الفردية متوفرة أدناه.",
                    strongestReasons = emptyList(),
                    pointsOfAgreement = emptyList(),
                    unresolvedDisagreements = emptyList(),
                    risks = emptyList(),
                    missingInfo = emptyList(),
                    isSuccess = false,
                    errorMessage = synthResult.exceptionOrNull()?.localizedMessage ?: "فشل طلب التركيب"
                )
            }

            stages[stages.size - 1] = stages[stages.size - 1].copy(progress = 1.0f, description = "اكتملت المناقشة بنجاح", isComplete = true)
            onStageUpdate(stages.last())

            val finalResult = DiscussionResult(
                stages = stages.toList(),
                analyses = analyses.toMap(),
                reviews = reviews.mapValues { it.value.toList() },
                synthesis = synthesis,
                participatingModels = participatingModels
            )
            onPartialResult(finalResult)
            return finalResult

        } catch (e: Exception) {
            if (e is CancellationException || isCancelled()) {
                return DiscussionResult(stages = stages, analyses = analyses, reviews = reviews.mapValues { it.value.toList() }, error = "تم إيقاف المناقشة", participatingModels = participatingModels)
            }
            return DiscussionResult(
                stages = stages,
                analyses = analyses,
                reviews = reviews.mapValues { it.value.toList() },
                error = e.localizedMessage ?: "حدث خطأ غير معروف أثناء المناقشة",
                participatingModels = participatingModels
            )
        }
    }

    private fun parseAnalysis(modelId: String, content: String): ModelAnalysis {
        return ModelAnalysis(
            modelId = modelId,
            interpretation = content,
            proposedAnswer = content,
            reasoning = content,
            assumptions = "",
            risks = ""
        )
    }

    private fun parseSynthesis(content: String, isSuccess: Boolean): SynthesisResult {
        return SynthesisResult(
            recommendation = content,
            strongestReasons = emptyList(),
            pointsOfAgreement = emptyList(),
            unresolvedDisagreements = emptyList(),
            risks = emptyList(),
            missingInfo = emptyList(),
            isSuccess = isSuccess
        )
    }
}
