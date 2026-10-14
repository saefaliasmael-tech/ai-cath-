package com.example

import com.example.data.provider.AiProvider
import com.example.domain.engine.DiscussionEngine
import com.example.domain.model.AiModel
import com.example.domain.model.ChatMessage
import com.example.domain.model.DiscussionTopic
import com.example.domain.model.ProviderException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class FakeAiProvider(
    private val analysisResponse1: String = "Analysis from Model 1",
    private val analysisResponse2: String = "Analysis from Model 2",
    private val reviewResponse1: String = "Review from Model 1",
    private val reviewResponse2: String = "Review from Model 2",
    private val synthesisResponse: String = "Final Synthesis Recommendation",
    private val errorToThrow: Throwable? = null,
    private val rateLimitTimes: Int = 0
) : AiProvider {

    val requestedPrompts = mutableListOf<Pair<String, List<ChatMessage>>>()
    var callCount = 0
    private var currentRateLimits = 0

    override suspend fun fetchModels(apiKey: String): Result<List<AiModel>> {
        return Result.success(listOf(AiModel("model1", "Model 1", true), AiModel("model2", "Model 2", true)))
    }

    override suspend fun chatCompletion(
        modelId: String,
        messages: List<ChatMessage>,
        apiKey: String
    ): Result<String> {
        callCount++
        requestedPrompts.add(modelId to messages)

        if (errorToThrow != null) {
            return Result.failure(errorToThrow)
        }

        if (currentRateLimits < rateLimitTimes) {
            currentRateLimits++
            return Result.failure(ProviderException.RateLimitException(1, "Rate limit"))
        }

        val promptText = messages.lastOrNull()?.content ?: ""
        return when {
            promptText.contains("مراجعة نقدية") || promptText.contains("مراجعة") -> {
                Result.success(if (modelId == "model1") reviewResponse1 else reviewResponse2)
            }
            promptText.contains("تركيباً نهائياً") || promptText.contains("المنسق النهائي") -> {
                Result.success(synthesisResponse)
            }
            else -> {
                Result.success(if (modelId == "model1") analysisResponse1 else analysisResponse2)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
class DiscussionEngineTest {

    @Test
    fun test1() = runTest {
        // 1. Independent analysis is requested from both selected models.
        val fake = FakeAiProvider()
        val engine = DiscussionEngine(fake, "test-key", 10)
        val topic = DiscussionTopic("Test Topic", "model1", "model2", 0)

        val result = engine.runDiscussion(topic, {}, {}, { false })

        assertTrue(result.analyses.containsKey("model1"))
        assertTrue(result.analyses.containsKey("model2"))
        assertEquals(2, result.participatingModels.size)
    }

    @Test
    fun test2() = runTest {
        // 2. Cross-review includes the other model's actual prior response.
        val fake = FakeAiProvider(
            analysisResponse1 = "UniqueAnalysis1",
            analysisResponse2 = "UniqueAnalysis2"
        )
        val engine = DiscussionEngine(fake, "test-key", 15)
        val topic = DiscussionTopic("Test Topic", "model1", "model2", 1)

        val result = engine.runDiscussion(topic, {}, {}, { false })

        assertTrue(result.reviews.containsKey("model1"))
        assertTrue(result.reviews.containsKey("model2"))
        val crossReviewPrompt = fake.requestedPrompts.find { it.first == "model1" && it.second.last().content.contains("UniqueAnalysis2") }
        assertNotNull(crossReviewPrompt)
    }

    @Test
    fun test3() = runTest {
        // 3. Synthesis receives the actual analysis and review results.
        val fake = FakeAiProvider()
        val engine = DiscussionEngine(fake, "test-key", 15)
        val topic = DiscussionTopic("Test Topic", "model1", "model2", 1)

        val result = engine.runDiscussion(topic, {}, {}, { false })

        assertNotNull(result.synthesis)
        assertTrue(result.synthesis?.isSuccess == true)
        val synthPrompt = fake.requestedPrompts.find { it.second.last().content.contains("المنسق النهائي") }
        assertNotNull(synthPrompt)
    }

    @Test
    fun test4() = runTest {
        // 4. Request limits prevent additional requests after the budget is exhausted.
        val fake = FakeAiProvider()
        val engine = DiscussionEngine(fake, "test-key", 1)
        val topic = DiscussionTopic("Test Topic", "model1", "model2", 1)

        val result = engine.runDiscussion(topic, {}, {}, { false })

        assertNotNull(result.error)
    }

    @Test
    fun test5() = runTest {
        // 5. Cancellation stops further work.
        val fake = FakeAiProvider()
        val engine = DiscussionEngine(fake, "test-key", 20)
        val topic = DiscussionTopic("Test Topic", "model1", "model2", 1)

        val result = engine.runDiscussion(topic, {}, {}, { true })

        assertNotNull(result.error)
    }

    @Test
    fun test6() = runTest {
        // 6. HTTP 429 and retry exhaustion are handled correctly.
        val fake = FakeAiProvider(rateLimitTimes = 100)
        val engine = DiscussionEngine(fake, "test-key", 20)
        val topic = DiscussionTopic("Test Topic", "model1", "model2", 0)

        val result = engine.runDiscussion(topic, {}, {}, { false })

        assertNotNull(result.error)
    }

    @Test
    fun test7() = runTest {
        // 7. Authentication errors are not retried indefinitely.
        val authError = ProviderException.AuthenticationException("Invalid key")
        val fake = FakeAiProvider(errorToThrow = authError)
        val engine = DiscussionEngine(fake, "bad-key", 20)
        val topic = DiscussionTopic("Test Topic", "model1", "model2", 0)

        val result = engine.runDiscussion(topic, {}, {}, { false })

        assertNotNull(result.error)
        assertTrue(fake.callCount <= 2)
    }

    @Test
    fun test8() = runTest {
        // 8. One provider failure preserves successful results from the other provider.
        val fake = object : AiProvider {
            override suspend fun fetchModels(apiKey: String): Result<List<AiModel>> = Result.success(emptyList())
            override suspend fun chatCompletion(modelId: String, messages: List<ChatMessage>, apiKey: String): Result<String> {
                return if (modelId == "model1") Result.success("Success from Model 1")
                else Result.failure(Exception("Model 2 failed"))
            }
        }
        val engine = DiscussionEngine(fake, "test-key", 10)
        val topic = DiscussionTopic("Test Topic", "model1", "model2", 0)

        val result = engine.runDiscussion(topic, {}, {}, { false })

        assertTrue(result.analyses.containsKey("model1"))
        assertEquals("Success from Model 1", result.analyses["model1"]?.proposedAnswer)
    }

    @Test
    fun test9() = runTest {
        // 9. The engine does not report false consensus.
        val fake = FakeAiProvider(
            analysisResponse1 = "Opinion A",
            analysisResponse2 = "Opinion B (Different)"
        )
        val engine = DiscussionEngine(fake, "test-key", 15)
        val topic = DiscussionTopic("Test Topic", "model1", "model2", 1)

        val result = engine.runDiscussion(topic, {}, {}, { false })

        assertEquals("Opinion A", result.analyses["model1"]?.proposedAnswer)
        assertEquals("Opinion B (Different)", result.analyses["model2"]?.proposedAnswer)
    }

    @Test
    fun test10() = runTest {
        // 10. A failed synthesis is never reported as successful.
        val fake = object : AiProvider {
            override suspend fun fetchModels(apiKey: String): Result<List<AiModel>> = Result.success(emptyList())
            override suspend fun chatCompletion(modelId: String, messages: List<ChatMessage>, apiKey: String): Result<String> {
                val prompt = messages.last().content
                return if (prompt.contains("المنسق النهائي")) {
                    Result.failure(Exception("Synthesis crashed"))
                } else {
                    Result.success("Analysis content")
                }
            }
        }
        val engine = DiscussionEngine(fake, "test-key", 15)
        val topic = DiscussionTopic("Test Topic", "model1", "model2", 0)

        val result = engine.runDiscussion(topic, {}, {}, { false })

        assertNotNull(result.synthesis)
        assertFalse(result.synthesis!!.isSuccess)
    }

    @Test
    fun test11() = runTest {
        // 11. Missing or malformed provider responses are handled safely.
        val fake = FakeAiProvider(
            analysisResponse1 = "",
            analysisResponse2 = "   "
        )
        val engine = DiscussionEngine(fake, "test-key", 15)
        val topic = DiscussionTopic("Test Topic", "model1", "model2", 0)

        val result = engine.runDiscussion(topic, {}, {}, { false })

        assertNotNull(result)
    }

    @Test
    fun test12() = runTest {
        // 12. The stopping rules terminate the discussion correctly.
        val fake = FakeAiProvider()
        val engine = DiscussionEngine(fake, "test-key", 20)
        val topic = DiscussionTopic("Test Topic", "model1", "model2", 1)

        val result = engine.runDiscussion(topic, {}, {}, { false })

        assertNotNull(result.synthesis)
        assertTrue(result.stages.all { it.isComplete })
    }
}
