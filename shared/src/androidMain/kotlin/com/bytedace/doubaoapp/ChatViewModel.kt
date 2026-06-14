package com.bytedace.doubaoapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytedace.doubaoapp.data.model.ChatMessage
import com.bytedace.doubaoapp.data.model.ChatUiState
import com.bytedace.doubaoapp.data.model.MessageRole
import com.bytedace.doubaoapp.data.model.Session
import com.bytedace.doubaoapp.data.provider.LlmProvider
import com.bytedace.doubaoapp.platform.AppServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val appServices: AppServices,
) : ViewModel() {

    private val contextPolicy = appServices.contextPolicy
    private val repository = appServices.chatRepository

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var sessionId: String = UUID.randomUUID().toString()

    init {
        observeModelSwitch()
        loadHistoryMessages()
    }

    private fun observeModelSwitch() {
        viewModelScope.launch {
            appServices.currentProviderKeyFlow.collect {
                val provider = appServices.currentLlmProvider
                _uiState.update { state ->
                    state.copy(
                        currentModelName = provider.modelName(),
                        currentModelSource = provider.modelSource(),
                        selectedImageUris = if (provider.modelSource() == "local") emptyList() else state.selectedImageUris,
                        webSearchEnabled = if (provider.modelSource() == "local") false else state.webSearchEnabled,
                        imageGenEnabled = if (provider.modelSource() == "local") false else state.imageGenEnabled,
                    )
                }
            }
        }
    }

    private fun currentLlmProvider(): LlmProvider = appServices.currentLlmProvider

    private fun isLocalModelActive(): Boolean = currentLlmProvider().modelSource() == "local"

    private fun showToast(message: String) = appServices.showToast(message)

    /** 指定 sessionId 加载已有会话（用于会话切换） */
    fun loadSession(sid: String) {
        sessionId = sid
        appServices.onChatSessionChanged()
        _uiState.update { it.copy(messages = emptyList()) }
        loadHistoryMessages()
    }

    /** 创建新会话 */
    fun createNewSession() {
        sessionId = UUID.randomUUID().toString()
        appServices.onChatSessionChanged()
        _uiState.update { it.copy(messages = emptyList(), inputText = "") }
    }

    private fun loadHistoryMessages() {
        viewModelScope.launch {
            repository.getMessagesBySession(sessionId).collect { messages ->
                // 只在首次加载时替换（避免流式更新时的冲突）
                if (_uiState.value.messages.isEmpty()) {
                    _uiState.update { it.copy(messages = messages) }
                }
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    // ---- 模式切换 ----

    fun toggleWebSearch() {
        if (isLocalModelActive()) return
        _uiState.update { it.copy(webSearchEnabled = !it.webSearchEnabled) }
    }

    fun toggleImageGen() {
        if (isLocalModelActive()) return
        _uiState.update { it.copy(imageGenEnabled = !it.imageGenEnabled) }
    }

    // ---- 图片选择 ----

    fun onImageSelected(uris: List<String>) {
        if (isLocalModelActive()) return
        _uiState.update { it.copy(selectedImageUris = it.selectedImageUris + uris) }
    }

    fun removeImage(uri: String) {
        _uiState.update { it.copy(selectedImageUris = it.selectedImageUris - uri) }
    }

    // ---- 发送消息 ----

    fun sendMessage() {
        if (_uiState.value.isLoading) return

        val text = _uiState.value.inputText.trim()
        val activeProvider = currentLlmProvider()
        val isLocalModel = activeProvider.modelSource() == "local"

        if (!isLocalModel && !appServices.currentCredentials().isChatReady()
            && !(_uiState.value.imageGenEnabled && text.isNotEmpty())
        ) {
            showToast("请先在设置中配置 Ark API Key 与文本对话接入点")
            return
        }

        // 图片生成模式：直接调用图片生成
        if (!isLocalModel && _uiState.value.imageGenEnabled && text.isNotEmpty()) {
            generateImage(text)
            return
        }

        if (text.isEmpty() && (isLocalModel || _uiState.value.selectedImageUris.isEmpty())) return

        viewModelScope.launch {
            val imageUris = mutableListOf<String>()
            val selectedImages = if (isLocalModel) emptyList() else _uiState.value.selectedImageUris
            for (imageRef in selectedImages) {
                try {
                    val dataUri = appServices.encodeImageToDataUri(imageRef)
                    imageUris.add(dataUri)
                } catch (_: Exception) {
                }
            }

            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.USER,
                content = text.ifEmpty { "[图片]" },
                timestamp = System.currentTimeMillis(),
                imageUris = imageUris,
                sessionId = sessionId,
            )

            val aiMessageId = UUID.randomUUID().toString()
            val aiMessage = ChatMessage(
                id = aiMessageId,
                role = MessageRole.ASSISTANT,
                content = "",
                timestamp = System.currentTimeMillis(),
                isStreaming = true,
                usedWebSearch = !isLocalModel && _uiState.value.webSearchEnabled,
                sessionId = sessionId,
            )

            _uiState.update { state ->
                state.copy(
                    messages = state.messages + userMessage + aiMessage,
                    inputText = "",
                    selectedImageUris = emptyList(),
                    isLoading = true,
                )
            }

            repository.insertMessage(userMessage)
            repository.insertSession(
                Session(
                    id = sessionId,
                    title = text.take(20).ifEmpty { "图片对话" },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )

            val enableWebSearch = !isLocalModel && _uiState.value.webSearchEnabled
            try {
                val currentMessages = _uiState.value.messages.filter { !it.isStreaming }
                activeProvider.chatStream(currentMessages, contextPolicy, enableWebSearch)
                    .collect { token ->
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { msg ->
                                    if (msg.id == aiMessageId) msg.copy(content = msg.content + token) else msg
                                },
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                msg.copy(
                                    content = msg.content.ifEmpty { "请求出错：${e.message}" },
                                    isStreaming = false,
                                )
                            } else msg
                        },
                        isLoading = false,
                    )
                }
                val finalMsg = _uiState.value.messages.find { it.id == aiMessageId }
                if (finalMsg != null) {
                    repository.insertMessage(finalMsg)
                }
                return@launch
            }

            _uiState.update { state ->
                val updatedMessages = state.messages.map { msg ->
                    if (msg.id == aiMessageId) msg.copy(isStreaming = false) else msg
                }
                state.copy(messages = updatedMessages, isLoading = false)
            }
            val finalMsg = _uiState.value.messages.find { it.id == aiMessageId }
            if (finalMsg != null) {
                repository.insertMessage(finalMsg)
            }

            generateFollowUpSuggestions(aiMessageId)
        }
    }

    // ---- 追问建议 ----

    /** 根据AI消息内容生成追问建议 */
    private fun generateFollowUpSuggestions(aiMessageId: String) {
        val aiMsg = _uiState.value.messages.find { it.id == aiMessageId }
        if (aiMsg == null || aiMsg.content.isEmpty()) return

        // 简单策略：基于消息内容关键词生成预设模板
        val suggestions = mutableListOf<String>()
        val content = aiMsg.content

        suggestions.add("详细解释一下")
        if (content.length > 100) {
            suggestions.add("简单总结一下")
        }
        suggestions.add("举个例子说明")
        suggestions.add("换个角度解释")

        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == aiMessageId) msg.copy(followUpSuggestions = suggestions.take(3)) else msg
                }
            )
        }
    }

    /** 点击追问建议，填入输入框并发送 */
    fun followUp(suggestion: String) {
        _uiState.update { it.copy(inputText = suggestion) }
        sendMessage()
    }

    // ---- 互动操作 ----

    fun copyMessage(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        appServices.copyToClipboard(message.content)
    }

    fun saveGeneratedImage(imageUrl: String) {
        viewModelScope.launch {
            val result = appServices.saveImageFromUrl(imageUrl)
            val message = if (result.isSuccess) {
                "图片已保存到相册"
            } else {
                "保存失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
            }
            showToast(message)
        }
    }

    fun likeMessage(messageId: String) {
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == messageId) msg.copy(isLiked = !msg.isLiked, isDisliked = false) else msg
            }
            state.copy(messages = updated)
        }
        // 持久化
        viewModelScope.launch {
            _uiState.value.messages.find { it.id == messageId }?.let { repository.updateMessage(it) }
        }
    }

    fun dislikeMessage(messageId: String) {
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == messageId) msg.copy(isDisliked = !msg.isDisliked, isLiked = false) else msg
            }
            state.copy(messages = updated)
        }
        viewModelScope.launch {
            _uiState.value.messages.find { it.id == messageId }?.let { repository.updateMessage(it) }
        }
    }

    fun retryMessage(messageId: String) {
        val messages = _uiState.value.messages
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 1) return
        val userMessage = messages[index - 1]
        if (userMessage.role != MessageRole.USER) return

        // 删除旧 AI 消息
        _uiState.update { state ->
            state.copy(messages = state.messages.filterNot { it.id == messageId })
        }
        viewModelScope.launch { repository.deleteMessage(messageId) }

        val newAiId = UUID.randomUUID().toString()
        val newAiMessage = ChatMessage(
            id = newAiId,
            role = MessageRole.ASSISTANT,
            content = "",
            timestamp = System.currentTimeMillis(),
            isStreaming = true,
            usedWebSearch = _uiState.value.webSearchEnabled,
            sessionId = sessionId
        )

        _uiState.update { state ->
            state.copy(messages = state.messages + newAiMessage, isLoading = true)
        }

        viewModelScope.launch {
            try {
                val currentMessages = _uiState.value.messages.filter { !it.isStreaming }
                val provider = currentLlmProvider()
                val enableWebSearch = provider.modelSource() != "local" && _uiState.value.webSearchEnabled
                provider.chatStream(currentMessages, contextPolicy, enableWebSearch)
                    .collect { token ->
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { msg ->
                                    if (msg.id == newAiId) msg.copy(content = msg.content + token) else msg
                                }
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == newAiId) {
                                msg.copy(content = msg.content.ifEmpty { "请求出错：${e.message}" }, isStreaming = false)
                            } else msg
                        },
                        isLoading = false
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    messages = state.messages.map { msg ->
                        if (msg.id == newAiId) msg.copy(isStreaming = false) else msg
                    },
                    isLoading = false
                )
            }
            val finalMsg = _uiState.value.messages.find { it.id == newAiId }
            if (finalMsg != null) repository.insertMessage(finalMsg)

            // 生成追问建议
            generateFollowUpSuggestions(newAiId)
        }
    }

    // ---- 语音播报 ----

    fun playAudio(messageId: String) {
        if (isLocalModelActive()) return
        if (!appServices.currentCredentials().isTalkReady()) {
            showToast("请先在设置中配置 Talk API Key")
            return
        }
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        if (message.content.isEmpty()) return
        _uiState.update { it.copy(playingMessageId = messageId) }

        val ttsProvider = appServices.ttsProvider
        viewModelScope.launch {
            try {
                ttsProvider.synthesize(message.content).collect { _ ->
                    // VolcTtsProvider 内部管理 AudioPlayer 播放
                }
            } catch (e: Exception) {
                // 播报失败，静默恢复状态
            }
            _uiState.update { it.copy(playingMessageId = null) }
        }
    }

    fun stopAudio() {
        val ttsProvider = appServices.ttsProvider
        ttsProvider.stop()
        _uiState.update { it.copy(playingMessageId = null) }
    }

    // ---- 语音模式 ----

    private var speechJob: kotlinx.coroutines.Job? = null
    private var voiceMessageSent = false

    /** 聊天模式 ASR 识别 → 文字输入 */
    fun startVoiceRecognition() {
        if (isLocalModelActive()) return
        if (!appServices.currentCredentials().isTalkReady()) {
            showToast("请先在设置中配置 Talk API Key")
            return
        }
        if (_uiState.value.isRecognizing) return
        voiceMessageSent = false
        _uiState.update { it.copy(isRecognizing = true) }
        val speechProvider = appServices.speechProvider
        speechJob = viewModelScope.launch {
            try {
                speechProvider.startRecognition().collect { result ->
                    // ASR 流式返回的是「累计全量文本」，需整体替换而非追加，否则会出现重复
                    if (result.text.isNotBlank()) {
                        _uiState.update { state -> state.copy(inputText = result.text) }
                    }
                    if (result.isFinal) {
                        _uiState.update { it.copy(isRecognizing = false) }
                        sendVoiceMessageOnce()
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecognizing = false) }
            }
        }
    }

    fun stopVoiceRecognition() {
        val speechProvider = appServices.speechProvider
        speechProvider.stopRecognition()
        _uiState.update { it.copy(isRecognizing = false) }
        // 等待 ASR 服务端最终帧返回；若没有 final 回调，则用当前中间结果兜底发送一次。
        viewModelScope.launch {
            delay(1_200)
            sendVoiceMessageOnce()
        }
    }

    private fun sendVoiceMessageOnce() {
        if (voiceMessageSent) return
        if (_uiState.value.inputText.isBlank()) return
        voiceMessageSent = true
        speechJob = null
        sendMessage()
    }

    /** 聊天模式 直接音频输入 */
    fun startDirectAudioInput() {
        if (isLocalModelActive()) return
        val audioInputProvider = appServices.audioInputProvider
        audioInputProvider.startRecording()
        _uiState.update { it.copy(isRecognizing = true) }
    }

    fun stopDirectAudioInput() {
        if (isLocalModelActive()) return
        val audioInputProvider = appServices.audioInputProvider
        val audioBase64 = audioInputProvider.stopRecordingAsBase64()
        _uiState.update { it.copy(isRecognizing = false) }
        if (audioBase64.isNotEmpty()) {
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.USER,
                content = "[语音输入]",
                timestamp = System.currentTimeMillis(),
                imageUris = listOf("data:audio/wav;base64,$audioBase64"),
                sessionId = sessionId
            )
            // 直接构建消息列表并发送
            val aiMessageId = UUID.randomUUID().toString()
            val aiMessage = ChatMessage(
                id = aiMessageId, role = MessageRole.ASSISTANT, content = "",
                timestamp = System.currentTimeMillis(), isStreaming = true,
                usedWebSearch = _uiState.value.webSearchEnabled, sessionId = sessionId
            )
            _uiState.update { state ->
                state.copy(messages = state.messages + userMessage + aiMessage, isLoading = true)
            }
            viewModelScope.launch { repository.insertMessage(userMessage) }
            viewModelScope.launch {
                try {
                    val currentMessages = _uiState.value.messages.filter { !it.isStreaming }
                    val provider = currentLlmProvider()
                    val enableWebSearch = provider.modelSource() != "local" && _uiState.value.webSearchEnabled
                    provider.chatStream(currentMessages, contextPolicy, enableWebSearch).collect { token ->
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map { msg ->
                                if (msg.id == aiMessageId) msg.copy(content = msg.content + token) else msg
                            })
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { state ->
                        state.copy(messages = state.messages.map { msg ->
                            if (msg.id == aiMessageId) msg.copy(content = msg.content.ifEmpty { "请求出错：${e.message}" }, isStreaming = false) else msg
                        }, isLoading = false)
                    }
                    return@launch
                }
                _uiState.update { state ->
                    state.copy(messages = state.messages.map { msg ->
                        if (msg.id == aiMessageId) msg.copy(isStreaming = false) else msg
                    }, isLoading = false)
                }
                val finalMsg = _uiState.value.messages.find { it.id == aiMessageId }
                if (finalMsg != null) repository.insertMessage(finalMsg)

                generateFollowUpSuggestions(aiMessageId)
            }
        }
    }

    /** 生图模式 ASR → 文字 → 图片生成 */
    fun startImageVoiceInput() {
        if (isLocalModelActive()) return
        if (!appServices.currentCredentials().isTalkReady()) {
            showToast("请先在设置中配置 Talk API Key")
            return
        }
        _uiState.update { it.copy(isRecognizing = true) }
        val speechProvider = appServices.speechProvider
        speechJob = viewModelScope.launch {
            try {
                speechProvider.startRecognition().collect { result ->
                    // ASR 流式返回的是「累计全量文本」，需整体替换而非追加，否则会出现重复
                    if (result.text.isNotBlank()) {
                        _uiState.update { state -> state.copy(inputText = result.text) }
                    }
                    if (result.isFinal) {
                        _uiState.update { it.copy(isRecognizing = false) }
                        if (_uiState.value.inputText.isNotBlank()) { generateImage(_uiState.value.inputText) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecognizing = false) }
            }
        }
    }

    /** 图片生成 */
    fun generateImage(prompt: String) {
        if (isLocalModelActive()) return
        val credentials = appServices.currentCredentials()
        if (!credentials.isImageGenReady()) {
            showToast("请先在设置中配置 Ark API Key 与图片生成接入点")
            return
        }
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank() || _uiState.value.isLoading) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = cleanPrompt,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId
        )
        val aiMessageId = UUID.randomUUID().toString()
        val pendingMessage = ChatMessage(
            id = aiMessageId,
            role = MessageRole.ASSISTANT,
            content = "正在生成图片，请稍候…",
            timestamp = System.currentTimeMillis(),
            isStreaming = true,
            sessionId = sessionId
        )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage + pendingMessage,
                inputText = "",
                isLoading = true
            )
        }

        viewModelScope.launch {
            repository.insertMessage(userMessage)
            repository.insertSession(
                Session(
                    id = sessionId,
                    title = cleanPrompt.take(20).ifEmpty { "图片生成" },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )

            try {
                val imageClient = com.bytedace.doubaoapp.data.api.ArkImageClient(
                    apiKey = credentials.arkApiKey,
                    endpointId = credentials.imageGenEndpointId,
                )
                val imageUrl = imageClient.generateImage(cleanPrompt)
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                msg.copy(
                                    content = "已为你生成图片：",
                                    generatedImageUrl = imageUrl,
                                    isStreaming = false
                                )
                            } else {
                                msg
                            }
                        },
                        isLoading = false
                    )
                }
                val finalMessage = _uiState.value.messages.find { it.id == aiMessageId }
                if (finalMessage != null) repository.insertMessage(finalMessage)
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                msg.copy(
                                    content = "图片生成失败：${e.message}",
                                    isStreaming = false
                                )
                            } else {
                                msg
                            }
                        },
                        isLoading = false
                    )
                }
            }
        }
    }
}