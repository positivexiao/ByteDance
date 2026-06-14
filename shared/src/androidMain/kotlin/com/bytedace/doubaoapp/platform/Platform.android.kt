package com.bytedace.doubaoapp.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.bytedace.doubaoapp.data.util.ImageEncoder
import com.bytedace.doubaoapp.data.util.ImageSaver
import java.io.File

actual fun getScreenWidthFraction(): Float {
    return 0.8f
}

@Composable
actual fun rememberImagePicker(onImagesPicked: (List<String>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        onImagesPicked(uris.map { it.toString() })
    }
    return remember { { launcher.launch("image/*") } }
}

actual fun decodeBase64Image(dataUri: String): ByteArray? {
    if (!dataUri.startsWith("data:")) return null
    val marker = "base64,"
    val idx = dataUri.indexOf(marker)
    if (idx < 0) return null
    return try {
        Base64.decode(dataUri.substring(idx + marker.length), Base64.DEFAULT)
    } catch (_: Exception) {
        null
    }
}

actual class CoilImageModel actual constructor(value: Any) {
    val value: Any = value
}

@Composable
actual fun PlatformAsyncImage(
    model: Any,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val coilModel = when (model) {
        is CoilImageModel -> model.value
        is String -> if (model.startsWith("content://") || model.startsWith("file://")) Uri.parse(model) else model
        else -> model
    }
    AsyncImage(
        model = coilModel,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

fun showAndroidToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun copyToAndroidClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
}

suspend fun encodeAndroidImageToDataUri(context: Context, imageRef: String): String {
    return ImageEncoder.encodeToDataUri(context.contentResolver, Uri.parse(imageRef))
}

suspend fun saveAndroidImageFromUrl(context: Context, url: String): Result<Unit> {
    return ImageSaver.saveImageFromUrl(context, url).map { }
}

@Composable
fun getScreenWidthFractionComposable(): Float {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return screenWidth * 0.8f
}

suspend fun copyModelFileToPrivateDir(context: Context, uriString: String): String {
    val uri = Uri.parse(uriString)
    val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
    val sourceName = queryAndroidDisplayName(context, uri).takeIf { it.endsWith(".gguf", ignoreCase = true) }
        ?: "local_model.gguf"
    val target = File(modelsDir, sourceName.sanitizeFileName())
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "无法打开模型文件" }
        target.outputStream().use { output -> input.copyTo(output) }
    }
    return target.absolutePath
}

private fun queryAndroidDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index).orEmpty()
        }
    }
    return ""
}

private fun String.sanitizeFileName(): String {
    return replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "local_model.gguf" }
}
