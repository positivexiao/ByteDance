package com.bytedace.doubaoapp.data.provider

import android.net.Uri
import com.bytedace.doubaoapp.data.model.SpeechResult
import kotlinx.coroutines.flow.Flow

/**
 * 语音识别 Provider 接口。
 * 远端实现：VolcSpeechProvider（火山引擎大模型 ASR WebSocket）
 * 
 * 用于：
 * 1. 聊天模式可选的 ASR 语音→文字输入
 * 2. 生图模式必须的 ASR 语音→文字输入（文字再传给图片生成 API）
 */
interface SpeechProvider {

    /** 开始录音识别，返回识别结果流（含中间结果和最终结果） */
    fun startRecognition(): Flow<SpeechResult>

    /** 停止录音识别 */
    fun stopRecognition()

    /** 是否正在识别中 */
    fun isRecognizing(): Boolean

    /** 销毁引擎，释放资源 */
    fun destroy()
}