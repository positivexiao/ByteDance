package com.bytedace.doubaoapp.ui.chat

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import coil.compose.AsyncImage
import com.bytedace.doubaoapp.data.model.ChatMessage
import com.bytedace.doubaoapp.data.model.MessageRole
import com.bytedace.doubaoapp.ui.theme.AssistantBubbleBg
import com.bytedace.doubaoapp.ui.theme.AssistantBubbleText
import com.bytedace.doubaoapp.ui.theme.CopyColor
import com.bytedace.doubaoapp.ui.theme.DislikeColor
import com.bytedace.doubaoapp.ui.theme.LikeColor
import com.bytedace.doubaoapp.ui.theme.UserBubbleBg
import com.bytedace.doubaoapp.ui.theme.UserBubbleText

@Composable
fun MessageItem(
    message: ChatMessage,
    onCopy: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onRetry: () -> Unit,
    onPlayAudio: () -> Unit,
    onStopAudio: () -> Unit = {},
    onFollowUp: (String) -> Unit = {},
    onSaveImage: (String) -> Unit = {},
    isPlaying: Boolean = false,
    showAudioActions: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == MessageRole.USER
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // AI 消息：联网搜索标识
        if (!isUser && message.usedWebSearch) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🌐",
                    fontSize = TextUnit(10f, TextUnitType.Sp),
                )
                Text(
                    text = "已搜索网页",
                    color = LikeColor,
                    fontSize = TextUnit(10f, TextUnitType.Sp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // 消息气泡
        Box(
            modifier = Modifier
                .widthIn(max = screenWidth * 0.8f)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 20.dp,
                    )
                )
                .background(if (isUser) UserBubbleBg else AssistantBubbleBg)
                .padding(12.dp),
        ) {
            Column {
                // 用户发送的图片
                if (message.imageUris.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(bottom = if (message.content.isNotEmpty()) 8.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        message.imageUris.take(3).forEach { imageUri ->
                            // Coil 无法直接加载 data:base64 URI，需先解码为 ByteArray
                            val model = remember(imageUri) { toCoilImageModel(imageUri) }
                            AsyncImage(
                                model = model,
                                contentDescription = "图片",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }

                // AI 生成的图片（左下角保存按钮 + 长按保存）
                if (message.generatedImageUrl != null) {
                    val imageUrl = message.generatedImageUrl
                    GeneratedImage(
                        imageUrl = imageUrl,
                        onSaveImage = { onSaveImage(imageUrl) },
                        modifier = Modifier.padding(
                            bottom = if (message.content.isNotEmpty()) 8.dp else 0.dp,
                        ),
                    )
                }

                // 文本内容
                if (message.content.isNotEmpty()) {
                    Text(
                        text = message.content + if (message.isStreaming) "▌" else "",
                        color = if (isUser) UserBubbleText else AssistantBubbleText,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        // AI 消息底部操作栏
        if (!isUser && !message.isStreaming && message.content.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeedbackButton(
                    icon = Icons.Default.ThumbUp,
                    label = "赞",
                    onClick = onLike,
                    isActive = message.isLiked,
                    activeColor = LikeColor,
                    normalColor = CopyColor,
                )
                FeedbackButton(
                    icon = Icons.Default.ThumbDown,
                    label = "踩",
                    onClick = onDislike,
                    isActive = message.isDisliked,
                    activeColor = DislikeColor,
                    normalColor = CopyColor,
                )
                FeedbackButton(
                    icon = Icons.Default.ContentCopy,
                    label = "复制",
                    onClick = onCopy,
                    isActive = false,
                    activeColor = CopyColor,
                    normalColor = CopyColor,
                )
                FeedbackButton(
                    icon = Icons.Default.Refresh,
                    label = "重试",
                    onClick = onRetry,
                    isActive = false,
                    activeColor = CopyColor,
                    normalColor = CopyColor,
                )

                if (showAudioActions) {
                    // 播报按钮：播放中显示停止图标，否则显示音量图标
                    if (isPlaying) {
                        FeedbackButton(
                            icon = Icons.Default.Stop,
                            label = "停止",
                            onClick = onStopAudio,
                            isActive = true,
                            activeColor = LikeColor,
                            normalColor = CopyColor,
                        )
                    } else {
                        FeedbackButton(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = "播报",
                            onClick = onPlayAudio,
                            isActive = false,
                            activeColor = LikeColor,
                            normalColor = CopyColor,
                        )
                    }
                }
            }

            // 追问建议
            if (message.followUpSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    message.followUpSuggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { onFollowUp(suggestion) },
                            label = {
                                Text(
                                    text = suggestion,
                                    fontSize = TextUnit(11f, TextUnitType.Sp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 将存储的图片标识转换为 Coil 可加载的 model。
 * Coil 原生不支持 data:base64 URI，需解码为 ByteArray（Coil 支持 ByteArray）。
 * 其它（content:// / http(s) URL）直接返回字符串本身。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GeneratedImage(
    imageUrl: String,
    onSaveImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "生成的图片，长按保存",
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = {},
                    onLongClick = onSaveImage,
                ),
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onSaveImage),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.ArrowDownward,
                contentDescription = "保存图片",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun toCoilImageModel(model: String): Any {
    if (!model.startsWith("data:")) return model
    val marker = "base64,"
    val idx = model.indexOf(marker)
    if (idx < 0) return model
    return try {
        Base64.decode(model.substring(idx + marker.length), Base64.DEFAULT)
    } catch (_: Exception) {
        model
    }
}

@Composable
private fun FeedbackButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isActive: Boolean,
    activeColor: Color,
    normalColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isActive) activeColor.copy(alpha = 0.12f) else normalColor.copy(alpha = 0.08f),
            ),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) activeColor else normalColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) activeColor else normalColor.copy(alpha = 0.6f),
            fontSize = TextUnit(9f, TextUnitType.Sp),
        )
    }
}