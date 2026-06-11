package com.bytedace.doubaoapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.bytedace.doubaoapp.data.context.ContextPolicy
import com.bytedace.doubaoapp.data.repository.ChatRepository

class ChatViewModelFactory(
    private val contextPolicy: ContextPolicy,
    private val repository: ChatRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        return ChatViewModel(
            application = application,
            contextPolicy = contextPolicy,
            repository = repository
        ) as T
    }
}