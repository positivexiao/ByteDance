package com.bytedace.doubaoapp.data.provider

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.bytedace.doubaoapp.data.api.ApiConfig
import com.bytedace.doubaoapp.data.model.SpeechResult
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

/**
 * 火山引擎语音识别 Provider — 大模型流式 ASR WebSocket 实现。
 *
 * 基于官方文档：wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async
 * 新版鉴权：只需 X-Api-Key Header（使用 Talk API Key）
 *
 * 用于：
 * 1. 聊天模式可选的 ASR 转文字输入
 * 2. 生图模式必须的 ASR 转文字输入
 *
 * 协议格式：4字节 Header + Payload（与 TTS 相同的二进制协议）
 */
class VolcSpeechProvider(
    private val apiKey: String  // Talk API Key
) : SpeechProvider {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()
    private val _isRecognizing = MutableStateFlow(false)
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null

    /**
     * 全局递增序列号。服务端按消息到达顺序自动分配序列号，并要求客户端发送的序列号与之严格一致：
     * full client request = 1，音频包依次 2、3、4…，最后一包为负序列号。
     * 之前的实现中 full client request 不带序列号、音频包从 1 开始，
     * 导致服务端报 "autoAssignedSequence (2) mismatch sequence in request (1)" 并丢弃全部请求，因此识别无任何结果。
     */
    private val sequence = java.util.concurrent.atomic.AtomicInteger(0)

    // 录音参数
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFmt = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = maxOf(AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFmt), 3200)

    // 识别结果收集
    private val resultCollector = MutableStateFlow<SpeechResult?>(null)

    override fun startRecognition(): Flow<SpeechResult> = flow {
        _isRecognizing.value = true
        sequence.set(0)
        resultCollector.value = null
        val requestId = UUID.randomUUID().toString()

        // WebSocket 连接（使用 X-Api-Key 鉴权）
        val request = Request.Builder()
            .url(ApiConfig.ASR_ADDRESS)  // bigmodel_async
            .addHeader("X-Api-Key", apiKey)
            .addHeader("X-Api-Resource-Id", ApiConfig.ASR_RESOURCE_ID_V2_DURATION)
            .addHeader("X-Api-Request-Id", requestId)
            .addHeader("X-Api-Sequence", "-1")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "ASR WebSocket connected")

                // 发送 full client request（音频元数据），序列号必须为 1（POSITIVE_SEQUENCE）
                val fullRequestJson = buildFullClientRequest()
                val compressedPayload = gzipCompress(fullRequestJson.toByteArray(Charsets.UTF_8))
                val header = buildHeader(
                    messageType = MESSAGE_TYPE_FULL_CLIENT_REQUEST,
                    flags = FLAG_POSITIVE_SEQUENCE,
                    serialization = SERIALIZATION_JSON,
                    compression = COMPRESSION_GZIP
                )
                val frame = buildBinaryFrame(header, compressedPayload, sequenceNumber = sequence.incrementAndGet())
                ws.send(okio.ByteString.of(*frame))

                // 启动录音并发送音频
                startRecordingAndSend(ws)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                try {
                    val data = bytes.toByteArray()
                    val headerBytes = data.copyOfRange(0, 4)
                    val messageType = (headerBytes[1].toInt() shr 4) and 0x0F

                    when (messageType) {
                        MESSAGE_TYPE_FULL_SERVER_RESPONSE -> {
                            // 解析 server response: header(4) + sequence(4) + payload_size(4) + payload
                            if (data.size < 12) return

                            val sequenceNumber = ((data[4].toInt() and 0xFF) shl 24) or
                                    ((data[5].toInt() and 0xFF) shl 16) or
                                    ((data[6].toInt() and 0xFF) shl 8) or
                                    (data[7].toInt() and 0xFF)

                            val payloadSize = ((data[8].toLong() and 0xFF) shl 24) or
                                    ((data[9].toLong() and 0xFF) shl 16) or
                                    ((data[10].toLong() and 0xFF) shl 8) or
                                    (data[11].toLong() and 0xFF)

                            if (payloadSize > 0 && data.size >= 12 + payloadSize) {
                                val payloadData = data.copyOfRange(12, 12 + payloadSize.toInt())
                                val compression = headerBytes[2].toInt() and 0x0F
                                val jsonBytes = if (compression == COMPRESSION_GZIP) {
                                    gzipDecompress(payloadData)
                                } else {
                                    payloadData
                                }
                                val jsonStr = String(jsonBytes, Charsets.UTF_8)
                                parseAsrResult(jsonStr, isFinalFrame = sequenceNumber < 0)
                            }
                        }
                        MESSAGE_TYPE_ERROR -> {
                            Log.e(TAG, "ASR server error received: ${parseErrorPayload(data)}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing ASR response: ${e.message}")
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "ASR text message received, try parse as binary frame")
                onMessage(ws, ByteString.of(*text.toByteArray(Charsets.ISO_8859_1)))
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "ASR WebSocket failure: ${t.message}")
                _isRecognizing.value = false
                stopRecording()
            }
        }

        webSocket = client.newWebSocket(request, listener)

        // 收集识别结果
        try {
            resultCollector.collect { result ->
                if (result != null) {
                    emit(result)
                    if (result.isFinal) {
                        _isRecognizing.value = false
                    }
                }
            }
        } finally {
            _isRecognizing.value = false
            stopRecording()
            webSocket?.close(1000, "Done")
        }
    }.flowOn(Dispatchers.IO)

    override fun stopRecognition() {
        // 先停止录音线程继续发包，避免序列号竞争
        _isRecognizing.value = false
        // 发送最后一包音频（负序列号标识结束），序列号幅值需与服务端自动分配的一致
        webSocket?.let { ws ->
            val emptyAudio = ByteArray(0)
            val compressedAudio = gzipCompress(emptyAudio)
            val header = buildHeader(
                messageType = MESSAGE_TYPE_AUDIO_ONLY_REQUEST,
                flags = FLAG_NEGATIVE_SEQUENCE,
                serialization = SERIALIZATION_NONE,
                compression = COMPRESSION_GZIP
            )
            val frame = buildBinaryFrame(header, compressedAudio, sequenceNumber = -sequence.incrementAndGet())
            ws.send(okio.ByteString.of(*frame))
        }
        stopRecording()
    }

    override fun isRecognizing(): Boolean = _isRecognizing.value

    override fun destroy() {
        stopRecognition()
        webSocket?.close(1000, "Destroy")
        webSocket = null
        client.dispatcher.executorService.shutdown()
    }

    // ---- 录音并发送音频 ----

    private fun startRecordingAndSend(ws: WebSocket) {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFmt,
                bufferSize
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "录音权限不足: ${e.message}")
            _isRecognizing.value = false
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord 初始化失败")
            _isRecognizing.value = false
            return
        }

        audioRecord?.startRecording()

        Thread {
            val audioBuffer = ShortArray(bufferSize / 2)

            while (_isRecognizing.value && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val readCount = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                if (readCount > 0) {
                    val byteOut = ByteArrayOutputStream()
                    for (i in 0 until readCount) {
                        byteOut.write(audioBuffer[i].toInt() and 0xFF)
                        byteOut.write((audioBuffer[i].toInt() shr 8) and 0xFF)
                    }
                    val pcmData = byteOut.toByteArray()
                    val compressedData = gzipCompress(pcmData)

                    val header = buildHeader(
                        messageType = MESSAGE_TYPE_AUDIO_ONLY_REQUEST,
                        flags = FLAG_POSITIVE_SEQUENCE,
                        serialization = SERIALIZATION_NONE,
                        compression = COMPRESSION_GZIP
                    )
                    // 音频包序列号依次 2、3、4…（紧接 full client request 的 1）
                    val frame = buildBinaryFrame(header, compressedData, sequenceNumber = sequence.incrementAndGet())
                    ws.send(okio.ByteString.of(*frame))
                }

                // 每包约200ms
                Thread.sleep(200)
            }
        }.start()
    }

    private fun stopRecording() {
        audioRecord?.let {
            try { it.stop(); it.release() } catch (_: IllegalStateException) {}
        }
        audioRecord = null
    }

    // ---- 二进制协议构建 ----

    private fun buildHeader(
        messageType: Int,
        flags: Int = 0,
        serialization: Int = SERIALIZATION_JSON,
        compression: Int = COMPRESSION_GZIP
    ): ByteArray {
        return byteArrayOf(
            ((PROTOCOL_VERSION shl 4) or HEADER_SIZE_4).toByte(),
            ((messageType shl 4) or flags).toByte(),
            ((serialization shl 4) or compression).toByte(),
            0x00
        )
    }

    private fun buildBinaryFrame(header: ByteArray, payload: ByteArray, sequenceNumber: Int? = null): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(header)

        // Sequence number (4 bytes, big-endian) — 仅 audio-only request 需要
        if (sequenceNumber != null) {
            baos.write((sequenceNumber shr 24) and 0xFF)
            baos.write((sequenceNumber shr 16) and 0xFF)
            baos.write((sequenceNumber shr 8) and 0xFF)
            baos.write(sequenceNumber and 0xFF)
        }

        // Payload size (4 bytes, big-endian)
        val payloadSize = payload.size
        baos.write((payloadSize shr 24) and 0xFF)
        baos.write((payloadSize shr 16) and 0xFF)
        baos.write((payloadSize shr 8) and 0xFF)
        baos.write(payloadSize and 0xFF)

        baos.write(payload)
        return baos.toByteArray()
    }

    private fun buildFullClientRequest(): String {
        val requestMap = mapOf(
            "user" to mapOf("uid" to "doubao_user"),
            "audio" to mapOf(
                "format" to "pcm",
                "rate" to 16000,
                "bits" to 16,
                "channel" to 1
            ),
            "request" to mapOf(
                "model_name" to "bigmodel",
                "enable_itn" to true,
                "enable_ddc" to true,
                "enable_punc" to true,
                "result_type" to "single"
            )
        )
        return gson.toJson(requestMap)
    }

    private fun parseAsrResult(jsonStr: String, isFinalFrame: Boolean = false) {
        try {
            val json = JsonParser.parseString(jsonStr).asJsonObject
            if (json.has("result")) {
                val result = json.getAsJsonObject("result")
                val text = result.get("text")?.asString ?: ""

                val isDefinite = result.has("utterances") &&
                        result.getAsJsonArray("utterances")
                            .any { it.asJsonObject.get("definite")?.asBoolean == true }

                resultCollector.value = SpeechResult(
                    text = text,
                    isFinal = isDefinite || isFinalFrame,
                    volume = 0f
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ASR result: ${e.message}")
        }
    }

    private fun parseErrorPayload(data: ByteArray): String {
        return try {
            if (data.size < 12) return "invalid error frame"
            val headerBytes = data.copyOfRange(0, 4)
            val compression = headerBytes[2].toInt() and 0x0F
            val payloadSize = ((data[8].toLong() and 0xFF) shl 24) or
                    ((data[9].toLong() and 0xFF) shl 16) or
                    ((data[10].toLong() and 0xFF) shl 8) or
                    (data[11].toLong() and 0xFF)
            if (payloadSize <= 0 || data.size < 12 + payloadSize.toInt()) return "empty error payload"
            val payloadData = data.copyOfRange(12, 12 + payloadSize.toInt())
            val errorBytes = if (compression == COMPRESSION_GZIP) gzipDecompress(payloadData) else payloadData
            String(errorBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "failed to parse error payload: ${e.message}"
        }
    }

    // ---- 压缩工具 ----

    private fun gzipCompress(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(data) }
        return baos.toByteArray()
    }

    private fun gzipDecompress(data: ByteArray): ByteArray {
        java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(data)).use { gzipStream ->
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var len: Int
            while (gzipStream.read(buffer).also { len = it } != -1) {
                baos.write(buffer, 0, len)
            }
            return baos.toByteArray()
        }
    }

    companion object {
        private const val TAG = "VolcSpeechProvider"

        // 协议常量
        private const val PROTOCOL_VERSION = 0b0001
        private const val HEADER_SIZE_4 = 0b0001

        // Message types
        private const val MESSAGE_TYPE_FULL_CLIENT_REQUEST = 0b0001
        private const val MESSAGE_TYPE_AUDIO_ONLY_REQUEST = 0b0010
        private const val MESSAGE_TYPE_FULL_SERVER_RESPONSE = 0b1001
        private const val MESSAGE_TYPE_ERROR = 0b1111

        // Flags
        private const val FLAG_NO_SEQUENCE = 0b0000
        private const val FLAG_POSITIVE_SEQUENCE = 0b0001
        private const val FLAG_LAST_PACKAGE = 0b0010
        private const val FLAG_NEGATIVE_SEQUENCE = 0b0011

        // Serialization & Compression
        private const val SERIALIZATION_NONE = 0b0000
        private const val SERIALIZATION_JSON = 0b0001
        private const val COMPRESSION_GZIP = 0b0001
    }
}