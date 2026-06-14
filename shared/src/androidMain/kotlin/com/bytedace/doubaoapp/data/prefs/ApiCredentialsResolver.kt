package com.bytedace.doubaoapp.data.prefs

object ApiCredentialsResolver {
    fun resolve(prefs: SettingsPrefs): ApiCredentials {
        return ApiCredentials(
            arkApiKey = prefs.getArkApiKey(),
            talkApiKey = prefs.getTalkApiKey(),
            chatEndpointId = prefs.getChatEndpointId(),
            imageGenEndpointId = prefs.getImageGenEndpointId(),
        )
    }
}
