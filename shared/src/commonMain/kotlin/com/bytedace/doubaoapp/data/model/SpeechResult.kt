package com.bytedace.doubaoapp.data.model

data class SpeechResult(
    val text: String,        // 识别文本
    val isFinal: Boolean,    // 是否最终结果（中间结果为 false）
    val volume: Float = 0f   // 录音音量归一化值(0-1)
)