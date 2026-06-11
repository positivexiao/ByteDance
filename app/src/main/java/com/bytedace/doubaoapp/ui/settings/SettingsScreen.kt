package com.bytedace.doubaoapp.ui.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.bytedace.doubaoapp.ui.components.CenteredAppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bytedace.doubaoapp.DoubaoApp
import com.bytedace.doubaoapp.data.provider.LocalLlmProvider
import com.bytedace.doubaoapp.data.prefs.ApiCredentialsResolver
import com.bytedace.doubaoapp.data.prefs.SettingsPrefs
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToBenchmark: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as DoubaoApp
    val prefs = remember { SettingsPrefs(context) }
    val scope = rememberCoroutineScope()

    val arkApiKeyInput = remember { mutableStateOf("") }
    val talkApiKeyInput = remember { mutableStateOf("") }
    val hasSavedArkKey = remember { mutableStateOf(prefs.hasArkApiKey()) }
    val hasSavedTalkKey = remember { mutableStateOf(prefs.hasTalkApiKey()) }
    val chatEndpoint = remember { mutableStateOf(prefs.getChatEndpointId()) }
    val imageGenEndpoint = remember { mutableStateOf(prefs.getImageGenEndpointId()) }
    val modelSource = remember { mutableStateOf(prefs.getModelSource()) }
    val localModelPath = remember { mutableStateOf(prefs.getLocalModelPath()) }
    val localStatus = remember { mutableStateOf(localStateText(app.localLlmProvider.state.value)) }
    val providerState by app.localLlmProvider.state.collectAsState()

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    localStatus.value = "正在复制模型文件..."
                    val start = SystemClock.elapsedRealtime()
                    val path = copyModelToPrivateDir(context, uri)
                    val copyMs = SystemClock.elapsedRealtime() - start
                    path to copyMs
                }.onSuccess { (path, copyMs) ->
                    localModelPath.value = path
                    prefs.saveLocalModelPath(path)
                    prefs.saveModelCopyDurationMs(copyMs)
                    localStatus.value = "已选择：${File(path).name}（复制 ${copyMs} ms）"
                }.onFailure { e ->
                    localStatus.value = "模型复制失败：${e.message}"
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        CenteredAppTopBar(
            title = "设置",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "API 密钥",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ark Key 用于聊天、联网、识图与生图；Talk Key 用于语音识别与播报。已保存的 Key 不会回显，留空则不修改。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = arkApiKeyInput.value,
            onValueChange = { arkApiKeyInput.value = it },
            label = { Text("Ark API Key") },
            placeholder = {
                Text(if (hasSavedArkKey.value) "已配置（留空则不修改）" else "请输入 Ark API Key")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )

        if (hasSavedArkKey.value) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    prefs.clearArkApiKey()
                    hasSavedArkKey.value = false
                    arkApiKeyInput.value = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清除 Ark API Key")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = talkApiKeyInput.value,
            onValueChange = { talkApiKeyInput.value = it },
            label = { Text("Talk API Key") },
            placeholder = {
                Text(if (hasSavedTalkKey.value) "已配置（留空则不修改）" else "请输入 Talk API Key")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )

        if (hasSavedTalkKey.value) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    prefs.clearTalkApiKey()
                    hasSavedTalkKey.value = false
                    talkApiKeyInput.value = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清除 Talk API Key")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "推理接入点配置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "在火山方舟控制台创建推理接入点后，将得到的 ep-xxx ID 填入下方",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = chatEndpoint.value,
            onValueChange = { chatEndpoint.value = it },
            label = { Text("文本对话接入点 ID (Doubao-seed-2.0-lite)") },
            placeholder = { Text("ep-xxxxxxxx") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = imageGenEndpoint.value,
            onValueChange = { imageGenEndpoint.value = it },
            label = { Text("图片生成接入点 ID (Seedream 5.0 lite)") },
            placeholder = { Text("ep-xxxxxxxx") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "端侧大模型",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "本地模型仅支持文本对话。已确认：Qwen2.5-0.5B-Instruct / Q4_K_M / context=2048 / predict=512 / temperature=0.7 / topP=0.9 / threads=4",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                modelSource.value = "remote"
                prefs.saveModelSource("remote")
                app.modelSwitchManager.switchProvider("remote")
                localStatus.value = localStateText(app.localLlmProvider.state.value)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (modelSource.value == "remote") "当前：远端豆包模型" else "切换到远端豆包模型")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                modelSource.value = "local"
                prefs.saveModelSource("local")
                app.modelSwitchManager.switchProvider("local")
                localStatus.value = localStateText(app.localLlmProvider.state.value)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (modelSource.value == "local") "当前：本地 llama.cpp 模型" else "切换到本地 llama.cpp 模型")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (localModelPath.value.isBlank()) "未选择 GGUF 模型" else "模型文件：${File(localModelPath.value).name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { modelPicker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("选择 GGUF 模型文件")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val path = localModelPath.value
                if (path.isBlank()) {
                    localStatus.value = "请先选择 GGUF 模型"
                    return@Button
                }
                localStatus.value = "正在加载模型..."
                scope.launch {
                    runCatching {
                        app.localLlmProvider.loadModel(path)
                    }.onSuccess {
                        localStatus.value = "模型已加载，可切换到本地模型对话"
                    }.onFailure {
                        localStatus.value = localStateText(app.localLlmProvider.state.value)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("加载本地模型")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "状态：${localStateText(providerState)}；${localStatus.value}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateToBenchmark,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("本地模型性能评测")
        }

        }


        Button(
            onClick = {
                if (arkApiKeyInput.value.isNotBlank()) {
                    prefs.saveArkApiKey(arkApiKeyInput.value.trim())
                    hasSavedArkKey.value = true
                    arkApiKeyInput.value = ""
                }
                if (talkApiKeyInput.value.isNotBlank()) {
                    prefs.saveTalkApiKey(talkApiKeyInput.value.trim())
                    hasSavedTalkKey.value = true
                    talkApiKeyInput.value = ""
                }
                prefs.saveChatEndpointId(chatEndpoint.value.trim())
                prefs.saveImageGenEndpointId(imageGenEndpoint.value.trim())
                app.reconfigureRemoteServices(ApiCredentialsResolver.resolve(prefs))
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text("保存")
        }
    }
}

private fun localStateText(state: LocalLlmProvider.LocalState): String {
    return when (state) {
        LocalLlmProvider.LocalState.NoModel -> "未加载模型"
        LocalLlmProvider.LocalState.Loading -> "正在加载模型..."
        is LocalLlmProvider.LocalState.Ready -> "模型已加载：${File(state.modelPath).name}"
        is LocalLlmProvider.LocalState.Generating -> "正在生成..."
        is LocalLlmProvider.LocalState.Error -> "错误：${state.message}"
    }
}

private suspend fun copyModelToPrivateDir(context: android.content.Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
    val sourceName = queryDisplayName(context, uri).takeIf { it.endsWith(".gguf", ignoreCase = true) }
        ?: "qwen2_5_0_5b_instruct_q4_k_m.gguf"
    val target = File(modelsDir, sourceName.sanitizeFileName())
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "无法打开模型文件" }
        target.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    target.absolutePath
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index).orEmpty()
        }
    }
    return ""
}

private fun String.sanitizeFileName(): String {
    return replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "local_model.gguf" }
}