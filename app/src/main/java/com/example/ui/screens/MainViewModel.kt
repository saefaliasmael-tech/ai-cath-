package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.provider.OpenRouterAiProvider
import com.example.data.secure.SecureStorage
import com.example.domain.engine.DiscussionEngine
import com.example.domain.model.AiModel
import com.example.domain.model.DiscussionResult
import com.example.domain.model.DiscussionStage
import com.example.domain.model.DiscussionTopic
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val secureStorage = SecureStorage(application)
    private val aiProvider = OpenRouterAiProvider()

    private val _apiKey = MutableStateFlow(secureStorage.getApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _models = MutableStateFlow<List<AiModel>>(emptyList())
    val models: StateFlow<List<AiModel>> = _models.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private val _selectedModel1 = MutableStateFlow("")
    val selectedModel1: StateFlow<String> = _selectedModel1.asStateFlow()

    private val _selectedModel2 = MutableStateFlow("")
    val selectedModel2: StateFlow<String> = _selectedModel2.asStateFlow()

    private val _maxRounds = MutableStateFlow(1)
    val maxRounds: StateFlow<Int> = _maxRounds.asStateFlow()

    private val _maxBudget = MutableStateFlow(10)
    val maxBudget: StateFlow<Int> = _maxBudget.asStateFlow()

    private val _discussionState = MutableStateFlow<DiscussionResult?>(null)
    val discussionState: StateFlow<DiscussionResult?> = _discussionState.asStateFlow()

    private val _isDiscussionRunning = MutableStateFlow(false)
    val isDiscussionRunning: StateFlow<Boolean> = _isDiscussionRunning.asStateFlow()

    private val _currentStage = MutableStateFlow<DiscussionStage?>(null)
    val currentStage: StateFlow<DiscussionStage?> = _currentStage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private var discussionJob: Job? = null
    private var cancelled = false

    init {
        if (_apiKey.value.isNotBlank()) {
            fetchModelCatalog()
        }
    }

    fun saveApiKey(key: String) {
        secureStorage.saveApiKey(key)
        _apiKey.value = key
        if (key.isNotBlank()) {
            fetchModelCatalog()
        }
    }

    fun clearApiKey() {
        secureStorage.clearApiKey()
        _apiKey.value = ""
        _models.value = emptyList()
    }

    fun setShowSettings(show: Boolean) {
        _showSettings.value = show
    }

    fun setSelectedModel1(id: String) {
        _selectedModel1.value = id
    }

    fun setSelectedModel2(id: String) {
        _selectedModel2.value = id
    }

    fun setMaxRounds(rounds: Int) {
        _maxRounds.value = rounds.coerceIn(1, 2)
    }

    fun setMaxBudget(budget: Int) {
        _maxBudget.value = budget.coerceIn(2, 20)
    }

    fun fetchModelCatalog() {
        val key = _apiKey.value
        if (key.isBlank()) {
            _errorMessage.value = "يرجى إدخال مفتاح OpenRouter API أولاً."
            return
        }
        viewModelScope.launch {
            _isFetchingModels.value = true
            _errorMessage.value = null
            val result = aiProvider.fetchModels(key)
            _isFetchingModels.value = false
            if (result.isSuccess) {
                val list = result.getOrThrow()
                _models.value = list
                // Auto-select first two models if not selected
                if (_selectedModel1.value.isBlank() && list.isNotEmpty()) {
                    _selectedModel1.value = list[0].id
                }
                if (_selectedModel2.value.isBlank() && list.size > 1) {
                    _selectedModel2.value = list[1].id
                }
            } else {
                _errorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "فشل جلب النماذج"
            }
        }
    }

    fun startDiscussion(topicText: String) {
        val key = _apiKey.value
        if (key.isBlank()) {
            _errorMessage.value = "يرجى إدخال مفتاح API في الإعدادات."
            _showSettings.value = true
            return
        }
        val m1 = _selectedModel1.value
        val m2 = _selectedModel2.value
        if (m1.isBlank() || m2.isBlank() || m1 == m2) {
            _errorMessage.value = "يرجى اختيار نموذجين مختلفين للمناقشة."
            return
        }
        if (topicText.isBlank()) {
            _errorMessage.value = "يرجى كتابة الموضوع أو السؤال المراد مناقشته."
            return
        }

        cancelled = false
        _errorMessage.value = null
        _discussionState.value = null
        _isDiscussionRunning.value = true
        _currentStage.value = null

        val topic = DiscussionTopic(
            topic = topicText,
            model1Id = m1,
            model2Id = m2,
            maxRounds = _maxRounds.value
        )

        val engine = DiscussionEngine(aiProvider, key, _maxBudget.value)

        discussionJob = viewModelScope.launch {
            val result = engine.runDiscussion(
                topic = topic,
                onStageUpdate = { stage ->
                    _currentStage.value = stage
                },
                onPartialResult = { partial ->
                    _discussionState.value = partial
                },
                isCancelled = { cancelled }
            )
            _discussionState.value = result
            _isDiscussionRunning.value = false
            if (result.error != null) {
                _errorMessage.value = result.error
            }
        }
    }

    fun cancelDiscussion() {
        cancelled = true
        discussionJob?.cancel()
        _isDiscussionRunning.value = false
        _errorMessage.value = "تم إيقاف المناقشة بواسطة المستخدم."
    }
}
