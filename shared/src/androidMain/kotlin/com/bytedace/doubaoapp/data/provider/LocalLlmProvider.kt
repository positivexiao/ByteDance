package com.bytedace.doubaoapp.data.provider

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.UnsupportedArchitectureException
import com.bytedace.doubaoapp.data.api.ApiConfig
import com.bytedace.doubaoapp.data.context.ContextPolicy
import com.bytedace.doubaoapp.data.model.ChatMessage
import com.bytedace.doubaoapp.data.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * llama.cpp 本地端侧大模型 Provider。
 *
 * 本地模式只支持文本对话；图片、联网、生图、语音等能力由 UI/ChatViewModel 禁用或拦截。
 * 初始确认参数：
 * - 模型：Qwen2.5-0.5B-Instruct GGUF
 * - 量化：Q4_K_M
 * - context=2048, predict=512, temperature=0.7, topP=0.9, threads=4
 *
 * 当前 llama.android 示例库只暴露 predictLength，temperature/topP/threads/context 先作为配置展示；
 * 若后续需要实际控制，需要扩展 native ai_chat.cpp。
 */
class LocalLlmProvider(
    context: Context,
) : LlmProvider {

    data class LocalConfig(
        val modelName: String = "Qwen2.5-0.5B-Instruct",
        val quantization: String = "Q4_K_M",
        val contextLength: Int = 2048,
        val predictLength: Int = 512,
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val threads: Int = 4,
    )

    sealed class LocalState {
        data object NoModel : LocalState()
        data object Loading : LocalState()
        data class Ready(val modelPath: String) : LocalState()
        data class Generating(val modelPath: String) : LocalState()
        data class Error(val message: String) : LocalState()
    }

    private val appContext = context.applicationContext
    private val engine: InferenceEngine by lazy { AiChat.getInferenceEngine(appContext) }

    private val _state = MutableStateFlow<LocalState>(LocalState.NoModel)
    val state = _state.asStateFlow()

    private var loadedModelPath: String? = null
    private var sessionBootstrapNeeded = false
    private val inferenceMutex = Mutex()
    val config = LocalConfig()

    /** 切换会话后标记：下次推理需重新加载模型并注入历史上下文。 */
    fun markSessionChanged() {
        sessionBootstrapNeeded = true
    }

    suspend fun loadModel(path: String) = inferenceMutex.withLock {
        val file = File(path)
        require(file.exists() && file.isFile) { "模型文件不存在：$path" }

        if (loadedModelPath == path && _state.value is LocalState.Ready) {
            return@withLock
        }

        _state.value = LocalState.Loading
        try {
            if (loadedModelPath != null) {
                runCatching { engine.cleanUp() }
                loadedModelPath = null
            }
            engine.loadModel(path)
            engine.setSystemPrompt(ApiConfig.DEFAULT_SYSTEM_PROMPT)
            loadedModelPath = path
            _state.value = LocalState.Ready(path)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            runCatching { engine.cleanUp() }
            loadedModelPath = null
            _state.value = LocalState.Error(loadErrorMessage(e))
            throw e
        }
    }

    fun isModelReady(): Boolean = loadedModelPath != null && state.value is LocalState.Ready

    override fun chatStream(
        messages: List<ChatMessage>,
        contextPolicy: ContextPolicy,
        enableWebSearch: Boolean
    ): Flow<String> = flow {
        inferenceMutex.withLock {
            val path = loadedModelPath
            if (path == null) {
                emit("本地模型尚未加载，请到设置页选择并加载 GGUF 模型。")
                return@withLock
            }
            if (_state.value !is LocalState.Ready) {
                emit("本地模型当前不可用，请稍后重试；如仍失败，请到设置页重新加载 GGUF 模型。")
                return@withLock
            }
            _state.value = LocalState.Generating(path)
            try {
                val prompt = prepareChatPrompt(contextPolicy.trimContext(messages))
                engine.sendUserPrompt(prompt, predictLength = config.predictLength).collect { token ->
                    emit(token)
                }
                _state.value = LocalState.Ready(path)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = LocalState.Error(e.message ?: "本地模型生成失败")
                throw e
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun chatCompletion(
        messages: List<ChatMessage>,
        contextPolicy: ContextPolicy,
        enableWebSearch: Boolean
    ): String {
        val sb = StringBuilder()
        chatStream(messages, contextPolicy, enableWebSearch).collect { sb.append(it) }
        return sb.toString()
    }

    override fun supportsImage(): Boolean = false

    override fun supportsToolUse(): Boolean = false

    override fun modelName(): String = "llama.cpp 本地 (${config.modelName} ${config.quantization})"

    override fun modelSource(): String = "local"

    /** 卸载后重新加载模型，返回加载耗时（毫秒）。 */
    suspend fun reloadAndMeasureLoadMs(path: String): Long {
        inferenceMutex.withLock {
            if (loadedModelPath != null) {
                runCatching { engine.cleanUp() }
                loadedModelPath = null
                _state.value = LocalState.NoModel
            }
        }
        val start = SystemClock.elapsedRealtime()
        loadModel(path)
        return SystemClock.elapsedRealtime() - start
    }

    /**
     * 调用 llama.cpp 原生 bench（pp=提示词处理, tg=文本生成）。
     * 返回 Markdown 表格字符串。
     */
    suspend fun runNativeBenchmark(
        pp: Int = 128,
        tg: Int = 128,
        pl: Int = 1,
        nr: Int = 3,
    ): String = inferenceMutex.withLock {
        val path = loadedModelPath
            ?: throw IllegalStateException("本地模型尚未加载")
        check(_state.value is LocalState.Ready || _state.value is LocalState.Generating) {
            "模型状态不可用：${_state.value}"
        }
        _state.value = LocalState.Ready(path)
        engine.bench(pp = pp, tg = tg, pl = pl, nr = nr)
    }

    /** 测量首 token 延迟（毫秒），默认重复 3 次取平均。 */
    suspend fun measureFirstTokenLatencyMs(
        prompt: String = "用一句话介绍你自己。",
        repeats: Int = 3,
        maxPredict: Int = 32,
    ): List<Long> {
        val latencies = mutableListOf<Long>()
        inferenceMutex.withLock {
            val path = loadedModelPath
                ?: throw IllegalStateException("本地模型尚未加载")
            repeat(repeats) {
                reloadModelContext()
                val start = SystemClock.elapsedRealtime()
                var recorded = false
                engine.sendUserPrompt(prompt, predictLength = maxPredict).collect { token ->
                    if (!recorded && token.isNotEmpty()) {
                        latencies.add(SystemClock.elapsedRealtime() - start)
                        recorded = true
                    }
                }
                _state.value = LocalState.Ready(path)
            }
        }
        return latencies
    }

    /** bench 后恢复对话状态，供后续 TTFT / 效果评测使用。 */
    suspend fun resetConversationContextForBenchmark() {
        inferenceMutex.withLock {
            if (loadedModelPath == null) return@withLock
            reloadModelContext()
        }
    }

    /** 单轮短回答，用于稳定性/效果评测；每轮前重新加载模型以获得干净上下文。 */
    suspend fun runStabilityRound(prompt: String, maxPredict: Int = 64): String =
        inferenceMutex.withLock {
            val path = loadedModelPath
                ?: throw IllegalStateException("本地模型尚未加载")
            reloadModelContext()
            val sb = StringBuilder()
            engine.sendUserPrompt(prompt, predictLength = maxPredict).collect { sb.append(it) }
            _state.value = LocalState.Ready(path)
            sb.toString().trim()
        }

    private fun isLocalStatusMessage(message: ChatMessage): Boolean {
        if (message.role != MessageRole.ASSISTANT) return false

        val content = message.content.trim()
        return content == "本地模型尚未加载，请到设置页选择并加载 GGUF 模型。" ||
            content == "本地模型当前不可用，请稍后重试；如仍失败，请到设置页重新加载 GGUF 模型。"
    }

    private fun loadErrorMessage(error: Throwable): String {
        return when (error) {
            is UnsupportedArchitectureException ->
                "模型架构或 GGUF 格式暂不被当前 llama.cpp 构建支持，请确认文件是 Qwen2.5 GGUF 且未损坏。"
            is OutOfMemoryError ->
                "设备内存不足，建议关闭后台应用，或改用更小量化/更小模型。"
            else -> error.message ?: "加载本地模型失败，建议查看 Logcat 中 InferenceEngineImpl/llama.cpp 日志。"
        }
    }

    /**
     * llama.android 只允许在 loadModel 后调用一次 setSystemPrompt。
     * 常规多轮对话由 native 侧累积上下文，因此默认只发送最新用户消息。
     * 切换会话后首次推理会重新加载模型，并把 Room 中的历史拼入单条 prompt。
     */
    private suspend fun prepareChatPrompt(messages: List<ChatMessage>): String {
        val relevantMessages = messages.filter {
            it.content.isNotBlank() && !isLocalStatusMessage(it)
        }
        val latestUserMessage = relevantMessages.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        val priorMessages = relevantMessages.dropLast(1)

        if (sessionBootstrapNeeded && priorMessages.isNotEmpty()) {
            reloadModelContext()
            sessionBootstrapNeeded = false
            return buildFullHistoryPrompt(priorMessages, latestUserMessage)
        }

        sessionBootstrapNeeded = false
        return latestUserMessage
    }

    private fun buildFullHistoryPrompt(
        priorMessages: List<ChatMessage>,
        latestUserMessage: String,
    ): String = buildString {
        appendLine("请根据以下对话历史回答最后一个用户问题。直接给出回答，不要重复用户的问题或原文。")
        priorMessages.forEach { msg ->
            when (msg.role) {
                MessageRole.USER -> appendLine("用户：${msg.content}")
                MessageRole.ASSISTANT -> appendLine("助手：${msg.content}")
                MessageRole.SYSTEM -> Unit
            }
        }
        appendLine()
        appendLine("用户：$latestUserMessage")
        append("助手：")
    }

    /** 卸载并重新加载模型，以便再次设置 system prompt 与清空 native KV cache。 */
    private suspend fun reloadModelContext() {
        val path = loadedModelPath ?: throw IllegalStateException("本地模型尚未加载")
        runCatching { engine.cleanUp() }
        loadedModelPath = null
        engine.loadModel(path)
        engine.setSystemPrompt(ApiConfig.DEFAULT_SYSTEM_PROMPT)
        loadedModelPath = path
        _state.value = LocalState.Ready(path)
    }
}
