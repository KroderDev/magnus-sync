package dev.kroder.magnus.domain.port

import dev.kroder.magnus.domain.model.PlayerData
import java.util.UUID

/**
 * Port interface for managing player data persistence and retrieval.
 * This interface decouples the domain logic from specific implementations like Postgres or Redis.
 * 
 * Functional Limits:
 * - Implementations must ensure thread-safety.
 * - Timeouts and connection errors should be handled by the implementation or a decorator.
 */
interface PlayerRepository {
    
    /**
     * Saves the player data to the primary storage (usually Redis + Postgres sync).
     * @param data The player data to persist.
     */
    fun save(data: PlayerData)

    /**
     * Retrieves the player data by their unique UUID.
     * @param uuid The UUID of the player.
     * @return The PlayerData if found, or null otherwise.
     */
    fun findByUuid(uuid: UUID): PlayerData?

    /**
     * Deletes the cached player data (useful when player is offline for a long time).
     * @param uuid The UUID of the player.
     */
    fun deleteCache(uuid: UUID)
}
