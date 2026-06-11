package com.bytedace.doubaoapp.data.model

data class AudioChunk(
    val pcmData: ByteArray,   // PCM 音频数据
    val isFinal: Boolean      // 是否最后一帧
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioChunk) return false
        return isFinal == other.isFinal
    }

    override fun hashCode(): Int {
        return isFinal.hashCode()
    }
}