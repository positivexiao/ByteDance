package com.bytedace.doubaoapp.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedace.doubaoapp.ChatViewModel
import com.bytedace.doubaoapp.ChatViewModelFactory
import com.bytedace.doubaoapp.data.context.ContextPolicy
import com.bytedace.doubaoapp.data.model.ChatMessage
import com.bytedace.doubaoapp.data.repository.ChatRepository
import com.bytedace.doubaoapp.ui.components.CenteredAppTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contextPolicy: ContextPolicy,
    repository: ChatRepository,
    sessionId: String = "",
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSessionList: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = ChatViewModelFactory(contextPolicy, repository)
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) {
        if (sessionId.isNotEmpty()) {
            viewModel.loadSession(sessionId)
        }
    }
    val listState = rememberLazyListState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        viewModel.onImageSelected(uris)
    }

    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        CenteredAppTopBar(
            title = "豆包",
            onBack = onNavigateToSessionList,
            actions = {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = uiState.messages,
                    key = { message: ChatMessage -> message.id }
                ) { message ->
                    MessageItem(
                        message = message,
                        onCopy = { viewModel.copyMessage(message.id) },
                        onLike = { viewModel.likeMessage(message.id) },
                        onDislike = { viewModel.dislikeMessage(message.id) },
                        onRetry = { viewModel.retryMessage(message.id) },
                        onPlayAudio = { viewModel.playAudio(message.id) },
                        onStopAudio = { viewModel.stopAudio() },
                        onFollowUp = { viewModel.followUp(it) },
                        onSaveImage = { viewModel.saveGeneratedImage(it) },
                        isPlaying = uiState.playingMessageId == message.id,
                        showAudioActions = uiState.currentModelSource != "local"
                    )
                }
            }

            if (uiState.isLoading && uiState.messages.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        InputBar(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding(),
            value = uiState.inputText,
            onValueChange = { viewModel.updateInputText(it) },
            onSend = { viewModel.sendMessage() },
            isRecognizing = uiState.isRecognizing,
            onStartRecognition = { viewModel.startVoiceRecognition() },
            onStopRecognition = { viewModel.stopVoiceRecognition() },
            onPickImage = { imagePickerLauncher.launch("image/*") },
            selectedImageUris = uiState.selectedImageUris,
            onRemoveImage = { viewModel.removeImage(it) },
            webSearchEnabled = uiState.webSearchEnabled,
            imageGenEnabled = uiState.imageGenEnabled,
            localTextOnlyMode = uiState.currentModelSource == "local",
            isLoading = uiState.isLoading,
            onToggleWebSearch = { viewModel.toggleWebSearch() },
            onToggleImageGen = { viewModel.toggleImageGen() }
        )
    }
}
