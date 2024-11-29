package com.example.ble_demo.ui

/**
 * チャットメッセージのテンプレート
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val isFromMe: Boolean,
    val timestamp: Long
)