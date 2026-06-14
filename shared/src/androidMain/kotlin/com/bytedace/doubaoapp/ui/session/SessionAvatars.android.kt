package com.bytedace.doubaoapp.ui.session

actual fun sessionAvatarIndex(sessionId: String): Int {
    return sessionId.hashCode().and(Int.MAX_VALUE) % sessionAvatarCount()
}

actual fun sessionAvatarCount(): Int = 4
