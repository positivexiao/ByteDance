package com.bytedace.doubaoapp.data.model

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val inputText: String = "",
    val selectedImageUris: List<String> = emptyList(),
    val currentModelName: String = "豆包(远端)",
    val currentModelSource: String = "remote",
    val isRecording: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val playingMessageId: String? = null,
    val isRecognizing: Boolean = false,
    val webSearchEnabled: Boolean = false,   // 联网搜索模式
    val imageGenEnabled: Boolean = false     // 图片生成模式
)