package com.bytedace.doubaoapp.data.provider

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.bytedace.doubaoapp.data.api.ApiConfig
import com.bytedace.doubaoapp.data.model.AudioChunk
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
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
import java.util.zip.GZIPInputStream

/**
 * 火山引擎「大模型语音合成 2.0」TTS Provider —— v3 双向流式协议实现。
 *
 * 参考文档：https://www.volcengine.com/docs/6561/1329505
 *
 * 之前的实现错误地使用了旧版 v1 `/api/v1/tts/ws_binary` 协议（需要 Bearer Token + appid），
 * 却又用 v3 风格的 X-Api-Key 鉴权，二者不匹配，导致服务端不返回音频，因此“没有声音”。
 *
 * 本实现按文档采用 v3 双向流式事件协议：
 *   StartConnection(1) → ConnectionStarted(50)
 *   StartSession(100)  → SessionStarted(150)
 *   TaskRequest(200)（发送待合成文本）+ FinishSession(102)
 *   服务端持续下发 TTSResponse(352) 音频帧 → 写入 AudioTrack 播放
 *   SessionFinished(152) → FinishConnection(2) → ConnectionFinished(52)
 *
 * 输出 PCM(24kHz/16bit/mono)，直接喂给 AudioTrack 播放。
 */
class VolcTtsProvider(
    private val talkApiKey: String,
    private val context: Context,
) : TtsProvider {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()
    private val _isPlaying = MutableStateFlow(value = false)
    private var webSocket: WebSocket? = null
    private var audioTrack: AudioTrack? = null

    private val sampleRate = ApiConfig.TTS_SAMPLE_RATE
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFmt = AudioFormat.ENCODING_PCM_16BIT

    override fun synthesize(text: String): Flow<AudioChunk> = callbackFlow {
        if (text.isEmpty()) {
            trySend(AudioChunk(ByteArray(0), isFinal = true))
            close()
            return@callbackFlow
        }

        _isPlaying.value = true
        initAudioTrack()

        val scope = this
        val sessionId = UUID.randomUUID().toString().replace("-", "")
        val request = Request.Builder()
            .url(ApiConfig.TTS_ADDRESS)
            .addHeader("X-Api-App-Key", talkApiKey)
            .addHeader("X-Api-Access-Key", talkApiKey)
            .addHeader("X-Api-Key", talkApiKey)
            .addHeader("X-Api-Resource-Id", ApiConfig.TTS_RESOURCE_ID)
            .addHeader("X-Api-Request-Id", UUID.randomUUID().toString())
            .addHeader("X-Api-Connect-Id", UUID.randomUUID().toString())
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "TTS WebSocket connected")
                ws.send(ByteString.of(*buildStartConnection()))
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                handleFrame(scope, ws, bytes.toByteArray(), text, sessionId)
            }

            override fun onMessage(ws: WebSocket, text2: String) {
                // 服务端二进制帧偶尔会被误判为 text，用 ISO_8859_1 1:1 还原字节再解析
                handleFrame(scope, ws, text2.toByteArray(Charsets.ISO_8859_1), text, sessionId)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "TTS WebSocket failure: ${t.message}")
                finishPlayback()
                scope.trySend(AudioChunk(ByteArray(0), isFinal = true))
                scope.close(t)
            }
        }

        webSocket = client.newWebSocket(request, listener)

        awaitClose {
            _isPlaying.value = false
            releaseAudioTrack()
            webSocket?.close(1000, "Done")
        }
    }.flowOn(Dispatchers.IO)

    override fun stop() {
        finishPlayback()
        webSocket?.close(1000, "Stop requested")
        webSocket = null
    }

    override fun isPlaying(): Boolean = _isPlaying.value

    override fun destroy() {
        stop()
        client.dispatcher.executorService.shutdown()
    }

    // ---- 帧分发（callbackFlow 内部使用，通过闭包持有 ProducerScope） ----

    private fun handleFrame(
        scope: kotlinx.coroutines.channels.ProducerScope<AudioChunk>,
        ws: WebSocket,
        data: ByteArray,
        text: String,
        sessionId: String,
    ) {
        try {
            val parsed = parseFrame(data) ?: return
            Log.d(TAG, "TTS frame: msgType=${parsed.messageType}, event=${parsed.event}, payloadSize=${parsed.payload.size}")

            when {
                parsed.messageType == MESSAGE_TYPE_ERROR -> {
                    Log.e(TAG, "TTS error: ${String(parsed.payload, Charsets.UTF_8)}")
                    finishPlayback()
                    scope.trySend(AudioChunk(ByteArray(0), isFinal = true))
                    scope.close()
                }

                parsed.event == EVENT_CONNECTION_STARTED -> {
                    ws.send(ByteString.of(*buildStartSession(sessionId)))
                }

                parsed.event == EVENT_SESSION_STARTED -> {
                    ws.send(ByteString.of(*buildTaskRequest(sessionId, text)))
                    ws.send(ByteString.of(*buildFinishSession(sessionId)))
                }

                parsed.event == EVENT_TTS_RESPONSE && parsed.messageType == MESSAGE_TYPE_AUDIO_ONLY_RESPONSE -> {
                    val audio = if (parsed.compression == COMPRESSION_GZIP) {
                        runCatching { gzipDecompress(parsed.payload) }.getOrDefault(parsed.payload)
                    } else {
                        parsed.payload
                    }
                    if (audio.isNotEmpty()) {
                        writePcmData(audio)
                        scope.trySend(AudioChunk(audio, isFinal = false))
                    }
                }

                parsed.event == EVENT_SESSION_FINISHED || parsed.event == EVENT_SESSION_FAILED -> {
                    ws.send(ByteString.of(*buildFinishConnection()))
                }

                parsed.event == EVENT_CONNECTION_FINISHED || parsed.event == EVENT_CONNECTION_FAILED -> {
                    finishPlayback()
                    scope.trySend(AudioChunk(ByteArray(0), isFinal = true))
                    scope.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling TTS frame: ${e.message}")
        }
    }

    // ---- 协议解析 ----

    private data class ParsedFrame(
        val messageType: Int,
        val flags: Int,
        val compression: Int,
        val event: Int,
        val payload: ByteArray,
    )

    private fun parseFrame(data: ByteArray): ParsedFrame? {
        if (data.size < 4) return null
        val headerSize = data[0].toInt() and 0x0F
        val messageType = (data[1].toInt() shr 4) and 0x0F
        val flags = data[1].toInt() and 0x0F
        val compression = data[2].toInt() and 0x0F

        var offset = headerSize * 4
        var event = EVENT_NONE

        if (flags and FLAG_WITH_EVENT != 0) {
            if (data.size < offset + 4) return null
            event = readInt32(data, offset)
            offset += 4
        }

        // 会话级事件携带 sessionID（连接级事件不携带）
        if (event != EVENT_NONE && event !in CONNECTION_EVENTS) {
            if (data.size < offset + 4) return ParsedFrame(messageType, flags, compression, event, ByteArray(0))
            val sessionIdLen = readInt32(data, offset)
            offset += 4
            if (sessionIdLen in 0..(data.size - offset)) {
                offset += sessionIdLen
            }
        }

        if (data.size < offset + 4) return ParsedFrame(messageType, flags, compression, event, ByteArray(0))
        val payloadSize = readInt32(data, offset)
        offset += 4
        val end = if (payloadSize in 0..(data.size - offset)) offset + payloadSize else data.size
        val payload = data.copyOfRange(offset, end)
        return ParsedFrame(messageType, flags, compression, event, payload)
    }

    // ---- 协议帧构建 ----

    private fun header(): ByteArray = byteArrayOf(
        ((PROTOCOL_VERSION shl 4) or HEADER_SIZE_4).toByte(),
        ((MESSAGE_TYPE_FULL_CLIENT_REQUEST shl 4) or FLAG_WITH_EVENT).toByte(),
        ((SERIALIZATION_JSON shl 4) or COMPRESSION_NONE).toByte(),
        0x00,
    )

    private fun buildEventFrame(event: Int, payload: ByteArray, sessionId: String?): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(header())
        writeInt32(baos, event)
        if (sessionId != null) {
            val sid = sessionId.toByteArray(Charsets.UTF_8)
            writeInt32(baos, sid.size)
            baos.write(sid)
        }
        writeInt32(baos, payload.size)
        baos.write(payload)
        return baos.toByteArray()
    }

    private fun buildStartConnection(): ByteArray =
        buildEventFrame(EVENT_START_CONNECTION, "{}".toByteArray(Charsets.UTF_8), sessionId = null)

    private fun buildFinishConnection(): ByteArray =
        buildEventFrame(EVENT_FINISH_CONNECTION, "{}".toByteArray(Charsets.UTF_8), sessionId = null)

    private fun buildStartSession(sessionId: String): ByteArray {
        val payload = gson.toJson(
            mapOf(
                "user" to mapOf("uid" to "doubao_user"),
                "event" to EVENT_START_SESSION,
                "namespace" to ApiConfig.TTS_NAMESPACE,
                "req_params" to mapOf(
                    "speaker" to ApiConfig.TTS_DEFAULT_SPEAKER,
                    "audio_params" to mapOf(
                        "format" to ApiConfig.TTS_AUDIO_FORMAT,
                        "sample_rate" to sampleRate,
                    ),
                ),
            ),
        ).toByteArray(Charsets.UTF_8)
        return buildEventFrame(EVENT_START_SESSION, payload, sessionId)
    }

    private fun buildTaskRequest(sessionId: String, text: String): ByteArray {
        val payload = gson.toJson(
            mapOf(
                "user" to mapOf("uid" to "doubao_user"),
                "event" to EVENT_TASK_REQUEST,
                "namespace" to ApiConfig.TTS_NAMESPACE,
                "req_params" to mapOf(
                    "text" to text,
                    "speaker" to ApiConfig.TTS_DEFAULT_SPEAKER,
                    "audio_params" to mapOf(
                        "format" to ApiConfig.TTS_AUDIO_FORMAT,
                        "sample_rate" to sampleRate,
                    ),
                ),
            ),
        ).toByteArray(Charsets.UTF_8)
        return buildEventFrame(EVENT_TASK_REQUEST, payload, sessionId)
    }

    private fun buildFinishSession(sessionId: String): ByteArray =
        buildEventFrame(EVENT_FINISH_SESSION, "{}".toByteArray(Charsets.UTF_8), sessionId)

    // ---- AudioTrack 管理 ----

    private fun initAudioTrack() {
        releaseAudioTrack()
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFmt)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFmt)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
    }

    private fun writePcmData(data: ByteArray) {
        audioTrack?.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING)
    }

    /** 播报完成：让缓冲区数据播完后再释放，避免截断结尾 */
    private fun finishPlayback() {
        _isPlaying.value = false
        audioTrack?.let {
            try {
                // MODE_STREAM 下 stop() 会把已写入缓冲区的剩余音频播放完
                it.stop()
            } catch (_: IllegalStateException) {
            }
        }
        releaseAudioTrack()
    }

    private fun releaseAudioTrack() {
        audioTrack?.let {
            try {
                it.release()
            } catch (_: IllegalStateException) {
            }
        }
        audioTrack = null
    }

    // ---- 工具 ----

    private fun readInt32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private fun writeInt32(baos: ByteArrayOutputStream, value: Int) {
        baos.write((value shr 24) and 0xFF)
        baos.write((value shr 16) and 0xFF)
        baos.write((value shr 8) and 0xFF)
        baos.write(value and 0xFF)
    }

    private fun gzipDecompress(data: ByteArray): ByteArray {
        GZIPInputStream(java.io.ByteArrayInputStream(data)).use { gzipStream ->
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
        private const val TAG = "VolcTtsProvider"

        private const val PROTOCOL_VERSION = 0b0001
        private const val HEADER_SIZE_4 = 0b0001

        private const val MESSAGE_TYPE_FULL_CLIENT_REQUEST = 0b0001
        private const val MESSAGE_TYPE_AUDIO_ONLY_RESPONSE = 0b1011
        private const val MESSAGE_TYPE_ERROR = 0b1111

        private const val FLAG_WITH_EVENT = 0b0100

        private const val SERIALIZATION_JSON = 0b0001
        private const val COMPRESSION_NONE = 0b0000
        private const val COMPRESSION_GZIP = 0b0001

        // 事件码
        private const val EVENT_NONE = 0
        private const val EVENT_START_CONNECTION = 1
        private const val EVENT_FINISH_CONNECTION = 2
        private const val EVENT_CONNECTION_STARTED = 50
        private const val EVENT_CONNECTION_FAILED = 51
        private const val EVENT_CONNECTION_FINISHED = 52
        private const val EVENT_START_SESSION = 100
        private const val EVENT_FINISH_SESSION = 102
        private const val EVENT_SESSION_STARTED = 150
        private const val EVENT_SESSION_FINISHED = 152
        private const val EVENT_SESSION_FAILED = 153
        private const val EVENT_TASK_REQUEST = 200
        private const val EVENT_TTS_RESPONSE = 352

        private val CONNECTION_EVENTS = setOf(
            EVENT_START_CONNECTION,
            EVENT_FINISH_CONNECTION,
            EVENT_CONNECTION_STARTED,
            EVENT_CONNECTION_FAILED,
            EVENT_CONNECTION_FINISHED,
        )
    }
}
