package com.bytedace.doubaoapp.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun VoiceInputButton(
    isRecognizing: Boolean,
    onStartRecognition: () -> Unit,
    onStopRecognition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartRecognition()
    }

    IconButton(
        onClick = {
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                if (isRecognizing) onStopRecognition() else onStartRecognition()
            }
        },
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            imageVector = if (isRecognizing) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isRecognizing) "停止录音" else "语音输入",
            tint = if (isRecognizing) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}