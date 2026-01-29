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
class SyncService(
    private val repository: PlayerRepository
) {

    /**
     * Handles the logic of loading a player's data when they join the server.
     * 
     * @param playerUuid The UUID of the player joining.
     * @return The data loaded from storage, or null if no data is found (new player).
     */
    fun loadPlayerData(playerUuid: UUID): PlayerData? {
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
        repository.save(data)
    }

    /**
     * Evicts data from the hot cache when it's no longer needed on this instance.
     * 
     * @param playerUuid The UUID of the player who left.
     */
    fun releaseCache(playerUuid: UUID) {
        repository.deleteCache(playerUuid)
    }
}
