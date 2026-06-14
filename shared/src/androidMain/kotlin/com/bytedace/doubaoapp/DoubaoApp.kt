package com.bytedace.doubaoapp

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.bytedace.doubaoapp.data.context.SlidingWindowPolicy
import com.bytedace.doubaoapp.data.db.AppDatabase
import com.bytedace.doubaoapp.data.prefs.ApiCredentials
import com.bytedace.doubaoapp.data.prefs.ApiCredentialsResolver
import com.bytedace.doubaoapp.data.prefs.SettingsPrefs
import com.bytedace.doubaoapp.data.provider.AndroidAudioInputProvider
import com.bytedace.doubaoapp.data.provider.AudioInputProvider
import com.bytedace.doubaoapp.data.provider.LocalLlmProvider
import com.bytedace.doubaoapp.data.provider.LlmProvider
import com.bytedace.doubaoapp.data.provider.ModelSwitchManager
import com.bytedace.doubaoapp.data.provider.RemoteLlmProvider
import com.bytedace.doubaoapp.data.provider.SpeechProvider
import com.bytedace.doubaoapp.data.provider.TtsProvider
import com.bytedace.doubaoapp.data.provider.VolcSpeechProvider
import com.bytedace.doubaoapp.data.provider.VolcTtsProvider
import com.bytedace.doubaoapp.data.repository.ChatRepository
import com.bytedace.doubaoapp.data.repository.ChatRepositoryImpl
import com.bytedace.doubaoapp.platform.AndroidAppServices
import com.bytedace.doubaoapp.platform.AppServices
import com.bytedace.doubaoapp.platform.copyToAndroidClipboard
import com.bytedace.doubaoapp.platform.encodeAndroidImageToDataUri
import com.bytedace.doubaoapp.platform.saveAndroidImageFromUrl
import com.bytedace.doubaoapp.platform.showAndroidToast

class DoubaoApp : Application() {

    lateinit var modelSwitchManager: ModelSwitchManager
        private set

    lateinit var localLlmProvider: LocalLlmProvider
        private set

    lateinit var contextPolicy: SlidingWindowPolicy
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var speechProvider: SpeechProvider
        private set

    lateinit var ttsProvider: TtsProvider
        private set

    lateinit var audioInputProvider: AudioInputProvider
        private set

    lateinit var settingsPrefs: SettingsPrefs
        private set

    lateinit var appServices: AppServices
        private set

    private var latestCredentials: ApiCredentials = ApiCredentials("", "", "", "")

    override fun onCreate() {
        super.onCreate()

        settingsPrefs = SettingsPrefs(applicationContext)

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "doubao_chat_db",
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

        chatRepository = ChatRepositoryImpl(
            messageDao = database.messageDao(),
            sessionDao = database.sessionDao(),
        )

        localLlmProvider = LocalLlmProvider(applicationContext)
        modelSwitchManager = ModelSwitchManager(
            providers = mutableMapOf(
                "remote" to RemoteLlmProvider(apiKey = "", endpointId = ""),
                "local" to localLlmProvider,
            ),
        )
        val savedModelSource = settingsPrefs.getModelSource()
        if (savedModelSource in listOf("remote", "local")) {
            modelSwitchManager.switchProvider(savedModelSource)
        }

        contextPolicy = SlidingWindowPolicy(maxMessageCount = 20)
        audioInputProvider = AndroidAudioInputProvider(cacheDir = applicationContext.cacheDir)

        reconfigureRemoteServices(ApiCredentialsResolver.resolve(settingsPrefs))

        appServices = AndroidAppServices(
            modelSwitchManager = modelSwitchManager,
            chatRepository = chatRepository,
            contextPolicy = contextPolicy,
            speechProvider = speechProvider,
            ttsProvider = ttsProvider,
            audioInputProvider = audioInputProvider,
            currentProviderKeyFlow = modelSwitchManager.currentProviderKeyFlow,
            llmProviderProvider = { currentLlmProvider },
            localLlmProvider = localLlmProvider,
            credentialsProvider = { latestCredentials },
            toastHandler = { showAndroidToast(applicationContext, it) },
            clipboardHandler = { copyToAndroidClipboard(applicationContext, it) },
            imageEncoder = { encodeAndroidImageToDataUri(applicationContext, it) },
            imageSaver = { saveAndroidImageFromUrl(applicationContext, it) },
        )
    }

    fun currentCredentials(): ApiCredentials = latestCredentials

    fun reconfigureRemoteServices(credentials: ApiCredentials) {
        latestCredentials = credentials

        if (::speechProvider.isInitialized) {
            speechProvider.stopRecognition()
        }
        if (::ttsProvider.isInitialized) {
            ttsProvider.stop()
        }

        val remoteProvider = RemoteLlmProvider(
            apiKey = credentials.arkApiKey,
            endpointId = credentials.chatEndpointId,
        )
        modelSwitchManager.replaceProvider("remote", remoteProvider)

        speechProvider = VolcSpeechProvider(apiKey = credentials.talkApiKey)
        ttsProvider = VolcTtsProvider(
            talkApiKey = credentials.talkApiKey,
            context = applicationContext,
        )
    }

    val currentLlmProvider: LlmProvider
        get() = modelSwitchManager.currentProvider

    companion object {
        fun from(context: Context): DoubaoApp = context.applicationContext as DoubaoApp
    }
}
