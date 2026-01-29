package dev.kroder.magnus.infrastructure.persistence

import dev.kroder.magnus.domain.exception.DataUnavailableException
import dev.kroder.magnus.domain.model.PlayerData
import dev.kroder.magnus.domain.port.PlayerRepository
import dev.kroder.magnus.infrastructure.persistence.local.LocalBackupRepository
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * A Decorator that adds resilience to the [PlayerRepository].
 * It catches exceptions from the primary repository and handles them:
 * - Read: If primary fails, try to fallback (optional) or throw specific Exception to kick player.
 * - Write: If primary fails, SAVE TO LOCAL BACKUP.
 */
class ResilientPlayerRepository(
    private val primary: PlayerRepository,
    private val backup: LocalBackupRepository
) : PlayerRepository {

    private val logger = LoggerFactory.getLogger("magnus-resilience")

    override fun save(data: PlayerData) {
        try {
            primary.save(data)
        } catch (e: Exception) {
            logger.error("Primary repository failed to save player ${data.username} (${data.uuid}). Failing over to LOCAL BACKUP.", e)
            try {
                backup.save(data)
                logger.warn("Saved player ${data.username} to local backup successfully.")
            } catch (ex: Exception) {
                logger.error("CRITICAL: Failed to save to local backup too!", ex)
            }
        }
    }

    override fun findByUuid(uuid: UUID): PlayerData? {
        // 1. O(1) Check: Do we have a specific local backup for this player?
        // This is the "In-Memory Dirty Set" optimization.
        val hasLocalBackup = backup.hasBackup(uuid)
        
        var remoteData: PlayerData? = null
        var remoteError: Exception? = null

        // 2. Try to load from Primary (DB/Redis)
        try {
            remoteData = primary.findByUuid(uuid)
        } catch (e: Exception) {
            remoteError = e
            logger.error("Primary repository failed to load data for $uuid. (Has Local: $hasLocalBackup)", e)
        }

        // 3. Conflict Resolution (The "Freshness Check")
        if (hasLocalBackup) {
            val localData = backup.findByUuid(uuid)
            
            if (localData != null) {
                // If remote is null (new player or DB down) -> Use Local
                if (remoteData == null) {
                    logger.warn("Using LOCAL backup for $uuid (Remote unavailable or null).")
                    return localData
                }

                // If both exist -> Compare Timestamps
                if (localData.lastUpdated > remoteData.lastUpdated) {
                    logger.warn("Using LOCAL backup for $uuid (Local is FRESHER: ${localData.lastUpdated} > ${remoteData.lastUpdated}). DB is stale.")
                    return localData
                } // else: Remote is newer or equal -> Use Remote
            }
        }

        // 4. Return Remote if valid
        if (remoteData != null) {
            return remoteData
        }

        // 5. If Remote failed and we had no local backup -> CRITICAL FAIL
        if (remoteError != null) {
            throw DataUnavailableException("Database is down and no local backup found for $uuid", remoteError)
        }

        // 6. Both are null (New Player)
        return null
    }

    override fun deleteCache(uuid: UUID) {
        try {
            primary.deleteCache(uuid)
        } catch (e: Exception) {
            logger.warn("Failed to delete cache for $uuid", e)
        }
    }
}
