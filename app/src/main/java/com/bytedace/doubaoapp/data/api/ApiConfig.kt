package com.bytedace.doubaoapp.data.api

/**
 * API 配置常量。
 * 火山引擎 Ark API + 语音服务相关配置项。
 */
object ApiConfig {

    /** Ark API 基础 URL（兼容 OpenAI 格式） */
    const val ARK_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"

    /** Chat Completions 请求路径 */
    const val CHAT_COMPLETIONS_PATH = "/chat/completions"

    /**
     * Responses API 请求路径 —— 联网搜索(web_search)等内置工具仅在该接口可用。
     * 标准 chat/completions 的 tools 只支持 type=function，不支持 web_search 插件。
     */
    const val RESPONSES_PATH = "/responses"

    /** 图片生成请求路径 */
    const val IMAGE_GENERATIONS_PATH = "/images/generations"

    // ---- 推理接入点（在设置页配置，默认为空） ----

    /** 文本对话接入点 ID 默认值 */
    const val DEFAULT_ENDPOINT_ID = ""

    /** 图片生成接入点 ID 默认值 */
    const val IMAGE_GEN_ENDPOINT_ID = ""

    /**
     * Seedream 5.0 lite 图片尺寸（2K 分辨率，模型按提示词语义推断宽高比）。
     */
    const val IMAGE_GEN_SIZE = "2K"

    // ---- 语音识别 ASR（使用 Talk API Key） ----

    /** ASR WebSocket 地址 — 大模型流式语音识别（流式输入流式返回，适合实时录音） */
    const val ASR_ADDRESS = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel"

    /** ASR 资源 ID — 大模型流式语音识别 小时版 */
    const val ASR_RESOURCE_ID_V2_DURATION = "volc.bigasr.sauc.duration"

    // ---- 语音合成 TTS（大模型语音合成 2.0 / v3 双向流式，使用 Talk API Key） ----
    // 参考文档：https://www.volcengine.com/docs/6561/1329505

    /** TTS WebSocket 地址 — v3 双向流式 */
    const val TTS_ADDRESS = "wss://openspeech.bytedance.com/api/v3/tts/bidirection"

    /**
     * TTS Resource Id — 决定模型版本/音色范围/计费项，必须与发音人配对：
     *   seed-tts-2.0           ↔ uranus 系列 2.0 音色（如 zh_female_vv_uranus_bigtts）
     *   volc.service_type.10029 ↔ moon/mars/wvae 等通用大模型音色
     */
    const val TTS_RESOURCE_ID = "seed-tts-2.0"

    /** TTS 命名空间 — 双向流式固定为 BidirectionalTTS */
    const val TTS_NAMESPACE = "BidirectionalTTS"

    /** TTS 默认发音人（大模型语音合成 2.0 uranus 女声，已实测可出声） */
    const val TTS_DEFAULT_SPEAKER = "zh_female_vv_uranus_bigtts"

    /** TTS 音频输出格式（PCM 直接喂给 AudioTrack 播放） */
    const val TTS_AUDIO_FORMAT = "pcm"

    /** TTS 音频采样率 */
    const val TTS_SAMPLE_RATE = 24000

    /** 默认 System Prompt */
    const val DEFAULT_SYSTEM_PROMPT = "你是豆包，是由字节跳动开发的AI智能助手。你是一位知识渊博、乐于助人的朋友，总是耐心、细致地回答用户的问题。"

    // ---- 网络配置 ----

    /** OkHttp 连接超时(ms) */
    const val CONNECT_TIMEOUT_MS = 15_000L

    /** OkHttp 读取超时(ms) — 流式场景需要更长 */
    const val READ_TIMEOUT_MS = 60_000L

    /** OkHttp 写入超时(ms) */
    const val WRITE_TIMEOUT_MS = 15_000L

    /** OkHttp 连接池最大连接数 */
    const val CONNECTION_POOL_MAX = 5

    /** OkHttp 连接池 keep-alive 分钟数 */
    const val CONNECTION_POOL_KEEP_ALIVE_MIN = 5L
}