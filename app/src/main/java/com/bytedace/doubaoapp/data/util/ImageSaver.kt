package com.bytedace.doubaoapp.data.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ImageSaver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun saveImageFromUrl(context: Context, imageUrl: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val bytes = downloadBytes(imageUrl)
                val mimeType = guessMimeType(imageUrl, bytes)
                val extension = extensionForMime(mimeType)
                val fileName = "doubao_${System.currentTimeMillis()}.$extension"

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/Doubao",
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                ) ?: return@withContext Result.failure(IllegalStateException("无法创建媒体文件"))

                resolver.openOutputStream(uri)?.use { output ->
                    output.write(bytes)
                } ?: return@withContext Result.failure(IllegalStateException("无法写入文件"))

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Result.success(uri)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun downloadBytes(imageUrl: String): ByteArray {
        if (imageUrl.startsWith("data:")) {
            val marker = "base64,"
            val idx = imageUrl.indexOf(marker)
            if (idx < 0) throw IllegalArgumentException("无效的图片数据")
            return android.util.Base64.decode(
                imageUrl.substring(idx + marker.length),
                android.util.Base64.DEFAULT,
            )
        }

        val request = Request.Builder().url(imageUrl).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("下载失败: ${response.code}")
        }
        return response.body?.bytes() ?: throw IllegalStateException("图片内容为空")
    }

    private fun guessMimeType(imageUrl: String, bytes: ByteArray): String {
        if (imageUrl.startsWith("data:")) {
            val end = imageUrl.indexOf(';').takeIf { it > 5 } ?: imageUrl.length
            return imageUrl.substring(5, end)
        }
        val headerMime = when {
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes.size >= 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "image/webp"
            else -> null
        }
        if (headerMime != null) return headerMime
        return when {
            imageUrl.contains(".png", ignoreCase = true) -> "image/png"
            imageUrl.contains(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }
    }

    private fun extensionForMime(mimeType: String): String = when {
        mimeType.contains("png") -> "png"
        mimeType.contains("webp") -> "webp"
        else -> "jpg"
    }
}
