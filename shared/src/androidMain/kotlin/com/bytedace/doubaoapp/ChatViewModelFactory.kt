package com.bytedace.doubaoapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.bytedace.doubaoapp.platform.AppServices

class ChatViewModelFactory(
    private val appServices: AppServices,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return ChatViewModel(appServices = appServices) as T
    }
}
