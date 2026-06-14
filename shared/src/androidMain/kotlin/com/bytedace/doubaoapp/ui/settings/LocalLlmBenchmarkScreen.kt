package com.bytedace.doubaoapp.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bytedace.doubaoapp.DoubaoApp
import com.bytedace.doubaoapp.data.benchmark.BenchmarkPhase
import com.bytedace.doubaoapp.data.benchmark.LocalLlmBenchmarkRunner
import com.bytedace.doubaoapp.data.benchmark.LocalLlmBenchmarkResult
import com.bytedace.doubaoapp.data.prefs.SettingsPrefs
import com.bytedace.doubaoapp.ui.components.CenteredAppTopBar
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLlmBenchmarkScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as DoubaoApp
    val prefs = remember { SettingsPrefs(context) }
    val scope = rememberCoroutineScope()

    val modelPath = remember { prefs.getLocalModelPath() }
    var includeEffectEval by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var phaseText by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<LocalLlmBenchmarkResult?>(null) }
    var reportMarkdown by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        CenteredAppTopBar(
            title = "本地模型性能评测",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "在真机上自动测量模型加载、首 token 延迟、llama.cpp 原生 tokens/s 与稳定性，并生成可复制的 Markdown 报告。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (modelPath.isBlank()) {
                    "模型：未选择，请先在设置页选择 GGUF 文件"
                } else {
                    "模型：${File(modelPath).name}"
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "包含效果评测（8 题，耗时更长）",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = includeEffectEval,
                    onCheckedChange = { includeEffectEval = it },
                    enabled = !running,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (running) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = phaseText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "评测期间请勿切换应用，预计 2–8 分钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Button(
                    onClick = {
                        if (modelPath.isBlank()) {
                            errorText = "请先在设置页选择 GGUF 模型文件"
                            return@Button
                        }
                        if (!File(modelPath).exists()) {
                            errorText = "模型文件不存在，请重新选择"
                            return@Button
                        }
                        running = true
                        errorText = null
                        report = null
                        reportMarkdown = ""
                        scope.launch {
                            runCatching {
                                LocalLlmBenchmarkRunner.run(
                                    context = context,
                                    provider = app.localLlmProvider,
                                    modelPath = modelPath,
                                    modelCopyMs = prefs.getModelCopyDurationMs(),
                                    includeEffectEval = includeEffectEval,
                                    onPhase = { phase, message ->
                                        phaseText = when (phase) {
                                            BenchmarkPhase.PREPARING -> "准备中…"
                                            BenchmarkPhase.RELOADING_MODEL -> "测量模型加载…"
                                            BenchmarkPhase.NATIVE_BENCH -> "原生 benchmark…"
                                            BenchmarkPhase.FIRST_TOKEN -> "测量首 token…"
                                            BenchmarkPhase.STABILITY -> "稳定性测试…"
                                            BenchmarkPhase.EFFECT_EVAL -> "效果评测…"
                                            BenchmarkPhase.DONE -> "完成"
                                        } + " $message"
                                    },
                                )
                            }.onSuccess { result ->
                                report = result
                                reportMarkdown = result.toMarkdownReport()
                            }.onFailure { e ->
                                errorText = e.message ?: "评测失败"
                            }
                            running = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = modelPath.isNotBlank(),
                ) {
                    Text("开始性能评测")
                }
            }

            errorText?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (report != null && reportMarkdown.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        copyToClipboard(context, reportMarkdown)
                        Toast.makeText(context, "报告已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("复制 Markdown 报告")
                }

                Spacer(modifier = Modifier.height(12.dp))

                report?.let { r ->
                    Text(
                        text = "摘要：加载 ${r.modelLoadMs} ms · 首 token ${"%.0f".format(r.firstTokenLatencyMs)} ms · " +
                            "生成 ${r.generationTokensPerSec?.let { "%.1f t/s".format(it) } ?: "—"} · " +
                            "峰值内存 ${r.peakMemoryMb} MB",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = reportMarkdown,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("local_llm_report", text))
}
