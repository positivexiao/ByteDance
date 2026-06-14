package com.bytedace.doubaoapp.data.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.bytedace.doubaoapp.data.model.AudioChunk
import kotlinx.coroutines.flow.Flow

/**
 * PCM 音频流播放器。
 * 接收 AudioChunk 流，使用 AudioTrack 实时播放。
 * 支持边收边播，降低播报延迟。
 */
class AudioPlayer {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    // 火山引擎 TTS 默认输出格式：16kHz, 16bit, mono
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun play(audioFlow: Flow<AudioChunk>) {
        stop()

        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        isPlaying = true
    }

    fun writePcmData(data: ByteArray) {
        if (isPlaying) {
            audioTrack?.write(data, 0, data.size)
        }
    }

    fun stop() {
        isPlaying = false
        audioTrack?.apply {
            try {
                stop()
                release()
            } catch (_: IllegalStateException) {
                // 忽略
            }
        }
        audioTrack = null
    }

    fun getIsPlaying(): Boolean = isPlaying
}