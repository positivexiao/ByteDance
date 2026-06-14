package com.bytedace.doubaoapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.bytedace.doubaoapp.platform.PlatformAsyncImage
import com.bytedace.doubaoapp.ui.theme.InputBarBackground

@Composable
fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isRecognizing: Boolean = false,
    onStartRecognition: () -> Unit = {},
    onStopRecognition: () -> Unit = {},
    onPickImage: () -> Unit = {},
    selectedImageUris: List<String> = emptyList(),
    onRemoveImage: (String) -> Unit = {},
    webSearchEnabled: Boolean = false,
    imageGenEnabled: Boolean = false,
    localTextOnlyMode: Boolean = false,
    isLoading: Boolean = false,
    onToggleWebSearch: () -> Unit = {},
    onToggleImageGen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val visibleImageUris = if (localTextOnlyMode) emptyList() else selectedImageUris
    val canSend = !isLoading && (value.isNotBlank() || visibleImageUris.isNotEmpty())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (localTextOnlyMode) {
            Text(
                text = "本地模型仅支持文本对话",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = TextUnit(12f, TextUnitType.Sp),
            )
        } else {
            // 模式切换按钮：联网搜索 / 生成图片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeChip(
                    icon = "🌐",
                    label = "联网搜索",
                    isSelected = webSearchEnabled,
                    onClick = onToggleWebSearch
                )
                ModeChip(
                    icon = "🎨",
                    label = "生成图片",
                    isSelected = imageGenEnabled,
                    onClick = onToggleImageGen
                )
            }
        }

        // 已选图片预览
        if (visibleImageUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                visibleImageUris.forEach { imageRef ->
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        PlatformAsyncImage(
                            model = imageRef,
                            contentDescription = "已选图片",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onRemoveImage(imageRef) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .padding(2.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除图片",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 输入区：图片选择 + 语音 + 输入框 + 发送
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!localTextOnlyMode) {
                // 图片选择按钮
                IconButton(
                    onClick = onPickImage,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = InputBarBackground,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "选择图片",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 语音输入按钮
                VoiceInputButton(
                    isRecognizing = isRecognizing,
                    onStartRecognition = onStartRecognition,
                    onStopRecognition = onStopRecognition,
                    modifier = Modifier.size(40.dp)
                )
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .background(InputBarBackground, RoundedCornerShape(24.dp)),
                placeholder = {
                    Text(
                        "给豆包发送消息...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = InputBarBackground,
                    unfocusedContainerColor = InputBarBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            // 发送按钮
            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (canSend)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ModeChip(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedBg = Color(0xFFE3F2FD)       // blue-50
    val selectedBorder = Color(0xFF2196F3)    // blue-500
    val selectedText = Color(0xFF1976D2)      // blue-600
    val unselectedBg = Color.Transparent
    val unselectedBorder = Color(0xFFE0E0E0)  // gray-200
    val unselectedText = Color(0xFF9E9E9E)    // gray-400

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(
                width = 1.dp,
                color = if (isSelected) selectedBorder else unselectedBorder,
                shape = RoundedCornerShape(50)
            )
            .background(if (isSelected) selectedBg else unselectedBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = TextUnit(12f, TextUnitType.Sp)
        )
        Text(
            text = label,
            color = if (isSelected) selectedText else unselectedText,
            fontSize = TextUnit(12f, TextUnitType.Sp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}