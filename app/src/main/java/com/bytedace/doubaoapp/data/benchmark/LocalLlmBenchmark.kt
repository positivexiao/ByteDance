package com.bytedace.doubaoapp.data.benchmark

import android.content.Context
import com.bytedace.doubaoapp.data.provider.LocalLlmProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class EffectQuestion(
    /** 报告表格中展示的题目原文 */
    val label: String,
    /** 实际发给模型的 prompt（可针对小模型优化措辞） */
    val prompt: String,
)

data class EffectAnswer(
    val question: String,
    val localAnswer: String,
    val generationMs: Long,
)

data class LocalLlmBenchmarkResult(
    val timestampMs: Long,
    val device: DeviceInfo,
    val buildType: String,
    val modelFileName: String,
    val modelFileSizeMb: Double,
    val modelCopyMs: Long?,
    val modelLoadMs: Long,
    val firstTokenLatencyMs: Double,
    val firstTokenSamplesMs: List<Long>,
    val promptProcessingTokensPerSec: Double?,
    val generationTokensPerSec: Double?,
    val nativeBenchReport: String,
    val peakMemoryMb: Long,
    val memoryAfterLoadMb: Long,
    val stabilityRounds: Int,
    val stabilityPassed: Boolean,
    val stabilityErrors: List<String>,
    val effectAnswers: List<EffectAnswer>,
) {
    fun toMarkdownReport(): String = LocalLlmReportFormatter.format(this)
}

enum class BenchmarkPhase {
    PREPARING,
    RELOADING_MODEL,
    NATIVE_BENCH,
    FIRST_TOKEN,
    STABILITY,
    EFFECT_EVAL,
    DONE,
}

object LocalLlmBenchmarkRunner {

    private const val BENCH_PP = 128
    private const val BENCH_TG = 128
    private const val BENCH_PL = 1
    private const val BENCH_NR = 3

    val effectQuestions: List<EffectQuestion> = listOf(
        EffectQuestion(
            label = "用一句话介绍你自己。",
            prompt = "用一句话介绍你自己。",
        ),
        EffectQuestion(
            label = "解释一下什么是端侧大模型。",
            prompt = "用两三句话解释什么是端侧大模型。",
        ),
        EffectQuestion(
            label = "给我 3 条学习 Kotlin Compose 的建议。",
            prompt = "请列出 3 条学习 Kotlin Compose 的建议，每条一行。",
        ),
        EffectQuestion(
            label = "把「今天天气不错，我想出去走走」改写得更正式。",
            prompt = "把下面这句话改写得更正式，只输出改写后的句子：今天天气不错，我想出去走走。",
        ),
        EffectQuestion(
            label = "计算：如果一本书每天读 35 页，12 天能读多少页？",
            prompt = "一本书每天读 35 页，连续读 12 天，一共读了多少页？请只回答数字。",
        ),
        EffectQuestion(
            label = "续写一句中文短故事：夜里，图书馆的灯突然亮了。",
            prompt = "请接着下面这句话继续写 2 到 3 句故事，不要重复原文：夜里，图书馆的灯突然亮了。",
        ),
        EffectQuestion(
            label = "总结这句话：端侧模型可以离线运行，但受限于设备性能。",
            prompt = "用不超过 30 个字概括下面这句话的意思，不要照抄原文：端侧模型可以离线运行，但受限于设备性能。",
        ),
        EffectQuestion(
            label = "说明本地模型相比远端模型的两个优点和两个缺点。",
            prompt = "请分别列出本地模型相比远端模型的 2 个优点和 2 个缺点，用条目回答。",
        ),
    )

    private val stabilityPrompts = listOf(
        "你好",
        "1+1等于几？请只回答数字。",
        "用一句话说明 Kotlin 是什么。",
        "写一句励志的话",
        "谢谢",
    )

    suspend fun run(
        context: Context,
        provider: LocalLlmProvider,
        modelPath: String,
        modelCopyMs: Long?,
        includeEffectEval: Boolean,
        onPhase: (BenchmarkPhase, String) -> Unit,
    ): LocalLlmBenchmarkResult {
        val peakTracker = DeviceMetrics.PeakTracker()
        peakTracker.sample()

        val modelFile = File(modelPath)
        require(modelFile.exists()) { "模型文件不存在：$modelPath" }

        val device = DeviceMetrics.collectDeviceInfo(context)
        onPhase(BenchmarkPhase.PREPARING, "采集设备信息…")

        onPhase(BenchmarkPhase.RELOADING_MODEL, "重新加载模型以测量加载耗时…")
        val loadMs = provider.reloadAndMeasureLoadMs(modelPath)
        peakTracker.sample()
        val memoryAfterLoad = DeviceMetrics.currentPssMb()

        onPhase(BenchmarkPhase.NATIVE_BENCH, "运行 llama.cpp 原生 benchmark（3 轮）…")
        val nativeReport = provider.runNativeBenchmark(
            pp = BENCH_PP,
            tg = BENCH_TG,
            pl = BENCH_PL,
            nr = BENCH_NR,
        )
        provider.resetConversationContextForBenchmark()
        peakTracker.sample()
        val ppSpeed = parseBenchSpeed(nativeReport, "pp")
        val tgSpeed = parseBenchSpeed(nativeReport, "tg")

        onPhase(BenchmarkPhase.FIRST_TOKEN, "测量首 token 延迟（3 次）…")
        val ttftSamples = provider.measureFirstTokenLatencyMs(repeats = 3)
        peakTracker.sample()
        val ttftAvg = if (ttftSamples.isEmpty()) 0.0 else ttftSamples.average()

        onPhase(BenchmarkPhase.STABILITY, "连续 5 轮对话稳定性测试…")
        val stabilityErrors = mutableListOf<String>()
        stabilityPrompts.forEachIndexed { index, prompt ->
            runCatching {
                val answer = provider.runStabilityRound(prompt)
                if (answer.isBlank()) {
                    stabilityErrors.add("第 ${index + 1} 轮输出为空")
                }
            }.onFailure { e ->
                stabilityErrors.add("第 ${index + 1} 轮失败：${e.message}")
            }
            peakTracker.sample()
        }

        val effectAnswers = mutableListOf<EffectAnswer>()
        if (includeEffectEval) {
            onPhase(BenchmarkPhase.EFFECT_EVAL, "运行效果评测问题集（8 题）…")
            effectQuestions.forEachIndexed { index, item ->
                val start = System.currentTimeMillis()
                val answer = runCatching {
                    provider.runStabilityRound(item.prompt, maxPredict = 256)
                }.getOrElse { e ->
                    stabilityErrors.add("效果题 ${index + 1} 失败：${e.message}")
                    "（生成失败：${e.message}）"
                }
                effectAnswers.add(
                    EffectAnswer(
                        question = item.label,
                        localAnswer = answer.take(500),
                        generationMs = System.currentTimeMillis() - start,
                    )
                )
                peakTracker.sample()
            }
        }

        onPhase(BenchmarkPhase.DONE, "评测完成")

        return LocalLlmBenchmarkResult(
            timestampMs = System.currentTimeMillis(),
            device = device,
            buildType = if (com.bytedace.doubaoapp.BuildConfig.DEBUG) "debug" else "release",
            modelFileName = modelFile.name,
            modelFileSizeMb = modelFile.length().toDouble() / (1024 * 1024),
            modelCopyMs = modelCopyMs,
            modelLoadMs = loadMs,
            firstTokenLatencyMs = ttftAvg,
            firstTokenSamplesMs = ttftSamples,
            promptProcessingTokensPerSec = ppSpeed,
            generationTokensPerSec = tgSpeed,
            nativeBenchReport = nativeReport,
            peakMemoryMb = peakTracker.peakMb(),
            memoryAfterLoadMb = memoryAfterLoad,
            stabilityRounds = stabilityPrompts.size,
            stabilityPassed = stabilityErrors.isEmpty(),
            stabilityErrors = stabilityErrors,
            effectAnswers = effectAnswers,
        )
    }

    /** 解析原生 bench 输出中 pp / tg 行的 t/s 均值。 */
    fun parseBenchSpeed(report: String, testType: String): Double? {
        val pattern = Regex("""\|\s*$testType\s+\d+\s*\|\s*([\d.]+)""")
        return pattern.find(report)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }
}

object LocalLlmReportFormatter {

    private fun formatMs(ms: Long?): String = when (ms) {
        null -> "未记录（模型已在私有目录或未通过文件选择器复制）"
        else -> "${ms} ms（${(ms / 1000.0).roundToInt()} s）"
    }

    private fun formatDouble(value: Double?, suffix: String = ""): String =
        if (value == null) "—" else "${"%.2f".format(Locale.US, value)}$suffix"

    fun format(result: LocalLlmBenchmarkResult): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(result.timestampMs))
        val cfg = LocalLlmProvider.LocalConfig()

        return buildString {
            appendLine("# 本地端侧大模型评测报告")
            appendLine()
            appendLine("> 由 App 内「性能评测」自动生成 · $date")
            appendLine()
            appendLine("## 接入概况")
            appendLine()
            appendLine("- 推理底座：llama.cpp Android library")
            appendLine("- App 模型来源：远端豆包模型 / 本地 llama.cpp 模型")
            appendLine("- 本地能力范围：仅支持文本对话")
            appendLine()
            appendLine("## 已确认模型参数")
            appendLine()
            appendLine("- 模型：${cfg.modelName} GGUF")
            appendLine("- 量化：${cfg.quantization}")
            appendLine("- 上下文长度：${cfg.contextLength}")
            appendLine("- 最大生成长度：${cfg.predictLength}")
            appendLine("- 温度：${cfg.temperature}")
            appendLine("- Top P：${cfg.topP}")
            appendLine("- 线程数：${cfg.threads}")
            appendLine()
            appendLine("## 测试环境")
            appendLine()
            appendLine("- 设备型号：${result.device.displayName()}")
            appendLine("- Android 版本：${result.device.androidVersion} (API ${result.device.sdkInt})")
            appendLine("- CPU 核心数：${result.device.cpuCores}")
            appendLine("- 设备总内存：${result.device.totalRamMb} MB")
            appendLine("- ABI：${result.device.abi}")
            appendLine("- 模型文件：${result.modelFileName}")
            appendLine("- 模型文件大小：${"%.2f".format(Locale.US, result.modelFileSizeMb)} MB")
            appendLine("- APK 构建类型：${result.buildType}")
            appendLine()
            appendLine("## 性能测试")
            appendLine()
            appendLine("| 指标 | 结果 | 备注 |")
            appendLine("| --- | --- | --- |")
            appendLine("| 模型复制耗时 | ${formatMs(result.modelCopyMs)} | 首次从文件选择器复制到私有目录 |")
            appendLine("| 模型加载耗时 | ${result.modelLoadMs} ms | 卸载后重新加载实测 |")
            appendLine(
                "| 首 token 延迟 | ${"%.0f".format(Locale.US, result.firstTokenLatencyMs)} ms | " +
                    "3 次样本：${result.firstTokenSamplesMs.joinToString()} |"
            )
            appendLine(
                "| 提示词处理速度 (pp) | ${formatDouble(result.promptProcessingTokensPerSec, " t/s")} | " +
                    "llama.cpp bench pp=$BENCH_PP, nr=$BENCH_NR |"
            )
            appendLine(
                "| 生成速度 (tg) | ${formatDouble(result.generationTokensPerSec, " t/s")} | " +
                    "llama.cpp bench tg=$BENCH_TG, nr=$BENCH_NR，取 3 次平均 |"
            )
            appendLine("| 加载后内存 (PSS) | ${result.memoryAfterLoadMb} MB | Debug.getMemoryInfo |")
            appendLine("| 峰值内存 (PSS) | ${result.peakMemoryMb} MB | 评测全程采样峰值 |")
            appendLine(
                "| 连续 ${result.stabilityRounds} 轮对话稳定性 | " +
                    "${if (result.stabilityPassed) "通过" else "未通过"} | " +
                    "${result.stabilityErrors.joinToString("; ").ifBlank { "无异常" }} |"
            )
            appendLine()
            appendLine("### 原生 benchmark 原始输出")
            appendLine()
            appendLine("```")
            appendLine(result.nativeBenchReport.trim())
            appendLine("```")
            appendLine()
            if (result.effectAnswers.isNotEmpty()) {
                appendLine("## 效果测试问题集")
                appendLine()
                appendLine("| 编号 | 问题 | 本地回答摘要 | 生成耗时 | 远端回答摘要 | 结论 |")
                appendLine("| --- | --- | --- | --- | --- | --- |")
                result.effectAnswers.forEachIndexed { index, item ->
                    val summary = item.localAnswer
                        .replace("\n", " ")
                        .take(80)
                        .let { if (item.localAnswer.length > 80) "$it…" else it }
                    appendLine(
                        "| ${index + 1} | ${item.question} | $summary | ${item.generationMs} ms | 待填写 | 待填写 |"
                    )
                }
                appendLine()
            }
            appendLine("## 初步结论")
            appendLine()
            appendLine(
                "本地模型在 ${result.device.displayName()} 上完成加载与推理评测。" +
                    "生成速度约 ${formatDouble(result.generationTokensPerSec, " tokens/s")}，" +
                    "首 token 延迟约 ${"%.0f".format(Locale.US, result.firstTokenLatencyMs)} ms。" +
                    if (result.stabilityPassed) "稳定性测试通过。" else "稳定性测试存在问题，请查看上表。"
            )
        }
    }

    private const val BENCH_PP = 128
    private const val BENCH_TG = 128
    private const val BENCH_NR = 3
}
