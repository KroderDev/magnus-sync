package dev.kroder.magnus.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a chat message for cross-server synchronization.
 * Contains raw text only - no formatting applied to maintain compatibility
 * with chat formatting mods (LuckPerms, Stylist, etc.).
 */
@Serializable
data class ChatMessage(
    val serverName: String,
    val playerUuid: String,
    val playerName: String,
    val rawMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)
