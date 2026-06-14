package com.bytedace.doubaoapp.platform

import com.bytedace.doubaoapp.data.context.ContextPolicy
import com.bytedace.doubaoapp.data.prefs.ApiCredentials
import com.bytedace.doubaoapp.data.provider.AudioInputProvider
import com.bytedace.doubaoapp.data.provider.LocalLlmProvider
import com.bytedace.doubaoapp.data.provider.LlmProvider
import com.bytedace.doubaoapp.data.provider.ModelSwitchManager
import com.bytedace.doubaoapp.data.provider.SpeechProvider
import com.bytedace.doubaoapp.data.provider.TtsProvider
import com.bytedace.doubaoapp.data.repository.ChatRepository
import kotlinx.coroutines.flow.StateFlow

class AndroidAppServices(
    override val modelSwitchManager: ModelSwitchManager,
    override val chatRepository: ChatRepository,
    override val contextPolicy: ContextPolicy,
    override val speechProvider: SpeechProvider,
    override val ttsProvider: TtsProvider,
    override val audioInputProvider: AudioInputProvider,
    override val currentProviderKeyFlow: StateFlow<String>,
    private val llmProviderProvider: () -> LlmProvider,
    private val localLlmProvider: LocalLlmProvider,
    private val credentialsProvider: () -> ApiCredentials,
    private val toastHandler: (String) -> Unit,
    private val clipboardHandler: (String) -> Unit,
    private val imageEncoder: suspend (String) -> String,
    private val imageSaver: suspend (String) -> Result<Unit>,
) : AppServices {
    override val currentLlmProvider: LlmProvider
        get() = llmProviderProvider()

    override fun currentCredentials(): ApiCredentials = credentialsProvider()

    override fun onChatSessionChanged() {
        localLlmProvider.markSessionChanged()
    }

    override fun showToast(message: String) = toastHandler(message)

    override fun copyToClipboard(text: String) = clipboardHandler(text)

    override suspend fun encodeImageToDataUri(imageRef: String): String = imageEncoder(imageRef)

    override suspend fun saveImageFromUrl(url: String): Result<Unit> = imageSaver(url)
}
