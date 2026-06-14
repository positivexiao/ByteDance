package com.bytedace.doubaoapp.data.provider

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android 音频输入 Provider — 录制 PCM/WAV 音频并编码为 Base64。
 * 用于直接发送给 Chat API 的音频多模态输入（聊天模式语音输入）。
 */
class AndroidAudioInputProvider(
    private val cacheDir: File
) : AudioInputProvider {

    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var audioBuffer: ByteArrayOutputStream? = null

    // 录音参数: 16kHz, 16bit, mono (与 Ark API 音频输入兼容)
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormatConfig = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = maxOf(AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormatConfig), 3200)

    override fun startRecording() {
        if (isRecording) return
        isRecording = true
        audioBuffer = ByteArrayOutputStream()

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormatConfig,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 初始化失败")
                isRecording = false
                return
            }

            audioRecord?.startRecording()
            Log.d(TAG, "开始录音")
        } catch (e: SecurityException) {
            Log.e(TAG, "录音权限不足: ${e.message}")
            isRecording = false
            return
        }

        // 录音线程持续读取音频数据
        recordingThread = Thread {
            val readBuffer = ShortArray(bufferSize / 2)
            while (isRecording) {
                val readCount = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                if (readCount > 0) {
                    for (i in 0 until readCount) {
                        audioBuffer?.write(readBuffer[i].toInt() and 0xFF)
                        audioBuffer?.write((readBuffer[i].toInt() shr 8) and 0xFF)
                    }
                }
            }
            Log.d(TAG, "录音线程结束")
        }
        recordingThread?.start()
    }

    override fun stopRecording(): android.net.Uri? {
        isRecording = false
        audioRecord?.let {
            try { it.stop(); it.release() } catch (_: IllegalStateException) {}
        }
        audioRecord = null

        try { recordingThread?.join(1000) } catch (_: InterruptedException) {}
        recordingThread = null

        val pcmData = audioBuffer?.toByteArray() ?: ByteArray(0)
        audioBuffer = null

        if (pcmData.isEmpty()) return null

        val wavData = addWavHeader(pcmData)
        val wavFile = writeWavFile(wavData)
        return android.net.Uri.fromFile(wavFile)
    }

    override fun stopRecordingAsBase64(): String {
        isRecording = false
        audioRecord?.let {
            try { it.stop(); it.release() } catch (_: IllegalStateException) {}
        }
        audioRecord = null

        try { recordingThread?.join(1000) } catch (_: InterruptedException) {}
        recordingThread = null

        val pcmData = audioBuffer?.toByteArray() ?: ByteArray(0)
        audioBuffer = null

        if (pcmData.isEmpty()) return ""

        val wavData = addWavHeader(pcmData)
        return Base64.encodeToString(wavData, Base64.NO_WRAP)
    }

    override fun isRecording(): Boolean = isRecording

    override fun destroy() {
        stopRecording()
    }

    /**
     * 给 PCM 数据添加 WAV header (44 bytes)
     */
    private fun addWavHeader(pcmData: ByteArray): ByteArray {
        val totalLength = 44 + pcmData.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        header.put(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
        header.putInt(totalLength - 8)
        header.put(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))

        // fmt sub-chunk
        header.put(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
        header.putInt(16)                              // Sub-chunk size
        header.putShort(1)                             // AudioFormat: PCM
        header.putShort(1)                             // NumChannels: mono
        header.putInt(sampleRate)                      // SampleRate
        header.putInt(sampleRate * 2)                  // ByteRate (SampleRate * NumChannels * BitsPerSample/8)
        header.putShort(2)                             // BlockAlign (NumChannels * BitsPerSample/8)
        header.putShort(16)                            // BitsPerSample

        // data sub-chunk
        header.put(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
        header.putInt(pcmData.size)

        val result = ByteArrayOutputStream()
        result.write(header.array())
        result.write(pcmData)
        return result.toByteArray()
    }

    /**
     * 保存 WAV 文件到本地缓存目录
     */
    private fun writeWavFile(wavData: ByteArray): File {
        val fileName = "audio_${UUID.randomUUID()}.wav"
        val file = File(cacheDir, fileName)
        FileOutputStream(file).use { it.write(wavData) }
        Log.d(TAG, "音频文件保存到: ${file.absolutePath}")
        return file
    }

    companion object {
        private const val TAG = "AndroidAudioInputProvider"
    }
}