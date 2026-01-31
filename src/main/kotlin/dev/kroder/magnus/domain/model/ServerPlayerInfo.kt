package dev.kroder.magnus.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the player list from a single server for heartbeat synchronization.
 */
@Serializable
data class ServerPlayerInfo(
    val serverName: String,
    val players: List<PlayerEntry>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Minimal player entry for player list sync.
 */
@Serializable
data class PlayerEntry(
    val uuid: String,
    val name: String
)
