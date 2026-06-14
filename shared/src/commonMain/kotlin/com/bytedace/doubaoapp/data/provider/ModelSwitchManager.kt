package com.bytedace.doubaoapp.data.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 模型切换管理器。
 * 管理当前活跃的 LlmProvider，支持远端/本地模型无缝切换。
 * 后续端智能扩展时，只需注册 "local" Provider 并调用 switchProvider("local")。
 */
class ModelSwitchManager(
    private val providers: MutableMap<String, LlmProvider>  // key = "remote" / "local"
) {
    private val _currentProviderKey = MutableStateFlow(providers.keys.firstOrNull() ?: "remote")
    val currentProviderKeyFlow: StateFlow<String> = _currentProviderKey.asStateFlow()

    val currentProvider: LlmProvider
        get() = providers[_currentProviderKey.value]
            ?: throw IllegalStateException("No provider found for key: ${_currentProviderKey.value}")

    val currentProviderKey: String get() = _currentProviderKey.value

    /** 替换指定 Provider 实例（保存设置后热更新远端配置） */
    fun replaceProvider(key: String, provider: LlmProvider) {
        if (!providers.containsKey(key)) {
            throw IllegalArgumentException("Unknown provider key: $key. Available: ${providers.keys}")
        }
        providers[key] = provider
    }

    /** 切换到指定 Provider（如 "remote" → "local"） */
    fun switchProvider(key: String) {
        if (providers.containsKey(key)) {
            _currentProviderKey.value = key
        } else {
            throw IllegalArgumentException("Unknown provider key: $key. Available: ${providers.keys}")
        }
    }

    /** 返回所有可用 Provider 列表，用于 UI 展示 */
    fun availableProviders(): List<Pair<String, String>> =
        providers.map { (k, v) -> k to v.modelName() }
}