package com.bytedace.doubaoapp.data.provider

import com.bytedace.doubaoapp.data.model.AudioChunk
import kotlinx.coroutines.flow.Flow

/**
 * 语音合成(TTS) Provider 接口。
 * 远端实现：VolcTtsProvider（火山引擎 TTS WebSocket）
 * 本地实现（未来）：LocalTtsProvider（本地 TTS 模型）
 */
interface TtsProvider {

    /** 合成语音，返回 PCM 音频数据流（流式边收边播） */
    fun synthesize(text: String): Flow<AudioChunk>

    /** 停止当前播报 */
    fun stop()

    /** 是否正在播报 */
    fun isPlaying(): Boolean

    /** 销毁，释放资源 */
    fun destroy()
}