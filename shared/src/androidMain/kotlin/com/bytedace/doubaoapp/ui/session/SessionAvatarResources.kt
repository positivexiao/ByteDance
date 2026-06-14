package com.bytedace.doubaoapp.ui.session

import com.bytedace.doubaoapp.shared.R

object SessionAvatarResources {
    val avatars = listOf(
        R.drawable.session_avatar_1,
        R.drawable.session_avatar_2,
        R.drawable.session_avatar_3,
        R.drawable.session_avatar_4,
    )

    fun forSession(sessionId: String): Int {
        val index = sessionAvatarIndex(sessionId)
        return avatars[index]
    }
}
