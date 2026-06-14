package com.bytedace.doubaoapp.platform

import com.bytedace.doubaoapp.data.context.ContextPolicy
import com.bytedace.doubaoapp.data.prefs.ApiCredentials
import com.bytedace.doubaoapp.data.provider.AudioInputProvider
import com.bytedace.doubaoapp.data.provider.LlmProvider
import com.bytedace.doubaoapp.data.provider.ModelSwitchManager
import com.bytedace.doubaoapp.data.provider.SpeechProvider
import com.bytedace.doubaoapp.data.provider.TtsProvider
import com.bytedace.doubaoapp.data.repository.ChatRepository
import kotlinx.coroutines.flow.StateFlow

interface AppServices {
    val modelSwitchManager: ModelSwitchManager
    val chatRepository: ChatRepository
    val contextPolicy: ContextPolicy
    val speechProvider: SpeechProvider
    val ttsProvider: TtsProvider
    val audioInputProvider: AudioInputProvider
    val currentProviderKeyFlow: StateFlow<String>
    val currentLlmProvider: LlmProvider
    fun currentCredentials(): ApiCredentials
    fun onChatSessionChanged() {}
    fun showToast(message: String)
    fun copyToClipboard(text: String)
    suspend fun encodeImageToDataUri(imageRef: String): String
    suspend fun saveImageFromUrl(url: String): Result<Unit>
}
