package com.bytedace.doubaoapp.data.model

/**
 * 联网搜索结果数据类。
 * 用于在 AI 消息中展示搜索引用来源。
 */
data class SearchResult(
    val title: String,    // 网页标题
    val url: String,      // 网页链接
    val snippet: String   // 搜索摘要
)