package com.bytedace.doubaoapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.bytedace.doubaoapp.data.context.ContextPolicy
import com.bytedace.doubaoapp.data.repository.ChatRepository

val LocalAppServices = staticCompositionLocalOf<AppServices> {
    error("AppServices not provided")
}

val LocalChatRepository = staticCompositionLocalOf<ChatRepository> {
    error("ChatRepository not provided")
}

val LocalContextPolicy = staticCompositionLocalOf<ContextPolicy> {
    error("ContextPolicy not provided")
}

expect fun getScreenWidthFraction(): Float

@Composable
expect fun rememberImagePicker(onImagesPicked: (List<String>) -> Unit): () -> Unit

expect fun decodeBase64Image(dataUri: String): ByteArray?

expect class CoilImageModel(value: Any)

@Composable
expect fun PlatformAsyncImage(
    model: Any,
    contentDescription: String?,
    modifier: androidx.compose.ui.Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale,
)
