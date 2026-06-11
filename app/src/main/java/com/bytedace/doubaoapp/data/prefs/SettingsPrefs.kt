package com.bytedace.doubaoapp.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bytedace.doubaoapp.data.api.ApiConfig

/**
 * 设置持久化工具类。
 * API Key 使用 EncryptedSharedPreferences；接入点等使用普通 SharedPreferences。
 */
class SettingsPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getArkApiKey(): String = securePrefs.getString(KEY_ARK_API_KEY, "") ?: ""

    fun saveArkApiKey(apiKey: String) {
        securePrefs.edit().putString(KEY_ARK_API_KEY, apiKey).apply()
    }

    fun clearArkApiKey() {
        securePrefs.edit().remove(KEY_ARK_API_KEY).apply()
    }

    fun hasArkApiKey(): Boolean = getArkApiKey().isNotBlank()

    fun getTalkApiKey(): String = securePrefs.getString(KEY_TALK_API_KEY, "") ?: ""

    fun saveTalkApiKey(apiKey: String) {
        securePrefs.edit().putString(KEY_TALK_API_KEY, apiKey).apply()
    }

    fun clearTalkApiKey() {
        securePrefs.edit().remove(KEY_TALK_API_KEY).apply()
    }

    fun hasTalkApiKey(): Boolean = getTalkApiKey().isNotBlank()

    fun getChatEndpointId(): String {
        return prefs.getString(KEY_CHAT_ENDPOINT, ApiConfig.DEFAULT_ENDPOINT_ID)
            ?: ApiConfig.DEFAULT_ENDPOINT_ID
    }

    fun saveChatEndpointId(endpointId: String) {
        prefs.edit().putString(KEY_CHAT_ENDPOINT, endpointId).apply()
    }

    fun getImageGenEndpointId(): String {
        return prefs.getString(KEY_IMAGE_GEN_ENDPOINT, ApiConfig.IMAGE_GEN_ENDPOINT_ID)
            ?: ApiConfig.IMAGE_GEN_ENDPOINT_ID
    }

    fun saveImageGenEndpointId(endpointId: String) {
        prefs.edit().putString(KEY_IMAGE_GEN_ENDPOINT, endpointId).apply()
    }

    fun getModelSource(): String {
        return prefs.getString(KEY_MODEL_SOURCE, "remote") ?: "remote"
    }

    fun saveModelSource(source: String) {
        prefs.edit().putString(KEY_MODEL_SOURCE, source).apply()
    }

    fun getLocalModelPath(): String {
        return prefs.getString(KEY_LOCAL_MODEL_PATH, "") ?: ""
    }

    fun saveLocalModelPath(path: String) {
        prefs.edit().putString(KEY_LOCAL_MODEL_PATH, path).apply()
    }

    fun getModelCopyDurationMs(): Long? {
        val value = prefs.getLong(KEY_MODEL_COPY_DURATION_MS, -1L)
        return if (value >= 0L) value else null
    }

    fun saveModelCopyDurationMs(durationMs: Long) {
        prefs.edit().putLong(KEY_MODEL_COPY_DURATION_MS, durationMs).apply()
    }

    companion object {
        private const val PREFS_NAME = "doubao_settings"
        private const val SECURE_PREFS_NAME = "doubao_secure_settings"
        private const val KEY_ARK_API_KEY = "ark_api_key"
        private const val KEY_TALK_API_KEY = "talk_api_key"
        private const val KEY_CHAT_ENDPOINT = "chat_endpoint_id"
        private const val KEY_IMAGE_GEN_ENDPOINT = "image_gen_endpoint_id"
        private const val KEY_MODEL_SOURCE = "model_source"
        private const val KEY_LOCAL_MODEL_PATH = "local_model_path"
        private const val KEY_MODEL_COPY_DURATION_MS = "model_copy_duration_ms"
    }
}
