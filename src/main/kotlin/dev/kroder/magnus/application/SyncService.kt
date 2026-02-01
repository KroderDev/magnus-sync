package dev.kroder.magnus.application

import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.domain.port.PlayerRepository
import java.util.UUID

/**
 * Application service that orchestrates the synchronization logic.
 * This service mediates between the Minecraft-specific listeners and the database adapters.
 * 
 * Functional Limits:
 * - This service does not know anything about NBT, JDBC, or Redis.
 * - It relies purely on the [PlayerData] model and [PlayerRepository] interface.
 */
import dev.kroder.magnus.domain.processing.LockManager
import dev.kroder.magnus.domain.exception.SessionLockedException

class SyncService(
    private val repository: PlayerRepository,
    private val lockManager: LockManager? = null
) {

    /**
     * Handles the logic of loading a player's data when they join the server.
     * 
     * @param playerUuid The UUID of the player joining.
     * @return The data loaded from storage, or null if no data is found (new player).
     */
    fun loadPlayerData(playerUuid: UUID): PlayerData? {
        if (lockManager != null && lockManager.isLocked(playerUuid)) {
            throw SessionLockedException("Session is locked for player $playerUuid. Please try again later.")
        }

        // Here we could add logic like: check cache, if not found check DB, etc.
        // But the repository implementation (Adapters) will handle the Redis/Postgres layering.
        return repository.findByUuid(playerUuid)
    }

    /**
     * Handles the logic of saving a player's data to storage.
     * 
     * @param data The player data snapshot captured from the game.
     */
    fun savePlayerData(data: PlayerData) {
        if (lockManager != null) {
            lockManager.lock(data.uuid)
        }
        
        try {
            repository.save(data)
        } finally {
            if (lockManager != null) {
                lockManager.unlock(data.uuid)
            }
        }
    }

    /**
     * Evicts data from the hot cache when it's no longer needed on this instance.
     * 
     * @param playerUuid The UUID of the player who left.
     */
    fun releaseCache(playerUuid: UUID) {
        repository.deleteCache(playerUuid)
    }

    /**
     * Checks if a player's session is currently locked.
     */
    fun isSessionLocked(playerUuid: UUID): Boolean {
        return lockManager?.isLocked(playerUuid) == true
    }

    /**
     * Saves all online players' data in a fail-safe manner.
     * This is typically used during server shutdown.
     */
    fun saveAllPlayerData(playerDataList: List<PlayerData>) {
        playerDataList.forEach { data ->
            try {
                savePlayerData(data)
            } catch (e: Exception) {
                // Fail-safe: Log error and continue with the next player
                // We don't have a logger here, but the repository or the caller should handle logging.
                // For now, we ensure the loop doesn't break.
                System.err.println("Failed to save player data for ${data.uuid}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
