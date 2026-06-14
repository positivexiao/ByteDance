package com.bytedace.doubaoapp.data.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * 图片编码工具类。
 * 将图片压缩并转为 Base64 字符串，用于发送给多模态 API。
 */
object ImageEncoder {

    /** 最大图片尺寸（px），发送前压缩 */
    private const val MAX_SIZE = 1024

    /** JPEG 压缩质量 (0-100) */
    private const val JPEG_QUALITY = 80

    /**
     * 将 Uri 指向的图片压缩并编码为 Base64 data URI。
     * @return "data:image/jpeg;base64,{BASE64_STRING}" 格式字符串
     */
    fun encodeToDataUri(contentResolver: ContentResolver, uri: Uri): String {
        val bitmap = decodeAndResize(contentResolver, uri)
        val base64 = encodeBitmapToBase64(bitmap)
        bitmap.recycle()
        return "data:image/jpeg;base64,$base64"
    }

    /**
     * 将 Uri 指向的图片压缩并编码为纯 Base64 字符串。
     */
    fun encodeToBase64(contentResolver: ContentResolver, uri: Uri): String {
        val bitmap = decodeAndResize(contentResolver, uri)
        val base64 = encodeBitmapToBase64(bitmap)
        bitmap.recycle()
        return base64
    }

    private fun decodeAndResize(contentResolver: ContentResolver, uri: Uri): Bitmap {
        contentResolver.openInputStream(uri).use { inputStream ->
            // 先解码尺寸
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)

            // 计算采样率
            var sampleSize = 1
            while (options.outWidth / sampleSize > MAX_SIZE || options.outHeight / sampleSize > MAX_SIZE) {
                sampleSize *= 2
            }

            // 解码图片
            contentResolver.openInputStream(uri).use { realStream ->
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                return BitmapFactory.decodeStream(realStream, null, decodeOptions)
                    ?: throw IllegalArgumentException("Failed to decode image from URI: $uri")
            }
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}