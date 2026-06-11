package com.bytedace.doubaoapp.data.provider

import android.net.Uri

/**
 * 音频输入 Provider 接口。
 * 用于录制音频并编码为 Base64 后直接发送给 Chat API（音频多模态）。
 */
interface AudioInputProvider {

    /** 开始录音 */
    fun startRecording()

    /** 停止录音并返回录音文件的 URI（本地缓存文件） */
    fun stopRecording(): Uri?

    /** 停止录音并返回 Base64 编码的 WAV 音频 */
    fun stopRecordingAsBase64(): String

    /** 是否正在录音 */
    fun isRecording(): Boolean

    /** 销毁 */
    fun destroy()
}