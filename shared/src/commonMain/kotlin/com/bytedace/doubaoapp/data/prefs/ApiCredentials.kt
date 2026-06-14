package com.bytedace.doubaoapp.data.prefs

data class ApiCredentials(
    val arkApiKey: String,
    val talkApiKey: String,
    val chatEndpointId: String,
    val imageGenEndpointId: String,
) {
    fun isChatReady(): Boolean = arkApiKey.isNotBlank() && chatEndpointId.isNotBlank()

    fun isImageGenReady(): Boolean = arkApiKey.isNotBlank() && imageGenEndpointId.isNotBlank()

    fun isTalkReady(): Boolean = talkApiKey.isNotBlank()
}
